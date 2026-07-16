package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.construction.ConstructionPortMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Draws a bright inset panel on a configured port face. */
public final class PortFaceOverlayRenderer {
    private static final ResourceLocation PANEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MOD_ID,
            "textures/misc/port_panel.png");
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float HALF_PANEL = 0.43F;
    private static final float FACE_OFFSET = 0.501F;

    private PortFaceOverlayRenderer() {}

    public static void render(Direction face, ConstructionPortMode mode, PoseStack poseStack,
            MultiBufferSource buffers, int packedOverlay) {
        if (mode == ConstructionPortMode.DISABLED) {
            return;
        }
        int color = mode.indicatorColor();
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(PANEL_TEXTURE));
        PoseStack.Pose pose = poseStack.last();

        float normalX = face.getStepX();
        float normalY = face.getStepY();
        float normalZ = face.getStepZ();
        float[] u = uAxis(face);
        float[] v = vAxis(face);
        float centerX = 0.5F + normalX * FACE_OFFSET;
        float centerY = 0.5F + normalY * FACE_OFFSET;
        float centerZ = 0.5F + normalZ * FACE_OFFSET;

        vertex(vertices, pose, centerX - u[0] * HALF_PANEL - v[0] * HALF_PANEL,
                centerY - u[1] * HALF_PANEL - v[1] * HALF_PANEL,
                centerZ - u[2] * HALF_PANEL - v[2] * HALF_PANEL,
                red, green, blue, 0.0F, 0.0F, packedOverlay, normalX, normalY, normalZ);
        vertex(vertices, pose, centerX + u[0] * HALF_PANEL - v[0] * HALF_PANEL,
                centerY + u[1] * HALF_PANEL - v[1] * HALF_PANEL,
                centerZ + u[2] * HALF_PANEL - v[2] * HALF_PANEL,
                red, green, blue, 1.0F, 0.0F, packedOverlay, normalX, normalY, normalZ);
        vertex(vertices, pose, centerX + u[0] * HALF_PANEL + v[0] * HALF_PANEL,
                centerY + u[1] * HALF_PANEL + v[1] * HALF_PANEL,
                centerZ + u[2] * HALF_PANEL + v[2] * HALF_PANEL,
                red, green, blue, 1.0F, 1.0F, packedOverlay, normalX, normalY, normalZ);
        vertex(vertices, pose, centerX - u[0] * HALF_PANEL + v[0] * HALF_PANEL,
                centerY - u[1] * HALF_PANEL + v[1] * HALF_PANEL,
                centerZ - u[2] * HALF_PANEL + v[2] * HALF_PANEL,
                red, green, blue, 0.0F, 1.0F, packedOverlay, normalX, normalY, normalZ);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, float x, float y, float z,
            int red, int green, int blue, float u, float v, int packedOverlay,
            float normalX, float normalY, float normalZ) {
        vertices.addVertex(pose.pose(), x, y, z)
                .setColor(red, green, blue, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay == 0 ? OverlayTexture.NO_OVERLAY : packedOverlay)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static float[] uAxis(Direction face) {
        return switch (face) {
            case DOWN, UP, SOUTH -> new float[] {1.0F, 0.0F, 0.0F};
            case NORTH -> new float[] {-1.0F, 0.0F, 0.0F};
            case WEST -> new float[] {0.0F, 0.0F, 1.0F};
            case EAST -> new float[] {0.0F, 0.0F, -1.0F};
        };
    }

    private static float[] vAxis(Direction face) {
        return switch (face) {
            case DOWN -> new float[] {0.0F, 0.0F, 1.0F};
            case UP -> new float[] {0.0F, 0.0F, -1.0F};
            default -> new float[] {0.0F, 1.0F, 0.0F};
        };
    }
}
