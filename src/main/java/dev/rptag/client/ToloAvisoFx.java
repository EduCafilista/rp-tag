package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.cl;
import static dev.rptag.client.AvisoFxUtil.col;
import static dev.rptag.client.AvisoFxUtil.cos;
import static dev.rptag.client.AvisoFxUtil.rnd;
import static dev.rptag.client.AvisoFxUtil.sin;
import static dev.rptag.client.AvisoFxUtil.ss;
import static dev.rptag.client.CartaTarotFx.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * 0 · O TOLO (o Louco) — estilo 36 do {@code /rp admin avisar2}.
 *
 * <p>Portado do protótipo {@code FoolWarning} do dono: névoa cinza, o sigilo de 22 caminhos com
 * as Sequências 9…0 e o <b>relógio</b>, a <b>lua carmesim</b> subindo, os <b>olhos na névoa</b>, a
 * carta do Louco que sobe e vira, as cartas orbitando e a poeira dourada.
 *
 * <p>Não confundir com o estilo 25 ({@code louco} = o Caminho do Louco, a escada das Sequências):
 * este aqui é a <b>carta</b> do Tolo, que o dono pediu de volta.
 */
public final class ToloAvisoFx extends CartaTarotFx {

    private static final ToloAvisoFx INST = new ToloAvisoFx();
    private final TemaTolo t = new TemaTolo();

