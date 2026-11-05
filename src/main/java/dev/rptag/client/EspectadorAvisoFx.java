package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import java.util.Random;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * AVISO DO ESPECTADOR (A JUSTIÇA) — estilo 17 do {@code /rp admin avisar}.
 *
 * <p>Portado do protótipo JavaFX do dono ({@code JusticeWarning}): <b>céu de sonho</b> com
 * aurora rosa e lilás, a <b>espiral hipnótica</b>, as <b>ondas mentais</b>, a <b>balança</b>,
 * o anel de olhos, os olhos flutuando, bolhas de sonho, pétalas, brilhos, a <b>carta da
 * Justiça</b> e o título.
 *
 * <p>Tempo (14 s, igual ao protótipo): névoa em 0, aurora em 0,5, espiral em 1, balança em
 * 2,5, sigilos em 0,3, carta sobe em 1,6 e vira em 3,2, olhos em 5,2, bolhas em 5,2,
 * título em 4,8, falas em 6,0/7,4/9,0, apaga em 12,4.
 *
 * <p>(5.14.0) Tudo geometria e texto — partícula desligada não tira nada.
 */
public final class EspectadorAvisoFx {

    private static final float DUR = 14f, CICLO = 14f;
    private static final int ROSA = 0xFF7AB8, ROSA_B = 0xFFC2DE, LILAS = 0xA078FF, NOITE = 0x0C0714,
            CREPUSCULO = 0x26123F, OURO_ROSA = 0xFFE1F0;

    private static final String L1 = "Entre sonhos e pensamentos, a Justiça vê o que você esconde.";
    private static final String L3 = "Ela já leu a sua mente.";
    private static final String[] SUSSURROS = {"o que você pensa?", "ela está ouvindo", "sonho", "balança",
            "Justiça", "mente aberta", "não minta", "Espectador", "o que você esconde?", "ela vê"};

    private static long inicio = -1;
    private static String msg = "Um grande perigo se aproxima do servidor.";
    private static float pararEm = -1;
    private static final Random R = new Random();

    private static boolean reduzido() {
        return CiberpsicoseAvisoFx.reduzido;
    }

    // ---- névoa (trás e frente)
    private static final int NN = 30, NF = 14;
    private static final float[] BX = new float[NN], BY = new float[NN], BR = new float[NN], BSP = new float[NN],
            BPH = new float[NN], BA = new float[NN];
    private static final float[] FX = new float[NF], FY = new float[NF], FR = new float[NF], FSP = new float[NF],
            FPH = new float[NF], FA = new float[NF];

    // ---- pétalas e brilhos
    private static final int NPET = 80, NBRILHO = 120;
    private static final float[] PX = new float[NPET], PY = new float[NPET], PVY = new float[NPET], PPH = new float[NPET],
            PROT = new float[NPET], PVR = new float[NPET], PS = new float[NPET];
    private static final int[] PCOR = new int[NPET];
    private static final boolean[] PVIVA = new boolean[NPET];
    private static int petProx;
    private static final float[] LX = new float[NBRILHO], LY = new float[NBRILHO], LS = new float[NBRILHO];
    private static final int[] LVIDA = new int[NBRILHO], LMAX = new int[NBRILHO];

    // ---- sussurros
    private static final int NSUS = 24;
    private static final String[] SUS_TXT = new String[NSUS];
    private static final float[] SUS_X = new float[NSUS], SUS_Y = new float[NSUS];
    private static final int[] SUS_VIDA = new int[NSUS], SUS_MAX = new int[NSUS];
    private static int susProx;
    private static float ultimoSussurro;

    static {
        for (int i = 0; i < NN; i++) {
            BX[i] = rnd(i, 1) * 800f;
            BY[i] = 100 + rnd(i, 3) * 450;
            BR[i] = 90 + rnd(i, 7) * 110;
            BSP[i] = 0.15f + rnd(i, 9) * 0.4f;
            BPH[i] = rnd(i, 11) * TAU;
            BA[i] = 0.06f + rnd(i, 13) * 0.07f;
        }
        for (int i = 0; i < NF; i++) {
            FX[i] = rnd(i, 40) * 800f;
            FY[i] = 380 + rnd(i, 43) * 170;
            FR[i] = 100 + rnd(i, 47) * 70;
            FSP[i] = 0.3f + rnd(i, 49) * 0.5f;
            FPH[i] = rnd(i, 51) * TAU;
            FA[i] = 0.05f + rnd(i, 53) * 0.03f;
        }
        for (int i = 0; i < NBRILHO; i++) {
            LMAX[i] = 1;
        }
        for (int i = 0; i < NSUS; i++) {
            SUS_MAX[i] = 1;
        }
    }

