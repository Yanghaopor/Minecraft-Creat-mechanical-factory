package com.example.examplemod.event;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.block.ConstructionInterfaceBlock;
import com.example.examplemod.blockentity.ConstructionBlockEntity;
import com.example.examplemod.construction.ConstructionRoomManager;
import com.example.examplemod.item.ConstructionBlockItem;
import com.example.examplemod.registry.ModBlockEntities;
import com.example.examplemod.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CommonEvents {
    private CommonEvents() {}

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Capability sides are already the queried face on the construction
        // block (a funnel east of the block asks for EAST), matching port keys.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ModBlockEntities.CONSTRUCTION_BLOCK.get(),
                (blockEntity, side) -> side == null ? null : blockEntity.getExternalItemHandler(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.CONSTRUCTION_BLOCK.get(),
                (blockEntity, side) -> side == null ? null : blockEntity.getExternalFluidHandler(side));

        // Interior interfaces expose capabilities only on their room-facing side.
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, blockEntity, side) -> {
            if (!(level instanceof ServerLevel serverLevel) || side == null
                    || side != state.getValue(ConstructionInterfaceBlock.FACING)) {
                return null;
            }
            ConstructionBlockEntity host = ConstructionRoomManager.findHost(serverLevel, pos);
            Direction port = ConstructionRoomManager.getPortDirection(serverLevel, pos);
            return host == null || port == null ? null : host.getInternalItemHandler(port);
        }, ModBlocks.CONSTRUCTION_INTERFACE.get());
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, blockEntity, side) -> {
            if (!(level instanceof ServerLevel serverLevel) || side == null
                    || side != state.getValue(ConstructionInterfaceBlock.FACING)) {
                return null;
            }
            ConstructionBlockEntity host = ConstructionRoomManager.findHost(serverLevel, pos);
            Direction port = ConstructionRoomManager.getPortDirection(serverLevel, pos);
            return host == null || port == null ? null : host.getInternalFluidHandler(port);
        }, ModBlocks.CONSTRUCTION_INTERFACE.get());
    }

    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(ConstructionRoomManager.CONSTRUCTION_DIMENSION)) {
            return;
        }
        if (event.getSpawnType() == MobSpawnType.NATURAL
                || event.getSpawnType() == MobSpawnType.CHUNK_GENERATION
                || event.getSpawnType() == MobSpawnType.PATROL) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemExpire(ItemExpireEvent event) {
        if (event.getExtraLife() <= 0 && event.getEntity().level() instanceof ServerLevel level) {
            ConstructionBlockItem.destroyPackedRoom(event.getEntity().getItem(), level);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ConstructionRoomManager.cleanupOrphanedRooms(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        ConstructionRoomManager.tickActiveRooms(event.getServer());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getState().is(ModBlocks.CONSTRUCTION_SHELL.get())
                || event.getState().is(ModBlocks.CONSTRUCTION_INTERFACE.get())
                || event.getState().is(ModBlocks.EXIT_CONSOLE.get())) {
            event.setCanceled(true);
            return;
        }
        if (event.getState().is(ModBlocks.CONSTRUCTION_BLOCK.get())
                && event.getLevel().getBlockEntity(event.getPos()) instanceof ConstructionBlockEntity blockEntity
                && event.getLevel() instanceof ServerLevel serverLevel
                && !blockEntity.canDismantle(serverLevel)) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(net.minecraft.network.chat.Component.translatable("message.examplemod.construction.not_empty"), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level().dimension().equals(ConstructionRoomManager.CONSTRUCTION_DIMENSION)) {
            ConstructionRoomManager.exit(player, player.blockPosition());
        }
    }
}