    private ToloAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        registrar(e, "tolo", INST);
    }

    public static void iniciar(String mensagem) {
        iniciar(INST, mensagem);
    }

    @Override
    protected Tema tema() {
        return t;
    }

    static final class TemaTolo extends CartaTarotFx.Tema {
        final List<float[]> poeira = new ArrayList<>();
        final List<float[]> olhos = new ArrayList<>();

        TemaTolo() {
            nome = "O LOUCO";
            romano = "0";
            ac = 0xD9B457;         // o ouro do protótipo
            fogc = 0xA8ACB8;       // névoa cinza
            glowc = 0xD9B457;
            flashc = 0xFFDC8C;
            shc = 0xC81E32;
            sky0 = 0x2B2D38;
            sky1 = 0x07080B;
            bk = 0x14161D;
            pc0 = 0xD8D0B6;
            pc1 = 0xA99D7C;
            ink = 0x3B2C12;
            tg0 = 0xFFF3C4;
            tg1 = 0xD9B457;
            tg2 = 0x8A6420;
            c1 = 0xB9B4A2;
            c2 = 0xF0EADC;
            l3c = 0xD03A3A;
            l1 = "Acima da névoa cinzenta, o Louco observa.";
            l3 = "Não pronuncie Seu nome.";
            palavras = new String[]{"quem é você?", "ele está aqui", "o Louco", "Sequência 0",
                    "não olhe para cima", "a névoa escuta", "o relógio não para", "Sefirá", "ele já sabe", "silêncio"};
        }

        @Override
        void reset() {
            poeira.clear();
            olhos.clear();
        }

        @Override
        void emblem() {
            // o olho no verso das cartas que orbitam
            contorno(0, 0, 4, 1.2f, col(ac, 1f));
            linha(0, -8, 0, -4, 1.2f, col(ac, 1f));
        }

        /** o verso da carta: moldura de ouro, os dois círculos, o olho vermelho. */
        @Override
        void back(float t) {
            contorno(0, 0, 34, 1f, col(ac, 0.7f));
            contorno(0, 0, 26, 1f, col(ac, 0.7f));
            for (int k = 0; k < 8; k++) {
                float a = k * 0.7854f + t * 0.3f;
                linha(cos(a) * 26, sin(a) * 26, cos(a) * 34, sin(a) * 34, 1f, col(ac, 0.7f));
            }
            // o olho (duas quadráticas fechadas)
            polilinha(new float[]{-20, -10, 0, 10, 20}, new float[]{0, -8, -16, -8, 0}, 5, 1f, col(ac, 0.85f));
            polilinha(new float[]{-20, -10, 0, 10, 20}, new float[]{0, 8, 16, 8, 0}, 5, 1f, col(ac, 0.85f));
            disco(0, 0, 6, 0xC01828, 1f);
            disco(0, 0, 2, 0x000000, 1f);
        }

        /** a arte da carta do Louco: o penhasco, a figura, a trouxa, o cão e a rosa. */
        @Override
        void art(float t) {
            degradeRect(-44, -52, 88, 106, 0x8E8F98, 0xC9C3AE);
            manchaE(11, -30, 30, 30, 0xBE1E1E, 0.5f);            // o clarão vermelho do alto
            disco(24, -30, 13, 0xB3261E, 1f);                    // o sol/lua carmesim da arte
            retangulo(-44, 30, 88, 24, col(0x2A2420, 1f));       // o chão
            // a figura de preto (de costas, olhando o alto)
            disco(0, -26, 5.5f, 0x0C0B0D, 1f);                   // a cabeça
            poligono(new float[]{-9, 9, 6, -6}, new float[]{-20, -20, -32, -32}, 4, col(0x0C0B0D, 1f)); // o chapéu
            poligono(new float[]{-8, 8, 11, -11}, new float[]{-19, -19, 14, 14}, 4, col(0x0C0B0D, 1f)); // o corpo
            linha(-6, -10, 16, -20, 3f, col(0x0C0B0D, 1f));      // o braço que segura a trouxa
            disco(18, -21, 5, 0x0C0B0D, 1f);                     // a trouxa
            linha(20, -18, 24, 30, 2f, col(0x0C0B0D, 1f));       // o cajado
            poligono(new float[]{-11, -6, -14, -9}, new float[]{14, 14, 28, 28}, 4, col(0x0C0B0D, 1f)); // as pernas
            poligono(new float[]{10, 16, 20, 14}, new float[]{28, 28, 34, 34}, 4, col(0x0C0B0D, 1f));   // o cão
        }

        /** a lua carmesim subindo e os olhos na névoa. */
        @Override
        void bg(float T, float t) {
            float mp = ss(4f, 6.2f, T);
            if (mp > 0.01f) {
                float mx = PCX + 215, my = 330 - 195 * mp;
                aditivo();
                mancha(mx, my, 230, 0xFF3228, 0.40f * mp);
                normal();
                alfa = mp;
                mancha(mx, my, 58, 0xC4202A, 1f);
                disco(mx, my, 58, 0xC4202A, 1f);
                disco(mx, my, 34, 0xFF7A66, 0.55f);
                // as crateras
                for (float[] c : new float[][]{{-18, -10, 10}, {14, 12, 13}, {-6, 24, 6}, {22, -18, 7}, {-26, 14, 5}}) {
                    disco(mx + c[0], my + c[1], c[2], 0x3C000A, 0.28f);
                }
                alfa = 1f;
            }
            // os olhos vermelhos na névoa
            float[][] P = {{120, 150}, {680, 200}, {90, 330}, {710, 380}, {250, 90}, {560, 70}};
            for (int i = 0; i < P.length; i++) {
                float o = ss(5.2f + i * 0.3f, 5.8f + i * 0.3f, T);
                if (o <= 0.01f) {
                    continue;
                }
                if (rnd((int) Math.floor(t * 1.5f) + i, 21) > 0.9f) {
                    o *= 0.1f;
                }
                for (float dx : new float[]{-14, 14}) {
                    float x = P[i][0] + dx, y = P[i][1];
                    aditivo();
                    manchaE(x, y, 16, 9 * o + 1, 0xFF2828, 0.18f * o);
                    normal();
                    // o olho: pálpebra de cima, pálpebra de baixo e a pupila
                    float h = 7 * o;
                    curva(x - 9, y, x, y - h * 1.7f, x + 9, y, 1.4f, col(0xDC2828, 0.75f * o));
                    curva(x - 9, y, x, y + h * 1.7f, x + 9, y, 1.4f, col(0xDC2828, 0.75f * o));
                    disco(x, y, Math.max(1f, 2.2f * o), 0x1A0000, 0.85f * o);
                }
            }
        }

        @Override
        void mid(float T, float t) {
        }

        /** a poeira dourada que sobe do anel. */
        @Override
        void front(float T, float t) {
            if (T > 2 && R.nextFloat() < 0.8f && poeira.size() < 140) {
                float a = R.nextFloat() * 6.2832f;
                poeira.add(new float[]{PCX + cos(a) * 200, PCY + sin(a) * 200,
                        (R.nextFloat() - 0.5f) * 0.3f, -(0.3f + R.nextFloat() * 0.5f),
                        80 + R.nextFloat() * 100, 1 + R.nextFloat() * 1.6f, 0});
            }
            aditivo();
            Iterator<float[]> it = poeira.iterator();
            while (it.hasNext()) {
                float[] d = it.next();
                d[0] += d[2] + sin(t * 2 + d[1] * 0.05f) * 0.15f;
                d[1] += d[3];
                d[6]++;
                if (d[6] > d[4]) {
                    it.remove();
                    continue;
                }
                retangulo(d[0], d[1], d[5], d[5], col(0xF0CD78, sin((float) Math.PI * d[6] / d[4]) * 0.7f));
            }
            normal();
        }

        /** o relógio: 60 traços, o ponteiro que dá um pulo por segundo e a marca vermelha. */
        @Override
        void ring(float T, float t, float dp) {
            for (int k = 0; k < 60; k++) {
                float a = k / 60f * 6.2832f;
                float L = k % 5 == 0 ? 10 : 5;
                linha(cos(a) * (150 - L), sin(a) * (150 - L), cos(a) * 150, sin(a) * 150, 1f, col(ac, 0.7f * dp));
            }
            int s = (int) Math.floor(t);
            float fr = t - s;
            float ang = (s + ss(0f, 1f, fr * 8)) * (float) Math.PI / 6f - 1.5708f;
            linha(0, 0, cos(ang) * 146, sin(ang) * 146, 2f, col(0xF0CD78, dp));
            disco(cos(ang) * 146, sin(ang) * 146, 4, 0xD22832, dp);
            if (fr < 0.33f) {
                float rr = 150 + fr * 60;
                anel(0, 0, rr, 2f, col(0xFF503C, (1 - fr * 3) * 0.4f * dp), 48);
            }
        }
    }
}
