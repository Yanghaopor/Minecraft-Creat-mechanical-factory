package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.blockentity.ConstructionBlockEntity;
import com.example.examplemod.blockentity.ConstructionInterfaceBlockEntity;
import com.example.examplemod.construction.ConstructionPortMode;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Shows the selected construction port while aiming with Create's wrench. */
@EventBusSubscriber(modid = ExampleMod.MOD_ID, value = Dist.CLIENT)
public final class ConstructionPortHud {
    private static final int BACKGROUND_COLOR = 0xA0101010;

    private ConstructionPortHud() {}

    @SubscribeEvent
    public static void renderPortHint(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || minecraft.options.hideGui || !isHoldingWrench(minecraft.player.getMainHandItem(),
                        minecraft.player.getOffhandItem())
                || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockEntity blockEntity = minecraft.level.getBlockEntity(hit.getBlockPos());
        ConstructionPortMode mode;
        if (blockEntity instanceof ConstructionBlockEntity construction) {
            mode = construction.getPortMode(hit.getDirection());
        } else if (blockEntity instanceof ConstructionInterfaceBlockEntity constructionInterface) {
            mode = constructionInterface.getPortMode();
        } else {
            return;
        }

        Component modeName = Component.translatable(mode.translationKey());
        Component title = Component.translatable("hud.examplemod.construction.port_type", modeName);
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int width = font.width(title);
        int left = (graphics.guiWidth() - width) / 2;
        int top = graphics.guiHeight() - 69;
        graphics.fill(left - 5, top - 4, left + width + 5, top + 11, BACKGROUND_COLOR);
        int modeColor = mode == ConstructionPortMode.DISABLED ? 0xFFB0B0B0 : 0xFF000000 | mode.indicatorColor();
        graphics.drawString(font, title, left, top, modeColor, true);
    }

    private static boolean isHoldingWrench(ItemStack mainHand, ItemStack offHand) {
        return mainHand.getItem() instanceof WrenchItem || offHand.getItem() instanceof WrenchItem;
    }
}
