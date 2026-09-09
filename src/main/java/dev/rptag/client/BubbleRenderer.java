package dev.rptag.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.rptag.RPTagMod;
import dev.rptag.Stickers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

/**
 * Balao de fala (design "cartao"): cartao com cantos chanfrados, sombra,
 * barra de destaque com a cor do jogador, ADESIVOS COLORIDOS inline (fonte
 * de bitmap) e texto com contraste automatico.
 *
 * <p>PIXEL-PERFECT (2.6.0): todas as posicoes sao numeros inteiros de pixel
 * (largura par e texto centralizado arredondado) — nada de meio-pixel borrado.
 *
 * <p>COMO E DESENHADO (a prova de bug): cada faixa do cartao e uma linha de
 * ESPACOS com cor de fundo via font.drawInBatch — o MESMO mecanismo do fundo
 * das nametags. Nenhum poligono custom. A PROVA DE CRASH: qualquer erro
 * desliga os baloes nesta sessao com aviso no log; o jogo NUNCA fecha.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class BubbleRenderer {

    private static final int MAX_TEXT_WIDTH = 110;
    private static final int LINE_HEIGHT = 10;

    /** O rabicho do balao: um triangulo da propria fonte do jogo. */
    private static final String TAIL_GLYPH = "▼";

    private BubbleRenderer() {
    }

    private static boolean disabled = false;

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (disabled) {
            return;
        }
        try {
            renderBubble(event);
        } catch (Throwable t) {
            // A PROVA DE CRASH: qualquer erro desliga os baloes desta sessao
            // (a tag RP, chat e comandos continuam funcionando) e registra o motivo.
            disabled = true;
            RPTagMod.LOGGER.error("RP Tag: erro ao desenhar balao de fala - baloes desligados ate reiniciar o jogo", t);
        }
    }

    /** Segmento de linha com o mesmo "tipo" (adesivo ou texto). */
    private static class Seg {
        final boolean sticker;
        final StringBuilder text = new StringBuilder();

        Seg(boolean sticker) {
            this.sticker = sticker;
        }
    }

    /** Quebra a linha em segmentos homogeneos (adesivo x texto comum). */
    private static List<Seg> segment(FormattedCharSequence line) {
        List<Seg> segs = new ArrayList<>();
        line.accept((idx, style, codePoint) -> {
            boolean st = Stickers.isStickerChar(codePoint);
            if (segs.isEmpty() || segs.get(segs.size() - 1).sticker != st) {
                segs.add(new Seg(st));
            }
            segs.get(segs.size() - 1).text.appendCodePoint(codePoint);
            return true;
        });
        return segs;
    }

    /** FormattedCharSequence do segmento (adesivos com a fonte de imagem). */
    private static FormattedCharSequence seq(Seg seg) {
        Style style = seg.sticker ? Stickers.stickerStyle() : Style.EMPTY;
        return sink -> {
            seg.text.codePoints().forEach(cp -> sink.accept(0, style, cp));
            return true;
        };
    }

    /** Desenha uma linha por segmentos: adesivos em BRANCO (cor verdadeira) e texto na cor normal. */
    private static float drawLine(Font font, Matrix4f matrix, net.minecraft.client.renderer.MultiBufferSource buffers,
            int light, List<Seg> segs, float x, float y, int textColor, boolean shadow) {
        float pen = x;
        for (Seg seg : segs) {
            int color = seg.sticker ? 0xFFFFFFFF : textColor;
            pen = font.drawInBatch(seq(seg), pen, y, color, shadow, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0, light);
        }
        return pen;
    }

    /** Versao apagada (atraves de paredes) dos segmentos. */
    private static void drawLineDimmed(Font font, Matrix4f matrix, net.minecraft.client.renderer.MultiBufferSource buffers,
            int light, List<Seg> segs, float x, float y, int dimmedText) {
        float pen = x;
        for (Seg seg : segs) {
            int color = seg.sticker ? 0x50FFFFFF : dimmedText;
            pen = font.drawInBatch(seq(seg), pen, y, color, false, matrix, buffers,
                    Font.DisplayMode.SEE_THROUGH, 0, light);
        }
    }

    /**
     * Desenha uma faixa do cartao: linha de espacos com cor de fundo.
     * A faixa fica com 4*n+2 px de largura e 10px de altura.
     */
    private static void band(Font font, Matrix4f matrix, net.minecraft.client.renderer.MultiBufferSource buffers,
            int light, float x, float y, int spaces, int argb) {
        if (spaces <= 0 || (argb >>> 24) == 0) {
            return;
        }
        font.drawInBatch(" ".repeat(spaces), x, y, 0, false, matrix,
                buffers, Font.DisplayMode.NORMAL, argb, light);
    }

    private static void renderBubble(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ClientBubbleCache.Bubble bubble = ClientBubbleCache.get(player.getUUID());
        if (bubble == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getEntityRenderDispatcher().distanceToSqr(player) > 64.0 * 64.0) {
            return;
        }

        Font font = minecraft.font;
        // texto com ADESIVOS: cada caractere-adesivo vira imagem colorida
        List<FormattedCharSequence> lines = font.split(Stickers.styled(bubble.text()), MAX_TEXT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }
        List<List<Seg>> segLines = new ArrayList<>();
        for (FormattedCharSequence line : lines) {
            segLines.add(segment(line));
        }

        int background = bubble.background();
        int colorRGB = bubble.colorRGB() & 0xFFFFFF;
        int fillBase = BubbleBackgrounds.bgColorFor(background, colorRGB);
        int accent = BubbleBackgrounds.accentColorFor(background, colorRGB);
        int barColor = BubbleBackgrounds.borderColorFor(background, colorRGB, bubble.borderRGB());
        int accentOrBar = barColor != 0 ? barColor : accent;
        int textColor = BubbleBackgrounds.textColorFor(background, colorRGB);
        int dimmedText = (textColor & 0x00FFFFFF) | 0x50000000;

        // medidas do cartao — SEMPRE em pixels inteiros e par (zero meio-pixel)
        int textW = 0;
        for (FormattedCharSequence line : lines) {
            textW = Math.max(textW, font.width(line));
        }
        int sFull = Math.max(2, (textW + 13) / 4);
        int bw = 4 * sFull + 2; // largura das faixas (par)
        float x0 = -bw / 2.0F;  // inteiro
        int n = lines.size();
        int top = 2 - 10 * n;   // base do cartao fica ~2px acima da origem (nametag)

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0, player.getBbHeight() + 0.95, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        var buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();

        // ---- cartao: sombra, faixas de fundo (gradiente real) e barra de cor ----
        for (int j = 0; j < n; j++) {
            boolean chamfer = (j == 0 || j == n - 1);
            float bx = x0 + (chamfer ? 4 : 0);
            float by = top + 1 + 10 * j;
            int s = sFull - (chamfer ? 2 : 0);

            // sombra deslocada (profundidade)
            band(font, matrix, buffers, light, bx + 2, by + 2, s, 0x5A000000);

            // fundo (gradiente pinta linha a linha com a cor do jogador)
            int rowFill = background == BubbleBackgrounds.BG_GRADIENTE
                    ? BubbleBackgrounds.gradientColorFor(colorRGB, n == 1 ? 0.5F : j / (float) (n - 1))
                    : fillBase;
            band(font, matrix, buffers, light, bx, by, s, rowFill);

            // barra de destaque com a cor escolhida
            band(font, matrix, buffers, light, bx + 2, by, 1, accentOrBar);
        }

        // rabicho ▼ logo abaixo do cartao
        int tailW = font.width(TAIL_GLYPH);
        font.drawInBatch(TAIL_GLYPH, -tailW / 2.0F, top + 10 * n + 3, fillBase,
                false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);

        // ---- texto + adesivos (passada apagada atravessando paredes + passada com sombra) ----
        int penY = top + 3;
        for (List<Seg> segs : segLines) {
            int lineW = 0;
            for (Seg seg : segs) {
                Style style = seg.sticker ? Stickers.stickerStyle() : Style.EMPTY;
                lineW += font.width(new StringBuilder(seg.text).codePoints()
                        .collect(() -> new StringBuilder(), StringBuilder::appendCodePoint, StringBuilder::append)
                        .toString());
            }
            // centralizacao em pixel inteiro (Math.round evita meio-pixel borrado)
            float lineX = -Math.round(lineW / 2.0F);
            drawLineDimmed(font, matrix, buffers, light, segs, lineX, penY, dimmedText);
            drawLine(font, matrix, buffers, light, segs, lineX, penY, textColor, true);
            penY += LINE_HEIGHT;
        }

        poseStack.popPose();
    }
}
