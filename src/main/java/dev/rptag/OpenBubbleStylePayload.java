package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Pacote S2C: abre a tela de personalizacao do balao com o estilo atual
 * do jogador (cores, adesivos e duracao).
 */
public record OpenBubbleStylePayload(int colorRGB, int borderRGB, int borderStyle, String prefix, String mid,
        String suffix, String corner, int stickerX, int stickerY, int background, int durationSec)
        implements CustomPacketPayload {

    public static final Type<OpenBubbleStylePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "open_bubble_style"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, OpenBubbleStylePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, p.colorRGB());
                        ByteBufCodecs.VAR_INT.encode(buf, p.borderRGB());
                        ByteBufCodecs.VAR_INT.encode(buf, p.borderStyle());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.prefix());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.mid());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.suffix());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.corner());
                        ByteBufCodecs.VAR_INT.encode(buf, p.stickerX());
                        ByteBufCodecs.VAR_INT.encode(buf, p.stickerY());
                        ByteBufCodecs.VAR_INT.encode(buf, p.background());
                        ByteBufCodecs.VAR_INT.encode(buf, p.durationSec());
                    },
                    buf -> new OpenBubbleStylePayload(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)));

    public static OpenBubbleStylePayload of(BubbleStyle style) {
        return new OpenBubbleStylePayload(style.colorRGB(), style.borderRGB(), style.borderStyle(),
                style.prefix(), style.mid(), style.suffix(), style.corner(), style.stickerX(), style.stickerY(),
                style.background(), style.durationSec());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
