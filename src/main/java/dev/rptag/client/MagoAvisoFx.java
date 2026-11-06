package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.cl;
import static dev.rptag.client.AvisoFxUtil.col;
import static dev.rptag.client.AvisoFxUtil.cos;
import static dev.rptag.client.AvisoFxUtil.mix;
import static dev.rptag.client.AvisoFxUtil.rnd;
import static dev.rptag.client.AvisoFxUtil.sin;
import static dev.rptag.client.AvisoFxUtil.ss;
import static dev.rptag.client.CartaTarotFx.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * I · O MAGO — estilo 31 do {@code /rp admin aviso}.
 *
 * <p>Portado do protótipo do dono ({@code MagoWarning}): a chuva de runas verdes, a lemniscata
 * (o infinito) percorrida por uma cauda de luz, as faíscas que saem da ponta e os quatro
 * elementos (fogo, água, terra e ar) girando em volta.
 */
public final class MagoAvisoFx extends CartaTarotFx {

    private static final MagoAvisoFx INST = new MagoAvisoFx();
    private final Tema t = new TemaMago();

    private MagoAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        registrar(e, "mago", INST);
    }

    public static void iniciar(String mensagem) {
        iniciar(INST, mensagem);
    }

    @Override
    protected Tema tema() {
        return t;
    }

    static final class TemaMago extends CartaTarotFx.Tema {
        final List<float[]> faiscas = new ArrayList<>();
        final int[][] el = {{255, 100, 50}, {80, 160, 255}, {170, 130, 70}, {235, 245, 255}};

        TemaMago() {
            nome = "O MAGO";
            romano = "I";
            ac = 0x50DCA0;
            fogc = 0x5AC8AA;
            sky0 = 0x04100D;
            sky1 = 0x0F2A22;
            glowc = 0x50DCA0;
            flashc = 0xB4FFDC;
            shc = 0x50E6A0;
            tg0 = 0xF0FFF6;
            tg1 = 0x5FE8A8;
            tg2 = 0x16805A;
            bk = 0x06201A;
            pc0 = 0xE7DDB8;
            pc1 = 0xB8A66C;
            ink = 0x12392F;
            l1 = "Como acima, assim abaixo: a vontade molda o mundo.";
            l3 = "O feitiço já foi lançado.";
            l3c = 0xFF6B4A;
            c1 = 0xA9D8C6;
            c2 = 0xEFFFF8;
            palavras = new String[]{"arcano", "vontade", "como acima", "assim abaixo", "fogo", "água", "terra",
                    "ar", "runas", "a palavra molda"};
        }

        @Override
        void reset() {
            faiscas.clear();
        }

        /** ponto da lemniscata (o infinito) no canvas. */
        static float[] lem(float u) {
            float d = 1 + sin(u) * sin(u);
            return new float[]{PCX + 270 * cos(u) / d, PCY - 10 + 120 * sin(u) * cos(u) / d};
        }

        /** o caminho da lemniscata escalado, centrado em (0, cy). */
        static void lemPath(float sc, float yOff, int n, float cy, float w, int cor) {
            float[] xs = new float[n + 1], ys = new float[n + 1];
            for (int k = 0; k <= n; k++) {
                float u = k / (float) n * 6.2832f, d = 1 + sin(u) * sin(u);
                xs[k] = sc * cos(u) / d;
                ys[k] = cy + yOff * sin(u) * cos(u) / d;
            }
            polilinha(xs, ys, n + 1, w, cor);
        }

        @Override
        void emblem() {
            lemPath(9, 9, 24, 0, 1f, col(ac, 1f));
        }

        @Override
        void back(float t) {
            lemPath(36, 36, 60, 0, 2f, col(0xFFD36A, 1f));
            for (int i = 0; i < 4; i++) {
                save();
                rotate(i * 1.5708f);
                translate(0, -60);
                simbolo(i, 5, 1.2f, col(0x5FE8A8, 1f));
                restore();
            }
        }

        @Override
        void art(float t) {
            degradeRect(-44, -52, 88, 106, 0xD9B64A, 0xF2E29A);
            lemPath(12, 6, 40, -40, 2f, col(0xB8860B, 1f));
            retangulo(-1.5f, -30, 3, 3, col(0xFFFFFF, 1f));
            linha(8, -4, 18, -34, 3f, col(0xFFFFFF, 1f));
            retangulo(16, -37, 4, 6, col(0x8A5A10, 1f));
            poligono(new float[]{-9, 9, 18, -18}, new float[]{-14, -14, 32, 32}, 4, col(0xB3202A, 1f));
            retangulo(-9, -14, 18, 5, col(0xF4F0E6, 1f));
            disco(0, -20, 7, 0xE8C9A0, 1f);
            setor(-7, -29, 14, 14, 10, 160, 0x5A3A1A, 1f);
            retangulo(-40, 32, 80, 10, col(0x6A3A1A, 1f));
            disco(-28, 30, 3, 0xB8D8FF, 1f);
            retangulo(-14, 27, 1.5f, 6, col(0xD9D9D9, 1f));
            disco(8, 30, 3, 0xFFD24A, 1f);
            retangulo(26, 26, 2, 7, col(0xEEEEEE, 1f));
            for (int x : new int[]{-36, -22, 24, 36}) {
                disco(x, 46, 3, 0xC01830, 1f);
            }
        }

        @Override
        void bg(float T, float t) {
            float a = ss(0.5f, 2f, T);
            // a chuva de runas
            for (int i = 0; i < 16; i++) {
                float x = 30 + i * 48 + rn(i, 1) * 16, spd = 40 + rn(i + 3, 2) * 60;
                float y0 = ((t * spd + rn(i + 7, 3) * 1600) % (PH + 240)) - 120;
                for (int k = 0; k < 6; k++) {
                    int gk = (int) Math.floor(rn(i * 7 + k + (int) Math.floor(t * (1 + rn(i, 4) * 2)), 5) * 5);
                    save();
                    translate(x, y0 - k * 20);
                    glifo(gk, 1.2f, col(0x50DCA0, (1 - k / 6f) * 0.4f * a));
                    restore();
                }
            }
        }

        @Override
        void mid(float T, float t) {
            float a = ss(2f, 3f, T);
            if (a <= 0.01f) {
                return;
            }
            // a lemniscata inteira
            float[] xs = new float[121], ys = new float[121];
            for (int k = 0; k <= 120; k++) {
                float[] p = lem(k / 120f * 6.2832f);
                xs[k] = p[0];
                ys[k] = p[1];
            }
            polilinha(xs, ys, 121, 1.5f, col(0x50DCA0, 0.2f * a));
            aditivo();
            float u0 = t * 1.1f;
            // a cauda de luz
            for (int k = 0; k < 50; k++) {
                float[] p = lem(u0 - k * 0.05f);
                int cor = k % 2 == 1 ? 0xFFD26E : 0x78FFC8;
                disco(p[0], p[1], 3.2f * (1 - k / 60f), cor, (1 - k / 50f) * 0.8f * a);
            }
            float[] h = lem(u0);
            if (R.nextFloat() < 0.8f) {
                faiscas.add(new float[]{h[0], h[1], (R.nextFloat() - 0.5f) * 1.4f, (R.nextFloat() - 0.5f) * 1.4f, 0,
                        30 + R.nextFloat() * 30, 1 + R.nextFloat() * 1.6f});
            }
            Iterator<float[]> it = faiscas.iterator();
            while (it.hasNext()) {
                float[] p = it.next();
                p[0] += p[2];
                p[1] += p[3];
                p[4]++;
                if (p[4] > p[5] || faiscas.size() > 200) {
                    it.remove();
                    continue;
                }
                retangulo(p[0], p[1], p[6], p[6], col(0xFFE18C, (1 - p[4] / p[5]) * 0.9f));
            }
            // os quatro elementos
            for (int i = 0; i < 4; i++) {
                float o = ss(4.5f + i * 0.3f, 5.1f + i * 0.3f, T);
                if (o <= 0.01f) {
                    continue;
                }
                float ang = t * 0.35f + i * 1.5708f;
                float x = PCX + cos(ang) * 262, y = PCY + sin(ang) * 72 - 10;
                float r = 15 + 3 * sin(t * 3 + i);
                mancha(x, y, r * 1.6f, el[i][0] << 16 | el[i][1] << 8 | el[i][2], 0.8f * o);
                disco(x, y, r, el[i][0] << 16 | el[i][1] << 8 | el[i][2], 0.25f * o);
                save();
                translate(x, y);
                simbolo(i, 8, 1.6f, col(0xFFFFFF, o));
                restore();
            }
            normal();
        }

        @Override
        void front(float T, float t) {
        }

        @Override
        void ring(float T, float t, float dp) {
            for (int i = 0; i < 4; i++) {
                save();
                rotate(i * 1.5708f);
                translate(0, -150);
                simbolo(i, 9, 1.6f, col(0x78FFC8, (0.35f + 0.65f * Math.max(0, sin(t * 2 - i * 1.57f))) * dp));
                restore();
            }
        }
    }
}
