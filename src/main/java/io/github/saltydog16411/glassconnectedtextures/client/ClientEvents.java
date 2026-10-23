package io.github.saltydog16411.glassconnectedtextures.client;

import io.github.saltydog16411.glassconnectedtextures.GlassConnectedTextures;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GlassConnectedTextures.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() { }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        SpriteSet.register(event);
    }

    @SubscribeEvent
    public static void modifyBakedModels(ModelEvent.ModifyBakingResult event) {
        SpriteSet.reload(event.getModels());

        for (GlassTargets.Target target : GlassTargets.all()) {
            for (BlockState state : target.block().getStateDefinition().getPossibleStates()) {
                var location = BlockModelShaper.stateToModelLocation(state);
                event.getModels().computeIfPresent(location, (key, model) -> {
                    if (model instanceof ConnectedGlassModel) {
                        return model;
                    }
                    return new ConnectedGlassModel(model, target.family(), target.pane());
                });
            }
        }
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        for (GlassTargets.Target target : GlassTargets.all()) {
            Block block = target.block();
            int tint = target.tint();
            event.register((state, level, pos, tintIndex) -> tintIndex == 0 ? tint : 0xFFFFFF, block);
        }
    }
}
