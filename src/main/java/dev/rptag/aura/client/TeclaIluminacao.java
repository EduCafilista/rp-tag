package dev.rptag.aura.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rptag.aura.AuraEntidades;
import dev.rptag.aura.PoderPortoesPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * (5.60.0) A TECLA DO PODER da aura de iluminacao: <b>G</b> (o jogador troca em Controles).
 * O pacote so avisa o servidor — quem valida (aura ligada + recarga) e o {@code PoderPortoes}.
 */
public final class TeclaIluminacao {
    public static final KeyMapping TECLA_PORTOES = new KeyMapping(
            "key.rptag.iluminacao", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.rptag");

    @EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static final class EventosDeMod {
        @SubscribeEvent
        public static void teclas(RegisterKeyMappingsEvent e) {
            e.register(TECLA_PORTOES);
        }

        @SubscribeEvent
        public static void renderizadores(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(AuraEntidades.ARMA_DOURADA.get(), ArmaDouradaRenderer::new);
        }
    }

    @EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
    public static final class EventosDeJogo {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            while (TECLA_PORTOES.consumeClick()) {
                if (Minecraft.getInstance().player != null) {
                    PacketDistributor.sendToServer(new PoderPortoesPayload());
                }
            }
        }
    }

    private TeclaIluminacao() {}
}
