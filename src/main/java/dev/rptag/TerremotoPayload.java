package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.4.0) Pacote S2C do TERREMOTO (colapso de Terras).
 *
 * @param acao  0 parar, 1 iniciar (com som), 2 retomar (sem som, ao entrar), 3 pulso, 4 levar
 * @param ticks duração da ação (pulso/levar), em ticks
 */
public record TerremotoPayload(int acao, int ticks) implements CustomPacketPayload {
    public static final int PARAR = 0, INICIAR = 1, RETOMAR = 2, PULSO = 3, LEVAR = 4;

    public static final Type<TerremotoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "terremoto"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, TerremotoPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { ByteBufCodecs.VAR_INT.encode(buf, p.acao()); ByteBufCodecs.VAR_INT.encode(buf, p.ticks()); },
                    buf -> new TerremotoPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
