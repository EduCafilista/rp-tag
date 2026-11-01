package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

/**
 * (5.7.9) PRIMITIVAS DE DESENHO DOS AVISOS NOVOS.
 *
 * <p>Os avisos do mod são feitos de triângulos (<code>AvisoFxUtil.Buf</code>).
 * Isso basta pra retângulo e degradê, mas os protótipos novos (o sol do
 * ONE FOR ALL, a morte op e o sangue op) precisam de triângulo solto, linha
 * grossa, anel, arco, polígono e disco com degradê radial — é o que esta
 * classe entrega, sempre em cima da MESMA <code>Buf</code> (um flush só).
 *
 * <p>Nada aqui guarda estado: são funções puras de desenho.
 */
public final class FxDraw {

    private FxDraw() {
    }

    /** Triângulo solto. */
    public static void tri(Buf b, float x1, float y1, float x2, float y2, float x3, float y3, int c) {
        b.tri(x1, y1, c, x2, y2, c, x3, y3, c);
    }

    /** Linha grossa de (x1,y1) até (x2,y2). */
    public static void linha(Buf b, float x1, float y1, float x2, float y2, float w, int c) {
        b.seg(x1, y1, x2, y2, w, c, c);
    }

    /** Anel (círculo vazado) feito de segmentos. */
    public static void anel(Buf b, float cx, float cy, float r, float w, int c, int seg) {
        if (r <= 0.5F || seg < 6) {
            return;
        }
        float passo = (float) (Math.PI * 2 / seg);
        for (int i = 0; i < seg; i++) {
            float a = i * passo;
            linha(b, cx + cos(a) * r, cy + sin(a) * r,
                    cx + cos(a + passo) * r, cy + sin(a + passo) * r, w, c);
        }
    }

    /** Arco (pedaço de anel). */
    public static void arco(Buf b, float cx, float cy, float r, float ini, float fim,
            float w, int c, int seg) {
        if (r <= 0.5F || seg < 2) {
            return;
        }
        float d = (fim - ini) / seg;
        for (int i = 0; i < seg; i++) {
            float a1 = ini + i * d, a2 = ini + (i + 1) * d;
            linha(b, cx + cos(a1) * r, cy + sin(a1) * r,
                    cx + cos(a2) * r, cy + sin(a2) * r, w, c);
        }
    }

    /** Disco chapado (leque de triângulos). */
    public static void disco(Buf b, float cx, float cy, float r, int rgb, float a, int seg) {
        if (r <= 0.3F || a <= 0.004F) {
            return;
        }
        int c = col(rgb, a);
        if (seg < 6) {
            seg = 6;
        }
        float passo = (float) (Math.PI * 2 / seg);
        for (int i = 0; i < seg; i++) {
            float a0 = i * passo, a1 = (i + 1) * passo;
            b.tri(cx, cy, c, cx + cos(a0) * r, cy + sin(a0) * r, c,
                    cx + cos(a1) * r, cy + sin(a1) * r, c);
        }
    }

    /** Disco com degradê radial (anéis concêntricos, do centro pra borda). */
    public static void discoGrad(Buf b, float cx, float cy, float r, int rgbCentro, int rgbBorda,
            float aCentro, float aBorda, int aneis) {
        if (aneis < 2) {
            aneis = 2;
        }
        for (int i = aneis - 1; i >= 0; i--) {
            float u = i / (float) (aneis - 1);
            float rr = r * (1.0F - u * 0.94F);
            disco(b, cx, cy, rr, mix(rgbCentro, rgbBorda, u), lerp(aCentro, aBorda, u),
                    Math.max(10, 26 - i / 2));
        }
    }

    /** Disco que some na borda (brasa, brilho). */
    public static void brilho(Buf b, float cx, float cy, float r, int rgb, float a) {
        b.brilho(cx, cy, r, rgb, a);
    }

    /** Elipse preenchida (faixas horizontais). */
    public static void elipse(Buf b, float cx, float cy, float rx, float ry, int c, int faixas) {
        if (rx <= 0.2F || ry <= 0.2F || faixas < 2) {
            return;
        }
        float h = ry * 2F / faixas;
        for (int i = 0; i < faixas; i++) {
            float y = cy - ry + i * h;
            float dy = (y + h * 0.5F - cy) / ry;
            float k = (float) Math.sqrt(Math.max(0.0F, 1.0F - dy * dy));
            b.rect(cx - rx * k, y, rx * 2F * k, h + 0.6F, c);
        }
    }

    /** Losango/estrela de 4 pontas. */
    public static void losango(Buf b, float cx, float cy, float rx, float ry, int c) {
        b.tri(cx - rx, cy, c, cx, cy - ry, c, cx + rx, cy, c);
        b.tri(cx - rx, cy, c, cx, cy + ry, c, cx + rx, cy, c);
    }

    /** ruído estável 0..1. */
    public static float hash(int i) {
        return rnd(i, 7);
    }

    /** suavização 0..1. */
    public static float ease(float k) {
        return ss(0.0F, 1.0F, k);
    }

    public static float clamp(float v, float a, float b) {
        return v < a ? a : Math.min(v, b);
    }

    /** Texto centrado em (cx, cy), com escala e cor (0xAARRGGBB). */
    public static void texto(net.minecraft.client.gui.GuiGraphics g, String s, float cx, float cy,
            float escala, int cor) {
        texto(g, s, cx, cy, escala, cor, false);
    }

    /** Texto centrado, com opção de itálico. */
    public static void texto(net.minecraft.client.gui.GuiGraphics g, String s, float cx, float cy,
            float escala, int cor, boolean italico) {
        net.minecraft.client.gui.Font f = net.minecraft.client.Minecraft.getInstance().font;
        String txt = italico ? "\u00a7o" + s : s;
        int larg = f.width(txt);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0F);
        g.pose().scale(escala, escala, 1F);
        g.drawString(f, txt, -larg / 2, 0, cor, false);
        g.pose().popPose();
    }
}
