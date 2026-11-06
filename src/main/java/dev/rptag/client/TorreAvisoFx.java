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
 * XVI · A TORRE — estilo 34 do {@code /rp admin avisar}.
 *
 * <p>Portado do protótipo do dono ({@code TorreWarning}): o raio caindo cinco vezes na torre, a
 * coroa arrancada voando, os destroços, as duas figuras caindo e as brasas subindo.
 */
public final class TorreAvisoFx extends CartaTarotFx {

    private static final TorreAvisoFx INST = new TorreAvisoFx();
    private final Tema t = new TemaTorre();

    private TorreAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        registrar(e, "torre", INST);
    }

    public static void iniciar(String mensagem) {
        iniciar(INST, mensagem);
    }

    @Override
    protected Tema tema() {
        return t;
    }

    /** um raio: a lista de pontos e quando ele nasceu. */
    static final class Raio {
        float[] xs, ys;
        float nasceu;
    }

    static final class TemaTorre extends CartaTarotFx.Tema {
        final List<Raio> raios = new ArrayList<>();
        final List<float[]> destrocos = new ArrayList<>();
        final List<float[]> figuras = new ArrayList<>();
        final List<float[]> brasas = new ArrayList<>();
        float lt = 0, lt2 = 0;

        TemaTorre() {
            nome = "A TORRE";
            romano = "XVI";
            ac = 0xFF963C;
            fogc = 0x78463C;
            sky0 = 0x0A0305;
            sky1 = 0x2E0E0A;
            glowc = 0xFF6428;
            flashc = 0xFFE6B4;
            shc = 0xFF7828;
            tg0 = 0xFFF4D0;
            tg1 = 0xFF9A3C;
            tg2 = 0xB01C10;
            bk = 0x1A0806;
            pc0 = 0xE8D4B0;
            pc1 = 0xB89060;
            ink = 0x3A1408;
            l1 = "O que foi construído sobre a mentira não resiste ao raio.";
            l3 = "A torre já está caindo.";
            l3c = 0xFFD24A;
            c1 = 0xE0B894;
            c2 = 0xFFF4E6;
            palavras = new String[]{"desabar", "o raio caiu", "ruína", "nada permanece", "queda", "fogo",
                    "tudo cai", "cinzas quentes"};
        }

        @Override
        void reset() {
            raios.clear();
            destrocos.clear();
            figuras.clear();
            brasas.clear();
            lt = 0;
            lt2 = 0;
        }

        /** o raio trêmulo do protótipo (deslocamento fractal). */
        static float[][] jag(float x0, float y0, float x1, float y1, float off, int its) {
            List<float[]> p = new ArrayList<>();
            p.add(new float[]{x0, y0});
            p.add(new float[]{x1, y1});
            for (int it = 0; it < its; it++) {
                List<float[]> nw = new ArrayList<>();
                for (int i = 0; i < p.size() - 1; i++) {
                    float[] a = p.get(i), b = p.get(i + 1);
                    nw.add(a);
                    nw.add(new float[]{(a[0] + b[0]) / 2 + (R.nextFloat() - 0.5f) * off,
                            (a[1] + b[1]) / 2 + (R.nextFloat() - 0.5f) * off * 0.4f});
                }
                nw.add(p.get(p.size() - 1));
                p = nw;
                off *= 0.55f;
            }
            float[][] r = new float[2][p.size()];
            for (int i = 0; i < p.size(); i++) {
                r[0][i] = p.get(i)[0];
                r[1][i] = p.get(i)[1];
            }
            return r;
        }

        @Override
        void emblem() {
            poligono(new float[]{3, -4, 1, -3, 5, 0}, new float[]{-10, 0, 0, 10, -3, -3}, 6, col(ac, 1f));
        }

        @Override
        void back(float t) {
            float[] xs = {-14, -10, 10, 14}, ys = {50, -20, -20, 50};
            for (int i = 0; i < 4; i++) {
                linha(xs[i], ys[i], xs[(i + 1) % 4], ys[(i + 1) % 4], 2f, col(0xFF9A3C, 1f));
            }
            polilinha(new float[]{-14, -18, -8, 0, 8, 18, 14}, new float[]{-20, -34, -26, -38, -26, -34, -20}, 7,
                    2f, col(0xFF9A3C, 1f));
            poligono(new float[]{24, 8, 16, 2, 28, 18}, new float[]{-52, -10, -10, 30, -20, -20}, 6,
                    col(0xFFD24A, 1f));
        }

        @Override
        void art(float t) {
            degradeRect(-44, -52, 88, 106, 0x1A1630, 0x7A2A18);
            poligono(new float[]{-16, -12, 12, 16}, new float[]{40, -14, -14, 40}, 4, col(0x8A8A98, 1f));
            retangulo(-3, 2, 6, 9, col(0xFFB040, 1f));
            retangulo(-3, 22, 6, 9, col(0xFFB040, 1f));
            float fl = sin(t * 10) * 2;
            // o fogo saindo pelo topo
            float[] fx = new float[22], fy = new float[22];
            int p = 0;
            fx[p] = -12;
            fy[p] = -14;
            p++;
            float px = -12, py = -14;
            for (int seg = 0; seg < 3; seg++) {
                float cx = seg == 0 ? -8 : seg == 1 ? 2 : 14;
                float cy = seg == 0 ? -30 + fl : seg == 1 ? -36 + fl : -26;
                float ex = seg == 0 ? -2 : seg == 1 ? 8 : 12;
                float ey = seg == 0 ? -18 : seg == 1 ? -18 : -14;
                for (int i = 1; i <= 6; i++) {
                    float u = i / 6f, v = 1 - u;
                    fx[p] = v * v * px + 2 * v * u * cx + u * u * ex;
                    fy[p] = v * v * py + 2 * v * u * cy + u * u * ey;
                    p++;
                }
                px = ex;
                py = ey;
            }
            poligono(fx, fy, p, col(0xFF6A20, 1f));
            retangulo(-36, -44, 14, 6, col(0xD4B060, 1f));
            retangulo(-36, -48, 3, 4, col(0xD4B060, 1f));
            retangulo(-29, -48, 3, 4, col(0xD4B060, 1f));
            retangulo(-23, -48, 3, 4, col(0xD4B060, 1f));
            poligono(new float[]{30, 14, 22, 10, 32, 24}, new float[]{-52, -22, -22, 8, -28, -28}, 6,
                    col(0xFFE060, 1f));
            // as duas pessoas caindo dentro da carta
            disco(-30, -6, 3, 0x0B0508, 1f);
            retangulo(-32, -3, 4, 8, col(0x0B0508, 1f));
            disco(30, 14, 3, 0x0B0508, 1f);
            retangulo(28, 17, 4, 8, col(0x0B0508, 1f));
            retangulo(-44, 40, 88, 14, col(0x2A1A14, 1f));
        }

        @Override
        void bg(float T, float t) {
            if (T < lt) {
                raios.clear();
            }
            for (float s : new float[]{2.2f, 3.5f, 4.8f, 7.0f, 9.2f}) {
                if (lt < s && T >= s) {
                    Raio r = new Raio();
                    float[][] pts = jag(PCX + 30, -10, PCX, 136, 60, 6);
                    r.xs = pts[0];
                    r.ys = pts[1];
                    r.nasceu = T;
                    raios.add(r);
                }
            }
            lt = T;
            float a = ss(0.3f, 1.5f, T), fl = 0.7f + 0.3f * rn((int) Math.floor(t * 9), 1);
            mancha(PCX, 470, 430, 0xFF6428, 0.5f * a * fl);
            retangulo(0, 470, PW, PH - 470, col(0x07040A, 1f));
            // a torre
            poligono(new float[]{PCX - 66, PCX - 50, PCX + 50, PCX + 66}, new float[]{470, 150, 150, 470}, 4,
                    col(0x0B0508, 1f));
            for (int y : new int[]{200, 280, 380}) {
                retangulo(PCX - 8, y, 16, 26, col(0xFF963C, (0.5f + 0.5f * rn((int) Math.floor(t * 8) + y, 2)) * a));
            }
            // a coroa arrancada voando
            float dt = Math.max(0, T - 4.8f), cx = dt * 110, cy = -110 * dt + 170 * dt * dt;
            if (150 + cy < 520) {
                save();
                translate(PCX + cx, 150 + cy);
                rotate(dt * 2.6f);
                float[] xs = {-58, -62, -44, -30, -14, 0, 14, 30, 44, 62, 58};
                float[] ys = {0, -30, -16, -38, -18, -42, -18, -38, -16, -30, 0};
                poligono(xs, ys, 11, col(0x0B0508, 1f));
                if (dt > 0) {
                    for (int i = 0; i < 11; i++) {
                        linha(xs[i], ys[i], xs[(i + 1) % 11], ys[(i + 1) % 11], 1.5f, col(0xFF8C32, 0.8f));
                    }
                }
                restore();
            }
            // os raios
            for (Raio b : raios) {
                float age = T - b.nasceu;
                if (age > 0.35f) {
                    continue;
                }
                float al = (rn((int) Math.floor(age * 40) + 1, 3) > 0.2f ? 1 : 0.4f) * (1 - age / 0.35f);
                aditivo();
                float[] lw = {9, 3.5f, 1.4f};
                int[] cs = {0xFFA03C, 0xFFDC96, 0xFFFFFF};
                float[] alphas = {0.2f * al, 0.7f * al, al};
                for (int k = 0; k < 3; k++) {
                    polilinha(b.xs, b.ys, b.xs.length, lw[k], col(cs[k], alphas[k]));
                }
                mancha(PCX, 140, 320, 0xFFEBBE, 0.55f * al);
                normal();
            }
        }

        @Override
        void mid(float T, float t) {
            if (T < lt2) {
                destrocos.clear();
                figuras.clear();
            }
            if (lt2 < 4.8f && T >= 4.8f) {
                for (int i = 0; i < 14; i++) {
                    destrocos.add(new float[]{PCX + (R.nextFloat() - 0.5f) * 80, 140, (R.nextFloat() - 0.5f) * 5,
                            -(2 + R.nextFloat() * 5), 0, (R.nextFloat() - 0.5f) * 0.3f, 5 + R.nextFloat() * 9});
                }
            }
            if (lt2 < 5.2f && T >= 5.2f) {
                for (int d : new int[]{-1, 1}) {
                    figuras.add(new float[]{PCX + d * 34, 250, d * 1.6f, -1, 0, d * 0.12f});
                }
            }
            lt2 = T;
            for (float[] d : destrocos) {
                d[3] += 0.18f;
                d[0] += d[2];
                d[1] += d[3];
                d[4] += d[5];
                save();
                translate(d[0], d[1]);
                rotate(d[4]);
                retangulo(-d[6] / 2, -d[6] / 2, d[6], d[6], col(0x1A0D10, 1f));
                contornoRet(-d[6] / 2, -d[6] / 2, d[6], d[6], 1f, col(0xFF8C32, 0.7f));
                restore();
            }
            destrocos.removeIf(d -> d[1] > PH + 30);
            for (float[] f : figuras) {
                f[3] += 0.14f;
                f[0] += f[2];
                f[1] += f[3];
                f[4] += f[5];
                save();
                translate(f[0], f[1]);
                rotate(f[4]);
                disco(0, -8, 3.2f, 0x0B0508, 1f);
                linha(0, -5, 0, 5, 2f, col(0x0B0508, 1f));
                linha(0, -2, -6, -8, 2f, col(0x0B0508, 1f));
                linha(0, -2, 6, -6, 2f, col(0x0B0508, 1f));
                linha(0, 5, -4, 13, 2f, col(0x0B0508, 1f));
                linha(0, 5, 5, 12, 2f, col(0x0B0508, 1f));
                restore();
            }
            figuras.removeIf(f -> f[1] > PH + 30);
        }

        @Override
        void front(float T, float t) {
            if (T > 1 && R.nextFloat() < 0.9f && brasas.size() < 160) {
                brasas.add(new float[]{PCX + (R.nextFloat() - 0.5f) * 380, 480, (R.nextFloat() - 0.5f) * 0.6f,
                        -(0.6f + R.nextFloat() * 1.4f), 0, 80 + R.nextFloat() * 90, 1 + R.nextFloat() * 2});
            }
            aditivo();
            int i = 0;
            Iterator<float[]> it = brasas.iterator();
            while (it.hasNext()) {
                float[] p = it.next();
                i++;
                p[0] += p[2] + sin(t * 3 + p[1] * 0.05f) * 0.3f;
                p[1] += p[3];
                p[4]++;
                if (p[4] > p[5]) {
                    it.remove();
                    continue;
                }
                int g = 140 + (int) (rn(i, 4) * 80);
                retangulo(p[0], p[1], p[6], p[6], col(g << 16 | 0x3C00, sin((float) Math.PI * p[4] / p[5]) * 0.9f));
            }
            normal();
        }

        @Override
        void ring(float T, float t, float dp) {
            for (int k = 0; k < 12; k++) {
                save();
                rotate(k * 0.5236f + t * 0.1f);
                translate(0, -150);
                float h = 1 + 0.35f * sin(t * 8 + k * 2);
                int r = 120 + (int) (60 * sin(t * 5 + k));
                // a chama em gota
                float[] xs = {-4, -5, 0, 5, 4, 0};
                float[] ys = {4, -4, -13 * h, -4, 4, 4};
                poligono(xs, ys, 6, col(r << 16 | 0x2800, 0.85f * dp));
                restore();
            }
        }
    }
}
