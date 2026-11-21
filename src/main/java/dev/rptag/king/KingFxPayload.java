package dev.rptag.king;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor -> clientes: só animação. Campos genéricos por tipo:
 * DECREE (x,y,z; a=raio) · CHAINS (id=alvo; a=ticks) · PORTAL (x,y,z; a=0 saída/1 chegada) · CONFUSE (a=ticks)
 * POOF (x,y,z) · CHARGE (id=dono; a=ticks; b=id do alvo) · BEAM (x,y,z -> x2,y2,z2; a=espessura)
 * METEOR (x,y,z; a=atraso em ticks; b=raio) · ZONE (x,y,z; a=raio; b=ticks) · REALM (x,y,z; a=raio; x2=início, y2=fim em game time)
 * WAVE (x,y,z; a=raio máximo)
 */
public record KingFxPayload(int kind, int id, double x, double y, double z, double x2, double y2, double z2, float a, float b)
        implements CustomPacketPayload {
    public static final int DECREE = 0, CHAINS = 1, PORTAL = 2, CONFUSE = 3, POOF = 4, CHARGE = 5,
            BEAM = 6, METEOR = 7, ZONE = 8, REALM = 9, WAVE = 10;

    public static final Type<KingFxPayload> TYPE = new Type<>(King.rl("fx"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, KingFxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.kind);
                buf.writeInt(p.id);
                buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z);
                buf.writeDouble(p.x2); buf.writeDouble(p.y2); buf.writeDouble(p.z2);
                buf.writeFloat(p.a); buf.writeFloat(p.b);
            },
            buf -> new KingFxPayload(ByteBufCodecs.VAR_INT.decode(buf), buf.readInt(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
