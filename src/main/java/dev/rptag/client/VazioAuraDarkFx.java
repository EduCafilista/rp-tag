package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;
import dev.rptag.RPTagMod;

/**
 * AURA DO VAZIO (deus) — 5.7.1.
 *
 * <p>Porte do protótipo do dono ({@code xzxRmnve.java}, classe
 * {@code VoidAuraDark}): a aura NEGRA que fica ligada enquanto o deus está no
 * modo vazio ({@code /vazio ligar}). Rachaduras vermelho-sangue saindo do chão,
 * tentáculos (halo vermelho + núcleo preto) que tremem em passos, estilhaços
 * negros orbitando, fumaça preta atrás e na frente, cinza subindo, névoa clara
 * atrás da silhueta, piscadas (a tela escurece por um instante) e a granulação
 * de filme velho.
 *
 * <p>Diferença proposital em relação ao protótipo: lá ele desenhava um
 * bonequinho de pixel no meio da tela (era a prévia); NO JOGO o personagem é o
 * de verdade, então o boneco e os olhos vermelhos de pixel não são desenhados —
 * o resto (o que dá o clima) é idêntico. O fundo também não é 100% opaco: fica
 * semi-transparente pra você continuar vendo o mundo.
 *
 * <p>Regra de ouro do mod: qualquer erro DESLIGA a aura, nunca o jogo.
 */
public final class VazioAuraDarkFx {

    private VazioAuraDarkFx() {
    }

    private static final float DW = 960f, DH = 640f;

    private static final int N_FUMACA = 70;
    private static final int N_CINZA = 60;

    /** fumaça (0 = atrás do player, 1 = na frente) e cinza (2). */
    private static final class P {
        float x, y, vx, vy, idade, max, tam;
        int tipo;
    }

    private static final P[] PS = new P[N_FUMACA + N_CINZA];
    private static final Random SORTE = new Random();

    private static boolean ligado;
    private static long ultimo = 0L;
    private static float t;

    static {
        for (int i = 0; i < PS.length; i++) {
            PS[i] = new P();
            PS[i].tipo = i < N_FUMACA ? (SORTE.nextFloat() < 0.55f ? 0 : 1) : 2;
            nascer(PS[i], SORTE.nextFloat() * 200f);
        }
    }

    public static void ligar() {
        ligado = true;
        ultimo = System.nanoTime();
        t = 0f;
    }

    public static void desligar() {
        ligado = false;
    }

    public static boolean ligado() {
        return ligado;
    }

    private static void nascer(P p, float atraso) {
        if (p.tipo == 2) {
            p.x = 480f + (SORTE.nextFloat() - 0.5f) * 320f;
            p.y = 570f - SORTE.nextFloat() * 80f;
            p.vx = (SORTE.nextFloat() - 0.5f) * 0.3f;
            p.vy = -(0.2f + SORTE.nextFloat() * 0.5f);
            p.tam = 1f + SORTE.nextFloat() * 1.5f;
            p.max = 160f + SORTE.nextFloat() * 120f;
        } else {
            p.x = 480f + (SORTE.nextFloat() - 0.5f) * 120f;
            p.y = 470f - SORTE.nextFloat() * 210f;
            p.vx = (SORTE.nextFloat() - 0.5f) * 0.4f + (p.x - 480f) * 0.004f;
            p.vy = -(0.2f + SORTE.nextFloat() * 0.6f);
            p.tam = 25f + SORTE.nextFloat() * 30f;
            p.max = 120f + SORTE.nextFloat() * 80f;
        }
        p.idade = -atraso;
    }

    private static double n(double x) {
        double v = Math.sin(x * 12.9898) * 43758.5453;
        return v - Math.floor(v);
    }

