package com.example.examplemod.registry;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.item.ConstructionKeyItem;
import com.example.examplemod.item.ConstructionBlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ExampleMod.MOD_ID);

    public static final DeferredItem<ConstructionBlockItem> CONSTRUCTION_BLOCK = ITEMS.register("construction_block",
            () -> new ConstructionBlockItem(ModBlocks.CONSTRUCTION_BLOCK.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ConstructionKeyItem> CONSTRUCTION_KEY = ITEMS.register("construction_key",
            () -> new ConstructionKeyItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
