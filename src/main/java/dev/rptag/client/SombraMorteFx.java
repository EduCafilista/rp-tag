package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import java.util.Random;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * SOMBRA DA MORTE — estilo 19 (5.7.2).
 *
 * <p>Pedido do dono: "faça um efeito de escuridão saindo do corpo de quem morrer,
 * assim ficando uma sombra bizarra de morte, escuridão e desaparecimento."
 *
 * <p>Quando alguém morre, a escuridão sobe do corpo (embaixo da tela, onde está o
 * cadáver): tentáculos pretos se erguem, poeira de cinza sobe, um halo vermelho
 * fraco pulsa no centro, a tela escurece quase por completo — e depois a sombra
 * se recolhe e some, deixando a tela limpa (nada de ficar preto).
 *
 * <pre>
 *   0,0   a escuridão brota do chão e os tentáculos sobem
 *   0,4   a tela escurece (pico em ~1,2 s)
 *   2,2   começa o "desaparecimento": a sombra se retira
 *   4,0   fim — a tela volta ao normal
 * </pre>
 *
 * <p>Regra de ouro do mod: qualquer erro DESLIGA o efeito, nunca o jogo.
 */
public final class SombraMorteFx {

    private SombraMorteFx() {
    }

    private static final float DUR = 4.2f;

    private static final int N_TENT = 11;
    private static final int N_CINZA = 60;

    private static long inicio = -1L;
    private static float tAnt;
    private static final Random SORTE = new Random();

    private static final float[] CINZA_X = new float[N_CINZA];
    private static final float[] CINZA_Y = new float[N_CINZA];
    private static final float[] CINZA_V = new float[N_CINZA];
    private static final float[] CINZA_F = new float[N_CINZA];

    // ------------------------------------------------------------- ciclo de vida
    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "sombra_morte"),
                SombraMorteFx::render);
    }

    public static void iniciar() {
        inicio = System.nanoTime();
        tAnt = 0f;
        for (int i = 0; i < N_CINZA; i++) {
            CINZA_X[i] = SORTE.nextFloat();
            CINZA_Y[i] = 0.55f + SORTE.nextFloat() * 0.5f;
            CINZA_V[i] = 0.04f + SORTE.nextFloat() * 0.10f;
            CINZA_F[i] = SORTE.nextFloat() * 6.28f;
        }
    }

    public static void parar() {
        inicio = -1L;
    }

    public static boolean ativo() {
        return inicio >= 0;
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0L) {
            return;
        }
        try {
            float t = (System.nanoTime() - inicio) / 1e9f;
            if (t >= DUR) {
                inicio = -1L;
                return;
            }
            float passo = Math.max(0f, Math.min(0.12f, t - tAnt));
            tAnt = t;

            int w = g.guiWidth();
            int h = g.guiHeight();
            float s = Math.min(w / 960f, h / 640f);
            float cx = w / 2f;
            float chao = h * 0.94f;

            // envelope: entra rápido (0,3 s), segura até 2,2 s, sai até 4,0 s
            float entra = ss(0.0f, 0.35f, t);
            float sai = 1f - ss(2.2f, DUR - 0.2f, t);
            float env = entra * sai;
            float escuro = ss(0.15f, 1.15f, t) * sai;      // pico do preto

            preparar(g);
            Buf b = new Buf(g.pose().last().pose());

            // ---------------- a escuridão que engole a tela (vinheta fechando)
            float raio = h * (1.35f - 0.55f * escuro);
            b.brilho(cx, chao, raio, 0x050106, 0.55f * escuro);
            b.rect(0, 0, w, h, col(0x020103, 0.42f * escuro));
            b.vinheta(w, h, Math.max(60f, 300f * s * escuro), 0x000000, 0.85f * escuro);

            // ---------------- halo vermelho fraco no peito do cadáver
            float pulso = 0.55f + 0.45f * sin(t * 5.5f);
            b.brilho(cx, chao - 60f * s, 150f * s, 0x8C0010, 0.30f * pulso * env);
            b.brilho(cx, chao - 60f * s, 60f * s, 0xD01020, 0.14f * pulso * env);

            // ---------------- TENTÁCULOS de sombra subindo do corpo
            for (int i = 0; i < N_TENT; i++) {
                float ang = (i / (float) N_TENT) * TAU + sin(t * 0.9f + i) * 0.18f;
                float comp = (150f + 150f * fr(sin(i * 91.7f) * 4375.5f)) * s
                        * (0.55f + 0.45f * eo(t / 1.6f)) * (0.6f + 0.4f * sai);
                float px = cx + cos(ang) * 40f * s;
                float py = chao;
                int n = 7;
                for (int k = 1; k <= n; k++) {
                    float f = k / (float) n;
                    float curv = sin(t * 2.2f + i * 1.7f + f * 3.1f) * 40f * f * s;
                    float nx = cx + cos(ang) * (40f + comp * f * 0.55f) * s + sin(ang) * curv;
                    float ny = chao - comp * f * (0.85f + 0.15f * sin(i)) + cos(ang) * curv * 0.3f;
                    float ww = (1f - f) * 22f * s + 3f * s;
                    // halo vermelho por baixo e núcleo preto por cima
                    b.seg(px, py, nx, ny, ww + 6f * s, col(0x6E000A, 0.35f * env), col(0x6E000A, 0.10f * env));
                    b.seg(px, py, nx, ny, ww, col(0x010001, 0.92f * env), col(0x010001, 0.35f * env));
                    px = nx;
                    py = ny;
                }
            }

            // ---------------- cinza subindo (o corpo se desfazendo)
            for (int i = 0; i < N_CINZA; i++) {
                float y = CINZA_Y[i] - CINZA_V[i] * t;
                if (y < -0.05f) {
                    continue;
                }
                float x = CINZA_X[i] * w + sin(t * 1.7f + CINZA_F[i]) * 12f * s;
                float a = env * (0.30f + 0.35f * fr(sin(i * 3.7f) * 999f));
                float sz = Math.max(1f, (1.2f + 1.6f * fr(sin(i * 7.1f) * 321f)) * s);
                b.rect(x, y * h, sz, sz, col(0x8A8A96, a));
            }

            // fios finos de "alma" escapando (3 feixes claros subindo)
            for (int i = 0; i < 3; i++) {
                float x0 = cx + (i - 1) * 26f * s;
                float alt = (120f + 90f * eo(t / 2.2f)) * s;
                b.seg(x0, chao - 10f * s, x0 + sin(t * 3f + i) * 18f * s, chao - alt,
                        Math.max(1f, 2.5f * s), col(0xB8B0C8, 0.18f * env), col(0xB8B0C8, 0f));
            }

            b.flush();
            normal();
        } catch (Throwable t) {
            inicio = -1L;
            dev.rptag.RPTagMod.LOGGER.warn("RP Tag: sombra da morte desligada", t);
        }
    }
}
