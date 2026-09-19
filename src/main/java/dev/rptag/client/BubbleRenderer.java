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

    /** (3.35.0) faixa de texto mais larga: menos quebras, balao mais legivel. */
    private static final int MAX_TEXT_WIDTH = 160; // (3.38.0) 140 -> 160: quebra menos
    private static final int LINE_HEIGHT = 10;
    /** Linha com adesivo fica mais alta: o glifo da 3.11 tem 16px de arte. */
    private static final int STICKER_LINE_HEIGHT = 20; // (3.29.0) 20px: glifo 16 centrado, NUNCA vaza

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
    /**
     * Escala do balao no mundo. UM unico lugar: a pilha usava 0.034F escrito na
     * mao e o render tambem — mudar um e esquecer o outro fazia os baloes
     * empilhados se sobreporem.
     */
    private static final float SCALE = 0.040F;

    /** Textura da pílula (64x32, branca com alfa suave — tingida na hora). */
    private static final ResourceLocation BUBBLE_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble.png");
    /** Textura do rabicho (16x16, triângulo curvado apontando pra baixo). */
    private static final ResourceLocation TAIL_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble_tail.png");

    private static final RenderType BUBBLE_TYPE = RenderType.text(BUBBLE_TEX);
    private static final RenderType TAIL_TYPE = RenderType.text(TAIL_TEX);
    /** CONTORNO COM RELEVO da Classica (tinta com luz em cima/sombra embaixo). */
    private static final ResourceLocation RING_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/ring.png");
    private static final RenderType RING_TYPE = RenderType.text(RING_TEX);
    // ANEL PROPRIOS (3.27.0): cada moldura e UMA textura de contorno (1 draw)
    private static final ResourceLocation CARTUM_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/cartum.png");
    private static final ResourceLocation DUPLA_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/dupla.png");
    private static final ResourceLocation DASHED_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/dashed.png");
    private static final ResourceLocation GIBI_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/gibi.png");
    private static final RenderType CARTUM_TYPE = RenderType.text(CARTUM_TEX);
    private static final RenderType DUPLA_TYPE = RenderType.text(DUPLA_TEX);
    private static final RenderType DASHED_TYPE = RenderType.text(DASHED_TEX);
    private static final RenderType GIBI_TYPE = RenderType.text(GIBI_TEX);
    /** ATLAS HD dos emojis (NOTO/Android 128px) — o mundo desenha QUADS direto. */
    private static final ResourceLocation HI_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/stickers_hi.png");
    private static final RenderType HI_TYPE = RenderType.text(HI_TEX);
    /** Textura propria do BRILHO (halo suave) — antes ele reusava o anel Classico. */
    private static final ResourceLocation BRILHO_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/brilho.png");
    private static final RenderType BRILHO_TYPE = RenderType.text(BRILHO_TEX);
    /**
     * (3.35.0) MARGEM DO CONTORNO POR ESTILO — a correcao central do visual.
     *
     * <p>Antes o anel era SEMPRE desenhado 2px pra fora da pilula, e o recheio
     * era desenhado depois por cima. Resultado: qualquer tinta alem desses 2px
     * ficava ESCONDIDA atras do recheio, entao Classica, Cartum, Dupla, Brilho
     * e Gibi saiam todas com a MESMA casquinha de 2px — as 6 molduras eram
     * visualmente identicas. Agora cada estilo cresce pra fora o quanto precisa.
     *
     * <p>Indices: 0 Classica, 1 Cartum, 2 Dupla, 3 Tracejada, 4 Brilho, 5 Gibi.
     * (o array antigo tinha 5 posicoes pra 6 estilos — o Gibi lia a margem do
     * Brilho por causa do clamp.)
     */
    private static final float[] RING_MARGIN = {2.0F, 4.0F, 5.0F, 3.0F, 4.0F, 5.0F};

    // regiao da pilula dentro do bubble.png (y 2..28 de 32; tampa = 16px de 64)
    private static final float V1 = 0.0F;
    private static final float V2 = 1.0F;
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

    /** Clareia um RGB em direcao ao branco (k 0..1) — usado pelo halo do Brilho. */
    private static int lighten(int rgb, float k) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (int) (r + (255 - r) * k);
        g = (int) (g + (255 - g) * k);
        b = (int) (b + (255 - b) * k);
        return (r << 16) | (g << 8) | b;
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
            List<Seg> segs, float x, float y, int textColor, float alpha) {
        float pen = x;
        for (Seg seg : segs) {
            if (seg.sticker) {
                // (3.31.0) EMOJI = QUAD HD do atlas NOTO (Android de verdade):
                // desenhado EXATAMENTE onde a geometria manda — nunca vira bloco
                // preto, nunca invade o texto. A largura continua a da fonte
                // (16px) pra nao mexer na quebra de linha.
                // (3.42.0) o comentario acima ja prometia herdar o alfa global,
                // mas o parametro nunca existia — o emoji inline nao acompanhava
                // o fade de entrada/saida do balao (aparecia/sumia de golpe).
                for (int cp : seg.text.codePoints().toArray()) {
                    emojiQuad(matrix, buffers, pen + 1.0F, y - 3.0F, cp - 0xE000, alpha);
                }
                pen += font.width(seq(seg));
                continue;
            }
            // (3.35.0) SEM sombra: dentro de um balao claro a sombra preta do
            // vanilla borrava cada letra e dava aquele aspecto sujo/embacado.
            // O recheio ja e opaco, entao o contraste sozinho basta.
            pen = font.drawInBatch(seq(seg), pen, y, textColor, false, matrix, buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        return pen;
    }

    /** QUAD do emoji no atlas HD (celula 16px de 128x256; arte 1024x2048). */
    private static void emojiQuad(Matrix4f m, MultiBufferSource buffers, float x, float y, int idx, float alpha) {
        if (idx < 0 || idx >= Stickers.COUNT) {
            idx = 0;
        }
        float u0 = (idx % 8) / 8.0F;
        float v0 = (idx / 8) / 16.0F;
        VertexConsumer buf = buffers.getBuffer(HI_TYPE);
        quad(m, buf, -0.07F, x, y, x + 16.0F, y + 16.0F, u0, v0, u0 + 0.125F, v0 + 0.0625F,
                withAlpha(0xFFFFFFFF, alpha));
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

    /**
     * CONTORNO COM RELEVO da Classica: a textura so tem tinta nas bordas de
     * cima/baixo (meio transparente) — um unico draw da o anel completo,
     * com tinta 2px pra fora (igual antes) e acabamento brilhante.
     */
    private static void ring(Matrix4f m, MultiBufferSource buffers, float z,
            float x, float y, float w, float h, int argb, RenderType tipo, float margin) {
        if ((argb >>> 24) == 0 || w < 4) {
            return;
        }
        VertexConsumer buf = buffers.getBuffer(tipo);
        float cap = Math.min(16.0F, w / 2.0F);
        quad(m, buf, z, x - margin, y - margin, x + cap, y + h + margin,
                0.0F, 0.0F, U_CAP, 1.0F, argb);
        quad(m, buf, z, x + cap, y - margin, x + w - cap, y + h + margin, U_CAP, 0.0F,
                1.0F - U_CAP, 1.0F, argb);
        quad(m, buf, z, x + w - cap, y - margin, x + w + margin, y + h + margin,
                1.0F - U_CAP, 0.0F, 1.0F, 1.0F, argb);
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


    /**
     * COSTURA (3.28.0): pontilhado em PERIODOS de 8px 1:1 — os pontos ficam
     * sempre do mesmo tamanho (fim do tracinho esticado/esquisito).
     */
    private static void costura(Matrix4f m, MultiBufferSource buffers, float z,
            float x, float y, float w, float h, int argb, float margin) {
        if ((argb >>> 24) == 0 || w < 40) {
            return;
        }
        VertexConsumer buf = buffers.getBuffer(DASHED_TYPE);
        // (3.35.0) DOIS bugs corrigidos aqui:
        // 1. a tracejada era desenhada EXATAMENTE no tamanho da pilula, e o
        //    recheio (desenhado depois, na frente) cobria os tracinhos
        //    inteiros — a moldura Tracejada simplesmente NAO aparecia;
        // 2. a "tampa" usava 32px de mundo, mas a textura foi feita com a
        //    tampa valendo 16+margem — por isso o contorno nao acompanhava a
        //    curva da pilula. Agora a tampa e 16 (igual ao anel) e tudo
        //    cresce pra fora pela margem.
        final float cap = 16.0F;
        quad(m, buf, z, x - margin, y - margin, x + cap, y + h + margin,
                0.0F, 0.0F, 32.0F / 192.0F, 1.0F, argb);
        float avail = w - 2.0F * cap;
        int n = (int) (avail / 8.0F);
        for (int i = 0; i < n; i++) {
            float u0 = (32.0F + (i % 4) * 8.0F) / 192.0F;
            quad(m, buf, z, x + cap + i * 8.0F, y - margin, x + cap + (i + 1) * 8.0F, y + h + margin,
                    u0, 0.0F, u0 + 8.0F / 192.0F, 1.0F, argb);
        }
        float resto = avail - n * 8.0F;
        if (resto > 0.3F) {
            float u0 = (32.0F + (n % 4) * 8.0F) / 192.0F;
            quad(m, buf, z, x + cap + n * 8.0F, y - margin, x + cap + n * 8.0F + resto, y + h + margin,
                    u0, 0.0F, u0 + resto / 192.0F, 1.0F, argb);
        }
        quad(m, buf, z, x + w - cap, y - margin, x + w + margin, y + h + margin,
                160.0F / 192.0F, 0.0F, 1.0F, 1.0F, argb);
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

        // (3.36.1) LEGIVEL DE LONGE: o balao ja vira pra CADA jogador que olha
        // (cada cliente renderiza o billboard virado pra propria camera — visual,
        // so pra quem esta perto). Agora tambem CRESCE SUAVEMENTE com a distancia
        // pra continuar legivel de longe sem virar um puntinho.
        float distToCam = Mth.sqrt((float) minecraft.getEntityRenderDispatcher().distanceToSqr(player));
        float distComp = 1.0F + 1.4F * Mth.clamp(distToCam / 64.0F, 0.0F, 1.0F)
                * Mth.clamp(distToCam / 64.0F, 0.0F, 1.0F); // 1.0x perto → ~2.4x a 64 blocos

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
                belowBlocks += hPx[j] * SCALE * distComp; // MESMA escala do render (c/ distancia) — fim da sobreposicao!
            }
            float up = belowBlocks + (count - 1 - i) * STACK_GAP_BLOCKS;
            renderOne(event, minecraft, player, stack.get(i), up, distComp);
        }
    }

    /**
     * Altura TOTAL do balao em pixels de GUI (pilula + bordas/gomos + margem),
     * usada pra empilhar sem sobrepor.
     */
    /**
     * (3.38.0) QUEBRA DE LINHA INTELIGENTE: o wrap guloso do vanilla jogava a
     * ultima palavra orfa numa linha de baixo curta demais ("tentou pegar o /
     * calice"). Aqui: mesmo numero minimo de linhas, mas repartido EQUILIBRADO
     * (programacao dinamica, custo = soma das folgas ao quadrado). Palavra
     * mais larga que a linha inteira e fatiada no limite (nunca clipa).
     */
    private static List<String> wrapBalanced(Font font, String speech) {
        final float spaceW = font.width(" ");
        List<String> words = new ArrayList<>();
        for (String raw : speech.split(" ")) {
            String w = raw;
            while (!w.isEmpty() && font.width(Stickers.styled(w).getVisualOrderText()) > MAX_TEXT_WIDTH) {
                int cut = w.length() - 1;
                while (cut > 1 && font.width(
                        Stickers.styled(w.substring(0, cut)).getVisualOrderText()) > MAX_TEXT_WIDTH) {
                    cut--;
                }
                words.add(w.substring(0, cut));
                w = w.substring(cut);
            }
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        int n = words.size();
        if (n == 0) {
            return new ArrayList<>();
        }
        float[] wArr = new float[n];
        for (int i = 0; i < n; i++) {
            wArr[i] = font.width(Stickers.styled(words.get(i)).getVisualOrderText());
        }
        // numero minimo de linhas (guloso = limite inferior)
        int minLines = 1;
        float cur = wArr[0];
        for (int i = 1; i < n; i++) {
            if (cur + spaceW + wArr[i] > MAX_TEXT_WIDTH) {
                minLines++;
                cur = wArr[i];
            } else {
                cur += spaceW + wArr[i];
            }
        }
        if (minLines == 1) {
            List<String> one = new ArrayList<>();
            one.add(String.join(" ", words));
            return one;
        }
        // DP: exatamente minLines linhas, minimizando soma das folgas^2
        final float INF = 1.0e9F;
        float[][] cost = new float[minLines + 1][n + 1];
        int[][] back = new int[minLines + 1][n + 1];
        for (float[] row : cost) {
            java.util.Arrays.fill(row, INF);
        }
        cost[0][0] = 0.0F;
        for (int k = 1; k <= minLines; k++) {
            for (int i = k; i <= n; i++) {
                for (int j = k - 1; j < i; j++) {
                    if (cost[k - 1][j] >= INF) {
                        continue;
                    }
                    float lw = wArr[j];
                    for (int t = j + 1; t < i; t++) {
                        lw += spaceW + wArr[t];
                    }
                    if (lw > MAX_TEXT_WIDTH) {
                        continue; // linha estoura
                    }
                    float slack = MAX_TEXT_WIDTH - lw;
                    float c = cost[k - 1][j] + slack * slack;
                    if (c < cost[k][i]) {
                        cost[k][i] = c;
                        back[k][i] = j;
                    }
                }
            }
        }
        List<String> out = new ArrayList<>();
        int[] brk = new int[minLines + 1];
        brk[minLines] = n;
        for (int k = minLines; k >= 1; k--) {
            brk[k - 1] = back[k][brk[k]];
        }
        for (int k = 0; k < minLines; k++) {
            StringBuilder sb = new StringBuilder();
            for (int i = brk[k]; i < brk[k + 1]; i++) {
                if (i > brk[k]) {
                    sb.append(' ');
                }
                sb.append(words.get(i));
            }
            out.add(sb.toString());
        }
        return out;
    }

    private static int bubbleHeightPx(Font font, ClientBubbleCache.Bubble bubble) {
        // (3.32.0) fala = TEXTO PURO; os emotes moram na faixa (so a faixa conta)
        String speech = bubble.text();
        int nLines = wrapBalanced(font, speech).size();
        if (nLines == 0) {
            return 0;
        }
        int pillH = nLines * LINE_HEIGHT + 8;
        if (!bubble.corner().isEmpty() || !bubble.extras().isEmpty()) {
            // (3.41.0) emblema agora fica EMBLEM_HALF(8) + EMBLEM_GAP(4) pra
            // fora da pilula, + a metade que pendura do lado de fora = 12+8=20
            pillH += 20;
        }
        int bsH = Math.max(0, bubble.borderStyle()); // (3.39.0) >= 0 SEMPRE (negativo = AIOOBE)
        int bsIdx = Math.min(bsH, RING_MARGIN.length - 1);
        // a moldura cresce pra fora nos DOIS lados + o rabicho pendura embaixo
        return pillH + Math.round(2.0F * RING_MARGIN[bsIdx]) + 14;
    }

    /** Desenha UM balao da pilha, com pop-in + flutuacao + fade. */
    private static void renderOne(RenderLivingEvent.Post<?, ?> event, Minecraft minecraft,
            Player player, ClientBubbleCache.Bubble bubble, float upBlocks, float distComp) {
        Font font = minecraft.font;
        // (3.13.0) VARIOS EMOJIS: os extras acompanham a fala DENTRO do balao,
        // DE ESPACADOS entre si — cada emoji fica distinto e legivel de longe.
        // (3.32.0) EMOJIS SO NAS BORDAS: os extras NAO entram mais no texto!
        // A fala e 100% texto; os emojis moram na FAIXA (igual ao adesivo) —
        // linha de emoji NUNCA mais (fim do espaco vazio do print).
        String speech = bubble.text();
        // (3.38.0) linhas EQUILIBRADAS (fim da palavra orfa na quebra de linha)
        List<String> linesTxt = wrapBalanced(font, speech);
        if (linesTxt.isEmpty()) {
            return;
        }
        List<List<Seg>> segLines = new ArrayList<>();
        for (String lineTxt : linesTxt) {
            segLines.add(segment(Stickers.styled(lineTxt).getVisualOrderText()));
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
        for (List<Seg> segs : segLines) {
            int lw = 0;
            for (Seg seg : segs) {
                lw += font.width(seq(seg));
            }
            textW = Math.max(textW, lw);
        }
        int n = linesTxt.size();
        // linha com adesivo (glifo 16px) ocupa 18px; texto puro segue em 10px
        int speechH = LINE_HEIGHT * n; // texto puro — emoji NAO cria linha NENHUMA
        // (3.22.0) ADESIVO SO NAS BORDAS: se tem adesivo, a pilula EXPANDE 18px
        // pro lado escolhido (topo se stickerY<50, base senao) — o adesivo vive
        // nessa faixa e NUNCA fica em cima das letras.
        boolean topBand = bubble.stickerY() < 50;
        boolean temBanda = !bubble.corner().isEmpty() || !bubble.extras().isEmpty();
        int bsPre = Math.max(0, bubble.borderStyle());
        // (3.35.0) o respiro interno agora e IGUAL pra todos: a moldura cresce
        // pra FORA (RING_MARGIN), entao nao precisa mais roubar espaco de dentro.
        int pad = 16;
        int extrasN = bubble.extras().length();
        // (3.37.0) FIM DA FAIXA (o "ar" que o usuario vivia reclamando!): a
        // pilula fica do tamanho EXATO do texto — o adesivo/emotes agora sao
        // um EMBLEMA pregado NA BORDA (sobre poe apenas o contorno, metade
        // pra fora), no lado escolhido. Nenhuma linha nova, nenhum vazio.
        //
        // (3.42.0) BUG "FORA DO LUGAR": essa largura NUNCA levava em conta o
        // espaco que o proprio emblema precisa. Numa mensagem curta (pilula
        // minima de 34px) com 2-3 emojis, cada emoji e desenhado 18px a
        // direita do anterior a partir de um ponto fixo — e isso ULTRAPASSA
        // a pilula de verdade rapido, sobrando emoji flutuando no vazio bem
        // longe do balao. Agora a pilula garante espaco pro emblema inteiro
        // (adesivo + emotes) por LARGURA, sem reintroduzir faixa/altura extra.
        int emblemW = 0;
        if (temBanda) {
            emblemW = 36 + Math.max(0, extrasN - 1) * 18;
            if (!bubble.corner().isEmpty()) {
                emblemW += 20;
            }
        }
        int pillW = Math.max(34, Math.max(textW + pad, emblemW));
        // (3.31.1) FIX CRITICO: a pilula tem que COBRIR a faixa do adesivo!
        // (era speechH+6 — com a faixa em cima, a pilula terminava 28px antes
        // do fim do texto e as letras PENDURAVAM FORA do balao — o bug do print!)
        int pillH = speechH + 8; // (3.37.0) SEM faixa: colada no texto
        float x0 = -pillW / 2.0F;
        // (3.36.0) FIX RAIZ DO "AR" (portado da 3.35.0 — a reforma tinha
        // voltado a geometria antiga!): a escala usa -Y, entao y=0 e a ANCORa
        // (logo acima do nome) e y POSITIVO DESCE por cima do nametag. A base
        // ficava em +4/+24 — o balao INVADIA o nome e sobrava um box enorme
        // meio vazio. Agora a base encosta EXATAMENTE na ancora (yP1 = 0) e o
        // balao cresce SO PRA CIMA, 100% colado — zero ar, nome livre.
        float yP1 = 0.0F;                    // base da pilula
        float yText = yP1 - 4 - speechH;     // topo da 1a linha
        float yP0 = yText - 4;               // topo da pilula

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0, player.getBbHeight() + 0.95 + upBlocks + floatUp * 0.030F, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        // (3.35.0) 0.034 -> 0.040: balao ~18% maior, legivel de mais longe
        poseStack.scale(SCALE * distComp * popScale(age), -SCALE * distComp * popScale(age), SCALE); // (3.36.1) cresce c/ a distancia = legivel de longe
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource buffers = event.getMultiBufferSource();

        int bs = bsPre;
        float margin = RING_MARGIN[Math.max(0, Math.min(bs, RING_MARGIN.length - 1))]; // (3.39.0) nunca negativo
        // rabicho: base larga o bastante pra parecer parte do balao
        final float tw = 13.0F;
        final float th = 13.0F;

        // ---- SOMBRA (suave, quase sem deslocamento — a antiga era um eco duro
        // deslocado 2px que dava cara de clipart) ----
        pill(matrix, buffers, -0.20F, x0 + 1, yP0 + 2, pillW, pillH, withAlpha(0x33000000, alpha));
        tail(matrix, buffers, -0.20F, 1, yP1 - 2, tw, th, withAlpha(0x33000000, alpha));

        // ---- CONTORNO (6 ESTILOS: Classica / Cartum / Dupla / Tracejada / Brilho / Gibi) ----
        if (border != 0) {
            float tailBorderW = tw + margin * 1.6F;
            float tailBorderH = th + margin;
            switch (bs) {
                case 1 -> { // CARTUM: tinta grossa
                    ring(matrix, buffers, -0.15F, x0, yP0, pillW, pillH, border, CARTUM_TYPE, margin);
                    tail(matrix, buffers, -0.15F, 0, yP1 - 2 - margin * 0.7F,
                            tailBorderW, tailBorderH, border);
                }
                case 2 -> { // DUPLA: duas linhas finas
                    ring(matrix, buffers, -0.15F, x0, yP0, pillW, pillH, border, DUPLA_TYPE, margin);
                    tail(matrix, buffers, -0.15F, 0, yP1 - 2 - margin * 0.7F,
                            tailBorderW, tailBorderH, border);
                }
                case 3 -> { // TRACEJADA: tracinhos acompanhando a curva
                    costura(matrix, buffers, -0.15F, x0, yP0, pillW, pillH, border, margin);
                    tail(matrix, buffers, -0.15F, 0, yP1 - 2 - margin * 0.7F,
                            tailBorderW, tailBorderH, border);
                }
                case 4 -> { // BRILHO: halo CLARO (o antigo usava a cor ESCURA da
                            // borda, entao o "brilho" era invisivel) + anel fino
                    int halo = withAlpha(0xCC000000 | lighten(colorRGB, 0.55F), alpha);
                    ring(matrix, buffers, -0.16F, x0, yP0, pillW, pillH, halo, BRILHO_TYPE, margin);
                    ring(matrix, buffers, -0.15F, x0, yP0, pillW, pillH, border, RING_TYPE, 2.0F);
                    tail(matrix, buffers, -0.15F, 0, yP1 - 3, tw + 3.0F, th + 2.0F, border);
                }
                case 5 -> { // GIBI: anel gordo de HQ
                    ring(matrix, buffers, -0.15F, x0, yP0, pillW, pillH, border, GIBI_TYPE, margin);
                    tail(matrix, buffers, -0.15F, 0, yP1 - 2 - margin * 0.7F,
                            tailBorderW, tailBorderH, border);
                }
                default -> { // CLASSICA: contorno limpo
                    ring(matrix, buffers, -0.15F, x0, yP0, pillW, pillH, border, RING_TYPE, margin);
                    tail(matrix, buffers, -0.15F, 0, yP1 - 2 - margin * 0.7F,
                            tailBorderW, tailBorderH, border);
                }
            }
        }

        // ---- RECHEIO (a cor de dentro escolhida) ----
        pill(matrix, buffers, -0.10F, x0, yP0, pillW, pillH, fill);
        tail(matrix, buffers, -0.09F, 0, yP1 - 2, tw, th, fill);

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
            drawLine(font, matrix, buffers, segs, lineX, penY, textColor, alpha);
            cum += LINE_HEIGHT;
        }

        // ---- (3.37.0) ADESIVO/EMOTES = EMBLEMA NA BORDA ----
        // Sobre poe APENAS o contorno (metade pra fora), no lado escolhido —
        // nunca sobre as letras, nunca criando faixa/vazio no balao.
        if (temBanda) {
            // (3.41.0) BUG DA SOBREPOSICAO: 'by' e o CENTRO do emblema, e o
            // glifo mede 16px de altura (8px pra cada lado do centro). Com
            // so 4px de offset, a METADE do glifo (8px) sempre invadia a
            // pilula por baixo — sobravam -4px, ou seja, 4px do emblema
            // ficavam LITERALMENTE EM CIMA DAS LETRAS. Agora o offset conta
            // a meia-altura do glifo (8) + uma folga de verdade (4).
            final float EMBLEM_HALF = 8.0F;
            final float EMBLEM_GAP = 4.0F;
            float by = topBand ? yP0 - EMBLEM_HALF - EMBLEM_GAP : yP1 + EMBLEM_HALF + EMBLEM_GAP;
            float bx = x0 + 18.0F;
            if (!bubble.corner().isEmpty()) {
                // (3.42.0) QUALIDADE INCONSISTENTE: o adesivo unico desenhava
                // pela FONTE de baixo-res (stickers.png, 16x16 por glifo),
                // enquanto os emotes extras JA usavam o atlas HD (emojiQuad,
                // 128x128 por glifo). No mesmo balao, um emoji saia nitido e
                // o outro saia borrado/blocado — agora os dois usam o MESMO
                // atlas HD, iguais em qualidade.
                int cornerIdx = (bubble.corner().charAt(0) & 0xFFFF) - 0xE000;
                float bxMax = x0 + pillW - 18.0F - extrasN * 18.0F;
                bx = Mth.clamp(x0 + bubble.stickerX() / 100.0F * pillW,
                        x0 + 18.0F, Math.max(x0 + 18.0F, bxMax));
                emojiQuad(matrix, buffers, bx - 8.0F, by - 8.0F, cornerIdx, alpha);
            }
            for (int i = 0; i < extrasN; i++) {
                int idx = (bubble.extras().charAt(i) & 0xFFFF) - 0xE000;
                emojiQuad(matrix, buffers, bx + 10.0F + i * 18.0F, by - 8.0F, idx, alpha);
            }
        }

        poseStack.popPose();
    }
}
