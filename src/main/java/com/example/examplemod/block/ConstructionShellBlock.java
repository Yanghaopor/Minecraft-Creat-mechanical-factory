package com.example.examplemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ConstructionShellBlock extends Block {
    public static final MapCodec<ConstructionShellBlock> CODEC = simpleCodec(ConstructionShellBlock::new);

    public ConstructionShellBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
