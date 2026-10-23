package io.github.saltydog16411.glassconnectedtextures.client;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

final class GlassTargets {
    enum TextureFamily { CLEAR, STAINED, TINTED }

    record Target(Block block, boolean pane, TextureFamily family, int tint) { }

    private static final Block[] STAINED_GLASS = {
            Blocks.WHITE_STAINED_GLASS,
            Blocks.ORANGE_STAINED_GLASS,
            Blocks.MAGENTA_STAINED_GLASS,
            Blocks.LIGHT_BLUE_STAINED_GLASS,
            Blocks.YELLOW_STAINED_GLASS,
            Blocks.LIME_STAINED_GLASS,
            Blocks.PINK_STAINED_GLASS,
            Blocks.GRAY_STAINED_GLASS,
            Blocks.LIGHT_GRAY_STAINED_GLASS,
            Blocks.CYAN_STAINED_GLASS,
            Blocks.PURPLE_STAINED_GLASS,
            Blocks.BLUE_STAINED_GLASS,
            Blocks.BROWN_STAINED_GLASS,
            Blocks.GREEN_STAINED_GLASS,
            Blocks.RED_STAINED_GLASS,
            Blocks.BLACK_STAINED_GLASS
    };

    private static final Block[] STAINED_PANES = {
            Blocks.WHITE_STAINED_GLASS_PANE,
            Blocks.ORANGE_STAINED_GLASS_PANE,
            Blocks.MAGENTA_STAINED_GLASS_PANE,
            Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
            Blocks.YELLOW_STAINED_GLASS_PANE,
            Blocks.LIME_STAINED_GLASS_PANE,
            Blocks.PINK_STAINED_GLASS_PANE,
            Blocks.GRAY_STAINED_GLASS_PANE,
            Blocks.LIGHT_GRAY_STAINED_GLASS_PANE,
            Blocks.CYAN_STAINED_GLASS_PANE,
            Blocks.PURPLE_STAINED_GLASS_PANE,
            Blocks.BLUE_STAINED_GLASS_PANE,
            Blocks.BROWN_STAINED_GLASS_PANE,
            Blocks.GREEN_STAINED_GLASS_PANE,
            Blocks.RED_STAINED_GLASS_PANE,
            Blocks.BLACK_STAINED_GLASS_PANE
    };

    private static final Set<Block> FULL_BLOCKS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Block> PANE_BLOCKS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<Target> TARGETS;

    static {
        List<Target> targets = new ArrayList<>(35);
        add(targets, Blocks.GLASS, false, TextureFamily.CLEAR, 0xFFFFFF);
        add(targets, Blocks.GLASS_PANE, true, TextureFamily.CLEAR, 0xFFFFFF);
        add(targets, Blocks.TINTED_GLASS, false, TextureFamily.TINTED, 0x5A5A63);

        DyeColor[] colors = DyeColor.values();
        if (colors.length != STAINED_GLASS.length || colors.length != STAINED_PANES.length) {
            throw new IllegalStateException("Unexpected DyeColor count: " + colors.length);
        }
        for (int i = 0; i < colors.length; i++) {
            int tint = dyeRgb(colors[i]);
            add(targets, STAINED_GLASS[i], false, TextureFamily.STAINED, tint);
            add(targets, STAINED_PANES[i], true, TextureFamily.STAINED, tint);
        }

        TARGETS = List.copyOf(targets);
    }

    private GlassTargets() { }

    static List<Target> all() {
        return TARGETS;
    }

    static boolean isPane(Block block) {
        return PANE_BLOCKS.contains(block);
    }

    static boolean connects(boolean selfIsPane, Block neighbor) {
        return selfIsPane ? PANE_BLOCKS.contains(neighbor) : FULL_BLOCKS.contains(neighbor);
    }

    private static void add(List<Target> targets,
                            Block block,
                            boolean pane,
                            TextureFamily family,
                            int tint) {
        Target target = new Target(block, pane, family, tint);
        targets.add(target);
        (pane ? PANE_BLOCKS : FULL_BLOCKS).add(block);
    }

    private static int dyeRgb(DyeColor dye) {
        float[] c = dye.getTextureDiffuseColors();
        int r = Math.round(c[0] * 255.0F) & 0xFF;
        int g = Math.round(c[1] * 255.0F) & 0xFF;
        int b = Math.round(c[2] * 255.0F) & 0xFF;
        return (r << 16) | (g << 8) | b;
    }
}
