package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.rptag.CanonCoracaoBlockEntity;
import dev.rptag.RPTagMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.util.Mth;

/**
 * (4.9.5) O CORAÇÃO DE LORE — MODELAGEM 3D FEITA À MÃO (malha esculpida).
 *
 * <p><b>Como o giro funciona no chão.</b> Um bloco comum não gira: o modelo dele é
 * estático. O que gira é o <b>BER</b> ({@link BlockEntityRenderer}): o jogo chama
 * {@link #render} a cada quadro e a rotação vem de um RELÓGIO DE PAREDE
 * ({@code System.nanoTime()}), não de tick — gira mesmo sem ticker, não trava com
 * lag, não "pula" quando o mundo pausa e para sozinho quando o jogador se afasta.
 *
 * <p><b>Por que mudou (4.9.5).</b> Até a 4.9.4 o coração era um <i>sólido voxel</i>:
 * a silhueta 15×13 era inflada em 13 fatias de 1 px. De frente lia como coração,
 * mas de lado virava um amontoado de tábuas e as bordas ficavam serrilhadas.
 * Agora é <b>malha</b>, igual à máscara de pedra:
 *
 * <ul>
 *   <li><b>contorno</b>: a curva clássica do coração
 *       ({@code x = 16 sen³t}, {@code y = 13cos t − 5cos2t − 2cos3t − cos4t}),
 *       normalizada para a caixa de 1 × 0,95;</li>
 *   <li><b>corpo</b>: cada ponto do contorno é puxado para o centro por
 *       {@code s} (0 = meio, 1 = borda) e a espessura afunda como
 *       {@code √(1 − s²)} — dois lóbulos, a ponta em V e a dobra do meio, visto de
 *       QUALQUER ângulo (nada de fatias);</li>
 *   <li><b>{@value #NT} × {@value #NS} × 2 faces</b> (frente e verso) + leque no
 *       centro, com <b>normais por diferença finita</b>;</li>
 *   <li><b>cor assada</b> por vértice (o {@code RenderType.lightning()} não usa
 *       normal nem luz do mundo): gradiente rosa → vinho, luz fixa de cima-esquerda
 *       e brilho especular no lóbulo esquerdo, que acompanha o giro porque é
 *       geometria;</li>
 *   <li>batida em dois tempos ("tum-tum") + flutuação + inclinação leve;</li>
 *   <li>tudo pré-computado uma vez em arrays — o render só percorre os triângulos.</li>
 * </ul>
 *
 * <p>A mesma matemática existe em {@code arte/previa_coracao3d.py}, que gera
 * {@code previa/coracao3d.png} (render de conferência em 3 ângulos).
 */
public class CanonCoracaoRenderer implements BlockEntityRenderer<CanonCoracaoBlockEntity> {

    // ---- malha
    private static final int NT = 56;              // pontos em volta do contorno
    private static final int NS = 13;              // anéis do centro até a borda
    private static final float ESPESSURA = 0.34f;  // metade da grossura (em blocos)
    private static final float LARGURA = 0.95f;    // largura final
    private static final float ALTURA = 0.90f;     // altura final

    /** cores: topo rosa claro → ponta vinho. */
    private static final int TOPO = 0xF75C7C, MEIO = 0xDC183E, PONTA = 0x840826;

    // ---- luz fixa (cima-esquerda-frente), já normalizada, usada para assar a cor
    private static final float LX, LY, LZ;

    static {
        float lx = -0.35f, ly = 0.62f, lz = -0.70f;
        float ln = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        LX = lx / ln; LY = ly / ln; LZ = lz / ln;
    }

    // ---- vértices pré-computados (posição + cor)
    private static final int MAXV = 12000;
    private static final float[] VX = new float[MAXV];
    private static final float[] VY = new float[MAXV];
    private static final float[] VZ = new float[MAXV];
    private static final int[] VC = new int[MAXV];
    private static int NV;

    // ---- normalização do contorno (caixa [-1,1]²), calculada uma vez
    private static final float CX, CY, ESC;

    static {
        float xmin = 9e9f, xmax = -9e9f, ymin = 9e9f, ymax = -9e9f;
        for (int k = 0; k < 400; k++) {
            float t = k / 400f;
            float x = contornoX(t), y = contornoY(t);
            xmin = Math.min(xmin, x); xmax = Math.max(xmax, x);
            ymin = Math.min(ymin, y); ymax = Math.max(ymax, y);
        }
        CX = (xmin + xmax) * 0.5f;
        CY = (ymin + ymax) * 0.5f;
        ESC = 2f / Math.max(xmax - xmin, ymax - ymin);
        construir();
    }

