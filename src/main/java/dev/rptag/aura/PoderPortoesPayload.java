package dev.rptag.aura;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** (5.60.0) cliente -> servidor: "apertei a tecla do poder com a aura ligada". */
public record PoderPortoesPayload() implements CustomPacketPayload {
    public static final Type<PoderPortoesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "iluminacao_portoes"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, PoderPortoesPayload> CODEC =
            StreamCodec.unit(new PoderPortoesPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
