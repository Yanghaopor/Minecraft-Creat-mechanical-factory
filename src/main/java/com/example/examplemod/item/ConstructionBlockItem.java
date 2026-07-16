package com.example.examplemod.item;

import com.example.examplemod.blockentity.ConstructionBlockEntity;
import com.example.examplemod.construction.ConstructionRoomManager;
import com.example.examplemod.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ConstructionBlockItem extends BlockItem {
    private static final String MIGRATION_ROOM_ID = "MigrationRoomId";
    private static final String MIGRATION_TOKEN = "MigrationToken";
    private static final String BLOCK_ENTITY_ROOM_ID = "RoomId";

    public ConstructionBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ItemStack createMigrationStack(ConstructionBlockEntity blockEntity, UUID token) {
        ItemStack stack = new ItemStack(ModItems.CONSTRUCTION_BLOCK.get());
        blockEntity.saveToItem(stack, blockEntity.getLevel().registryAccess());
        CompoundTag identityTag = new CompoundTag();
        identityTag.putUUID(MIGRATION_ROOM_ID, blockEntity.getRoomId());
        identityTag.putUUID(MIGRATION_TOKEN, token);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(identityTag));
        return stack;
    }

    public static Optional<MigrationIdentity> getMigrationIdentity(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(MIGRATION_ROOM_ID) || !tag.hasUUID(MIGRATION_TOKEN)) {
            return Optional.empty();
        }
        return Optional.of(new MigrationIdentity(tag.getUUID(MIGRATION_ROOM_ID), tag.getUUID(MIGRATION_TOKEN)));
    }

    public static void destroyPackedRoom(ItemStack stack, ServerLevel level) {
        getMigrationIdentity(stack).ifPresent(identity -> ConstructionRoomManager.destroyPackedRoom(
                level.getServer(), identity.roomId(), identity.token()));
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        if (!super.canPlace(context, state)) {
            return false;
        }
        Optional<UUID> blockEntityRoomId = getBlockEntityRoomId(context.getItemInHand());
        Optional<MigrationIdentity> identity = getMigrationIdentity(context.getItemInHand());
        if (context.getLevel().isClientSide) {
            return true;
        }
        if (blockEntityRoomId.isEmpty()) {
            return identity.isEmpty();
        }
        return identity.filter(value -> value.roomId().equals(blockEntityRoomId.get()))
                .filter(value -> ConstructionRoomManager.canClaim(context.getLevel().getServer(),
                        value.roomId(), value.token()))
                .isPresent();
    }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player, ItemStack stack,
                                                   BlockState state) {
        Optional<MigrationIdentity> identity = getMigrationIdentity(stack);
        if (identity.isPresent() && level instanceof ServerLevel serverLevel) {
            MigrationIdentity value = identity.get();
            if (!ConstructionRoomManager.claim(serverLevel, pos, value.roomId(), value.token())) {
                return false;
            }
        }
        boolean loaded = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        if (identity.isPresent() && level.getBlockEntity(pos) instanceof ConstructionBlockEntity blockEntity) {
            blockEntity.setRoomId(identity.get().roomId());
        }
        return loaded;
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity, DamageSource damageSource) {
        if (itemEntity.level() instanceof ServerLevel level) {
            destroyPackedRoom(itemEntity.getItem(), level);
        }
        super.onDestroyed(itemEntity, damageSource);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel level
                && entity.getY() < level.getMinBuildHeight() - 32) {
            destroyPackedRoom(stack, level);
        }
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return getMigrationIdentity(stack).isPresent() || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        getMigrationIdentity(stack).ifPresent(identity -> tooltipComponents.add(
                Component.translatable("tooltip.examplemod.construction.migration")
                        .withStyle(ChatFormatting.GOLD)));
        if (getMigrationIdentity(stack).isPresent()) {
            tooltipComponents.add(Component.translatable("tooltip.examplemod.construction.migration_warning")
                    .withStyle(ChatFormatting.RED));
        }
    }

    private static Optional<UUID> getBlockEntityRoomId(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(BLOCK_ENTITY_ROOM_ID) ? Optional.of(tag.getUUID(BLOCK_ENTITY_ROOM_ID)) : Optional.empty();
    }

    public record MigrationIdentity(UUID roomId, UUID token) {}
}