    public CanonCoracaoRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx) {
    }

    // ============================================================== geometria

    /**
     * Contorno clássico do coração:
     * {@code x = 16 sen³t}, {@code y = 13cos t − 5cos2t − 2cos3t − cos4t} (÷16).
     */
    private static float contornoX(float t) {
        double a = 2.0 * Math.PI * t, sa = Math.sin(a);
        return (float) (sa * sa * sa);
    }

    private static float contornoY(float t) {
        double a = 2.0 * Math.PI * t;
        return (float) ((13.0 * Math.cos(a) - 5.0 * Math.cos(2 * a) - 2.0 * Math.cos(3 * a)
                - Math.cos(4 * a)) / 16.0);
    }

    /** ponto do contorno já normalizado para a caixa [-1,1]². */
    private static float[] contornoNorm(float t) {
        return new float[]{(contornoX(t) - CX) * ESC, (contornoY(t) - CY) * ESC};
    }

    /** superfície: s = 0 (centro) até 1 (borda); frente/verso no eixo Z. */
    static void ponto(float t, float s, boolean frente, float[] out) {
        float[] c = contornoNorm(t);
        out[0] = c[0] * s * (LARGURA * 0.5f);
        out[1] = c[1] * s * (ALTURA * 0.5f) + ALTURA * 0.5f;
        out[2] = ESPESSURA * (float) Math.sqrt(Math.max(0f, 1f - s * s)) * (frente ? 1f : -1f);
    }

    /** normal por diferença finita, apontada para fora do centro do coração. */
    static void normal(float t, float s, boolean frente, float[] out) {
        final float e = 1e-3f;
        float tp = (t + e) % 1f, tm = (t - e + 1f) % 1f;
        float sp = Math.min(1f, s + e), sm = Math.max(0f, s - e);
        float[] p = new float[3], a = new float[3], b = new float[3];
        float[] c = new float[3], d = new float[3];
        ponto(tp, s, frente, a);
        ponto(tm, s, frente, b);
        ponto(t, sp, frente, c);
        ponto(t, sm, frente, d);
        float dtx = a[0] - b[0], dty = a[1] - b[1], dtz = a[2] - b[2];
        float dsx = c[0] - d[0], dsy = c[1] - d[1], dsz = c[2] - d[2];
        float nx = dsy * dtz - dsz * dty;
        float ny = dsz * dtx - dsx * dtz;
        float nz = dsx * dty - dsy * dtx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-9f) {
            nx = 0f; ny = 0f; nz = frente ? 1f : -1f;
        } else {
            nx /= len; ny /= len; nz /= len;
        }
        ponto(t, s, frente, p);
        float px = p[0], py = p[1] - ALTURA * 0.5f, pz = p[2];
        if (nx * px + ny * py + nz * pz < 0f) {
            nx = -nx; ny = -ny; nz = -nz;
        }
        out[0] = nx; out[1] = ny; out[2] = nz;
    }

    /** cor assada: gradiente vertical + luz fixa + especular no lóbulo esquerdo. */
    private static int cor(float t, float s, float nx, float ny, float nz, boolean frente) {
        float[] p = new float[3];
        ponto(t, s, frente, p);
        float u = Math.min(1f, Math.max(0f, p[1] / ALTURA));
        int base;
        if (u < 0.5f) {
            base = mix(TOPO, MEIO, u * 2f);
        } else {
            base = mix(MEIO, PONTA, (u - 0.5f) * 2f);
        }
        float dif = Math.max(0f, nx * LX + ny * LY + nz * LZ);
        float k = 0.52f + 0.62f * dif;
        float esp = 0f;
        if (frente) {
            float ex = (p[0] + 0.30f) / 0.22f;
            float ey = (p[1] - ALTURA * 0.80f) / 0.20f;
            esp = 0.30f * (float) Math.exp(-(ex * ex + ey * ey)) * dif * dif;
        }
        return clareia(escala(base, k), esp);
    }

    private static void vert(float[] p, int c) {
        if (NV >= MAXV) {
            return;
        }
        VX[NV] = p[0]; VY[NV] = p[1]; VZ[NV] = p[2]; VC[NV] = c;
        NV++;
    }

    private static void construir() {
        NV = 0;
        float[] p = new float[3], n = new float[3];
        float[] p0 = new float[3], p1 = new float[3], p2 = new float[3], p3 = new float[3];
        for (int lado = 0; lado < 2; lado++) {
            boolean frente = lado == 0;
            for (int k = 0; k < NS - 1; k++) {
                float s0 = 0.14f + (1f - 0.14f) * (k / (float) (NS - 1));
                float s1 = 0.14f + (1f - 0.14f) * ((k + 1) / (float) (NS - 1));
                for (int j = 0; j < NT; j++) {
                    float t0 = j / (float) NT, t1 = ((j + 1) % NT) / (float) NT;
                    ponto(t0, s0, frente, p0);
                    ponto(t1, s0, frente, p1);
                    ponto(t1, s1, frente, p2);
                    ponto(t0, s1, frente, p3);
                    int c0 = corDe(t0, s0, frente, n);
                    int c1 = corDe(t1, s0, frente, n);
                    int c2 = corDe(t1, s1, frente, n);
                    int c3 = corDe(t0, s1, frente, n);
                    if (frente) {
                        vert(p0, c0); vert(p1, c1); vert(p2, c2);
                        vert(p0, c0); vert(p2, c2); vert(p3, c3);
                    } else {
                        vert(p3, c3); vert(p2, c2); vert(p1, c1);
                        vert(p3, c3); vert(p1, c1); vert(p0, c0);
                    }
                }
            }
            // leque no centro (tampa o buraco do meio)
            float sm = 0.14f;
            ponto(0f, 0f, frente, p);
            int cc = corDe(0f, 0f, frente, n);
            for (int j = 0; j < NT; j++) {
                float t0 = j / (float) NT, t1 = ((j + 1) % NT) / (float) NT;
                float[] a = new float[3], b = new float[3];
                ponto(t0, sm, frente, a);
                ponto(t1, sm, frente, b);
                int ca = corDe(t0, sm, frente, n);
                int cb = corDe(t1, sm, frente, n);
                if (frente) {
                    vert(p, cc); vert(a, ca); vert(b, cb);
                } else {
                    vert(b, cb); vert(a, ca); vert(p, cc);
                }
            }
        }
    }

    private static int corDe(float t, float s, boolean frente, float[] n) {
        normal(t, s, frente, n);
        return cor(t, s, n[0], n[1], n[2], frente);
    }

    private static int escala(int rgb, float f) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float f) {
        f = Math.max(0f, Math.min(1f, f));
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return (r << 16) | (g << 8) | bl;
    }

    private static int clareia(int rgb, float f) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += Math.round((255 - r) * f);
        g += Math.round((255 - g) * f);
        b += Math.round((255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int shade(int rgb, float f) {
        int r = (int) Math.min(255, (((rgb >> 16) & 0xFF) * f));
        int g = (int) Math.min(255, (((rgb >> 8) & 0xFF) * f));
        int b = (int) Math.min(255, ((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    // ============================================================== render
    @Override
    public void render(CanonCoracaoBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        try {
            double t = System.nanoTime() / 1.0e9;

            // fase por posição: dois corações no mundo não giram em sincronia
            int fase = (be.getBlockPos().getX() * 37 + be.getBlockPos().getZ() * 91) & 1023;
            float ang = (float) ((t * 24.0) % 360.0) + fase * 0.3515625F;   // ~15 s por volta

            // batida em dois tempos (tum-tum) em vez de seno puro
            double bt = (t * 1.15) % 1.0;
            float b1 = (float) Math.exp(-bt * 18.0);
            float b2 = (float) Math.exp(-Math.max(0, bt - 0.22) * 20.0) * (bt > 0.22 ? 1 : 0);
            float pulso = 1.0F + 0.055F * (b1 + 0.55F * b2);

            float dy = Mth.sin((float) (t * 0.9)) * 0.022F;                  // flutua
            float incl = Mth.sin((float) (t * 0.53)) * 5.0F;                 // inclina devagar
            float brilho = 0.90F + 0.10F * (b1 + 0.5F * b2);

            pose.pushPose();
            pose.translate(0.5, 0.03 + dy, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(ang));
            pose.mulPose(Axis.ZP.rotationDegrees(incl));
            pose.mulPose(Axis.XP.rotationDegrees(6.0F));
            pose.scale(pulso, pulso, pulso);

            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            for (int q = 0; q + 2 < NV; q += 3) {
                for (int v = 0; v < 3; v++) {
                    int i = q + v;
                    int c = shade(VC[i], brilho);
                    vc.addVertex(pose.last(), VX[i], VY[i], VZ[i])
                            .setColor((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, 255);
                }
            }
            pose.popPose();
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: erro no coracao canonico", t);
        }
    }

    /** quantos triângulos o coração tem (usado na verificação). */
    public static int quads() {
        return NV / 3;
    }
}
