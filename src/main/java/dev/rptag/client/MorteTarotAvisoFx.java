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
 * XIII · A MORTE — estilo 32 do {@code /rp admin avisar} (o dono pediu um nome; este é o da carta).
 *
 * <p>Portado do protótipo do dono ({@code MorteWarning}): o sol pálido nascendo entre as torres, a
 * bandeira negra tremulando, a caveira, a foice, a lâmina de luz atravessando a tela de tempos em
 * tempos e as pétalas caindo (algumas vermelhas).
 */
public final class MorteTarotAvisoFx extends CartaTarotFx {

    private static final MorteTarotAvisoFx INST = new MorteTarotAvisoFx();
    private final Tema t = new TemaMorte();

    private MorteTarotAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        registrar(e, "morte_tarot", INST);
    }

    public static void iniciar(String mensagem) {
        iniciar(INST, mensagem);
    }

    @Override
    protected Tema tema() {
        return t;
    }

    static final class TemaMorte extends CartaTarotFx.Tema {
        final List<float[]> petalas = new ArrayList<>();

        TemaMorte() {
            nome = "A MORTE";
            romano = "XIII";
            ac = 0xD7D7E4;
            fogc = 0x78788A;
            sky0 = 0x030304;
            sky1 = 0x15151C;
            glowc = 0xC8C8E1;
            flashc = 0xFFFFFF;
            shc = 0xE6E6F5;
            tg0 = 0xFFFFFF;
            tg1 = 0xD8D8E4;
            tg2 = 0x6A6A80;
            bk = 0x0A0A0E;
            pc0 = 0xDDD9D0;
            pc1 = 0x9D9A90;
            ink = 0x1A1A20;
            l1 = "Nada escapa do fim: tudo o que vive um dia se despede.";
            l3 = "A sua vez chegou.";
            l3c = 0xE03A3A;
            c1 = 0xB4B4C4;
            c2 = 0xF4F4FA;
            palavras = new String[]{"fim", "cinzas", "renascer", "a foice", "tudo passa", "silêncio eterno",
                    "poeira", "a última página"};
        }

        @Override
        void reset() {
            petalas.clear();
        }

        @Override
        void emblem() {
            contorno(0, 0, 3, 1f, col(ac, 1f));
            for (int k = 0; k < 5; k++) {
                float a = k * 1.2566f;
                contorno(cos(a) * 5, sin(a) * 5, 2.4f, 1f, col(ac, 1f));
            }
        }

        @Override
        void back(float t) {
            for (int k = 0; k < 5; k++) {
                float a = k * 1.2566f - 1.57f + t * 0.1f;
                contorno(cos(a) * 13, sin(a) * 13, 11, 1.4f, col(0xE8E8F0, 1f));
            }
            contorno(0, 0, 6, 1.4f, col(0xE8E8F0, 1f));
            linha(0, 24, 0, 62, 1.4f, col(0xE8E8F0, 1f));
            curva(0, 42, 14, 36, 18, 28, 1.4f, col(0xE8E8F0, 1f));
        }

        @Override
        void art(float t) {
            degradeRect(-44, -52, 88, 106, 0x6A6A78, 0xE8E0D0);
            setor(-16, 20, 32, 32, 0, 180, 0xF0E6A0, 1f);
            retangulo(-40, 6, 9, 34, col(0x2A2A34, 1f));
            retangulo(31, 6, 9, 34, col(0x2A2A34, 1f));
            elipse(0, 20, 17, 8, 0xF4F2EA, 1f);
            retangulo(-14, 24, 3, 14, col(0xF4F2EA, 1f));
            retangulo(-6, 24, 3, 14, col(0xF4F2EA, 1f));
            retangulo(4, 24, 3, 14, col(0xF4F2EA, 1f));
            retangulo(12, 24, 3, 14, col(0xF4F2EA, 1f));
            poligono(new float[]{10, 20, 26, 16}, new float[]{16, 2, 6, 20}, 4, col(0xF4F2EA, 1f));
            retangulo(-4, -12, 8, 24, col(0x14141A, 1f));
            poligono(new float[]{-4, 0, 4}, new float[]{-12, -20, -12}, 3, col(0x14141A, 1f));
            disco(0, -20, 4.5f, 0xF4F2EA, 1f);
            retangulo(-2, -21, 1.4f, 1.6f, col(0x14141A, 1f));
            retangulo(1, -21, 1.4f, 1.6f, col(0x14141A, 1f));
            linha(-12, -40, -10, 30, 1.5f, col(0x14141A, 1f));
            // a bandeira negra tremulando
            float[] bx = new float[26], by = new float[26];
            int p = 0;
            for (int i = 0; i <= 12; i++) {
                float u = i / 12f, v = 1 - u;
                bx[p] = v * v * -12 + 2 * v * u * -4 + u * u * 6;
                by[p] = v * v * -40 + 2 * v * u * (-36 + sin(t * 3) * 2) + u * u * -40;
                p++;
            }
            for (int i = 0; i <= 12; i++) {
                float u = 1 - i / 12f, v = 1 - u;
                bx[p] = v * v * 6 + 2 * v * u * -4 + u * u * -12;
                by[p] = v * v * -18 + 2 * v * u * -22 + u * u * -18;
                p++;
            }
            poligono(bx, by, p, col(0x14141A, 1f));
            disco(-3, -29, 3, 0xF4F2EA, 1f);
        }

        @Override
        void bg(float T, float t) {
            float a = ss(0.5f, 2f, T);
            mancha(PCX, 440, 260, 0xF0EBD7, 0.5f * a);
            // o sol pálido nascendo
            setor(PCX - 66, 452 - 6 * sin(t * 0.4f) - 66, 132, 132, 0, 180, 0xECE6CE, 0.85f * a);
            // as duas torres
            for (int x : new int[]{120, 680}) {
                retangulo(x - 28, 300, 56, 170, col(0x050507, 1f));
                for (int k = 0; k < 4; k++) {
                    retangulo(x - 28 + k * 16, 288, 10, 14, col(0x050507, 1f));
                }
            }
            retangulo(0, 452, PW, PH - 452, col(0x050507, 1f));
            // o mastro e a bandeira
            float px = 70;
            retangulo(px - 2, 70, 4, 392, col(0x050507, 1f));
            int m = 16;
            float[] xs = new float[m * 2], ys = new float[m * 2];
            for (int i = 0; i < m; i++) {
                float x = i * 6;
                xs[i] = px + x;
                ys[i] = 80 + sin(t * 3 + x * 0.08f) * 5 * (x / 90);
            }
            for (int i = 0; i < m; i++) {
                float x = 90 - i * 6;
                xs[m + i] = px + x;
                ys[m + i] = 150 + sin(t * 3 + x * 0.08f) * 5 * (x / 90);
            }
            poligono(xs, ys, m * 2, col(0x050507, a));
            for (int i = 0; i < m * 2; i++) {
                linha(xs[i], ys[i], xs[(i + 1) % (m * 2)], ys[(i + 1) % (m * 2)], 1f, col(0xC8C8D7, 0.4f));
            }
            // o emblema da morte na bandeira
            float ry = 115 + sin(t * 3 + 3.6f) * 2.5f;
            for (int k = 0; k < 5; k++) {
                float aa = k * 1.2566f;
                contorno(px + 45 + cos(aa) * 5, ry + sin(aa) * 5, 4, 1e-3f, col(0xE8E8F0, 1f));
            }
        }

        /** a lâmina de luz que atravessa a tela. */
        private static float[] bez(float u) {
            return new float[]{(1 - u) * (1 - u) * 60 + 2 * u * (1 - u) * 400 + u * u * 740,
                    (1 - u) * (1 - u) * 430 + 2 * u * (1 - u) * -40 + u * u * 430};
        }

        @Override
        void mid(float T, float t) {
            if (T < 2.5f) {
                return;
            }
            float ph = (t % 3.5f) / 0.55f;
            if (ph > 1.25f) {
                return;
            }
            float p = Math.min(1, ph), fade = ph > 1 ? Math.max(0, 1 - (ph - 1) * 4) : 1;
            aditivo();
            for (int k = 1; k <= 30; k++) {
                float[] a = bez((k - 1) / 30f * p), b = bez(k / 30f * p);
                linha(a[0], a[1], b[0], b[1], 1 + k / 30f * 6, col(0xF0F0FF, k / 30f * 0.55f * fade));
            }
            normal();
        }

        @Override
        void front(float T, float t) {
            if (T > 2 && R.nextFloat() < 0.3f && petalas.size() < 70) {
                petalas.add(new float[]{R.nextFloat() * PW, -10, 0.5f + R.nextFloat() * 0.7f, R.nextFloat() * 6.28f,
                        R.nextFloat() * 6.28f, 3 + R.nextFloat() * 3, R.nextFloat() < 0.12f ? 1 : 0});
            }
            Iterator<float[]> it = petalas.iterator();
            while (it.hasNext()) {
                float[] p = it.next();
                p[1] += p[2];
                p[0] += sin(t * 1.1f + p[3]) * 0.7f;
                p[4] += 0.03f;
                if (p[1] > PH + 10) {
                    it.remove();
                    continue;
                }
                float s = p[5];
                save();
                translate(p[0], p[1]);
                rotate(p[4]);
                scale(1, Math.max(0.05f, 0.5f + 0.5f * sin(t * 2 + p[3])));
                alfa = 0.85f;
                int cor = p[6] > 0 ? 0xC02A2A : 0xE8E8F0;
                folha(0, -s, s * 0.9f, -s * 0.3f, 0, s * 1.2f, -s * 0.9f, -s * 0.3f, col(cor, 1f));
                alfa = 1f;
                restore();
            }
            // a poeira
            for (int i = 0; i < 40; i++) {
                float x = (rn(i, 1) * PW + sin(t * 0.5f + i) * 20) % PW;
                float y = (rn(i + 5, 2) * PH + t * (20 + rn(i + 3, 3) * 30)) % PH;
                retangulo(x, y, 1.5f, 1.5f, col(0xAAAAAA, 0.4f));
            }
        }

        @Override
        void ring(float T, float t, float dp) {
            int act = (int) Math.floor(t * 2) % 13;
            for (int k = 0; k < 13; k++) {
                save();
                rotate(k / 13f * 6.2832f);
                translate(0, -150);
                int cor = k == act ? col(0xE03A3A, dp) : col(0xD7D7E4, 0.7f * dp);
                linha(-4, -4, 4, 4, 1.4f, cor);
                linha(4, -4, -4, 4, 1.4f, cor);
                restore();
            }
        }
    }
}
