package dev.rptag;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * (3.55.0) SYNC DE MUSICA DO SERVIDOR: todo mundo ouve sem baixar nada.
 *
 * <p>O admin coloca o arquivo em {@code config/rptag/musicas/} do SERVIDOR
 * (na mao ou com {@code /lorezone baixar <url>}). Quando um jogador entra
 * numa zona com musica {@code @nome}, o servidor pergunta se o cliente ja
 * tem o arquivo; quem nao tem recebe os bytes em pedaços e toca na hora.
 * O arquivo fica em CACHE na memoria (primeiro uso le do disco).
 */
public final class MusicSync {

    private MusicSync() {
    }

    /** cache: nome do arquivo (com extensao) -> bytes. */
    private static final ConcurrentHashMap<String, byte[]> ARQ = new ConcurrentHashMap<>();

    /** Limite de segurança (32 MB por arquivo). */
    private static final int MAX = 32 * 1024 * 1024;

    /** @return os bytes do arquivo da pasta de musicas do SERVIDOR (ou null). */
    public static byte[] carregar(String nomeArq) {
        if (nomeArq == null || !nomeArq.matches("[a-zA-Z0-9_-]+\\.(ogg|wav)")) {
            return null;
        }
        byte[] emCache = ARQ.get(nomeArq);
        if (emCache != null) {
            return emCache;
        }
        try {
            Path p = Path.of("config", "rptag", "musicas").resolve(nomeArq);
            if (!Files.isRegularFile(p)) {
                return null;
            }
            byte[] b = Files.readAllBytes(p);
            if (b.length == 0 || b.length > MAX) {
                return null;
            }
            ARQ.put(nomeArq, b);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** (re)carrega o cache depois de baixar/gravar um arquivo novo. */
    public static void recarregar(String nomeArq, byte[] bytes) {
        if (bytes != null && bytes.length > 0 && bytes.length <= MAX) {
            ARQ.put(nomeArq, bytes);
        }
    }

    /**
     * Inicia a musica custom {@code @nomeSemExt} para o jogador: manda a
     * PERGUNTA (o cliente que tem o arquivo toca direto; quem nao tem baixa
     * do servidor em pedaços). Nomes tentam .ogg e .wav.
     */
    public static void pedir(ServerPlayer player, String nomeSemExt) {
        for (String ext : new String[]{".ogg", ".wav"}) {
            String arq = nomeSemExt + ext;
            byte[] b = carregar(arq);
            if (b != null) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        player, new MusicFileQueryPayload(arq, b.length));
                return;
            }
        }
        player.sendSystemMessage(Component.literal(
                "\u00a77[\u00a7dRP Tag\u00a77] \u00a7cmusica '@" + nomeSemExt
                        + "' nao existe no SERVIDOR (pasta config/rptag/musicas) — "
                        + "admin: /lorezone baixar <url>"));
    }

    /**
     * Resposta do cliente: se ele NAO tem o arquivo, envia todos os pedaços
     * (o pacote custom e limitado a ~32 KB, entao mandamos em fatias de 30 KB).
     */
    public static void responder(ServerPlayer player, String nomeArq, boolean tem) {
        if (tem) {
            return; // o cliente ja esta tocando
        }
        byte[] b = carregar(nomeArq);
        if (b == null) {
            return;
        }
        int total = (b.length + MusicFileChunkPayload.CHUNK - 1) / MusicFileChunkPayload.CHUNK;
        for (int i = 0; i < total; i++) {
            int off = i * MusicFileChunkPayload.CHUNK;
            int len = Math.min(MusicFileChunkPayload.CHUNK, b.length - off);
            byte[] parte = Arrays.copyOfRange(b, off, off + len);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new MusicFileChunkPayload(nomeArq, total, i, parte));
        }
    }
}
