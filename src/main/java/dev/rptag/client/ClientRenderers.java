package dev.rptag.client;

import dev.rptag.RPTagMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** (3.84.0) registro dos renderers do mod (mod bus, cliente). */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientRenderers {

    private ClientRenderers() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(RPTagMod.CORACAO_BE.get(), CanonCoracaoRenderer::new);
    }
}
