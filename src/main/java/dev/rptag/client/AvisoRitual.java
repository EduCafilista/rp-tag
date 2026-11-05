package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * (5.14.0) Peças de desenho comuns aos avisos "de ritual" — LOUCO, MARINHEIRO e ESPECTADOR.
 *
 * <p>Os três protótipos do dono são JavaFX (gradiente radial, polígono, arco, texto com
 * sombra). O jogo não tem nada disso no GUI, então esta classe dá o mínimo equivalente em
 * triângulos do {@link AvisoFxUtil.Buf}: polígono, polilinha, arco, halo radial, degrau
 * vertical e texto escalado.
 */
public final class AvisoRitual {

    private AvisoRitual() {
    }

    /** preenche um polígono (leque a partir do centro de massa). */
    public static void poligono(Buf b, float[] xs, float[] ys, int n, int cor) {
        if (n < 3) {
            return;
        }
        float cx = 0, cy = 0;
        for (int i = 0; i < n; i++) {
            cx += xs[i];
            cy += ys[i];
        }
        cx /= n;
        cy /= n;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            b.tri(cx, cy, cor, xs[i], ys[i], cor, xs[j], ys[j], cor);
        }
    }

    public static void linha(Buf b, float x1, float y1, float x2, float y2, float w, int cor) {
        b.seg(x1, y1, x2, y2, w, cor, cor);
    }

    public static void polilinha(Buf b, float[] xs, float[] ys, int n, float w, int cor) {
        for (int i = 0; i < n - 1; i++) {
            b.seg(xs[i], ys[i], xs[i + 1], ys[i + 1], w, cor, cor);
        }
    }

    /** arco de círculo (a0→a1 em radianos) feito de segmentos. */
    public static void arco(Buf b, float cx, float cy, float r, float a0, float a1, float w, int cor, int passos) {
        float ant = a0;
        for (int i = 1; i <= passos; i++) {
            float ang = a0 + (a1 - a0) * i / passos;
            b.seg(cx + cos(ant) * r, cy + sin(ant) * r, cx + cos(ang) * r, cy + sin(ang) * r, w, cor, cor);
            ant = ang;
        }
    }

    /** círculo cheio de raio r com borda suave (anel entre r*0.86 e r). */
    public static void disco(Buf b, float cx, float cy, float r, int rgb, float a) {
        b.circ(cx, cy, r * 0.88f, col(rgb, a));
        if (a > 0.02f) {
            anel(b, cx, cy, r * 0.86f, r, rgb, a, a * 0.35f, 18);
        }
    }

    /** faixa circular entre dois raios, alfa interpolado. */
    public static void anel(Buf b, float cx, float cy, float rIn, float rOut, int rgb, float aIn, float aOut, int segs) {
        int ci = col(rgb, aIn), co = col(rgb, aOut);
        for (int s = 0; s < segs; s++) {
            float t0 = s * TAU / segs, t1 = (s + 1) * TAU / segs;
            b.quad(cx + cos(t0) * rIn, cy + sin(t0) * rIn, ci,
                    cx + cos(t1) * rIn, cy + sin(t1) * rIn, ci,
                    cx + cos(t1) * rOut, cy + sin(t1) * rOut, co,
                    cx + cos(t0) * rOut, cy + sin(t0) * rOut, co);
        }
    }

    /** halo radial: cheio no meio (r0) e sumindo até r1. */
    public static void halo(Buf b, float cx, float cy, float r0, float r1, int rgb, float a) {
        if (a <= 0.004f) {
            return;
        }
        // (5.14.0) 6 anéis x 14 lados: a névoa desenha 46 halos POR QUADRO, então o halo
        // precisa ser barato — com 10x20 a tela gastava ~18 mil triângulos só de névoa.
        int passos = 6;
        float rAnt = r0;
        for (int i = 1; i <= passos; i++) {
            float u = i / (float) passos;
            float r = lerp(r0, r1, u);
            anel(b, cx, cy, rAnt, r, rgb, a * (1 - (i - 1) / (float) passos) * (1 - (i - 1) / (float) passos),
                    a * (1 - u) * (1 - u), 14);
            rAnt = r;
        }
    }

    /** retângulo em degradê vertical (faixas horizontais). */
    public static void degradeVertical(Buf b, float x, float y, float w, float h, int cTopo, int cBase, int faixas) {
        for (int i = 0; i < faixas; i++) {
            float u0 = i / (float) faixas, u1 = (i + 1) / (float) faixas;
            int c0 = mix(cTopo, cBase, u0), c1 = mix(cTopo, cBase, u1);
            b.quad(x, y + h * u0, c0, x + w, y + h * u0, c0, x + w, y + h * u1, c1, x, y + h * u1, c1);
        }
    }

    /** retângulo em degradê horizontal. */
    public static void degradeHorizontal(Buf b, float x, float y, float w, float h, int cEsq, int cDir, int faixas) {
        for (int i = 0; i < faixas; i++) {
            float u0 = i / (float) faixas, u1 = (i + 1) / (float) faixas;
            int c0 = mix(cEsq, cDir, u0), c1 = mix(cEsq, cDir, u1);
            b.quad(x + w * u0, y, c0, x + w * u1, y, c1, x + w * u1, y + h, c1, x + w * u0, y + h, c0);
        }
    }

    /** texto com escala (o GUI só desenha na escala 1, então escala a matriz). */
    public static void texto(GuiGraphics g, String s, float x, float y, float esc, int cor, boolean sombra) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(esc, esc, 1f);
        g.drawString(Minecraft.getInstance().font, s, 0, 0, cor, sombra);
        g.pose().popPose();
    }

    /** texto centralizado horizontalmente em {@code cx}. */
    public static void textoCentro(GuiGraphics g, String s, float cx, float y, float esc, int cor, boolean sombra) {
        float larg = Minecraft.getInstance().font.width(s) * esc;
        texto(g, s, cx - larg / 2f, y, esc, cor, sombra);
    }

    /** prepara o estado de render e devolve a matriz (chame no começo do render). */
    public static Buf comecar(GuiGraphics g) {
        preparar(g);
        return new Buf(g.pose().last().pose());
    }

    public static void terminar(Buf b) {
        b.flush();
        normal();
        RenderSystem.enableDepthTest();
    }
}
