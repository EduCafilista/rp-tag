package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (3.55.0) Sync de musica CLIENTE->SERVIDOR: RESPOSTA.
 *
 * <p>Responde a {@link MusicFileQueryPayload}: {@code tem=true} (o cliente ja
 * tem o arquivo e vai tocar) ou {@code tem=false} (manda os bytes!).
 *
 * @param nome nome do ARQUIVO com extensao
 * @param tem  true = ja tenho e ja estou tocando
 */
public record MusicFileAnswerPayload(String nome, boolean tem) implements CustomPacketPayload {

    public static final Type<MusicFileAnswerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "music_answer"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, MusicFileAnswerPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, MusicFileAnswerPayload::nome,
                    ByteBufCodecs.BOOL, MusicFileAnswerPayload::tem,
                    MusicFileAnswerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
