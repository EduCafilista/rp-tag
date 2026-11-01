package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.5.0) Pacote S2C do PODER DO VOID: a tela da vítima escurece até o preto total
 * em {@code ticks} ticks (o servidor executa a ação quando o tempo acaba).
 */
public record VoidPayload(int ticks) implements CustomPacketPayload {
    public static final Type<VoidPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "void_poder"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, VoidPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> ByteBufCodecs.VAR_INT.encode(buf, p.ticks()),
                    buf -> new VoidPayload(ByteBufCodecs.VAR_INT.decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
