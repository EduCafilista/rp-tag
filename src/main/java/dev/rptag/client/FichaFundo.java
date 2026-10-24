package dev.rptag.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * (4.8.0) O FUNDO DO TABLET: galáxia + buraco negro de Netuno.
 *
 * <p>Mesma pegada do fundo do balão do RP Tag (céu profundo), só que mais
 * espacial: estrelas em três profundidades, poeira de nebulosa, e atrás do
 * personagem um disco de acreção laranja girando em volta de um buraco negro
 * azul-turquesa (Netuno).
 *
 * <p>Tudo procedural e em quads — nada de textura, por isso é leve (o custo é
 * o mesmo de desenhar ~180 estrelinhas por quadro).
 */
final class FichaFundo {

    private static final int NEBULOSA_A = 0x3A1E5C;
    private static final int NEBULOSA_B = 0x123A52;
    private static final int POEIRA = 0x8E6BD0;

    private FichaFundo() {
    }

    /** pintura de fundo inteira. */
    static void fundo(GuiGraphics g, int w, int h, float t) {
        // céu: do preto-azulado do topo ao roxo do horizonte
        g.fillGradient(0, 0, w, h, 0xFF07040F, 0xFF150A24);
        g.fillGradient(0, (int) (h * 0.45f), w, h, 0x00150A24, 0x40100918);

        // nebulosas: duas manchas grandes que respiram devagar
        nebulosa(g, w * 0.22f + Mth.sin(t * 0.11f) * w * 0.03f, h * 0.30f, w * 0.42f, NEBULOSA_A, 0.20f);
        nebulosa(g, w * 0.78f + Mth.cos(t * 0.09f) * w * 0.03f, h * 0.62f, w * 0.38f, NEBULOSA_B, 0.17f);

        estrelas(g, w, h, t, 0.35f, 0.5f, 0.30f);
        estrelas(g, w, h, t, 0.6f, 1.0f, 0.22f);
    }

    /** o buraco negro de Netuno, atrás do personagem. */
    static void buracoNegro(GuiGraphics g, float cx, float cy, float raio, float t) {
        // halo externo (o "calor" do disco)
        for (int i = 4; i >= 1; i--) {
            float r = raio * (1.35f + i * 0.32f);
            disco(g, cx, cy, r, t + i * 0.7f, 0.055f * (5 - i));
        }
        // disco de acreção: muitos arcos curtos girando em velocidade diferente
        disco(g, cx, cy, raio * 1.28f, t, 0.30f);
        disco(g, cx, cy, raio * 1.10f, t * 1.35f, 0.26f);

        // o buraco: esfera escura com borda azul (o evento de horizonte)
        int passos = 22;
        for (int i = passos; i >= 1; i--) {
            float f = i / (float) passos;
            int a = (int) (0.9f * 255 * (1 - f) + 20);
            g.fill((int) (cx - raio * f), (int) (cy - raio * f),
                    (int) (cx + raio * f), (int) (cy + raio * f),
                    (a << 24) | 0x05030A);
        }
        // anel fino azul-turquesa (a luz dobrada na borda)
        for (int i = 0; i < 3; i++) {
            float r = raio * (1.0f + i * 0.012f);
            int cor = i == 0 ? 0x66D8FF : (i == 1 ? 0x3FA8E0 : 0x1F5C9C);
            arco(g, cx, cy, r, r, (t * 40 + i * 30) % 360, 300, cor, 0.32f - i * 0.08f);
        }
        // faíscas caindo no buraco
        for (int i = 0; i < 26; i++) {
            double an = i * 2.399963 + t * (0.35 + (i % 5) * 0.07);
            float d = raio * (1.15f + 0.55f * frac(i * 0.137f + t * 0.05f));
            float x = cx + Mth.cos((float) an) * d;
            float y = cy + Mth.sin((float) an) * d * 0.55f;
            float a = 0.25f + 0.35f * Mth.sin(t * 2 + i);
            quad(g, x, y, 1.4f, corComAlfa(0xFFD9A0, Math.max(0, a)));
        }
    }


