package com.example.examplemod.registry;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.block.ConstructionBlock;
import com.example.examplemod.block.ConstructionInterfaceBlock;
import com.example.examplemod.block.ConstructionShellBlock;
import com.example.examplemod.block.ExitConsoleBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ExampleMod.MOD_ID);

    public static final DeferredBlock<ConstructionBlock> CONSTRUCTION_BLOCK = BLOCKS.register("construction_block",
            () -> new ConstructionBlock(BlockBehaviour.Properties.of().strength(5.0F, 3_600_000.0F).sound(SoundType.METAL)));
    public static final DeferredBlock<ConstructionShellBlock> CONSTRUCTION_SHELL = BLOCKS.register("construction_shell",
            () -> new ConstructionShellBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F).sound(SoundType.METAL)));
    public static final DeferredBlock<ConstructionInterfaceBlock> CONSTRUCTION_INTERFACE = BLOCKS.register("construction_interface",
            () -> new ConstructionInterfaceBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F).sound(SoundType.COPPER)));
    public static final DeferredBlock<ExitConsoleBlock> EXIT_CONSOLE = BLOCKS.register("exit_console",
            () -> new ExitConsoleBlock(BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F).sound(SoundType.METAL)));

    private ModBlocks() {}
}
