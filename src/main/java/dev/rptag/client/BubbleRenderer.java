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
 * Balao de fala no ESTILO CLASSICO de quadrinhos: pílula ARREDONDADA com a
 * cor de DENTRO escolhida pelo jogador, CONTORNO da cor da borda, sombra,
 * rabicho e ADESIVOS coloridos (antes / meio / depois / CANTO estilo reacao).
 *
 * <p>PIXEL-PERFECT: todas as posicoes sao inteiros de pixel. Tudo desenhado
 * pelo proprio motor de texto do jogo (o mesmo mecanismo do fundo das
 * nametags) — a prova de invisivel e a prova de crash (qualquer erro desliga
 * os baloes com aviso no log; o jogo NUNCA fecha).
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
     * Faixa do balao: linha de espacos com cor de fundo (4*n+2 px por 10px de
     * altura). Se a cor tiver alpha, o desenho do jogo mistura igual tinta.
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
        List<FormattedCharSequence> lines = font.split(Stickers.styled(bubble.text()), MAX_TEXT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }
        List<List<Seg>> segLines = new ArrayList<>();
        for (FormattedCharSequence line : lines) {
            segLines.add(segment(line));
        }

        int colorRGB = bubble.colorRGB() & 0xFFFFFF;
        int fill = BubbleBackgrounds.fillColorFor(colorRGB);
        int border = BubbleBackgrounds.borderColorFor(colorRGB, bubble.borderRGB());
        int textColor = BubbleBackgrounds.textColorFor(colorRGB);
        int dimmedText = (textColor & 0x00FFFFFF) | 0x50000000;

        // ---- geometria da pílula (pixels inteiros, largura par) ----
        int textW = 0;
        for (FormattedCharSequence line : lines) {
            textW = Math.max(textW, font.width(line));
        }
        int sFull = Math.max(2, (textW + 13) / 4);
        int bw = 4 * sFull + 2;          // largura das faixas cheias (par)
        float x0 = -bw / 2.0F;           // inteiro
        int n = lines.size();
        int yTop = 3 - 10 * n;           // y da 1a faixa de texto (base ~3px acima da origem)

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0, player.getBbHeight() + 0.95, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        var buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();

        int sCap = sFull - 2;            // faixa "tampa" (arredonda as pontas)
        float xCap = x0 + 4;             // deslocada 4px pra dentro
        int yCapTop = yTop - 10;         // tampa de cima
        int yCapBot = yTop + 10 * n;     // tampa de baixo

        // ---- SOMBRA (profundidade) ----
        band(font, matrix, buffers, light, xCap + 1, yCapTop + 2, sCap, 0x56000000);
        for (int j = 0; j < n; j++) {
            band(font, matrix, buffers, light, x0 + 1, yTop + 10 * j + 2, sFull, 0x56000000);
        }
        band(font, matrix, buffers, light, xCap + 1, yCapBot + 2, sCap, 0x56000000);

        // ---- CONTORNO (anel de quadrinhos: exposto 2px acima/abaixo e nas laterais) ----
        if (border != 0) {
            band(font, matrix, buffers, light, xCap, yCapTop - 2, sCap, border);      // topo
            band(font, matrix, buffers, light, xCap, yCapBot + 2, sCap, border);      // base
            for (int j = 0; j < n; j++) {
                band(font, matrix, buffers, light, x0 - 4, yTop + 10 * j, 1, border); // lateral esq
                band(font, matrix, buffers, light, x0 + bw, yTop + 10 * j, 1, border);// lateral dir
            }
            band(font, matrix, buffers, light, xCap, yCapTop, sCap, border);          // tampa topo (atras do recheio)
            band(font, matrix, buffers, light, xCap, yCapBot, sCap, border);          // tampa base
        }

        // ---- RECHEIO (a cor de dentro escolhida) ----
        band(font, matrix, buffers, light, xCap, yCapTop, sCap, fill);
        for (int j = 0; j < n; j++) {
            band(font, matrix, buffers, light, x0, yTop + 10 * j, sFull, fill);
        }
        band(font, matrix, buffers, light, xCap, yCapBot, sCap, fill);

        // ---- rabicho ▼ ----
        int tailW = font.width(TAIL_GLYPH);
        font.drawInBatch(TAIL_GLYPH, -tailW / 2.0F, yCapBot + 9, fill,
                false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);

        // ---- ADESIVO DE CANTO (estilo reacao, no canto superior direito) ----
        if (!bubble.corner().isEmpty()) {
            var cornerSticker = Stickers.styled(bubble.corner());
            font.drawInBatch(cornerSticker, x0 + bw - 4, yCapTop - 9,
                    0xFFFFFFFF, true, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
        }

        // ---- texto + adesivos (passada apagada + passada com sombra) ----
        for (int j = 0; j < n; j++) {
            List<Seg> segs = segLines.get(j);
            int lineW = 0;
            for (Seg seg : segs) {
                lineW += font.width(seq(seg));
            }
            float lineX = -Math.round(lineW / 2.0F);
            float penY = yTop + 10 * j;
            drawLineDimmed(font, matrix, buffers, light, segs, lineX, penY, dimmedText);
            drawLine(font, matrix, buffers, light, segs, lineX, penY, textColor, true);
        }

        poseStack.popPose();
    }
}
