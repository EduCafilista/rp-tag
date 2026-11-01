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
        PayloadRegistrar registrar = event.registrar("11");
        registrar.playToClient(TabletSyncPayload.TYPE, TabletSyncPayload.STREAM_CODEC,
                ClientPayloadHandler::handleTablet);
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
        // (5.7.2) SISTEMA DE TÍTULOS (etiqueta colorida + título pessoal)
        registrar.playToClient(TituloSyncPayload.TYPE, TituloSyncPayload.STREAM_CODEC,
                ClientPayloadHandler::handleTitulo);
    }
}
