package dev.rptag;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import dev.rptag.client.ClientPayloadHandler;

/** Registro de todos os pacotes do mod. */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class ModNetworking {

    private ModNetworking() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(final RegisterPayloadHandlersEvent event) {
        // v2: pacotes de estilo do balao mudaram de formato na 3.2/3.5/3.6.
        // Versao diferente de jar agora e RECUSADA com aviso claro (antes corrompia
        // os saves de cor silenciosamente — cliente novo em servidor velho).
        // (3.44.0) v3: GANHOU os canais fear/screen_text (cliente velho em
        // servidor novo seria recusado por canal desconhecido; agora a recusa
        // e limpa, com aviso de versao).
        // (3.50.0) v4: GANHOU o canal lorezone_music (musica custom por
        // arquivo .ogg no cliente).
        // (3.55.0) v5: GANHOU music_query/music_chunk/music_answer (a musica
        // DO SERVIDOR vai pro cliente em pedaços — todo mundo ouve).
        // (4.9.0) v7: o item da ficha saiu do mod — cliente v6 (que ainda
        // manda os pacotes antigos) e recusado na hora, com mensagem de versao.
        // (5.2.0) v8: TABLET NEPTUNE (tablet_sync cliente / tablet_update servidor).
        // (5.4.0) v9: EVENTO NEPTUNE (terremoto, saida verde, susto, aura, nebulosa)
        // (5.7.2) v10: TITULOS (sync_titulo) — cliente 5.7.1 nao tem o canal
        // (5.7.4) v11: o pacote AURA ganhou o UUID de quem e o vazio (raios 3D)
        // (5.9.0) v12: sync_titulo ganhou a COR do titulo pessoal
        // (5.13.0) v13: VAZIO_PORTAL (posicao + estado do portal negro, pedestais)
        // (5.13.6) v14: VAZIO_ALMAS (posicao das almas vazias, desenhadas por geometria)
        // (5.17.0) v15: BARRA_HUD (a barra que fica sempre na tela) e AXION_HUD (a gatinha)
        // (5.25.1) v16: MORTE_RP (o servidor diz se a morte é de vida canônica)
        PayloadRegistrar registrar = event.registrar("18");
        registrar.playToClient(TabletSyncPayload.TYPE, TabletSyncPayload.STREAM_CODEC,
                ClientPayloadHandler::handleTablet);
        // (5.13.0) o portal negro do vazio (posicao, pedestais, estado da abertura)
        registrar.playToClient(VazioPortalPayload.TYPE, VazioPortalPayload.STREAM_CODEC,
                ClientPayloadHandler::handleVazioPortal);
        // (5.13.6) as almas vazias perdidas na dimensão
        registrar.playToClient(VazioAlmasPayload.TYPE, VazioAlmasPayload.STREAM_CODEC,
                ClientPayloadHandler::handleVazioAlmas);
        // (5.31.0) o leque de habilidades do vazio manda o número da habilidade
        registrar.playToServer(dev.rptag.VazioPoderC2S.TYPE, dev.rptag.VazioPoderC2S.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    try {
                        if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) {
                            dev.rptag.VazioPoderes.Poder poder =
                                    dev.rptag.VazioPoderes.poderPorIndice(payload.poder());
                            if (poder != null) {
                                dev.rptag.VazioPoderes.usar(sp, poder);
                            }
                        }
                    } catch (Throwable t) {
                        dev.rptag.RPTagMod.LOGGER.warn("RP Tag: habilidade do leque falhou", t);
                    }
                }));
        registrar.playToServer(TabletUpdatePayload.TYPE, TabletUpdatePayload.STREAM_CODEC,
                TabletService::handleUpdate);
        registrar.playToClient(SyncRPStatePayload.TYPE, SyncRPStatePayload.STREAM_CODEC,
                ClientPayloadHandler::handleSync);
        registrar.playToClient(PersonaSyncPayload.TYPE, PersonaSyncPayload.STREAM_CODEC,
                ClientPayloadHandler::handlePersona);
        registrar.playToClient(ChatBubblePayload.TYPE, ChatBubblePayload.STREAM_CODEC,
                ClientPayloadHandler::handleBubble);
        registrar.playToClient(OpenBubbleStylePayload.TYPE, OpenBubbleStylePayload.STREAM_CODEC,
                ClientPayloadHandler::handleOpenBubbleStyle);
        registrar.playToClient(FearPayload.TYPE, FearPayload.STREAM_CODEC,
                ClientPayloadHandler::handleFear);
        registrar.playToClient(ScreenTextPayload.TYPE, ScreenTextPayload.STREAM_CODEC,
                ClientPayloadHandler::handleScreenText);
        // (3.77.0) BANNER da vida canônica (LORE ON/OFF verde/vermelho)
        registrar.playToClient(dev.rptag.CanonBannerPayload.TYPE, dev.rptag.CanonBannerPayload.STREAM_CODEC,
                ClientPayloadHandler::handleCanonBanner);
        // (3.50.0) MUSICA CUSTOM de lore zone (.ogg da pasta do cliente)
        // (5.29.0) os poderes do vazio (raio, orbe, onda, olhos) e o portal do juízo
        registrar.playToClient(dev.rptag.VazioPoderPayload.TYPE, dev.rptag.VazioPoderPayload.STREAM_CODEC,
                ClientPayloadHandler::handleVazioPoder);
        registrar.playToClient(dev.rptag.ZoneShowPayload.TYPE, dev.rptag.ZoneShowPayload.STREAM_CODEC,
                ClientPayloadHandler::handleZoneShow);

        registrar.playToClient(LorezoneMusicPayload.TYPE, LorezoneMusicPayload.STREAM_CODEC,
                ClientPayloadHandler::handleLorezoneMusic);
        // (3.55.0) SYNC da musica do servidor (pergunta -> pedaços -> resposta)
        registrar.playToClient(dev.rptag.MusicFileQueryPayload.TYPE, dev.rptag.MusicFileQueryPayload.STREAM_CODEC,
                ClientPayloadHandler::handleMusicQuery);
        registrar.playToClient(dev.rptag.MusicFileChunkPayload.TYPE, dev.rptag.MusicFileChunkPayload.STREAM_CODEC,
                ClientPayloadHandler::handleMusicChunk);
        registrar.playToServer(dev.rptag.MusicFileAnswerPayload.TYPE, dev.rptag.MusicFileAnswerPayload.STREAM_CODEC,
                ServerEvents::handleMusicAnswer);
        registrar.playToServer(UpdateBubbleStylePayload.TYPE, UpdateBubbleStylePayload.STREAM_CODEC,
                ServerEvents::handleUpdateBubbleStyle);
        // (3.65.0) TYPEWRITER: BIDIRECIONAL (C2S = jogador pede; S2C = server
        // confirma). O MESMO TYPE nao pode ser registrado 2x (provado no boot:
        // "Cannot register payload rptag:typewriter as it is already
        // registered") — playBidirectional + rotas pelo flow.
        registrar.playBidirectional(TypewriterPayload.TYPE, TypewriterPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.flow().isServerbound()) {
                        ServerEvents.handleSetTypewriter(payload, context);
                    } else {
                        ClientPayloadHandler.handleTypewriter(payload, context);
                    }
                });
        registrar.playToServer(SetBubbleModePayload.TYPE, SetBubbleModePayload.STREAM_CODEC,
                ServerEvents::handleSetBubbleMode);
        // (5.4.0) EVENTO NEPTUNE — todos S2C
        registrar.playToClient(NeptunePackets.Terremoto.TYPE, NeptunePackets.Terremoto.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.rptag.client.NeptuneFx.onTerremoto(p)));
        registrar.playToClient(NeptunePackets.Saida.TYPE, NeptunePackets.Saida.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.rptag.client.NeptuneFx.onSaida(p)));
        registrar.playToClient(NeptunePackets.Susto.TYPE, NeptunePackets.Susto.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.rptag.client.NeptuneFx.onSusto(p)));
        registrar.playToClient(NeptunePackets.Aura.TYPE, NeptunePackets.Aura.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.rptag.client.NeptuneFx.onAura(p)));
        registrar.playToClient(NeptunePackets.Nebulosa.TYPE, NeptunePackets.Nebulosa.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.rptag.client.NeptuneFx.onNebulosa(p)));
        // (5.6.0) CENA: rachadura na tela, queda do jogador e o estouro inicial
        registrar.playToClient(NeptunePackets.Cena.TYPE, NeptunePackets.Cena.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.rptag.client.NeptuneFx.onCena(p)));
        // (5.7.7) CÓDIGOS EM VOLTA DO PLAYER na saída (animação no mundo)
        registrar.playToClient(NeptunePackets.Codigos.TYPE, NeptunePackets.Codigos.STREAM_CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> dev.rptag.client.GlifosSaidaFx.iniciar(p)));
        // (5.17.0) a BARRA de progressão que fica sempre na tela (o ADM manda o estado inteiro)
        registrar.playToClient(BarraPayload.TYPE, BarraPayload.STREAM_CODEC,
                dev.rptag.client.ClientPayloadHandler::handleBarra);
        // (5.17.0) a AXION no canto inferior direito (dormindo/acordada/escondida)
        registrar.playToClient(AxionPayload.TYPE, AxionPayload.STREAM_CODEC,
                dev.rptag.client.ClientPayloadHandler::handleAxion);
        // (5.20.0) a AXION FALANDO: balão vermelho com letras pretas saindo dela
        registrar.playToClient(AxionFalaPayload.TYPE, AxionFalaPayload.STREAM_CODEC,
                dev.rptag.client.ClientPayloadHandler::handleAxionFala);
        // (5.25.1) MORTE FORA DE RP: escurece a tela e sobram so os botoes de respawn
        // (5.26.0) o vampiro avisa o cliente quando ele está na noite dele (aí o cliente acende
        // a visão de vampiro do mod, que não depende de shader)
        registrar.playToClient(VampiroNoitePayload.TYPE, VampiroNoitePayload.STREAM_CODEC,
                ClientPayloadHandler::handleVampiroNoite);
        registrar.playToClient(MorteRpPayload.TYPE, MorteRpPayload.STREAM_CODEC,
                dev.rptag.client.ClientPayloadHandler::handleMorte);
        // (5.7.2) SISTEMA DE TÍTULOS (etiqueta colorida + título pessoal)
        registrar.playToClient(TituloSyncPayload.TYPE, TituloSyncPayload.STREAM_CODEC,
                ClientPayloadHandler::handleTitulo);
    }
}
