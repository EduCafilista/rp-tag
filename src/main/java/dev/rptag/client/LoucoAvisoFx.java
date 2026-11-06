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
 * AVISO DO CAMINHO DO LOUCO — estilo 25 do {@code /rp admin avisar}.
 *
 * <p>Portado do protótipo JavaFX do dono ({@code FoolPathwayWarning}): a <b>escada de
 * Sequências</b> sobe de 9 até 0 e cada Sequência liga o seu próprio efeito:
 *
 * <pre>
 *  9 Vidente ............... pêndulo (com rastro e brilho)
 *  8 Palhaço ............... bonecos de papel balançando
 *  7 Mago .................. chamas nas duas bordas
 *  6 Sem Rosto ............. máscaras orbitando (na frente e atrás da carta)
 *  5 Marionetista .......... fios espirituais descendo do alto
 *  4 Feiticeiro Bizarro .... anel de maldição + brasas
 *  3 Estudioso do Passado .. o tempo corre ao contrário (relógio reverso, ecos da carta)
 *  2 Invocador de Milagres . pilares de luz
 *  1 Atendente dos Mistérios mãos de névoa subindo das bordas
 *  0 O Louco ............... a carta
 * </pre>
 *
 * <p>Linha do tempo: 1,4 s Sequência 9 · uma a cada 0,5 s · 5,9 s a carta vira e a Sequência 0
 * acende · 7,0 s o título · 8,2/9,5/11,0 s as falas · 14,4 s apaga · ciclo de 16 s.
 *
 * <p>(5.15.0) Nada de partícula do jogo e nada de vídeo: tudo é geometria e texto do próprio
 * mod, então "partículas: mínimo" não tira nada daqui.
 */
public final class LoucoAvisoFx {

    private static final float DUR = 16f, CICLO = 16f;
    /** o protótipo desenha num canvas 800x600 com o centro em (400, 245). */
    private static final float PW = 800f, PH = 600f, PCX = 400f, PCY = 245f;

    private static final int PALE = 0xE8E6F6, SILV = 0xAAB4CD, VIO = 0x8C78E6, CRIM = 0xC81E32,
            OURO = 0xFFEBAA, LARANJA = 0xFFAA46, NOITE = 0x07080F;

    private static final String[] NOMES = {"Vidente", "Palhaço", "Mago", "Sem Rosto", "Marionetista",
            "Feiticeiro Bizarro", "Estudioso do Passado", "Invocador de Milagres", "Atendente dos Mistérios",
            "O Louco"};
    private static final String[] SUSSURROS = {"Louco", "marionete", "fios", "sem rosto", "palhaço",
            "o espírito puxa", "Sequência 9", "milagre", "quem move você?", "a névoa responde"};
    private static final String L1 = "Todo fio leva ao mesmo lugar: ao Louco.";
    private static final String L3 = "Você já era uma marionete.";

    /** posição dos quatro bonecos de papel (no canvas do protótipo). */
    private static final float[][] BONECOS = {{70, 230}, {130, 360}, {730, 200}, {670, 340}};

    /** quando a Sequência {@code s} acende (s = 9 … 0). */
    private static float tn(int s) {
        return s == 0 ? 5.9f : 1.4f + (9 - s) * 0.5f;
    }

    private static boolean reduzido() {
        return CiberpsicoseAvisoFx.reduzido;
    }

    private static long inicio = -1;
    private static String msg = "";
    private static float pararEm = -1;
    private static float ultimaT;
    private static float ultimoSussurro;
    private static final Random R = new Random();

    // ---- névoa: trás e frente
    private static final int NPB = 34, NPF = 14;
    private static final float[] PBX = new float[NPB], PBY = new float[NPB], PBR = new float[NPB],
            PBSP = new float[NPB], PBPH = new float[NPB], PBA = new float[NPB];
    private static final float[] PFX = new float[NPF], PFY = new float[NPF], PFR = new float[NPF],
            PFSP = new float[NPF], PFPG = new float[NPF], PFA = new float[NPF];

    // ---- faiscas (as "particulas" do protótipo)
    private static final int NFX = 260;
    private static final float[] FXP = new float[NFX], FYP = new float[NFX], FVX = new float[NFX],
            FVY = new float[NFX], FS = new float[NFX];
    private static final float[] FMAX = new float[NFX], FVIDA = new float[NFX];
    private static final int[] FCOR = new int[NFX];
    private static final boolean[] FVIVA = new boolean[NFX];
    private static int fxProx;

    // ---- ondas (ripples) do estouro de cada Sequência
    private static final int NRP = 40;
    private static final float[] RPX = new float[NRP], RPY = new float[NRP], RPVIDA = new float[NRP];
    private static final int[] RPCOR = new int[NRP];
    private static final boolean[] RPVIVA = new boolean[NRP];
    private static int rpProx;

    // ---- sussurros
    private static final int NSUS = 24;
    private static final String[] SUS_TXT = new String[NSUS];
    private static final float[] SUS_X = new float[NSUS], SUS_Y = new float[NSUS];
    private static final int[] SUS_VIDA = new int[NSUS], SUS_MAX = new int[NSUS];
    private static int susProx;

    static {
        for (int i = 0; i < NPB; i++) {
            PBX[i] = rnd(i, 1) * PW;
            PBY[i] = 100 + rnd(i, 3) * 450;
            PBR[i] = 90 + rnd(i, 7) * 110;
            PBSP[i] = 0.2f + rnd(i, 9) * 0.5f;
            PBPH[i] = rnd(i, 11) * TAU;
            PBA[i] = 0.07f + rnd(i, 13) * 0.08f;
        }
        for (int i = 0; i < NPF; i++) {
            PFX[i] = rnd(i, 40) * PW;
            PFY[i] = 380 + rnd(i, 43) * 170;
            PFR[i] = 100 + rnd(i, 47) * 70;
            PFSP[i] = 0.3f + rnd(i, 49) * 0.5f;
            PFPG[i] = rnd(i, 51) * TAU;
            PFA[i] = 0.05f + rnd(i, 53) * 0.03f;
        }
        for (int i = 0; i < NSUS; i++) {
            SUS_MAX[i] = 1;
        }
    }

