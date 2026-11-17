package dev.rptag.aura;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** (5.70.0) Cliente -> servidor: "apertei a tecla do Prisma Celestial". */
public record PrismaPayload() implements CustomPacketPayload {
    public static final Type<PrismaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "prisma"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, PrismaPayload> CODEC =
            StreamCodec.unit(new PrismaPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
