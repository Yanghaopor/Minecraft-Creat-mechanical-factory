package com.example.examplemod.block;

import com.example.examplemod.blockentity.ConstructionInterfaceBlockEntity;
import com.example.examplemod.construction.ConstructionRoomManager;
import com.example.examplemod.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

public final class ConstructionInterfaceBlock extends KineticBlock implements EntityBlock {
    public static final MapCodec<ConstructionInterfaceBlock> CODEC = simpleCodec(ConstructionInterfaceBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public ConstructionInterfaceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends KineticBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING)
                && level.getBlockEntity(pos) instanceof ConstructionInterfaceBlockEntity blockEntity
                && blockEntity.isKineticActive();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConstructionInterfaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.CONSTRUCTION_INTERFACE.get()
                ? (ignoredLevel, ignoredPos, ignoredState, blockEntity) -> ((ConstructionInterfaceBlockEntity) blockEntity).tick()
                : null;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (side.getOpposite() != state.getValue(FACING)) {
            return 0;
        }
        if (!(level instanceof Level mutableLevel) || mutableLevel.isClientSide) {
            return 0;
        }
        if (!(mutableLevel instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return 0;
        }
        var host = ConstructionRoomManager.findHost(serverLevel, pos);
        var port = ConstructionRoomManager.getPortDirection(serverLevel, pos);
        return host != null && port != null ? host.getInternalRedstoneSignal(port) : 0;
    }
}
