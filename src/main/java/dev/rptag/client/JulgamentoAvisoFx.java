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
 * XX · O JULGAMENTO — estilo 29 do {@code /rp admin avisar}.
 *
 * <p>Portado do protótipo do dono ({@code JulgamentoWarning}): as asas gigantes abertas atrás da
 * carta, o anjo com a trombeta, as ondas douradas do som, as almas subindo em espiral e as penas
 * caindo.
 */
public final class JulgamentoAvisoFx extends CartaTarotFx {

    private static final JulgamentoAvisoFx INST = new JulgamentoAvisoFx();
    private final Tema t = new TemaJulgamento();

    private JulgamentoAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        registrar(e, "julgamento", INST);
    }

    public static void iniciar(String mensagem) {
        iniciar(INST, mensagem);
    }

    @Override
    protected Tema tema() {
        return t;
    }

    static final class TemaJulgamento extends CartaTarotFx.Tema {
        final List<float[]> almas = new ArrayList<>();

        TemaJulgamento() {
            nome = "O JULGAMENTO";
            romano = "XX";
            ac = 0xC8D7EB;
            fogc = 0xBECDE1;
            sky0 = 0x06080D;
            sky1 = 0x1C2433;
            glowc = 0xE1EEFF;
            flashc = 0xEBF2FF;
            shc = 0xD2E1FA;
            tg0 = 0xFFFFFF;
            tg1 = 0xD8E4F4;
            tg2 = 0x7A8AA8;
            bk = 0x0D121C;
            pc0 = 0xDFE3EA;
            pc1 = 0xA2ADBF;
            ink = 0x1C2433;
            l1 = "A trombeta soou: todos serão chamados a responder.";
            l3 = "Sua hora chegou.";
            l3c = 0xE8C46A;
            c1 = 0xB8C4D8;
            c2 = 0xF4F8FF;
            palavras = new String[]{"levanta-te", "a trombeta soa", "julgamento", "o que você fez?", "almas",
                    "ninguém escapa", "é a hora", "a balança pesa"};
        }

        @Override
        void reset() {
            almas.clear();
        }

        @Override
        void emblem() {
            poligono(new float[]{0, 6, 2, 2, -2, -2, -6}, new float[]{-9, 0, 0, 8, 8, 0, 0}, 7, col(ac, 1f));
        }

        @Override
        void back(float t) {
            contorno(0, -22, 10, 1.5f, col(0xD8E4F4, 1f));
            for (int s : new int[]{-1, 1}) {
                for (int k = 0; k < 5; k++) {
                    curva(s * 6, -4, s * (24 + k * 5), -30 + k * 10, s * (38 - k * 3), -6 + k * 14, 1.5f,
                            col(0xD8E4F4, 1f));
                }
            }
        }

        @Override
        void art(float t) {
            degradeRect(-44, -52, 88, 106, 0x8FA6C8, 0xEEF3FA);
            float[][] nuvens = {{-30, -10, 12}, {-20, -6, 9}, {30, -14, 11}, {22, -8, 8}};
            for (float[] c : nuvens) {
                elipse(c[0], c[1], c[2] * 1.6f, c[2] * 0.7f, 0xFFFFFF, 0.8f);
            }
            poligono(new float[]{0, -30, -20, 0, 20, 30}, new float[]{-34, -44, -24, -28, -24, -44}, 6,
                    col(0xF4F8FF, 1f));
            poligono(new float[]{-5, 5, 7, -7}, new float[]{-30, -30, -10, -10}, 4, col(0xFFFFFF, 1f));
            disco(0, -34, 5, 0xE8C9A0, 1f);
            linha(5, -22, 30, -30, 2.5f, col(0xD4A838, 1f));
            linha(30, -34, 30, -26, 2.5f, col(0xD4A838, 1f));
            retangulo(-44, 36, 88, 18, col(0x6A7A90, 1f));
            for (int x : new int[]{-26, 0, 26}) {
                retangulo(x - 7, 38, 14, 6, col(0x2A303C, 1f));
                disco(x, 24, 4, 0x2A303C, 1f);
                retangulo(x - 3, 26, 6, 12, col(0x2A303C, 1f));
                linha(x - 3, 28, x - 9, 18, 2f, col(0x2A303C, 1f));
                linha(x + 3, 28, x + 9, 18, 2f, col(0x2A303C, 1f));
            }
        }

        @Override
        void bg(float T, float t) {
            float a = ss(0.5f, 2f, T);
            mancha(PCX, -40, 520, 0xE1EEFF, 0.3f * a);
            aditivo();
            // as nove penas de cada asa
            for (int s : new int[]{-1, 1}) {
                for (int k = 0; k < 9; k++) {
                    float thk = -(70 - k * 7) * 0.0174533f + sin(t * 0.8f) * 0.08f * (k / 9f);
                    float L = 330 - k * 18, bx = PCX + s * 40, by = PCY - 30;
                    float ex = bx + s * cos(thk) * L, ey = by + sin(thk) * L;
                    float mx = (bx + ex) / 2, my = (by + ey) / 2;
                    float px = -sin(thk) * 14, py = cos(thk) * 14 * s;
                    folha(bx, by, mx + px * s, my + py, ex, ey, mx - px * s, my - py, col(0xE1EBFA, 0.07f * a));
                    curva(bx, by, mx + px * s, my + py, ex, ey, 1f, col(0xE1EBFA, 0.14f * a));
                    curva(bx, by, mx - px * s, my - py, ex, ey, 1f, col(0xE1EBFA, 0.14f * a));
                }
            }
            // as ondas douradas do som da trombeta
            float amp = 0.5f + 0.5f * ss(4.8f, 5.3f, T);
            for (int k = 0; k < 4; k++) {
                float r = (t * 110 + k * 80) % 320;
                contorno(PCX, 30, r, 2 + 2 * (1 - r / 320), col(0xE6BE6E, (1 - r / 320) * 0.22f * amp * a));
            }
            normal();
        }

        @Override
        void mid(float T, float t) {
            if (T > 2.5f && R.nextFloat() < 0.15f && almas.size() < 50) {
                almas.add(new float[]{120 + R.nextFloat() * 560, 470, -(0.4f + R.nextFloat() * 0.6f),
                        R.nextFloat() * 6.28f});
            }
            aditivo();
            Iterator<float[]> it = almas.iterator();
            while (it.hasNext()) {
                float[] p = it.next();
                p[1] += p[2];
                p[0] += sin(t * 1.5f + p[3]) * 0.5f;
                if (p[1] < 40) {
                    it.remove();
                    continue;
                }
                float al = Math.min(1, (470 - p[1]) / 60) * Math.min(1, (p[1] - 40) / 120);
                for (int k = 5; k > 0; k--) {
                    disco(p[0] - sin(t * 1.5f + p[3] + k * 0.2f) * 0.5f * k, p[1] + k * 5, 3,
                            0xE6F0FF, 0.08f * al * (6 - k));
                }
                mancha(p[0], p[1], 14, 0xFFFFFF, 0.95f * al);
                disco(p[0], p[1], 4, 0xD2E1FF, 0.4f * al);
            }
            normal();
        }

        @Override
        void front(float T, float t) {
            aditivo();
            float a = ss(2f, 3f, T);
            // os nove feixes de luz do alto
            for (int k = 0; k < 9; k++) {
                float ang = (-35 + k * 8.75f) * 0.0174533f;
                poligono(new float[]{PCX, PCX + sin(ang - 0.03f) * 700, PCX + sin(ang + 0.03f) * 700},
                        new float[]{-20, cos(ang - 0.03f) * 700 - 20, cos(ang + 0.03f) * 700 - 20}, 3,
                        col(0xE6F0FF, (0.05f + 0.03f * sin(t * 1.5f + k)) * a));
            }
            normal();
            // as penas caindo
            for (int i = 0; i < 50; i++) {
                float x = (rn(i, 1) * PW + sin(t * 0.6f + i) * 25) % PW;
                float y = (rn(i + 5, 2) * PH + t * (25 + rn(i + 3, 3) * 40)) % PH;
                save();
                translate(x, y);
                rotate(t * 0.5f + i);
                folha(0, -4, 2, 0, 0, 4, -2, 0, col(0xE8EEF8, 0.5f));
                restore();
            }
        }

        @Override
        void ring(float T, float t, float dp) {
            for (int k = 0; k < 12; k++) {
                save();
                rotate(k * 0.5236f);
                translate(0, -150);
                poligono(new float[]{0, 5, -5}, new float[]{-7, 2, 2}, 3,
                        col(0xEBF0FF, (0.3f + 0.7f * Math.max(0, sin(t * 2 - k * 0.5f))) * dp));
                restore();
            }
        }
    }
}
