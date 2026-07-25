package com.example.examplemod.construction;

import com.example.examplemod.ExampleMod;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public final class ConstructionRoomData extends SavedData {
    private static final String DATA_NAME = ExampleMod.MOD_ID + "_construction_rooms";
    private final Map<UUID, RoomRecord> rooms = new HashMap<>();
    private final Map<Integer, UUID> roomIdsByIndex = new HashMap<>();
    private final TreeSet<Integer> freeRoomIndices = new TreeSet<>();
    private int nextRoomIndex;

    public static ConstructionRoomData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ConstructionRoomData::new, ConstructionRoomData::load), DATA_NAME);
    }

    private static ConstructionRoomData load(CompoundTag tag, HolderLookup.Provider registries) {
        ConstructionRoomData data = new ConstructionRoomData();
        data.nextRoomIndex = tag.getInt("NextRoomIndex");
        ListTag roomTags = tag.getList("Rooms", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < roomTags.size(); index++) {
            RoomRecord record = RoomRecord.fromTag(roomTags.getCompound(index));
            data.rooms.put(record.id(), record);
            data.roomIdsByIndex.put(roomIndex(record.origin()), record.id());
        }
        Set<Integer> usedIndices = new HashSet<>();
        data.rooms.values().forEach(record -> usedIndices.add(roomIndex(record.origin())));
        for (int index = 0; index < data.nextRoomIndex; index++) {
            if (!usedIndices.contains(index)) {
                data.freeRoomIndices.add(index);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("NextRoomIndex", nextRoomIndex);
        ListTag roomTags = new ListTag();
        rooms.values().forEach(record -> roomTags.add(record.toTag()));
        tag.put("Rooms", roomTags);
        return tag;
    }

    public RoomRecord create(ServerLevel hostLevel, BlockPos hostPos) {
        int index = freeRoomIndices.isEmpty() ? nextRoomIndex++ : freeRoomIndices.pollFirst();
        BlockPos immutableHostPos = hostPos.immutable();
        RoomRecord record = new RoomRecord(UUID.randomUUID(), hostLevel.dimension().location(), immutableHostPos,
                originForIndex(index), null, hostLevel.dimension().location(), immutableHostPos, false);
        rooms.put(record.id(), record);
        roomIdsByIndex.put(index, record.id());
        setDirty();
        return record;
    }

    public Optional<RoomRecord> get(UUID id) {
        return Optional.ofNullable(rooms.get(id));
    }

    public void setEntryReturn(UUID id, ResourceLocation dimension, BlockPos pos, boolean parentWorldPosition) {
        RoomRecord record = rooms.get(id);
        if (record != null) {
            rooms.put(id, record.withEntryReturn(dimension, pos, parentWorldPosition));
            setDirty();
        }
    }

    public Optional<RoomRecord> findInterior(BlockPos pos) {
        int gridX = Math.floorDiv(pos.getX(), 32);
        int gridZ = Math.floorDiv(pos.getZ(), 32);
        if (gridX < 0 || gridX >= 512 || gridZ < 0) {
            return Optional.empty();
        }
        UUID id = roomIdsByIndex.get(gridZ * 512 + gridX);
        RoomRecord record = id == null ? null : rooms.get(id);
        return record != null && record.contains(pos) ? Optional.of(record) : Optional.empty();
    }

    /**
     * Rooms whose host block sits inside {@code parent}'s region in the given
     * dimension. These are nested factories: destroying the parent must also
     * destroy them, because their host block is about to be cleared away and
     * the {@code onRemove} cascade is not reliable during a bulk block sweep.
     * Packed rooms are excluded — their host block is an item, not a block in
     * the region, so the parent's clear does not affect them.
     */
    public List<RoomRecord> roomsHostedInside(ResourceLocation hostDimension, RoomRecord parent) {
        return rooms.values().stream()
                .filter(record -> !record.id().equals(parent.id()))
                .filter(record -> !record.isPacked())
                .filter(record -> record.hostDimension().equals(hostDimension))
                .filter(record -> parent.contains(record.hostPos()))
                .toList();
    }

    public List<RoomRecord> records() {
        return List.copyOf(rooms.values());
    }

    /** Iterates the server-thread-owned room map without allocating a copy every tick. */
    public void forEachRecord(Consumer<RoomRecord> action) {
        rooms.values().forEach(action);
    }

    public int allocatedRoomSlots() {
        return nextRoomIndex;
    }

    public static BlockPos originForIndex(int index) {
        int x = Math.floorMod(index, 512) * 32;
        int z = Math.floorDiv(index, 512) * 32;
        return new BlockPos(x, 64, z);
    }

    public void remove(UUID id) {
        RoomRecord removed = rooms.remove(id);
        if (removed != null) {
            int index = roomIndex(removed.origin());
            roomIdsByIndex.remove(index);
            freeRoomIndices.add(index);
            setDirty();
        }
    }

    /**
     * Rebinds a deployed room after Sable moves its host block into or out of a
     * physics sub-level. The room identity and migration state are preserved.
     */
    public boolean relocateHost(UUID id, ServerLevel level, BlockPos pos) {
        RoomRecord record = rooms.get(id);
        if (record == null || record.isPacked()
                || !record.hostDimension().equals(level.dimension().location())) {
            return false;
        }
        rooms.put(id, record.withRelocatedHost(level, pos));
        setDirty();
        return true;
    }

    public Optional<UUID> beginMigration(UUID id, ServerLevel hostLevel, BlockPos hostPos) {
        RoomRecord record = rooms.get(id);
        if (record == null || record.isPacked() || !record.isHostedBy(hostLevel, hostPos)) {
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        rooms.put(id, record.withMigrationToken(token));
        setDirty();
        return Optional.of(token);
    }

    public boolean canClaim(UUID id, UUID token) {
        RoomRecord record = rooms.get(id);
        return record != null && token.equals(record.migrationToken());
    }

    public boolean claim(UUID id, UUID token, ServerLevel newHostLevel, BlockPos newHostPos) {
        RoomRecord record = rooms.get(id);
        if (record == null || !token.equals(record.migrationToken())) {
            return false;
        }
        rooms.put(id, record.withHost(newHostLevel, newHostPos));
        setDirty();
        return true;
    }

    public boolean restoreHost(UUID id, UUID token, ServerLevel hostLevel, BlockPos hostPos) {
        return claim(id, token, hostLevel, hostPos);
    }

    public boolean isHostedBy(UUID id, ServerLevel hostLevel, BlockPos hostPos) {
        RoomRecord record = rooms.get(id);
        return record != null && !record.isPacked() && record.isHostedBy(hostLevel, hostPos);
    }

    public Optional<RoomRecord> removeHosted(UUID id, ServerLevel hostLevel, BlockPos hostPos) {
        RoomRecord record = rooms.get(id);
        if (record == null || record.isPacked() || !record.isHostedBy(hostLevel, hostPos)) {
            return Optional.empty();
        }
        remove(id);
        return Optional.of(record);
    }

    public Optional<RoomRecord> removePacked(UUID id, UUID token) {
        RoomRecord record = rooms.get(id);
        if (record == null || !token.equals(record.migrationToken())) {
            return Optional.empty();
        }
        remove(id);
        return Optional.of(record);
    }

    private static int roomIndex(BlockPos origin) {
        return Math.floorDiv(origin.getZ(), 32) * 512 + Math.floorDiv(origin.getX(), 32);
    }

    public record RoomRecord(UUID id, ResourceLocation hostDimension, BlockPos hostPos, BlockPos origin,
                             @Nullable UUID migrationToken,
                             @Nullable ResourceLocation entryReturnDimension,
                             @Nullable BlockPos entryReturnPosition,
                             boolean entryReturnParentWorld) {
        /** Size of the outer shell (1-block thick walls on all sides). */
        public static final int OUTER_SIZE = 18;
        /** Size of the interior space (usable build area). */
        public static final int INNER_SIZE = 16;
        /** Y-level where the exit console is placed (2 blocks from origin, at player's standing height). */
        public static final int EXIT_CONSOLE_Y_OFFSET = 2;

        public boolean contains(BlockPos pos) {
            return pos.getX() >= origin.getX() && pos.getX() < origin.getX() + OUTER_SIZE
                    && pos.getY() >= origin.getY() && pos.getY() < origin.getY() + OUTER_SIZE
                    && pos.getZ() >= origin.getZ() && pos.getZ() < origin.getZ() + OUTER_SIZE;
        }

        public BlockPos socketPosition(net.minecraft.core.Direction direction) {
            return switch (direction) {
                case DOWN -> origin.offset(8, 0, 8);
                case UP -> origin.offset(8, 17, 8);
                case NORTH -> origin.offset(8, 9, 0);
                case SOUTH -> origin.offset(8, 9, 17);
                case WEST -> origin.offset(0, 9, 8);
                case EAST -> origin.offset(17, 9, 8);
            };
        }

        public boolean isPacked() {
            return migrationToken != null;
        }

        public boolean isHostedBy(ServerLevel level, BlockPos pos) {
            return !isPacked() && hostDimension.equals(level.dimension().location()) && hostPos.equals(pos);
        }

        RoomRecord withMigrationToken(UUID token) {
            return new RoomRecord(id, hostDimension, hostPos, origin, token, entryReturnDimension, entryReturnPosition,
                    entryReturnParentWorld);
        }

        RoomRecord withHost(ServerLevel level, BlockPos pos) {
            return new RoomRecord(id, level.dimension().location(), pos.immutable(), origin, null,
                    entryReturnDimension, entryReturnPosition, entryReturnParentWorld);
        }

        RoomRecord withRelocatedHost(ServerLevel level, BlockPos pos) {
            return new RoomRecord(id, level.dimension().location(), pos.immutable(), origin, migrationToken,
                    entryReturnDimension, entryReturnPosition, entryReturnParentWorld);
        }

        RoomRecord withEntryReturn(ResourceLocation dimension, BlockPos pos, boolean parentWorldPosition) {
            return new RoomRecord(id, hostDimension, hostPos, origin, migrationToken, dimension, pos.immutable(),
                    parentWorldPosition);
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("HostDimension", hostDimension.toString());
            tag.putInt("HostX", hostPos.getX());
            tag.putInt("HostY", hostPos.getY());
            tag.putInt("HostZ", hostPos.getZ());
            tag.putInt("OriginX", origin.getX());
            tag.putInt("OriginY", origin.getY());
            tag.putInt("OriginZ", origin.getZ());
            if (migrationToken != null) {
                tag.putUUID("MigrationToken", migrationToken);
            }
            if (entryReturnDimension != null) {
                tag.putString("EntryReturnDimension", entryReturnDimension.toString());
                tag.putInt("EntryReturnX", entryReturnPosition.getX());
                tag.putInt("EntryReturnY", entryReturnPosition.getY());
                tag.putInt("EntryReturnZ", entryReturnPosition.getZ());
                tag.putBoolean("EntryReturnParentWorld", entryReturnParentWorld);
            }
            return tag;
        }

        static RoomRecord fromTag(CompoundTag tag) {
            ResourceLocation entryReturnDimension = tag.contains("EntryReturnDimension")
                    ? ResourceLocation.parse(tag.getString("EntryReturnDimension")) : null;
            BlockPos entryReturnPosition = entryReturnDimension != null
                    ? new BlockPos(tag.getInt("EntryReturnX"), tag.getInt("EntryReturnY"), tag.getInt("EntryReturnZ"))
                    : null;
            return new RoomRecord(tag.getUUID("Id"), ResourceLocation.parse(tag.getString("HostDimension")),
                    new BlockPos(tag.getInt("HostX"), tag.getInt("HostY"), tag.getInt("HostZ")),
                    new BlockPos(tag.getInt("OriginX"), tag.getInt("OriginY"), tag.getInt("OriginZ")),
                    tag.hasUUID("MigrationToken") ? tag.getUUID("MigrationToken") : null,
                    entryReturnDimension, entryReturnPosition, tag.getBoolean("EntryReturnParentWorld"));
        }
    }
}