    private EspectadorAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_espectador"),
                EspectadorAvisoFx::render);
    }

    public static void iniciar(String mensagem) {
        iniciar(mensagem, DUR);
    }

    public static void iniciar(String mensagem, float segundos) {
        msg = (mensagem == null || mensagem.isBlank()) ? "Um grande perigo se aproxima do servidor." : mensagem;
        pararEm = -1;
        ultimoSussurro = 0;
        for (int i = 0; i < NPET; i++) {
            PVIVA[i] = false;
        }
        for (int i = 0; i < NBRILHO; i++) {
            LMAX[i] = 1;
            LVIDA[i] = 1;
        }
        for (int i = 0; i < NSUS; i++) {
            SUS_MAX[i] = 1;
            SUS_VIDA[i] = 1;
        }
        inicio = System.nanoTime();
        tocar(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.5f);
    }

    public static void parar() {
        if (inicio >= 0 && pararEm < 0) {
            pararEm = (System.nanoTime() - inicio) / 1e9f;
        }
    }

    // ==================================================================== render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) {
            return;
        }
        float t = (System.nanoTime() - inicio) / 1e9f;
        float fim = DUR;
        if (pararEm >= 0) {
            fim = Math.min(fim, pararEm + 1.2f);
        }
        if (t >= fim) {
            inicio = -1;
            return;
        }
        float T = t % CICLO;
        int w = g.guiWidth(), h = g.guiHeight();
        float k = Math.min(w / 800f, h / 600f);
        float cx = w / 2f, cy = h / 2f;
        float env = ss(0f, 0.8f, T) * (1 - ss(fim - 1.6f, fim - 0.2f, T));
        float nevoa = ss(0f, 1.5f, T) * (1 + ss(T - 12f, T - 10f, T) * 0.6f);

        Buf b = AvisoRitual.comecar(g);

        // ---- céu de sonho + estrelas
        AvisoRitual.degradeVertical(b, 0, 0, w, h, NOITE, CREPUSCULO, 20);
        AvisoRitual.halo(b, cx, cy, 40 * k, 400 * k, ROSA, 0.10f * nevoa * env);
        for (int i = 0; i < 70; i++) {
            float a = (0.25f + 0.75f * (0.5f + 0.5f * sin(T * (0.8f + rnd(i, 7) * 2) + i))) * env;
            float x = rnd(i, 1) * w, y = rnd(i, 3) * (cy * 0.7f);
            float sz = rnd(i, 5) > 0.8f ? 2 * k : 1 * k;
            if (a > 0.02f) {
                b.rect(x, y, sz, sz, col(0xFFE6F3, a * 0.7f));
            }
        }

        // ---- aurora
        aurora(b, T, ss(0.5f, 2.5f, T) * env, k, cx, cy);

        // ---- espiral hipnótica + ondas mentais
        mente(b, T, k, cx, cy, env);

        // ---- névoa de trás
        nevoa(b, false, T, nevoa, k, cx, cy, env);

        // ---- a balança
        balanca(b, T, k, cx, cy, env);

        // ---- olhos flutuantes
        olhos(b, T, k, cx, cy, env);

        // ---- sigilos
        float dp = ss(0.3f, 2.3f, T);
        if (dp > 0.01f) {
            sigilos(b, T, dp, k, cx, cy, env);
            numeros(g, T, dp, k, cx, cy, env);
        }

        // ---- cartas
        orbitando(b, T, false, k, cx, cy, env);
        carta(b, T, k, cx, cy, env);
        orbitando(b, T, true, k, cx, cy, env);

        // ---- bolhas de sonho
        bolhas(b, T, k, cx, cy, env);

        // ---- pétalas
        petalas(b, t, T, k, cx, cy, env);

        // ---- brilhos
        brilhos(b, T, k, cx, cy, env, w, h);
        b.flush();

        // ---- névoa da frente
        b = AvisoRitual.comecar(g);
        nevoa(b, true, T, nevoa, k, cx, cy, env);
        b.flush();

        // ---- sussurros
        if (T > 5 && T < 11.5f && t - ultimoSussurro > 0.7f) {
            ultimoSussurro = t;
            int i = susProx++ % NSUS;
            SUS_TXT[i] = SUSSURROS[R.nextInt(SUSSURROS.length)];
            SUS_X[i] = 50 + R.nextFloat() * 700;
            SUS_Y[i] = 110 + R.nextFloat() * 320;
            SUS_VIDA[i] = 0;
            SUS_MAX[i] = 140;
        }
        for (int i = 0; i < NSUS; i++) {
            if (SUS_MAX[i] <= SUS_VIDA[i]) {
                continue;
            }
            SUS_VIDA[i]++;
            SUS_Y[i] -= 0.15f;
            float a = sin((float) Math.PI * SUS_VIDA[i] / SUS_MAX[i]) * 0.32f * env;
            if (a <= 0.01f) {
                continue;
            }
            AvisoRitual.textoCentro(g, SUS_TXT[i], cx + (SUS_X[i] - 400) * k, cy + (SUS_Y[i] - 300) * k, k * 0.9f,
                    col(0xFFC8E0, a), false);
        }

        // ---- textos
        titulo(g, T, k, cx, cy, env);
        falas(g, T, k, cx, cy, env);

        // ---- vinheta rosa batendo como coração
        Buf v = AvisoRitual.comecar(g);
        float bate = (float) Math.pow(Math.max(0, sin(t * 2.4f)), 2) * ss(5f, 6f, T);
        v.vinheta(w, h, Math.min(w, h) * 0.20f, 0x12042A, 0.45f * env);
        if (bate > 0.01f) {
            AvisoRitual.anel(v, cx, cy, Math.min(w, h) * 0.40f, Math.min(w, h) * 0.95f, 0x2A0620, 0f,
                    0.55f * bate * env, 26);
        }
        v.flush();
        RenderSystem.enableDepthTest();
    }

    // ==================================================================== céu
    private static void aurora(Buf b, float T, float amt, float k, float cx, float cy) {
        if (amt <= 0.01f) {
            return;
        }
        aditivo();
        int[] cores = {0xFF6EB4, 0xA078FF, 0xFFAADC};
        float[][] passes = {{46, 0.05f}, {24, 0.07f}, {8, 0.10f}};
        float[] xs = new float[52], ys = new float[52];
        for (int i = 0; i < 3; i++) {
            for (float[] p : passes) {
                for (int j = 0; j < 52; j++) {
                    float x = -10 + j * 16;
                    xs[j] = cx + (x - 400) * k;
                    ys[j] = cy + (150 + i * 45 + sin(x * 0.008f + T * 0.35f + i * 2) * 38
                            + sin(x * 0.02f - T * 0.5f + i) * 14 - 300) * k;
                }
                AvisoRitual.polilinha(b, xs, ys, 52, p[0] * k, col(cores[i], p[1] * amt * 3.2f));
            }
        }
        normal();
    }

    private static void mente(Buf b, float T, float k, float cx, float cy, float env) {
        float a = ss(1f, 3f, T) * env;
        if (a <= 0.01f) {
            return;
        }
        aditivo();
        // três braços de espiral
        for (int arm = 0; arm < 3; arm++) {
            float[] xs = new float[49], ys = new float[49];
            for (int i = 0; i < 49; i++) {
                float rr = (8 + i * 5) * k;
                float th = arm * 2.0944f + rr * 0.055f / k - T * 0.5f;
                xs[i] = cx + cos(th) * rr;
                ys[i] = cy + sin(th) * rr;
            }
            AvisoRitual.polilinha(b, xs, ys, 49, 2 * k, col(ROSA, 0.09f * a));
        }
        // ondas que expandem
        for (int j = 0; j < 3; j++) {
            float ph = (T * 0.5f + j / 3f) % 1f;
            float rr = (40 + ph * 240) * k;
            AvisoRitual.arco(b, cx, cy, rr, 0f, TAU, 2 * k, col(0xFFA0D2, (1 - ph) * 0.18f * a), 48);
        }
        normal();
    }

    private static void balanca(Buf b, float T, float k, float cx, float cy, float env) {
        float a = ss(2.5f, 3.5f, T) * env;
        if (a <= 0.01f) {
            return;
        }
        float inclina = sin(T * 0.9f) * (0.1f + 0.1f * ss(4.8f, 5.6f, T));
        float L = 150 * k;
        float bx = cx, by = cy - 140 * k;
        // mastro
        b.seg(bx, by - 34 * k, bx, by + 30 * k, 4 * k, col(0xFFAAD2, 0.6f * a), col(0xFFAAD2, 0.6f * a));
        AvisoRitual.arco(b, bx, by - 38 * k, 6 * k, 0f, TAU, 2 * k, col(0xFFAAD2, 0.6f * a), 14);
        // haste
        b.seg(bx - L * cos(inclina), by - L * sin(inclina), bx + L * cos(inclina), by + L * sin(inclina), 3 * k,
                col(0xFFAAD2, 0.7f * a), col(0xFFAAD2, 0.7f * a));
        // os dois pratos
        for (int s = -1; s <= 1; s += 2) {
            float ex = bx - s * L * cos(inclina), ey = by - s * L * sin(inclina);
            int c = col(0xFFAAD2, 0.55f * a);
            b.seg(ex, ey, ex - 38 * k, ey + 105 * k, 1.2f * k, c, c);
            b.seg(ex, ey, ex + 38 * k, ey + 105 * k, 1.2f * k, c, c);
            // a bacia
            AvisoRitual.poligono(b, new float[]{ex - 42 * k, ex - 22 * k, ex + 22 * k, ex + 42 * k},
                    new float[]{ey + 105 * k, ey + 124 * k, ey + 124 * k, ey + 105 * k}, 4,
                    col(0xFF96C8, 0.22f * a));
            b.seg(ex - 42 * k, ey + 105 * k, ex + 42 * k, ey + 105 * k, 2 * k, c, c);
            b.circ(ex, ey, 4 * k, col(0xFFC8E6, 0.8f * a));
        }
    }

    private static void nevoa(Buf b, boolean frente, float T, float amt, float k, float cx, float cy, float env) {
        if (frente) {
            for (int i = 0; i < NF; i++) {
                float px = ((FX[i] + T * FSP[i] * 14f) % 1300f) - 250f;
                float py = FY[i] + sin(T * 0.3f + FPH[i]) * 20f;
                int cor = i % 2 == 1 ? 0xFF8CC8 : 0xAA82FF;
                AvisoRitual.halo(b, cx + (px - 400) * k, cy + (py - 300) * k, FR[i] * k * 0.2f, FR[i] * k, cor,
                        FA[i] * amt * env * 0.7f * 1.6f);
            }
        } else {
            for (int i = 0; i < NN; i++) {
                float px = ((BX[i] + T * BSP[i] * 14f) % 1300f) - 250f;
                float py = BY[i] + sin(T * 0.3f + BPH[i]) * 20f;
                int cor = i % 2 == 1 ? 0xFF8CC8 : 0xAA82FF;
                AvisoRitual.halo(b, cx + (px - 400) * k, cy + (py - 300) * k, BR[i] * k * 0.2f, BR[i] * k, cor,
                        BA[i] * amt * env * 1.6f);
            }
        }
    }

    private static void olhos(Buf b, float T, float k, float cx, float cy, float env) {
        float[][] P = {{120, 150}, {680, 200}, {90, 330}, {710, 380}, {250, 90}, {560, 70}};
        for (int i = 0; i < P.length; i++) {
            float o = ss(5.2f + i * 0.3f, 5.8f + i * 0.3f, T);
            if (o <= 0.01f) {
                continue;
            }
            if (rnd((int) (Math.floor(T * 1.5f) + i), 3) > 0.9f) {
                o *= 0.1f;
            }
            float x = cx + (P[i][0] - 400) * k, y = cy + (P[i][1] - 300) * k;
            float dx = cx - x, dy = cy - y;
            float d = (float) Math.max(0.001f, Math.hypot(dx, dy));
            AvisoRitual.halo(b, x, y, 6 * k, 40 * k, ROSA, 0.20f * o * env);
            float hh = 11 * o * k, ww = 24 * k;
            AvisoRitual.poligono(b, new float[]{x - ww, x, x + ww, x}, new float[]{y, y - hh, y, y + hh}, 4,
                    col(0xFFEBF5, 0.85f * o * env));
            float ix = x + dx / d * 6 * k, iy = y + dy / d * 3 * k;
            b.circ(ix, iy, 6 * k, col(0xFF4F9E, 0.95f * o * env));
            float pr = (3 + 2 * sin(T * 3 + i)) * k;
            b.circ(ix, iy, pr * 0.7f, col(0x0A0008, o * env));
        }
    }

    private static void sigilos(Buf b, float T, float dp, float k, float cx, float cy, float env) {
        float rot = T * 0.12f;
        float a = dp * env;
        for (int i = 0; i < 22; i++) {
            float a1 = i / 22f * TAU + rot, a2 = ((i + 9) % 22) / 22f * TAU + rot;
            b.seg(cx + cos(a1) * 200 * k, cy + sin(a1) * 200 * k, cx + cos(a2) * 200 * k, cy + sin(a2) * 200 * k,
                    1f, col(0xFF96CD, 0.16f * a), col(0xFF96CD, 0.16f * a));
        }
        float[][] aneis = {{200, 2, 0.7f}, {176, 1, 0.5f}, {150, 2, 0.6f}, {132, 1, 0.3f}};
        for (float[] an : aneis) {
            AvisoRitual.arco(b, cx, cy, an[0] * k, -1.5708f, -1.5708f + TAU * dp, an[1] * k, col(0xFF96CD, an[2] * a), 72);
        }
        for (int i = 0; i < 22; i++) {
            float ang = i / 22f * TAU + rot;
            glifo(b, i % 5, cx + cos(ang) * 188 * k, cy + sin(ang) * 188 * k, 1.6f * k, col(0xD2AAFF, 0.8f * a));
        }
        // anel de olhos do Espectador
        for (int i = 0; i < 12; i++) {
            float ang = i / 12f * TAU + T * 0.2f;
            float op = (0.55f + 0.45f * sin(T * 0.9f + i)) * (rnd((int) Math.floor(T) + i, 5) > 0.93f ? 0.1f : 1f);
            float x = cx + cos(ang) * 150 * k, y = cy + sin(ang) * 150 * k;
            float hh = 6 * op * k, ww = 9 * k;
            b.seg(x - ww, y, x, y - hh, 1.2f * k, col(0xFFA0CD, 0.9f * a), col(0xFFA0CD, 0.9f * a));
            b.seg(x, y - hh, x + ww, y, 1.2f * k, col(0xFFA0CD, 0.9f * a), col(0xFFA0CD, 0.9f * a));
            b.seg(x + ww, y, x, y + hh, 1.2f * k, col(0xFFA0CD, 0.9f * a), col(0xFFA0CD, 0.9f * a));
            b.seg(x, y + hh, x - ww, y, 1.2f * k, col(0xFFA0CD, 0.9f * a), col(0xFFA0CD, 0.9f * a));
            b.circ(x, y, 3 * op * k, col(0xFF5AA0, a));
        }
    }

    private static void numeros(GuiGraphics g, float T, float dp, float k, float cx, float cy, float env) {
        float a = dp * env;
        if (a <= 0.01f) {
            return;
        }
        float rot = T * 0.12f;
        for (int i = 0; i < 10; i++) {
            float ang = i / 10f * TAU - rot * 1.6f;
            AvisoRitual.textoCentro(g, String.valueOf(9 - i), cx + cos(ang) * 163 * k - 3 * k,
                    cy + sin(ang) * 163 * k - 4.5f * k, k * 1.35f, col(0xFFD7EB, 0.85f * a), false);
        }
    }

    private static void glifo(Buf b, int tipo, float x, float y, float u, int c) {
        switch (tipo) {
            case 0 -> {
                b.seg(x - 5 * u, y - 5 * u, x + 5 * u, y + 5 * u, 1.2f * u, c, c);
                b.seg(x + 5 * u, y - 5 * u, x - 5 * u, y + 5 * u, 1.2f * u, c, c);
            }
            case 1 -> {
                AvisoRitual.arco(b, x, y, 4 * u, 0f, TAU, 1.2f * u, c, 14);
                b.seg(x, y - 8 * u, x, y - 4 * u, 1.2f * u, c, c);
            }
            case 2 -> {
                b.seg(x, y - 6 * u, x + 5 * u, y + 4 * u, 1.2f * u, c, c);
                b.seg(x + 5 * u, y + 4 * u, x - 5 * u, y + 4 * u, 1.2f * u, c, c);
                b.seg(x - 5 * u, y + 4 * u, x, y - 6 * u, 1.2f * u, c, c);
            }
            case 3 -> {
                b.seg(x, y - 6 * u, x, y + 6 * u, 1.2f * u, c, c);
                b.seg(x - 4 * u, y - 2 * u, x + 4 * u, y - 2 * u, 1.2f * u, c, c);
                b.seg(x - 3 * u, y + 3 * u, x + 3 * u, y + 3 * u, 1.2f * u, c, c);
            }
            default -> {
                AvisoRitual.arco(b, x, y - 1 * u, 5 * u, 0.2f, (float) Math.PI - 0.2f, 1.2f * u, c, 12);
                b.seg(x, y - 6 * u, x, y, 1.2f * u, c, c);
            }
        }
    }

    private static void bolhas(Buf b, float T, float k, float cx, float cy, float env) {
        for (int i = 0; i < 5; i++) {
            float o = ss(5.2f + i * 0.3f, 5.8f + i * 0.3f, T) * env;
            if (o <= 0.01f) {
                continue;
            }
            float x = cx + cos(T * 0.25f + i * 1.26f) * (265 + 25 * sin(i)) * k;
            float y = cy + (40 + sin(T * 0.35f + i * 2) * 120) * k;
            float rr = (16 + 4 * sin(T * 2 + i)) * k;
            AvisoRitual.halo(b, x, y, rr * 0.3f, rr, ROSA_B, 0.22f * o);
            b.circ(x, y, rr * 0.9f, col(0xFFC8E6, 0.05f * o));
            AvisoRitual.arco(b, x, y, rr, 0f, TAU, 1.2f * k, col(0xFFBEE1, 0.7f * o), 20);
            AvisoRitual.poligono(b, new float[]{x - rr * 0.6f, x - rr * 0.1f, x - rr * 0.1f, x - rr * 0.6f},
                    new float[]{y - rr * 0.53f, y - rr * 0.53f, y - rr * 0.27f, y - rr * 0.27f}, 4,
                    col(0xFFFFFF, 0.75f * o));
        }
    }

    private static void petalas(Buf b, float t, float T, float k, float cx, float cy, float env) {
        if (T > 2 && R.nextFloat() < 0.3f) {
            int i = petProx++ % NPET;
            PX[i] = R.nextFloat() * 800f;
            PY[i] = -10;
            PVY[i] = 0.6f + R.nextFloat() * 0.8f;
            PPH[i] = R.nextFloat() * TAU;
            PROT[i] = R.nextFloat() * TAU;
            PVR[i] = (R.nextFloat() - 0.5f) * 0.06f;
            PS[i] = 4 + R.nextFloat() * 3;
            PCOR[i] = R.nextBoolean() ? 0xFF8FC4 : 0xFFC2DE;
            PVIVA[i] = true;
        }
        for (int i = 0; i < NPET; i++) {
            if (!PVIVA[i]) {
                continue;
            }
            PY[i] += PVY[i];
            PX[i] += sin(t * 1.2f + PPH[i]) * 0.8f;
            PROT[i] += PVR[i];
            if (PY[i] > 610) {
                PVIVA[i] = false;
                continue;
            }
            float x = cx + (PX[i] - 400) * k, y = cy + (PY[i] - 300) * k;
            float s = PS[i] * k * 0.6f;
            float larg = s * Math.max(0.12f, 0.5f + 0.5f * sin(t * 2 + PPH[i]));
            AvisoRitual.poligono(b, new float[]{x, x + larg, x, x - larg}, new float[]{y - s, y, y + s, y}, 4,
                    col(PCOR[i], 0.8f * env));
        }
    }

    private static void brilhos(Buf b, float T, float k, float cx, float cy, float env, int w, int h) {
        if (T > 2 && R.nextFloat() < 0.7f) {
            for (int i = 0; i < NBRILHO; i++) {
                if (LMAX[i] > LVIDA[i]) {
                    continue;
                }
                float a = R.nextFloat() * TAU, rr = (60 + R.nextFloat() * 220) * k;
                LX[i] = cx + cos(a) * rr;
                LY[i] = cy + sin(a) * rr * 0.9f;
                LMAX[i] = 40 + R.nextInt(50);
                LS[i] = 1 + R.nextFloat() * 2.4f;
                LVIDA[i] = 0;
                break;
            }
        }
        aditivo();
        for (int i = 0; i < NBRILHO; i++) {
            if (LMAX[i] <= LVIDA[i]) {
                continue;
            }
            LVIDA[i]++;
            float a = sin((float) Math.PI * LVIDA[i] / LMAX[i]) * 0.9f * env;
            if (a <= 0.01f) {
                continue;
            }
            float s = LS[i] * k;
            b.rect(LX[i], LY[i], s, s, col(0xFFE1F0, a));
            if (LS[i] > 2) {
                b.rect(LX[i] - s * 2, LY[i] + s * 0.4f, s * 5, 1 * k, col(0xFFA0D2, a * 0.6f));
                b.rect(LX[i] + s * 0.4f, LY[i] - s * 2, 1 * k, s * 5, col(0xFFA0D2, a * 0.6f));
            }
        }
        normal();
    }

    private static void carta(Buf b, float T, float k, float cx, float cy, float env) {
        float sobe = ss(1.6f, 3.0f, T);
        if (sobe <= 0.01f) {
            return;
        }
        float vira = ss(3.2f, 4.1f, T);
        float ang = (float) Math.PI * vira;
        float sx = (float) Math.cos(ang);
        float y = cy + ((1 - sobe) * 140 + sin(T * 1.4f) * 5) * k;
        boolean frente = ang > 1.5708f;
        float esc = Math.max(0.02f, Math.abs(sx));
        float a = sobe * env;
        float hw = 60 * k * esc, hh = 95 * k;
        AvisoRitual.halo(b, cx, y, 20 * k, 110 * k, ROSA, 0.10f * a);
        b.rect(cx - hw, y - hh, hw * 2, hh * 2, col(frente ? 0xC6B4C6 : 0x1A0F2E, a));
        moldura(b, cx, y, hw, hh, 3 * k * esc, col(frente ? 0x5A2A52 : 0xFF9AD0, 0.9f * a));
        moldura(b, cx, y, hw * 0.90f, hh * 0.90f, 1 * k * esc, col(frente ? 0x5A2A52 : 0xFF9AD0, 0.55f * a));
        if (!frente) {
            AvisoRitual.arco(b, cx, y, 26 * k * esc, 0f, TAU, 1.5f * k * esc, col(ROSA, 0.7f * a), 24);
            // espiral que enrola
            float[] xs = new float[29], ys = new float[29];
            for (int i = 0; i < 29; i++) {
                float rr = (4 + i) * k * esc;
                float th = rr * 0.32f / Math.max(0.05f, k) - T * 1.2f;
                xs[i] = cx + cos(th) * rr;
                ys[i] = y + sin(th) * rr;
            }
            AvisoRitual.polilinha(b, xs, ys, 29, 1.2f * k * esc, col(ROSA, 0.7f * a));
            AvisoRitual.poligono(b, new float[]{cx - 26 * k * esc, cx, cx + 26 * k * esc, cx},
                    new float[]{y, y - 13 * k, y, y + 13 * k}, 4, col(0xFF4F9E, 0.85f * a));
            b.circ(cx, y, 6 * k * esc, col(0x0A0008, a));
        } else {
            AvisoRitual.degradeVertical(b, cx - hw * 0.75f, y - hh * 0.55f, hw * 1.5f, hh * 1.1f, 0x3A2A5E, 0xD9A6C8, 8);
            // a figura de vestido, com o olho na mão e a balança
            b.circ(cx, y - 46 * k, 7 * k * esc, col(0x1A0D22, a));
            AvisoRitual.poligono(b, new float[]{cx - 11 * k * esc, cx + 11 * k * esc, cx + 20 * k * esc, cx - 20 * k * esc},
                    new float[]{y - 36 * k, y - 36 * k, y + 40 * k, y + 40 * k}, 4, col(0xFF7AB8, 0.85f * a));
            b.seg(cx + 10 * k * esc, y + 14 * k, cx + 24 * k * esc, y - 8 * k, 2.5f * k * esc, col(0x1A0D22, a),
                    col(0x1A0D22, a));
            b.seg(cx + 24 * k * esc, y - 30 * k, cx + 38 * k * esc, y - 34 * k, 1.5f * k * esc, col(0xFFC2DE, 0.9f * a),
                    col(0xFFC2DE, 0.9f * a));
            b.rect(cx - hw * 0.75f, y + 34 * k, hw * 1.5f, 20 * k, col(0x3A1A38, a));
        }
    }

    private static void moldura(Buf b, float cx, float cy, float hw, float hh, float w, int c) {
        b.seg(cx - hw, cy - hh, cx + hw, cy - hh, w, c, c);
        b.seg(cx + hw, cy - hh, cx + hw, cy + hh, w, c, c);
        b.seg(cx + hw, cy + hh, cx - hw, cy + hh, w, c, c);
        b.seg(cx - hw, cy + hh, cx - hw, cy - hh, w, c, c);
    }

    private static void orbitando(Buf b, float T, boolean frente, float k, float cx, float cy, float env) {
        float a0 = ss(5.6f, 6.4f, T);
        if (a0 <= 0.01f) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            float a = T * 0.5f + i * 1.2566f, sn = sin(a);
            if ((sn >= 0) != frente) {
                continue;
            }
            float d = (sn + 1) / 2f, esc = (0.7f + 0.3f * d) * k;
            float x = cx + cos(a) * 255 * k;
            float y = cy + (sn * 70 - 10 + sin(T + i) * 6) * k;
            float al = a0 * env * (0.5f + 0.5f * d);
            b.rect(x - 14 * esc, y - 22 * esc, 28 * esc, 44 * esc, col(0x1A0F2E, al));
            moldura(b, x, y, 14 * esc, 22 * esc, 1.5f * esc, col(0xFF9AD0, 0.85f * al));
            b.seg(x - 9 * esc, y, x, y - 5 * esc, 1.2f * esc, col(0xFF9AD0, al), col(0xFF9AD0, al));
            b.seg(x, y - 5 * esc, x + 9 * esc, y, 1.2f * esc, col(0xFF9AD0, al), col(0xFF9AD0, al));
            b.seg(x + 9 * esc, y, x, y + 5 * esc, 1.2f * esc, col(0xFF9AD0, al), col(0xFF9AD0, al));
            b.seg(x, y + 5 * esc, x - 9 * esc, y, 1.2f * esc, col(0xFF9AD0, al), col(0xFF9AD0, al));
            b.circ(x, y, 2.5f * esc, col(0xFF4F9E, al));
        }
    }

    // ==================================================================== texto
    private static void titulo(GuiGraphics g, float T, float k, float cx, float cy, float env) {
        float T0 = 4.8f;
        if (T < T0) {
            return;
        }
        String palavra = "AVISO";
        float esc = k * 6.2f;
        float espaco = 14 * k;
        float total = 0;
        var fonte = net.minecraft.client.Minecraft.getInstance().font;
        for (int i = 0; i < palavra.length(); i++) {
            total += fonte.width(String.valueOf(palavra.charAt(i))) * esc;
        }
        total += espaco * (palavra.length() - 1);
        float x = cx - total / 2f;
        for (int i = 0; i < palavra.length(); i++) {
            String ch = String.valueOf(palavra.charAt(i));
            float a = ss(T0 + i * 0.18f, T0 + i * 0.18f + 0.45f, T) * env;
            float larg = fonte.width(ch) * esc;
            if (a > 0.01f) {
                AvisoRitual.texto(g, ch, x, cy + 200 * k + (1 - a) * 18 * k, esc, col(ROSA_B, a), true);
            }
            x += larg + espaco;
        }
        float u = ss(T0 + 0.9f, T0 + 1.7f, T) * env;
        if (u > 0.01f) {
            Buf b = AvisoRitual.comecar(g);
            b.rect(cx - (total / 2f * u + 10 * k), cy + 212 * k, total * u + 20 * k, 1.5f * k, col(0xFF8CC8, 0.8f * u));
            AvisoRitual.poligono(b, new float[]{cx, cx + 5 * k, cx, cx - 5 * k},
                    new float[]{cy + 207 * k, cy + 212 * k, cy + 217 * k, cy + 212 * k}, 4, col(ROSA_B, u));
            b.flush();
        }
    }

    private static void falas(GuiGraphics g, float T, float k, float cx, float cy, float env) {
        digitado(g, L1, 6.0f, 54, T, k, cx, cy, col(0xC9B4E0, env));
        digitado(g, msg, 7.4f, 82, T, k, cx, cy, col(0xFFF3FA, env));
        digitado(g, L3, 9.0f, 108, T, k, cx, cy, col(0xFF5C9A, env));
    }

    private static void digitado(GuiGraphics g, String txt, float inicioT, float dy, float T, float k, float cx, float cy,
            int cor) {
        int n = (int) Math.floor((T - inicioT) * 30);
        if (n <= 0 || T < inicioT) {
            return;
        }
        String s = txt.substring(0, Math.min(txt.length(), n));
        boolean cursor = n < txt.length() && ((int) Math.floor(T * 6)) % 2 == 1;
        AvisoRitual.textoCentro(g, s + (cursor ? "_" : ""), cx, cy + dy * k, k * 1.42f, cor, true);
    }
}
