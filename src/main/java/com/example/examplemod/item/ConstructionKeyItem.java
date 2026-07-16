package com.example.examplemod.item;

import com.example.examplemod.blockentity.ConstructionBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class ConstructionKeyItem extends Item {
    public ConstructionKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof ConstructionBlockEntity blockEntity)) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            blockEntity.tryDismantle(player);
        } else {
            blockEntity.enter(player);
        }
        return InteractionResult.SUCCESS;
    }
}
