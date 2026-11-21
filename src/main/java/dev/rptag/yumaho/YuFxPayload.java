package dev.rptag.yumaho;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> clientes próximos: só visual (raios, partículas, tremor). */
// (5.78.0) o campo do record NAO pode se chamar `type` (colide com o type() do CustomPacketPayload)
public record YuFxPayload(int kind, double x, double y, double z, double x2, double y2, double z2, float a, float b)
        implements CustomPacketPayload {
    public static final int HIT = 0, RELEASE = 1, WHIP = 2, GEAR = 3,
            /** (5.78.5) a ponta do chicote estalou (bateu em algo). */
            CRACK = 4;

    public static final Type<YuFxPayload> TYPE = new Type<>(YuMaho.rl("fx"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, YuFxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.kind);
                buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z);
                buf.writeDouble(p.x2); buf.writeDouble(p.y2); buf.writeDouble(p.z2);
                buf.writeFloat(p.a); buf.writeFloat(p.b);
            },
            buf -> new YuFxPayload(ByteBufCodecs.VAR_INT.decode(buf),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
