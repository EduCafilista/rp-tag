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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

/**
 * Balao de fala estilo QUADRINHOS — desenhado com TEXTURA SUAVE (pilula
 * arredondada anti-aliased + rabicho curvado), tingida com as cores do
 * jogador: recheio da cor de dentro, contorno da cor da borda e sombra.
 *
 * <p>(3.11.0) ANIMACAO estilo mods famosos (Chat Bubbles/TalkBubbles):
 * POP-IN com ressalto ao nascer, FLUTUACAO suave pra cima e FADE na saida.
 * PILHA: ate 3 baloes por jogador — o mais novo perto da cabeca, os antigos
 * sobem. Tudo em FULLBRIGHT (legivel no escuro), a prova de crash (qualquer
 * erro desliga os baloes com aviso no log; o jogo NUNCA fecha).
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class BubbleRenderer {

    private static final int MAX_TEXT_WIDTH = 110;
    private static final int LINE_HEIGHT = 10;
    /** Linha com adesivo fica mais alta: o glifo da 3.11 tem 16px de arte. */
    private static final int STICKER_LINE_HEIGHT = 18;

    // ---- ANIMACAO (o padrao dos mods famosos) ----
    /** Duracao do POP-IN com ressalto. */
    private static final long POP_MS = 130;
    /** Tempo em que o balao sobe suavemente (depois para de flutuar). */
    private static final long FLOAT_MS = 3000;
    /** Quanto o balao sobe, em pixels de mundo (x 0.030 de escala). */
    private static final float FLOAT_UP_PX = 2.5F;
    /** Duracao do FADE no fim da vida do balao. */
    private static final long FADE_MS = 400;
    /** Respiro ENTRE baloes da pilha (a altura real de cada um ja e somada). */
    private static final float STACK_GAP_BLOCKS = 0.12F;

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

    /** Insere um espaco entre cada emoji (cada um legivel, nada de muro). */
    private static String spacedExtras(String s) {
        if (s.length() <= 2) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += 2) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(s, i, Math.min(i + 2, s.length()));
        }
        return sb.toString();
    }

    /** Escurece o RGB de uma cor ARGB (f 0..1) — pra aro externo da Dupla. */
    private static int shaded(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Multiplica o canal alfa de uma cor ARGB (k 0..1). */
    private static int withAlpha(int argb, float k) {
        int a = (int) (((argb >>> 24) & 0xFF) * Mth.clamp(k, 0.0F, 1.0F));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Escala do POP-IN (easeOutBack: cresce com ressalto e assenta em 1). */
    private static float popScale(long ageMs) {
        if (ageMs >= POP_MS) {
            return 1.0F;
        }
        float t = Math.max(0.0F, (float) ageMs / POP_MS);
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float e = 1.0F + c3 * (t - 1) * (t - 1) * (t - 1) + c1 * (t - 1) * (t - 1);
        return Math.max(0.05F, e);
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

    /**
     * Desenha uma linha por segmentos: adesivos em BRANCO (cor verdadeira,
     * herdando o alfa global) e texto na cor normal.
     */
    private static float drawLine(Font font, Matrix4f matrix, MultiBufferSource buffers,
            List<Seg> segs, float x, float y, int textColor) {
        float pen = x;
        for (Seg seg : segs) {
            // (3.15.0) CORRIGE O MURO PRETO: adesivo e ARTE COLORIDA e nunca
            // herda a cor da letra (letra preta transformava o emoji em bloco
            // preto solido). Alfa global apenas (fade).
            int color = seg.sticker ? withAlpha(0xFFFFFFFF, 1.0F) : textColor;
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

    /** TRACEJADA: tracinhos arredondados ao redor de todo o balao (+ rabicho em descada). */
    private static void dashedBorder(Matrix4f m, MultiBufferSource buffers, float z,
            float x0, float yP0, float pillW, float yP1, int argb) {
        float dash = 6.0F, gap = 6.0F, t = 4.0F;
        float yTop = yP0 - 3, yBot = yP1 + 3;
        float xL = x0 - 3, xR = x0 + pillW + 3;
        for (float x = xL; x < xR - 1; x += dash + gap) {
            float w = Math.min(dash, xR - x);
            pill(m, buffers, z, x, yTop, w, t, argb);
            pill(m, buffers, z, x, yBot - t, w, t, argb);
        }
        for (float y = yTop + 9; y < yBot - 9; y += dash + gap) {
            float h = Math.min(dash, yBot - 9 - y);
            if (h < 2) {
                break;
            }
            pill(m, buffers, z, xL, y, t, h, argb);
            pill(m, buffers, z, xR - t, y, t, h, argb);
        }
        // rabicho em descadinha (dois tracinhos caindo do centro)
        pill(m, buffers, z, -2.0F, yBot - 1, t, 7, argb);
        pill(m, buffers, z, 2.0F, yBot + 5, t, 7, argb);
    }

    /** NUVEM: bolas fofas PERTO do balao (3.17.0: menor, mais delicada). */
    private static void cloudBorder(Matrix4f m, MultiBufferSource buffers, float z,
            float x0, float yP0, float pillW, float pillH, float yP1, int argb) {
        // base inflada discreta
        pill(m, buffers, z, x0 - 3, yP0 - 3, pillW + 6, pillH + 6, argb);
        tail(m, buffers, z, 0, yP1 - 3, 15, 14, argb);
        // gomos pequenos e colados (nuvem fofa, nao gigante)
        float d = 10.0F, step = 7.0F;
        for (float x = x0 - 4; x < x0 + pillW + 1; x += step) {
            pill(m, buffers, z + 0.01F, x, yP0 - 5, d, d, argb);
            pill(m, buffers, z + 0.01F, x, yP1 + 5 - d, d, d, argb);
        }
        for (float y = yP0 - 1; y < yP1 - 1; y += step) {
            pill(m, buffers, z + 0.01F, x0 - 5, y, d, d, argb);
            pill(m, buffers, z + 0.01F, x0 + pillW + 5 - d, y, d, d, argb);
        }
    }

    private static void renderBubble(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        List<ClientBubbleCache.Bubble> stack = ClientBubbleCache.get(player.getUUID());
        if (stack.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getEntityRenderDispatcher().distanceToSqr(player) > 64.0 * 64.0) {
            return;
        }

        // ---- PILHA (3.19.0): pela ALTURA REAL de cada balao — nada se sobrepoe ----
        // (antes o gap era fixo e baloes de 2-3 linhas se cruzavam)
        Font fontH = minecraft.font;
        int count = stack.size();
        int[] hPx = new int[count];
        for (int i = 0; i < count; i++) {
            hPx[i] = bubbleHeightPx(fontH, stack.get(i));
        }
        for (int i = 0; i < count; i++) {
            float belowBlocks = 0.0F;
            for (int j = i + 1; j < count; j++) {
                belowBlocks += hPx[j] * 0.030F; // alturas dos baloes de baixo
            }
            float up = belowBlocks + (count - 1 - i) * STACK_GAP_BLOCKS;
            renderOne(event, minecraft, player, stack.get(i), up);
        }
    }

    /**
     * Altura TOTAL do balao em pixels de GUI (pilula + bordas/gomos + margem),
     * usada pra empilhar sem sobrepor.
     */
    private static int bubbleHeightPx(Font font, ClientBubbleCache.Bubble bubble) {
        String speech = bubble.text();
        if (!bubble.extras().isEmpty()) {
            speech = speech + " " + spacedExtras(bubble.extras());
        }
        List<FormattedCharSequence> lines = font.split(Stickers.styled(speech), MAX_TEXT_WIDTH);
        if (lines.isEmpty()) {
            return 0;
        }
        int extraH = 0;
        for (FormattedCharSequence line : lines) {
            boolean[] has = {false};
            line.accept((idx, style, cp) -> {
                if (Stickers.isStickerChar(cp)) {
                    has[0] = true;
                }
                return true;
            });
            if (has[0]) {
                extraH += STICKER_LINE_HEIGHT - LINE_HEIGHT; // linha com emoji e mais alta
            }
        }
        int pillH = lines.size() * LINE_HEIGHT + extraH + 6;
        return pillH + 14; // margem pra bordas/gomos (nuvem, cartum, gibi)
    }

    /** Desenha UM balao da pilha, com pop-in + flutuacao + fade. */
    private static void renderOne(RenderLivingEvent.Post<?, ?> event, Minecraft minecraft,
            Player player, ClientBubbleCache.Bubble bubble, float upBlocks) {
        Font font = minecraft.font;
        // (3.13.0) VARIOS EMOJIS: os extras acompanham a fala DENTRO do balao,
        // DE ESPACADOS entre si — cada emoji fica distinto e legivel de longe.
        String speech = bubble.text();
        if (!bubble.extras().isEmpty()) {
            speech = speech + " " + spacedExtras(bubble.extras());
        }
        List<FormattedCharSequence> lines = font.split(Stickers.styled(speech), MAX_TEXT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }
        List<List<Seg>> segLines = new ArrayList<>();
        for (FormattedCharSequence line : lines) {
            segLines.add(segment(line));
        }

        long now = System.currentTimeMillis();
        long age = now - bubble.bornMillis();
        long remaining = bubble.expireMillis() - now;
        // alfa global: entra rapido, sai suave (fade do fim da vida)
        float alpha = age < POP_MS ? Math.max(0.1F, age / (float) POP_MS)
                : (remaining < FADE_MS ? Math.max(0.1F, remaining / (float) FADE_MS) : 1.0F);
        // flutuacao suave pra cima nos primeiros FLOAT_MS
        float floatUp = FLOAT_UP_PX * Mth.clamp(age / (float) FLOAT_MS, 0.0F, 1.0F);

        int colorRGB = bubble.colorRGB() & 0xFFFFFF;
        int fill = withAlpha(BubbleBackgrounds.fillColorFor(colorRGB) | 0xFF000000, alpha);
        int border = BubbleBackgrounds.borderColorFor(colorRGB, bubble.borderRGB());
        border = border != 0 ? withAlpha(border | 0xFF000000, alpha) : 0;
        int textColor = bubble.textRGB() == BubbleStyle.TEXT_AUTO
                ? BubbleBackgrounds.textColorFor(colorRGB)
                : (0xFF000000 | bubble.textRGB());
        textColor = withAlpha(textColor, alpha);

        // ---- geometria (pixels inteiros) ----
        int textW = 0;
        for (FormattedCharSequence line : lines) {
            textW = Math.max(textW, font.width(line));
        }
        int n = lines.size();
        // linha com adesivo (glifo 16px) ocupa 18px; texto puro segue em 10px
        int[] lineH = new int[n];
        int extraH = 0;
        for (int j = 0; j < n; j++) {
            boolean hasSticker = false;
            for (Seg seg : segLines.get(j)) {
                if (seg.sticker) {
                    hasSticker = true;
                    break;
                }
            }
            lineH[j] = hasSticker ? STICKER_LINE_HEIGHT : LINE_HEIGHT;
            extraH += lineH[j] - LINE_HEIGHT;
        }
        int speechH = LINE_HEIGHT * n + extraH;
        int bsPre = Math.max(0, bubble.borderStyle());
        // molduras que estouram pra fora (tracinhos/gomos/tinta grossa) ganham respiro
        int pad = (bsPre == 3 || bsPre == 4 || bsPre == 5) ? 20 : 14;
        if (extraH > 0) {
            pad += 2; // emoji na linha pede um pelinho mais de ar
        }
        int pillW = Math.max(32, textW + pad);
        int pillH = speechH + 6;
        float x0 = -pillW / 2.0F;
        float yText = 3 - speechH;              // topo da 1a linha de texto
        float yP0 = yText - 3;                  // topo da pilula
        float yP1 = yText + speechH + 3;        // base da pilula

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0, player.getBbHeight() + 0.95 + upBlocks + floatUp * 0.030F, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.030F * popScale(age), -0.030F * popScale(age), 0.030F);
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource buffers = event.getMultiBufferSource();

        // ---- SOMBRA ----
        pill(matrix, buffers, -0.20F, x0 + 2, yP0 + 2, pillW, pillH, withAlpha(0x40000000, alpha));
        tail(matrix, buffers, -0.20F, 0, yP1 - 1, 12, 12, withAlpha(0x40000000, alpha));

        // ---- CONTORNO (6 ESTILOS: Classica / Cartum / Dupla / Tracejada / Nuvem / Gibi) ----
        int bs = bsPre;
        if (border != 0) {
            switch (bs) {
                case 1 -> { // CARTUM: tinta grossa, capa de quadrinho
                    pill(matrix, buffers, -0.15F, x0 - 4, yP0 - 4, pillW + 8, pillH + 8, border);
                    tail(matrix, buffers, -0.15F, 0, yP1 - 4, 18, 17, border);
                }
                case 2 -> { // DUPLA: linha fina + aro EXTERNO ESCURO (bem distinta)
                    pill(matrix, buffers, -0.15F, x0 - 1, yP0 - 1, pillW + 2, pillH + 2, border);
                    pill(matrix, buffers, -0.16F, x0 - 5, yP0 - 5, pillW + 10, pillH + 10,
                            shaded(border, 0.55F));
                    tail(matrix, buffers, -0.15F, 0, yP1 - 4, 16, 15, border);
                    tail(matrix, buffers, -0.16F, 0, yP1 - 5, 17, 16, shaded(border, 0.55F));
                }
                case 3 -> // TRACEJADA: tracinhos fofos em toda a volta
                        dashedBorder(matrix, buffers, -0.15F, x0, yP0, pillW, yP1, border);
                case 4 -> // NUVEM: bolas fofas, o balao vira uma nuvenzinha
                        cloudBorder(matrix, buffers, -0.15F, x0, yP0, pillW, pillH, yP1, border);
                case 5 -> { // GIBI: balaozao de HQ, tinta bem grossa
                    pill(matrix, buffers, -0.15F, x0 - 5, yP0 - 5, pillW + 10, pillH + 10, border);
                    tail(matrix, buffers, -0.15F, 0, yP1 - 5, 18, 18, border);
                }
                default -> { // CLASSICA: linha de tinta em toda a volta
                    pill(matrix, buffers, -0.15F, x0 - 2, yP0 - 2, pillW + 4, pillH + 4, border);
                    tail(matrix, buffers, -0.15F, 0, yP1 - 4, 16, 15, border);
                }
            }
        }

        // ---- RECHEIO (a cor de dentro escolhida) ----
        pill(matrix, buffers, -0.10F, x0, yP0, pillW, pillH, fill);
        tail(matrix, buffers, -0.09F, 0, yP1 - 3, 12, 12, fill);
        if (bs == 5) { // GIBI: brilho gelatinoso classico das HQs (faixa branca no topo)
            pill(matrix, buffers, -0.08F, x0 + 8, yP0 + 3, Math.max(12, pillW * 0.45F), 3,
                    withAlpha(0x99FFFFFF, alpha));
        }

        // ---- texto + adesivos dentro da fala (sombra vanilla = legivel e limpo) ----
        int cum = 0;
        for (int j = 0; j < n; j++) {
            List<Seg> segs = segLines.get(j);
            int lineW = 0;
            for (Seg seg : segs) {
                lineW += font.width(seq(seg));
            }
            float lineX = -Math.round(lineW / 2.0F);
            float penY = yText + cum;
            drawLine(font, matrix, buffers, segs, lineX, penY, textColor);
            cum += lineH[j];
        }

        // ---- ADESIVO LIVRE: em qualquer ponto do balao (posicao % salva) ----
        if (!bubble.corner().isEmpty()) {
            var sticker = Stickers.styled(bubble.corner());
            float sw = font.width(sticker);
            float sx = x0 + bubble.stickerX() / 100.0F * pillW;
            float sy = yP0 + bubble.stickerY() / 100.0F * pillH;
            font.drawInBatch(sticker, sx - sw / 2.0F, sy - 8.0F, withAlpha(0xFFFFFFFF, alpha), true,
                    matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }

        poseStack.popPose();
    }
}
