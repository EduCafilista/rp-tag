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
        PayloadRegistrar registrar = event.registrar("5");
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
        // (3.50.0) MUSICA CUSTOM de lore zone (.ogg da pasta do cliente)
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
        registrar.playToServer(SetBubbleModePayload.TYPE, SetBubbleModePayload.STREAM_CODEC,
                ServerEvents::handleSetBubbleMode);
    }
}
