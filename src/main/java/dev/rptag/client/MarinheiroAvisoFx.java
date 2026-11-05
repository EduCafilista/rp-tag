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
 * AVISO DO MARINHEIRO (O ENFORCADO) — estilo 16 do {@code /rp admin avisar}.
 *
 * <p>Portado do protótipo JavaFX do dono ({@code HangedManWarning}): <b>tempestade</b>, raios
 * ramificados, <b>mar revolto</b>, <b>leme de navio</b> girando no anel de sigilo, arcos
 * elétricos correndo pelo anel, <b>bolas de raio</b> (esferas flutuantes), chuva, a
 * <b>carta do Enforcado</b>, cartas orbitando, faíscas, sussurros e o título.
 *
 * <p>Tempo (14 s, igual ao protótipo):
 * <pre>
 *  0,0 s  a tempestade entra (céu, nuvens, mar)
 *  2,2 s  primeiro relâmpago
 *  3,6 s  os feixes azuis acendem; o leme aparece
 *  4,8 s  o relâmpago grande + tremor (a carta começa a subir em 1,6 s e vira em 3,2 s)
 *  5,2 s  as bolas de raio acendem
 *  5,6 s  as cartas começam a orbitar
 *  12,4 s a tela se apaga
 * </pre>
 *
 * <p>(5.14.0) Nada de partícula e nada de arquivo: o raio é geometria refeita a cada descarga.
 */
public final class MarinheiroAvisoFx {

    private static final float DUR = 14f, CICLO = 14f;
    private static final int AZUL = 0x6EC0FF, AZUL_B = 0xEAF6FF, NOITE = 0x05070D, MAR = 0x0D2236,
            ENFORCADO = 0x162235, VERMELHO = 0xC4202A;
    private static final float HOR = 440;

    private static final String L1 = "Sobre o mar de névoa, o Enforcado escuta a tempestade.";
    private static final String L3 = "O trovão já chamou o seu nome.";
    private static final String[] SUSSURROS = {"o mar escuta", "maré negra", "o trovão chama", "âncora",
            "Enforcado", "relâmpago", "a tempestade sabe", "afunde", "Caminho do Marinheiro", "ele está pendurado"};
    /** os instantes das descargas grandes (o 4,8 é o que faz a tela tremer). */
    private static final float[] AGENDA = {2.2f, 3.7f, 4.8f, 6.9f, 9.0f, 10.6f};

    private static long inicio = -1;
    private static String msg = "Um grande perigo se aproxima do servidor.";
    private static float pararEm = -1;
    private static final Random R = new Random();

    private static boolean reduzido() {
        return CiberpsicoseAvisoFx.reduzido;
    }

    // ---- nuvens e névoa do mar
    private static final int NNUVEM = 26, NNEBLINA = 14;
    private static final float[] NX = new float[NNUVEM], NY = new float[NNUVEM], NR = new float[NNUVEM],
            NSP = new float[NNUVEM], NPH = new float[NNUVEM], NA = new float[NNUVEM];
    private static final float[] MX = new float[NNEBLINA], MY = new float[NNEBLINA], MR = new float[NNEBLINA],
            MSP = new float[NNEBLINA], MPH = new float[NNEBLINA], MA = new float[NNEBLINA];

    // ---- raios (até 4 vivos ao mesmo tempo)
    private static final int NRAIO = 4, PTS = 25, RAMOS = 4, PRAMO = 7;
    private static final float[][] RX = new float[NRAIO][PTS], RY = new float[NRAIO][PTS];
    private static final float[][][] BRX = new float[NRAIO][RAMOS][PRAMO], BRY = new float[NRAIO][RAMOS][PRAMO];
    private static final float[] RNASC = new float[NRAIO];
    private static final boolean[] RGRANDE = new boolean[NRAIO];
    private static final boolean[] RVIVO = new boolean[NRAIO];
    private static int raioProx;
    private static float ultimaAgenda = -1;

