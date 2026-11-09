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

    /** (5.31.0) as teclas do leque (R abre, X repete) — aparecem em Opções → Controles. */
    @SubscribeEvent
    public static void onRegisterKeys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        seguro("teclas_do_leque", () -> PoderLeque.registrarTeclas(event));
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // (3.92.0) ASCENSÃO: camada do FX cinematográfico (pilar de luz,
        // asas, hexagrama, letras caindo) — a mensagem vem do comando
        // (5.31.0) o LEQUE DE HABILIDADES do vazio (tecla R)
        seguro("leque_poderes", () -> PoderLeque.registrarCamada(event));
        seguro("divino_aviso_fx", () -> DivinoAvisoFx.registrar(event));
        // (3.93.0) CHAMADO DO ABISMO: camada do FX do demoniaco (mandibulas,
        // fissuras de lava, pentagrama, chifres, olho) — mensagem do comando
        seguro("demoniaco_aviso_fx", () -> DemoniacoAvisoFx.registrar(event));
        seguro("sangue_aviso_fx", () -> SangueAvisoFx.registrar(event));
        seguro("void_aviso_fx", () -> VoidAvisoFx.registrar(event));
        seguro("pesadelo_aviso_fx", () -> PesadeloAvisoFx.registrar(event));
        // (3.97.0) FORNALHA (inferno), GEADA ETERNA (gelo), TRADUCAO ARCANA (runico)
        seguro("inferno_aviso_fx", () -> InfernoAvisoFx.registrar(event));
        seguro("gelo_aviso_fx", () -> GeloAvisoFx.registrar(event));
        seguro("runico_aviso_fx", () -> RunicoAvisoFx.registrar(event));
        // (3.98.0) estilo 11 = PESADELO2 "Sono Sem Descanso" (o modelo
        // antigo, do 3.96.4, mantido como opcao ao lado do "Seguido")
        seguro("pesadelo_antigo_aviso_fx", () -> PesadeloAntigoAvisoFx.registrar(event));
        // (4.1.0) estilo 4 = MORTE "Linha Reta" (monitor cardiaco que vira
        // linha reta + cranio pixel art + cinzas) — vale pro aviso de morte
        // do /rp admin avisar e pro ritual da VIDA CANONICA
        seguro("morte_aviso_fx", () -> MorteAvisoFx.registrar(event));
        // (4.5.0) estilo 12 = EGIPICIO "O Olho de Horus" (cartucho + hieroglifos + areia)
        seguro("egipcio_aviso_fx", () -> EgipcioAvisoFx.registrar(event));
        // (4.9.9) estilo 3 = REAL "Decreto da Coroa"
        seguro("real_aviso_fx", () -> RealAvisoFx.registrar(event));
        // (4.9.2) estilo 13 = EXPURGO "Hora do Expurgo" (TV de emergencia:
        // estatica, contagem 5-4-3-2-1, sirene, mascaras) — prototipo do dono
        seguro("expurgo_aviso_fx", () -> ExpurgoAvisoFx.registrar(event));
        // (4.9.3) estilo 14 = CIBERPSICOSE "Alerta de Ciberpsicose" (glitch
        // cyberpunk: estatica, humanidade caindo, vidro estilhacando, ECG)
        seguro("ciberpsicose_aviso_fx", () -> CiberpsicoseAvisoFx.registrar(event));
        // (5.14.0) os tres avisos do dono: Louco, Marinheiro (Enforcado) e Espectador (Justica)
        seguro("louco_aviso_fx", () -> LoucoAvisoFx.registrar(event));
        seguro("marinheiro_aviso_fx", () -> MarinheiroAvisoFx.registrar(event));
        seguro("espectador_aviso_fx", () -> EspectadorAvisoFx.registrar(event));
        seguro("eremita_aviso_fx", () -> EremitaAvisoFx.registrar(event));
        seguro("julgamento_aviso_fx", () -> JulgamentoAvisoFx.registrar(event));
        seguro("lua_aviso_fx", () -> LuaAvisoFx.registrar(event));
        seguro("mago_aviso_fx", () -> MagoAvisoFx.registrar(event));
        seguro("morte_tarot_aviso_fx", () -> MorteTarotAvisoFx.registrar(event));
        seguro("sol_tarot_aviso_fx", () -> SolTarotAvisoFx.registrar(event));
        seguro("torre_aviso_fx", () -> TorreAvisoFx.registrar(event));
        seguro("estrela_aviso_fx", () -> EstrelaAvisoFx.registrar(event));
        seguro("tolo_aviso_fx", () -> ToloAvisoFx.registrar(event));
        // (5.5.0) estilo 15 = ENERGIA "Anomalia de Entropia" (raios, plasma
        // roxo, glitch, medidor de entropia) — protótipo do dono
        seguro("energia_aviso_fx", () -> EnergiaAvisoFx.registrar(event));
        // (5.7.0) estilo 16 = CONHECIMENTO "O Olho que Sabe" (anéis de runas,
        // olho dourado, olhinhos nas beiradas, sussurros) — protótipo do dono
        seguro("conhecimento_aviso_fx", () -> ConhecimentoAvisoFx.registrar(event));
        // (5.7.9) os avisos dos protótipos do dono (o ONE FOR ALL saiu na 5.16.4):
        //   20 = MORTEOP    (espiral, relógio, crânio se desmanchando em cinzas)
        seguro("morte_op_aviso_fx", () -> MorteOpAvisoFx.registrar(event));
        // (5.8.1) 22 = MEDOOP (a membrana, a lanterna e a silhueta alta)
        seguro("medo_op_aviso_fx", () -> MedoOpAvisoFx.registrar(event));
        // (5.7.0) ANIMAÇÃO DE SAÍDA do jogador (sprite dissolvendo em código)
        seguro("saida_player_fx", () -> SaidaPlayerFx.registrar(event));
        // (5.17.0) 37 = FITA CASSETE (o aviso que a fita toca)
        seguro("fita_aviso_fx", () -> FitaAvisoFx.registrar(event));
        // (5.24.3) 38 = INFECCAO (veias + olho + FORCA x SANIDADE) e 39 = a variante Minecraft
        // (5.26.0) a visão de vampiro (clarão prateado no escuro, quando o servidor avisa).
        // (5.26.1) registrada AQUI e em nenhum outro lugar — ela já esteve anotada com
        // @EventBusSubscriber também, e o NeoForge derrubou o cliente por camada repetida.
        seguro("visao_vampiro", () -> VisaoVampiroFx.registrar(event));
        seguro("infeccao_aviso_fx", () -> InfeccaoAvisoFx.registrar(event));
        seguro("infeccao2aviso_fx", () -> Infeccao2AvisoFx.registrar(event));
        // (5.17.0) a BARRA que fica sempre na tela e a AXION no canto inferior direito
        seguro("barra_hud_fx", () -> BarraHudFx.registrar(event));
        AxionHud.registrar(event);
        // (5.12.0) 23 = ENGOLIR (o terremoto levando a pessoa — protótipo do dono)
        seguro("engolir_fx", () -> EngolirFx.registrar(event));
        // (5.7.2) SOMBRA DA MORTE: a escuridão saindo do corpo de quem morre
        seguro("sombra_morte_fx", () -> SombraMorteFx.registrar(event));
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

    /**
     * (5.26.1) REGISTRO À PROVA DE BALA — lição do log do dono (cliente morreu na tela de
     * carregamento com "Layer already registered: rptag:visao_vampiro").
     *
     * <p>Uma camada repetida (ou qualquer erro de registro) não pode mais derrubar o jogo: o
     * erro fica no log com o nome da camada e o cliente segue abrindo com as outras.
     */
    private static void seguro(String nome, Runnable registro) {
        try {
            registro.run();
        } catch (Throwable erro) {
            RPTagMod.LOGGER.error("RP Tag: a camada '{}' nao pode ser registrada (o jogo segue sem ela)",
                    nome, erro);
        }
    }
}
