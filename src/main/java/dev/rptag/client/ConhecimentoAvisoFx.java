package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * AVISO CONHECIMENTO — estilo 16 do /rp admin avisar (5.10.0).
 *
 * <p>Porte do protótipo NOVO do dono ({@code AvisoConhecimento}): o Conhecimento
 * de Ordem Paranormal — calmo e geométrico, não caótico. Um sigilo preciso
 * (estrela de seis pontas, quadrados, bandas de runas), um olho dourado que
 * observa, QUATRO MEMÓRIAS penduradas em cordas no anel, uma balança entre
 * SANIDADE e CONHECIMENTO e o contador de VERDADES. Sussurros nas laterais e
 * runas subindo/descendo nas quatro colunas das bordas.
 *
 * <h2>Roteiro (segundos)</h2>
 * <pre>
 *   0,0   preto abre; fundo âmbar escuro no centro
 *   0,3   fios radiais, anéis 172/178/146 e a banda de 48 runas
 *   0,8   estrela de seis pontas + quadrados (a ordem geométrica)
 *   1,0   as 4 colunas de runas das bordas sobem
 *   1,3   anéis internos 92/72 + banda de 30 runas
 *   1,4   o OLHO abre (pisca a cada 7,5 s) e olha devagar
 *   2,0   as cordas crescem do anel até os alfinetes
 *   2,4   as 4 memórias aparecem (foto lavada + runa em cima)
 *   2,8   a BALANÇA SANIDADE × CONHECIMENTO
 *   3,0   o contador VERDADES
 *   3,4   o texto grande letra por letra
 *   4,3   memória 1: a barra enche e a VERDADE aparece (e assim 2, 3, 4)
 *  10,0   com as 4 reveladas: onda, clarão e o texto troca (STAGE1)
 *  15,6   escurece
 * </pre>
 *
 * <p>No jogo não tem mouse: o protótipo revela a memória quando o cursor passa
 * por cima — aqui a revelação é por TEMPO. Um segundo antes de cada verdade a
 * barra de progresso enche sozinha (e o olho olha pra memória que está sendo
 * revelada), então a verdade "se abre" na frente do jogador.
 *
 * <p>Regra de ouro do mod: qualquer erro DESLIGA o aviso, nunca o jogo.
 */
public final class ConhecimentoAvisoFx {

    private ConhecimentoAvisoFx() {
    }

    // ------------------------------------------------------------- constantes
    /** espaço de desenho do protótipo (o render escala pra tela do jogador). */
    private static final float DW = 960f, DH = 640f;
    private static final float DCX = DW / 2f, DCY = 300f;
    private static final float CPS = 22f;

    private static final int OURO = 0xFFD046;
    private static final int AMBAR = 0xEB9619;
    private static final int PALIDO = 0xFFF6C8;
    private static final int BRANCO = 0xFFFCF0;

    private static final float DUR = 17.0f;
    private static final float T_TEXTO = 3.4f;          // o texto principal começa a digitar
    private static final float T_CORDAS = 2.0f;         // as cordas crescem
    private static final float T_MEMORIA = 2.4f;        // as molduras das memórias
    private static final float T_REVELA = 4.3f;         // a 1ª verdade se abre
    private static final float REVELA_PASSO = 1.9f;     // uma verdade a cada 1,9 s
    private static final float ENCHE = 1.1f;            // quanto tempo a barra leva pra encher

    private static final String TITULO = "AVISO";
    private static final String[] FRASES0 = {
            "Uma verdade foi revelada. Ela não pode ser esquecida.",
            "Quanto mais você sabe, menos você consegue voltar a ser quem era."
    };
    private static final String[] FRASES1 = {
            "Agora você sabe.",
            "E não pode deixar de saber."
    };
    /** o que está escrito em cada memória depois de revelada (2 linhas). */
    private static final String[][] VERDADES = {
            {"Você já esteve", "aqui antes."},
            {"Seu nome não foi", "escolha sua."},
            {"Alguém lembra de você.", "Você não lembra."},
            {"As vozes sempre", "foram uma só."}
    };
    private static final String[] SUSSURROS_TEXTO = {
            "tudo tem uma regra", "a verdade tem um preço", "eu lembro de você", "você sonhou isso",
            "sombras também lembram", "tudo está conectado", "equilíbrio", "olhe de novo",
            "pergunte, eu respondo", "a ordem existe"
    };

    /** posição das 4 memórias penduradas (igual ao protótipo). */
    private static final float[] MEM_X = {170f, 790f, 128f, 832f};
    private static final float[] MEM_Y = {212f, 196f, 372f, 366f};
    private static final float[] MEM_GIRO = {-4f, 3f, 3f, -3f};
    private static final float MEM_W = 132f, MEM_H = 96f;

    private static final int N_POEIRA = 70;
    private static final int N_FIBRAS = 44;

    // ---------------------------------------------------------------- estado
    private static long inicio = -1L;
    private static float tAnt;
    private static String msg = "CONHECIMENTO";
    private static float flash, pulso;
    private static float proxSussurro = 4.5f;
    private static float finalT = -1f;
    private static float textoBase = T_TEXTO;
    private static boolean etapa1;
    private static float olharX, olharY;

    private static final boolean[] REVELADA = new boolean[4];
    private static final float[] PROG = new float[4];
    private static final float[] REVEL_T = {-1f, -1f, -1f, -1f};
    private static int nRev;

    /** alfabeto de runas: cada runa é uma polilinha em grade 3x4 (semente 21). */
    private static final float[][][] RUNAS = new float[28][][];

    private static final float[] POE_X = new float[N_POEIRA];
    private static final float[] POE_Y = new float[N_POEIRA];
    private static final float[] POE_V = new float[N_POEIRA];
    private static final float[] POE_F = new float[N_POEIRA];

    private static final List<Sussurro> SUSSURROS = new ArrayList<>();
    private static final List<Onda> ONDAS = new ArrayList<>();
    private static final Random SORTE = new Random();

    private static final class Sussurro {
        String s;
        float x, y, idade, vida;
    }

    private static final class Onda {
        float x, y, idade;
        boolean forte;
    }

    static {
        // as runas não mudam entre avisos: alfabeto fixo, semente 21
        Random r = new Random(21);
        for (int i = 0; i < RUNAS.length; i++) {
            int n = 3 + r.nextInt(3);
            float[][] segs = new float[n][4];
            float px = r.nextInt(3) / 2f, py = r.nextInt(4) / 3f * 1.5f;
            for (int k = 0; k < n; k++) {
                float nx, ny;
                do {
                    nx = r.nextInt(3) / 2f;
                    ny = r.nextInt(4) / 3f * 1.5f;
                } while (nx == px && ny == py);
                segs[k][0] = px;
                segs[k][1] = py;
                segs[k][2] = nx;
                segs[k][3] = ny;
                px = nx;
                py = ny;
            }
            RUNAS[i] = segs;
        }
        for (int i = 0; i < N_POEIRA; i++) {
            POE_X[i] = r.nextFloat() * DW;
            POE_Y[i] = r.nextFloat() * DH;
            POE_V[i] = 5f + r.nextFloat() * 16f;
            POE_F[i] = r.nextFloat() * 6.28f;
        }
    }

