package com.example.examplemod;

import com.example.examplemod.event.CommonEvents;
import com.example.examplemod.registry.ModBlockEntities;
import com.example.examplemod.registry.ModBlocks;
import com.example.examplemod.registry.ModCreativeTabs;
import com.example.examplemod.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ExampleMod.MOD_ID)
public final class ExampleMod {
    public static final String MOD_ID = "examplemod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ExampleMod(IEventBus modBus) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTabs.CREATIVE_TABS.register(modBus);
        modBus.addListener(CommonEvents::registerCapabilities);
        NeoForge.EVENT_BUS.register(CommonEvents.class);
    }
}
