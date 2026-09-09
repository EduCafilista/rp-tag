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
 * @param colorRGB    cor de destaque do balao (24 bits, 0xRRGGBB)
 * @param borderRGB   cor da barra (0xRRGGBB), -1 sem, -2 auto
 * @param italic      texto em italico (usado em /me e sussurros)
 * @param prefix      adesivo antes (ja embutido no texto; fica "" aqui)
 * @param suffix      adesivo depois (ja embutido no texto; fica "" aqui)
 * @param background  estilo de fundo (0-6, veja {@link BubbleStyle})
 * @param durationMs  tempo de exibicao em milissegundos
 */
public record ChatBubblePayload(UUID playerId, String text, int colorRGB, int borderRGB, boolean italic,
        String prefix, String suffix, int background, int durationMs) implements CustomPacketPayload {

    public static final Type<ChatBubblePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "chat_bubble"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ChatBubblePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        net.minecraft.core.UUIDUtil.STREAM_CODEC.encode(buf, p.playerId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.text());
                        ByteBufCodecs.VAR_INT.encode(buf, p.colorRGB());
                        ByteBufCodecs.VAR_INT.encode(buf, p.borderRGB());
                        ByteBufCodecs.BOOL.encode(buf, p.italic());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.prefix());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.suffix());
                        ByteBufCodecs.VAR_INT.encode(buf, p.background());
                        ByteBufCodecs.VAR_INT.encode(buf, p.durationMs());
                    },
                    buf -> new ChatBubblePayload(
                            net.minecraft.core.UUIDUtil.STREAM_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
