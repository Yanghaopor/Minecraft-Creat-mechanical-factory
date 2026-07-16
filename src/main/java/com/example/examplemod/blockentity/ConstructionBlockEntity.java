package com.example.examplemod.blockentity;

import com.example.examplemod.construction.ConstructionPortMode;
import com.example.examplemod.construction.ConstructionRoomData;
import com.example.examplemod.construction.ConstructionRoomManager;
import com.example.examplemod.item.ConstructionBlockItem;
import com.example.examplemod.registry.ModBlockEntities;
import com.example.examplemod.registry.ModBlocks;
import com.example.examplemod.registry.ModItems;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class ConstructionBlockEntity extends KineticBlockEntity {
    private static final int ITEM_BUFFER_SLOTS = 9;
    private static final int FLUID_BUFFER_CAPACITY = 16_000;

    private final EnumMap<Direction, ConstructionPortMode> ports = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, ItemStackHandler> itemBuffers = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, FluidTank> fluidBuffers = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> redstoneSignals = new EnumMap<>(Direction.class);
    @Nullable
    private UUID roomId;
    @Nullable
    private BlockPos entryAnchorPosition;
    private boolean entryAnchorActive;
    /** Not saved: a FakePlayer is recreated after a level reload when needed. */
    private boolean entryAnchorRegistered;
    private float bridgedSourceSpeed;
    private float bridgedCapacity;
    private float bridgedStress;
    private boolean bridgeSourceMode;
    /** Set only while the host is being packed into its unique migration item. */
    private boolean preserveRoomOnRemoval;

    public ConstructionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONSTRUCTION_BLOCK.get(), pos, state);
        for (Direction direction : Direction.values()) {
            ports.put(direction, ConstructionPortMode.DISABLED);
            redstoneSignals.put(direction, 0);
            itemBuffers.put(direction, new ItemStackHandler(ITEM_BUFFER_SLOTS) {
                @Override
                protected void onContentsChanged(int slot) {
                    setChanged();
                }
            });
            fluidBuffers.put(direction, new FluidTank(FLUID_BUFFER_CAPACITY) {
                @Override
                protected void onContentsChanged() {
                    setChanged();
                }
            });
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (getLevel() instanceof ServerLevel level) {
            tickRoom(level);
            tickKineticBridge();
        }
    }

    public ConstructionPortMode getPortMode(Direction direction) {
        return ports.get(direction);
    }

    @Nullable
    public UUID getRoomId() {
        return roomId;
    }

    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
        setChanged();
        sendData();
    }

    public void cyclePort(Direction direction) {
        ConstructionPortMode next = ports.get(direction).next();
        refreshKinetics();
        ports.put(direction, next);
        if (getLevel() instanceof ServerLevel level) {
            level.invalidateCapabilities(getBlockPos());
            level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
            ConstructionRoomManager.get(level.getServer(), roomId == null ? new UUID(0, 0) : roomId).ifPresent(record -> {
                ServerLevel room = level.getServer().getLevel(ConstructionRoomManager.CONSTRUCTION_DIMENSION);
                if (room != null) {
                    BlockPos socket = record.socketPosition(direction);
                    room.invalidateCapabilities(socket);
                    room.updateNeighborsAt(socket, ModBlocks.CONSTRUCTION_INTERFACE.get());
                    if (room.getBlockEntity(socket) instanceof ConstructionInterfaceBlockEntity interfaceBlockEntity) {
                        interfaceBlockEntity.setPortMode(next);
                    }
                }
            });
        }
        setChanged();
        sendData();
    }

    public void enter(ServerPlayer player) {
        try {
            ConstructionRoomManager.enter(player, this);
        } catch (IllegalStateException exception) {
            player.displayClientMessage(Component.translatable("message.examplemod.construction.dimension_unavailable"), true);
        }
    }

    /**
     * Places a hidden FakePlayer at the position from which the owner entered.
     * It creates normal player chunk tickets in the outer world while the room
     * is occupied; it is removed as soon as the last visitor exits.
     */
    public void beginEntryAnchor(ServerPlayer enteringPlayer) {
        // One room can have multiple visitors. Keep the original return anchor
        // until the final visitor leaves instead of moving it under them.
        if (!entryAnchorActive) {
            entryAnchorPosition = enteringPlayer.blockPosition().immutable();
            entryAnchorActive = true;
        }
        if (getLevel() instanceof ServerLevel hostLevel) {
            ensureEntryAnchor(hostLevel);
        }
        setChanged();
        sendData();
    }

    public void endEntryAnchorIfRoomEmpty() {
        if (!(getLevel() instanceof ServerLevel hostLevel) || roomId == null) {
            return;
        }
        ConstructionRoomData.RoomRecord record = ConstructionRoomManager.get(hostLevel.getServer(), roomId).orElse(null);
        ServerLevel room = hostLevel.getServer().getLevel(ConstructionRoomManager.CONSTRUCTION_DIMENSION);
        if (record != null && room != null && room.players().stream().anyMatch(player -> record.contains(player.blockPosition()))) {
            return;
        }
        removeEntryAnchor(hostLevel);
        entryAnchorActive = false;
        entryAnchorPosition = null;
        setChanged();
        sendData();
    }

    private void ensureEntryAnchor(ServerLevel hostLevel) {
        if (!entryAnchorActive || entryAnchorRegistered || roomId == null || entryAnchorPosition == null) {
            return;
        }
        FakePlayer anchor = FakePlayerFactory.get(hostLevel, new GameProfile(roomId, "Construction"));
        anchor.setPos(entryAnchorPosition.getX() + 0.5D, entryAnchorPosition.getY(), entryAnchorPosition.getZ() + 0.5D);
        anchor.setInvisible(true);
        // Register only with the chunk source. The anchor has real player
        // tickets but is not a world entity, so it cannot be seen or collide.
        hostLevel.getChunkSource().addEntity(anchor);
        entryAnchorRegistered = true;
    }

    private void removeEntryAnchor(ServerLevel hostLevel) {
        if (!entryAnchorRegistered || roomId == null) {
            return;
        }
        FakePlayer anchor = FakePlayerFactory.get(hostLevel, new GameProfile(roomId, "Construction"));
        hostLevel.getChunkSource().removeEntity(anchor);
        entryAnchorRegistered = false;
    }

    public void tryDismantle(ServerPlayer player) {
        if (!(getLevel() instanceof ServerLevel hostLevel)) {
            return;
        }
        if (!canDismantle(hostLevel)) {
            player.displayClientMessage(Component.translatable("message.examplemod.construction.not_empty"), true);
            return;
        }
        Block.popResource(hostLevel, getBlockPos(), new ItemStack(ModItems.CONSTRUCTION_BLOCK.get()));
        hostLevel.removeBlock(getBlockPos(), false);
    }

    public void tryPackForMigration(ServerPlayer player) {
        if (!(getLevel() instanceof ServerLevel hostLevel)) {
            return;
        }
        if (roomId == null) {
            refreshKinetics();
            ItemStack stack = new ItemStack(ModItems.CONSTRUCTION_BLOCK.get());
            saveToItem(stack, hostLevel.registryAccess());
            preserveRoomOnRemoval = true;
            if (hostLevel.removeBlock(getBlockPos(), false)) {
                giveOrDrop(player, stack);
            } else {
                preserveRoomOnRemoval = false;
            }
            return;
        }

        ConstructionRoomData.RoomRecord record = ConstructionRoomManager.get(hostLevel.getServer(), roomId).orElse(null);
        if (record == null || !record.isHostedBy(hostLevel, getBlockPos())) {
            player.displayClientMessage(Component.translatable("message.examplemod.construction.migration_invalid"), true);
            return;
        }
        ServerLevel room = hostLevel.getServer().getLevel(ConstructionRoomManager.CONSTRUCTION_DIMENSION);
        if (room != null && room.players().stream().anyMatch(innerPlayer -> record.contains(innerPlayer.blockPosition()))) {
            player.displayClientMessage(Component.translatable("message.examplemod.construction.occupied"), true);
            return;
        }

        UUID token = ConstructionRoomManager.beginMigration(hostLevel, getBlockPos(), roomId).orElse(null);
        if (token == null) {
            player.displayClientMessage(Component.translatable("message.examplemod.construction.migration_invalid"), true);
            return;
        }
        removeEntryAnchor(hostLevel);
        entryAnchorActive = false;
        entryAnchorPosition = null;
        refreshKinetics();
        ItemStack migrationStack = ConstructionBlockItem.createMigrationStack(this, token);
        preserveRoomOnRemoval = true;
        if (!hostLevel.removeBlock(getBlockPos(), false)) {
            preserveRoomOnRemoval = false;
            ConstructionRoomManager.restoreHost(hostLevel, getBlockPos(), roomId, token);
            return;
        }
        giveOrDrop(player, migrationStack);
    }

    public void onHostRemoved() {
        if (!(getLevel() instanceof ServerLevel hostLevel)) {
            return;
        }
        if (preserveRoomOnRemoval) {
            preserveRoomOnRemoval = false;
            return;
        }
        removeEntryAnchor(hostLevel);
        if (roomId != null) {
            ConstructionRoomManager.destroyActiveRoom(hostLevel, getBlockPos(), roomId);
            roomId = null;
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    public boolean canDismantle(ServerLevel hostLevel) {
        if (roomId == null) {
            return true;
        }
        ConstructionRoomData.RoomRecord record = ConstructionRoomManager.get(hostLevel.getServer(), roomId).orElse(null);
        ServerLevel room = hostLevel.getServer().getLevel(ConstructionRoomManager.CONSTRUCTION_DIMENSION);
        if (record == null || room == null) {
            return true;
        }
        if (room.players().stream().anyMatch(player -> record.contains(player.blockPosition()))) {
            return false;
        }
        AABB bounds = new AABB(record.origin().getX() + 1, record.origin().getY() + 1, record.origin().getZ() + 1,
                record.origin().getX() + 17, record.origin().getY() + 17, record.origin().getZ() + 17);
        if (!room.getEntitiesOfClass(ItemEntity.class, bounds).isEmpty()) {
            return false;
        }
        for (int x = 1; x <= 16; x++) {
            for (int y = 1; y <= 16; y++) {
                for (int z = 1; z <= 16; z++) {
                    BlockState state = room.getBlockState(record.origin().offset(x, y, z));
                    if (!state.isAir() && !state.is(ModBlocks.CONSTRUCTION_INTERFACE.get()) && !state.is(ModBlocks.EXIT_CONSOLE.get())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Nullable
    public IItemHandler getExternalItemHandler(Direction direction) {
        return switch (getPortMode(direction)) {
            case ITEM_INPUT -> new PortItemHandler(direction, true, false);
            case ITEM_OUTPUT -> new PortItemHandler(direction, false, true);
            default -> null;
        };
    }

    @Nullable
    public IItemHandler getInternalItemHandler(Direction direction) {
        return switch (getPortMode(direction)) {
            case ITEM_INPUT -> new PortItemHandler(direction, false, true);
            case ITEM_OUTPUT -> new PortItemHandler(direction, true, false);
            default -> null;
        };
    }

    @Nullable
    public IFluidHandler getExternalFluidHandler(Direction direction) {
        return switch (getPortMode(direction)) {
            case FLUID_INPUT -> new PortFluidHandler(direction, true, false);
            case FLUID_OUTPUT -> new PortFluidHandler(direction, false, true);
            default -> null;
        };
    }

    @Nullable
    public IFluidHandler getInternalFluidHandler(Direction direction) {
        return switch (getPortMode(direction)) {
            case FLUID_INPUT -> new PortFluidHandler(direction, false, true);
            case FLUID_OUTPUT -> new PortFluidHandler(direction, true, false);
            default -> null;
        };
    }

    public int getExternalRedstoneSignal(Direction direction) {
        return getPortMode(direction) == ConstructionPortMode.REDSTONE_OUTPUT ? redstoneSignals.get(direction) : 0;
    }

    public int getInternalRedstoneSignal(Direction direction) {
        return getPortMode(direction) == ConstructionPortMode.REDSTONE_INPUT ? redstoneSignals.get(direction) : 0;
    }

    @Override
    public float getGeneratedSpeed() {
        return bridgeSourceMode ? bridgedSourceSpeed : 0;
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (!bridgeSourceMode) {
            return super.calculateAddedStressCapacity();
        }
        lastCapacityProvided = bridgedCapacity;
        return bridgedCapacity;
    }

    @Override
    public float calculateStressApplied() {
        if (countKineticPorts(ConstructionPortMode.KINETIC_INPUT) == 0) {
            return super.calculateStressApplied();
        }
        lastStressApplied = bridgedStress;
        return bridgedStress;
    }

    public float getExportedCapacity() {
        float speed = Math.abs(getTheoreticalSpeed());
        return speed < 0.001F || !hasNetwork() ? 0 : getOrCreateNetwork().calculateCapacity() / speed;
    }

    public float getExportedStress() {
        float speed = Math.abs(getTheoreticalSpeed());
        return speed < 0.001F || !hasNetwork() ? 0 : getOrCreateNetwork().calculateStress() / speed;
    }

    private void tickRoom(ServerLevel hostLevel) {
        if (roomId == null) {
            return;
        }
        ConstructionRoomData.RoomRecord record = ConstructionRoomManager.get(hostLevel.getServer(), roomId).orElse(null);
        ServerLevel room = hostLevel.getServer().getLevel(ConstructionRoomManager.CONSTRUCTION_DIMENSION);
        if (record == null || !record.isHostedBy(hostLevel, getBlockPos())) {
            roomId = null;
            setChanged();
            sendData();
            return;
        }
        if (room == null) {
            return;
        }
        ensureEntryAnchor(hostLevel);
        if (hostLevel.getGameTime() % 20 == 0) {
            syncRoomPortModes(room, record);
        }

        for (Direction direction : Direction.values()) {
            ConstructionPortMode mode = getPortMode(direction);
            int previous = redstoneSignals.get(direction);
            int next = previous;
            if (mode == ConstructionPortMode.REDSTONE_INPUT) {
                next = hostLevel.getSignal(getBlockPos().relative(direction), direction);
            } else if (mode == ConstructionPortMode.REDSTONE_OUTPUT) {
                BlockPos socket = record.socketPosition(direction);
                next = room.getSignal(socket.relative(direction.getOpposite()), direction.getOpposite());
            }
            if (next != previous) {
                redstoneSignals.put(direction, next);
                hostLevel.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
                room.updateNeighborsAt(record.socketPosition(direction), ModBlocks.CONSTRUCTION_INTERFACE.get());
                setChanged();
            }
        }
    }

    private void tickKineticBridge() {
        List<KineticPeer> inputPeers = getKineticPeers(ConstructionPortMode.KINETIC_INPUT);
        List<KineticPeer> outputPeers = getKineticPeers(ConstructionPortMode.KINETIC_OUTPUT);
        if (!inputPeers.isEmpty()) {
            // The outer network drives all internal input interfaces. Create snapshots a
            // member's base stress values when it joins a network, so the
            // bridge also updates the network's member maps when either side
            // changes instead of relying on networkDirty alone.
            updateBridgeSource(0, false);
            float capacityShare = getExportedCapacity() / inputPeers.size();
            for (KineticPeer peer : inputPeers) {
                peer.blockEntity().setBridgedStress(0);
                peer.blockEntity().setBridgedCapacity(capacityShare);
                peer.blockEntity().updateBridgeSource(getTheoreticalSpeed(), true);
            }
            for (KineticPeer peer : outputPeers) {
                peer.blockEntity().updateBridgeSource(0, false);
                peer.blockEntity().setBridgedStress(0);
            }
            setBridgedStress(sumDistinctNetworkStress(inputPeers));
        } else if (!outputPeers.isEmpty()) {
            // With no outer input, the first powered internal output network
            // drives every external output face. Multiple interfaces attached
            // to that same internal network share the mirrored outer load.
            for (KineticPeer peer : outputPeers) {
                peer.blockEntity().updateBridgeSource(0, false);
            }
            KineticPeer driver = findPoweredPeer(outputPeers);
            List<KineticPeer> activeOutputs = peersOnSameNetwork(outputPeers, driver);
            setBridgedStress(0);
            setBridgedCapacity(driver.blockEntity().getExportedCapacity());
            updateBridgeSource(driver.blockEntity().getTheoreticalSpeed(), true);
            float stressShare = activeOutputs.isEmpty() ? 0 : getExportedStress() / activeOutputs.size();
            for (KineticPeer peer : outputPeers) {
                peer.blockEntity().setBridgedStress(activeOutputs.contains(peer) ? stressShare : 0);
            }
        } else {
            setBridgedStress(0);
            setBridgedCapacity(0);
            updateBridgeSource(0, false);
        }
    }

    private float sumDistinctNetworkStress(List<KineticPeer> peers) {
        Set<Long> visitedNetworks = new HashSet<>();
        float total = 0;
        for (KineticPeer peer : peers) {
            ConstructionInterfaceBlockEntity blockEntity = peer.blockEntity();
            if (blockEntity.hasNetwork() && visitedNetworks.add(blockEntity.network)) {
                total += blockEntity.getExportedStress();
            }
        }
        return total;
    }

    private static KineticPeer findPoweredPeer(List<KineticPeer> peers) {
        return peers.stream()
                .filter(peer -> Math.abs(peer.blockEntity().getTheoreticalSpeed()) >= 0.001F)
                .findFirst()
                .orElse(peers.getFirst());
    }

    private static List<KineticPeer> peersOnSameNetwork(List<KineticPeer> peers, KineticPeer driver) {
        Long network = driver.blockEntity().network;
        if (network == null) {
            return List.of(driver);
        }
        return peers.stream()
                .filter(peer -> Objects.equals(peer.blockEntity().network, network))
                .toList();
    }

    private void updateBridgeSource(float speed, boolean sourceMode) {
        if (bridgeSourceMode == sourceMode && Math.abs(bridgedSourceSpeed - speed) < 0.001F) {
            return;
        }
        detachKinetics();
        clearKineticInformation();
        bridgeSourceMode = sourceMode;
        bridgedSourceSpeed = sourceMode ? speed : 0;
        if (!sourceMode) {
            bridgedCapacity = 0;
        }
        if (sourceMode && Math.abs(speed) >= 0.001F) {
            setSpeed(speed);
            setNetwork(getBlockPos().asLong());
            attachKinetics();
        } else if (!sourceMode) {
            // Return to Create's normal receiving behaviour on the next tick.
            updateSpeed = true;
        }
        setChanged();
    }

    private void setBridgedCapacity(float capacity) {
        capacity = sanitizeStressValue(capacity);
        if (Math.abs(bridgedCapacity - capacity) < 0.001F) {
            return;
        }
        bridgedCapacity = capacity;
        lastCapacityProvided = capacity;
        if (bridgeSourceMode && hasNetwork()) {
            getOrCreateNetwork().updateCapacityFor(this, capacity);
        }
        setChanged();
    }

    private void setBridgedStress(float stress) {
        stress = sanitizeStressValue(stress);
        if (Math.abs(bridgedStress - stress) < 0.001F) {
            return;
        }
        bridgedStress = stress;
        lastStressApplied = stress;
        if (hasNetwork()) {
            getOrCreateNetwork().updateStressFor(this, stress);
        }
        setChanged();
    }

    private static float sanitizeStressValue(float value) {
        return Float.isFinite(value) && value > 0 ? value : 0;
    }

    /** Keeps client-visible interior panels correct after room creation or a reload. */
    public void syncRoomPortModes() {
        if (!(getLevel() instanceof ServerLevel hostLevel) || roomId == null) {
            return;
        }
        ConstructionRoomData.RoomRecord record = ConstructionRoomManager.get(hostLevel.getServer(), roomId).orElse(null);
        ServerLevel room = hostLevel.getServer().getLevel(ConstructionRoomManager.CONSTRUCTION_DIMENSION);
        if (record != null && room != null) {
            syncRoomPortModes(room, record);
        }
    }

    private void syncRoomPortModes(ServerLevel room, ConstructionRoomData.RoomRecord record) {
        for (Direction direction : Direction.values()) {
            BlockEntity blockEntity = room.getBlockEntity(record.socketPosition(direction));
            if (blockEntity instanceof ConstructionInterfaceBlockEntity interfaceBlockEntity) {
                interfaceBlockEntity.setPortMode(getPortMode(direction));
            }
        }
    }

    private List<KineticPeer> getKineticPeers(ConstructionPortMode mode) {
        List<KineticPeer> peers = new ArrayList<>();
        if (!(getLevel() instanceof ServerLevel hostLevel) || roomId == null) {
            return peers;
        }
        ConstructionRoomData.RoomRecord record = ConstructionRoomManager.get(hostLevel.getServer(), roomId).orElse(null);
        ServerLevel room = hostLevel.getServer().getLevel(ConstructionRoomManager.CONSTRUCTION_DIMENSION);
        if (record == null || room == null) {
            return peers;
        }
        for (Direction direction : Direction.values()) {
            if (getPortMode(direction) != mode) {
                continue;
            }
            BlockEntity blockEntity = room.getBlockEntity(record.socketPosition(direction));
            if (blockEntity instanceof ConstructionInterfaceBlockEntity peer) {
                peers.add(new KineticPeer(direction, peer));
            }
        }
        return peers;
    }

    private int countKineticPorts(ConstructionPortMode mode) {
        return (int) ports.values().stream().filter(portMode -> portMode == mode).count();
    }

    private record KineticPeer(Direction direction, ConstructionInterfaceBlockEntity blockEntity) {}

    public void refreshKinetics() {
        detachKinetics();
        clearKineticInformation();
        bridgeSourceMode = false;
        bridgedSourceSpeed = 0;
        bridgedCapacity = 0;
        bridgedStress = 0;
        updateSpeed = true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (roomId != null) {
            tag.putUUID("RoomId", roomId);
        }
        tag.putBoolean("EntryAnchorActive", entryAnchorActive);
        if (entryAnchorPosition != null) {
            tag.putLong("EntryAnchorPosition", entryAnchorPosition.asLong());
        }
        int[] modeIds = new int[Direction.values().length];
        CompoundTag itemTags = new CompoundTag();
        CompoundTag fluidTags = new CompoundTag();
        int[] signals = new int[Direction.values().length];
        for (Direction direction : Direction.values()) {
            int index = direction.ordinal();
            modeIds[index] = getPortMode(direction).ordinal();
            itemTags.put(direction.getSerializedName(), itemBuffers.get(direction).serializeNBT(registries));
            fluidTags.put(direction.getSerializedName(), fluidBuffers.get(direction).writeToNBT(registries, new CompoundTag()));
            signals[index] = redstoneSignals.get(direction);
        }
        tag.putIntArray("PortModes", modeIds);
        tag.put("ItemBuffers", itemTags);
        tag.put("FluidBuffers", fluidTags);
        tag.putIntArray("RedstoneSignals", signals);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        roomId = tag.hasUUID("RoomId") ? tag.getUUID("RoomId") : null;
        entryAnchorActive = tag.getBoolean("EntryAnchorActive");
        entryAnchorPosition = tag.contains("EntryAnchorPosition") ? BlockPos.of(tag.getLong("EntryAnchorPosition")) : null;
        entryAnchorRegistered = false;
        int[] modeIds = tag.getIntArray("PortModes");
        int[] signals = tag.getIntArray("RedstoneSignals");
        CompoundTag itemTags = tag.getCompound("ItemBuffers");
        CompoundTag fluidTags = tag.getCompound("FluidBuffers");
        for (Direction direction : Direction.values()) {
            int index = direction.ordinal();
            ConstructionPortMode portMode = ConstructionPortMode.fromId(index < modeIds.length ? modeIds[index] : 0);
            ports.put(direction, portMode);
            redstoneSignals.put(direction, index < signals.length ? signals[index] : 0);
            if (itemTags.contains(direction.getSerializedName())) {
                itemBuffers.get(direction).deserializeNBT(registries, itemTags.getCompound(direction.getSerializedName()));
            }
            if (fluidTags.contains(direction.getSerializedName())) {
                fluidBuffers.get(direction).readFromNBT(registries, fluidTags.getCompound(direction.getSerializedName()));
            }
        }
    }

    private final class PortItemHandler implements IItemHandler {
        private final Direction direction;
        private final boolean allowInsert;
        private final boolean allowExtract;

        private PortItemHandler(Direction direction, boolean allowInsert, boolean allowExtract) {
            this.direction = direction;
            this.allowInsert = allowInsert;
            this.allowExtract = allowExtract;
        }

        private ItemStackHandler buffer() {
            return itemBuffers.get(direction);
        }

        @Override
        public int getSlots() {
            return buffer().getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return buffer().getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return allowInsert ? buffer().insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return allowExtract ? buffer().extractItem(slot, Math.min(amount, 64), simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return buffer().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return allowInsert && buffer().isItemValid(slot, stack);
        }
    }

    private final class PortFluidHandler implements IFluidHandler {
        private final Direction direction;
        private final boolean allowFill;
        private final boolean allowDrain;

        private PortFluidHandler(Direction direction, boolean allowFill, boolean allowDrain) {
            this.direction = direction;
            this.allowFill = allowFill;
            this.allowDrain = allowDrain;
        }

        private FluidTank tank() {
            return fluidBuffers.get(direction);
        }

        @Override
        public int getTanks() {
            return tank().getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return this.tank().getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return this.tank().getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return allowFill && this.tank().isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return allowFill ? tank().fill(resource.copyWithAmount(Math.min(resource.getAmount(), 1_000)), action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return allowDrain ? tank().drain(resource.copyWithAmount(Math.min(resource.getAmount(), 1_000)), action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return allowDrain ? tank().drain(Math.min(maxDrain, 1_000), action) : FluidStack.EMPTY;
        }
    }
}
