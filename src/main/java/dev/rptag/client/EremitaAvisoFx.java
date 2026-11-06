package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.cl;
import static dev.rptag.client.AvisoFxUtil.col;
import static dev.rptag.client.AvisoFxUtil.cos;
import static dev.rptag.client.AvisoFxUtil.mix;
import static dev.rptag.client.AvisoFxUtil.rnd;
import static dev.rptag.client.AvisoFxUtil.sin;
import static dev.rptag.client.AvisoFxUtil.ss;
import static dev.rptag.client.CartaTarotFx.*;

import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * IX · O EREMITA — estilo 28 do {@code /rp admin avisar}.
 *
 * <p>Portado do protótipo do dono ({@code EremitaWarning}): montanhas de neve em três camadas,
 * aurora verde-azul no céu, a lanterna pendurada que balança (e ilumina a neve em volta), o
 * sigilo de 22 caminhos e a carta com a estrela de seis pontas.
 */
public final class EremitaAvisoFx extends CartaTarotFx {

    private static final EremitaAvisoFx INST = new EremitaAvisoFx();
    private final Tema t = new TemaEremita();

    private EremitaAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        registrar(e, "eremita", INST);
    }

    public static void iniciar(String mensagem) {
        iniciar(INST, mensagem);
    }

    @Override
    protected Tema tema() {
        return t;
    }

    static final class TemaEremita extends CartaTarotFx.Tema {
        float lx = Float.NaN, ly;

        TemaEremita() {
            nome = "O EREMITA";
            romano = "IX";
            ac = 0x82D7EB;
            fogc = 0x96BED7;
            sky0 = 0x05080F;
            sky1 = 0x0F1D2B;
            glowc = 0x82D7EB;
            flashc = 0xC8EBFF;
            shc = 0x78D2F0;
            tg0 = 0xF4FCFF;
            tg1 = 0x8FDCF0;
            tg2 = 0x2A7A9A;
            bk = 0x07141C;
            pc0 = 0xCFD8E0;
            pc1 = 0x8FA0B0;
            ink = 0x142230;
            l1 = "Na solidão da montanha, só a lanterna conhece o caminho.";
            l3 = "Ela já foi até você.";
            l3c = 0xFFB86B;
            c1 = 0xAAC4D4;
            c2 = 0xF0FBFF;
            palavras = new String[]{"silêncio", "a lanterna guia", "sozinho", "neve", "o caminho", "norte",
                    "ninguém vem", "a estrela"};
        }

        @Override
        void reset() {
            lx = Float.NaN;
        }

        @Override
        void emblem() {
            hexagrama(8, 0, 1f, col(ac, 1f));
        }

        @Override
        void back(float t) {
            contorno(0, 0, 34, 1.5f, col(0x8FDCF0, 1f));
            save();
            rotate(t * 0.2f);
            hexagrama(30, 0, 1.5f, col(0x8FDCF0, 1f));
            restore();
        }

        @Override
        void art(float t) {
            degradeRect(-44, -52, 88, 106, 0x3A4A66, 0xAAB8CC);
            poligono(new float[]{-44, -14, 0, 16, 44}, new float[]{20, -30, -6, -24, 20}, 5, col(0xE8F0F8, 1f));
            retangulo(-44, 20, 88, 34, col(0xDBE6F0, 1f));
            poligono(new float[]{-10, -6, 0, 6, 12}, new float[]{36, -6, -12, -6, 36}, 5, col(0x3A4A5E, 1f));
            disco(0, -8, 5, 0xC8B8A0, 1f);
            retangulo(-5, -12, 10, 5, col(0xE8E8EE, 1f));
            linha(-16, 38, -14, -14, 2f, col(0x4A3A2A, 1f));
            linha(8, 6, 24, -16, 3f, col(0x3A4A5E, 1f));
            mancha(26, -22, 20, 0xFFDC82, 0.9f);
            disco(26, -22, 5, 0xFFE9A0, 1f);
        }

        @Override
        void bg(float T, float t) {
            float a = ss(0.3f, 1.5f, T);
            // estrelas
            for (int i = 0; i < 60; i++) {
                alfa = 1f;
                float al = a * (0.3f + 0.7f * (0.5f + 0.5f * sin(t * (0.8f + rn(i + 7, 1) * 2) + i)));
                retangulo(rn(i, 1) * PW, rn(i + 3, 2) * 330, 1.5f, 1.5f, col(0xE8F4FF, al));
            }
            // aurora em três larguras
            aditivo();
            float[] largs = {40, 16, 5}, als = {0.04f, 0.06f, 0.09f};
            for (int p = 0; p < 3; p++) {
                int cnt = (int) ((PW + 20) / 16) + 1;
                float[] xs = new float[cnt], ys = new float[cnt];
                for (int k = 0; k < cnt; k++) {
                    float x = -10 + k * 16;
                    xs[k] = x;
                    ys[k] = 130 + sin(x * 0.01f + t * 0.3f) * 40 + sin(x * 0.025f - t * 0.4f) * 12;
                }
                polilinha(xs, ys, cnt, largs[p], col(0x5AE6C8, als[p] * a));
            }
            normal();
            // as três camadas de montanha
            int[] cores = {0x0A1624, 0x08121E, 0x050B14};
            int m = (int) ((PW + 20) / 10) + 1;
            for (int i = 0; i < 3; i++) {
                float base = 340 + i * 55, amp = 90 - i * 20;
                float[] xs = new float[m + 2], ys = new float[m + 2];
                for (int k = 0; k < m; k++) {
                    float x = -10 + k * 10;
                    xs[k] = x;
                    ys[k] = base - Math.abs(sin(x * 0.011f + i * 3)) * amp * 0.8f
                            - rn((int) Math.floor(x / 10) + i * 50, 7) * 10;
                }
                xs[m] = PW + 10;
                ys[m] = PH;
                xs[m + 1] = -10;
                ys[m + 1] = PH;
                poligono(xs, ys, m + 2, col(cores[i], 1f));
                if (i < 2) {
                    polilinha(xs, ys, m, 1.5f, col(0xDCF0FF, 0.25f));
                }
            }
        }

        @Override
        void mid(float T, float t) {
            float a = ss(2f, 3f, T);
            if (a <= 0.01f) {
                return;
            }
            float ang = sin(t * 0.9f) * 0.12f, px = 650, L = 150;
            float ex = px + sin(ang) * L, ey = cos(ang) * L;
            lx = ex;
            ly = ey + 14;
            float fl = 0.7f + 0.3f * rn((int) Math.floor(t * 10), 3);
            aditivo();
            mancha(ex, ey + 14, 200, 0xFFBE5A, 0.45f * a * fl);
            normal();
            linha(px, 0, ex, ey, 1.5f, col(0xB4BEC8, 0.8f * a));
            save();
            translate(ex, ey);
            rotate(-ang);
            alfa = a;
            retangulo(-9, 0, 18, 4, col(0x2A2018, 1f));
            retangulo(-9, 26, 18, 4, col(0x2A2018, 1f));
            retangulo(-7, 4, 14, 22, col(0xFFC86E, 0.75f * fl));
            save();
            translate(0, 15);
            hexagrama(6, 0, 1f, col(0xFFF6D8, 1f));
            restore();
            alfa = 1f;
            restore();
            aditivo();
            for (int i = 0; i < 16; i++) {
                float x = PCX + cos(t * 0.2f + i * 2) * (150 + rn(i, 1) * 220);
                float y = PCY + 60 + sin(t * 0.3f + i * 3) * (60 + rn(i + 5, 2) * 120);
                float al = (0.3f + 0.7f * (0.5f + 0.5f * sin(t * 2 + i * 2))) * a * 0.8f;
                disco(x, y, 2, 0xFFC864, al);
            }
            normal();
        }

        @Override
        void front(float T, float t) {
            int cnt = 40 + (int) Math.floor(100 * ss(1f, 4f, T));
            for (int i = 0; i < cnt; i++) {
                float x = (rn(i, 1) * PW * 1.2f + sin(t * 0.5f + i) * 30 + t * 10) % PW;
                float y = (rn(i + 5, 2) * PH + t * (30 + rn(i + 3, 3) * 50)) % PH;
                float s = 1 + rn(i + 9, 4) * 1.6f;
                int rgb = 0xEBF5FF;
                float al = 0.55f;
                if (!Float.isNaN(lx)) {
                    float d = (float) Math.hypot(x - lx, y - ly);
                    if (d < 190) {
                        rgb = 0xFFD796;
                        al = 0.55f + 0.4f * (1 - d / 190);
                    }
                }
                retangulo(x, y, s, s, col(rgb, al));
            }
        }

        @Override
        void ring(float T, float t, float dp) {
            save();
            rotate(t * 0.1f);
            hexagrama(132, 0, 1.3f, col(0x8FDCF0, 0.6f * dp));
            restore();
            for (int k = 0; k < 8; k++) {
                float a = k * 0.7854f;
                linha(cos(a) * 142, sin(a) * 142, cos(a) * 158, sin(a) * 158, 1.3f, col(0x8FDCF0, 0.8f * dp));
            }
        }
    }
}
