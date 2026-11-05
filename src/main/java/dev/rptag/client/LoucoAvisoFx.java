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
 * AVISO DO LOUCO — estilo 15 do {@code /rp admin avisar}.
 *
 * <p>Portado do protótipo JavaFX do dono ({@code FoolWarning}): a <b>névoa cinzenta</b>, os
 * <b>anéis de sigilo</b> com os 22 caminhos, o <b>relógio</b> com as sequências 9…0, a
 * <b>lua carmesim</b> subindo, os <b>olhos na névoa</b>, a <b>carta do Louco</b> (que sobe e
 * vira), cinco cartas orbitando, poeira dourada, sussurros e o título.
 *
 * <p>Tempo (igual ao protótipo, 14 s):
 * <pre>
 *  0,0 s  névoa entra
 *  0,3 s  os sigilos se desenham; o relógio começa a andar
 *  1,6 s  a carta sobe do fundo
 *  3,2 s  a carta vira (mostra a face)
 *  3,6 s  os feixes dourados acendem
 *  4,0 s  a lua carmesim sobe
 *  4,8 s  o título AVISO
 *  5,2 s  os olhos abrem na névoa; as cartas começam a orbitar
 *  6,0 s  as falas aparecem (digitadas)
 *  12,4 s a tela se apaga
 * </pre>
 *
 * <p>(5.14.0) Só o encanamento é do mod; o desenho é o do protótipo. Nada de partícula —
 * desligar partículas no jogo não tira nada daqui.
 */
public final class LoucoAvisoFx {

    private static final float DUR = 14f, CICLO = 14f;
    private static final int OURO = 0xD9B457, OURO_B = 0xF0D78C, BRUMA = 0xA8ACB8, NOITE = 0x08070E,
            CARMESIM = 0xC4202A, CARMESIM_B = 0xFF7A66;

    private static final String L1 = "Acima da névoa cinzenta, o Louco observa.";
    private static final String L3 = "Não pronuncie Seu nome.";
    private static final String[] SUSSURROS = {"quem é você?", "ele está aqui", "o Louco", "Sequência 0",
            "não olhe para cima", "a névoa escuta", "o relógio não para", "Sefirá", "ele já sabe", "silêncio"};

    /** quem sofre com imagem piscando usa o mesmo interruptor do aviso de ciberpsicose. */
    private static boolean reduzido() {
        return CiberpsicoseAvisoFx.reduzido;
    }

    private static long inicio = -1;
    private static String msg = "Um grande perigo se aproxima do servidor.";
    private static float pararEm = -1;
    private static final Random R = new Random();

    // ---- névoa (46 bolhas, as "front" passam na frente da carta)
    private static final int NPUFF = 46;
    private static final float[] PX = new float[NPUFF], PY = new float[NPUFF], PR = new float[NPUFF],
            PSP = new float[NPUFF], PPH = new float[NPUFF], PA = new float[NPUFF];
    private static final boolean[] PFRENTE = new boolean[NPUFF];

    // ---- poeira dourada
    private static final int NPOEIRA = 140;
    private static final float[] DX = new float[NPOEIRA], DY = new float[NPOEIRA], DVX = new float[NPOEIRA],
            DVY = new float[NPOEIRA], DS = new float[NPOEIRA];
    private static final int[] DVIDA = new int[NPOEIRA], DMAX = new int[NPOEIRA];

    // ---- sussurros
    private static final int NSUS = 24;
    private static final String[] SUS_TXT = new String[NSUS];
    private static final float[] SUS_X = new float[NSUS], SUS_Y = new float[NSUS];
    private static final int[] SUS_VIDA = new int[NSUS], SUS_MAX = new int[NSUS];
    private static int susProx;
    private static float ultimoSussurro;

    static {
        for (int i = 0; i < NPUFF; i++) {
            PFRENTE[i] = i % 3 == 0;
            PX[i] = rnd(i, 1) * 800f;
            PY[i] = PFRENTE[i] ? 60 + rnd(i, 3) * 380 : 100 + rnd(i, 3) * 450;
            PR[i] = 90 + rnd(i, 7) * 110;
            PSP[i] = 0.2f + rnd(i, 9) * 0.5f;
            PPH[i] = rnd(i, 11) * TAU;
            PA[i] = 0.07f + rnd(i, 13) * 0.08f;
        }
        for (int i = 0; i < NPOEIRA; i++) {
            DMAX[i] = 1;
        }
        for (int i = 0; i < NSUS; i++) {
            SUS_MAX[i] = 1;
        }
    }