    private LoucoAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_louco"),
                LoucoAvisoFx::render);
    }

    public static void iniciar(String mensagem) {
        iniciar(mensagem, DUR);
    }

    /** segundos &lt;= 0: fica até chamar {@link #parar()}. */
    public static void iniciar(String mensagem, float segundos) {
        // (5.16.4) SEM TEXTO = sem fala: o aviso aparece sozinho (o dono: "as vezes e apenas aviso").
        msg = (mensagem == null) ? "" : mensagem.trim();
        pararEm = -1;
        ultimaT = 0;
        ultimoSussurro = 0;
        for (int i = 0; i < NFX; i++) {
            FVIVA[i] = false;
        }
        for (int i = 0; i < NRP; i++) {
            RPVIVA[i] = false;
        }
        for (int i = 0; i < NSUS; i++) {
            SUS_MAX[i] = 1;
            SUS_VIDA[i] = 1;
        }
        inicio = System.nanoTime();
        tocar(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 0.45f);
    }

    public static void parar() {
        if (inicio >= 0 && pararEm < 0) {
            pararEm = (System.nanoTime() - inicio) / 1e9f;
        }
    }

    // ==================================================================== geometria do protótipo
    /** posição do nó da Sequência {@code i} (i = 0 é a 9): x, y e o ângulo. */
    private static void noPos(int i, float[] out) {
        float a = (float) Math.toRadians(-90 + 36 * (i + 1));
        out[0] = PCX + cos(a) * 190;
        out[1] = PCY + sin(a) * 190;
        out[2] = a;
    }

    /** boneco {@code i}: x, y e a inclinação. */
    private static void bonecoPos(int i, float t, float[] out) {
        out[0] = BONECOS[i][0] + sin(t * 0.8f + i * 2) * 14;
        out[1] = BONECOS[i][1] + sin(t * 1.1f + i) * 10;
        out[2] = sin(t * 1.2f + i) * 0.25f;
    }

    /** máscara {@code i}: x, y, seno (frente/trás) e ângulo. */
    private static void mascaraPos(int i, float t, float[] out) {
        float a = t * 0.5f + i * 1.2566f, sn = sin(a);
        out[0] = PCX + cos(a) * 255;
        out[1] = PCY + sn * 70 - 10 + sin(t + i) * 6;
        out[2] = sn;
        out[3] = a;
    }

    // ==================================================================== render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) {
            return;
        }
        float t = (System.nanoTime() - inicio) / 1e9f;
        float fim = DUR;
        if (pararEm >= 0) {
            fim = Math.min(fim, pararEm + 1.4f);
        }
        if (t >= fim) {
            inicio = -1;
            return;
        }
        float T = t % CICLO;

        int w = g.guiWidth(), h = g.guiHeight();
        float k = Math.min(w / PW, h / PH);
        float cx = w / 2f, cy = h / 2f;
        float env = ss(0f, 0.8f, T) * (1 - ss(14.4f, 16f, T));
        float nevoa = ss(0f, 1.5f, T) * (1 + 0.6f * ss(5.4f, 6.4f, T) + ss(14f, 15.5f, T) * 0.6f);
        float feixes = ss(6f, 7.2f, T);

        // ---- uma Sequência acabou de acender? estoura faíscas no nó dela
        if (T < ultimaT) {
            limparFx();
        }
        float[] np = new float[3];
        for (int i = 0; i < 10; i++) {
            int s = 9 - i;
            float quando = tn(s);
            if (ultimaT < quando && T >= quando) {
                noPos(i, np);
                estourar(np[0], np[1], s == 0 || s == 4 ? CRIM : s == 2 ? OURO : PALE, s == 0 ? 40 : 16);
            }
        }
        ultimaT = T;

        Buf b = AvisoRitual.comecar(g);

        // ---- céu + brilho violeta no centro
        AvisoRitual.degradeVertical(b, 0, 0, w, h, NOITE, 0x151B30, 20);
        AvisoRitual.halo(b, sx(cx, PCX, k), sy(cy, PCY, k), 40 * k, 420 * k, VIO, 0.16f * nevoa * env);

        pilares(b, T, t, k, cx, cy, env);
        raios(b, t, feixes, k, cx, cy, env);
        nevoa(b, false, T, nevoa, k, cx, cy, env);

        pendulo(b, T, t, k, cx, cy, env);
        escada(b, T, t, k, cx, cy, env);
        mascaras(b, T, t, false, k, cx, cy, env);
        carta(b, T, t, k, cx, cy, env);
        mascaras(b, T, t, true, k, cx, cy, env);
        bonecos(b, T, t, k, cx, cy, env);
        chamas(b, T, t, k, cx, cy, env);
        float[] c = new float[2];
        cartaY(T, t, c);
        fios(b, T, t, c[1], k, cx, cy, env);
        maosDeNevoa(b, T, t, k, cx, cy, env, w, h);

        faiscas(b, T, t, k, cx, cy, env, w, h);
        b.flush();

        // ---- os números e os nomes das Sequências (texto, fora do Buf)
        escadaTextos(g, T, t, k, cx, cy, env);

        // ---- névoa da frente
        b = AvisoRitual.comecar(g);
        nevoa(b, true, T, nevoa, k, cx, cy, env);
        b.flush();

        // ---- sussurros
        if (T > 7 && T < 13 && t - ultimoSussurro > 0.7f) {
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
            float a = sin((float) Math.PI * SUS_VIDA[i] / SUS_MAX[i]) * 0.28f * env;
            if (a <= 0.01f) {
                continue;
            }
            AvisoRitual.textoCentro(g, SUS_TXT[i], sx(cx, SUS_X[i], k), sy(cy, SUS_Y[i], k), k * 0.9f,
                    col(PALE, a), false);
        }

        titulo(g, T, t, k, cx, cy, env);
        falas(g, T, k, cx, cy, env);

        // ---- clarões, vinheta e grão
        Buf v = AvisoRitual.comecar(g);
        float clar = T > 5.9f ? Math.max(0, 1 - (T - 5.9f) / 0.6f) : 0;
        float rubro = T > 7f ? Math.max(0, 1 - (T - 7f) / 0.7f) : 0;
        if (!reduzido()) {
            if (clar > 0.01f) {
                v.rect(0, 0, w, h, col(PALE, 0.30f * clar * env));
            }
            if (rubro > 0.01f) {
                v.rect(0, 0, w, h, col(CRIM, 0.22f * rubro * env));
            }
        }
        float pulso = (float) Math.pow(Math.max(0, sin(t * 2.4f)), 2) * ss(6f, 7f, T);
        if (pulso > 0.01f) {
            AvisoRitual.anel(v, cx, cy, Math.min(w, h) * 0.42f, Math.min(w, h) * 0.98f, 0x080E14, 0f,
                    0.6f * pulso * env, 26);
        }
        v.flush();
        RenderSystem.enableDepthTest();
    }

    // ==================================================================== conversão canvas -> tela
    private static float sx(float cx, float x, float k) {
        return cx + (x - PCX) * k;
    }

    private static float sy(float cy, float y, float k) {
        return cy + (y - PH / 2f) * k;
    }

    // ==================================================================== cenário
    private static void pilares(Buf b, float T, float t, float k, float cx, float cy, float env) {
        if (T < tn(2)) {
            return;
        }
        float a = ss(tn(2), tn(2) + 0.8f, T) * (T < 14 ? 1 : Math.max(0, 1 - (T - 14) / 1f)) * env;
        if (a <= 0.01f) {
            return;
        }
        aditivo();
        for (int j = 0; j < 3; j++) {
            float x = PCX + (j - 1) * 170;
            float fl = 0.7f + 0.3f * rnd((int) Math.floor(t * 8) + j, 3);
            AvisoRitual.degradeVertical(b, sx(cx, x - 22, k), sy(cy, 0, k), 44 * k, 470 * k,
                    col(OURO, 0.20f * a * fl), col(OURO, 0f), 10);
        }
        normal();
    }

    private static void raios(Buf b, float t, float amt, float k, float cx, float cy, float env) {
        if (amt <= 0.01f) {
            return;
        }
        aditivo();
        float ox = sx(cx, PCX, k), oy = sy(cy, PCY, k);
        for (int i = 0; i < 16; i++) {
            float a = i * TAU / 16f + t * 0.05f;
            float x1 = ox + cos(a) * 430 * k, y1 = oy + sin(a) * 430 * k;
            float x2 = ox + cos(a + 0.035f) * 430 * k, y2 = oy + sin(a + 0.035f) * 430 * k;
            AvisoRitual.poligono(b, new float[]{ox, x1, x2}, new float[]{oy, y1, y2}, 3, col(PALE, 0.045f * amt * env));
        }
        normal();
    }

    private static void nevoa(Buf b, boolean frente, float T, float amt, float k, float cx, float cy, float env) {
        if (frente) {
            for (int i = 0; i < NPF; i++) {
                float px = ((PFX[i] + T * PFSP[i] * 14f) % (PW + 500)) - 250f;
                float py = PFY[i] + sin(T * 0.3f + PFPG[i]) * 22f;
                float a = PFA[i] * amt * env * 0.7f;
                if (a > 0.004f) {
                    AvisoRitual.halo(b, sx(cx, px, k), sy(cy, py, k), PFR[i] * k * 0.2f, PFR[i] * k, SILV, a * 1.6f);
                }
            }
        } else {
            for (int i = 0; i < NPB; i++) {
                float px = ((PBX[i] + T * PBSP[i] * 14f) % (PW + 500)) - 250f;
                float py = PBY[i] + sin(T * 0.3f + PBPH[i]) * 22f;
                float a = PBA[i] * amt * env;
                if (a > 0.004f) {
                    AvisoRitual.halo(b, sx(cx, px, k), sy(cy, py, k), PBR[i] * k * 0.2f, PBR[i] * k, SILV, a * 1.6f);
                }
            }
        }
    }

    // ==================================================================== 9 Vidente: o pêndulo
    private static void pendulo(Buf b, float T, float t, float k, float cx, float cy, float env) {
        if (T < tn(9)) {
            return;
        }
        float a = ss(tn(9), tn(9) + 0.6f, T) * env;
        float px = 110, L = 170;
        // rastro (a ponta do pêndulo em 7 instantes)
        for (int j = 6; j >= 0; j--) {
            float ang = sin((t - j * 0.04f) * 1.7f) * 0.55f;
            float ex = px + sin(ang) * L, ey = cos(ang) * L;
            if (j > 0) {
                b.circ(sx(cx, ex, k), sy(cy, ey, k), 9 * k, col(VIO, 0.05f * (7 - j) * a));
                continue;
            }
            b.seg(sx(cx, px, k), sy(cy, 0, k), sx(cx, ex, k), sy(cy, ey, k), 1.2f * k, col(SILV, 0.7f * a),
                    col(SILV, 0.7f * a));
            AvisoRitual.halo(b, sx(cx, ex, k), sy(cy, ey, k), 3 * k, 22 * k, VIO, 0.35f * a);
            // o corpo: losango claro
            float bx = sx(cx, ex, k), by = sy(cy, ey, k);
            AvisoRitual.poligono(b, new float[]{bx, bx + 8 * k, bx, bx - 8 * k},
                    new float[]{by - 12 * k, by + 2 * k, by + 22 * k, by + 2 * k}, 4, col(0xF2F0FF, a));
            if (Math.abs(cos(t * 1.7f)) < 0.12f) {
                b.seg(bx, by - 14 * k, bx, by - 4 * k, 2 * k, col(0xFFFFFF, a), col(0xFFFFFF, a));
                b.seg(bx - 6 * k, by - 9 * k, bx + 6 * k, by - 9 * k, 2 * k, col(0xFFFFFF, a), col(0xFFFFFF, a));
            }
        }
    }

    // ==================================================================== 8 Palhaço: os bonecos
    private static void bonecos(Buf b, float T, float t, float k, float cx, float cy, float env) {
        if (T < tn(8)) {
            return;
        }
        float a = ss(tn(8), tn(8) + 0.6f, T) * env * 0.9f;
        float[] d = new float[3];
        for (int i = 0; i < 4; i++) {
            bonecoPos(i, t, d);
            float bx = sx(cx, d[0], k), by = sy(cy, d[1], k);
            int cor = col(0xEFEEF5, a);
            b.circ(bx, by - 16 * k, 6 * k, cor);                                    // cabeça
            AvisoRitual.poligono(b, new float[]{bx - 6 * k, bx + 6 * k, bx + 9 * k, bx - 9 * k},
                    new float[]{by - 9 * k, by - 9 * k, by + 10 * k, by + 10 * k}, 4, cor);   // corpo
            b.seg(bx - 14 * k, by - 8 * k, bx + 14 * k, by - 8 * k, 3 * k, cor, cor);        // braços
            b.seg(bx - 6 * k, by + 10 * k, bx - 6 * k, by + 22 * k, 4 * k, cor, cor);        // pernas
            b.seg(bx + 6 * k, by + 10 * k, bx + 6 * k, by + 22 * k, 4 * k, cor, cor);
            // o sorriso vermelho e os olhos
            AvisoRitual.arco(b, bx, by - 19 * k, 3 * k, 0.2f, (float) Math.PI - 0.2f, 1.2f * k, col(0xC01830, a), 8);
            b.rect(bx - 2 * k, by - 18 * k, 1.5f * k, 1.5f * k, col(0x222222, a));
            b.rect(bx + 2 * k, by - 18 * k, 1.5f * k, 1.5f * k, col(0x222222, a));
        }
    }

    // ==================================================================== 7 Mago: as chamas
    private static void chamas(Buf b, float T, float t, float k, float cx, float cy, float env) {
        if (T < tn(7)) {
            return;
        }
        float a = ss(tn(7), tn(7) + 0.6f, T) * env;
        aditivo();
        for (int sx0 : new int[]{60, 740}) {
            for (int j = 0; j < 3; j++) {
                float bx = sx0 + (j - 1) * 22;
                float by = 545 - (j % 2) * 8;
                float s = 1 + (0.2f * j) % 2;
                chama(b, sx(cx, bx, k), sy(cy, by, k), s * k, t, j + sx0, a);
            }
        }
        normal();
    }

    private static void chama(Buf b, float x, float y, float s, float t, float ph, float a) {
        float h = 1 + 0.25f * sin(t * 9 + ph);
        float sw = sin(t * 5 + ph) * 3 * s;
        float topo = y - 34 * s * h;
        // três camadas: vermelho embaixo, laranja no meio, ponta clara
        AvisoRitual.poligono(b, new float[]{x - 8 * s, x - 5 * s, x + sw, x + 5 * s + sw * 0.6f, x + 8 * s},
                new float[]{y, y - 14 * s, topo, y - 16 * s, y}, 5, col(0xDC2832, 0.55f * a));
        AvisoRitual.poligono(b, new float[]{x - 5 * s, x + sw * 0.8f, x + 4 * s},
                new float[]{y, y - 22 * s * h, y}, 3, col(0xFF9640, 0.55f * a));
        AvisoRitual.poligono(b, new float[]{x - 2 * s, x + sw * 0.4f, x + 2 * s},
                new float[]{y - 2 * s, y - 30 * s * h, y - 4 * s}, 3, col(0xFFE6A0, 0.5f * a));
    }

    // ==================================================================== 6 Sem Rosto: as máscaras
    private static void mascaras(Buf b, float T, float t, boolean frente, float k, float cx, float cy, float env) {
        if (T < tn(6)) {
            return;
        }
        float a0 = ss(tn(6), tn(6) + 0.7f, T) * env;
        float[] m = new float[4];
        for (int i = 0; i < 5; i++) {
            mascaraPos(i, t, m);
            if ((m[2] >= 0) != frente) {
                continue;
            }
            float d = (m[2] + 1) / 2f, s = (0.75f + 0.3f * d) * k;
            float a = a0 * (0.55f + 0.45f * d);
            float mx = sx(cx, m[0], k), my = sy(cy, m[1], k);
            b.elipse(mx, my, 1, 0, 15 * s, 21 * s, col(0xE8E6F4, a));
            b.elipse(mx, my, 1, 0, 15 * s, 21 * s, col(0x282840, a * 0.10f));
            // os olhos (amêndoas que abrem e fecham)
            float abr = 0.5f + 0.5f * sin(t * 1.3f + i);
            float ox = sin(t * 0.7f + i) * 2;
            olho(b, mx + (-6 + ox) * s, my - 3 * s, 4 * s, (2.5f * abr + 0.4f) * s, col(0x15152A, a));
            olho(b, mx + (6 + ox) * s, my - 3 * s, 4 * s, (2.5f * abr + 0.4f) * s, col(0x15152A, a));
        }
    }

    /** amêndoa (losango achatado) — os olhos das máscaras. */
    private static void olho(Buf b, float x, float y, float w, float h, int cor) {
        AvisoRitual.poligono(b, new float[]{x - w, x, x + w, x}, new float[]{y, y - h, y, y + h}, 4, cor);
    }

    // ==================================================================== 5 Marionetista: os fios
    private static void fios(Buf b, float T, float t, float yCarta, float k, float cx, float cy, float env) {
        if (T < tn(5)) {
            return;
        }
        float a = ss(tn(5), tn(5) + 0.8f, T) * env;
        int n = 2;
        if (T >= tn(6)) {
            n += 5;
        }
        if (T >= tn(8)) {
            n += 4;
        }
        for (int i = 0; i < n; i++) {
            float x, y;
            if (i < 2) {
                x = PCX + (i == 0 ? -40 : 40);
                y = yCarta - 95;
            } else if (i < 7) {
                float[] m = new float[4];
                mascaraPos(i - 2, t, m);
                x = m[0];
                y = m[1] - 22;
            } else {
                float[] d = new float[3];
                bonecoPos(i - 7, t, d);
                x = d[0];
                y = d[1] - 24;
            }
            float sw = sin(t * 1.3f + i * 1.7f) * 12;
            // a curva do fio: (x+sw*.3, -5) -> controle (x+sw, y*.5) -> (x, y)
            float ax = sx(cx, x + sw * 0.3f, k), ay = sy(cy, -5, k);
            float c1x = sx(cx, x + sw, k), c1y = sy(cy, y * 0.5f, k);
            float ex = sx(cx, x, k), ey = sy(cy, y, k);
            float px = ax, py = ay;
            for (int j = 1; j <= 12; j++) {
                float u = j / 12f;
                float iu = 1 - u;
                float nx = iu * iu * ax + 2 * iu * u * c1x + u * u * ex;
                float ny = iu * iu * ay + 2 * iu * u * c1y + u * u * ey;
                b.seg(px, py, nx, ny, 1f * k, col(PALE, 0.35f * a), col(PALE, 0.35f * a));
                px = nx;
                py = ny;
            }
        }
    }

    // ==================================================================== 1 Atendente: as mãos de névoa
    private static void maosDeNevoa(Buf b, float T, float t, float k, float cx, float cy, float env, int w, int h) {
        if (T < tn(1)) {
            return;
        }
        float g0 = ss(tn(1), tn(1) + 1.4f, T) * (T < 14 ? 1 : Math.max(0, 1 - (T - 14) / 1.2f)) * env;
        if (g0 <= 0.01f) {
            return;
        }
        float[][] bases = {{30, PH}, {PW - 30, PH}, {130, PH + 20}, {PW - 130, PH + 20}};
        float[] px = new float[8], py = new float[8];
        for (int i = 0; i < 4; i++) {
            float bx = bases[i][0], by = bases[i][1];
            float dir = bx < PCX ? 1 : -1;
            float tx = PCX + dir * -(90 + (i % 2) * 40), ty = PCY + 90;
            float ex = bx + (tx - bx) * g0, ey = by + (ty - by) * g0;
            for (int j = 0; j <= 7; j++) {
                float f = j / 7f;
                float jj = sin(t * 1.5f + i + j) * 10 * sin((float) Math.PI * f);
                px[j] = bx + (ex - bx) * f + jj;
                py[j] = by + (ey - by) * f - sin((float) Math.PI * f) * 30;
            }
            for (int j = 1; j <= 7; j++) {
                float lw = (1 - j / 7f) * 18 + 3;
                b.seg(sx(cx, px[j - 1], k), sy(cy, py[j - 1], k), sx(cx, px[j], k), sy(cy, py[j], k), lw * k,
                        col(SILV, 0.18f * g0), col(SILV, 0.18f * g0));
            }
            // os cinco dedos
            float ang0 = (float) Math.atan2(ey - by, ex - bx);
            for (int f = 0; f < 5; f++) {
                float an = ang0 + (f - 2) * 0.4f + sin(t * 2 + f + i) * 0.15f;
                b.seg(sx(cx, px[7], k), sy(cy, py[7], k),
                        sx(cx, px[7] + cos(an) * 24 * g0, k), sy(cy, py[7] + sin(an) * 24 * g0, k),
                        3 * k, col(SILV, 0.30f * g0), col(SILV, 0.30f * g0));
            }
        }
    }

    // ==================================================================== a escada das Sequências
    private static void escada(Buf b, float T, float t, float k, float cx, float cy, float env) {
        float dp = ss(0.3f, 1.5f, T) * env;
        if (dp <= 0.01f) {
            return;
        }
        float ox = sx(cx, PCX, k), oy = sy(cy, PCY, k);
        float rot = t * 0.1f;

        // 22 caminhos ligados (estrela)
        for (int i = 0; i < 22; i++) {
            float a1 = i / 22f * TAU + rot, a2 = ((i + 9) % 22) / 22f * TAU + rot;
            b.seg(ox + cos(a1) * 175 * k, oy + sin(a1) * 175 * k, ox + cos(a2) * 175 * k, oy + sin(a2) * 175 * k,
                    1f, col(SILV, 0.14f * dp), col(SILV, 0.14f * dp));
        }
        // três anéis tracejados que se desenham do topo
        anelTracejado(b, ox, oy, 190 * k, dp, col(SILV, 0.35f), 2 * k);
        anelTracejado(b, ox, oy, 160 * k, dp, col(SILV, 0.35f), 1 * k);
        anelTracejado(b, ox, oy, 130 * k, dp, col(SILV, 0.35f), 1.5f * k);

        // quantas Sequências já acenderam
        int acesas = 0;
        for (int s = 9; s >= 0; s--) {
            if (T >= tn(s)) {
                acesas++;
            }
        }
        if (acesas > 1) {
            aditivo();
            float a0 = (float) Math.toRadians(-90 + 36), a1 = a0 - (float) Math.toRadians((acesas - 1) * 36.0);
            AvisoRitual.arco(b, ox, oy, 190 * k, a0, a1, 2.5f * k, col(PALE, 0.75f * dp), 40);
            normal();
        }

        // 4 Feiticeiro Bizarro: o anel de maldição
        if (T >= tn(4)) {
            float a = ss(tn(4), tn(4) + 0.6f, T) * env;
            anelTracejado(b, ox, oy, 146 * k, 1f, col(CRIM, 0.8f * a), 1.5f * k);
            for (int i = 0; i < 12; i++) {
                float an = i / 12f * TAU - t * 0.4f;
                float px = ox + cos(an) * 146 * k, py = oy + sin(an) * 146 * k;
                b.seg(px - 3 * k, py - 3 * k, px + 3 * k, py + 3 * k, 1.5f * k, col(0xFF465A, 0.8f * a),
                        col(0xFF465A, 0.8f * a));
                b.seg(px + 3 * k, py - 3 * k, px - 3 * k, py + 3 * k, 1.5f * k, col(0xFF465A, 0.8f * a),
                        col(0xFF465A, 0.8f * a));
            }
        }

        // 3 Estudioso do Passado: o relógio ao contrário
        if (T >= tn(3)) {
            float a = ss(tn(3), tn(3) + 0.6f, T) * env;
            b.seg(ox, oy, ox + cos(-t * 0.6f - 1.57f) * 118 * k, oy + sin(-t * 0.6f - 1.57f) * 118 * k, 2 * k,
                    col(PALE, 0.55f * a), col(PALE, 0.55f * a));
            b.seg(ox, oy, ox + cos(-t * 3.6f - 1.57f) * 88 * k, oy + sin(-t * 3.6f - 1.57f) * 88 * k, 1.2f * k,
                    col(PALE, 0.55f * a), col(PALE, 0.55f * a));
        }

        // os dez nós
        float[] np = new float[3];
        for (int i = 0; i < 10; i++) {
            int s = 9 - i;
            noPos(i, np);
            boolean acesso = T >= tn(s), ultimo = s == 0;
            float idade = T - tn(s), brilho = acesso ? (float) Math.exp(-idade * 2) : 0;
            float nx = sx(cx, np[0], k), ny = sy(cy, np[1], k);
            aditivo();
            AvisoRitual.halo(b, nx, ny, 4 * k, 32 * k, ultimo ? CRIM : PALE,
                    (0.5f * brilho + (acesso ? 0.18f : 0.03f)) * dp);
            normal();
            b.circ(nx, ny, 11 * k, col(acesso ? PALE : 0x0C0F1A, acesso ? 0.95f : dp));
            AvisoRitual.arco(b, nx, ny, 11 * k, 0f, TAU, acesso && ultimo ? 3 * k : 1.5f * k,
                    col(acesso ? (ultimo ? CRIM : SILV) : SILV, acesso ? 1f : 0.45f * dp), 24);
        }
    }

    /** anel tracejado (o setLineDashes do protótipo): 4° de traço, 4° de vão. */
    private static void anelTracejado(Buf b, float cx, float cy, float r, float dp, int cor, float w) {
        float visivel = TAU * dp;
        float passo = (float) Math.toRadians(8);
        int n = (int) (visivel / passo) + 1;
        for (int i = 0; i < n; i++) {
            float a0 = i * passo, a1 = Math.min(visivel, a0 + passo * 0.5f);
            if (a1 <= a0) {
                continue;
            }
            AvisoRitual.arco(b, cx, cy, r, a0, a1, w, cor, 3);
        }
    }

    /** números e nomes das Sequências (precisa do GuiGraphics). */
    private static void escadaTextos(GuiGraphics g, float T, float t, float k, float cx, float cy, float env) {
        float dp = ss(0.3f, 1.5f, T) * env;
        if (dp <= 0.01f) {
            return;
        }
        float[] np = new float[3];
        for (int i = 0; i < 10; i++) {
            int s = 9 - i;
            noPos(i, np);
            boolean acesso = T >= tn(s);
            float idade = T - tn(s);
            float nx = sx(cx, np[0], k), ny = sy(cy, np[1], k);
            // o número dentro do nó
            AvisoRitual.textoCentro(g, String.valueOf(s), nx, ny - 4.5f * k, k * 1.15f,
                    acesso ? col(0x14182A, 0.95f) : col(PALE, 0.5f * dp), false);
            // o nome do lado de fora
            float cs = cos(np[2]);
            float lx = nx + cs * 26 * k, ly = ny + sin(np[2]) * 26 * k - 4 * k;
            String nome = NOMES[i];
            float larg = net.minecraft.client.Minecraft.getInstance().font.width(nome) * k * 0.85f;
            float alinhado = cs > 0.35f ? lx : cs < -0.35f ? lx - larg : lx - larg / 2f;
            int cor = acesso ? col(PALE, ss(0f, 0.4f, Math.max(0, idade)) * 0.9f) : col(SILV, 0.18f * dp);
            AvisoRitual.texto(g, nome, alinhado, ly, k * 0.85f, cor, false);
        }
    }

    // ==================================================================== a carta
    private static void cartaY(float T, float t, float[] out) {
        float sobe = ss(1f, 2.4f, T);
        out[0] = sobe;
        out[1] = PCY + (1 - sobe) * 140 + sin(t * 1.4f) * 5;
    }

    private static void carta(Buf b, float T, float t, float k, float cx, float cy, float env) {
        float[] c = new float[2];
        cartaY(T, t, c);
        float sobe = c[0], y = c[1];
        if (sobe <= 0.01f) {
            return;
        }
        float a = sobe * env;

        // 3 Estudioso do Passado: os ecos da carta
        if (T >= tn(3)) {
            float ae = ss(tn(3), tn(3) + 0.6f, T) * env;
            for (int j = 1; j <= 3; j++) {
                float ex = sx(cx, PCX + sin(t * 1.5f + j) * 14 * j, k);
                moldura(b, ex, sy(cy, y, k), 60 * k, 95 * k, 1.5f * k, col(PALE, 0.22f / j * ae));
            }
        }

        float vira = ss(5.9f, 6.8f, T);
        float ang = (float) Math.PI * vira;
        float esc = Math.max(0.02f, Math.abs((float) Math.cos(ang)));
        boolean frente = ang > 1.5708f;
        float px = sx(cx, PCX, k), py = sy(cy, y, k);
        float hw = 60 * k * esc, hh = 95 * k;

        AvisoRitual.halo(b, px, py, 20 * k, 110 * k, VIO, 0.12f * a);
        b.rect(px - hw, py - hh, hw * 2, hh * 2, col(frente ? 0xB8B4C8 : 0x0D1020, a));
        moldura(b, px, py, hw, hh, 3 * k * esc, col(frente ? 0x2C2A46 : 0xB9B4DC, 0.9f * a));
        moldura(b, px, py, hw * 0.88f, hh * 0.88f, 1 * k * esc, col(frente ? 0x2C2A46 : 0xB9B4DC, 0.5f * a));

        if (!frente) {
            // verso: o círculo dos dez pontos e o "?"
            AvisoRitual.arco(b, px, py, 30 * k * esc, 0f, TAU, 1.5f * k * esc, col(0xB9B4DC, 0.7f * a), 26);
            float apagados = Math.max(0, (float) Math.floor((T - 1.4f) / 0.5f));
            for (int j = 0; j < 10; j++) {
                float an = j / 10f * TAU - t * 0.4f;
                boolean aceso = j >= 10 - apagados;
                b.circ(px + cos(an) * 30 * k * esc, py + sin(an) * 30 * k * esc, 3 * k * esc,
                        col(aceso ? 0xE8E6F6 : 0xB9B4DC, aceso ? a : 0.3f * a));
            }
            // o "?" (três traços)
            AvisoRitual.arco(b, px, py - 12 * k, 7 * k * esc, 3.6f, 6.0f, 2.4f * k * esc, col(0xB9B4DC, 0.9f * a), 10);
            b.seg(px, py - 6 * k, px, py + 2 * k, 2.4f * k * esc, col(0xB9B4DC, 0.9f * a), col(0xB9B4DC, 0.9f * a));
            b.circ(px, py + 8 * k, 1.6f * k * esc, col(0xB9B4DC, 0.9f * a));
        } else {
            // face: o caminhante de costas sob a lua vermelha
            AvisoRitual.degradeVertical(b, px - hw * 0.75f, py - hh * 0.55f, hw * 1.5f, hh * 1.1f, 0x2A2D4A, 0x8F93B5, 8);
            AvisoRitual.halo(b, px + 24 * k * esc, py - 30 * k, 8 * k * esc, 30 * k * esc, 0xBE1E28, 0.35f * a);
            b.circ(px + 11 * k * esc, py - 43 * k, 13 * k * esc, col(0xB3261E, 0.9f * a));
            b.rect(px - hw * 0.75f, py + 30 * k, hw * 1.5f, 24 * k, col(0x1C1A2C, a));
            b.rect(px - 8 * k * esc, py - 22 * k, 16 * k * esc, 18 * k, col(0x0C0B14, a));
            AvisoRitual.poligono(b, new float[]{px - 12 * k * esc, px + 12 * k * esc, px + 17 * k * esc, px - 17 * k * esc},
                    new float[]{py + 8 * k, py + 8 * k, py + 34 * k, py + 34 * k}, 4, col(0x0C0B14, a));
            b.seg(px + 24 * k * esc, py + 10 * k, px + 28 * k * esc, py + 34 * k, 2 * k * esc, col(0x0C0B14, a),
                    col(0x0C0B14, a));
        }
    }

    private static void moldura(Buf b, float cx, float cy, float hw, float hh, float w, int c) {
        b.seg(cx - hw, cy - hh, cx + hw, cy - hh, w, c, c);
        b.seg(cx + hw, cy - hh, cx + hw, cy + hh, w, c, c);
        b.seg(cx + hw, cy + hh, cx - hw, cy + hh, w, c, c);
        b.seg(cx - hw, cy + hh, cx - hw, cy - hh, w, c, c);
    }

    // ==================================================================== faíscas e ondas
    private static void estourar(float x, float y, int cor, int quantos) {
        for (int i = 0; i < quantos; i++) {
            int idx = fxProx++ % NFX;
            float an = R.nextFloat() * TAU, s = 0.6f + R.nextFloat() * 2;
            FXP[idx] = x;
            FYP[idx] = y;
            FVX[idx] = cos(an) * s;
            FVY[idx] = sin(an) * s;
            FMAX[idx] = 30 + R.nextFloat() * 40;
            FS[idx] = 1 + R.nextFloat() * 1.8f;
            FCOR[idx] = cor;
            FVIDA[idx] = 0;
            FVIVA[idx] = true;
        }
        int r = rpProx++ % NRP;
        RPX[r] = x;
        RPY[r] = y;
        RPCOR[r] = cor;
        RPVIDA[r] = 0;
        RPVIVA[r] = true;
    }

    private static void limparFx() {
        for (int i = 0; i < NFX; i++) {
            FVIVA[i] = false;
        }
        for (int i = 0; i < NRP; i++) {
            RPVIVA[i] = false;
        }
    }

    private static void faiscas(Buf b, float T, float t, float k, float cx, float cy, float env, int w, int h) {
        // os geradores de faísca do protótipo (maldição, milagres, chamas)
        if (T >= tn(4) && R.nextFloat() < 0.5f) {
            addFx(PCX + (R.nextFloat() - 0.5f) * 260, PCY + 150, (R.nextFloat() - 0.5f) * 0.3f,
                    -(0.3f + R.nextFloat() * 0.5f), 60 + R.nextFloat() * 60, 1.5f + R.nextFloat() * 2, CRIM);
        }
        if (T >= tn(2) && R.nextFloat() < 0.6f) {
            addFx(PCX + (R.nextInt(3) - 1) * 170 + (R.nextFloat() - 0.5f) * 40, R.nextFloat() * 400,
                    (R.nextFloat() - 0.5f) * 0.2f, 0.3f + R.nextFloat() * 0.4f, 60 + R.nextFloat() * 50,
                    1 + R.nextFloat() * 1.6f, OURO);
        }
        if (T >= tn(7) && R.nextFloat() < 0.5f) {
            float sx0 = R.nextBoolean() ? 60 : 740;
            addFx(sx0 + (R.nextFloat() - 0.5f) * 50, 520, (R.nextFloat() - 0.5f) * 0.4f,
                    -(0.6f + R.nextFloat() * 0.8f), 30 + R.nextFloat() * 30, 1 + R.nextFloat() * 1.5f, LARANJA);
        }

        aditivo();
        for (int i = 0; i < NRP; i++) {
            if (!RPVIVA[i]) {
                continue;
            }
            RPVIDA[i]++;
            if (RPVIDA[i] > 36) {
                RPVIVA[i] = false;
                continue;
            }
            float f = RPVIDA[i] / 36f;
            float rr = (6 + f * 55) * k;
            AvisoRitual.arco(b, sx(cx, RPX[i], k), sy(cy, RPY[i], k), rr, 0f, TAU, 2 * k, col(RPCOR[i], (1 - f) * 0.6f), 24);
        }
        for (int i = 0; i < NFX; i++) {
            if (!FVIVA[i]) {
                continue;
            }
            FXP[i] += FVX[i];
            FYP[i] += FVY[i];
            FVX[i] *= 0.99f;
            FVIDA[i]++;
            if (FVIDA[i] > FMAX[i]) {
                FVIVA[i] = false;
                continue;
            }
            float a = sin((float) Math.PI * FVIDA[i] / FMAX[i]) * 0.85f * env;
            if (a <= 0.01f) {
                continue;
            }
            float s = FS[i] * k;
            b.rect(sx(cx, FXP[i], k), sy(cy, FYP[i], k), s, s, col(FCOR[i], a));
        }
        normal();
    }

    private static void addFx(float x, float y, float vx, float vy, float max, float s, int cor) {
        int i = fxProx++ % NFX;
        FXP[i] = x;
        FYP[i] = y;
        FVX[i] = vx;
        FVY[i] = vy;
        FMAX[i] = max;
        FS[i] = s;
        FCOR[i] = cor;
        FVIDA[i] = 0;
        FVIVA[i] = true;
    }

    // ==================================================================== texto
    private static void titulo(GuiGraphics g, float T, float t, float k, float cx, float cy, float env) {
        float T0 = 7f;
        if (T < T0) {
            return;
        }
        String palavra = "AVISO";
        float esc = k * 6.2f;
        float espaco = 14 * k;
        var fonte = net.minecraft.client.Minecraft.getInstance().font;
        float total = 0;
        for (int i = 0; i < palavra.length(); i++) {
            total += fonte.width(String.valueOf(palavra.charAt(i))) * esc;
        }
        total += espaco * (palavra.length() - 1);
        float x = cx - total / 2f;
        float baseY = sy(cy, 515, k);
        float ruido = rnd((int) Math.floor(t * 14), 5) > 0.94f ? (R.nextFloat() - 0.5f) * 10 * k : 0;

        for (int i = 0; i < palavra.length(); i++) {
            String ch = String.valueOf(palavra.charAt(i));
            float a = ss(T0 + i * 0.18f, T0 + i * 0.18f + 0.45f, T) * env;
            float larg = fonte.width(ch) * esc;
            float yy = baseY + (1 - a) * 18 * k + sin(t * 2 + i * 1.3f) * 3 * k;
            if (a > 0.01f) {
                float cxl = x + larg / 2f + ruido;
                AvisoRitual.texto(g, ch, x + ruido, yy, esc, col(PALE, a), true);
                // o fio da marionete descendo até a letra
                Buf b = AvisoRitual.comecar(g);
                b.seg(cxl, sy(cy, -5, k), cxl, yy - 42 * k, 1f * k, col(PALE, 0.25f * a), col(PALE, 0.25f * a));
                b.flush();
            }
            x += larg + espaco;
        }
        float u = ss(T0 + 0.9f, T0 + 1.7f, T) * env;
        if (u > 0.01f) {
            Buf b = AvisoRitual.comecar(g);
            float ly = sy(cy, 527, k);
            b.rect(cx - (total / 2f * u + 10 * k), ly, total * u + 20 * k, 1.5f * k, col(SILV, 0.8f * u));
            AvisoRitual.poligono(b, new float[]{cx, cx + 5 * k, cx, cx - 5 * k},
                    new float[]{ly - 5 * k, ly, ly + 5 * k, ly}, 4, col(PALE, u));
            b.flush();
        }
    }

    private static void falas(GuiGraphics g, float T, float k, float cx, float cy, float env) {
        digitado(g, L1, 8.2f, 556, T, k, cx, cy, col(0xB9B6D2, env), 0.92f);
        digitado(g, msg, 9.5f, 578, T, k, cx, cy, col(0xF2F0FC, env), 1.06f);
        digitado(g, L3, 11.0f, 596, T, k, cx, cy, col(0xE0463C, env), 0.80f);
    }

    private static void digitado(GuiGraphics g, String txt, float inicioT, float yProt, float T, float k, float cx,
            float cy, int cor, float escala) {
        // (5.16.4) sem texto: nada de fala inventada
        if (txt == null || txt.isEmpty()) {
            return;
        }
        int n = (int) Math.floor((T - inicioT) * 30);
        if (n <= 0 || T < inicioT) {
            return;
        }
        String s = txt.substring(0, Math.min(txt.length(), n));
        boolean cursor = n < txt.length() && ((int) Math.floor(T * 6)) % 2 == 1;
        AvisoRitual.textoCentro(g, s + (cursor ? "_" : ""), cx, sy(cy, yProt, k), k * 1.42f * escala, cor, true);
    }
}
