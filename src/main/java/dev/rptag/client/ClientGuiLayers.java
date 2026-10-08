package dev.rptag.client;

import dev.rptag.RPTagMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * (3.54.0) CAMADA OFICIAL de HUD do medo.
 *
 * <p>O desenho do medo (vinheta + veu azul-noite + icone) vivia no
 * {@code RenderGuiEvent.Post} — e COM SHADERS (Iris) ele ficava invisivel/
 * tapado pela saida do shader. Aqui registramos o desenho como uma CAMADA
 * de HUD de verdade (a mesma pipeline do blur de abobora/vinheta vanilla),
 * {@code registerAboveAll} = a ULTIMA coisa a ser desenhada na tela. Essa
 * pipeline roda DEPOIS do pos-processamento do shader, entao o medo aparece
 * COM SHADERS e SEM SHADERS do mesmo jeito.
 *
 * <p>O desenho em si vive em {@link ClientEffects#desenharMedoCamada}
 * (a prova de crash: qualquer erro desliga o efeito na sessao).
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientGuiLayers {

    private ClientGuiLayers() {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "medo_veu"),
                (g, delta) -> ClientEffects.desenharMedoCamada(g));
    }
}
