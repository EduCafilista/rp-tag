package dev.rptag.aura;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** (5.70.0) Servidor -> clientes proximos: este jogador comecou a lancar o Prisma Celestial. */
public record PrismaFxPayload(int quemLanca) implements CustomPacketPayload {
    public static final Type<PrismaFxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "prisma_fx"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, PrismaFxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PrismaFxPayload::quemLanca,
            PrismaFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
