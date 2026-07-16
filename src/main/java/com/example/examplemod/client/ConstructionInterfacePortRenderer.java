package com.example.examplemod.client;

import com.example.examplemod.block.ConstructionInterfaceBlock;
import com.example.examplemod.blockentity.ConstructionInterfaceBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

public final class ConstructionInterfacePortRenderer implements BlockEntityRenderer<ConstructionInterfaceBlockEntity> {
    public ConstructionInterfacePortRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(ConstructionInterfaceBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction interiorFace = blockEntity.getBlockState().getValue(ConstructionInterfaceBlock.FACING);
        PortFaceOverlayRenderer.render(interiorFace, blockEntity.getPortMode(), poseStack, buffers, packedOverlay);
    }
}
