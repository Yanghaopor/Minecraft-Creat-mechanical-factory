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
        int x = Math.floorMod(index, 512) * 32;
        int z = Math.floorDiv(index, 512) * 32;
        RoomRecord record = new RoomRecord(UUID.randomUUID(), hostLevel.dimension().location(), hostPos.immutable(),
                new BlockPos(x, 64, z), null);
        rooms.put(record.id(), record);
        setDirty();
        return record;
    }

    public Optional<RoomRecord> get(UUID id) {
        return Optional.ofNullable(rooms.get(id));
    }

    public Optional<RoomRecord> findInterior(BlockPos pos) {
        return rooms.values().stream().filter(record -> record.contains(pos)).findFirst();
    }

    public List<RoomRecord> records() {
        return List.copyOf(rooms.values());
    }

    public void remove(UUID id) {
        RoomRecord removed = rooms.remove(id);
        if (removed != null) {
            freeRoomIndices.add(roomIndex(removed.origin()));
            setDirty();
        }
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
                             @Nullable UUID migrationToken) {
        private static final int OUTER_SIZE = 18;

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
            return new RoomRecord(id, hostDimension, hostPos, origin, token);
        }

        RoomRecord withHost(ServerLevel level, BlockPos pos) {
            return new RoomRecord(id, level.dimension().location(), pos.immutable(), origin, null);
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
            return tag;
        }

        static RoomRecord fromTag(CompoundTag tag) {
            return new RoomRecord(tag.getUUID("Id"), ResourceLocation.parse(tag.getString("HostDimension")),
                    new BlockPos(tag.getInt("HostX"), tag.getInt("HostY"), tag.getInt("HostZ")),
                    new BlockPos(tag.getInt("OriginX"), tag.getInt("OriginY"), tag.getInt("OriginZ")),
                    tag.hasUUID("MigrationToken") ? tag.getUUID("MigrationToken") : null);
        }
    }
}
