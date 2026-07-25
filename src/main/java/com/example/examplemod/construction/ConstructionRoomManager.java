package com.example.examplemod.construction;

import com.example.examplemod.blockentity.ConstructionBlockEntity;
import com.example.examplemod.compatibility.SableHostPosition;
import com.example.examplemod.registry.ModBlocks;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

public final class ConstructionRoomManager {
    private static final int ACTIVE_TICKET_LIFETIME_TICKS = 200;
    // Commands such as /fill bypass player-break events. Validate room hosts
    // once a minute to keep the fallback cleanup inexpensive on busy servers.
    private static final int ORPHAN_CLEANUP_INTERVAL_TICKS = 20 * 60;
    private static final int HOST_TICKET_RADIUS = 2;
    // A region ticket's distance is a ticket-strength offset, not the number
    // of surrounding chunks that receive the same ticking level. The room is
    // 18 blocks wide and starts on a chunk boundary, so it occupies a 2x2
    // chunk area. Distance 2 makes only the origin chunk entity-ticking and
    // downgrades the other room chunks to block-ticking after the player
    // leaves. That freezes item entities and Create contraptions and can stall
    // an otherwise running production line. Distance 3 keeps every chunk
    // touched by the room at ENTITY_TICKING or better.
    private static final int ROOM_TICKET_RADIUS = 3;
    private static final TicketType<UUID> HOST_TICKET = TicketType.create("examplemod_construction_host",
            Comparator.comparing(UUID::toString), ACTIVE_TICKET_LIFETIME_TICKS);
    private static final TicketType<UUID> ROOM_TICKET = TicketType.create("examplemod_construction_room",
            Comparator.comparing(UUID::toString), ACTIVE_TICKET_LIFETIME_TICKS);
    // Kept separate from the permanent deployed-factory tickets. This is renewed
    // only while a player is inside a room, so it can retain the exact entry
    // area without registering a fake player with physics-mod chunk tracking.
    private static final TicketType<UUID> ENTRY_ANCHOR_TICKET = TicketType.create("examplemod_construction_entry_anchor",
            Comparator.comparing(UUID::toString), ACTIVE_TICKET_LIFETIME_TICKS);
    /** Rooms currently paused by an UNLOAD port redstone signal — keepActive skips these. */
    private static final Set<UUID> UNLOADED_ROOMS = ConcurrentHashMap.newKeySet();
    /** Host tickets actually registered with vanilla's distance manager. */
    private static final Set<UUID> ACTIVE_HOST_REGION_TICKETS = ConcurrentHashMap.newKeySet();
    /** Rooms queued for unload ticket removal at the start of the next tick.
     *  Deferring prevents removeRegionTicket from firing inside the server's
     *  polling loop while DistanceManager is mid-update. */
    private static final Set<UUID> DEFERRED_UNLOADS = ConcurrentHashMap.newKeySet();
    /**
     * Cross-dimension entries which first need one complete server tick outside
     * a Sable sub-level. See {@link #enter(ServerPlayer, ConstructionBlockEntity)}.
     */
    private static final Map<UUID, PendingRoomEntry> PENDING_ROOM_ENTRIES = new HashMap<>();
    public static final ResourceKey<Level> CONSTRUCTION_DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("examplemod", "construction"));

    private ConstructionRoomManager() {}

    public static Optional<ConstructionRoomData.RoomRecord> get(MinecraftServer server, UUID id) {
        return ConstructionRoomData.get(server).get(id);
    }

    /** Renews the temporary chunk ticket that protects an occupied room's return area. */
    public static boolean renewEntryAnchor(ServerLevel hostLevel, BlockPos entryPos, UUID roomId) {
        // Sable owns chunk lifetime for its sub-level plots and explicitly
        // cancels vanilla addRegionTicket calls there. Do not later remove a
        // ticket which Sable intentionally never added.
        if (isSablePlotCoordinate(hostLevel, entryPos)) {
            return false;
        }
        hostLevel.resetEmptyTime();
        hostLevel.getChunkSource().addRegionTicket(ENTRY_ANCHOR_TICKET, new ChunkPos(entryPos),
                HOST_TICKET_RADIUS, roomId, true);
        return true;
    }

    /** Releases the temporary entry-area ticket once the final room visitor leaves. */
    public static void releaseEntryAnchor(ServerLevel hostLevel, BlockPos entryPos, UUID roomId) {
        hostLevel.getChunkSource().removeRegionTicket(ENTRY_ANCHOR_TICKET, new ChunkPos(entryPos),
                HOST_TICKET_RADIUS, roomId, true);
    }

    public static ConstructionRoomData.RoomRecord getOrCreate(ServerLevel hostLevel, BlockPos hostPos, UUID existingId) {
        ConstructionRoomData data = ConstructionRoomData.get(hostLevel.getServer());
        ConstructionRoomData.RoomRecord record = existingId == null ? null
                : data.get(existingId).filter(existing -> existing.isHostedBy(hostLevel, hostPos)).orElse(null);
        boolean created = record == null;
        if (created) {
            record = data.create(hostLevel, hostPos);
        }
        ServerLevel roomLevel = hostLevel.getServer().getLevel(CONSTRUCTION_DIMENSION);
        if (roomLevel == null) {
            throw new IllegalStateException("Construction dimension is not available");
        }
        keepActive(hostLevel.getServer(), record);
        if (created) {
            // A slot can be reused after its previous record was removed. Clear
            // any structure left by an interrupted/older cleanup before the new
            // room is built, otherwise buildRoom's shell check would adopt it.
            clearRoom(hostLevel.getServer(), record);
        }
        buildRoom(roomLevel, record);
        return record;
    }

    /**
     * Keeps both halves of every deployed factory ticking. This is deliberately
     * server-owned: a block entity cannot renew its own ticket after its host
     * chunk has already unloaded.
     */
    public static void tickActiveRooms(MinecraftServer server) {
        finishPendingRoomEntries(server);
        // Drain deferred unload requests from the previous tick.  Executing them
        // here — before any keepActive() call runs — guarantees removeRegionTicket
        // never fires while DistanceManager.runAllUpdates is in progress.
        if (!DEFERRED_UNLOADS.isEmpty()) {
            Set<UUID> toRelease = Set.copyOf(DEFERRED_UNLOADS);
            DEFERRED_UNLOADS.clear();
            for (UUID id : toRelease) {
                doReleaseUnload(server, id);
            }
        }

        if (server.getTickCount() % ORPHAN_CLEANUP_INTERVAL_TICKS == 0) {
            cleanupOrphanedRooms(server);
        }
        ConstructionRoomData.get(server).forEachRecord(record -> {
            if (!record.isPacked()) {
                keepActive(server, record);
            }
        });
    }

    /** Clears ticket and transfer bookkeeping that must never leak into a new server session. */
    public static void resetTransientState() {
        UNLOADED_ROOMS.clear();
        ACTIVE_HOST_REGION_TICKETS.clear();
        DEFERRED_UNLOADS.clear();
        PENDING_ROOM_ENTRIES.clear();
    }

    /** Drops a staged entry when the connection disappears before it completes. */
    public static void cancelPendingEntry(UUID playerId) {
        PENDING_ROOM_ENTRIES.remove(playerId);
    }

    /** True while an UNLOAD port has paused this room's interior. */
    public static boolean isRoomUnloaded(@Nullable UUID roomId) {
        return roomId != null && UNLOADED_ROOMS.contains(roomId);
    }

    private record PendingRoomEntry(UUID roomId, ResourceLocation sourceDimension, int readyAtTick) {}

    public static void enter(ServerPlayer player, ConstructionBlockEntity host) {
        // One click can be received again while a Sable-safe transfer is waiting
        // for its staging tick. It must not create a second pending teleport.
        if (PENDING_ROOM_ENTRIES.containsKey(player.getUUID())) {
            return;
        }
        ConstructionRoomData data = ConstructionRoomData.get(player.server);
        ConstructionRoomData.RoomRecord record = getOrCreate((ServerLevel) host.getLevel(), host.getBlockPos(), host.getRoomId());
        ServerLevel playerLevel = player.level() instanceof ServerLevel level ? level : null;
        Vec3 safeSableExit = playerLevel != null && ModList.get().isLoaded("sable")
                && SableHostPosition.isPlayerOnSubLevel(player)
                ? SableHostPosition.projectOutOfSubLevel(playerLevel, player.position()) : null;
        boolean needsSableStaging = safeSableExit != null
                && Double.isFinite(safeSableExit.x) && Double.isFinite(safeSableExit.y) && Double.isFinite(safeSableExit.z);
        // Remember where the player entered from. Physics mods such as Create: Aeronautics
        // may place the host block in a sub-level with extremely distant coordinates; the
        // saved entry point lets us return the player to a sane location on exit.
        BlockPos entryReturn = needsSableStaging ? BlockPos.containing(safeSableExit) : player.blockPosition().immutable();
        data.setEntryReturn(record.id(), player.level().dimension().location(), entryReturn, needsSableStaging);
        host.setRoomId(record.id());
        host.syncRoomPortModes();
        refreshTransferCapabilities(player.server, record);
        // Retain the exact return area while a visitor is inside. Persistent
        // factory ticking itself is owned by tickActiveRooms, independently
        // of this temporary entry anchor.
        host.beginEntryAnchor(player);
        if (needsSableStaging && playerLevel != null) {
            /*
             * Sable stores airship chunks in a separate plot grid. Moving a
             * player straight from that grid to another dimension can leave a
             * vanilla PLAYER ticket pointing at a plot ChunkHolder; after the
             * holder is removed, DistanceManager crashes with an empty
             * IllegalStateException. First move to the real parent-world point,
             * let one full tick settle the player ticket, then enter the room.
             */
            player.teleportTo(playerLevel, safeSableExit.x, safeSableExit.y, safeSableExit.z,
                    player.getYRot(), player.getXRot());
            SableHostPosition.clearPlayerSubLevelTracking(player);
            PENDING_ROOM_ENTRIES.put(player.getUUID(), new PendingRoomEntry(record.id(),
                    playerLevel.dimension().location(), player.server.getTickCount() + 2));
            return;
        }
        ServerLevel roomLevel = player.server.getLevel(CONSTRUCTION_DIMENSION);
        player.teleportTo(roomLevel, record.origin().getX() + 8.5D, record.origin().getY() + 1.1D,
                record.origin().getZ() + 8.5D, player.getYRot(), player.getXRot());
    }

    /** Completes a room entry only after Sable's outgoing player ticket settled. */
    private static void finishPendingRoomEntries(MinecraftServer server) {
        if (PENDING_ROOM_ENTRIES.isEmpty()) {
            return;
        }
        int currentTick = server.getTickCount();
        Iterator<Map.Entry<UUID, PendingRoomEntry>> iterator = PENDING_ROOM_ENTRIES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PendingRoomEntry> entry = iterator.next();
            PendingRoomEntry pending = entry.getValue();
            if (pending.readyAtTick() > currentTick) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.level().dimension().location().equals(pending.sourceDimension())) {
                continue;
            }
            ConstructionRoomData.RoomRecord record = ConstructionRoomData.get(server).get(pending.roomId()).orElse(null);
            ServerLevel roomLevel = server.getLevel(CONSTRUCTION_DIMENSION);
            if (record == null || record.isPacked() || roomLevel == null) {
                continue;
            }
            player.teleportTo(roomLevel, record.origin().getX() + 8.5D, record.origin().getY() + 1.1D,
                    record.origin().getZ() + 8.5D, player.getYRot(), player.getXRot());
        }
    }

    public static void exit(ServerPlayer player, BlockPos interiorPos) {
        ConstructionRoomData data = ConstructionRoomData.get(player.server);
        ConstructionRoomData.RoomRecord record = data.findInterior(interiorPos).orElse(null);
        if (record == null) {
            player.teleportTo(player.server.overworld(), player.server.overworld().getSharedSpawnPos().getX() + 0.5D,
                    player.server.overworld().getSharedSpawnPos().getY() + 1.0D,
                    player.server.overworld().getSharedSpawnPos().getZ() + 0.5D, player.getYRot(), player.getXRot());
            return;
        }

        ServerLevel hostLevel = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, record.hostDimension()));
        BlockPos hostPos = record.hostPos();

        // A factory deployed onto a physics platform (a Sable-driven Create: Aeronautics
        // airship, for example) keeps a stable BlockPos inside the platform's moving
        // sub-level, while its real parent-world position changes every tick. Project
        // that stored BlockPos through the sub-level's current transform to find where
        // the host block actually is right now, so the player steps out onto the ship
        // wherever it has flown instead of where it was when they entered. Sable returns
        // the position unchanged when it is not inside any sub-level, so ordinary
        // factories keep their exact host-block exit point. The resolver class is only
        // class-loaded after the ModList guard, so Sable stays a soft dependency.
        if (hostLevel != null) {
            Vec3 currentHostPoint = resolveExitPoint(hostLevel, hostPos, false);
            if (currentHostPoint != null) {
                player.teleportTo(hostLevel, currentHostPoint.x, currentHostPoint.y, currentHostPoint.z,
                        player.getYRot(), player.getXRot());
                finishExitTransfer(player, hostLevel, hostPos, record);
                return;
            }
        }

        // Sable is absent, the host dimension is missing, or projection failed: the
        // stored host BlockPos may be a far-away sub-level coordinate whose direct
        // teleport crashes the physics engine (enormous entity AABB), so fall back to
        // the saved entry point, then world spawn. A missing host dimension (platform
        // disassembled/unloaded) used to strand the player inside the construction
        // dimension; it now falls through here instead.
        BlockPos fallbackPos = record.entryReturnPosition();
        ResourceLocation fallbackDim = record.entryReturnDimension();
        if (fallbackPos != null && fallbackDim != null) {
            ServerLevel fallbackLevel = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, fallbackDim));
            Vec3 fallbackPoint = fallbackLevel == null ? null
                    : resolveExitPoint(fallbackLevel, fallbackPos, record.entryReturnParentWorld());
            if (fallbackLevel != null && fallbackPoint != null) {
                player.displayClientMessage(Component.translatable("message.examplemod.construction.host_relocated"), true);
                player.teleportTo(fallbackLevel, fallbackPoint.x, fallbackPoint.y, fallbackPoint.z,
                        player.getYRot(), player.getXRot());
                finishExitTransfer(player, hostLevel, hostPos, record);
                return;
            }
        }

        player.displayClientMessage(Component.translatable("message.examplemod.construction.host_unreachable"), true);
        ServerLevel spawnLevel = player.server.overworld();
        BlockPos spawn = spawnLevel.getSharedSpawnPos();
        player.teleportTo(spawnLevel, spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        finishExitTransfer(player, hostLevel, hostPos, record);
    }

    @Nullable
    private static Vec3 resolveExitPoint(ServerLevel level, BlockPos pos, boolean parentWorldPosition) {
        Vec3 ordinaryPoint = new Vec3(pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D);
        if (!ModList.get().isLoaded("sable") || parentWorldPosition) {
            return ordinaryPoint;
        }
        if (!SableHostPosition.isInPlotGrid(level, pos)) {
            return ordinaryPoint;
        }
        if (!SableHostPosition.isInSubLevel(level, pos)) {
            return null;
        }
        Vec3 projected = SableHostPosition.projectOutOfSubLevel(level, ordinaryPoint);
        return projected != null
                && Double.isFinite(projected.x) && Double.isFinite(projected.y) && Double.isFinite(projected.z)
                ? projected : null;
    }

    private static void finishExitTransfer(ServerPlayer player, @Nullable ServerLevel hostLevel,
                                           BlockPos hostPos, ConstructionRoomData.RoomRecord record) {
        if (hostLevel != null && !isAbandonedPlot(hostLevel, hostPos)
                && getLoadedBlockEntity(hostLevel, hostPos) instanceof ConstructionBlockEntity host) {
            host.endEntryAnchorIfRoomEmpty();
        }
        if (ModList.get().isLoaded("sable")) {
            // A cross-dimension teleport leaves Sable's server-side sub-level
            // tracking latch untouched, so it never re-sends the add/movement
            // stream and the client keeps stale (or no) tracking: the physical
            // structure stops responding to punches until an unrelated re-sync
            // minutes later. Clearing the latch forces Sable to rebuild
            // tracking for the player's new position on the next tick.
            SableHostPosition.clearPlayerSubLevelTracking(player);
        }
        refreshTransferCapabilities(player.server, record);
    }

    private static void refreshTransferCapabilities(MinecraftServer server, ConstructionRoomData.RoomRecord record) {
        ServerLevel hostLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.hostDimension()));
        ServerLevel roomLevel = server.getLevel(CONSTRUCTION_DIMENSION);
        if (hostLevel != null && !isAbandonedPlot(hostLevel, record.hostPos())
                && getLoadedBlockEntity(hostLevel, record.hostPos()) instanceof ConstructionBlockEntity) {
            hostLevel.invalidateCapabilities(record.hostPos());
            hostLevel.updateNeighborsAt(record.hostPos(), ModBlocks.CONSTRUCTION_BLOCK.get());
        }
        if (roomLevel != null) {
            for (Direction direction : Direction.values()) {
                BlockPos socket = record.socketPosition(direction);
                roomLevel.invalidateCapabilities(socket);
                roomLevel.updateNeighborsAt(socket, ModBlocks.CONSTRUCTION_INTERFACE.get());
            }
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
        if (hostLevel == null || record.isPacked() || isAbandonedPlot(hostLevel, record.hostPos())
                || !(getLoadedBlockEntity(hostLevel, record.hostPos()) instanceof ConstructionBlockEntity host)
                || host.isRoomUnloaded()
                || !record.isHostedBy(hostLevel, host.getBlockPos())) {
            return null;
        }
        return host;
    }

    /**
     * Updates a room record when Sable assembles or disassembles the physical
     * structure containing its host block. That operation moves the same block
     * entity between parent-world and plot-grid coordinates without going
     * through the normal migration-item flow.
     */
    public static Optional<ConstructionRoomData.RoomRecord> rebindMovedHost(
            ServerLevel hostLevel, ConstructionBlockEntity host) {
        UUID roomId = host.getRoomId();
        if (roomId == null) {
            return Optional.empty();
        }
        ConstructionRoomData data = ConstructionRoomData.get(hostLevel.getServer());
        ConstructionRoomData.RoomRecord record = data.get(roomId).orElse(null);
        if (record == null || record.isPacked()
                || !record.hostDimension().equals(hostLevel.dimension().location())) {
            return Optional.empty();
        }
        BlockPos currentPos = host.getBlockPos();
        if (record.isHostedBy(hostLevel, currentPos)) {
            return Optional.of(record);
        }
        if (!isSablePlotCoordinate(hostLevel, currentPos)
                && !isSablePlotCoordinate(hostLevel, record.hostPos())) {
            return Optional.empty();
        }

        BlockEntity previousHost = isAbandonedPlot(hostLevel, record.hostPos())
                ? null : getLoadedBlockEntity(hostLevel, record.hostPos());
        if (previousHost instanceof ConstructionBlockEntity other && other != host
                && roomId.equals(other.getRoomId())) {
            return Optional.empty();
        }
        if (ACTIVE_HOST_REGION_TICKETS.remove(roomId)
                && !isSablePlotCoordinate(hostLevel, record.hostPos())) {
            hostLevel.getChunkSource().removeRegionTicket(HOST_TICKET, new ChunkPos(record.hostPos()),
                    HOST_TICKET_RADIUS, roomId, true);
        }
        if (!data.relocateHost(roomId, hostLevel, currentPos)) {
            return Optional.empty();
        }
        ConstructionRoomData.RoomRecord relocated = data.get(roomId).orElseThrow();
        keepActive(hostLevel.getServer(), relocated);
        return Optional.of(relocated);
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
            UNLOADED_ROOMS.remove(record.id());
            DEFERRED_UNLOADS.remove(record.id());
            releaseActiveTickets(hostLevel.getServer(), record);
            destroyRoomAndChildren(hostLevel.getServer(), data, record);
        });
    }

    public static void destroyPackedRoom(MinecraftServer server, UUID roomId, UUID token) {
        ConstructionRoomData data = ConstructionRoomData.get(server);
        data.removePacked(roomId, token).ifPresent(record -> {
            UNLOADED_ROOMS.remove(record.id());
            DEFERRED_UNLOADS.remove(record.id());
            releaseActiveTickets(server, record);
            destroyRoomAndChildren(server, data, record);
        });
    }

    /**
     * Destroys a room together with every factory nested inside it, to any
     * depth. Nested hosts live in the construction dimension; their block is
     * about to be cleared away with the parent, so their own rooms would
     * otherwise be orphaned. We remove each child's record and tickets first,
     * recurse into its children, and only then clear the parent region — so
     * clearing the parent's blocks never has to rely on a per-block onRemove
     * cascade to reach the nested rooms.
     */
    private static void destroyRoomAndChildren(MinecraftServer server, ConstructionRoomData data,
                                               ConstructionRoomData.RoomRecord record) {
        for (ConstructionRoomData.RoomRecord child
                : data.roomsHostedInside(CONSTRUCTION_DIMENSION.location(), record)) {
            data.remove(child.id());
            releaseActiveTickets(server, child);
            destroyRoomAndChildren(server, data, child);
        }
        clearRoom(server, record);
    }

    /** Removes rooms left behind by older versions that had no host-removal hook. */
    public static void cleanupOrphanedRooms(MinecraftServer server) {
        ConstructionRoomData data = ConstructionRoomData.get(server);
        cleanupOrphanedHosts(server, data);
    }

    /**
     * Validates active room hosts independently of player break events. This
     * catches command replacements (for example {@code /fill ... air}) even
     * if another removal path did not invoke the block's cleanup hook.
     */
    private static void cleanupOrphanedHosts(MinecraftServer server, ConstructionRoomData data) {
        for (ConstructionRoomData.RoomRecord record : data.records()) {
            if (record.isPacked()) {
                continue;
            }
            ServerLevel hostLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.hostDimension()));
            if (hostLevel == null || isAbandonedPlot(hostLevel, record.hostPos())) {
                // A physics structure may be temporarily absent while Sable is
                // loading it. Never turn that transient state into data loss.
                continue;
            }
            BlockEntity blockEntity = getLoadedBlockEntity(hostLevel, record.hostPos());
            if (blockEntity == null) {
                // Cleanup must not force-load ordinary chunks or private Sable
                // plots. The normal block onRemove hook handles real removals.
                continue;
            }
            boolean validHost = blockEntity instanceof ConstructionBlockEntity host
                    && record.id().equals(host.getRoomId());
            if (!validHost) {
                // A record removed earlier as another room's nested child is
                // already gone; skip it so we neither double-clear nor treat a
                // freed slot as a fresh orphan.
                if (data.get(record.id()).isEmpty()) {
                    continue;
                }
                UNLOADED_ROOMS.remove(record.id());
                DEFERRED_UNLOADS.remove(record.id());
                data.remove(record.id());
                releaseActiveTickets(server, record);
                destroyRoomAndChildren(server, data, record);
            } else {
                keepActive(server, record);
            }
        }
    }

    private static void keepActive(MinecraftServer server, ConstructionRoomData.RoomRecord record) {
        // Skip rooms paused by an UNLOAD port — their ROOM_TICKET was released by
        // the host BE's tick and must stay released until the signal disappears.
        ServerLevel hostLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.hostDimension()));
        ServerLevel roomLevel = server.getLevel(CONSTRUCTION_DIMENSION);
        boolean hostReportsPaused = false;
        if (hostLevel != null) {
            boolean sablePlot = isSablePlotCoordinate(hostLevel, record.hostPos());
            // The paused state is stored on the host block entity. Rehydrate the
            // in-memory set from it before renewing any room ticket so a server
            // restart cannot briefly wake an UNLOAD-paused factory.
            BlockEntity hostEntity = sablePlot && !SableHostPosition.isInSubLevel(hostLevel, record.hostPos())
                    ? null : getLoadedBlockEntity(hostLevel, record.hostPos());
            hostReportsPaused = hostEntity
                    instanceof ConstructionBlockEntity host && host.isRoomUnloaded();
            if (hostReportsPaused) {
                UNLOADED_ROOMS.add(record.id());
            }
            // Region tickets keep chunks loaded/ticking, but they do not count
            // as vanilla/NeoForge forced chunks for ServerLevel's empty-world
            // guard. With no real player in this dimension, ServerLevel stops
            // ticking every entity and block entity after 300 ticks even while
            // the chunks still report as entity-ticking. A deployed factory is
            // active server work, so prevent its host dimension from sleeping.
            hostLevel.resetEmptyTime();
            if (sablePlot) {
                // Sable's ServerChunkCache cancels addRegionTicket for every
                // plot coordinate, including an abandoned plot. Never add or
                // remove a vanilla ticket there: removeRegionTicket is not
                // cancelled by Sable and can corrupt DistanceManager state.
                ACTIVE_HOST_REGION_TICKETS.remove(record.id());
            } else {
                hostLevel.getChunkSource().addRegionTicket(HOST_TICKET, new ChunkPos(record.hostPos()),
                        HOST_TICKET_RADIUS, record.id(), true);
                ACTIVE_HOST_REGION_TICKETS.add(record.id());
            }
        }
        // Keep the exterior host alive so it can notice when the UNLOAD signal
        // turns off; only its interior room ticket is suspended.
        if (hostReportsPaused || UNLOADED_ROOMS.contains(record.id())) {
            return;
        }
        if (roomLevel != null) {
            // This is the critical half when the last visitor exits the room:
            // Create funnels and machines are block entities and otherwise stop
            // exactly 300 ticks later despite the room ticket remaining active.
            roomLevel.resetEmptyTime();
            roomLevel.getChunkSource().addRegionTicket(ROOM_TICKET, new ChunkPos(record.origin()),
                    ROOM_TICKET_RADIUS, record.id(), true);
        }
    }

    private static void releaseActiveTickets(MinecraftServer server, ConstructionRoomData.RoomRecord record) {
        ServerLevel hostLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, record.hostDimension()));
        ServerLevel roomLevel = server.getLevel(CONSTRUCTION_DIMENSION);
        if (hostLevel != null && ACTIVE_HOST_REGION_TICKETS.remove(record.id())) {
            hostLevel.getChunkSource().removeRegionTicket(HOST_TICKET, new ChunkPos(record.hostPos()),
                    HOST_TICKET_RADIUS, record.id(), true);
        }
        if (roomLevel != null) {
            roomLevel.getChunkSource().removeRegionTicket(ROOM_TICKET, new ChunkPos(record.origin()),
                    ROOM_TICKET_RADIUS, record.id(), true);
        }
    }

    /**
     * Releases only the room-dimension tickets to pause a factory. The host
     * dimension ticket is kept so the host block can still detect redstone
     * signals while the room is unloaded.
     */
    public static void releaseActiveTicketsForUnload(@Nullable UUID roomId, MinecraftServer server) {
        if (roomId == null) {
            return;
        }
        // Mark the room as unloaded so keepActive() skips it during this tick's
        // tickActiveRooms call.  The actual ticket removal is deferred to the
        // start of the next tick to avoid firing inside DistanceManager.runAllUpdates.
        UNLOADED_ROOMS.add(roomId);
        DEFERRED_UNLOADS.add(roomId);
    }

    /**
     * Performs the actual ROOM_TICKET removal for a deferred unload request.
     * Called only from tickActiveRooms, which runs before DistanceManager is
     * locked inside the polling loop.
     */
    private static void doReleaseUnload(MinecraftServer server, UUID roomId) {
        ConstructionRoomData.get(server).get(roomId).ifPresent(record -> {
            ServerLevel roomLevel = server.getLevel(CONSTRUCTION_DIMENSION);
            if (roomLevel != null) {
                roomLevel.getChunkSource().removeRegionTicket(ROOM_TICKET, new ChunkPos(record.origin()),
                        ROOM_TICKET_RADIUS, record.id(), true);
            }
        });
    }

    /**
     * Restores room-dimension tickets after an UNLOAD signal is removed.
     */
    public static void restoreActiveTicketsForUnload(@Nullable UUID roomId, MinecraftServer server) {
        if (roomId == null) {
            return;
        }
        UNLOADED_ROOMS.remove(roomId);
        ConstructionRoomData.get(server).get(roomId).ifPresent(record -> {
            ServerLevel roomLevel = server.getLevel(CONSTRUCTION_DIMENSION);
            if (roomLevel != null) {
                roomLevel.resetEmptyTime();
                roomLevel.getChunkSource().addRegionTicket(ROOM_TICKET, new ChunkPos(record.origin()),
                        ROOM_TICKET_RADIUS, record.id(), true);
            }
        });
    }

    private static boolean isSablePlotCoordinate(ServerLevel level, BlockPos pos) {
        return ModList.get().isLoaded("sable") && SableHostPosition.isInPlotGrid(level, pos);
    }

    private static boolean isAbandonedPlot(ServerLevel level, BlockPos pos) {
        return isSablePlotCoordinate(level, pos) && !SableHostPosition.isInSubLevel(level, pos);
    }

    /** Reads a block entity only when its chunk is already resident. */
    @Nullable
    private static BlockEntity getLoadedBlockEntity(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
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
        clearRoomAt(level, record.origin());
    }

    private static void clearRoomAt(ServerLevel level, BlockPos origin) {
        int clearFlags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (int x = 0; x < ConstructionRoomData.RoomRecord.OUTER_SIZE; x++) {
            for (int y = 0; y < ConstructionRoomData.RoomRecord.OUTER_SIZE; y++) {
                for (int z = 0; z < ConstructionRoomData.RoomRecord.OUTER_SIZE; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), clearFlags);
                    }
                }
            }
        }
        // Clear non-player entities left by machines or older cleanup versions.
        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + ConstructionRoomData.RoomRecord.OUTER_SIZE,
                origin.getY() + ConstructionRoomData.RoomRecord.OUTER_SIZE,
                origin.getZ() + ConstructionRoomData.RoomRecord.OUTER_SIZE);
        level.getEntities((Entity) null, bounds, entity -> !(entity instanceof Player)).forEach(Entity::discard);
    }

    private static void buildRoom(ServerLevel level, ConstructionRoomData.RoomRecord record) {
        BlockPos origin = record.origin();
        if (level.getBlockState(origin).is(ModBlocks.CONSTRUCTION_SHELL.get())) {
            return;
        }

        int shellFlags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int x = 0; x < ConstructionRoomData.RoomRecord.OUTER_SIZE; x++) {
            for (int y = 0; y < ConstructionRoomData.RoomRecord.OUTER_SIZE; y++) {
                for (int z = 0; z < ConstructionRoomData.RoomRecord.OUTER_SIZE; z++) {
                    if (x == 0 || x == ConstructionRoomData.RoomRecord.OUTER_SIZE - 1
                            || y == 0 || y == ConstructionRoomData.RoomRecord.OUTER_SIZE - 1
                            || z == 0 || z == ConstructionRoomData.RoomRecord.OUTER_SIZE - 1) {
                        level.setBlock(origin.offset(x, y, z),
                                ModBlocks.CONSTRUCTION_SHELL.get().defaultBlockState(), shellFlags);
                    }
                }
            }
        }
        for (Direction direction : Direction.values()) {
            BlockPos socket = record.socketPosition(direction);
            level.setBlock(socket, ModBlocks.CONSTRUCTION_INTERFACE.get().defaultBlockState()
                    .setValue(com.example.examplemod.block.ConstructionInterfaceBlock.FACING, direction.getOpposite()), Block.UPDATE_ALL);
        }
        level.setBlock(origin.offset(8, ConstructionRoomData.RoomRecord.EXIT_CONSOLE_Y_OFFSET, 0),
                ModBlocks.EXIT_CONSOLE.get().defaultBlockState(), Block.UPDATE_ALL);
    }
}
