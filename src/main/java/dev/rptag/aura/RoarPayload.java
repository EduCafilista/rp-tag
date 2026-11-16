package dev.rptag.aura;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente -> servidor: "apertei a tecla do rugido". */
public record RoarPayload() implements CustomPacketPayload {
    public static final Type<RoarPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "roar"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, RoarPayload> CODEC =
            StreamCodec.unit(new RoarPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
