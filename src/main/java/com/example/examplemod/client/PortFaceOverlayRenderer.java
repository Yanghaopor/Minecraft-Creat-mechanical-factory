package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.blockentity.ConstructionBlockEntity;
import com.example.examplemod.construction.ConstructionPortMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Renders the factory's port indicators without tinting the face texture.
 */
public final class PortFaceOverlayRenderer {
    private static final ResourceLocation LED_TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MOD_ID,
            "textures/misc/port_led.png");
    private static final ResourceLocation KINETIC_INPUT_PANEL = ResourceLocation.fromNamespaceAndPath(ExampleMod.MOD_ID,
            "textures/block/construction_block_kinetic_input.png");
    private static final ResourceLocation KINETIC_OUTPUT_PANEL = ResourceLocation.fromNamespaceAndPath(ExampleMod.MOD_ID,
            "textures/block/construction_block_kinetic_output.png");
    private static final ResourceLocation ITEM_INPUT_PANEL = ResourceLocation.fromNamespaceAndPath(ExampleMod.MOD_ID,
            "textures/block/construction_block_item_input.png");
    private static final ResourceLocation ITEM_OUTPUT_PANEL = ResourceLocation.fromNamespaceAndPath(ExampleMod.MOD_ID,
            "textures/block/construction_block_item_output.png");
    private static final ResourceLocation FLUID_INPUT_PANEL = ResourceLocation.fromNamespaceAndPath(ExampleMod.MOD_ID,
            "textures/block/construction_block_fluid_input.png");
    private static final ResourceLocation FLUID_OUTPUT_PANEL = ResourceLocation.fromNamespaceAndPath(ExampleMod.MOD_ID,
            "textures/block/construction_block_fluid_output.png");
    private static final ResourceLocation REDSTONE_INPUT_PANEL = ResourceLocation.fromNamespaceAndPath(ExampleMod.MOD_ID,
            "textures/block/construction_block_redstone_input.png");
    private static final ResourceLocation REDSTONE_OUTPUT_PANEL = ResourceLocation.fromNamespaceAndPath(ExampleMod.MOD_ID,
            "textures/block/construction_block_redstone_output.png");
    private static final ResourceLocation UNLOAD_PANEL = ResourceLocation.fromNamespaceAndPath(ExampleMod.MOD_ID,
            "textures/block/construction_block_unload.png");
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final float[] X_AXIS = {1.0F, 0.0F, 0.0F};
    private static final float[] Y_AXIS = {0.0F, 1.0F, 0.0F};
    private static final float[] Z_AXIS = {0.0F, 0.0F, 1.0F};
    private static final float LED_INSET = 0.28F;
    private static final float LED_HALF = 0.085F;
    private static final float PANEL_HALF = 0.5F;
    private static final float FACE_OFFSET = 0.502F;
    // The 32px construction-block texture reserves a 4px LED block at each
    // physical vertex. Offset the emitter from the face centre into that block.
    private static final float CORNER_CENTER_OFFSET = 0.44F;
    private static final float CORNER_HALF = 0.055F;

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
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(LED_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        drawFaceCornerLeds(vertices, pose, face, red, green, blue, LED_INSET, LED_HALF, packedOverlay);
    }

    /** Renders the atlas-backed kinetic panel over the configured exterior face. */
    public static void renderStatusPanel(Direction face, ConstructionPortMode mode, PoseStack poseStack,
            MultiBufferSource buffers, int packedOverlay) {
        ResourceLocation panelTexture = switch (mode) {
            case KINETIC_INPUT -> KINETIC_INPUT_PANEL;
            case KINETIC_OUTPUT -> KINETIC_OUTPUT_PANEL;
            case ITEM_INPUT -> ITEM_INPUT_PANEL;
            case ITEM_OUTPUT -> ITEM_OUTPUT_PANEL;
            case FLUID_INPUT -> FLUID_INPUT_PANEL;
            case FLUID_OUTPUT -> FLUID_OUTPUT_PANEL;
            case REDSTONE_INPUT -> REDSTONE_INPUT_PANEL;
            case REDSTONE_OUTPUT -> REDSTONE_OUTPUT_PANEL;
            case UNLOAD -> UNLOAD_PANEL;
            default -> null;
        };
        if (panelTexture == null) {
            return;
        }

        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(panelTexture));
        PoseStack.Pose pose = poseStack.last();
        drawFacePanel(vertices, pose, face, packedOverlay);
    }

    /**
     * Uses the eight physical block vertices as the factory's only port LEDs.
     * Each corner has one small emissive face for each of its three incident
     * block faces, keeping all six port states distinguishable at once.
     */
    public static void renderBlockCorners(ConstructionBlockEntity blockEntity, PoseStack poseStack,
            MultiBufferSource buffers, int packedOverlay) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucentEmissive(LED_TEXTURE));
        PoseStack.Pose pose = poseStack.last();

        for (Direction face : DIRECTIONS) {
            ConstructionPortMode mode = blockEntity.getPortMode(face);
            if (mode == ConstructionPortMode.DISABLED) {
                continue;
            }
            int color = mode.indicatorColor();
            drawFaceCornerLeds(vertices, pose, face, color >> 16 & 0xFF, color >> 8 & 0xFF,
                    color & 0xFF, CORNER_CENTER_OFFSET, CORNER_HALF, packedOverlay);
        }
    }

    private static void drawFaceCornerLeds(VertexConsumer vertices, PoseStack.Pose pose, Direction face,
            int red, int green, int blue, float inset, float halfSize, int packedOverlay) {
        float normalX = face.getStepX();
        float normalY = face.getStepY();
        float normalZ = face.getStepZ();
        float[] u = uAxis(face);
        float[] v = vAxis(face);

        for (int signU = -1; signU <= 1; signU += 2) {
            for (int signV = -1; signV <= 1; signV += 2) {
                drawLed(vertices, pose, u, v, normalX, normalY, normalZ,
                        signU * inset, signV * inset, halfSize, red, green, blue, packedOverlay);
            }
        }
    }

    private static void drawFacePanel(VertexConsumer vertices, PoseStack.Pose pose, Direction face,
            int packedOverlay) {
        drawLed(vertices, pose, uAxis(face), vAxis(face), face.getStepX(), face.getStepY(), face.getStepZ(),
                0.0F, 0.0F, PANEL_HALF, 255, 255, 255, packedOverlay);
    }

    private static void drawLed(VertexConsumer vertices, PoseStack.Pose pose, float[] u, float[] v,
            float normalX, float normalY, float normalZ, float offsetU, float offsetV,
            int red, int green, int blue, int packedOverlay) {
        drawLed(vertices, pose, u, v, normalX, normalY, normalZ, offsetU, offsetV, LED_HALF,
                red, green, blue, packedOverlay);
    }

    private static void drawLed(VertexConsumer vertices, PoseStack.Pose pose, float[] u, float[] v,
            float normalX, float normalY, float normalZ, float offsetU, float offsetV, float halfSize,
            int red, int green, int blue, int packedOverlay) {
        float centerX = 0.5F + normalX * FACE_OFFSET + u[0] * offsetU + v[0] * offsetV;
        float centerY = 0.5F + normalY * FACE_OFFSET + u[1] * offsetU + v[1] * offsetV;
        float centerZ = 0.5F + normalZ * FACE_OFFSET + u[2] * offsetU + v[2] * offsetV;

        vertex(vertices, pose, centerX - u[0] * halfSize - v[0] * halfSize,
                centerY - u[1] * halfSize - v[1] * halfSize,
                centerZ - u[2] * halfSize - v[2] * halfSize,
                red, green, blue, 0.0F, 0.0F, packedOverlay, normalX, normalY, normalZ);
        vertex(vertices, pose, centerX + u[0] * halfSize - v[0] * halfSize,
                centerY + u[1] * halfSize - v[1] * halfSize,
                centerZ + u[2] * halfSize - v[2] * halfSize,
                red, green, blue, 1.0F, 0.0F, packedOverlay, normalX, normalY, normalZ);
        vertex(vertices, pose, centerX + u[0] * halfSize + v[0] * halfSize,
                centerY + u[1] * halfSize + v[1] * halfSize,
                centerZ + u[2] * halfSize + v[2] * halfSize,
                red, green, blue, 1.0F, 1.0F, packedOverlay, normalX, normalY, normalZ);
        vertex(vertices, pose, centerX - u[0] * halfSize + v[0] * halfSize,
                centerY - u[1] * halfSize + v[1] * halfSize,
                centerZ - u[2] * halfSize + v[2] * halfSize,
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
            case DOWN, UP, SOUTH -> X_AXIS;
            case NORTH -> new float[] {-1.0F, 0.0F, 0.0F};
            case WEST -> Z_AXIS;
            case EAST -> new float[] {0.0F, 0.0F, -1.0F};
        };
    }

    private static float[] vAxis(Direction face) {
        return switch (face) {
            case DOWN -> Z_AXIS;
            case UP -> new float[] {0.0F, 0.0F, -1.0F};
            default -> Y_AXIS;
        };
    }
}
