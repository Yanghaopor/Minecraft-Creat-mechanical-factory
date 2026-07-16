package com.example.examplemod.registry;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.blockentity.ConstructionBlockEntity;
import com.example.examplemod.blockentity.ConstructionInterfaceBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ExampleMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConstructionBlockEntity>> CONSTRUCTION_BLOCK =
            BLOCK_ENTITIES.register("construction_block", () -> BlockEntityType.Builder
                    .of(ConstructionBlockEntity::new, ModBlocks.CONSTRUCTION_BLOCK.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ConstructionInterfaceBlockEntity>> CONSTRUCTION_INTERFACE =
            BLOCK_ENTITIES.register("construction_interface", () -> BlockEntityType.Builder
                    .of(ConstructionInterfaceBlockEntity::new, ModBlocks.CONSTRUCTION_INTERFACE.get()).build(null));

    private ModBlockEntities() {}
}
