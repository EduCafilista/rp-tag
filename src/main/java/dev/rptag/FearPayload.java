package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Pacote S2C: efeito MEDO — a tela do jogador treme nas extremidades
 * (camera balanca + vinheta vermelha pulsando nas bordas).
 *
 * @param seconds duracao em segundos (servidor clampa 1..30)
 */
public record FearPayload(int seconds) implements CustomPacketPayload {

    public static final Type<FearPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "fear"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, FearPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> ByteBufCodecs.VAR_INT.encode(buf, p.seconds()),
                    buf -> new FearPayload(ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
