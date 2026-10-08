package dev.rptag.client;

import dev.rptag.ChatBubblePayload;
import dev.rptag.FearPayload;
import dev.rptag.MusicFileAnswerPayload;
import dev.rptag.MusicFileChunkPayload;
import dev.rptag.MusicFileQueryPayload;
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

    /** (3.50.0) MUSICA CUSTOM de lore zone: toca/para o .ogg da pasta. */
    public static void handleLorezoneMusic(final dev.rptag.LorezoneMusicPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.tocar()) {
                MusicaCustom.tocar(payload.nome());
            } else {
                MusicaCustom.parar();
            }
        });
    }

    /** pedaços em montagem (sync do servidor): nome -> partes[total]. */
    private static final java.util.concurrent.ConcurrentHashMap<String, byte[][]> PARTES =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * (3.55.0) O SERVIDOR quer tocar a musica dele: se o cliente ja tem o
     * arquivo (tamanho certo), toca na hora e responde tem=true; se nao tem,
     * responde tem=false e espera os pedaços.
     */
    public static void handleMusicQuery(final MusicFileQueryPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            boolean tem = false;
            try {
                java.nio.file.Path p = MusicaCustom.pasta().resolve(payload.nome());
                tem = java.nio.file.Files.isRegularFile(p)
                        && java.nio.file.Files.size(p) == payload.size();
            } catch (Exception ignored) {
            }
            if (tem) {
                MusicaCustom.tocarArquivoNome(payload.nome());
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new MusicFileAnswerPayload(payload.nome(), true));
            } else {
                PARTES.remove(payload.nome());
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new MusicFileAnswerPayload(payload.nome(), false));
            }
        });
    }

    /**
     * (3.55.0) Recebe um pedaço do arquivo do servidor: monta tudo, grava em
     * config/rptag/musicas/ e toca (todo mundo ouve a mesma musica!).
     */
    public static void handleMusicChunk(final MusicFileChunkPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                byte[][] partes = PARTES.computeIfAbsent(payload.nome(),
                        n -> new byte[payload.total()][]);
                if (partes.length != payload.total() || payload.indice() >= partes.length) {
                    return;
                }
                partes[payload.indice()] = payload.dados();
                int totalBytes = 0;
                for (byte[] parte : partes) {
                    if (parte == null) {
                        return; // ainda faltam pedaços
                    }
                    totalBytes += parte.length;
                }
                java.nio.file.Path arquivo = MusicaCustom.pasta().resolve(payload.nome());
                java.nio.file.Files.createDirectories(MusicaCustom.pasta());
                byte[] tudo = new byte[totalBytes];
                int pos = 0;
                for (byte[] parte : partes) {
                    System.arraycopy(parte, 0, tudo, pos, parte.length);
                    pos += parte.length;
                }
                java.nio.file.Files.write(arquivo, tudo);
                PARTES.remove(payload.nome());
                MusicaCustom.tocarArquivoNome(payload.nome());
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new MusicFileAnswerPayload(payload.nome(), true));
            } catch (Exception e) {
                dev.rptag.RPTagMod.LOGGER.warn("RP Tag: falha ao receber musica do servidor", e);
                PARTES.remove(payload.nome());
            }
        });
    }
}
