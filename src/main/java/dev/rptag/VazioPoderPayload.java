package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.29.0) Pacote S2C dos <b>poderes do vazio</b>: manda o evento visual para quem está perto
 * (o raio carmesim, o orbe, a onda, a rajada de olhos e o portal do julgamento).
 *
 * @param tipo 0 = raio, 1 = orbe, 2 = onda, 3 = olhos, 4 = portal do julgamento
 * @param ox/oy/oz onde começa (ou onde o portal está)
 * @param tx/ty/tz onde termina (alvo do raio/orbe; no portal, igual à origem)
 */
public record VazioPoderPayload(int tipo, double ox, double oy, double oz,
        double tx, double ty, double tz) implements CustomPacketPayload {

    public static final Type<VazioPoderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "vazio_poder"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, VazioPoderPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, p.tipo());
                        ByteBufCodecs.DOUBLE.encode(buf, p.ox());
                        ByteBufCodecs.DOUBLE.encode(buf, p.oy());
                        ByteBufCodecs.DOUBLE.encode(buf, p.oz());
                        ByteBufCodecs.DOUBLE.encode(buf, p.tx());
                        ByteBufCodecs.DOUBLE.encode(buf, p.ty());
                        ByteBufCodecs.DOUBLE.encode(buf, p.tz());
                    },
                    buf -> new VazioPoderPayload(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.DOUBLE.decode(buf),
                            ByteBufCodecs.DOUBLE.decode(buf),
                            ByteBufCodecs.DOUBLE.decode(buf),
                            ByteBufCodecs.DOUBLE.decode(buf),
                            ByteBufCodecs.DOUBLE.decode(buf),
                            ByteBufCodecs.DOUBLE.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
