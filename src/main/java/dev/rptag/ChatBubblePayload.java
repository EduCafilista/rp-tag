package dev.rptag;

import java.util.UUID;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Pacote S2C: balao de fala (chat bubble) para mostrar sobre a cabeca
 * de um jogador.
 *
 * @param playerId quem falou
 * @param text     texto do balao (ja truncado pelo servidor)
 * @param colorRGB cor de fundo do balao (24 bits, 0xRRGGBB)
 * @param italic   texto em italico (usado em /me e sussurros)
 */
public record ChatBubblePayload(UUID playerId, String text, int colorRGB, boolean italic)
        implements CustomPacketPayload {

    public static final Type<ChatBubblePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "chat_bubble"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ChatBubblePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        net.minecraft.core.UUIDUtil.STREAM_CODEC.encode(buf, p.playerId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.text());
                        ByteBufCodecs.VAR_INT.encode(buf, p.colorRGB());
                        ByteBufCodecs.BOOL.encode(buf, p.italic());
                    },
                    buf -> new ChatBubblePayload(
                            net.minecraft.core.UUIDUtil.STREAM_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
