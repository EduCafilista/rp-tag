package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: o cliente manda o estilo do balao montado na tela de personalizacao
 * ({@code /balao}). O servidor valida, salva e confirma.
 */
public record UpdateBubbleStylePayload(int colorRGB, String prefix, String suffix, int background)
        implements CustomPacketPayload {

    public static final Type<UpdateBubbleStylePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "update_bubble_style"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, UpdateBubbleStylePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, p.colorRGB());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.prefix());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.suffix());
                        ByteBufCodecs.VAR_INT.encode(buf, p.background());
                    },
                    buf -> new UpdateBubbleStylePayload(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
