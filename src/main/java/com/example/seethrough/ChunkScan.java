package com.example.seethrough;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Marks loaded chunks that contain artificial light, amethyst, or man-made blocks.
 * Press L to toggle. Each flagged chunk gets a square outline at your feet level:
 *   yellow = artificial light, purple = amethyst, red = man-made blocks.
 * If a chunk has more than one, the squares are nested inside each other.
 */
public class ChunkScan {
    static final int FLAG_LIGHT = 1, FLAG_AMETHYST = 2, FLAG_BUILT = 4;
    static final int RADIUS = 8; // chunks around you (limited by the server's view distance)

    static final float[][] COLORS = {
        {1f, 0.9f, 0f},   // light  - yellow
        {0.7f, 0.2f, 1f}, // amethyst - purple
        {1f, 0.1f, 0.1f}  // man-made - red
    };

    // Edit these lists to change what counts.
    static final Set<Block> LIGHT_BLOCKS = Set.of(
        Blocks.TORCH, Blocks.WALL_TORCH, Blocks.SOUL_TORCH, Blocks.SOUL_WALL_TORCH,
        Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH,
        Blocks.LANTERN, Blocks.SOUL_LANTERN, Blocks.GLOWSTONE, Blocks.SEA_LANTERN,
        Blocks.REDSTONE_LAMP, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
        Blocks.JACK_O_LANTERN, Blocks.END_ROD,
        Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT
    );

    static final Set<Block> AMETHYST_BLOCKS = Set.of(
        Blocks.AMETHYST_BLOCK, Blocks.BUDDING_AMETHYST, Blocks.AMETHYST_CLUSTER,
        Blocks.LARGE_AMETHYST_BUD, Blocks.MEDIUM_AMETHYST_BUD, Blocks.SMALL_AMETHYST_BUD
    );

    static final Set<Block> BUILT_BLOCKS = Set.of(
        Blocks.BRICKS, Blocks.GLASS, Blocks.GLASS_PANE, Blocks.CRAFTING_TABLE,
        Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.HOPPER,
        Blocks.DISPENSER, Blocks.DROPPER, Blocks.OBSERVER, Blocks.PISTON,
        Blocks.STICKY_PISTON, Blocks.TNT, Blocks.ANVIL, Blocks.ENCHANTING_TABLE,
        Blocks.BREWING_STAND, Blocks.SMOOTH_STONE, Blocks.IRON_BLOCK
    );

    static final Predicate<BlockState> P_LIGHT =
        s -> LIGHT_BLOCKS.contains(s.getBlock());
    static final Predicate<BlockState> P_AMETHYST =
        s -> AMETHYST_BLOCKS.contains(s.getBlock());
    static final Predicate<BlockState> P_BUILT =
        s -> BUILT_BLOCKS.contains(s.getBlock())
            || s.isIn(BlockTags.PLANKS) || s.isIn(BlockTags.WOOL)
            || s.isIn(BlockTags.DOORS) || s.isIn(BlockTags.BEDS);

    static boolean on = false;
    static final Map<Long, Integer> results = new HashMap<>();
    static KeyBinding key;

    public static void init() {
        key = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.seethrough.chunkscan", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_L, "category.seethrough"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null || mc.world == null) return;
            while (key.wasPressed()) {
                on = !on;
                if (!on) results.clear();
            }
            if (on && mc.world.getTime() % 20 == 0) scan(mc);
        });

        WorldRenderEvents.LAST.register(ctx -> {
            if (!on || results.isEmpty()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;

            MatrixStack matrices = ctx.matrixStack();
            Vec3d cam = ctx.camera().getPos();
            double y = mc.player.getY();

            VertexConsumerProvider.Immediate provider = mc.getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer lines = provider.getBuffer(RenderLayer.getLines());

            RenderSystem.disableDepthTest();
            for (Map.Entry<Long, Integer> e : results.entrySet()) {
                ChunkPos p = new ChunkPos(e.getKey());
                int flags = e.getValue();
                int nested = 0;
                for (int i = 0; i < 3; i++) {
                    if ((flags & (1 << i)) == 0) continue;
                    double in = nested * 0.6;
                    nested++;
                    Box box = new Box(
                        p.getStartX() + in, y, p.getStartZ() + in,
                        p.getStartX() + 16 - in, y + 0.5, p.getStartZ() + 16 - in
                    ).offset(-cam.x, -cam.y, -cam.z);
                    float[] c = COLORS[i];
                    WorldRenderer.drawBox(matrices, lines, box, c[0], c[1], c[2], 1f);
                }
            }
            provider.draw(RenderLayer.getLines());
            RenderSystem.enableDepthTest();
        });
    }

    static void scan(MinecraftClient mc) {
        results.clear();
        ChunkPos here = mc.player.getChunkPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int cx = here.x + dx, cz = here.z + dz;
                Chunk chunk = mc.world.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;

                int flags = 0;
                for (ChunkSection section : chunk.getSectionArray()) {
                    if (section == null || section.isEmpty()) continue;
                    if ((flags & FLAG_LIGHT) == 0 && section.hasAny(P_LIGHT)) flags |= FLAG_LIGHT;
                    if ((flags & FLAG_AMETHYST) == 0 && section.hasAny(P_AMETHYST)) flags |= FLAG_AMETHYST;
                    if ((flags & FLAG_BUILT) == 0 && section.hasAny(P_BUILT)) flags |= FLAG_BUILT;
                }
                if (flags != 0) results.put(ChunkPos.toLong(cx, cz), flags);
            }
        }
    }
}
