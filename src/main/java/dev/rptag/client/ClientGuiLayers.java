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
        // (4.1.0) estilo 4 = MORTE "Linha Reta" (monitor cardiaco que vira
        // linha reta + cranio pixel art + cinzas) — vale pro aviso de morte
        // do /rp admin avisar e pro ritual da VIDA CANONICA
        MorteAvisoFx.registrar(event);
        // (4.5.0) estilo 12 = EGIPICIO "O Olho de Horus" (cartucho + hieroglifos + areia)
        EgipcioAvisoFx.registrar(event);
        // (4.9.9) estilo 3 = REAL "Decreto da Coroa"
        RealAvisoFx.registrar(event);
        // (4.9.2) estilo 13 = EXPURGO "Hora do Expurgo" (TV de emergencia:
        // estatica, contagem 5-4-3-2-1, sirene, mascaras) — prototipo do dono
        ExpurgoAvisoFx.registrar(event);
        // (4.9.3) estilo 14 = CIBERPSICOSE "Alerta de Ciberpsicose" (glitch
        // cyberpunk: estatica, humanidade caindo, vidro estilhacando, ECG)
        CiberpsicoseAvisoFx.registrar(event);
        // (5.14.0) os tres avisos do dono: Louco, Marinheiro (Enforcado) e Espectador (Justica)
        LoucoAvisoFx.registrar(event);
        MarinheiroAvisoFx.registrar(event);
        EspectadorAvisoFx.registrar(event);
        // (5.5.0) estilo 15 = ENERGIA "Anomalia de Entropia" (raios, plasma
        // roxo, glitch, medidor de entropia) — protótipo do dono
        EnergiaAvisoFx.registrar(event);
        // (5.7.0) estilo 16 = CONHECIMENTO "O Olho que Sabe" (anéis de runas,
        // olho dourado, olhinhos nas beiradas, sussurros) — protótipo do dono
        ConhecimentoAvisoFx.registrar(event);
        // (5.7.0) estilo 17 = ALLFORONE "As Vozes São Uma Pessoa" (sol pintado,
        // silhueta em chamas, olhos, brasas) — protótipo do dono
        AllForOneAvisoFx.registrar(event);
        // (5.7.9) os TRÊS avisos novos dos protótipos do dono:
        //   19 = ONEFALLALL (o sol dourado por trás da silhueta encapuzada,
        //        as 8 vidas antigas em volta, a tocha pulando)
        //   20 = MORTEOP    (espiral, relógio, crânio se desmanchando em cinzas)
        //   21 = SANGUEOP   (fundo pulsando na batida, veias, gota, anel tracejado)
        SolAvisoFx.registrar(event);
        MorteOpAvisoFx.registrar(event);
        SangueOpAvisoFx.registrar(event);
        // (5.8.1) 22 = MEDOOP (a membrana, a lanterna e a silhueta alta)
        MedoOpAvisoFx.registrar(event);
        // (5.7.0) ANIMAÇÃO DE SAÍDA do jogador (sprite dissolvendo em código)
        SaidaPlayerFx.registrar(event);
        // (5.12.0) 23 = ENGOLIR (o terremoto levando a pessoa — protótipo do dono)
        EngolirFx.registrar(event);
        // (5.7.2) SOMBRA DA MORTE: a escuridão saindo do corpo de quem morre
        SombraMorteFx.registrar(event);
        // (5.4.0) EVENTO NEPTUNE: terremoto (nebulosa + poeira + visao dupla),
        // saida verde, susto do bicho, nebulosa do vazio e aura do deus.
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "neptune_fx"),
                (g, delta) -> NeptuneFx.desenhar(g));
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
