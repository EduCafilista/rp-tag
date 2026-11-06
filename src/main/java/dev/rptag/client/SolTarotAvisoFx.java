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
 * XIX · O SOL — estilo 33 do {@code /rp admin aviso}.
 *
 * <p>Portado do protótipo do dono ({@code SolWarning}): o sol dourado nascendo com 24 raios, os
 * girassóis, as três colunas de luz, as penas caindo e a poeira de luz subindo.
 */
public final class SolTarotAvisoFx extends CartaTarotFx {

    private static final SolTarotAvisoFx INST = new SolTarotAvisoFx();
    private final Tema t = new TemaSol();

    private SolTarotAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        registrar(e, "sol_tarot", INST);
    }

    public static void iniciar(String mensagem) {
        iniciar(INST, mensagem);
    }

    @Override
    protected Tema tema() {
        return t;
    }

    static final class TemaSol extends CartaTarotFx.Tema {
        final List<float[]> penas = new ArrayList<>();
        final List<float[]> poeira = new ArrayList<>();

        TemaSol() {
            nome = "O SOL";
            romano = "XIX";
            ac = 0xFFCD64;
            fogc = 0xFFCD8C;
            sky0 = 0x180A06;
            sky1 = 0x3E1F0E;
            glowc = 0xFFAA3C;
            flashc = 0xFFEBAA;
            shc = 0xFFBE50;
            tg0 = 0xFFFBE8;
            tg1 = 0xFFC850;
            tg2 = 0xC8560C;
            bk = 0x2A1408;
            pc0 = 0xF1E3B8;
            pc1 = 0xC9A45E;
            ink = 0x4A2A08;
            l1 = "Quando o sol nasce, nenhuma sombra se esconde.";
            l3 = "A luz já encontrou você.";
            l3c = 0xFF9A3C;
            c1 = 0xE8CFA0;
            c2 = 0xFFF6E0;
            palavras = new String[]{"luz", "a aurora vê", "Sol", "não se esconda", "cantem", "o amanhecer cobra",
                    "brilho", "sem sombras"};
        }

        @Override
        void reset() {
            penas.clear();
            poeira.clear();
        }

        @Override
        void emblem() {
            contorno(0, 0, 4, 1f, col(ac, 1f));
            for (int k = 0; k < 8; k++) {
                float a = k * 0.785f;
                linha(cos(a) * 6, sin(a) * 6, cos(a) * 10, sin(a) * 10, 1f, col(ac, 1f));
            }
        }

        @Override
        void back(float t) {
            contorno(0, 0, 18, 1.5f, col(0xFFD36A, 1f));
            contorno(0, 0, 10, 1.5f, col(0xFFD36A, 1f));
            for (int k = 0; k < 16; k++) {
                float a = k * 0.3927f + t * 0.2f, r = k % 2 == 1 ? 32 : 38;
                linha(cos(a) * 22, sin(a) * 22, cos(a) * r, sin(a) * r, 1.5f, col(0xFFD36A, 1f));
            }
        }

        @Override
        void art(float t) {
            degradeRect(-44, -52, 88, 106, 0xF5C660, 0xFFF2C0);
            // os doze raios
            for (int k = 0; k < 12; k++) {
                save();
                translate(0, -14);
                rotate(k * 0.5236f + t * 0.1f);
                poligono(new float[]{-3, 0, 3}, new float[]{-17, -34, -17}, 3, col(0xC85A10, 1f));
                restore();
            }
            disco(0, -14, 17, 0xE8791A, 1f);
            retangulo(-44, 36, 88, 18, col(0x6A4A1A, 1f));
            // os dois girassóis
            for (int x : new int[]{-28, 28}) {
                linha(x, 50, x, 30, 2f, col(0x3A7A2A, 1f));
                for (int k = 0; k < 8; k++) {
                    float a = k * 0.785f;
                    save();
                    translate(x + cos(a) * 6, 26 + sin(a) * 6);
                    rotate(a);
                    elipse(0, 0, 4, 2.2f, 0xF6C21A, 1f);
                    restore();
                }
                disco(x, 26, 4, 0x5A3410, 1f);
            }
        }

        @Override
        void bg(float T, float t) {
            float a = ss(0.5f, 2f, T), sx0 = PCX, sy0 = 90;
            mancha(sx0, sy0, 330, 0xFFD782, 0.55f * a);
            mancha(sx0, sy0, 200, 0xFF963C, 0.2f * a);
            save();
            translate(sx0, sy0);
            rotate(t * 0.04f);
            aditivo();
            for (int k = 0; k < 24; k++) {
                rotate(TAU / 24f);
                float L = k % 2 == 1 ? 520 : 380;
                poligono(new float[]{0, -10, 10}, new float[]{0, -L, -L}, 3,
                        col(0xFFD278, (0.07f + 0.03f * sin(t * 2 + k)) * a));
            }
            normal();
            restore();
            alfa = a;
            disco(sx0, sy0, 54 + 2 * sin(t * 2), 0xFFB340, 1f);
            disco(sx0, sy0, 46 + 2 * sin(t * 2), 0xFFDF8A, 1f);
            disco(sx0, sy0, 36 + 2 * sin(t * 2), 0xFFFDF0, 1f);
            alfa = 1f;
        }

        @Override
        void mid(float T, float t) {
            if (T < 2.5f) {
                return;
            }
            float a = ss(2.5f, 3.3f, T) * (T < 12.4f ? 1 : 0);
            if (a <= 0.01f) {
                return;
            }
            aditivo();
            // as três colunas de luz (degrade do topo para baixo)
            for (int j = 0; j < 3; j++) {
                float x = PCX + (j - 1) * 170, fl = 0.7f + 0.3f * rn((int) Math.floor(t * 8) + j, 1);
                int n = 8;
                for (int i = 0; i < n; i++) {
                    float al = 0.2f * a * fl * (1 - i / (float) n);
                    retangulo(x - 22, 470f * i / n, 44, 470f / n + 0.5f, col(0xFFEBAA, al));
                }
            }
            normal();
        }

        @Override
        void front(float T, float t) {
            // as penas
            if (T > 2 && R.nextFloat() < 0.25f && penas.size() < 40) {
                penas.add(new float[]{R.nextFloat() * PW, -10, 0.5f + R.nextFloat() * 0.6f, R.nextFloat() * 6.28f,
                        R.nextFloat() * 6.28f, 5 + R.nextFloat() * 4});
            }
            Iterator<float[]> it = penas.iterator();
            while (it.hasNext()) {
                float[] p = it.next();
                p[1] += p[2];
                p[0] += sin(t + p[3]) * 0.7f;
                p[4] += 0.02f;
                if (p[1] > PH + 10) {
                    it.remove();
                    continue;
                }
                float s = p[5];
                save();
                translate(p[0], p[1]);
                rotate(p[4]);
                alfa = 0.85f;
                folha(0, -s * 2, s, -s * 0.2f, 0, s * 2, -s, -s * 0.2f, col(0xFFF3D0, 1f));
                linha(0, -s * 2, 0, s * 2, 0.8f, col(0xE0A840, 1f));
                alfa = 1f;
                restore();
            }
            // a poeira de luz
            if (T > 1.5f && R.nextFloat() < 0.6f && poeira.size() < 100) {
                poeira.add(new float[]{R.nextFloat() * PW, PH, -(0.3f + R.nextFloat() * 0.6f), 0,
                        100 + R.nextFloat() * 100, 1 + R.nextFloat() * 1.6f});
            }
            aditivo();
            it = poeira.iterator();
            while (it.hasNext()) {
                float[] p = it.next();
                p[1] += p[2];
                p[0] += sin(t * 2 + p[1] * 0.03f) * 0.3f;
                p[3]++;
                if (p[3] > p[4]) {
                    it.remove();
                    continue;
                }
                retangulo(p[0], p[1], p[5], p[5], col(0xFFE196, sin((float) Math.PI * p[3] / p[4]) * 0.8f));
            }
            normal();
        }

        @Override
        void ring(float T, float t, float dp) {
            for (int k = 0; k < 12; k++) {
                save();
                rotate(k * 0.5236f + t * 0.1f);
                translate(0, -150);
                contorno(0, 0, 3, 1.2f, col(0xFFD778, 0.9f * dp));
                for (int j = 0; j < 8; j++) {
                    float a = j * 0.785f;
                    linha(cos(a) * 5, sin(a) * 5, cos(a) * 9, sin(a) * 9, 1.2f, col(0xFFD778, 0.9f * dp));
                }
                restore();
            }
        }
    }
}