    // ---- faíscas elétricas
    private static final int NFAISCA = 140;
    private static final float[] FX0 = new float[NFAISCA], FY0 = new float[NFAISCA], FVX = new float[NFAISCA],
            FVY = new float[NFAISCA], FS = new float[NFAISCA];
    private static final int[] FVIDA = new int[NFAISCA], FMAX = new int[NFAISCA];

    // ---- sussurros
    private static final int NSUS = 24;
    private static final String[] SUS_TXT = new String[NSUS];
    private static final float[] SUS_X = new float[NSUS], SUS_Y = new float[NSUS];
    private static final int[] SUS_VIDA = new int[NSUS], SUS_MAX = new int[NSUS];
    private static int susProx;
    private static float ultimoSussurro;

    static {
        for (int i = 0; i < NNUVEM; i++) {
            NX[i] = rnd(i, 1) * 800f;
            NY[i] = -20 + rnd(i, 3) * 240;
            NR[i] = 110 + rnd(i, 7) * 120;
            NSP[i] = 0.15f + rnd(i, 9) * 0.4f;
            NPH[i] = rnd(i, 11) * TAU;
            NA[i] = 0.5f + rnd(i, 13) * 0.3f;
        }
        for (int i = 0; i < NNEBLINA; i++) {
            MX[i] = rnd(i, 40) * 800f;
            MY[i] = 380 + rnd(i, 43) * 160;
            MR[i] = 100 + rnd(i, 47) * 70;
            MSP[i] = 0.3f + rnd(i, 49) * 0.5f;
            MPH[i] = rnd(i, 51) * TAU;
            MA[i] = 0.05f + rnd(i, 53) * 0.03f;
        }
        for (int i = 0; i < NFAISCA; i++) {
            FMAX[i] = 1;
        }
        for (int i = 0; i < NSUS; i++) {
            SUS_MAX[i] = 1;
        }
    }

