package com.example.examplemod.construction;

import com.example.examplemod.blockentity.ConstructionBlockEntity;
import com.example.examplemod.registry.ModBlocks;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

public final class ConstructionRoomManager {
    private static final int ACTIVE_TICKET_LIFETIME_TICKS = 200;
    private static final int HOST_TICKET_RADIUS = 2;
    private static final int ROOM_TICKET_RADIUS = 2;
    private static final TicketType<UUID> HOST_TICKET = TicketType.create("examplemod_construction_host",
            Comparator.comparing(UUID::toString), ACTIVE_TICKET_LIFETIME_TICKS);
    private static final TicketType<UUID> ROOM_TICKET = TicketType.create("examplemod_construction_room",
            Comparator.comparing(UUID::toString), ACTIVE_TICKET_LIFETIME_TICKS);
    public static final ResourceKey<Level> CONSTRUCTION_DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("examplemod", "construction"));

    private ConstructionRoomManager() {}

    public static Optional<ConstructionRoomData.RoomRecord> get(MinecraftServer server, UUID id) {
        return ConstructionRoomData.get(server).get(id);
    }

    public static ConstructionRoomData.RoomRecord getOrCreate(ServerLevel hostLevel, BlockPos hostPos, UUID existingId) {
        ConstructionRoomData data = ConstructionRoomData.get(hostLevel.getServer());
        ConstructionRoomData.RoomRecord record = existingId == null ? data.create(hostLevel, hostPos)
                : data.get(existingId).filter(existing -> existing.isHostedBy(hostLevel, hostPos))
                        .orElseGet(() -> data.create(hostLevel, hostPos));
        ServerLevel roomLevel = hostLevel.getServer().getLevel(CONSTRUCTION_DIMENSION);
        if (roomLevel == null) {
            throw new IllegalStateException("Construction dimension is not available");
        }
        keepActive(hostLevel.getServer(), record);
        buildRoom(roomLevel, record);
        return record;
    }

    /**
     * Keeps both halves of every deployed factory ticking. This is deliberately
     * server-owned: a block entity cannot renew its own ticket after its host
     * chunk has already unloaded.
     */
    public static void tickActiveRooms(MinecraftServer server) {
        for (ConstructionRoomData.RoomRecord record : ConstructionRoomData.get(server).records()) {
            if (!record.isPacked()) {
                keepActive(server, record);
            }
        }
    }

    public static void enter(ServerPlayer player, ConstructionBlockEntity host) {
        ConstructionRoomData.RoomRecord record = getOrCreate((ServerLevel) host.getLevel(), host.getBlockPos(), host.getRoomId());
        host.setRoomId(record.id());
        host.syncRoomPortModes();
        // Retain the exact return area while a visitor is inside. Persistent
        // factory ticking itself is owned by tickActiveRooms, independently
        // of this temporary entry anchor.
        host.beginEntryAnchor(player);
        ServerLevel roomLevel = player.server.getLevel(CONSTRUCTION_DIMENSION);
        player.teleportTo(roomLevel, record.origin().getX() + 8.5D, record.origin().getY() + 1.1D,
                record.origin().getZ() + 8.5D, player.getYRot(), player.getXRot());
    }

    public static void exit(ServerPlayer player, BlockPos interiorPos) {
        ConstructionRoomData.RoomRecord record = ConstructionRoomData.get(player.server).findInterior(interiorPos)
                .orElse(null);
        if (record == null) {
            player.teleportTo(player.server.overworld(), player.server.overworld().getSharedSpawnPos().getX() + 0.5D,
                    player.server.overworld().getSharedSpawnPos().getY() + 1.0D,
                    player.server.overworld().getSharedSpawnPos().getZ() + 0.5D, player.getYRot(), player.getXRot());
            return;
        }

        ServerLevel hostLevel = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, record.hostDimension()));
        if (hostLevel == null) {
            player.displayClientMessage(Component.translatable("message.examplemod.construction.host_missing"), true);
            return;
        }
        BlockPos hostPos = record.hostPos();
        player.teleportTo(hostLevel, hostPos.getX() + 0.5D, hostPos.getY() + 1.1D, hostPos.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        if (hostLevel.getBlockEntity(hostPos) instanceof ConstructionBlockEntity host) {
            host.endEntryAnchorIfRoomEmpty();
        }
    }

    public static ConstructionBlockEntity findHost(ServerLevel interiorLevel, BlockPos interiorPos) {
        if (!interiorLevel.dimension().equals(CONSTRUCTION_DIMENSION)) {
            return null;
        }
        ConstructionRoomData.RoomRecord record = ConstructionRoomData.get(interiorLevel.getServer()).findInterior(interiorPos)
                .orElse(null);
        if (record == null) {
            return null;
        }
        ServerLevel hostLevel = interiorLevel.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, record.hostDimension()));
        if (hostLevel == null || record.isPacked()
                || !(hostLevel.getBlockEntity(record.hostPos()) instanceof ConstructionBlockEntity host)
                || !record.isHostedBy(hostLevel, host.getBlockPos())) {
            return null;
        }
        return host;
    }

    public static Optional<UUID> beginMigration(ServerLevel hostLevel, BlockPos hostPos, UUID roomId) {
        ConstructionRoomData data = ConstructionRoomData.get(hostLevel.getServer());
        ConstructionRoomData.RoomRecord record = data.get(roomId).orElse(null);
        Optional<UUID> token = data.beginMigration(roomId, hostLevel, hostPos);
        if (token.isPresent() && record != null) {
            releaseActiveTickets(hostLevel.getServer(), record);
        }
        return token;
    }

    public static boolean canClaim(MinecraftServer server, UUID roomId, UUID token) {
        return ConstructionRoomData.get(server).canClaim(roomId, token);
    }

    public static boolean claim(ServerLevel hostLevel, BlockPos hostPos, UUID roomId, UUID token) {
        ConstructionRoomData data = ConstructionRoomData.get(hostLevel.getServer());
        boolean claimed = data.claim(roomId, token, hostLevel, hostPos);
        if (claimed) {
            data.get(roomId).ifPresent(record -> keepActive(hostLevel.getServer(), record));
        }
        return claimed;
    }

    public static boolean restoreHost(ServerLevel hostLevel, BlockPos hostPos, UUID roomId, UUID token) {
        ConstructionRoomData data = ConstructionRoomData.get(hostLevel.getServer());
        boolean restored = data.restoreHost(roomId, token, hostLevel, hostPos);
        if (restored) {
            data.get(roomId).ifPresent(record -> keepActive(hostLevel.getServer(), record));
        }
        return restored;
    }

    public static boolean isHostedBy(ServerLevel hostLevel, BlockPos hostPos, UUID roomId) {
        return ConstructionRoomData.get(hostLevel.getServer()).isHostedBy(roomId, hostLevel, hostPos);
    }

    public static void destroyActiveRoom(ServerLevel hostLevel, BlockPos hostPos, UUID roomId) {
        ConstructionRoomData data = ConstructionRoomData.get(hostLevel.getServer());
        data.removeHosted(roomId, hostLevel, hostPos).ifPresent(record -> {
            releaseActiveTickets(hostLevel.getServer(), record);
            clearRoom(hostLevel.getServer(), record);
        });
    }

    public static void destroyPackedRoom(MinecraftServer server, UUID roomId, UUID token) {
        ConstructionRoomData data = ConstructionRoomData.get(server);
        data.removePacked(roomId, token).ifPresent(record -> {
            releaseActiveTickets(server, record);
            clearRoom(server, record);
        });
    }

    /** Removes rooms left behind by older versions that had no host-removal hook. */
    public static void cleanupOrphanedRooms(MinecraftServer server) {
        ConstructionRoomData data = ConstructionRoomData.get(server);
        for (ConstructionRoomData.RoomRecord record : data.records()) {
            if (record.isPacked()) {
                continue;
            }
            ServerLevel hostLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.hostDimension()));
            if (hostLevel != null) {
                // Startup cleanup must inspect the real host, not interpret an
                // unloaded chunk as a missing block entity.
                hostLevel.getChunkAt(record.hostPos());
            }
            boolean validHost = hostLevel != null
                    && hostLevel.getBlockEntity(record.hostPos()) instanceof ConstructionBlockEntity host
                    && record.id().equals(host.getRoomId());
            if (!validHost) {
                data.remove(record.id());
                releaseActiveTickets(server, record);
                clearRoom(server, record);
            } else {
                keepActive(server, record);
            }
        }
    }

    private static void keepActive(MinecraftServer server, ConstructionRoomData.RoomRecord record) {
        ServerLevel hostLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.hostDimension()));
        ServerLevel roomLevel = server.getLevel(CONSTRUCTION_DIMENSION);
        if (hostLevel != null) {
            hostLevel.getChunkSource().addRegionTicket(HOST_TICKET, new ChunkPos(record.hostPos()),
                    HOST_TICKET_RADIUS, record.id(), true);
        }
        if (roomLevel != null) {
            roomLevel.getChunkSource().addRegionTicket(ROOM_TICKET, new ChunkPos(record.origin()),
                    ROOM_TICKET_RADIUS, record.id(), true);
        }
    }

    private static void releaseActiveTickets(MinecraftServer server, ConstructionRoomData.RoomRecord record) {
        ServerLevel hostLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.hostDimension()));
        ServerLevel roomLevel = server.getLevel(CONSTRUCTION_DIMENSION);
        if (hostLevel != null) {
            hostLevel.getChunkSource().removeRegionTicket(HOST_TICKET, new ChunkPos(record.hostPos()),
                    HOST_TICKET_RADIUS, record.id(), true);
        }
        if (roomLevel != null) {
            roomLevel.getChunkSource().removeRegionTicket(ROOM_TICKET, new ChunkPos(record.origin()),
                    ROOM_TICKET_RADIUS, record.id(), true);
        }
    }

    public static Direction getPortDirection(ServerLevel interiorLevel, BlockPos interiorPos) {
        ConstructionRoomData.RoomRecord record = ConstructionRoomData.get(interiorLevel.getServer()).findInterior(interiorPos)
                .orElse(null);
        if (record == null) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            if (record.socketPosition(direction).equals(interiorPos)) {
                return direction;
            }
        }
        return null;
    }

    public static void clearRoom(MinecraftServer server, ConstructionRoomData.RoomRecord record) {
        ServerLevel level = server.getLevel(CONSTRUCTION_DIMENSION);
        if (level == null) {
            return;
        }
        BlockPos origin = record.origin();
        for (int x = 0; x < 18; x++) {
            for (int y = 0; y < 18; y++) {
                for (int z = 0; z < 18; z++) {
                    level.removeBlock(origin.offset(x, y, z), false);
                }
            }
        }
        // Removing blocks loads every room chunk and may itself spill container
        // contents, so clear entities afterwards to also catch those drops.
        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + 18, origin.getY() + 18, origin.getZ() + 18);
        level.getEntities((Entity) null, bounds, entity -> !(entity instanceof Player)).forEach(Entity::discard);
    }

    private static void buildRoom(ServerLevel level, ConstructionRoomData.RoomRecord record) {
        BlockPos origin = record.origin();
        if (level.getBlockState(origin).is(ModBlocks.CONSTRUCTION_SHELL.get())) {
            return;
        }

        for (int x = 0; x < 18; x++) {
            for (int y = 0; y < 18; y++) {
                for (int z = 0; z < 18; z++) {
                    if (x == 0 || x == 17 || y == 0 || y == 17 || z == 0 || z == 17) {
                        level.setBlock(origin.offset(x, y, z), ModBlocks.CONSTRUCTION_SHELL.get().defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        for (Direction direction : Direction.values()) {
            BlockPos socket = record.socketPosition(direction);
            level.setBlock(socket, ModBlocks.CONSTRUCTION_INTERFACE.get().defaultBlockState()
                    .setValue(com.example.examplemod.block.ConstructionInterfaceBlock.FACING, direction.getOpposite()), Block.UPDATE_ALL);
        }
        level.setBlock(origin.offset(8, 2, 0), ModBlocks.EXIT_CONSOLE.get().defaultBlockState(), Block.UPDATE_ALL);
    }
}
