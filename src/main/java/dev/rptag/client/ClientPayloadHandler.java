package dev.rptag.client;

import dev.rptag.ChatBubblePayload;
import dev.rptag.FearPayload;
import dev.rptag.MusicFileAnswerPayload;
import dev.rptag.MusicFileChunkPayload;
import dev.rptag.MusicFileQueryPayload;
import dev.rptag.PersonaSyncPayload;
import dev.rptag.ScreenTextPayload;
import dev.rptag.SyncRPStatePayload;
import dev.rptag.TypewriterPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Handlers (cliente) dos pacotes de sincronizacao do mod. */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    /** (5.2.0) tablet Neptune: estado da ficha chega do servidor. */
    public static void handleTablet(dev.rptag.TabletSyncPayload payload,
                                    net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> TabletClientEvents.receive(payload));
    }

    /** (5.13.0) estado do portal do vazio: o cliente desenha o vórtice e as colunas. */
    public static void handleVazioPortal(final dev.rptag.VazioPortalPayload payload,
                                         final IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                VazioPortalFx.receber(payload);
            } catch (Throwable erro) {
                dev.rptag.RPTagMod.LOGGER.warn("RP Tag: estado do portal do vazio falhou", erro);
            }
        });
    }

    /** (5.13.6) posições das almas vazias: o cliente desenha o corpo delas. */
    public static void handleVazioAlmas(final dev.rptag.VazioAlmasPayload payload,
                                        final IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                VazioAlmasFx.receber(payload);
            } catch (Throwable erro) {
                dev.rptag.RPTagMod.LOGGER.warn("RP Tag: almas do vazio falharam", erro);
            }
        });
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

    /** (5.7.2) TÍTULO de um jogador: guarda e redesenha o nametag. */
    public static void handleTitulo(final dev.rptag.TituloSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ClientTitulos.set(payload.playerId(), payload.etiqueta(), payload.cor(),
                        payload.personalizado(), payload.corPessoal());
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.level != null) {
                    Player player = minecraft.level.getPlayerByUUID(payload.playerId());
                    if (player != null) {
                        player.refreshDisplayName();
                    }
                }
            } catch (Throwable erro) {
                dev.rptag.RPTagMod.LOGGER.warn("RP Tag: falha ao aplicar titulo no cliente", erro);
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
                        payload.durationMs(), payload.tone()));
    }

    public static void handleOpenBubbleStyle(final dev.rptag.OpenBubbleStylePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> BubbleStyleScreen.open(payload));
    }

    /** (3.65.0) TYPEWRITER: estado confirmado pelo servidor. */
    public static void handleTypewriter(final TypewriterPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientBubbleCache.TYPEWRITER = payload.on());
    }

    /** (3.44.0) MEDO: liga a tremedeira + vinheta neste cliente. */
    public static void handleFear(final FearPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientEffects.startFear(payload.seconds()));
    }

    /** (3.44.0) AVISO: texto GRANDE no centro da tela. */
    public static void handleScreenText(final ScreenTextPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> ClientEffects.startScreenText(payload.text(), payload.seconds(), payload.style()));
    }

    /** (5.17.0) a barra de progressão do HUD (o ADM manda rótulo, valor e cores). */
    public static void handleBarra(final dev.rptag.BarraPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                BarraHudFx.receber(payload);
            } catch (Throwable erro) {
                dev.rptag.RPTagMod.LOGGER.warn("RP Tag: barra do HUD falhou", erro);
            }
        });
    }

    /** (5.17.0) a Axion: dormindo, acordada ou escondida. */
    public static void handleAxion(final dev.rptag.AxionPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                AxionHud.receber(payload);
            } catch (Throwable erro) {
                dev.rptag.RPTagMod.LOGGER.warn("RP Tag: Axion falhou", erro);
            }
        });
    }

    /** (3.77.0) BANNER da vida canônica (LORE ON/OFF). */
    public static void handleCanonBanner(final dev.rptag.CanonBannerPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> ClientEffects.startCanonBanner(
                payload.title(), payload.sub(), payload.seconds(), payload.on()));
    }

    /** (3.50.0) MUSICA CUSTOM de lore zone: toca/para o .ogg da pasta. */
    /** (4.0.0) barreira da lorezone — SO o admin que pediu recebe este pacote. */
    public static void handleZoneShow(final dev.rptag.ZoneShowPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> ZoneVisualizer.aplicar(payload));
    }

    public static void handleLorezoneMusic(final dev.rptag.LorezoneMusicPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.tocar()) {
                // (3.99.0) a musica tem TEMPO MAXIMO (nunca fica eterna)
                MusicaCustom.definirLimite(payload.maxSeg());
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
            // (3.99.0) o prazo comeca a contar JA (inclui o download)
            MusicaCustom.definirLimite(payload.maxSeg());
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
