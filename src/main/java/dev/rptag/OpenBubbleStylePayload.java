package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C: abre a tela de personalizacao do balao ({@code /balao}), ja com o
 * estilo atual do jogador preenchido.
 */
public record OpenBubbleStylePayload(int colorRGB, String prefix, String suffix, int background)
        implements CustomPacketPayload {

    public static final Type<OpenBubbleStylePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "open_bubble_style"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, OpenBubbleStylePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, p.colorRGB());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.prefix());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.suffix());
                        ByteBufCodecs.VAR_INT.encode(buf, p.background());
                    },
                    buf -> new OpenBubbleStylePayload(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)));

    public static OpenBubbleStylePayload of(BubbleStyle style) {
        return new OpenBubbleStylePayload(style.colorRGB(), style.prefix(), style.suffix(), style.background());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
