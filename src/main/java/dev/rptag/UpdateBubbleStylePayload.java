package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Pacote C2S: jogador salvou o estilo do balao na tela de personalizacao
 * (cor de dentro, cor da borda, adesivos antes/meio/depois/canto e duracao).
 */
public record UpdateBubbleStylePayload(int colorRGB, int borderRGB, int borderStyle, int textColorRGB,
        String prefix, String mid, String suffix, String corner, String extras, int stickerX,
        int stickerY, int background, int durationSec)
        implements CustomPacketPayload {

    public static final Type<UpdateBubbleStylePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "update_bubble_style"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, UpdateBubbleStylePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, p.colorRGB());
                        ByteBufCodecs.VAR_INT.encode(buf, p.borderRGB());
                        ByteBufCodecs.VAR_INT.encode(buf, p.borderStyle());
                        ByteBufCodecs.VAR_INT.encode(buf, p.textColorRGB());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.prefix());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.mid());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.suffix());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.corner());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.extras());
                        ByteBufCodecs.VAR_INT.encode(buf, p.stickerX());
                        ByteBufCodecs.VAR_INT.encode(buf, p.stickerY());
                        ByteBufCodecs.VAR_INT.encode(buf, p.background());
                        ByteBufCodecs.VAR_INT.encode(buf, p.durationSec());
                    },
                    buf -> new UpdateBubbleStylePayload(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
