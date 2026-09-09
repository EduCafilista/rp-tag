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
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SyncRPStatePayload.TYPE, SyncRPStatePayload.STREAM_CODEC,
                ClientPayloadHandler::handleSync);
        registrar.playToClient(PersonaSyncPayload.TYPE, PersonaSyncPayload.STREAM_CODEC,
                ClientPayloadHandler::handlePersona);
        registrar.playToClient(ChatBubblePayload.TYPE, ChatBubblePayload.STREAM_CODEC,
                ClientPayloadHandler::handleBubble);
        registrar.playToClient(OpenBubbleStylePayload.TYPE, OpenBubbleStylePayload.STREAM_CODEC,
                ClientPayloadHandler::handleOpenBubbleStyle);
        registrar.playToServer(UpdateBubbleStylePayload.TYPE, UpdateBubbleStylePayload.STREAM_CODEC,
                ServerEvents::handleUpdateBubbleStyle);
    }
}
