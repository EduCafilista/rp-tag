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
        // (3.92.0) ASCENSÃO: camada do FX cinematográfico (pilar de luz,
        // asas, hexagrama, letras caindo) — a mensagem vem do comando
        DivinoAvisoFx.registrar(event);
        // (3.93.0) CHAMADO DO ABISMO: camada do FX do demoniaco (mandibulas,
        // fissuras de lava, pentagrama, chifres, olho) — mensagem do comando
        DemoniacoAvisoFx.registrar(event);
        SangueAvisoFx.registrar(event);
        VoidAvisoFx.registrar(event);
        PesadeloAvisoFx.registrar(event);
        // (3.97.0) FORNALHA (inferno), GEADA ETERNA (gelo), TRADUCAO ARCANA (runico)
        InfernoAvisoFx.registrar(event);
        GeloAvisoFx.registrar(event);
        RunicoAvisoFx.registrar(event);
        // (3.98.0) estilo 11 = PESADELO2 "Sono Sem Descanso" (o modelo
        // antigo, do 3.96.4, mantido como opcao ao lado do "Seguido")
        PesadeloAntigoAvisoFx.registrar(event);
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "medo_veu"),
                (g, delta) -> ClientEffects.desenharMedoCamada(g));
        // (3.78.0) VÉU avermelhado da vida perdida (ANTES do aviso = o
        // texto fica POR CIMA do véu)
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "canon_veu"),
                (g, delta) -> ClientEffects.desenhaVeuCanon(g));
        // (3.61.0) AVISO na camada oficial tambem: no RenderGuiEvent.Post ele
        // ficava FORA da saida do shader e sumia com Iris (mesmo mal do medo)
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "aviso_hud"),
                (g, delta) -> ClientEffects.desenhaAviso(g));
        // (3.77.0) BANNER da vida canônica (na camada oficial = com shaders)
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "canon_banner"),
                (g, delta) -> ClientEffects.desenhaBannerCanon(g));
    }
}
