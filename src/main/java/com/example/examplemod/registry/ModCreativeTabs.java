package com.example.examplemod.registry;

import com.example.examplemod.ExampleMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExampleMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CONSTRUCTION = CREATIVE_TABS.register("construction",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.examplemod.construction"))
                    .icon(() -> ModItems.CONSTRUCTION_BLOCK.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CONSTRUCTION_BLOCK.get());
                        output.accept(ModItems.CONSTRUCTION_KEY.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
