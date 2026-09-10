package dev.rptag;

import java.util.UUID;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Pacote S2C: balao de fala (chat bubble) para mostrar sobre a cabeca
 * de um jogador — ja vem com o estilo completo do autor.
 *
 * @param playerId    quem falou
 * @param text        texto do balao (ja truncado e decorado pelo servidor)
 * @param colorRGB    cor da parte de dentro (24 bits, 0xRRGGBB)
 * @param borderRGB   cor da borda (0xRRGGBB), -1 sem, -2 auto
 * @param italic      texto em italico (usado em /me e sussurros)
 * @param corner      adesivo LIVRE sobre o balao
 * @param extras      varios emojis acompanhando a fala (desenhados apos o texto)
 * @param stickerX    posicao do adesivo em % na largura do balao
 * @param stickerY    posicao do adesivo em % na altura do balao
 * @param background  (compatibilidade; o visual usa as cores)
 * @param durationMs  tempo de exibicao em milissegundos
 */
public record ChatBubblePayload(UUID playerId, String text, int colorRGB, int borderRGB, int borderStyle,
        int textColorRGB, boolean italic, String corner, String extras, int stickerX, int stickerY,
        int background, int durationMs)
        implements CustomPacketPayload {

    public static final Type<ChatBubblePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "chat_bubble"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ChatBubblePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        net.minecraft.core.UUIDUtil.STREAM_CODEC.encode(buf, p.playerId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.text());
                        ByteBufCodecs.VAR_INT.encode(buf, p.colorRGB());
                        ByteBufCodecs.VAR_INT.encode(buf, p.borderRGB());
                        ByteBufCodecs.VAR_INT.encode(buf, p.borderStyle());
                        ByteBufCodecs.VAR_INT.encode(buf, p.textColorRGB());
                        ByteBufCodecs.BOOL.encode(buf, p.italic());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.corner());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.extras());
                        ByteBufCodecs.VAR_INT.encode(buf, p.stickerX());
                        ByteBufCodecs.VAR_INT.encode(buf, p.stickerY());
                        ByteBufCodecs.VAR_INT.encode(buf, p.background());
                        ByteBufCodecs.VAR_INT.encode(buf, p.durationMs());
                    },
                    buf -> new ChatBubblePayload(
                            net.minecraft.core.UUIDUtil.STREAM_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
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
