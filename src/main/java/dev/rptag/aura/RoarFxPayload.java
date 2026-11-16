package dev.rptag.aura;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> clientes próximos: inicia a onda visual do rugido na posição dada. */
public record RoarFxPayload(double x, double y, double z) implements CustomPacketPayload {
    public static final Type<RoarFxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "roar_fx"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, RoarFxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, RoarFxPayload::x,
            ByteBufCodecs.DOUBLE, RoarFxPayload::y,
            ByteBufCodecs.DOUBLE, RoarFxPayload::z,
            RoarFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