    private LoucoAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_louco"), LoucoAvisoFx::render);
    }

    public static void iniciar(String mensagem) {
        iniciar(mensagem, DUR);
    }

    /** segundos &lt;= 0: fica até chamar {@link #parar()}. */
    public static void iniciar(String mensagem, float segundos) {
        msg = (mensagem == null || mensagem.isBlank()) ? "Um grande perigo se aproxima do servidor." : mensagem;
        pararEm = -1;
        ultimoSussurro = 0;
        for (int i = 0; i < NPOEIRA; i++) {
            DMAX[i] = 1;
            DVIDA[i] = 1;
        }
        for (int i = 0; i < NSUS; i++) {
            SUS_MAX[i] = 1;
            SUS_VIDA[i] = 1;
        }
        inicio = System.nanoTime();
        tocar(SoundEvents.AMBIENT_CAVE.value(), 0.55f, 0.35f);
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
        if (reduzido()) {
            t = T;
        }

        int w = g.guiWidth(), h = g.guiHeight();
        float k = Math.min(w / 800f, h / 600f);
        float cx = w / 2f, cy = h / 2f;
        float env = ss(0f, 0.8f, T) * (1 - ss(fim - 1.6f, fim - 0.2f, T));
        float bruma = ss(0f, 1.5f, T) * (1 + ss(T - 12f, T - 10f, T) * 0.6f);
        float feixes = ss(3.6f, 4.8f, T);

        Buf b = AvisoRitual.comecar(g);

        // ---- fundo: noite + clarão dourado no centro
        AvisoRitual.degradeVertical(b, 0, 0, w, h, NOITE, 0x14121C, 24);
        AvisoRitual.halo(b, cx, cy, 40 * k, 520 * k, OURO, 0.10f * bruma * env);

        // ---- névoa de trás
        nevoa(b, false, T, bruma, k, cx, cy, env);

        // ---- feixes dourados
        if (feixes > 0.01f) {
            for (int i = 0; i < 16; i++) {
                float a = i * TAU / 16 + T * 0.05f;
                float x1 = cx + cos(a) * 430 * k, y1 = cy + sin(a) * 430 * k;
                float x2 = cx + cos(a + 0.035f) * 430 * k, y2 = cy + sin(a + 0.035f) * 430 * k;
                AvisoRitual.poligono(b, new float[]{cx, x1, x2}, new float[]{cy, y1, y2}, 3, col(OURO, 0.05f * feixes * env));
            }
        }

        // ---- lua carmesim (sobe a partir de 4 s)
        lua(b, T, k, cx, cy, env);

        // ---- sigilos (22 caminhos + relógio das sequências)
        float dp = ss(0.3f, 2.3f, T);
        if (dp > 0.01f) {
            sigilos(b, T, dp, k, cx, cy, env);
        }

        // ---- as sequências 9…0 (texto, fora do Buf)
        numeros(g, T, dp, k, cx, cy, env);

        // ---- olhos na névoa (a partir de 5,2 s)
        olhos(b, T, k, cx, cy, env);

        // ---- cartas orbitando (trás) + a carta do Louco + (frente)
        orbitando(b, T, false, k, cx, cy, env);
        carta(b, T, k, cx, cy, env);
        orbitando(b, T, true, k, cx, cy, env);

        // ---- poeira dourada
        poeira(b, t, T, k, cx, cy, env);
        b.flush();

        // ---- névoa da frente (por cima da carta)
        b = AvisoRitual.comecar(g);
        nevoa(b, true, T, bruma, k, cx, cy, env);
        b.flush();

        // ---- sussurros (texto)
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
            AvisoRitual.textoCentro(g, SUS_TXT[i], cx + (SUS_X[i] - 400) * k, cy + (SUS_Y[i] - 300) * k,
                    k * 0.9f, col(0xE0DCC8, a), false);
        }

        // ---- título e falas
        titulo(g, T, k, cx, cy, env);
        falas(g, T, k, cx, cy, env);

        // ---- vinheta e grão
        RenderSystem.enableBlend();
        Buf v = AvisoRitual.comecar(g);
        v.vinheta(w, h, Math.min(w, h) * 0.20f, NOITE, 0.55f * env);
        float pulso = (float) Math.pow(Math.max(0, sin(t * 2.4f)), 2) * ss(5f, 6f, T);
        if (pulso > 0.01f) {
            AvisoRitual.anel(v, cx, cy, Math.min(w, h) * 0.42f, Math.min(w, h) * 0.95f, 0x2A0206, 0f, 0.55f * pulso, 26);
        }
        v.flush();
        RenderSystem.enableDepthTest();
    }

    // ==================================================================== peças
    private static void nevoa(Buf b, boolean frente, float T, float amt, float k, float cx, float cy, float env) {
        for (int i = 0; i < NPUFF; i++) {
            if (PFRENTE[i] != frente) {
                continue;
            }
            float px = ((PX[i] + T * PSP[i] * 14f) % 1300f) - 250f;
            float py = PY[i] + sin(T * 0.3f + PPH[i]) * 22f;
            float a = PA[i] * amt * env * (frente ? 0.7f : 1f);
            if (a <= 0.004f) {
                continue;
            }
            AvisoRitual.halo(b, cx + (px - 400) * k, cy + (py - 300) * k, PR[i] * k * 0.25f, PR[i] * k, BRUMA, a * 2.2f);
        }
    }

    private static void lua(Buf b, float T, float k, float cx, float cy, float env) {
        float mp = ss(4f, 6.2f, T);
        if (mp <= 0.01f) {
            return;
        }
        float mx = cx + 215 * k, my = cy + (330 - 195 * mp - 300) * k;
        AvisoRitual.halo(b, mx, my, 20 * k, 230 * k, CARMESIM, 0.34f * mp * env);
        AvisoRitual.halo(b, mx, my, 10 * k, 58 * k, CARMESIM_B, 0.55f * mp * env);
        b.circ(mx, my, 50 * k, col(CARMESIM, 0.85f * mp * env));
        b.circ(mx, my, 34 * k, col(0xFF9A80, 0.35f * mp * env));
        // crateras
        b.circ(mx - 18 * k, my - 10 * k, 10 * k, col(0x3C0008, 0.30f * mp * env));
        b.circ(mx + 14 * k, my + 12 * k, 13 * k, col(0x3C0008, 0.30f * mp * env));
        b.circ(mx - 6 * k, my + 24 * k, 6 * k, col(0x3C0008, 0.26f * mp * env));
    }

    private static void sigilos(Buf b, float T, float dp, float k, float cx, float cy, float env) {
        float rot = T * 0.12f;
        float a = dp * env;

        // 22 caminhos: cada casa ligada à nona seguinte
        for (int i = 0; i < 22; i++) {
            float a1 = i / 22f * TAU + rot, a2 = ((i + 9) % 22) / 22f * TAU + rot;
            b.seg(cx + cos(a1) * 200 * k, cy + sin(a1) * 200 * k,
                    cx + cos(a2) * 200 * k, cy + sin(a2) * 200 * k, 1f, col(OURO, 0.16f * a), col(OURO, 0.16f * a));
        }
        // os anéis se desenham a partir do topo
        float[][] aneis = {{200, 2, 0.7f}, {176, 1, 0.5f}, {150, 2, 0.6f}, {132, 1, 0.3f}};
        for (float[] an : aneis) {
            AvisoRitual.arco(b, cx, cy, an[0] * k, -1.5708f, -1.5708f + TAU * dp, an[1] * k,
                    col(OURO, an[2] * a), 72);
        }
        // marcas dos caminhos
        for (int i = 0; i < 22; i++) {
            float ang = i / 22f * TAU + rot;
            float px = cx + cos(ang) * 188 * k, py = cy + sin(ang) * 188 * k;
            glifo(b, i % 5, px, py, 1.6f * k, col(OURO_B, 0.8f * a));
        }
        // tiques do relógio
        for (int i = 0; i < 60; i++) {
            float ang = i / 60f * TAU;
            float L = (i % 5 == 0 ? 10 : 5) * k;
            b.seg(cx + cos(ang) * (150 * k - L), cy + sin(ang) * (150 * k - L),
                    cx + cos(ang) * 150 * k, cy + sin(ang) * 150 * k, 1f, col(OURO, 0.7f * a), col(OURO, 0.7f * a));
        }
        // o ponteiro: anda um passo por segundo, com "clique" (ease) no fim
        int s = (int) Math.floor(T);
        float fr = T - s, ang = (s + ss(0f, 1f, fr * 8)) * (float) Math.PI / 6f - 1.5708f;
        b.seg(cx, cy, cx + cos(ang) * 146 * k, cy + sin(ang) * 146 * k, 2f * k, col(OURO_B, a), col(OURO_B, a));
        b.circ(cx + cos(ang) * 146 * k, cy + sin(ang) * 146 * k, 4 * k, col(0xD22832, a));
        if (fr < 0.33f) {
            AvisoRitual.arco(b, cx, cy, (150 + fr * 60) * k, 0f, TAU, 1.5f * k, col(0xFF503C, (1 - fr * 3) * 0.4f * a), 40);
        }
    }

    /** as sequências 9…0 girando ao contrário do relógio (texto, então fora do Buf). */
    private static void numeros(GuiGraphics g, float T, float dp, float k, float cx, float cy, float env) {
        float a = dp * env;
        if (a <= 0.01f) {
            return;
        }
        float rot = T * 0.12f;
        for (int i = 0; i < 10; i++) {
            float ang = i / 10f * TAU - rot * 1.6f;
            AvisoRitual.textoCentro(g, String.valueOf(9 - i), cx + cos(ang) * 163 * k - 3 * k,
                    cy + sin(ang) * 163 * k - 4.5f * k, k * 1.35f, col(0xF0D78C, 0.85f * a), false);
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
            float dx = (cx - x), dy = (cy - y);
            float d = (float) Math.max(0.001f, Math.hypot(dx, dy));
            AvisoRitual.halo(b, x, y, 6 * k, 40 * k, OURO, 0.20f * o * env);
            // pálpebra: losango achatado
            float hh = 11 * o * k, ww = 24 * k;
            AvisoRitual.poligono(b, new float[]{x - ww, x, x + ww, x}, new float[]{y, y - hh, y, y + hh}, 4,
                    col(0xF2EAD0, 0.85f * o * env));
            float ix = x + dx / d * 6 * k, iy = y + dy / d * 3 * k;
            b.circ(ix, iy, 5 * k, col(0xD9A227, 0.95f * o * env));
            b.circ(ix, iy, 1.6f * k, col(0x120E06, o * env));
        }
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

        // sombra/aura
        AvisoRitual.halo(b, cx, y, 20 * k, 110 * k, frente ? OURO : OURO, 0.10f * a);
        // corpo
        int corpo = frente ? mix(0xD8D0B6, 0xA99D7C, 0.5f) : 0x14161D;
        b.rect(cx - hw, y - hh, hw * 2, hh * 2, col(corpo, a));
        // moldura dupla
        moldura(b, cx, y, hw, hh, 3f * k * esc, col(frente ? 0x5A4520 : OURO, 0.9f * a));
        moldura(b, cx, y, hw * 0.90f, hh * 0.90f, 1f * k * esc, col(frente ? 0x5A4520 : OURO, 0.6f * a));

        if (!frente) {
            // verso: dois círculos + olho dourado
            AvisoRitual.arco(b, cx, y, 34 * k * esc, 0f, TAU, 1.5f * k * esc, col(OURO, 0.7f * a), 26);
            AvisoRitual.arco(b, cx, y, 26 * k * esc, 0f, TAU, 1.2f * k * esc, col(OURO, 0.55f * a), 22);
            for (int i = 0; i < 8; i++) {
                float an = i * 0.7854f + T * 0.3f;
                b.seg(cx + cos(an) * 26 * k * esc, y + sin(an) * 26 * k, cx + cos(an) * 34 * k * esc, y + sin(an) * 34 * k,
                        1f * k * esc, col(OURO, 0.7f * a), col(OURO, 0.7f * a));
            }
            AvisoRitual.poligono(b, new float[]{cx - 20 * k * esc, cx, cx + 20 * k * esc, cx},
                    new float[]{y, y - 9 * k, y, y + 9 * k}, 4, col(OURO, 0.55f * a));
            b.circ(cx, y, 4 * k * esc, col(0xC01828, 0.95f * a));
        } else {
            // face: o caminhante sob a lua vermelha
            AvisoRitual.degradeVertical(b, cx - hw * 0.75f, y - hh * 0.55f, hw * 1.5f, hh * 1.1f, 0x8E8F98, 0xC9C3AE, 8);
            AvisoRitual.halo(b, cx + 24 * k * esc, y - 30 * k, 8 * k * esc, 30 * k * esc, 0xB3261E, 0.45f * a);
            b.circ(cx + 11 * k * esc, y - 43 * k, 13 * k * esc, col(0xB3261E, 0.9f * a));
            b.rect(cx - hw * 0.75f, y + 34 * k, hw * 1.5f, 20 * k, col(0x2A2420, a));
            // figura de costas (chapéu, ombros, cajado)
            b.rect(cx - 8 * k * esc, y - 22 * k, 16 * k * esc, 18 * k, col(0x0C0B0D, a));
            AvisoRitual.poligono(b, new float[]{cx - 12 * k * esc, cx + 12 * k * esc, cx + 17 * k * esc, cx - 17 * k * esc},
                    new float[]{y + 8 * k, y + 8 * k, y + 34 * k, y + 34 * k}, 4, col(0x0C0B0D, a));
            b.seg(cx + 24 * k * esc, y + 10 * k, cx + 28 * k * esc, y + 34 * k, 2f * k * esc, col(0x0C0B0D, a), col(0x0C0B0D, a));
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
            b.rect(x - 14 * esc, y - 22 * esc, 28 * esc, 44 * esc, col(0x14161D, al));
            moldura(b, x, y, 14 * esc, 22 * esc, 1.5f * esc, col(OURO, 0.85f * al));
            AvisoRitual.arco(b, x, y, 6 * esc, 0f, TAU, 1.2f * esc, col(OURO, 0.8f * al), 12);
            b.circ(x, y, 2 * esc, col(0xC01828, al));
        }
    }

    private static void poeira(Buf b, float t, float T, float k, float cx, float cy, float env) {
        if (T > 2 && R.nextFloat() < 0.8f) {
            for (int i = 0; i < NPOEIRA; i++) {
                if (DMAX[i] <= DVIDA[i]) {
                    float an = R.nextFloat() * TAU;
                    DX[i] = 400 + cos(an) * 200;
                    DY[i] = 255 + sin(an) * 200;
                    DVX[i] = (R.nextFloat() - 0.5f) * 0.3f;
                    DVY[i] = -(0.3f + R.nextFloat() * 0.5f);
                    DMAX[i] = 80 + R.nextInt(100);
                    DS[i] = 1 + R.nextFloat() * 1.6f;
                    DVIDA[i] = 0;
                    break;
                }
            }
        }
        aditivo();
        for (int i = 0; i < NPOEIRA; i++) {
            if (DMAX[i] <= DVIDA[i]) {
                continue;
            }
            DX[i] += DVX[i] + sin(t * 2 + DY[i] * 0.05f) * 0.15f;
            DY[i] += DVY[i];
            DVIDA[i]++;
            float a = sin((float) Math.PI * DVIDA[i] / DMAX[i]) * 0.7f * env;
            if (a <= 0.01f) {
                continue;
            }
            float s = DS[i] * k;
            b.rect(cx + (DX[i] - 400) * k, cy + (DY[i] - 300) * k, s, s, col(0xF0CD78, a));
        }
        normal();
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
        for (int i = 0; i < palavra.length(); i++) {
            total += net.minecraft.client.Minecraft.getInstance().font.width(String.valueOf(palavra.charAt(i))) * esc;
        }
        total += espaco * (palavra.length() - 1);
        float x = cx - total / 2f;
        float baseY = cy + 200 * k;
        for (int i = 0; i < palavra.length(); i++) {
            String ch = String.valueOf(palavra.charAt(i));
            float a = ss(T0 + i * 0.18f, T0 + i * 0.18f + 0.45f, T) * env;
            float larg = net.minecraft.client.Minecraft.getInstance().font.width(ch) * esc;
            if (a > 0.01f) {
                float yy = baseY + (1 - a) * 18 * k;
                int cor = col(OURO_B, a);
                AvisoRitual.texto(g, ch, x, yy, esc, cor, true);
            }
            x += larg + espaco;
        }
        float u = ss(T0 + 0.9f, T0 + 1.7f, T) * env;
        if (u > 0.01f) {
            Buf b = AvisoRitual.comecar(g);
            b.rect(cx - (total / 2f * u + 10 * k), cy + 212 * k, (total * u + 20 * k), 1.5f * k, col(OURO, 0.8f * u));
            AvisoRitual.poligono(b, new float[]{cx, cx + 5 * k, cx, cx - 5 * k},
                    new float[]{cy + 207 * k, cy + 212 * k, cy + 217 * k, cy + 212 * k}, 4, col(OURO, u));
            b.flush();
        }
    }

    private static void falas(GuiGraphics g, float T, float k, float cx, float cy, float env) {
        digitado(g, L1, 6.0f, 54, T, k, cx, cy, col(0xC9BC96, env));
        digitado(g, msg, 7.4f, 82, T, k, cx, cy, col(0xF4E6C8, env));
        digitado(g, L3, 9.0f, 108, T, k, cx, cy, col(0xD03A3A, env));
    }

    /** letra por letra (30 por segundo), com cursor piscando. */
    private static void digitado(GuiGraphics g, String txt, float inicioT, float dy, float T, float k, float cx, float cy,
            int cor) {
        int n = (int) Math.floor((T - inicioT) * 30);
        if (n <= 0 || T < inicioT) {
            return;
        }
        String s = txt.substring(0, Math.min(txt.length(), n));
        boolean cursor = n < txt.length() && ((int) Math.floor(T * 6)) % 2 == 1;
        float esc = k * 1.42f;
        AvisoRitual.textoCentro(g, s + (cursor ? "_" : ""), cx, cy + dy * k, esc, cor, true);
    }

}