    /** (4.8.0) o disco mágico no chão, embaixo do personagem. */
    static void discoMagico(GuiGraphics g, float cx, float cy, float raio, float t) {
        // elipses concêntricas que respiram
        for (int i = 0; i < 5; i++) {
            float fase = t * (0.55f + i * 0.12f) + i * 1.7f;
            float r = raio * (0.55f + i * 0.115f) * (1f + 0.02f * Mth.sin(fase));
            int alfa = (int) (70 - i * 9 + 18 * Mth.sin(fase));
            arco(g, cx, cy, r, r * 0.26f, (t * 22f + i * 47f) % 360f, 360f,
                    i % 2 == 0 ? 0x7FD8FF : 0xB98CFF, Math.max(0.05f, alfa / 255f));
        }
        // pontinhos parados (a base do círculo)
        for (int i = 0; i < 28; i++) {
            double an = i * (Math.PI * 2 / 28);
            float x = cx + Mth.cos((float) an) * raio * 0.72f;
            float y = cy + Mth.sin((float) an) * raio * 0.72f * 0.26f;
            quad(g, x, y, 0.8f, corComAlfa(0xCFE9FF, 0.25f + 0.2f * Mth.sin(t * 2 + i)));
        }
        // brilho central
        for (int i = 3; i >= 1; i--) {
            float r = raio * 0.30f * (i / 3f);
            arco(g, cx, cy, r, r * 0.26f, 0, 360, 0x9FE0FF, 0.10f);
        }
    }

    // ------------------------------------------------------------- pedaços
    private static void nebulosa(GuiGraphics g, float cx, float cy, float r, int cor, float alfa) {
        for (int i = 6; i >= 1; i--) {
            float rr = r * (i / 6f);
            g.fill((int) (cx - rr), (int) (cy - rr * 0.62f), (int) (cx + rr), (int) (cy + rr * 0.62f),
                    corComAlfa(cor, alfa * 0.10f * (7 - i)));
        }
    }

    private static void estrelas(GuiGraphics g, int w, int h, float t, float vel, float tam, float alfa) {
        for (int i = 0; i < 64; i++) {
            float x = (rnd(i, 1) * w + t * vel * 6f) % (w + 8) - 4;
            float y = rnd(i, 2) * h;
            float tw = 0.55f + 0.45f * Mth.sin(t * (0.8f + rnd(i, 3)) + i);
            int cor = i % 7 == 0 ? 0xFFD9A0 : (i % 5 == 0 ? 0x9FD8FF : 0xFFFFFF);
            quad(g, x, y, tam * (0.7f + rnd(i, 4)), corComAlfa(cor, alfa * tw));
        }
    }

    private static void disco(GuiGraphics g, float cx, float cy, float r, float t, float alfa) {
        int n = 40;
        for (int i = 0; i < n; i++) {
            float a0 = (i * 360f / n + t * 26f) % 360f;
            float larg = 360f / n * 0.85f;
            int cor = i % 3 == 0 ? 0xFF7A2A : (i % 3 == 1 ? 0xFFB44A : 0xFFE9A8);
            arco(g, cx, cy, r, r * 0.34f, a0, larg, cor, alfa * (0.5f + 0.5f * Mth.sin(i + t * 3)));
        }
    }

    /** arco feito de quads (elipse achatada — o disco visto de lado). */
    private static void arco(GuiGraphics g, float cx, float cy, float rx, float ry,
                             float inicioGraus, float aberturaGraus, int cor, float alfa) {
        int passos = Math.max(4, (int) (aberturaGraus / 6f));
        for (int i = 0; i < passos; i++) {
            float a = (float) Math.toRadians(inicioGraus + aberturaGraus * i / passos);
            float x = cx + Mth.cos(a) * rx;
            float y = cy + Mth.sin(a) * ry;
            quad(g, x, y, 1.6f, corComAlfa(cor, alfa));
        }
    }

    private static void quad(GuiGraphics g, float x, float y, float s, int cor) {
        g.fill((int) (x - s), (int) (y - s), (int) (x + s), (int) (y + s), cor);
    }

    static int corComAlfa(int rgb, float a) {
        int alfa = (int) (Mth.clamp(a, 0f, 1f) * 255) << 24;
        return alfa | (rgb & 0xFFFFFF);
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private static float rnd(int i, int s) {
        return frac((float) (Math.sin(i * 12.9898 + s * 78.233) * 43758.547));
    }
}
