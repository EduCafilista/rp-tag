package dev.rptag.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.BubbleStyle;
import dev.rptag.RPTagMod;
import dev.rptag.Stickers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

/**
 * Balao de fala estilo QUADRINHOS — desenhado com TEXTURA SUAVE (pílula
 * arredondada anti-aliased + rabicho curvado), tingida com as cores do
 * jogador: recheio da cor de dentro, contorno da cor da borda e sombra.
 *
 * <p>O adesivo fica em QUALQUER ponto do balao (posicao % salva pelo editor).
 * Tudo em FULLBRIGHT (legivel no escuro), a prova de crash (qualquer erro
 * desliga os baloes com aviso no log; o jogo NUNCA fecha).
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class BubbleRenderer {

    private static final int MAX_TEXT_WIDTH = 110;
    private static final int LINE_HEIGHT = 10;

    /** Textura da pílula (64x32, branca com alfa suave — tingida na hora). */
    private static final ResourceLocation BUBBLE_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble.png");
    /** Textura do rabicho (16x16, triângulo curvado apontando pra baixo). */
    private static final ResourceLocation TAIL_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble_tail.png");

    private static final RenderType BUBBLE_TYPE = RenderType.text(BUBBLE_TEX);
    private static final RenderType TAIL_TYPE = RenderType.text(TAIL_TEX);

    // regiao da pilula dentro do bubble.png (y 2..28 de 32; tampa = 16px de 64)
    private static final float V1 = 2.0F / 32.0F;
    private static final float V2 = 28.0F / 32.0F;
    private static final float U_CAP = 16.0F / 64.0F;

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
    private static float drawLine(Font font, Matrix4f matrix, MultiBufferSource buffers,
            List<Seg> segs, float x, float y, int textColor) {
        float pen = x;
        for (Seg seg : segs) {
            int color = seg.sticker ? 0xFFFFFFFF : textColor;
            pen = font.drawInBatch(seq(seg), pen, y, color, true, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        return pen;
    }

    /**
     * Um retangulo da pílula por 3 fatias da textura (tampa esquerda + meio
     * esticado + tampa direita): as pontas arredondadas NUNCA deformam.
     */
    private static void pill(Matrix4f m, MultiBufferSource buffers, float z,
            float x, float y, float w, float h, int argb) {
        if ((argb >>> 24) == 0 || w < 2) {
            return;
        }
        VertexConsumer buf = buffers.getBuffer(BUBBLE_TYPE);
        float cap = Math.min(16.0F, w / 2.0F);
        quad(m, buf, z, x, y, x + cap, y + h, 0.0F, V1, U_CAP, V2, argb);
        quad(m, buf, z, x + cap, y, x + w - cap, y + h, U_CAP, V1, 1.0F - U_CAP, V2, argb);
        quad(m, buf, z, x + w - cap, y, x + w, y + h, 1.0F - U_CAP, V1, 1.0F, V2, argb);
    }

    /** O rabicho (textura 16x16 desenhada em w x h, centralizado em cx). */
    private static void tail(Matrix4f m, MultiBufferSource buffers, float z,
            float cx, float yTop, float w, float h, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        quad(m, buffers.getBuffer(TAIL_TYPE), z, cx - w / 2.0F, yTop, cx + w / 2.0F, yTop + h,
                0.0F, 0.0F, 1.0F, 1.0F, argb);
    }

    private static void quad(Matrix4f m, VertexConsumer buf, float z,
            float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int argb) {
        buf.addVertex(m, x1, y1, z).setColor(argb).setUv(u1, v1).setUv1(0, 10).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
        buf.addVertex(m, x1, y2, z).setColor(argb).setUv(u1, v2).setUv1(0, 10).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
        buf.addVertex(m, x2, y2, z).setColor(argb).setUv(u2, v2).setUv1(0, 10).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
        buf.addVertex(m, x2, y1, z).setColor(argb).setUv(u2, v1).setUv1(0, 10).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
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
        // (3.10.0) VARIOS EMOJIS: os extras acompanham a fala DENTRO do balao,
        // depois do texto — cada um e arte colorida de verdade, como no celular.
        String speech = bubble.text();
        if (!bubble.extras().isEmpty()) {
            speech = speech + " " + bubble.extras();
        }
        List<FormattedCharSequence> lines = font.split(Stickers.styled(speech), MAX_TEXT_WIDTH);
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
        int textColor = bubble.textRGB() == BubbleStyle.TEXT_AUTO
                ? BubbleBackgrounds.textColorFor(colorRGB)
                : (0xFF000000 | bubble.textRGB());

        // ---- geometria (pixels inteiros) ----
        int textW = 0;
        for (FormattedCharSequence line : lines) {
            textW = Math.max(textW, font.width(line));
        }
        int n = lines.size();
        int pillW = Math.max(32, textW + 14);
        int pillH = n * LINE_HEIGHT + 6;
        float x0 = -pillW / 2.0F;
        float yText = 3 - LINE_HEIGHT * n;      // topo da 1a linha de texto
        float yP0 = yText - 3;                  // topo da pilula
        float yP1 = yText + LINE_HEIGHT * n + 3; // base da pilula

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0, player.getBbHeight() + 0.95, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.030F, -0.030F, 0.030F);
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource buffers = event.getMultiBufferSource();

        // ---- SOMBRA ----
        pill(matrix, buffers, -0.20F, x0 + 2, yP0 + 2, pillW, pillH, 0x40000000);
        tail(matrix, buffers, -0.20F, 0, yP1 - 1, 12, 12, 0x40000000);

        // ---- CONTORNO (3 ESTILOS: Classica / Cartum grossa / Dupla) ----
        int bs = Math.max(0, bubble.borderStyle());
        if (border != 0) {
            if (bs == 1) { // CARTUM: tinta grossa, capa de quadrinho
                pill(matrix, buffers, -0.15F, x0 - 4, yP0 - 4, pillW + 8, pillH + 8, border);
                tail(matrix, buffers, -0.15F, 0, yP1 - 4, 18, 17, border);
            } else if (bs == 2) { // DUPLA: duas linhas finas com respiro
                pill(matrix, buffers, -0.15F, x0 - 1, yP0 - 1, pillW + 2, pillH + 2, border);
                pill(matrix, buffers, -0.14F, x0 - 4, yP0 - 4, pillW + 8, pillH + 8, border);
                tail(matrix, buffers, -0.15F, 0, yP1 - 4, 16, 15, border);
            } else { // CLASSICA: linha de tinta em toda a volta
                pill(matrix, buffers, -0.15F, x0 - 2, yP0 - 2, pillW + 4, pillH + 4, border);
                tail(matrix, buffers, -0.15F, 0, yP1 - 4, 16, 15, border);
            }
        }

        // ---- RECHEIO (a cor de dentro escolhida) ----
        pill(matrix, buffers, -0.10F, x0, yP0, pillW, pillH, fill);
        tail(matrix, buffers, -0.09F, 0, yP1 - 3, 12, 12, fill);

        // ---- texto + adesivos dentro da fala (sombra vanilla = legivel e limpo) ----
        for (int j = 0; j < n; j++) {
            List<Seg> segs = segLines.get(j);
            int lineW = 0;
            for (Seg seg : segs) {
                lineW += font.width(seq(seg));
            }
            float lineX = -Math.round(lineW / 2.0F);
            float penY = yText + LINE_HEIGHT * j;
            drawLine(font, matrix, buffers, segs, lineX, penY, textColor);
        }

        // ---- ADESIVO LIVRE: em qualquer ponto do balao (posicao % salva) ----
        if (!bubble.corner().isEmpty()) {
            var sticker = Stickers.styled(bubble.corner());
            float sw = font.width(sticker);
            float sx = x0 + bubble.stickerX() / 100.0F * pillW;
            float sy = yP0 + bubble.stickerY() / 100.0F * pillH;
            font.drawInBatch(sticker, sx - sw / 2.0F, sy - 4.5F, 0xFFFFFFFF, true,
                    matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }

        poseStack.popPose();
    }
}
