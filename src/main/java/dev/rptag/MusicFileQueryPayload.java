package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (3.55.0) Sync de musica SERVIDOR->CLIENTE, passo 1: PERGUNTA.
 *
 * <p>O servidor tem o arquivo em {@code config/rptag/musicas/<nome>} (baixado
 * com {@code /lorezone baixar} ou enviado pelo admin) e quer que o jogador
 * ouca. Pergunta: "voce ja tem <nome> com <size> bytes?" — o cliente responde
 * com {@link MusicFileAnswerPayload}: se ja tem, toca na hora; se nao tem, o
 * servidor manda os bytes em pedaços ({@link MusicFileChunkPayload}).
 *
 * @param nome nome do ARQUIVO com extensao (ex.: "musica-x.ogg")
 * @param size tamanho esperado em bytes
 */
public record MusicFileQueryPayload(String nome, long size) implements CustomPacketPayload {

    public static final Type<MusicFileQueryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "music_query"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, MusicFileQueryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, MusicFileQueryPayload::nome,
                    ByteBufCodecs.VAR_LONG, MusicFileQueryPayload::size,
                    MusicFileQueryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
