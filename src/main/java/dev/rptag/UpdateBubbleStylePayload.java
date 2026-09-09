package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Pacote C2S: jogador salvou o estilo do balao na tela de personalizacao
 * (seletor de cor, barra, adesivos antes/meio/depois, fundo e duracao).
 */
public record UpdateBubbleStylePayload(int colorRGB, int borderRGB, String prefix, String mid,
        String suffix, int background, int durationSec) implements CustomPacketPayload {

    public static final Type<UpdateBubbleStylePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "update_bubble_style"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, UpdateBubbleStylePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, p.colorRGB());
                        ByteBufCodecs.VAR_INT.encode(buf, p.borderRGB());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.prefix());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.mid());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.suffix());
                        ByteBufCodecs.VAR_INT.encode(buf, p.background());
                        ByteBufCodecs.VAR_INT.encode(buf, p.durationSec());
                    },
                    buf -> new UpdateBubbleStylePayload(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
