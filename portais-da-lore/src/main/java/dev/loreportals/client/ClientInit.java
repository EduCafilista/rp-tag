package dev.loreportals.client;

import dev.loreportals.LorePortalsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Lado cliente: registra o renderizador do nucleo. */
@EventBusSubscriber(modid = LorePortalsMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientInit {

    private ClientInit() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(LorePortalsMod.PORTAL_BE.get(), PortalRenderer::new);
    }
}
