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
 * XVII · A ESTRELA — estilo 35 do {@code /rp admin avisar}.
 *
 * <p>Portado do protótipo do dono ({@code EstrelaWarning}): a estrela de oito pontas gigante no
 * alto, a constelação de sete estrelas ligadas, a aurora em três espessuras, os dois rios de luz
 * descendo para a carta e a chuva de estrelinhas subindo.
 */
public final class EstrelaAvisoFx extends CartaTarotFx {

    private static final EstrelaAvisoFx INST = new EstrelaAvisoFx();
    private final Tema t = new TemaEstrela();

    private EstrelaAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        registrar(e, "estrela", INST);
    }

    public static void iniciar(String mensagem) {
        iniciar(INST, mensagem);
    }

    @Override
    protected Tema tema() {
        return t;
    }

    static final class TemaEstrela extends CartaTarotFx.Tema {
        final List<float[]> estrelinhas = new ArrayList<>();

        TemaEstrela() {
            nome = "A ESTRELA";
            romano = "XVII";
            ac = 0x96E1FF;
            fogc = 0x78AAE6;
            sky0 = 0x030616;
            sky1 = 0x0F2250;
            glowc = 0x82C8FF;
            flashc = 0xD2F0FF;
            shc = 0x78D2FF;
            tg0 = 0xFFFFFF;
            tg1 = 0x9FE0FF;
            tg2 = 0x3A7AD0;
            bk = 0x07102A;
            pc0 = 0xDCE8F6;
            pc1 = 0x9AB4D4;
            ink = 0x12285A;
            l1 = "Mesmo na noite mais escura, uma estrela sabe onde você está.";
            l3 = "Não deixe a calma enganar você.";
            l3c = 0x8FE8FF;
            c1 = 0xA8C4E4;
            c2 = 0xF0F8FF;
            palavras = new String[]{"desejo", "esperança", "brilhe", "a estrela guia", "calma", "sonhe",
                    "água clara", "amanhã"};
        }

        @Override
        void reset() {
            estrelinhas.clear();
        }

        @Override
        void emblem() {
            estrela8(0, 0, 10, 4, 0, false, ac, 1f);
        }

        @Override
        void back(float t) {
            estrela8(0, 0, 36, 10, t * 0.05f, false, 0xFFE9A0, 1f);
            contorno(0, 0, 12, 1.6f, col(0xFFE9A0, 1f));
            for (int k = 0; k < 7; k++) {
                float a = k / 7f * 6.2832f - 1.57f;
                estrela8(cos(a) * 54, sin(a) * 54 * 0.9f, 6, 2.4f, 0, true, 0xFFE9A0, 1f);
            }
        }

        @Override
        void art(float t) {
            degradeRect(-44, -52, 88, 106, 0x0F2A6A, 0x9FD0FF);
            estrela8(0, -30, 16, 5, 0, true, 0xFFD84A, 1f);
            for (int k = 0; k < 7; k++) {
                float a = (float) Math.PI + (k + 0.5f) / 7 * (float) Math.PI;
                estrela8(cos(a) * 34, -34 + sin(a) * 14, 5, 2, 0, true, 0xFFD84A, 1f);
            }
            elipse(0, 44, 34, 8, 0x2A6AD0, 1f);
            elipse(0, 28, 9, 6, 0xE8C9A0, 1f);
            disco(-6, 14, 4.5f, 0xE8C9A0, 1f);
            setor(-11, 9, 10, 10, 20, 150, 0x8A5A2A, 1f);
            linha(-2, 22, -22, 26, 3f, col(0xE8C9A0, 1f));
            linha(2, 24, 20, 30, 3f, col(0xE8C9A0, 1f));
            curva(-22, 26, -30, 34, -26, 42, 2f, col(0xB4E6FF, 0.9f));
            curva(20, 30, 30, 36, 26, 42, 2f, col(0xB4E6FF, 0.9f));
            retangulo(-26, 22, 8, 8, col(0xC27A3A, 1f));
            retangulo(18, 26, 8, 8, col(0xC27A3A, 1f));
        }

        @Override
        void bg(float T, float t) {
            float a = ss(0.3f, 1.5f, T);
            // as estrelas
            for (int i = 0; i < 120; i++) {
                float tw = 0.2f + 0.8f * (0.5f + 0.5f * sin(t * (0.6f + rn(i + 7, 1) * 2) + i));
                float sz = rn(i + 5, 2) > 0.9f ? 2 : 1;
                int cor = rn(i + 9, 3) > 0.6f ? 0xFFFFFF : 0xAAD7FF;
                retangulo(rn(i, 4) * PW, rn(i + 3, 5) * PH * 0.75f, sz, sz, col(cor, a * tw));
            }
            // a aurora em três espessuras
            aditivo();
            float[] largs = {40, 16, 5}, als = {0.04f, 0.06f, 0.09f};
            for (int p = 0; p < 3; p++) {
                int cnt = (int) ((PW + 20) / 16) + 1;
                float[] xs = new float[cnt], ys = new float[cnt];
                for (int k = 0; k < cnt; k++) {
                    float x = -10 + k * 16;
                    xs[k] = x;
                    ys[k] = 170 + sin(x * 0.009f + t * 0.3f) * 40 + sin(x * 0.02f - t * 0.4f) * 12;
                }
                polilinha(xs, ys, cnt, largs[p], col(0x64C8FF, als[p] * a));
            }
            // a constelação de sete
            float[] sx7 = new float[7], sy7 = new float[7];
            for (int k = 0; k < 7; k++) {
                float th = (float) Math.PI + (k + 0.5f) / 7 * (float) Math.PI;
                sx7[k] = PCX + cos(th) * 270;
                sy7[k] = 128 + sin(th) * 105;
            }
            polilinha(sx7, sy7, 7, 1f, col(0xAADCFF, 0.18f * a));
            normal();
            for (int k = 0; k < 7; k++) {
                float tw = 0.6f + 0.4f * sin(t * 2 + k);
                estrela8(sx7[k], sy7[k], 16 * tw, 5, t * 0.1f, true, 0xFFF0AA, a * tw);
            }
            // a estrela grande
            mancha(PCX, 95, 200, 0xFFEBAA, 0.5f * a);
            estrela8(PCX, 95, 70 + 4 * sin(t * 2), 14, t * 0.08f, true, 0xFFF6C8, a);
            // o risco de luz que passa de vez em quando
            float sh = (t % 4) / 0.7f;
            if (sh < 1 && T > 1.5f) {
                aditivo();
                float x0 = 80 + sh * 500, y0 = 40 + sh * 160;
                linha(x0, y0, x0 - 90, y0 - 30, 2, col(0xFFFFFF, 0.9f * (1 - sh)));
                normal();
            }
        }

        @Override
        void mid(float T, float t) {
            float a = ss(2.2f, 3.2f, T);
            if (a <= 0.01f) {
                return;
            }
            aditivo();
            // os dois rios de luz
            for (int s : new int[]{-1, 1}) {
                float x0 = s < 0 ? 40 : 760, x1 = PCX + s * 150;
                float[] pa = {10, 4, 1.5f};
                float[] al = {0.12f, 0.35f, 0.9f};
                for (int p = 0; p < 3; p++) {
                    curva(x0, 250, (x0 + x1) / 2, 230, x1, 468, pa[p], col(0x8CDCFF, al[p] * a));
                }
                for (int k = 0; k < 3; k++) {
                    float ph = (t * 0.8f + k / 3f + (s > 0 ? 0.15f : 0)) % 1;
                    float rr = 10 + ph * 40;
                    anel(x1, 470, rr, 1.5f, col(0xAAC8FF, (1 - ph) * 0.5f * a), 20);
                }
            }
            normal();
        }

        @Override
        void front(float T, float t) {
            if (T > 1.5f && R.nextFloat() < 0.6f && estrelinhas.size() < 110) {
                estrelinhas.add(new float[]{R.nextFloat() * PW, PH, -(0.2f + R.nextFloat() * 0.5f), 0,
                        120 + R.nextFloat() * 120, 1 + R.nextFloat() * 2});
            }
            aditivo();
            Iterator<float[]> it = estrelinhas.iterator();
            while (it.hasNext()) {
                float[] p = it.next();
                p[1] += p[2];
                p[0] += sin(t * 1.5f + p[1] * 0.02f) * 0.3f;
                p[3]++;
                if (p[3] > p[4]) {
                    it.remove();
                    continue;
                }
                float al = sin((float) Math.PI * p[3] / p[4]);
                retangulo(p[0], p[1], p[5], p[5], col(0xBEEBFF, al * 0.8f));
                if (p[5] > 2.2f) {
                    retangulo(p[0] - 4, p[1] + p[5] / 2, 10, 1, col(0xDCF5FF, al * 0.5f));
                    retangulo(p[0] + p[5] / 2, p[1] - 4, 1, 10, col(0xDCF5FF, al * 0.5f));
                }
            }
            normal();
        }

        @Override
        void ring(float T, float t, float dp) {
            for (int k = 0; k < 8; k++) {
                save();
                rotate(k * 0.7854f + t * 0.1f);
                translate(0, -150);
                float tw = 0.5f + 0.5f * sin(t * 2.5f - k * 0.8f);
                estrela8(0, 0, 5 + 5 * tw, 2, 0, true, 0xFFF0AA, (0.5f + 0.5f * tw) * dp);
                restore();
            }
        }
    }
}
