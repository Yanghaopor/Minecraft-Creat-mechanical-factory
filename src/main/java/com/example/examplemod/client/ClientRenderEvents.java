package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ExampleMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientRenderEvents {
    private ClientRenderEvents() {}

    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CONSTRUCTION_BLOCK.get(), ConstructionBlockPortRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CONSTRUCTION_INTERFACE.get(), ConstructionInterfacePortRenderer::new);
    }
}
