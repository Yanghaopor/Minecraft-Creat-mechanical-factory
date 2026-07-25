package com.example.examplemod.block;

import com.example.examplemod.blockentity.ConstructionBlockEntity;
import com.example.examplemod.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.simibubi.create.content.kinetics.base.KineticBlock;

public final class ConstructionBlock extends KineticBlock implements EntityBlock {
    public static final MapCodec<ConstructionBlock> CODEC = simpleCodec(ConstructionBlock::new);

    public ConstructionBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return level.getBlockEntity(pos) instanceof ConstructionBlockEntity blockEntity
                && blockEntity.hasExternalKineticConnection(face);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConstructionBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.CONSTRUCTION_BLOCK.get()
                ? (ignoredLevel, ignoredPos, ignoredState, blockEntity) -> ((ConstructionBlockEntity) blockEntity).tick()
                : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ConstructionBlockEntity blockEntity) {
            blockEntity.cyclePort(hitResult.getDirection());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        cyclePortFromWrench(context);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide
                && context.getPlayer() instanceof ServerPlayer player
                && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof ConstructionBlockEntity blockEntity) {
            blockEntity.tryPackForMigration(player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof ConstructionBlockEntity blockEntity) {
            blockEntity.onHostRemoved();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static void cyclePortFromWrench(UseOnContext context) {
        if (!context.getLevel().isClientSide
                && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof ConstructionBlockEntity blockEntity) {
            blockEntity.cyclePort(context.getClickedFace());
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof ConstructionBlockEntity blockEntity
                ? blockEntity.getExternalRedstoneSignal(side.getOpposite()) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return getSignal(state, level, pos, side);
    }

    // The construction block is a dedicated redstone I/O face, not a passive
    // conductor. The default (isRedstoneConductor == true for a full solid
    // block) makes SignalGetter#getSignal return max(own output, direct signal
    // received from any neighbour), so a strongly powered input (repeater, lever,
    // comparator, observer into an input face) leaks through the block as weak
    // power on every face and makes output ports read powered regardless of the
    // real interior/exterior signal. Returning false keeps getSignal to the
    // intended port output only; input reading is unaffected because it reads
    // the neighbour's emitted signal, not weak power through this block.
    @Override
    public boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side) {
        return false;
    }
}
