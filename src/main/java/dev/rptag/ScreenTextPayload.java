package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Pacote S2C: texto GRANDE no centro da tela (aviso RP — ex.: "voce esta
 * sendo observado"). O texto ja vem com os codigos de cor (& -> section)
 * traduzidos pelo SERVIDOR.
 *
 * @param text    texto com codigos de cor (<= 160 chars)
 * @param seconds duracao em segundos (servidor clampa 1..60)
 */
public record ScreenTextPayload(String text, int seconds) implements CustomPacketPayload {

    public static final Type<ScreenTextPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "screen_text"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ScreenTextPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.text());
                        ByteBufCodecs.VAR_INT.encode(buf, p.seconds());
                    },
                    buf -> new ScreenTextPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
