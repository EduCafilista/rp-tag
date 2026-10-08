package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (3.55.0) Sync de musica SERVIDOR->CLIENTE, passo 2: PEDAÇO.
 *
 * <p>Um pedaço do arquivo (ate ~30 KB). O cliente monta todos os
 * {@code total} pedaços, grava {@code config/rptag/musicas/<nome>} e toca.
 *
 * @param nome   nome do ARQUIVO com extensao
 * @param total  quantidade total de pedaços
 * @param indice indice deste pedaço (0-based)
 * @param dados  bytes deste pedaço
 */
public record MusicFileChunkPayload(String nome, int total, int indice, byte[] dados)
        implements CustomPacketPayload {

    public static final int CHUNK = 30_000;

    public static final Type<MusicFileChunkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "music_chunk"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, MusicFileChunkPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, MusicFileChunkPayload::nome,
                    ByteBufCodecs.VAR_INT, MusicFileChunkPayload::total,
                    ByteBufCodecs.VAR_INT, MusicFileChunkPayload::indice,
                    ByteBufCodecs.BYTE_ARRAY, MusicFileChunkPayload::dados,
                    MusicFileChunkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
