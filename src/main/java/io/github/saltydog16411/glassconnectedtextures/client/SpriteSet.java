package io.github.saltydog16411.glassconnectedtextures.client;

import io.github.saltydog16411.glassconnectedtextures.GlassConnectedTextures;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

final class SpriteSet {
    private static final int BORDER_VARIANT_COUNT = 15;

    private static final EnumMap<GlassTargets.TextureFamily, TextureAtlasSprite[]> SPRITES =
            new EnumMap<>(GlassTargets.TextureFamily.class);

    private SpriteSet() { }

    static void register(ModelEvent.RegisterAdditional event) {
        for (GlassTargets.TextureFamily family : GlassTargets.TextureFamily.values()) {
            for (int mask = 0; mask < BORDER_VARIANT_COUNT; mask++) {
                event.register(modelLocation(family, mask));
            }
        }
    }

    static void reload(Map<ResourceLocation, BakedModel> models) {
        SPRITES.clear();
        for (GlassTargets.TextureFamily family : GlassTargets.TextureFamily.values()) {
            TextureAtlasSprite[] sprites = new TextureAtlasSprite[BORDER_VARIANT_COUNT];
            for (int mask = 0; mask < BORDER_VARIANT_COUNT; mask++) {
                ResourceLocation location = modelLocation(family, mask);
                BakedModel model = Objects.requireNonNull(models.get(location), "Missing carrier model: " + location);
                sprites[mask] = model.getParticleIcon();
            }
            SPRITES.put(family, sprites);
        }
    }

    static TextureAtlasSprite get(GlassTargets.TextureFamily family, int mask) {
        if (mask < 0 || mask >= BORDER_VARIANT_COUNT) {
            throw new IllegalArgumentException("Border mask must be 0..14, got " + mask);
        }
        TextureAtlasSprite[] sprites = SPRITES.get(family);
        if (sprites == null) {
            throw new IllegalStateException("Connected glass sprites have not been baked yet");
        }
        return sprites[mask];
    }

    static ResourceLocation modelLocation(GlassTargets.TextureFamily family, int mask) {
        String path = "block/ctm/" + family.name().toLowerCase() + "_" + mask;
        return Objects.requireNonNull(ResourceLocation.tryBuild(GlassConnectedTextures.MOD_ID, path));
    }
}
