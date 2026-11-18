package dev.rptag.aura;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> todos os clientes: o terremoto (céu carmesim, lua de sangue, tela tremendo) ligou/desligou. */
public record QuakeStatePayload(boolean active, float intensity) implements CustomPacketPayload {
    public static final Type<QuakeStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "quake_state"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, QuakeStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, QuakeStatePayload::active,
            ByteBufCodecs.FLOAT, QuakeStatePayload::intensity,
            QuakeStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
