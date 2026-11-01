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
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * AVISO CONHECIMENTO — estilo 16 do /rp admin avisar (5.7.0).
 *
 * <p>Porte fiel do protótipo do dono ({@code OVsEHLOl.java}, classe
 * {@code AvisoConhecimento}): o olho dourado que SABE tudo. Anéis de runas
 * girando em sentidos opostos, olhinhos abrindo nas beiradas da tela, poeira
 * dourada subindo, sussurros ("eu sei o seu nome") e as duas barras
 * SANIDADE × CONHECIMENTO — quanto mais sabe, menos sã.
 *
 * <h2>Roteiro (em segundos)</h2>
 * <pre>
 *   0,0   a tela abre no escuro e clareia (fundo âmbar em volta do olho)
 *   0,6   raios dourados saem do centro
 *   0,9   os 3 anéis de runas aparecem girando
 *   1,0   o OLHO GRANDE abre (pisca de vez em quando) e acompanha o olhar
 *   2,0   os 12 olhinhos das laterais começam a abrir
 *   0,8   "AVISO" no topo (ícone de perigo)
 *   2,2   "CONHECIMENTO" letra por letra, com brilho
 *   4,1   as duas frases entram digitadas
 *   4,0   as barras SANIDADE e CONHECIMENTO aparecem (a sanidade cai)
 *   8,5   o "[ ENTENDIDO ]" decorativo
 *   fim   tudo escurece e apaga
 * </pre>
 *
 * <p>No jogo não tem mouse: o olho grande olha devagar pra um lado e pro outro
 * sozinho, e as ondas de "revelação" (que o protótipo fazia no clique) saem
 * sozinhas em intervalos, junto com os sussurros.
 *
 * <p>Regra de ouro do mod: qualquer erro DESLIGA o aviso, nunca o jogo.
 */
public final class ConhecimentoAvisoFx {

    private ConhecimentoAvisoFx() {
    }

    // ------------------------------------------------------------- constantes
    private static final float DUR = 13.5f;
    private static final float T_TEXTO = 2.2f;
    private static final float T_LINHAS = T_TEXTO + 1.9f;
    private static final float T_BARRAS = 4.0f;
    private static final float T_BOTAO = 8.5f;

    private static final int OURO = 0xFFCD3C;
    private static final int AMBAR = 0xFF9614;
    private static final int PALIDO = 0xFFF6B4;

    /** espaço de desenho do protótipo (o render escala pra tela do jogador). */
    private static final float DW = 960f, DH = 640f;
    private static final float DCX = DW / 2f, DCY = 300f;

    private static final float[] ANEL_R = {108f, 138f, 168f};
    private static final int[] ANEL_N = {34, 44, 54};
    private static final float[] ANEL_DIR = {1f, -1f, 1f};
    private static final float[] ANEL_VEL = {0.16f, 0.12f, 0.09f};
    private static final float[] ANEL_TAM = {15f, 17f, 19f};

    private static final int N_OLHOS = 12;
    private static final int N_POEIRA = 90;

    private static final String[] FRASES = {
            "eu sei o seu nome", "você já sabia disso", "tudo está conectado",
            "não feche os olhos", "pergunte, eu respondo", "cada resposta tem um preço",
            "eu vi o que você fez", "isso já aconteceu antes", "você não é o primeiro",
            "olhe mais fundo"
    };

    // ---------------------------------------------------------------- estado
    private static long inicio = -1L;
    private static float tAnt;
    private static String msg = "CONHECIMENTO";
    private static float flash, pulso;
    private static float proxSussurro = 1.6f, proxOnda = 2.4f;

    /** alfabeto de runas: cada runa é uma polilinha em grade 3x4. */
    private static final float[][][] RUNAS = new float[28][][];