    private MarinheiroAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_marinheiro"),
                MarinheiroAvisoFx::render);
    }

    public static void iniciar(String mensagem) {
        iniciar(mensagem, DUR);
    }

    public static void iniciar(String mensagem, float segundos) {
        msg = (mensagem == null || mensagem.isBlank()) ? "Um grande perigo se aproxima do servidor." : mensagem;
        pararEm = -1;
        ultimaAgenda = -1;
        ultimoSussurro = 0;
        for (int i = 0; i < NRAIO; i++) {
            RVIVO[i] = false;
        }
        for (int i = 0; i < NFAISCA; i++) {
            FMAX[i] = 1;
            FVIDA[i] = 1;
        }
        for (int i = 0; i < NSUS; i++) {
            SUS_MAX[i] = 1;
            SUS_VIDA[i] = 1;
        }
        inicio = System.nanoTime();
        tocar(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.3f, 0.45f);
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
        float mar = ss(0f, 1.5f, T) * (1 + ss(T - 12f, T - 10f, T) * 0.8f);
        float feixes = ss(3.6f, 4.8f, T);

        // ---- descargas
        agenda(t, T);
        float flash = flash(t);
        float tremor = reduzido() ? 0f : tremorDe(t);
        float ox = (R.nextFloat() - 0.5f) * tremor * k, oy = (R.nextFloat() - 0.5f) * tremor * k;

        Buf b = AvisoRitual.comecar(g);

        // ---- céu (degradê) + clarão do raio
        AvisoRitual.degradeVertical(b, ox, oy, w, HOR * k + cy + oy, 0x05070D, 0x12213A, 18);
        if (flash > 0.01f) {
            AvisoRitual.halo(b, cx, cy + (HOR - 300) * k, 60 * k, 380 * k, 0x3C6EAA, 0.30f * flash * env);
        }

        // ---- nuvens
        for (int i = 0; i < NNUVEM; i++) {
            float px = ((NX[i] + T * NSP[i] * 12f) % 1300f) - 250f;
            float py = NY[i] + sin(T * 0.3f + NPH[i]) * 14f;
            float fl = flash * 0.55f;
            int cor = ((int) (14 + 96 * fl) << 16) | ((int) (20 + 120 * fl) << 8) | (int) (32 + 158 * fl);
            AvisoRitual.halo(b, cx + (px - 400) * k + ox, cy + (py - 300) * k + oy, NR[i] * k * 0.2f, NR[i] * k,
                    cor, NA[i] * 0.55f * env);
        }

        // ---- mar (4 faixas de onda em perspectiva)
        mar(b, T, flash, ss(4f, 6f, T) * env, k, cx, cy, ox, oy, env);

        // ---- raios
        raios(b, t, k, cx, cy, ox, oy, env);

        // ---- feixes azuis
        if (feixes > 0.01f) {
            for (int i = 0; i < 16; i++) {
                float a = i * TAU / 16 + T * 0.05f;
                float x1 = cx + cos(a) * 430 * k, y1 = cy + sin(a) * 430 * k;
                float x2 = cx + cos(a + 0.035f) * 430 * k, y2 = cy + sin(a + 0.035f) * 430 * k;
                AvisoRitual.poligono(b, new float[]{cx, x1, x2}, new float[]{cy, y1, y2}, 3,
                        col(AZUL, 0.04f * feixes * env));
            }
        }

        // ---- sigilos com o leme
        float dp = ss(0.3f, 2.3f, T);
        if (dp > 0.01f) {
            sigilos(b, t, T, dp, k, cx, cy, env);
            numeros(g, T, dp, k, cx, cy, env);
        }

        // ---- bolas de raio
        esferas(b, T, k, cx, cy, env);

        // ---- cartas
        orbitando(b, T, false, k, cx, cy, env);
        carta(b, T, k, cx, cy, env);
        orbitando(b, T, true, k, cx, cy, env);

        // ---- faíscas elétricas
        faiscas(b, t, T, k, cx, cy, env);
        b.flush();

        // ---- névoa da frente + chuva
        b = AvisoRitual.comecar(g);
        for (int i = 0; i < NNEBLINA; i++) {
            float px = ((MX[i] + T * MSP[i] * 14f) % 1300f) - 250f;
            float py = MY[i] + sin(T * 0.3f + MPH[i]) * 16f;
            AvisoRitual.halo(b, cx + (px - 400) * k, cy + (py - 300) * k, MR[i] * k * 0.2f, MR[i] * k,
                    0x96AFC8, MA[i] * mar * env * 1.6f);
        }
        chuva(b, t, T, k, cx, cy, w, h, env, ox, oy);
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
            float a = sin((float) Math.PI * SUS_VIDA[i] / SUS_MAX[i]) * 0.30f * env;
            if (a <= 0.01f) {
                continue;
            }
            AvisoRitual.textoCentro(g, SUS_TXT[i], cx + (SUS_X[i] - 400) * k, cy + (SUS_Y[i] - 300) * k, k * 0.9f,
                    col(0xB4D2EB, a), false);
        }

        // ---- textos
        titulo(g, T, k, cx, cy, env);
        falas(g, T, k, cx, cy, env);

        // ---- clarão do relâmpago, vinheta, grão
        Buf v = AvisoRitual.comecar(g);
        if (flash > 0.01f && !reduzido()) {
            v.rect(0, 0, w, h, col(0xAFD4FF, 0.22f * flash * env));
        }
        v.vinheta(w, h, Math.min(w, h) * 0.22f, NOITE, 0.55f * env);
        float pulso = (float) Math.pow(Math.max(0, sin(t * 2.4f)), 2) * ss(5f, 6f, T);
        if (pulso > 0.01f) {
            AvisoRitual.anel(v, cx, cy, Math.min(w, h) * 0.42f, Math.min(w, h) * 0.95f, 0x14040A, 0f,
                    0.55f * pulso * env, 26);
        }
        v.flush();
        RenderSystem.enableDepthTest();
    }

    // ==================================================================== raios
    private static void agenda(float t, float T) {
        if (T < ultimaAgenda) {
            return;
        }
        for (float s : AGENDA) {
            if (ultimaAgenda < s && T >= s) {
                nascer(t, s == 4.8f, -1);
                if (s == 4.8f) {
                    nascer(t, true, 120);
                    nascer(t, true, 680);
                    tocar(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.9f, 0.55f);
                }
            }
        }
        if (R.nextFloat() < 0.02f && T > 1 && T < 12.5f) {
            nascer(t, false, -1);
        }
        ultimaAgenda = T;
    }

    /** gera um raio por deslocamento de ponto médio, com ramos. */
    private static void nascer(float t, boolean grande, float x0) {
        int i = raioProx++ % NRAIO;
        if (x0 < 0) {
            x0 = 100 + R.nextFloat() * 600;
        }
        float x1 = x0 + (R.nextFloat() - 0.5f) * 200;
        // pontos base: reta de (x0,-10) a (x1, HOR-10), refinada 4x
        float[] xs = new float[PTS], ys = new float[PTS];
        xs[0] = x0;
        ys[0] = -10;
        xs[1] = x1;
        ys[1] = HOR - 10;
        int n = 2;
        float off = 90;
        for (int it = 0; it < 4 && n < PTS; it++) {
            float[] nx = new float[PTS], ny = new float[PTS];
            int m = 0;
            for (int j = 0; j < n - 1 && m + 2 < PTS; j++) {
                nx[m] = xs[j];
                ny[m] = ys[j];
                m++;
                nx[m] = (xs[j] + xs[j + 1]) / 2f + (R.nextFloat() - 0.5f) * off;
                ny[m] = (ys[j] + ys[j + 1]) / 2f + (R.nextFloat() - 0.5f) * off * 0.4f;
                m++;
            }
            if (m == 0) {
                break;
            }
            nx[m] = xs[n - 1];
            ny[m] = ys[n - 1];
            m++;
            xs = nx;
            ys = ny;
            n = m;
            off *= 0.55f;
        }
        for (int j = 0; j < PTS; j++) {
            RX[i][j] = xs[Math.min(j, n - 1)];
            RY[i][j] = ys[Math.min(j, n - 1)];
        }
        for (int r = 0; r < RAMOS; r++) {
            int base = Math.min(n - 2, 2 + R.nextInt(Math.max(1, n - 4)));
            float bx = xs[base], by = ys[base];
            float ex = bx + (R.nextFloat() - 0.5f) * 220, ey = by + 60 + R.nextFloat() * 110;
            float px = bx, py = by;
            for (int j = 1; j < PRAMO; j++) {
                float u = j / (float) (PRAMO - 1);
                px = lerp(bx, ex, u) + (R.nextFloat() - 0.5f) * 26;
                py = lerp(by, ey, u) + (R.nextFloat() - 0.5f) * 14;
                BRX[i][r][j] = px;
                BRY[i][r][j] = py;
            }
            BRX[i][r][0] = bx;
            BRY[i][r][0] = by;
        }
        RNASC[i] = t;
        RGRANDE[i] = grande;
        RVIVO[i] = true;
    }

    private static float flash(float t) {
        float f = 0;
        for (int i = 0; i < NRAIO; i++) {
            if (!RVIVO[i]) {
                continue;
            }
            float a = t - RNASC[i];
            if (a < 0.5f) {
                f = Math.max(f, (float) Math.exp(-a * 7) * (RGRANDE[i] ? 1f : 0.6f));
            }
        }
        return reduzido() ? f * 0.4f : f;
    }

    private static float tremorDe(float t) {
        float v = 0;
        for (int i = 0; i < NRAIO; i++) {
            if (!RVIVO[i] || !RGRANDE[i]) {
                continue;
            }
            float a = t - RNASC[i];
            if (a > 0.1f && a < 0.6f) {
                v = Math.max(v, (float) Math.exp(-(a - 0.1f) * 6));
            }
        }
        return v * 8;
    }

    private static void raios(Buf b, float t, float k, float cx, float cy, float ox, float oy, float env) {
        aditivo();
        for (int i = 0; i < NRAIO; i++) {
            if (!RVIVO[i]) {
                continue;
            }
            float a = t - RNASC[i];
            if (a > 0.5f) {
                RVIVO[i] = false;
                continue;
            }
            float pisca = (rnd((int) (Math.floor(a * 30) + i), 7) > 0.25f) ? 1f : 0.4f;
            float al = pisca * (float) Math.exp(-a * 5) * env;
            desenhaCaminho(b, RX[i], RY[i], PTS, 1f, al, k, cx, cy, ox, oy);
            for (int r = 0; r < RAMOS; r++) {
                desenhaCaminho(b, BRX[i][r], BRY[i][r], PRAMO, 0.5f, al, k, cx, cy, ox, oy);
            }
        }
        normal();
    }

    private static void desenhaCaminho(Buf b, float[] xs, float[] ys, int n, float w, float al, float k, float cx,
            float cy, float ox, float oy) {
        for (int j = 0; j < n - 1; j++) {
            float x0 = cx + (xs[j] - 400) * k + ox, y0 = cy + (ys[j] - 300) * k + oy;
            float x1 = cx + (xs[j + 1] - 400) * k + ox, y1 = cy + (ys[j + 1] - 300) * k + oy;
            b.seg(x0, y0, x1, y1, 9 * w * k, col(0x3C82FF, 0.16f * al), col(0x3C82FF, 0.16f * al));
            b.seg(x0, y0, x1, y1, 3.5f * w * k, col(0x96D2FF, 0.60f * al), col(0x96D2FF, 0.60f * al));
            b.seg(x0, y0, x1, y1, 1.3f * w * k, col(0xFFFFFF, 0.95f * al), col(0xFFFFFF, 0.95f * al));
        }
    }

    // ==================================================================== mar e chuva
    private static void mar(Buf b, float T, float flash, float surge, float k, float cx, float cy, float ox, float oy,
            float env) {
        int[] cores = {0x0D2236, 0x0A1B2D, 0x07121F, 0x040B14};
        float[] xs = new float[104], ys = new float[104];
        for (int i = 0; i < 4; i++) {
            float base = HOR + i * 26, amp = (5 + i * 3) * (1 + surge * 0.9f);
            int cnt = 102;
            for (int j = 0; j < cnt; j++) {
                float x = -10 + j * 8;
                xs[j] = cx + (x - 400) * k + ox;
                ys[j] = cy + (base + sin(x * 0.012f + T * (0.8f + i * 0.25f) + i * 2) * amp
                        + sin(x * 0.03f - T * 1.3f + i) * amp * 0.4f - 300) * k + oy;
            }
            // fecha em baixo (triângulo até a base da tela)
            float[] px = new float[cnt + 2], py = new float[cnt + 2];
            System.arraycopy(xs, 0, px, 0, cnt);
            System.arraycopy(ys, 0, py, 0, cnt);
            px[cnt] = xs[cnt - 1];
            py[cnt] = cy + 320 * k;
            px[cnt + 1] = xs[0];
            py[cnt + 1] = cy + 320 * k;
            AvisoRitual.poligono(b, px, py, cnt + 2, col(cores[i], env));
            if (i < 2) {
                AvisoRitual.polilinha(b, xs, ys, cnt, 1.4f * k, col(0xBEE1FF, (0.14f + flash * 0.45f) * env));
            }
        }
    }

    private static void chuva(Buf b, float t, float T, float k, float cx, float cy, int w, int h, float env, float ox,
            float oy) {
        int cnt = 40 + (int) Math.floor(120 * ss(2f, 5f, T));
        int cor = col(0xAACDF0, 0.22f * env);
        for (int i = 0; i < cnt; i++) {
            float x = ((rnd(i, 1) * 1040) + t * 40 * (0.6f + rnd(i, 9))) % 860 - 30;
            float y = ((rnd(i, 5) * 600) + t * (520 + rnd(i, 3) * 300)) % 640 - 20;
            float sx = cx + (x - 400) * k + ox, sy = cy + (y - 300) * k + oy;
            b.seg(sx, sy, sx - 6 * k, sy + 14 * k, 1f * k, cor, cor);
        }
    }

    // ==================================================================== sigilos
    private static void sigilos(Buf b, float t, float T, float dp, float k, float cx, float cy, float env) {
        float rot = T * 0.12f;
        float a = dp * env;
        for (int i = 0; i < 22; i++) {
            float a1 = i / 22f * TAU + rot, a2 = ((i + 9) % 22) / 22f * TAU + rot;
            b.seg(cx + cos(a1) * 200 * k, cy + sin(a1) * 200 * k, cx + cos(a2) * 200 * k, cy + sin(a2) * 200 * k,
                    1f, col(AZUL, 0.16f * a), col(AZUL, 0.16f * a));
        }
        float[][] aneis = {{200, 2, 0.7f}, {176, 1, 0.5f}, {150, 2, 0.6f}, {132, 1, 0.3f}};
        for (float[] an : aneis) {
            AvisoRitual.arco(b, cx, cy, an[0] * k, -1.5708f, -1.5708f + TAU * dp, an[1] * k, col(AZUL, an[2] * a), 72);
        }
        for (int i = 0; i < 22; i++) {
            float ang = i / 22f * TAU + rot;
            glifo(b, i % 5, cx + cos(ang) * 188 * k, cy + sin(ang) * 188 * k, 1.6f * k, col(0xA8D2FF, 0.8f * a));
        }
        // ---- leme de navio
        float wr = T * 0.15f + sin(T * 0.7f) * 0.1f;
        for (int i = 0; i < 8; i++) {
            float ang = i * (float) Math.PI / 4f + wr;
            float ca = cos(ang), sa = sin(ang);
            b.seg(cx + ca * 36 * k, cy + sa * 36 * k, cx + ca * 150 * k, cy + sa * 150 * k, 3 * k,
                    col(AZUL, 0.55f * a), col(AZUL, 0.55f * a));
            b.seg(cx + ca * 150 * k, cy + sa * 150 * k, cx + ca * 164 * k, cy + sa * 164 * k, 5 * k,
                    col(0xAAD6FF, 0.85f * a), col(0xAAD6FF, 0.85f * a));
            b.circ(cx + ca * 167 * k, cy + sa * 167 * k, 3 * k, col(0xD2EEFF, a));
        }
        for (int i = 0; i < 32; i++) {
            float ang = i / 32f * TAU;
            float L = (i % 4 == 0 ? 10 : 5) * k;
            b.seg(cx + cos(ang) * (150 * k - L), cy + sin(ang) * (150 * k - L), cx + cos(ang) * 150 * k,
                    cy + sin(ang) * 150 * k, 1f, col(AZUL, 0.7f * a), col(AZUL, 0.7f * a));
        }
        // ---- arcos elétricos correndo pelo anel
        aditivo();
        for (int j = 0; j < 3; j++) {
            float a0 = rnd((int) (Math.floor(t * 10) * 3 + j), 11) * TAU;
            float span = 0.5f + rnd((int) (Math.floor(t * 10) + j * 7), 13) * 0.6f;
            float[] xs = new float[15], ys = new float[15];
            for (int i = 0; i <= 14; i++) {
                float ang = a0 + span * i / 14f;
                float rr = (200 + (R.nextFloat() - 0.5f) * 10) * k;
                xs[i] = cx + cos(ang) * rr;
                ys[i] = cy + sin(ang) * rr;
            }
            AvisoRitual.polilinha(b, xs, ys, 15, 4 * k, col(0x3C8CFF, 0.30f * a));
            AvisoRitual.polilinha(b, xs, ys, 15, 1.2f * k, col(0xDCF0FF, 0.90f * a));
        }
        normal();
    }

    /** as sequências 9…0 (texto). */
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

    /** bolas de raio: esferas flutuando ao redor do anel, com estalos saindo delas. */
    private static void esferas(Buf b, float T, float k, float cx, float cy, float env) {
        aditivo();
        for (int i = 0; i < 5; i++) {
            float o = ss(5.2f + i * 0.3f, 5.8f + i * 0.3f, T);
            if (o <= 0.01f) {
                continue;
            }
            float x = cx + cos(T * 0.3f + i * 1.26f) * (260 + 30 * sin(i)) * k;
            float y = cy + (60 + sin(T * 0.4f + i * 2) * 110) * k;
            float rr = (10 + 3 * sin(T * 3 + i)) * k;
            AvisoRitual.halo(b, x, y, rr * 0.4f, rr * 3f, 0x78C8FF, 0.55f * o * env);
            b.circ(x, y, rr * 0.55f, col(0xFFFFFF, 0.85f * o * env));
            for (int j = 0; j < 4; j++) {
                float ang = R.nextFloat() * TAU, L = (18 + R.nextFloat() * 10) * k;
                float mx = x + cos(ang) * L * 0.5f + (R.nextFloat() - 0.5f) * 6 * k;
                float my = y + sin(ang) * L * 0.5f + (R.nextFloat() - 0.5f) * 6 * k;
                int c = col(0xC8E6FF, 0.7f * o * env);
                b.seg(x, y, mx, my, 1f * k, c, c);
                b.seg(mx, my, x + cos(ang) * L, y + sin(ang) * L, 1f * k, c, c);
            }
        }
        normal();
    }

    private static void faiscas(Buf b, float t, float T, float k, float cx, float cy, float env) {
        if (T > 2 && R.nextFloat() < 0.8f) {
            for (int i = 0; i < NFAISCA; i++) {
                if (FMAX[i] > FVIDA[i]) {
                    continue;
                }
                float ang = R.nextFloat() * TAU;
                FX0[i] = 400 + cos(ang) * 200;
                FY0[i] = 255 + sin(ang) * 200;
                FVX[i] = cos(ang) * 0.5f + (R.nextFloat() - 0.5f) * 0.4f;
                FVY[i] = sin(ang) * 0.5f - 0.2f;
                FMAX[i] = 30 + R.nextInt(50);
                FS[i] = 1 + R.nextFloat() * 1.6f;
                FVIDA[i] = 0;
                break;
            }
        }
        aditivo();
        for (int i = 0; i < NFAISCA; i++) {
            if (FMAX[i] <= FVIDA[i]) {
                continue;
            }
            FX0[i] += FVX[i] + (R.nextFloat() - 0.5f) * 1.2f;
            FY0[i] += FVY[i] + (R.nextFloat() - 0.5f) * 1.2f;
            FVIDA[i]++;
            float a = sin((float) Math.PI * FVIDA[i] / FMAX[i]) * 0.85f * env;
            if (a <= 0.01f) {
                continue;
            }
            float s = FS[i] * k;
            b.rect(cx + (FX0[i] - 400) * k, cy + (FY0[i] - 300) * k, s, s, col(0xAAE1FF, a));
        }
        normal();
    }

    // ==================================================================== carta
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

        AvisoRitual.halo(b, cx, y, 20 * k, 110 * k, AZUL, 0.10f * a);
        b.rect(cx - hw, y - hh, hw * 2, hh * 2, col(frente ? 0xB6C2CC : 0x0B1220, a));
        moldura(b, cx, y, hw, hh, 3 * k * esc, col(frente ? 0x26384F : 0x6EC0FF, 0.9f * a));
        moldura(b, cx, y, hw * 0.90f, hh * 0.90f, 1 * k * esc, col(frente ? 0x26384F : 0x6EC0FF, 0.55f * a));

        if (!frente) {
            AvisoRitual.arco(b, cx, y, 34 * k * esc, 0f, TAU, 2 * k * esc, col(AZUL, 0.7f * a), 26);
            AvisoRitual.arco(b, cx, y, 10 * k * esc, 0f, TAU, 1.5f * k * esc, col(AZUL, 0.7f * a), 12);
            for (int i = 0; i < 8; i++) {
                float an = i * 0.7854f + T * 0.3f;
                b.seg(cx + cos(an) * 10 * k * esc, y + sin(an) * 10 * k, cx + cos(an) * 34 * k * esc,
                        y + sin(an) * 34 * k, 1f * k * esc, col(AZUL, 0.7f * a), col(AZUL, 0.7f * a));
            }
            // o enforcado (silhueta em zigue-zague)
            AvisoRitual.poligono(b, new float[]{cx + 4 * k * esc, cx - 8 * k * esc, cx - 1 * k * esc, cx - 5 * k * esc,
                    cx + 9 * k * esc, cx + 2 * k * esc},
                    new float[]{y - 26 * k, y + 2 * k, y + 2 * k, y + 26 * k, y - 6 * k, y - 6 * k}, 6,
                    col(0xEAF6FF, 0.95f * a));
        } else {
            AvisoRitual.degradeVertical(b, cx - hw * 0.75f, y - hh * 0.55f, hw * 1.5f, hh * 1.1f, 0x2B3B52, 0x7F93A8, 8);
            // forca
            b.rect(cx - 32 * k * esc, y - 48 * k, 64 * k * esc, 5 * k, col(0x0B0E14, a));
            b.rect(cx - 32 * k * esc, y - 48 * k, 4 * k * esc, 96 * k, col(0x0B0E14, a));
            b.rect(cx + 28 * k * esc, y - 48 * k, 4 * k * esc, 96 * k, col(0x0B0E14, a));
            // corda + corpo pendurado
            b.seg(cx, y - 43 * k, cx, y - 26 * k, 1.5f * k * esc, col(0x0B0E14, a), col(0x0B0E14, a));
            b.seg(cx, y - 26 * k, cx, y + 2 * k, 5 * k * esc, col(0x0B0E14, a), col(0x0B0E14, a));
            b.seg(cx, y + 2 * k, cx, y + 24 * k, 9 * k * esc, col(0x0B0E14, a), col(0x0B0E14, a));
            b.circ(cx, y + 31 * k, 7 * k * esc, col(0x0B0E14, a));
            AvisoRitual.arco(b, cx, y + 31 * k, 12 * k * esc, 0f, TAU, 1.5f * k * esc, col(0xCFE8FF, 0.9f * a), 20);
            b.rect(cx - hw * 0.75f, y + 34 * k, hw * 1.5f, 20 * k, col(0x1B2C40, a));
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
            b.rect(x - 14 * esc, y - 22 * esc, 28 * esc, 44 * esc, col(0x0B1220, al));
            moldura(b, x, y, 14 * esc, 22 * esc, 1.5f * esc, col(AZUL, 0.85f * al));
            AvisoRitual.poligono(b, new float[]{x + 3 * esc, x - 5 * esc, x, x - 3 * esc, x + 6 * esc, x + 1 * esc},
                    new float[]{y - 12 * esc, y + 2 * esc, y + 2 * esc, y + 12 * esc, y - 3 * esc, y - 3 * esc}, 6,
                    col(AZUL_B, al));
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
                AvisoRitual.texto(g, ch, x, cy + 200 * k + (1 - a) * 18 * k, esc, col(AZUL_B, a), true);
            }
            x += larg + espaco;
        }
        float u = ss(T0 + 0.9f, T0 + 1.7f, T) * env;
        if (u > 0.01f) {
            Buf b = AvisoRitual.comecar(g);
            b.rect(cx - (total / 2f * u + 10 * k), cy + 212 * k, total * u + 20 * k, 1.5f * k, col(AZUL, 0.8f * u));
            AvisoRitual.poligono(b, new float[]{cx, cx + 5 * k, cx, cx - 5 * k},
                    new float[]{cy + 207 * k, cy + 212 * k, cy + 217 * k, cy + 212 * k}, 4, col(AZUL_B, u));
            b.flush();
        }
    }

    private static void falas(GuiGraphics g, float T, float k, float cx, float cy, float env) {
        digitado(g, L1, 6.0f, 54, T, k, cx, cy, col(0xB9C4CE, env));
        digitado(g, msg, 7.4f, 82, T, k, cx, cy, col(0xEFF7FF, env));
        digitado(g, L3, 9.0f, 108, T, k, cx, cy, col(0xD03A3A, env));
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
