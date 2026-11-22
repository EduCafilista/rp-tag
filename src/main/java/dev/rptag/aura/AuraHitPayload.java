package dev.rptag.aura;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> clientes próximos: efeito de golpe da aura (style = AuraEstilo.index()) em (x,y,z); (dx,dz) = direção do golpe. */
public record AuraHitPayload(int style, double x, double y, double z, float dx, float dz) implements CustomPacketPayload {
    public static final Type<AuraHitPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "aura_hit_fx"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, AuraHitPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AuraHitPayload::style,
            ByteBufCodecs.DOUBLE, AuraHitPayload::x,
            ByteBufCodecs.DOUBLE, AuraHitPayload::y,
            ByteBufCodecs.DOUBLE, AuraHitPayload::z,
            ByteBufCodecs.FLOAT, AuraHitPayload::dx,
            ByteBufCodecs.FLOAT, AuraHitPayload::dz,
            AuraHitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