    private static final float[] OLHO_X = new float[N_OLHOS];
    private static final float[] OLHO_Y = new float[N_OLHOS];
    private static final float[] OLHO_W = new float[N_OLHOS];
    private static final float[] OLHO_F = new float[N_OLHOS];

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
    }

    static {
        // alfabeto fixo (semente 21, igual ao protótipo) — não muda entre avisos
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
        for (int i = 0; i < N_OLHOS; i++) {
            boolean esq = i % 2 == 0;
            OLHO_X[i] = esq ? 50f + r.nextFloat() * 200f : 710f + r.nextFloat() * 200f;
            OLHO_Y[i] = 130f + r.nextFloat() * 290f;
            OLHO_W[i] = 16f + r.nextFloat() * 16f;
            OLHO_F[i] = r.nextFloat() * 6.28f;
        }
        for (int i = 0; i < N_POEIRA; i++) {
            POE_X[i] = r.nextFloat() * DW;
            POE_Y[i] = r.nextFloat() * DH;
            POE_V[i] = 6f + r.nextFloat() * 22f;
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
        proxSussurro = 1.6f;
        proxOnda = 2.4f;
        SUSSURROS.clear();
        ONDAS.clear();
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

            preparar(g);
            Buf b = new Buf(g.pose().last().pose());

            // ---------------- fundo âmbar (escuro nas bordas, claro no olho)
            b.rect(0, 0, w, h, col(0x080401, 0.94f * env));
            b.brilho(cx, cy, 620f * s, 0x5A3406, 0.55f * env);
            b.brilho(cx, cy, 400f * s, 0x7A4A08, 0.40f * env);
            b.brilho(cx, cy, 240f * s, 0x9A6410, 0.26f * env);

            // névoa âmbar passeando (as "imagens" do protótipo)
            for (int i = 0; i < 8; i++) {
                float nx = cx + 300f * s * sin(t * 0.10f * (i % 4 + 1) + i * 1.9f);
                float ny = cy + 180f * s * cos(t * 0.09f * (i % 3 + 1) + i * 2.4f);
                b.brilho(nx, ny, (160f + 50f * sin(t * 0.3f + i)) * s, AMBAR, 0.05f * env);
            }

            // ---------------- raios dourados saindo do olho
            float fade = eo((t - 0.6f) / 1.5f) * env;
            if (fade > 0.01f) {
                b.brilho(cx, cy, (280f + 20f * sin(t * 1.5f)) * s, PALIDO, 0.22f * fade);
                b.brilho(cx, cy, 380f * s, OURO, 0.15f * fade);
                int n = 15;
                for (int i = 0; i < n; i++) {
                    float base = i * TAU / n - t * 0.04f + sin(i * 12.9898f) * 0.1f;
                    float len = 100f + 100f * fr(sin(i * 78.233f) * 43758.5453f) + 14f * sin(t * 1.4f + i);
                    for (int k = 0; k < 5; k++) {
                        float a = base + (k - 2) * 0.03f;
                        float r0 = 190f + (k % 3) * 7f;
                        float r1 = r0 + len * (0.55f + 0.45f * fr(sin((i * 7 + k) * 3.77f) * 9999f));
                        float al = (0.45f + 0.35f * fr(sin((i + k) * 5.1f) * 99f)) * fade;
                        float esp = (1.5f + 3f * fr(sin((i * 3 + k) * 2.9f) * 77f)) * s;
                        int c = col(OURO, al);
                        b.seg(cx + cos(a) * r0 * s, cy + sin(a) * r0 * s,
                                cx + cos(a) * r1 * s, cy + sin(a) * r1 * s, Math.max(1f, esp), c, c);
                    }
                }
            }

            // ---------------- 3 anéis de runas girando
            float fadeAneis = eo((t - 0.9f) / 1.2f) * env;
            if (fadeAneis > 0.01f) {
                float olharX = sin(t * 0.42f) * 0.6f;
                for (int ri = 0; ri < 3; ri++) {
                    float r = ANEL_R[ri] * (1f + 0.012f * sin(t * 2f + ri)) * s;
                    b.anel(cx, cy, r + 14f * s, 64, 1f * s, 0f, AMBAR, 0.30f * fadeAneis);
                    for (int k = 0; k < ANEL_N[ri]; k++) {
                        float th = TAU * k / ANEL_N[ri] + ANEL_DIR[ri] * t * ANEL_VEL[ri];
                        float x = cx + cos(th) * r, y = cy + sin(th) * r;
                        // no jogo não tem mouse: a "proximidade" vem da onda de revelação
                        boolean perto = pulso > 0.2f || (k % 17 == 0 && olharX > 0.45f);
                        int idx = (ri * 37 + k * 11) % RUNAS.length;
                        if (perto) {
                            idx = (idx + (int) (t * 14) + k) % RUNAS.length;
                        }
                        float a = (perto ? 1.0f : 0.55f + 0.25f * sin(t * 1.3f + k)) * fadeAneis;
                        int cor = perto ? PALIDO : OURO;
                        float esp = (perto ? 2.2f : 1.6f) * s;
                        runa(b, idx, x, y, th + (float) Math.PI / 2f, ANEL_TAM[ri] * s, cor, cl(a), esp);
                    }
                }
            }

            // ---------------- OLHO GRANDE
            float abre = eo((t - 1.0f) / 1.6f) * env;
            if (abre > 0.01f) {
                float ph = fr(t / 6.3f) * 6.3f;
                float open = abre;
                if (ph < 0.22f) {
                    open *= Math.abs(ph - 0.11f) / 0.11f;    // piscada lenta
                }
                float lx = sin(t * 0.42f) * 26f * s, ly = cos(t * 0.31f) * 12f * s;
                b.brilho(cx, cy, 150f * s * (0.9f + 0.1f * open), OURO, 0.35f * open);
                olho(b, cx, cy, 96f * s, 50f * s, open, lx, ly, true, 1f);
            }

            // ---------------- olhinhos das laterais
            for (int i = 0; i < N_OLHOS; i++) {
                float o = cl(1.7f * sin(t * 0.42f + OLHO_F[i]) - 0.35f);
                o = Math.max(o, pulso) * eo((t - 2.0f - i * 0.2f) / 0.8f);
                if (o <= 0.03f) {
                    continue;
                }
                float x = ox + OLHO_X[i] * s, y = oy + OLHO_Y[i] * s;
                float ww = OLHO_W[i] * s;
                float lx = sin(t * 0.5f + i) * ww * 0.18f;
                float ly = cos(t * 0.37f + i * 2f) * ww * 0.10f;
                olho(b, x, y, ww, ww * 0.48f, o, lx, ly, false, (0.85f * env));
            }

            // ---------------- poeira dourada subindo
            for (int i = 0; i < N_POEIRA; i++) {
                float pxx = ox + POE_X[i] * s, pyy = oy + POE_Y[i] * s;
                if (pyy < -10 || pyy > h + 10) {
                    continue;
                }
                float sz = (2.5f + 2f * sin(t * 3f + POE_F[i])) * s;
                b.brilho(pxx, pyy, Math.max(1f, sz), OURO, 0.35f * env);
            }

            // ---------------- ondas de revelação
            for (Onda o : ONDAS) {
                float k = o.idade / 1.2f;
                float rad = (10f + 340f * eo(k)) * s;
                float a = (1f - k) * 0.8f * env;
                b.anel(o.x, o.y, rad, 56, Math.max(1f, (1f + 5f * (1f - k)) * s), 0f, OURO, a);
                b.anel(o.x, o.y, rad * 0.94f, 56, Math.max(1f, 1f * s), 0f, PALIDO, a * 0.9f);
            }

            b.vinheta(w, h, Math.max(60f, 120f * s), 0x000000, 0.55f * env);
            b.flush();
            normal();

            // ---------------- sussurros (texto, por cima da geometria)
            for (Sussurro su : SUSSURROS) {
                float k = cl(su.idade / su.vida);
                float a = (float) Math.pow(Math.sin(Math.PI * k), 1.5) * 0.9f * env;
                if (a < 0.02f) {
                    continue;
                }
                float esc = Math.max(0.9f, 2.0f * s);
                txt(g, f, su.s, ox + su.x * s + sin(t * 5f + su.x) * 1.2f * s,
                        oy + su.y * s - 12f, esc, (int) (a * 255) << 24 | (PALIDO & 0xFFFFFF));
            }

            // ================= UI
            float fi = eo((t - 0.8f) / 0.8f) * env;
            if (fi > 0.02f) {
                iconePerigo(g, cx, oy + 64f * s, 40f * s, fi, t);
                tituloEspacado(g, f, "AVISO", cx, oy + 124f * s - 14f, Math.max(1.0f, 1.9f * s),
                        (int) (fi * 255) << 24 | (PALIDO & 0xFFFFFF), 8f * s);
            }

            // faixa escura do rodapé
            float pa = eo((t - 1.6f) / 0.9f) * env;
            if (pa > 0.02f) {
                preparar(g);
                Buf b2 = new Buf(g.pose().last().pose());
                float topo = h - 210f * s;
                b2.quad(0, topo, col(0x0C0600, 0f), w, topo, col(0x0C0600, 0f),
                        w, h, col(0x080300, 0.95f * pa), 0, h, col(0x080300, 0.95f * pa));
                b2.rect(0, h - 90f * s, w, 90f * s, col(0x080300, 0.6f * pa));
                b2.flush();
                normal();
            }

            // ---------------- título grande letra por letra
            if (t >= T_TEXTO) {
                int n = (int) ((t - T_TEXTO) * 9f);
                float esc = Math.max(1.0f, 2.6f * s);
                float esp = 5f * s;
                String big = msg;
                float larguraMax = w * 0.94f;
                if (f.width(big) * esc > larguraMax) {
                    esc = Math.max(1.2f, larguraMax / Math.max(1, f.width(big)));
                }
                int total = 0;
                for (int i = 0; i < big.length(); i++) {
                    total += f.width(String.valueOf(big.charAt(i)));
                }
                float larg = total * esc + esp * (big.length() - 1);
                float x = cx - larg / 2f;
                float y = h - 126f * s;
                for (int i = 0; i < big.length(); i++) {
                    String ch = String.valueOf(big.charAt(i));
                    if (i < n) {
                        float idade = (t - T_TEXTO) - i / 9f;
                        float brilho = cl(1f - idade / 0.7f);
                        int cor = brilho > 0.05f ? 0xFFFFEB : 0xFFD65A;
                        if (brilho > 0.05f) {
                            txt(g, f, ch, x - 1f * s, y, esc, (int) (brilho * 160) << 24 | (0xFFF6B4 & 0xFFFFFF));
                        }
                        txt(g, f, ch, x, y, esc, (int) (env * 255) << 24 | (cor & 0xFFFFFF));
                    }
                    x += f.width(ch) * esc + esp;
                }
            }

            // ---------------- as duas frases, digitadas
            if (t >= T_LINHAS) {
                String[] linhas = {
                        "Uma verdade foi revelada. Ela não pode ser esquecida.",
                        "Quanto mais você sabe, menos você consegue voltar a ser quem era."
                };
                int restante = (int) ((t - T_LINHAS) * 26f);
                float esc = Math.max(0.85f, 1.35f * s);
                for (int i = 0; i < linhas.length && restante > 0; i++) {
                    int n = Math.min(restante, linhas[i].length());
                    restante -= linhas[i].length();
                    String trecho = linhas[i].substring(0, n);
                    float lar = f.width(trecho) * esc;
                    txt(g, f, trecho, cx - lar / 2f, h - (88 - i * 16) * s, esc,
                            (int) (env * 235) << 24 | (0xFFE8AA & 0xFFFFFF));
                }
            }

            // ---------------- BARRA SANIDADE × CONHECIMENTO
            float ba = eo((t - T_BARRAS) / 0.8f) * env;
            if (ba > 0.02f) {
                float k = eo((t - T_BARRAS) / 9f);
                float conhece = 100f * k;
                float sanidade = 100f - 41f * k + 1.8f * sin(t * 17f) * k;
                float y = h - 26f * s;
                barra(g, f, Math.max(12f, 40f * s), y - 20f * s, "SANIDADE", sanidade, 0xE65A28, ba, s);
                barra(g, f, Math.max(12f, 330f * s), y - 20f * s, "CONHECIMENTO", conhece, OURO, ba, s);
            }

            // ---------------- "[ ENTENDIDO ]" decorativo
            if (t > T_BOTAO) {
                float ba2 = eo((t - T_BOTAO) / 0.5f) * env;
                float bw = 150f * s, bh = 26f * s;
                float bx = w - bw - Math.max(14f, 40f * s);
                float by = h - bh - Math.max(14f, 34f * s);
                preparar(g);
                Buf b3 = new Buf(g.pose().last().pose());
                b3.rect(bx, by, bw, bh, col(0x1E1002, 0.6f * ba2));
                float lw = Math.max(1f, 1.8f * s);
                int c = col(OURO, 0.9f * ba2);
                b3.seg(bx, by, bx + bw, by, lw, c, c);
                b3.seg(bx + bw, by, bx + bw, by + bh, lw, c, c);
                b3.seg(bx + bw, by + bh, bx, by + bh, lw, c, c);
                b3.seg(bx, by + bh, bx, by, lw, c, c);
                b3.flush();
                normal();
                String bt = "[ ENTENDIDO ]";
                float esc = Math.max(0.8f, 1.2f * s);
                txt(g, f, bt, bx + (bw - f.width(bt) * esc) / 2f, by + bh * 0.32f, esc,
                        (int) (ba2 * 255) << 24 | (0xFFF6B4 & 0xFFFFFF));
            }

            // ---------------- flash da revelação e escurecer do começo
            if (flash > 0) {
                preparar(g);
                Buf b4 = new Buf(g.pose().last().pose());
                b4.rect(0, 0, w, h, col(PALIDO, flash * 0.45f));
                b4.flush();
                normal();
            }
            float preto = (1f - eo(t / 1.2f)) * 0.85f + (1f - sai) * 0.9f;
            if (preto > 0.02f) {
                preparar(g);
                Buf b5 = new Buf(g.pose().last().pose());
                b5.rect(0, 0, w, h, col(0x000000, cl(preto)));
                b5.flush();
                normal();
            }
        } catch (Throwable t) {
            inicio = -1L;
            dev.rptag.RPTagMod.LOGGER.warn("RP Tag: aviso CONHECIMENTO desligado", t);
        }
    }

    // --------------------------------------------------------------- atualizar
    private static void atualizar(float t, float passo) {
        pulso = Math.max(0f, pulso - passo * 0.8f);
        flash = Math.max(0f, flash - passo * 2.2f);

        for (int i = 0; i < N_POEIRA; i++) {
            POE_Y[i] -= POE_V[i] * passo;
            POE_X[i] += sin(t * 0.8f + POE_F[i]) * 8f * passo;
            if (POE_Y[i] < -6f) {
                POE_Y[i] = DH + 6f;
                POE_X[i] = SORTE.nextFloat() * DW;
            }
        }
        if (t > proxSussurro && SUSSURROS.size() < 5) {
            Sussurro su = new Sussurro();
            su.s = FRASES[SORTE.nextInt(FRASES.length)];
            boolean esq = SORTE.nextBoolean();
            su.x = esq ? 40f + SORTE.nextFloat() * 180f : 700f + SORTE.nextFloat() * 180f;
            su.y = 150f + SORTE.nextFloat() * 280f;
            su.vida = 3.8f + SORTE.nextFloat();
            SUSSURROS.add(su);
            proxSussurro = t + 1.0f + SORTE.nextFloat() * 1.4f;
        }
        for (int i = SUSSURROS.size() - 1; i >= 0; i--) {
            Sussurro su = SUSSURROS.get(i);
            su.idade += passo;
            su.y -= 6f * passo;
            if (su.idade > su.vida) {
                SUSSURROS.remove(i);
            }
        }
        // no jogo quem fazia a onda era o clique: aqui ela sai sozinha
        if (t > proxOnda) {
            revelar(DCX + (SORTE.nextFloat() - 0.5f) * 260f, DCY + (SORTE.nextFloat() - 0.5f) * 140f);
            proxOnda = t + 2.6f + SORTE.nextFloat() * 2.2f;
        }
        for (int i = ONDAS.size() - 1; i >= 0; i--) {
            Onda o = ONDAS.get(i);
            o.idade += passo;
            if (o.idade > 1.2f) {
                ONDAS.remove(i);
            }
        }
    }

    /** A "revelação" do protótipo (lá era o clique do mouse). */
    private static void revelar(float x, float y) {
        flash = 0.55f;
        pulso = 1f;
        Onda o = new Onda();
        o.x = x;
        o.y = y;
        ONDAS.add(o);
        Sussurro su = new Sussurro();
        su.s = "você viu";
        su.x = Math.max(20f, Math.min(DW - 160f, x - 40f));
        su.y = Math.max(140f, Math.min(420f, y));
        su.vida = 2.6f;
        SUSSURROS.add(su);
    }

    // ---------------------------------------------------------------- desenho
    /** desenha uma runa (polilinha) girada em torno de (x,y). */
    private static void runa(Buf b, int idx, float x, float y, float ang, float tam, int cor, float a, float esp) {
        float[][] s = RUNAS[Math.floorMod(idx, RUNAS.length)];
        float ca = cos(ang), sa = sin(ang);
        int c = col(cor, a);
        for (float[] sg : s) {
            float x1 = (sg[0] - 0.5f) * tam * 0.55f, y1 = (sg[1] - 0.75f) * tam * 0.55f;
            float x2 = (sg[2] - 0.5f) * tam * 0.55f, y2 = (sg[3] - 0.75f) * tam * 0.55f;
            b.seg(x + x1 * ca - y1 * sa, y + x1 * sa + y1 * ca,
                    x + x2 * ca - y2 * sa, y + x2 * sa + y2 * ca, esp, c, c);
        }
    }

    /** olho: pálpebra em forma de lente, íris dourada com pupila, brilho. */
    private static void olho(Buf b, float cx, float cy, float w, float h, float open,
            float lx, float ly, boolean detalhe, float alpha) {
        float hh = Math.max(0.5f, h * open);
        if (open <= 0.04f) {
            float e = Math.max(1f, (detalhe ? 3f : 1.4f) * (hh / Math.max(0.5f, h)));
            b.seg(cx - w, cy, cx + w, cy, Math.max(1f, e), col(OURO, alpha), col(OURO, alpha));
            return;
        }
        // lente: duas quadráticas aproximadas por polilinhas
        int N = 10;
        // (5.7.1) BUG: era N*2 e o laço escreve até N+i = 2N (estourava o array
        // no MEIO do aviso: o desenho do olho lançava ArrayIndexOutOfBounds e o
        // try/catch desligava tudo — era o "começa e crasha" que o dono viu)
        float[] px = new float[N * 2 + 1], py = new float[N * 2 + 1];
        for (int i = 0; i <= N; i++) {
            float u = i / (float) N;
            px[i] = cx - w + 2 * w * u;
            py[i] = cy - 2 * hh * 2 * u * (1 - u);
        }
        for (int i = 0; i <= N; i++) {
            float u = i / (float) N;
            px[N + i] = cx + w - 2 * w * u;
            py[N + i] = cy + 2 * hh * 2 * u * (1 - u);
        }
        // preenchimento (leque a partir do centro)
        int centro = col(0xFFFCE1, alpha);
        int borda = col(0xE8B648, alpha);
        for (int i = 0; i < px.length - 1; i++) {
            b.tri(cx, cy, centro, px[i], py[i], borda, px[i + 1], py[i + 1], borda);
        }
        // íris
        float ir = Math.min(hh * 0.95f, w * 0.42f);
        float ix = cx + lx, iy = cy + ly;
        b.brilho(ix, iy, ir, 0xFFFABE, alpha);
        b.circ(ix, iy, ir * 0.72f, col(OURO, alpha));
        b.circ(ix, iy, ir * 0.45f, col(AMBAR, alpha));
        // pupila (que respira)
        float pw = ir * (0.22f + 0.05f * sin(tAnt * 3f));
        b.elipse(ix, iy, 0, 1, pw, ir * 0.82f, col(0x140800, alpha));
        // brilho
        b.brilho(ix - ir * 0.46f, iy - ir * 0.56f, Math.max(1f, ir * 0.22f), 0xFFFFFF, alpha * 0.85f);
        // contorno
        int cont = col(OURO, alpha);
        float esp = Math.max(1f, (detalhe ? 3.2f : 1.5f) * (w / 96f));
        for (int i = 0; i < px.length - 1; i++) {
            b.seg(px[i], py[i], px[i + 1], py[i + 1], esp, cont, cont);
        }
        b.seg(px[px.length - 1], py[py.length - 1], px[0], py[0], esp, cont, cont);
    }

    private static void iconePerigo(GuiGraphics g, float cx, float cy, float s, float a, float t) {
        preparar(g);
        Buf b = new Buf(g.pose().last().pose());
        b.brilho(cx, cy, s * 1.9f, OURO, 0.35f * a * (0.75f + 0.25f * sin(t * 4f)));
        float[] xs = {cx, cx + s * 0.58f, cx - s * 0.58f};
        float[] ys = {cy - s * 0.5f, cy + s * 0.5f, cy + s * 0.5f};
        int c = col(PALIDO, a);
        float esp = Math.max(1.2f, s * 0.10f);
        b.seg(xs[0], ys[0], xs[1], ys[1], esp, c, c);
        b.seg(xs[1], ys[1], xs[2], ys[2], esp, c, c);
        b.seg(xs[2], ys[2], xs[0], ys[0], esp, c, c);
        b.flush();
        normal();
        // o olhinho dentro do triângulo
        preparar(g);
        Buf b2 = new Buf(g.pose().last().pose());
        olho(b2, cx, cy + s * 0.12f, s * 0.28f, s * 0.11f, 1f, 0, 0, false, a);
        b2.flush();
        normal();
    }

    private static void barra(GuiGraphics g, Font f, float x, float y, String rotulo,
            float v, int cor, float a, float s) {
        preparar(g);
        Buf b = new Buf(g.pose().last().pose());
        int segs = 20;
        int on = Math.round(cl(v / 100f) * segs);
        float lar = Math.max(3f, 5f * s), alt = Math.max(5f, 9f * s);
        float esp = Math.max(7f, 11f * s);
        for (int i = 0; i < segs; i++) {
            b.rect(x + i * esp, y, lar, alt, i < on ? col(cor, a) : col(0x6E501E, 0.35f * a));
        }
        b.flush();
        normal();
        float esc = Math.max(0.75f, 1.05f * s);
        txt(g, f, rotulo, x, y - 11f * s, esc, (int) (a * 255) << 24 | (0xFFE096 & 0xFFFFFF));
        txt(g, f, String.format("%d%%", (int) cl(v)), x + segs * esp + 5f * s, y + alt * 0.35f, esc,
                (int) (a * 255) << 24 | (PALIDO & 0xFFFFFF));
    }

    private static void tituloEspacado(GuiGraphics g, Font f, String s, float cx, float y,
            float esc, int cor, float espaco) {
        float total = 0f;
        for (int i = 0; i < s.length(); i++) {
            total += f.width(String.valueOf(s.charAt(i))) * esc + espaco;
        }
        float x = cx - total / 2f;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            txt(g, f, ch, x, y, esc, cor);
            x += f.width(ch) * esc + espaco;
        }
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
