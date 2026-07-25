package com.example.examplemod.blockentity;

import com.example.examplemod.construction.ConstructionPortMode;
import com.example.examplemod.construction.ConstructionRoomManager;
import com.example.examplemod.registry.ModBlockEntities;
import com.example.examplemod.registry.ModBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class ConstructionInterfaceBlockEntity extends KineticBlockEntity {
    private ConstructionPortMode portMode = ConstructionPortMode.DISABLED;
    private float bridgedSourceSpeed;
    private float bridgedCapacity;
    private float bridgedStress;
    private boolean bridgeSourceMode;
    @Nullable
    private ConstructionBlockEntity lastCapabilityHost;
    private final IItemHandler itemHandlerProxy = new InteriorItemHandlerProxy();
    private final IFluidHandler fluidHandlerProxy = new InteriorFluidHandlerProxy();

    public ConstructionInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONSTRUCTION_INTERFACE.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (getLevel() instanceof ServerLevel level) {
            ConstructionBlockEntity host = getHost();
            if (host != lastCapabilityHost) {
                // Create caches adjacent capabilities. The real inventory is in
                // another dimension, so a host unload/reload must invalidate the
                // interface capability even though this interface never unloaded.
                lastCapabilityHost = host;
                level.invalidateCapabilities(getBlockPos());
                level.updateNeighborsAt(getBlockPos(), ModBlocks.CONSTRUCTION_INTERFACE.get());
            }
            Direction direction = getPortDirection();
            // Check direction first, then host - order matters for compound condition
            if (direction != null && host != null && host.getPortMode(direction) == ConstructionPortMode.KINETIC_INPUT) {
                updateBridgeSource(host.getTheoreticalSpeed(), true);
            } else {
                updateBridgeSource(0, false);
            }
        }
    }

    public boolean isKineticActive() {
        Direction direction = getPortDirection();
        ConstructionBlockEntity host = getHost();
        return direction != null && host != null && host.getPortMode(direction).isKinetic();
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
        if (portMode != ConstructionPortMode.KINETIC_OUTPUT) {
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

    public void refreshKinetics() {
        detachKinetics();
        clearKineticInformation();
        bridgeSourceMode = false;
        bridgedSourceSpeed = 0;
        bridgedCapacity = 0;
        bridgedStress = 0;
        updateSpeed = true;
    }

    public void updateBridgeSource(float speed, boolean sourceMode) {
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
            updateSpeed = true;
        }
        setChanged();
    }

    public void setBridgedCapacity(float capacity) {
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

    public void setBridgedStress(float stress) {
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

    public ConstructionPortMode getPortMode() {
        return portMode;
    }

    /**
     * A stable capability object for Create's capability cache. The actual host
     * is deliberately resolved for every operation because it lives in another
     * dimension and its block entity can be replaced while this interface and
     * a funnel's cached capability remain loaded.
     */
    public IItemHandler getItemHandlerProxy() {
        return itemHandlerProxy;
    }

    public IFluidHandler getFluidHandlerProxy() {
        return fluidHandlerProxy;
    }

    public void setPortMode(ConstructionPortMode portMode) {
        if (this.portMode == portMode) {
            return;
        }
        refreshKinetics();
        this.portMode = portMode;
        bridgedCapacity = 0;
        bridgedStress = 0;
        setChanged();
        sendData();
        if (getLevel() instanceof ServerLevel level) {
            level.invalidateCapabilities(getBlockPos());
            level.updateNeighborsAt(getBlockPos(), ModBlocks.CONSTRUCTION_INTERFACE.get());
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("PortMode", portMode.ordinal());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        portMode = ConstructionPortMode.fromId(tag.getInt("PortMode"));
    }

    @Nullable
    private ConstructionBlockEntity getHost() {
        return getLevel() instanceof ServerLevel level ? ConstructionRoomManager.findHost(level, getBlockPos()) : null;
    }

    @Nullable
    private Direction getPortDirection() {
        return getLevel() instanceof ServerLevel level ? ConstructionRoomManager.getPortDirection(level, getBlockPos()) : null;
    }

    @Nullable
    private IItemHandler resolveItemHandler() {
        ConstructionBlockEntity host = getHost();
        Direction direction = getPortDirection();
        return host == null || direction == null ? null : host.getInternalItemHandler(direction);
    }

    @Nullable
    private IFluidHandler resolveFluidHandler() {
        ConstructionBlockEntity host = getHost();
        Direction direction = getPortDirection();
        return host == null || direction == null ? null : host.getInternalFluidHandler(direction);
    }

    private final class InteriorItemHandlerProxy implements IItemHandler {
        @Override
        public int getSlots() {
            IItemHandler target = resolveItemHandler();
            return target == null ? 9 : target.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler target = resolveItemHandler();
            return target == null ? ItemStack.EMPTY : target.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            IItemHandler target = resolveItemHandler();
            return target == null ? stack : target.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler target = resolveItemHandler();
            return target == null ? ItemStack.EMPTY : target.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            IItemHandler target = resolveItemHandler();
            return target == null ? 64 : target.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            IItemHandler target = resolveItemHandler();
            return target != null && target.isItemValid(slot, stack);
        }
    }

    private final class InteriorFluidHandlerProxy implements IFluidHandler {
        @Override
        public int getTanks() {
            IFluidHandler target = resolveFluidHandler();
            return target == null ? 1 : target.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler target = resolveFluidHandler();
            return target == null ? FluidStack.EMPTY : target.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler target = resolveFluidHandler();
            return target == null ? 16_000 : target.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler target = resolveFluidHandler();
            return target != null && target.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler target = resolveFluidHandler();
            return target == null ? 0 : target.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler target = resolveFluidHandler();
            return target == null ? FluidStack.EMPTY : target.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler target = resolveFluidHandler();
            return target == null ? FluidStack.EMPTY : target.drain(maxDrain, action);
        }
    }
}
