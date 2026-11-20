package com.example.seethrough;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class SeeThrough implements ClientModInitializer {
    public static boolean esp = false, freecam = false;
    public static Vec3d camPos = Vec3d.ZERO;
    public static float camYaw, camPitch;

    // Block -> RGB color. Edit this to choose what gets highlighted.
    static final Map<Block, float[]> TARGETS = Map.of(
        Blocks.CHEST,              new float[]{1f, 0.7f, 0f},
        Blocks.TRAPPED_CHEST,      new float[]{1f, 0.3f, 0f},
        Blocks.ENDER_CHEST,        new float[]{0.6f, 0f, 1f},
        Blocks.BARREL,             new float[]{0.8f, 0.5f, 0.2f},
        Blocks.SPAWNER,            new float[]{1f, 0f, 0f},
        Blocks.DIAMOND_ORE,        new float[]{0f, 1f, 1f},
        Blocks.DEEPSLATE_DIAMOND_ORE, new float[]{0f, 1f, 1f}
    );
    static final int RADIUS = 24;
    static final Map<BlockPos, float[]> found = new HashMap<>();

    static KeyBinding espKey, camKey;

    @Override
    public void onInitializeClient() {
        ChunkScan.init();
        espKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.seethrough.esp", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.seethrough"));
        camKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.seethrough.freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.seethrough"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.world == null) return;

            while (espKey.wasPressed()) esp = !esp;
            while (camKey.wasPressed()) {
                freecam = !freecam;
                if (freecam) {
                    camPos = mc.gameRenderer.getCamera().getPos();
                    camYaw = mc.player.getYaw();
                    camPitch = mc.player.getPitch();
                }
            }

            if (freecam) moveCamera(mc);
            if (esp && mc.world.getTime() % 20 == 0) scan(mc);
        });

        WorldRenderEvents.LAST.register(ctx -> {
            if (!esp || found.isEmpty()) return;
            MatrixStack matrices = ctx.matrixStack();
            Vec3d cam = ctx.camera().getPos();
            VertexConsumerProvider.Immediate provider =
                MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer lines = provider.getBuffer(RenderLayer.getLines());

            RenderSystem.disableDepthTest(); // draw through walls
            for (var e : found.entrySet()) {
                Box box = new Box(e.getKey()).offset(-cam.x, -cam.y, -cam.z);
                float[] c = e.getValue();
                WorldRenderer.drawBox(matrices, lines, box, c[0], c[1], c[2], 1f);
            }
            provider.draw(RenderLayer.getLines());
            RenderSystem.enableDepthTest();
        });
    }

    static void scan(MinecraftClient mc) {
        found.clear();
        BlockPos origin = mc.player.getBlockPos();
        for (BlockPos p : BlockPos.iterate(origin.add(-RADIUS, -RADIUS, -RADIUS),
                                           origin.add(RADIUS, RADIUS, RADIUS))) {
            float[] color = TARGETS.get(mc.world.getBlockState(p).getBlock());
            if (color != null) found.put(p.toImmutable(), color);
        }
    }

    static void moveCamera(MinecraftClient mc) {
        var o = mc.options;
        double speed = o.sprintKey.isPressed() ? 1.0 : 0.4;
        Vec3d look = Vec3d.fromPolar(camPitch, camYaw);
        Vec3d right = Vec3d.fromPolar(0, camYaw + 90);
        Vec3d move = Vec3d.ZERO;
        if (o.forwardKey.isPressed()) move = move.add(look);
        if (o.backKey.isPressed())    move = move.subtract(look);
        if (o.rightKey.isPressed())   move = move.add(right);
        if (o.leftKey.isPressed())    move = move.subtract(right);
        if (o.jumpKey.isPressed())    move = move.add(0, 1, 0);
        if (o.sneakKey.isPressed())   move = move.subtract(0, 1, 0);
        if (move.lengthSquared() > 0) camPos = camPos.add(move.normalize().multiply(speed));
    }
}
