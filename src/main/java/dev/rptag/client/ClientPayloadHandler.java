package dev.rptag.client;

import dev.rptag.ChatBubblePayload;
import dev.rptag.FearPayload;
import dev.rptag.PersonaSyncPayload;
import dev.rptag.ScreenTextPayload;
import dev.rptag.SyncRPStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Handlers (cliente) dos pacotes de sincronizacao do mod. */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    public static void handleSync(final SyncRPStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientRPStates.set(payload.playerId(), payload.inRp());

            // Atualiza a nametag acima da cabeca imediatamente.
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                Player player = minecraft.level.getPlayerByUUID(payload.playerId());
                if (player != null) {
                    player.refreshDisplayName();
                }
            }
        });
    }

    public static void handlePersona(final PersonaSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPersonaCache.set(payload.playerId(), payload.name(), payload.age(), payload.desc());

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                Player player = minecraft.level.getPlayerByUUID(payload.playerId());
                if (player != null) {
                    player.refreshDisplayName();
                }
            }
        });
    }

    public static void handleBubble(final ChatBubblePayload payload, final IPayloadContext context) {
        context.enqueueWork(() ->
                ClientBubbleCache.set(payload.playerId(), payload.text(), payload.colorRGB(),
                        payload.borderRGB(), payload.borderStyle(), payload.textColorRGB(), payload.italic(),
                        payload.corner(), payload.extras(), payload.stickerX(), payload.stickerY(),
                        payload.durationMs()));
    }

    public static void handleOpenBubbleStyle(final dev.rptag.OpenBubbleStylePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> BubbleStyleScreen.open(payload));
    }

    /** (3.44.0) MEDO: liga a tremedeira + vinheta neste cliente. */
    public static void handleFear(final FearPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientEffects.startFear(payload.seconds()));
    }

    /** (3.44.0) AVISO: texto GRANDE no centro da tela. */
    public static void handleScreenText(final ScreenTextPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientEffects.startScreenText(payload.text(), payload.seconds()));
    }
}