    // ------------------------------------------------------------- ciclo de vida
    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_conhecimento"),
                ConhecimentoAvisoFx::render);
    }

    public static void iniciar(String mensagem) {
        msg = (mensagem == null || mensagem.isBlank()) ? "CONHECIMENTO"
                : mensagem.replaceAll("\u00a7.", "").toUpperCase(java.util.Locale.ROOT);
        inicio = System.nanoTime();
        tAnt = 0f;
        flash = pulso = 0f;
        proxSussurro = 4.5f;
        finalT = -1f;
        textoBase = T_TEXTO;
        etapa1 = false;
        olharX = olharY = 0f;
        nRev = 0;
        for (int i = 0; i < 4; i++) {
            REVELADA[i] = false;
            PROG[i] = 0f;
            REVEL_T[i] = -1f;
        }
        SUSSURROS.clear();
        ONDAS.clear();
        // o som do protótipo antigo continua (o dono gostou do sino)
        try {
            tocar(dev.rptag.RPTagMod.SOM_CONHECIMENTO.get(), 1.0f, 1.0f);
        } catch (Throwable ignorado) {
            // sem som, o aviso continua
        }
    }

    public static void parar() {
        inicio = -1L;
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

            Minecraft mc = Minecraft.getInstance();
            Font f = mc.font;
            int w = g.guiWidth();
            int h = g.guiHeight();
            float s = Math.min(w / DW, h / DH);
            float ox = w / 2f - DCX * s;
            float oy = h / 2f - DCY * s;
            float cx = w / 2f;
            float cy = oy + DCY * s;

            atualizar(t, passo);

            float entra = ss(0.05f, 0.6f, t);
            float sai = 1f - ss(DUR - 1.4f, DUR - 0.2f, t);
            float env = entra * sai;
            float boost = finalT > 0f ? eo((t - finalT) / 1.2f) : 0f;

            preparar(g);
            Buf b = new Buf(g.pose().last().pose());

            fundo(b, w, h, cx, cy, s, t, env);
            scripts(b, ox, oy, s, w, h, t, env);
            sigilo(b, cx, cy, s, t, env, pulso);
            cordas(b, cx, cy, s, t, env);
            for (int i = 0; i < 4; i++) {
                memoria(b, i, ox + MEM_X[i] * s, oy + MEM_Y[i] * s, s, t, env);
            }
            olho(b, cx, cy + 0f, 68f * s + 8f * s * boost, 36f * s + 6f * s * boost,
                    t, env, boost, s);
            poeira(b, ox, oy, s, t, env, h);
            ondas(b, ox, oy, s, t, env);
            balanca(b, ox, oy, s, t, env);
            contador(b, ox, oy, s, t, env);
            icone(b, cx, oy + 62f * s, 38f * s, s, t, env);
            faixaBaixo(b, w, h, s, t, env);
            b.vinheta(w, h, Math.max(60f, 130f * s), 0x000000, 0.62f * env);
            b.flush();
            normal();

            // ---------------- textos (por cima da geometria)
            tituloAVISO(g, f, cx, oy + 124f * s - 14f, s, t, env);
            sussurros(g, f, ox, oy, s, t, env);
            verdades(g, f, ox, oy, s, t, env);
            textoGrande(g, f, w, h, s, t, env);
            dica(g, f, w, h, s, t, env);

            // ---------------- clarão das revelações e escurecer
            preparar(g);
            Buf b2 = new Buf(g.pose().last().pose());
            if (flash > 0.01f) {
                b2.rect(0, 0, w, h, col(PALIDO, flash * 0.42f));
            }
            float preto = (1f - eo(t / 1.2f)) * 0.88f + (1f - sai) * 0.92f;
            if (preto > 0.02f) {
                b2.rect(0, 0, w, h, col(0x000000, cl(preto)));
            }
            b2.flush();
            normal();
        } catch (Throwable t) {
            inicio = -1L;
            dev.rptag.RPTagMod.LOGGER.warn("RP Tag: aviso CONHECIMENTO desligado", t);
        }
    }

    // --------------------------------------------------------------- atualizar
    private static void atualizar(float t, float passo) {
        pulso = Math.max(0f, pulso - passo * 0.8f);
        flash = Math.max(0f, flash - passo * 1.6f);

        for (int i = 0; i < N_POEIRA; i++) {
            POE_Y[i] -= POE_V[i] * passo;
            POE_X[i] += sin(t * 0.7f + POE_F[i]) * 6f * passo;
            if (POE_Y[i] < -6f) {
                POE_Y[i] = DH + 6f;
                POE_X[i] = SORTE.nextFloat() * DW;
            }
        }
        if (t > proxSussurro && SUSSURROS.size() < 4) {
            Sussurro su = new Sussurro();
            su.s = SUSSURROS_TEXTO[SORTE.nextInt(SUSSURROS_TEXTO.length)];
            boolean esq = SORTE.nextBoolean();
            su.x = esq ? 40f + SORTE.nextFloat() * 150f : 740f + SORTE.nextFloat() * 150f;
            su.y = 150f + SORTE.nextFloat() * 280f;
            su.vida = 3.4f + SORTE.nextFloat();
            SUSSURROS.add(su);
            proxSussurro = t + 1.6f + SORTE.nextFloat() * 1.6f;
        }
        for (int i = SUSSURROS.size() - 1; i >= 0; i--) {
            Sussurro su = SUSSURROS.get(i);
            su.idade += passo;
            su.y -= 5f * passo;
            if (su.idade > su.vida) {
                SUSSURROS.remove(i);
            }
        }
        for (int i = ONDAS.size() - 1; i >= 0; i--) {
            Onda o = ONDAS.get(i);
            o.idade += passo;
            if (o.idade > 1.4f) {
                ONDAS.remove(i);
            }
        }

        // ---- memórias: o protótipo revelava passando o mouse; aqui é o TEMPO
        // que enche a barra (e o olho olha pra memória que está enchendo)
        int alvo = -1;
        float maior = 0f;
        for (int i = 0; i < 4; i++) {
            if (REVELADA[i]) {
                continue;
            }
            float abre = T_REVELA + i * REVELA_PASSO;
            float p = cl((t - (abre - ENCHE)) / ENCHE);
            PROG[i] = Math.max(PROG[i], p);
            if (PROG[i] >= 1f) {
                revelar(i, t);
            } else if (PROG[i] > maior) {
                maior = PROG[i];
                alvo = i;
            }
        }
        float dx = 0f, dy = 0f;
        if (alvo >= 0 && maior > 0.12f) {
            float vx = MEM_X[alvo] - DCX, vy = MEM_Y[alvo] - DCY;
            float l = Math.max(1f, (float) Math.sqrt(vx * vx + vy * vy));
            dx = vx / l * 13f * maior;
            dy = vy / l * 7f * maior;
        } else {
            dx = sin(t * 0.37f) * 9f;
            dy = cos(t * 0.29f) * 5f;
        }
        float k = Math.min(1f, passo * 2.4f);
        olharX += (dx - olharX) * k;
        olharY += (dy - olharY) * k;
    }

    /** abre uma verdade (no protótipo era o cursor em cima; aqui é o relógio). */
    private static void revelar(int i, float t) {
        if (REVELADA[i]) {
            return;
        }
        REVELADA[i] = true;
        PROG[i] = 1f;
        REVEL_T[i] = t;
        nRev++;
        flash = Math.max(flash, 0.2f);
        Onda o = new Onda();
        o.x = MEM_X[i];
        o.y = MEM_Y[i];
        ONDAS.add(o);
        if (nRev >= 4) {
            finalReveal(t);
        }
    }

    /** as 4 verdades abertas: clarão, onda grande e o texto troca. */
    private static void finalReveal(float t) {
        finalT = t;
        flash = 0.8f;
        pulso = 1f;
        Onda o = new Onda();
        o.x = DCX;
        o.y = DCY;
        o.forte = true;
        ONDAS.add(o);
        textoBase = t + 1.2f;
        etapa1 = true;
    }

    private static float know() {
        float k = nRev / 4f * 0.72f + 0.28f * eo((tAnt - 4f) / 22f);
        return 100f * cl(k);
    }

    // ---------------------------------------------------------------- fundo
    private static void fundo(Buf b, int w, int h, float cx, float cy, float s, float t, float env) {
        b.rect(0, 0, w, h, col(0x040201, 0.97f * env));
        // o degradê radial do centro (vinho/dourado escuro)
        float ap = eo(t / 2f);
        gradienteRadial(b, cx, cy, Math.max(w, h) * 0.95f, 0x36220A, 0x160D03, 0x040201, env);
        // as duas "imagens" de luz do protótipo (sprites 1 e 0 em SCREEN)
        aditivo();
        b.brilho(cx, cy, 380f * s, AMBAR, 0.32f * ap * env);
        b.brilho(cx, cy, 250f * s, OURO, 0.22f * ap * env);
        // oito feixes saindo do olho
        for (int i = 0; i < 8; i++) {
            float a = i * TAU / 8f + t * 0.02f;
            float a0 = a - 0.055f, a1 = a + 0.055f;
            float r0 = 60f * s, r1 = 760f * s;
            int dentro = col(0xFFE182, 0.13f * ap * env);
            int fora = col(0xFFE182, 0f);
            b.quad(cx + cos(a0) * r0, cy + sin(a0) * r0, dentro,
                    cx + cos(a1) * r0, cy + sin(a1) * r0, dentro,
                    cx + cos(a1) * r1, cy + sin(a1) * r1, fora,
                    cx + cos(a0) * r1, cy + sin(a0) * r1, fora);
        }
        normal();
        // sombras lentas atravessando a cena
        for (int i = 0; i < 3; i++) {
            float x = cx + 520f * s * sin(t * 0.05f + i * 2.1f);
            float y = cy + 90f * s + 120f * s * cos(t * 0.04f + i);
            b.brilho(x, y, 250f * s, 0x000000, 0.30f * env);
        }
    }

    /**
     * Degradê radial de verdade, anel por anel. (Antes eram 3 discos empilhados
     * com brilho(): cada disco terminava no seu raio e aparecia um ANEL na tela.)
     */
    private static void gradienteRadial(Buf b, float cx, float cy, float r,
            int cCentro, int cMeio, int cBorda, float a) {
        final int an = 8, n = 36;
        for (int i = 0; i < an; i++) {
            float u0 = i / (float) an, u1 = (i + 1) / (float) an;
            int k0 = corParada(u0, cCentro, cMeio, cBorda), k1 = corParada(u1, cCentro, cMeio, cBorda);
            int a0 = col(k0, a), a1 = col(k1, a);
            float r0 = r * u0, r1 = r * u1;
            for (int k = 0; k < n; k++) {
                float t0 = k * TAU / n, t1 = (k + 1) * TAU / n;
                b.quad(cx + cos(t0) * r0, cy + sin(t0) * r0, a0,
                        cx + cos(t1) * r0, cy + sin(t1) * r0, a0,
                        cx + cos(t1) * r1, cy + sin(t1) * r1, a1,
                        cx + cos(t0) * r1, cy + sin(t0) * r1, a1);
            }
        }
    }

    private static int corParada(float u, int c0, int c1, int c2) {
        return u < 0.5f ? mix(c0, c1, u * 2f) : mix(c1, c2, (u - 0.5f) * 2f);
    }

    /** as 4 colunas de runas das bordas (o protótipo usava a borda da janela). */
    private static void scripts(Buf b, float ox, float oy, float s, int w, int h, float t, float env) {
        float ap = eo((t - 1.0f) / 1.5f) * env;
        if (ap <= 0.01f) {
            return;
        }
        // as colunas acompanham a BORDA DA TELA, não a moldura 960x640
        float metade = (w / 2f) / s;
        float esq = DCX - metade, dir = DCX + metade;
        float[] cols = {esq + 34f, esq + 72f, dir - 72f, dir - 34f};
        float topo = -oy / s, alturaC = h / s;
        for (int c = 0; c < 4; c++) {
            float dirV = (c % 2 == 0) ? 1f : -1f;
            for (int k = 0; k < 22; k++) {
                float range = alturaC + 40f;
                float yy = fr((k * 28f + dirV * t * 14f) / range) * range + topo - 20f;
                float fade = cl(Math.min(yy - topo, topo + alturaC - yy) / 90f);
                int idx = (int) (rnd(c * 31 + k, 3) * 28f);
                boolean brilha = rnd(c * 7 + k, (int) (t * 0.5f)) > 0.93f;
                int cor = brilha ? PALIDO : OURO;
                float al = 0.42f * fade * ap * (brilha ? 2f : 1f);
                if (al < 0.02f) {
                    continue;
                }
                runa(b, idx, ox + cols[c] * s, oy + yy * s, 0f, 13f * s, cor, al, Math.max(1f, 1.1f * s));
            }
        }
    }

    /** o sigilo: fios, anéis, bandas de runas, estrela de 6 pontas e quadrados. */
    private static void sigilo(Buf b, float cx, float cy, float s, float t, float env, float pulso) {
        float a1 = eo((t - 0.3f) / 1.2f) * env;
        float a2 = eo((t - 0.8f) / 1.2f) * env;
        float a3 = eo((t - 1.3f) / 1.2f) * env;
        if (a1 > 0.01f) {
            // fios radiais
            int c = col(OURO, 0.18f * a1);
            float esp = Math.max(1f, 0.9f * s);
            for (int i = 0; i < 24; i++) {
                float a = i * TAU / 24f + t * 0.01f;
                b.seg(cx + cos(a) * 44f * s, cy + sin(a) * 44f * s,
                        cx + cos(a) * 172f * s, cy + sin(a) * 172f * s, esp, c, c);
            }
            b.anel(cx, cy, 172f * s, 72, Math.max(1f, 1.7f * s), 0f, OURO, 0.85f * a1);
            b.anel(cx, cy, 178f * s, 72, Math.max(1f, 1.1f * s), 0f, OURO, 0.40f * a1);
            b.anel(cx, cy, 146f * s, 64, Math.max(1f, 1.1f * s), 0f, OURO, 0.40f * a1);
            // 72 tracinhos no aro
            for (int i = 0; i < 72; i++) {
                float a = i * TAU / 72f + t * 0.02f;
                float r2 = 178f + (i % 6 == 0 ? 10f : 4f);
                int cc = col(OURO, 0.70f * a1);
                b.seg(cx + cos(a) * 178f * s, cy + sin(a) * 178f * s,
                        cx + cos(a) * r2 * s, cy + sin(a) * r2 * s, Math.max(1f, s), cc, cc);
            }
            runeBand(b, cx, cy, 159f * s, 44, 16f * s, -t * 0.05f, a1, 1, t, pulso, s);
        }
        if (a2 > 0.01f) {
            poligono(b, cx, cy, 3, 135f * s, t * 0.05f, OURO, 0.85f * a2, Math.max(1f, 1.5f * s));
            poligono(b, cx, cy, 3, 135f * s, -t * 0.05f + (float) Math.PI, OURO, 0.85f * a2, Math.max(1f, 1.5f * s));
        }
        if (a3 > 0.01f) {
            poligono(b, cx, cy, 4, 108f * s, t * 0.07f, OURO, 0.6f * a3, Math.max(1f, 1.2f * s));
            poligono(b, cx, cy, 4, 108f * s, t * 0.07f + (float) Math.PI / 4f, OURO, 0.6f * a3, Math.max(1f, 1.2f * s));
            b.anel(cx, cy, 92f * s, 64, Math.max(1f, 1.1f * s), 0f, OURO, 0.5f * a3);
            runeBand(b, cx, cy, 82f * s, 28, 12f * s, t * 0.07f, a3, 2, t, pulso, s);
            b.anel(cx, cy, 72f * s, 48, Math.max(1f, s), 0f, OURO, 0.45f * a3);
        }
    }

    private static void poligono(Buf b, float cx, float cy, int n, float r, float rot,
            int cor, float a, float esp) {
        int c = col(cor, a);
        float px = 0f, py = 0f, fx0 = 0f, fy0 = 0f;
        for (int i = 0; i <= n; i++) {
            float ang = rot + i * TAU / n - (float) Math.PI / 2f;
            float x = cx + cos(ang) * r, y = cy + sin(ang) * r;
            if (i == 0) {
                fx0 = x;
                fy0 = y;
            } else {
                b.seg(px, py, x, y, esp, c, c);
            }
            px = x;
            py = y;
        }
        b.seg(px, py, fx0, fy0, esp, c, c);
    }

    private static void runeBand(Buf b, float cx, float cy, float r, int n, float tam, float rot,
            float alpha, int salt, float t, float pulso, float s) {
        for (int k = 0; k < n; k++) {
            float th = TAU * k / n + rot;
            float x = cx + cos(th) * r, y = cy + sin(th) * r;
            boolean perto = pulso > 0.2f || (k % 13 == 0 && sin(t * 0.6f + k) > 0.6f);
            int idx = (salt * 37 + k * 11) % 28;
            if (perto) {
                idx = (idx + (int) (t * 12f) + k) % 28;      // a runa "se decifra"
            }
            float al = alpha * (perto ? 1f : 0.6f + 0.3f * sin(t * 1.2f + k));
            if (al < 0.02f) {
                continue;
            }
            runa(b, idx, x, y, th + (float) Math.PI / 2f, tam, perto ? PALIDO : OURO, al,
                    (perto ? 2.1f : 1.5f) * s);
        }
    }

    /** desenha uma runa (polilinha) girada em torno de (x,y). */
    private static void runa(Buf b, int idx, float x, float y, float ang, float tam,
            int cor, float a, float esp) {
        float[][] sg = RUNAS[Math.floorMod(idx, RUNAS.length)];
        float ca = cos(ang), sa = sin(ang);
        int c = col(cor, a);
        for (float[] q : sg) {
            float x1 = (q[0] - 0.5f) * tam * 0.55f, y1 = (q[1] - 0.75f) * tam * 0.55f;
            float x2 = (q[2] - 0.5f) * tam * 0.55f, y2 = (q[3] - 0.75f) * tam * 0.55f;
            b.seg(x + x1 * ca - y1 * sa, y + x1 * sa + y1 * ca,
                    x + x2 * ca - y2 * sa, y + x2 * sa + y2 * ca, esp, c, c);
        }
    }

    // ---------------------------------------------------------------- cordas
    private static float[] quadBez(float x0, float y0, float x1, float y1, float x2, float y2, float u) {
        float a = (1 - u) * (1 - u), c = 2 * (1 - u) * u, d = u * u;
        return new float[]{a * x0 + c * x1 + d * x2, a * y0 + c * y1 + d * y2};
    }

    private static void cordas(Buf b, float cx, float cy, float s, float t, float env) {
        for (int i = 0; i < 4; i++) {
            float cresce = eo((t - T_CORDAS - i * 0.2f) / 1.4f);
            if (cresce <= 0.01f) {
                continue;
            }
            float fcx = cx + MEM_X[i] * s;
            float fcy = cy - DCY * s + (MEM_Y[i] + 4f * sin(t * 0.6f + i * 1.9f)) * s;
            float ang = (float) Math.atan2(fcy - cy, fcx - cx);
            float p0x = cx + cos(ang) * 178f * s, p0y = cy + sin(ang) * 178f * s;
            float p2x = fcx, p2y = fcy - (MEM_H / 2f + 6f) * s;
            float ccx = (p0x + p2x) / 2f;
            float ccy = (p0y + p2y) / 2f + (28f + 6f * sin(t * 0.8f + i)) * s;
            int n = 24, m = (int) (n * cl(cresce));
            if (m < 2) {
                continue;
            }
            float brilho = REVELADA[i] ? 1f : 0.55f + 0.25f * PROG[i];
            float px = p0x, py = p0y;
            int cGrossa = col(OURO, 0.18f * brilho * env);
            int cFina = col(OURO, 0.75f * brilho * env);
            for (int k = 1; k <= m; k++) {
                float[] q = quadBez(p0x, p0y, ccx, ccy, p2x, p2y, k / (float) n);
                b.seg(px, py, q[0], q[1], Math.max(1f, 5f * s), cGrossa, cGrossa);
                b.seg(px, py, q[0], q[1], Math.max(1f, 1.4f * s), cFina, cFina);
                px = q[0];
                py = q[1];
            }
            // pulsos de luz correndo do anel até a memória
            aditivo();
            float spd = REVELADA[i] ? 0.34f : 0.14f;
            for (int k = 0; k < 3; k++) {
                float u = 1f - fr(t * spd + k / 3f + i * 0.23f);
                if (u > cresce) {
                    continue;
                }
                float[] q = quadBez(p0x, p0y, ccx, ccy, p2x, p2y, u);
                b.brilho(q[0], q[1], (REVELADA[i] ? 9f : 6f) * s, PALIDO, 0.85f * brilho * env);
            }
            normal();
        }
    }

    // ---------------------------------------------------------------- memórias
    /**
     * Uma memória pendurada: foto sépia lavada com vultos, moldura dourada com
     * alfinete e a verdade digitada depois que abre.
     */
    private static void memoria(Buf b, int i, float mx, float my, float s, float t, float env) {
        float a = eo((t - T_MEMORIA - i * 0.25f) / 1.2f) * env;
        if (a <= 0.01f) {
            return;
        }
        float cyOff = 4f * sin(t * 0.6f + i * 1.9f) * s;
        float rot = (MEM_GIRO[i] + 1.4f * sin(t * 0.5f + i)) * DEG;
        float p = eo(PROG[i]);
        float hw = MEM_W / 2f * s, hh = MEM_H / 2f * s;
        float ca = cos(rot), sa = sin(rot);
        float cxx = mx, cyy = my + cyOff;

        // sombra
        sombra(b, cxx, cyy, hw, hh, ca, sa, 5f * s, 7f * s, col(0x000000, 0.55f * a));
        // moldura
        b.quad(cxx + (-hw - 5f * s) * ca - (-hh - 5f * s) * sa, cyy + (-hw - 5f * s) * sa + (-hh - 5f * s) * ca, col(0x1A1005, a),
                cxx + (hw + 5f * s) * ca - (-hh - 5f * s) * sa, cyy + (hw + 5f * s) * sa + (-hh - 5f * s) * ca, col(0x1A1005, a),
                cxx + (hw + 5f * s) * ca - (hh + 5f * s) * sa, cyy + (hw + 5f * s) * sa + (hh + 5f * s) * ca, col(0x1A1005, a),
                cxx + (-hw - 5f * s) * ca - (hh + 5f * s) * sa, cyy + (-hw - 5f * s) * sa + (hh + 5f * s) * ca, col(0x1A1005, a));
        // a foto (degradê sépia: escuro nas bordas, claro no meio)
        int fora = col(mix(0x40301A, 0x7A5E38, p * 0.45f), a);
        int dentro = col(mix(0x7A5E38, 0xC9A46B, p * 0.45f), a);
        b.quad(cxx - hw * ca + hh * sa, cyy - hw * sa - hh * ca, fora,
                cxx + hw * ca + hh * sa, cyy + hw * sa - hh * ca, fora,
                cxx + hw * ca - hh * sa, cyy + hw * sa + hh * ca, fora,
                cxx - hw * ca - hh * sa, cyy - hw * sa + hh * ca, fora);
        b.brilho(cxx, cyy, Math.max(hw, hh) * 1.05f, mix(0x8A6E42, 0xD8B478, p * 0.5f), 0.55f * a);
        // os vultos da lembrança (aparecem conforme a memória "foca")
        for (int k = 0; k < 3; k++) {
            float u = rnd(i * 5 + k, 11) - 0.5f, v = rnd(i * 7 + k, 23) - 0.35f;
            float r = (k == 0 ? 9f : 13f + 6f * rnd(i + k, 5)) * s;
            float ox2 = u * hw * 1.2f, oy2 = v * hh * 1.1f;
            b.elipse(cxx + ox2 * ca - oy2 * sa, cyy + ox2 * sa + oy2 * ca, ca, sa,
                    r * 0.8f, r * (k == 0 ? 1.0f : 1.7f), col(0x1A1208, (0.30f + 0.45f * p) * a));
        }
        // quando ainda não abriu: a foto está LAVADA e tem uma runa em cima
        if (p < 0.98f) {
            b.brilho(cxx, cyy, Math.max(hw, hh) * 1.2f, 0xC8A46E, (1f - p) * 0.42f * a);
            runa(b, (int) (t * 5f) + i * 5, cxx, cyy, 0f, 40f * s,
                    PALIDO, (1f - p) * 0.5f * a, Math.max(1f, 1.4f * s));
            if (PROG[i] > 0.02f) {                       // a barra que enche sozinha
                float lar = MEM_W * s * PROG[i];
                b.quad(cxx - hw, cyy + hh - 5f * s, col(OURO, 0.9f * a),
                        cxx - hw + lar, cyy + hh - 5f * s, col(PALIDO, 0.9f * a),
                        cxx - hw + lar, cyy + hh, col(PALIDO, 0.9f * a),
                        cxx - hw, cyy + hh, col(OURO, 0.9f * a));
            }
        } else {
            // escurecer o pé da foto pra verdade ficar legível
            b.quad(cxx - hw, cyy - 6f * s, col(0x000000, 0f),
                    cxx + hw, cyy - 6f * s, col(0x000000, 0f),
                    cxx + hw, cyy + hh, col(0x000000, 0.86f * a),
                    cxx - hw, cyy + hh, col(0x000000, 0.86f * a));
        }
        // moldura dourada + alfinete
        contorno(b, cxx, cyy, hw + 5f * s, hh + 5f * s, ca, sa,
                REVELADA[i] ? PALIDO : OURO, a, Math.max(1f, (REVELADA[i] ? 2.6f : 1.8f) * s));
        // o alfinete: local (0, -hh-10) girado junto com a moldura
        float pinY = -(hh + 10f * s);
        b.circ(cxx - pinY * sa, cyy + pinY * ca, Math.max(1.5f, 4f * s), col(OURO, a));
    }

    private static void sombra(Buf b, float cx, float cy, float hw, float hh,
            float ca, float sa, float ox, float oy, int c) {
        float x = cx + ox, y = cy + oy;
        b.quad(x - hw * ca + hh * sa, y - hw * sa - hh * ca, c,
                x + hw * ca + hh * sa, y + hw * sa - hh * ca, c,
                x + hw * ca - hh * sa, y + hw * sa + hh * ca, c,
                x - hw * ca - hh * sa, y - hw * sa + hh * ca, c);
    }

    private static void contorno(Buf b, float cx, float cy, float hw, float hh,
            float ca, float sa, int cor, float a, float esp) {
        int c = col(cor, a);
        float x1 = cx - hw * ca + hh * sa, y1 = cy - hw * sa - hh * ca;
        float x2 = cx + hw * ca + hh * sa, y2 = cy + hw * sa - hh * ca;
        float x3 = cx + hw * ca - hh * sa, y3 = cy + hw * sa + hh * ca;
        float x4 = cx - hw * ca - hh * sa, y4 = cy - hw * sa + hh * ca;
        b.seg(x1, y1, x2, y2, esp, c, c);
        b.seg(x2, y2, x3, y3, esp, c, c);
        b.seg(x3, y3, x4, y4, esp, c, c);
        b.seg(x4, y4, x1, y1, esp, c, c);
    }

    // ---------------------------------------------------------------- olho
    private static void olho(Buf b, float cx, float cy, float w, float h, float t,
            float env, float boost, float s) {
        float abre = eo((t - 1.4f) / 1.8f) * env;
        if (abre <= 0.01f) {
            return;
        }
        float ph = fr(t / 7.5f) * 7.5f;
        float open = abre;
        if (ph < 0.22f) {
            open *= Math.abs(ph - 0.11f) / 0.11f;          // piscada lenta
        }
        float hh = Math.max(0.5f, h * open);
        // brilho de fundo
        aditivo();
        b.brilho(cx, cy, w * 1.15f, OURO, (0.30f + 0.25f * boost) * open * env);
        normal();
        if (open <= 0.05f) {
            float e = Math.max(1f, 3f * s);
            b.seg(cx - w, cy, cx + w, cy, e, col(OURO, env), col(OURO, env));
            return;
        }
        // pálpebra em forma de lente (duas quadráticas), preenchida em leque
        int n = 12;
        float[] px = new float[n * 2 + 1], py = new float[n * 2 + 1];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            px[i] = cx - w + 2 * w * u;
            py[i] = cy - 2 * hh * 2 * u * (1 - u);
        }
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            px[n + i] = cx + w - 2 * w * u;
            py[n + i] = cy + 2 * hh * 2 * u * (1 - u);
        }
        int centro = col(0xFFFCEB, env);
        int borda = col(0xECBE5A, env);
        for (int i = 0; i < px.length - 1; i++) {
            b.tri(cx, cy, centro, px[i], py[i], borda, px[i + 1], py[i + 1], borda);
        }
        // íris (fibras radiais) + pupila + brilho
        float ir = Math.min(hh * 0.95f, w * 0.42f);
        float ix = cx + olharX * s, iy = cy + olharY * s;
        iris(b, ix, iy, ir, env, t);
        float pr = ir * (0.33f + 0.04f * sin(t * 1.3f) - 0.06f * boost);
        b.circ(ix, iy, pr, col(0x0E0600, env));
        b.circ(ix - ir * 0.42f, iy - ir * 0.46f, Math.max(1f, ir * 0.11f), col(0xFFFFFF, 0.85f * env));
        // contorno com "drop shadow" dourado
        float esp = Math.max(1.5f, 3.2f * s);
        int c = col(OURO, env);
        for (int i = 0; i < px.length - 1; i++) {
            b.seg(px[i], py[i], px[i + 1], py[i + 1], esp, c, c);
        }
        b.seg(px[px.length - 1], py[py.length - 1], px[0], py[0], esp, c, c);
    }

    private static void iris(Buf b, float ix, float iy, float ir, float env, float t) {
        if (ir < 1f) {
            return;
        }
        int centro = 0xFFDC6E, meio = 0xE88E16, fora = 0x7A3A02;
        // anéis do disco
        b.circ(ix, iy, ir, col(fora, env));
        b.circ(ix, iy, ir * 0.86f, col(meio, env));
        b.circ(ix, iy, ir * 0.52f, col(centro, env));
        // fibras radiais: cada uma com tamanho/opacidade própria (hash fixo)
        for (int i = 0; i < N_FIBRAS; i++) {
            float ang = i * TAU / N_FIBRAS + sin(i * 12.9f) * 0.03f;
            float r0 = ir * (0.30f + 0.10f * rnd(i, 3));
            float r1 = ir * (0.72f + 0.24f * rnd(i, 7));
            float al = (0.18f + 0.34f * rnd(i, 13)) * env;
            int cor = (i % 3 == 0) ? 0xFFE8A0 : 0x8A4A00;
            float esp = Math.max(1f, ir * 0.045f);
            b.seg(ix + cos(ang) * r0, iy + sin(ang) * r0,
                    ix + cos(ang + 0.06f) * r1, iy + sin(ang + 0.06f) * r1,
                    esp, col(cor, al), col(cor, al));
        }
        // borda escura da íris
        b.anel(ix, iy, ir * 0.97f, 24, Math.max(1f, ir * 0.10f), t * 0.02f, 0x3A1C00, 0.55f * env);
    }

    // ---------------------------------------------------------------- poeira e ondas
    private static void poeira(Buf b, float ox, float oy, float s, float t, float env, int h) {
        aditivo();
        for (int i = 0; i < N_POEIRA; i++) {
            float x = ox + POE_X[i] * s, y = oy + POE_Y[i] * s;
            if (y < -12 || y > h + 12) {
                continue;
            }
            float sz = Math.max(1f, (3.5f + 2.5f * sin(t * 2f + POE_F[i])) * s);
            b.brilho(x, y, sz, OURO, 0.45f * env);
        }
        normal();
    }

    private static void ondas(Buf b, float ox, float oy, float s, float t, float env) {
        for (Onda o : ONDAS) {
            float k = o.idade / 1.4f;
            float rad = (10f + (o.forte ? 460f : 340f) * eo(k)) * s;
            float a = (1f - k) * (o.forte ? 0.75f : 0.6f) * env;
            if (a <= 0.01f) {
                continue;
            }
            float x = ox + o.x * s, y = oy + o.y * s;
            b.anel(x, y, rad, 56, Math.max(1f, (1f + 4f * (1f - k)) * s), 0f, OURO, a);
            b.anel(x, y, rad * 0.94f, 48, Math.max(1f, s), 0f, PALIDO, a * 0.8f);
        }
    }

    // ---------------------------------------------------------------- balança
    private static void balanca(Buf b, float ox, float oy, float s, float t, float env) {
        float ap = eo((t - 2.8f) / 1.0f) * env;
        if (ap <= 0.01f) {
            return;
        }
        float[] g = geomBalanca(ox, oy, s);
        float lx = g[0], ly = g[1], rx = g[2], ry = g[3];
        float san = g[4], conhece = g[5];
        float cx = ox + 118f * s, cy = oy + 92f * s;
        int c = col(OURO, ap);
        float e = Math.max(1f, 2f * s);
        b.seg(cx, cy, cx, cy + 62f * s, e, c, c);
        b.seg(cx - 22f * s, cy + 62f * s, cx + 22f * s, cy + 62f * s, e, c, c);
        b.seg(lx, ly, rx, ry, Math.max(1f, 2.6f * s), c, c);
        b.circ(cx, cy, Math.max(1.5f, 4f * s), col(PALIDO, ap));
        for (int lado = 0; lado < 2; lado++) {
            float px = lado == 0 ? lx : rx, py = lado == 0 ? ly : ry;
            int cc = col(OURO, ap);
            b.seg(px, py, px - 16f * s, py + 30f * s, Math.max(1f, s), cc, cc);
            b.seg(px, py, px + 16f * s, py + 30f * s, Math.max(1f, s), cc, cc);
            // o prato (arco de 180 graus)
            float px0 = px - 20f * s, py0 = py + 18f * s, raio = 20f * s;
            float ax = px0 + raio, ay = py0 + 12f * s;
            for (int i = 0; i < 10; i++) {
                float a0 = (float) Math.PI * i / 10f, a1 = (float) Math.PI * (i + 1) / 10f;
                b.seg(ax - raio * cos(a0), ay - raio * 0.6f * sin(a0),
                        ax - raio * cos(a1), ay - raio * 0.6f * sin(a1), Math.max(1f, 1.6f * s), cc, cc);
            }
            float sz = lado == 0 ? 10f + 14f * san / 100f : 10f + 16f * conhece / 100f;
            aditivo();
            b.brilho(px, py + 22f * s, sz * 1.2f * s, lado == 0 ? PALIDO : OURO, 0.85f * ap);
            normal();
        }
    }

    /**
     * A geometria da balança: [lx, ly, rx, ry, sanidade, conhecimento]. O passo
     * do texto usa os MESMOS números do desenho, senão os rótulos descolam dos
     * pratos quando a balança inclina.
     */
    private static float[] geomBalanca(float ox, float oy, float s) {
        float conhece = know();
        float san = 100f - 0.45f * conhece;
        float tilt = Math.max(-0.5f, Math.min(0.5f, (conhece - san) / 100f * 0.5f));
        float cx = ox + 118f * s, cy = oy + 92f * s, L = 62f * s;
        return new float[]{cx - cos(tilt) * L, cy - sin(tilt) * L,
                cx + cos(tilt) * L, cy + sin(tilt) * L, san, conhece};
    }

    // ---------------------------------------------------------------- contador
    private static void contador(Buf b, float ox, float oy, float s, float t, float env) {
        float ap = eo((t - 3.0f) / 1.0f) * env;
        if (ap <= 0.01f) {
            return;
        }
        int c = col(OURO, ap);
        for (int i = 0; i < 4; i++) {
            float x = ox + (760f + i * 34f) * s, y = oy + 108f * s;
            if (REVELADA[i]) {
                b.diamante(x, y, 12f * s, 14f * s, col(PALIDO, ap));
            }
            b.seg(x, y - 14f * s, x + 12f * s, y, Math.max(1f, 1.6f * s), c, c);
            b.seg(x + 12f * s, y, x, y + 14f * s, Math.max(1f, 1.6f * s), c, c);
            b.seg(x, y + 14f * s, x - 12f * s, y, Math.max(1f, 1.6f * s), c, c);
            b.seg(x - 12f * s, y, x, y - 14f * s, Math.max(1f, 1.6f * s), c, c);
        }
    }

    // ---------------------------------------------------------------- ícone e rodapé
    private static void icone(Buf b, float cx, float cy, float tam, float s, float t, float env) {
        float a = (0.78f + 0.22f * sin(t * 3f)) * eo((t - 0.8f) / 0.8f) * env;
        if (a <= 0.01f) {
            return;
        }
        aditivo();
        b.brilho(cx, cy, tam * 1.7f, OURO, 0.40f * a);
        normal();
        int c = col(PALIDO, a);
        float e = Math.max(1.2f, tam * 0.10f);
        float ax = cx, ay = cy - tam * 0.5f, bx = cx + tam * 0.58f, by = cy + tam * 0.5f;
        float dx = cx - tam * 0.58f, dy = cy + tam * 0.5f;
        b.seg(ax, ay, bx, by, e, c, c);
        b.seg(bx, by, dx, dy, e, c, c);
        b.seg(dx, dy, ax, ay, e, c, c);
        // o olhinho dentro do triângulo
        float ex = cx, ey = cy + tam * 0.12f, ew = tam * 0.26f, eh = tam * 0.13f;
        b.tri(ex, ey, c, ex - ew, ey, c, ex, ey - eh, c);
        b.tri(ex, ey, c, ex + ew, ey, c, ex, ey - eh, c);
        b.tri(ex, ey, c, ex - ew, ey, c, ex, ey + eh, c);
        b.tri(ex, ey, c, ex + ew, ey, c, ex, ey + eh, c);
        b.circ(ex, ey, Math.max(1f, tam * 0.07f), c);
    }

    private static void faixaBaixo(Buf b, int w, int h, float s, float t, float env) {
        float pa = eo((t - 2.2f) / 1.0f) * env;
        if (pa <= 0.02f) {
            return;
        }
        float topo = h - 210f * s;
        b.quad(0, topo, col(0x0C0600, 0f), w, topo, col(0x0C0600, 0f),
                w, h, col(0x080300, 0.92f * pa), 0, h, col(0x080300, 0.92f * pa));
        b.rect(0, h - 76f * s, w, 76f * s, col(0x080300, 0.55f * pa));
    }

    // ---------------------------------------------------------------- textos
    private static void tituloAVISO(GuiGraphics g, Font f, float cx, float y, float s, float t, float env) {
        float fi = eo((t - 0.8f) / 0.8f) * env;
        if (fi <= 0.02f) {
            return;
        }
        float esc = Math.max(1.0f, 1.9f * s);
        float esp = 8f * s;
        float total = 0f;
        for (int i = 0; i < TITULO.length(); i++) {
            total += f.width(String.valueOf(TITULO.charAt(i))) * esc + esp;
        }
        float x = cx - total / 2f;
        for (int i = 0; i < TITULO.length(); i++) {
            String ch = String.valueOf(TITULO.charAt(i));
            txt(g, f, ch, x, y, esc, (int) (fi * 255) << 24 | (PALIDO & 0xFFFFFF));
            x += f.width(ch) * esc + esp;
        }
    }

    private static void sussurros(GuiGraphics g, Font f, float ox, float oy, float s, float t, float env) {
        float esc = Math.max(0.9f, 1.9f * s);
        for (Sussurro su : SUSSURROS) {
            float k = cl(su.idade / su.vida);
            float a = (float) Math.pow(Math.sin(Math.PI * k), 1.5) * 0.9f * env;
            if (a < 0.02f) {
                continue;
            }
            txt(g, f, su.s, ox + su.x * s + sin(t * 5f + su.x) * 1.2f * s,
                    oy + su.y * s - 12f, esc, (int) (a * 255) << 24 | (PALIDO & 0xFFFFFF));
        }
        // rótulos da balança (mesmos números do desenho) e o nome do contador
        float ap = eo((t - 2.8f) / 1.0f) * env;
        if (ap > 0.02f) {
            float[] ge = geomBalanca(ox, oy, s);
            float escP = Math.max(0.7f, 1.0f * s);
            int cor = (int) (ap * 255) << 24 | (0xFFE8A0 & 0xFFFFFF);
            txt(g, f, "SANIDADE", ge[0] - 34f * s, ge[1] + 46f * s, escP, cor);
            txt(g, f, String.format("%d%%", (int) ge[4]), ge[0] - 12f * s, ge[1] + 58f * s, escP, cor);
            txt(g, f, "CONHECIMENTO", ge[2] - 40f * s, ge[3] + 46f * s, escP, cor);
            txt(g, f, String.format("%d%%", (int) ge[5]), ge[2] - 12f * s, ge[3] + 58f * s, escP, cor);
        }
        float ac = eo((t - 3.0f) / 1.0f) * env;
        if (ac > 0.02f) {
            float escC = Math.max(0.7f, 1.0f * s);
            txt(g, f, "VERDADES", ox + 752f * s, oy + 74f * s, escC,
                    (int) (ac * 255) << 24 | (0xFFE8A0 & 0xFFFFFF));
        }
    }

    /** a VERDADE escrita dentro de cada memória já aberta (digitada, como no protótipo). */
    private static void verdades(GuiGraphics g, Font f, float ox, float oy, float s, float t, float env) {
        for (int i = 0; i < 4; i++) {
            if (!REVELADA[i] || REVEL_T[i] < 0f) {
                continue;
            }
            float cxM = ox + MEM_X[i] * s;
            float cyM = oy + MEM_Y[i] * s + 4f * sin(t * 0.6f + i * 1.9f) * s;
            int n = (int) ((t - REVEL_T[i]) * 30f);
            if (n <= 0) {
                continue;
            }
            float esc = Math.max(0.55f, 0.8f * s);
            int cor = (int) (env * 255) << 24 | (BRANCO & 0xFFFFFF);
            int sombra = (int) (env * 120) << 24 | 0x000000;
            for (int ln = 0; ln < 2 && n > 0; ln++) {
                String s2 = VERDADES[i][ln];
                int m = Math.min(n, s2.length());
                n -= s2.length();
                String trecho = s2.substring(0, m);
                float x = cxM - f.width(trecho) * esc / 2f;
                float y = cyM + (16f + ln * 14f) * s;
                txt(g, f, trecho, x + 1f, y + 1f, esc, sombra);
                txt(g, f, trecho, x, y, esc, cor);
            }
        }
    }

    private static void textoGrande(GuiGraphics g, Font f, int w, int h, float s, float t, float env) {
        if (t < textoBase) {
            return;
        }
        String[] frases = etapa1 ? FRASES1 : FRASES0;
        int n = (int) ((t - textoBase) * CPS);

        // o palavra grande (CONHECIMENTO), letra por letra com brilho na chegada
        float esc = Math.max(1.0f, 2.4f * s);
        float esp = 4.5f * s;
        float larguraMax = w * 0.90f;
        float larg = 0f;
        for (int i = 0; i < msg.length(); i++) {
            larg += f.width(String.valueOf(msg.charAt(i))) * esc + esp;
        }
        if (larg > larguraMax && larg > 1f) {
            esc = Math.max(1.1f, esc * larguraMax / larg);
            larg = larguraMax;
        }
        float x = w / 2f - larg / 2f;
        float y = h - 126f * s;
        for (int i = 0; i < msg.length(); i++) {
            String ch = String.valueOf(msg.charAt(i));
            if (i < n) {
                float idade = (t - textoBase) - i / CPS;
                float brilho = cl(1f - idade / 0.7f);
                if (brilho > 0.05f) {
                    txt(g, f, ch, x - 1f * s, y, esc, (int) (brilho * 150) << 24 | (PALIDO & 0xFFFFFF));
                }
                txt(g, f, ch, x, y, esc,
                        (int) (env * 255) << 24 | (brilho > 0.05f ? 0xFFFFEB : 0xFFD65A));
            }
            x += f.width(ch) * esc + esp;
        }

        // as duas frases, digitadas (entram no ritmo da primeira)
        int resto = n - msg.length();
        float esc2 = Math.max(0.85f, 1.3f * s);
        for (int i = 0; i < 2 && resto > 0; i++) {
            int m = Math.min(resto, frases[i].length());
            resto -= frases[i].length();
            String trecho = frases[i].substring(0, m);
            float lar = f.width(trecho) * esc2;
            txt(g, f, trecho, w / 2f - lar / 2f, h - (88f - i * 24f) * s, esc2,
                    (int) (env * 235) << 24 | (0xFFE8AA & 0xFFFFFF));
        }
    }

    private static void dica(GuiGraphics g, Font f, int w, int h, float s, float t, float env) {
        if (t < textoBase + (msg.length() + FRASES0[0].length() + FRASES0[1].length()) / CPS + 0.4f) {
            return;
        }
        float a = (0.35f + 0.3f * sin(t * 3f)) * env;
        if (a < 0.03f) {
            return;
        }
        String d = nRev < 4 ? "as memórias se abrem sozinhas" : "o que foi sabido não se desaprende";
        float esc = Math.max(0.8f, 1.1f * s);
        txt(g, f, d, w / 2f - f.width(d) * esc / 2f, h - 12f * s, esc,
                (int) (a * 255) << 24 | (PALIDO & 0xFFFFFF));
    }

    private static void txt(GuiGraphics g, Font f, String s, float x, float y, float esc, int cor) {
        if (((cor >>> 24) & 255) < 4) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(esc, esc, 1f);
        g.drawString(f, s, 0, 0, cor, false);
        g.pose().popPose();
    }
}
