package com.example.examplemod.client;

import com.example.examplemod.blockentity.ConstructionBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public final class ConstructionBlockPortRenderer implements BlockEntityRenderer<ConstructionBlockEntity> {
    public ConstructionBlockPortRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ConstructionBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        for (Direction direction : Direction.values()) {
            PortFaceOverlayRenderer.render(direction, blockEntity.getPortMode(direction), poseStack, buffers, packedOverlay);
        }
    }
}
