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
 *
 * <p>(5.71.0) <b>SEGURA QUE NAO PARA.</b> O dono: <i>"o ataque dos portoes pode ser 'infinito' ate
 * o player soltar o botao, ai para de lancar as lancas douradas"</i>. Entao agora a tecla manda
 * duas coisas: apertou = {@code segurando=true}, soltou = {@code segurando=false}. Quem decide se
 * pode atirar (ADM + aura ligada) e o {@code PoderPortoes}.
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
        /** (5.71.0) estado da tecla no tick anterior — e o que detecta "soltou". */
        private static boolean apertada;

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            boolean agora = TECLA_PORTOES.isDown();
            if (agora == apertada) {
                return;                      // nada mudou
            }
            apertada = agora;
            if (Minecraft.getInstance().player == null) {
                return;
            }
            PacketDistributor.sendToServer(new PoderPortoesPayload(agora));
        }
    }

    private TeclaIluminacao() {}
}