    // ------------------------------------------------------------------ render
    public static void desenhar(GuiGraphics g) {
        if (!ligado) {
            return;
        }
        try {
            long agora = System.nanoTime();
            float dt = Math.min(0.1f, (agora - ultimo) / 1e9f);
            ultimo = agora;
            t += dt;
            atualizar(dt);

            int w = g.guiWidth();
            int h = g.guiHeight();
            float s = Math.min(w / DW, h / DH);
            float cx = w / 2f;
            float pe = h * 0.88f;                 // "pés" (chão da tela)
            float corpo = h * 0.60f;              // altura do corpo/aura

            preparar(g);
            Buf b = new Buf(g.pose().last().pose());

            // ---------------- escurecer (NÃO opaco: o mundo continua aparecendo)
            b.rect(0, 0, w, h, col(0x0A0A0C, 0.42f));
            b.brilho(cx, corpo, Math.max(w, h) * 0.55f, 0x2A2230, 0.10f);
            // névoa clara atrás pra silhueta escura aparecer
            b.brilho(cx, corpo, 300f * s, 0x50424E, 0.12f);
            b.rect(0, pe + 22f * s, w, h - pe, col(0x070706, 0.35f));

            // ---------------- rachaduras saindo do chão
            float brilhoR = 0.22f + 0.10f * sin(t * 0.8f);
            for (int i = 0; i < 10; i++) {
                float a = i / 10f * TAU + (float) n(i) * 0.5f;
                float len = (120f + (float) n(i + 9) * 140f) * s;
                float px = cx, py = pe + 8f * s;
                for (int k = 1; k <= 8; k++) {
                    float f = k / 8f;
                    float j = ((float) n(i * 13 + k) - 0.5f) * 40f * s;
                    float nx = cx + cos(a) * len * f - sin(a) * j * f;
                    float ny = pe + 8f * s + (sin(a) * len * f + cos(a) * j * f) * 0.25f;
                    b.seg(px, py, nx, ny, Math.max(1.5f, 6f * s), col(0xC01010, brilhoR * 0.55f),
                            col(0xC01010, brilhoR * 0.55f));
                    b.seg(px, py, nx, ny, Math.max(1f, 1.6f * s), col(0x5A0505, 0.85f), col(0x5A0505, 0.85f));
                    px = nx;
                    py = ny;
                }
            }

            // ---------------- fumaça de trás
            fumaca(b, 0, cx, corpo, s);

            // ---------------- estilhaços orbitando
            for (int i = 0; i < 14; i++) {
                float r0 = (90f + (float) n(i) * 150f) * s;
                float dir = n(i + 3) < 0.5 ? -1f : 1f;
                float ang = (float) n(i + 7) * TAU + t * 0.05f * dir;
                float x = cx + cos(ang) * r0;
                float y = corpo + sin(ang) * r0 * 0.7f + sin(t * 0.4f + i) * 8f * s;
                float sz = (6f + (float) n(i + 21) * 12f) * s;
                float rot = (float) n(i + 30) * 6f + t * 0.1f * dir;
                float x1 = x + cos(rot) * sz * 1.8f, y1 = y + sin(rot) * sz * 1.8f;
                float x2 = x + cos(rot + 2.1f + 0.5f) * sz, y2 = y + sin(rot + 2.1f + 0.5f) * sz;
                float x3 = x + cos(rot + 4.2f) * sz, y3 = y + sin(rot + 4.2f) * sz;
                b.tri(x1, y1, col(0x030203, 0.85f), x2, y2, col(0x030203, 0.85f), x3, y3, col(0x030203, 0.85f));
                int c = col(0x6E000A, 0.55f);
                b.seg(x1, y1, x2, y2, Math.max(1f, 1f * s), c, c);
                b.seg(x2, y2, x3, y3, Math.max(1f, 1f * s), c, c);
                b.seg(x3, y3, x1, y1, Math.max(1f, 1f * s), c, c);
            }

            // ---------------- tentáculos (halo vermelho + núcleo preto)
            int passo = (int) Math.floor(t * 3f);          // tremor em passos
            for (int passe = 0; passe < 2; passe++) {
                for (int i = 0; i < 12; i++) {
                    float a = i / 12f * TAU + ((float) n(i) - 0.5f) * 0.5f;
                    float sx = cx + cos(a) * 25f * s, sy = corpo + sin(a) * 45f * s;
                    float len = (120f + (float) n(i + 50) * 90f) * (1f + 0.08f * sin(t * 0.8f + i)) * s;
                    float px = sx, py = sy;
                    for (int k = 1; k <= 7; k++) {
                        float f = k / 7f;
                        float j = ((float) n(i * 17 + k + passo * 31) - 0.5f) * 60f * f * s;
                        float nx = sx + cos(a) * len * f - sin(a) * j;
                        float ny = sy + sin(a) * len * f * 0.9f - f * 30f * s + cos(a) * j;
                        float ww = ((1f - f) * 7f + 1f) * s;
                        if (passe == 0) {
                            b.seg(px, py, nx, ny, ww + 4f * s, col(0x82000A, 0.45f), col(0x82000A, 0.45f));
                        } else {
                            b.seg(px, py, nx, ny, ww, col(0x010001, 0.92f), col(0x010001, 0.92f));
                        }
                        px = nx;
                        py = ny;
                    }
                }
            }

            // ---------------- fumaça da frente + cinza
            fumaca(b, 1, cx, corpo, s);
            for (P p : PS) {
                if (p.tipo != 2 || p.idade < 0f) {
                    continue;
                }
                float a = sin((float) Math.PI * cl(p.idade / p.max)) * 0.5f;
                float sz = Math.max(1f, p.tam * s);
                b.rect(cx + (p.x - 480f) * s, corpo + (p.y - 470f) * s, sz, sz, col(0x9A9AA2, a));
            }

            // ---------------- vinheta forte (o preto que engole as bordas)
            b.vinheta(w, h, Math.max(90f, 190f * s), 0x000000, 0.80f);

            // piscada de raiva: a tela escurece por um instante
            boolean pisca = n(Math.floor(t * 10f)) > 0.93;
            if (pisca) {
                b.rect(0, 0, w, h, col(0x000000, 0.20f));
            }

            // granulação de filme velho (determinística: não cintila aleatório)
            int q = (int) (t * 12f);
            for (int i = 0; i < 120; i++) {
                float gx = (float) n(i * 3.1 + q * 1.7) * w;
                float gy = (float) n(i * 7.3 + q * 2.3) * h;
                b.rect(gx, gy, 1f, 1f, col(0xFFFFFF, 0.07f));
            }

            b.flush();
            normal();
        } catch (Throwable erro) {
            ligado = false;
            RPTagMod.LOGGER.warn("RP Tag: aura do vazio desligada", erro);
        }
    }

    private static void fumaca(Buf b, int tipo, float cx, float corpo, float s) {
        for (P p : PS) {
            if (p.tipo != tipo || p.idade < 0f) {
                continue;
            }
            float a = sin((float) Math.PI * cl(p.idade / p.max)) * 0.85f;
            float x = cx + (p.x - 480f) * s;
            float y = corpo + (p.y - 470f) * s;
            b.brilho(x, y, Math.max(2f, p.tam * s), 0x020002, a);
        }
    }

    private static void atualizar(float dt) {
        for (P p : PS) {
            if (p.idade < 0f) {
                p.idade += dt * 60f;         // "atraso" conta em frames (como no protótipo)
                continue;
            }
            p.x += p.vx + sin(t * 0.8f + p.y * 0.02f) * 0.15f;
            p.y += p.vy;
            p.idade += dt * 60f;
            if (p.tipo != 2) {
                p.tam += 0.2f;
            }
            if (p.idade >= p.max) {
                nascer(p, 0f);
            }
        }
    }
}
