package dev.rptag.aura;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> clientes próximos: impacto de queda gravitacional em (x,y,z) com o raio dado. */
public record GravityImpactFxPayload(double x, double y, double z, float radius) implements CustomPacketPayload {
    public static final Type<GravityImpactFxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "gravity_impact_fx"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, GravityImpactFxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, GravityImpactFxPayload::x,
            ByteBufCodecs.DOUBLE, GravityImpactFxPayload::y,
            ByteBufCodecs.DOUBLE, GravityImpactFxPayload::z,
            ByteBufCodecs.FLOAT, GravityImpactFxPayload::radius,
            GravityImpactFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
