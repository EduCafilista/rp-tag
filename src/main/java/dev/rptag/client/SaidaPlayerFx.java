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
 * ANIMAÇÃO DE SAÍDA DOS JOGADORES — 5.7.0.
 *
 * <p>Porte fiel do protótipo do dono ({@code yJXaxPdQ.java}, classe
 * {@code AnimacaoSaida}): o bonequinho do jogador que está DESCONECTANDO se
 * desfaz em chuva de código enquanto anéis de texto ("session.close();",
 * "01001011 ") giram em volta dele. Embaixo, um log de terminal vai
 * confirmando tudo com [OK] e o título troca de "SAINDO DO JOGO..." para
 * "SESSÃO ENCERRADA"; no fim aparece o "DESCONECTADO" e
 * "Até a próxima, &lt;nome&gt;."
 *
 * <h2>Roteiro (em segundos)</h2>
 * <pre>
 *   0,0   a chuva de código já está caindo; o player aparece no centro
 *   0,8   começa o log de terminal (encerrando sessão / salvando inventário…)
 *   2,6   o FEIXE DE VARREDURA sobe pelo corpo e o player se desfaz em glifos
 *   4,8   o anel de código abre (sessão encerrada)
 *   5,0   "DESCONECTADO" (com separação de cor) e "Até a próxima, nome."
 *   fim   tudo apaga
 * </pre>
 *
 * <p>O nome vem do pacote (o jogador que saiu). O protótipo repetia sozinho;
 * aqui toca UMA vez por saída, que é o que faz sentido no jogo.
 *
 * <p>Regra de ouro do mod: qualquer erro DESLIGA a animação, nunca o jogo.
 */
public final class SaidaPlayerFx {

    private SaidaPlayerFx() {
    }

    // ------------------------------------------------------------- constantes
    private static final float DUR = 9.0f;
    private static final float T_DESFAZ = 2.6f, DURACAO_DESFAZ = 2.2f;
    private static final float T_TEXTO = 5.0f;

    private static final int CIANO = 0x5AE1FF;
    private static final int QUENTE = 0xEBFCFF;
    private static final int VERDE = 0x64FFAA;
    private static final String GLIFOS = "01{}<>/;=+*#$%&0101";

    private static final float DW = 960f, DH = 640f;
    private static final float DCX = DW / 2f, DCY = 318f;
    private static final float PX = 12f;                 // "pixel" do sprite
    private static final float X0 = DCX - 8 * PX, Y0 = DCY - 16 * PX;

    private static final String[] ANEIS = {
            "01001011 01101001 ", "player.save(); ", "0xFF 0x3A 0x7C ", "session.close(); ", "SESSION_END // "
    };
    private static final float[] ANEL_OFFY = {-150f, -75f, 0f, 75f, 150f};
    private static final float[] ANEL_RX = {150f, 175f, 190f, 175f, 150f};
    private static final float[] ANEL_RY = {34f, 40f, 44f, 40f, 34f};
    private static final int ANEL_N = 40;

    private static final int N_COLUNAS = 18;             // chuva de código
    private static final int N_GLIFOS_CHUVA = 10;

    // ---------------------------------------------------------------- estado
    private static long inicio = -1L;
    private static float tAnt;
    private static String jogador = "Steve";
    private static float giro;
    private static float proximaLinha;

    private static int[][] SPRITE;                       // 32 x 16 (0 = vazio)
    private static final float[][] CV = new float[32][16];      // quando cada pixel se desfaz
    private static final float[][] SOMBRA = new float[32][16];
    private static final boolean[][] SOLTO = new boolean[32][16];
    private static final float[] DESLOC = new float[32];

    private static final float[] CHUVA_X = new float[N_COLUNAS];
    private static final float[] CHUVA_Y = new float[N_COLUNAS];
    private static final float[] CHUVA_V = new float[N_COLUNAS];

    private static final List<Glifo> PARTES = new ArrayList<>();
    private static final Random SORTE = new Random();

    private static final class Glifo {
        float x, y, vx, vy, vida, max, fase;
        char ch;
    }

    static {
        construirSprite();
        for (int i = 0; i < N_COLUNAS; i++) {
            CHUVA_X[i] = i * DW / N_COLUNAS + 8f;
            CHUVA_Y[i] = SORTE.nextFloat() * DH;
            CHUVA_V[i] = 60f + SORTE.nextFloat() * 110f;
        }
        for (int r = 0; r < 32; r++) {
            for (int c = 0; c < 16; c++) {
                SOMBRA[r][c] = 0.88f + SORTE.nextFloat() * 0.22f;
            }
        }
    }

    /** o bonequinho 16x32 do protótipo (vista de frente): cabeça, camisa, calça, sapatos. */
    private static void construirSprite() {
        int[][] sp = new int[32][16];
        ret(sp, 0, 7, 4, 11, 0xC68E6B);       // cabeça
        ret(sp, 0, 1, 4, 11, 0x4A2E14);       // cabelo
        ret(sp, 2, 3, 4, 4, 0x4A2E14);
        ret(sp, 2, 3, 11, 11, 0x4A2E14);
        sp[4][5] = 0xFFFFFF;
        sp[4][6] = 0x3A3A9A;                  // olhos
        sp[4][9] = 0x3A3A9A;
        sp[4][10] = 0xFFFFFF;
        ret(sp, 6, 7, 6, 9, 0x8A5A3C);        // boca
        ret(sp, 8, 19, 4, 11, 0x00A8A8);      // camisa
        ret(sp, 8, 11, 0, 3, 0x00A8A8);       // mangas
        ret(sp, 8, 11, 12, 15, 0x00A8A8);
        ret(sp, 12, 19, 0, 3, 0xC68E6B);      // braços
        ret(sp, 12, 19, 12, 15, 0xC68E6B);
        ret(sp, 20, 28, 4, 11, 0x3C3C9A);     // calça
        ret(sp, 29, 31, 4, 11, 0x6A6A6A);     // sapatos
        SPRITE = sp;
    }

    private static void ret(int[][] sp, int r0, int r1, int c0, int c1, int cor) {
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                sp[r][c] = cor;
            }
        }
    }

    // ------------------------------------------------------------- ciclo de vida
    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "saida_player"),
                SaidaPlayerFx::render);
    }

    /** @param nome jogador que saiu (aparece no log e no "Até a próxima"). */
    public static void iniciar(String nome) {
        jogador = (nome == null || nome.isBlank()) ? "Steve" : nome.replaceAll("\u00a7.", "");
        inicio = System.nanoTime();
        tAnt = 0f;
        giro = 0f;
        proximaLinha = 0.06f;
        PARTES.clear();
        for (int r = 0; r < 32; r++) {
            for (int c = 0; c < 16; c++) {
                // de baixo pra cima, com um pouco de aleatório (o mesmo do protótipo)
                CV[r][c] = T_DESFAZ + DURACAO_DESFAZ * (((31 - r) / 31f) * 0.85f + SORTE.nextFloat() * 0.15f);
                SOLTO[r][c] = false;
            }
        }
        for (int i = 0; i < 32; i++) {
            DESLOC[i] = 0f;
        }
        for (int i = 0; i < N_COLUNAS; i++) {
            CHUVA_Y[i] = SORTE.nextFloat() * DH;
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

            atualizar(t, passo);

            float entra = ss(0.0f, 0.6f, t);
            float sai = 1f - ss(DUR - 1.2f, DUR - 0.15f, t);
            float env = entra * sai;
            float desf = cl((t - T_DESFAZ) / DURACAO_DESFAZ);

            preparar(g);
            Buf b = new Buf(g.pose().last().pose());

            // ---------------- fundo azul-noite com brilho atrás do player
            b.rect(0, 0, w, h, col(0x02050C, 0.95f * env));
            b.brilho(w / 2f, h / 2f, 620f * s, 0x08182C, 0.95f * env);
            b.brilho(w / 2f, h / 2f, (240f + 140f * desf) * s, CIANO, (0.10f + 0.22f * desf) * env);

            // ---------------- SPRITE do jogador (pixels) + feixe de varredura
            float respira = t < T_DESFAZ ? sin(t * 2.2f) * 1.5f : 0f;
            for (int r = 0; r < 32; r++) {
                for (int c = 0; c < 16; c++) {
                    int cor = SPRITE[r][c];
                    if (cor == 0) {
                        continue;
                    }
                    float px = ox + (X0 + c * PX + DESLOC[r]) * s;
                    float py = oy + (Y0 + r * PX + respira) * s;
                    float d = t - CV[r][c];
                    float lado = PX * s;
                    if (d < 0f) {
                        float perto = -d;
                        float brilho = perto < 0.3f ? 1f + 0.8f * (1f - perto / 0.3f) * SORTE.nextFloat() : 1f;
                        float sh = SOMBRA[r][c] * brilho;
                        int r8 = (int) cl(((cor >> 16) & 255) / 255f * sh) * 255;
                        int g8 = (int) cl(((cor >> 8) & 255) / 255f * sh) * 255;
                        int b8 = (int) cl((cor & 255) / 255f * sh) * 255;
                        b.rect(px, py, Math.max(1f, lado - 0.6f), Math.max(1f, lado - 0.6f),
                                col((r8 << 16) | (g8 << 8) | b8, env));
                    } else if (d < 0.35f) {
                        int c2 = col(CIANO, 0.5f * env);
                        b.rect(px, py, lado - 1f, lado - 1f, col(0x0A2233, 0.55f * env));
                        b.seg(px, py, px + lado, py, 1f, c2, c2);
                        b.seg(px + lado, py, px + lado, py + lado, 1f, c2, c2);
                        b.seg(px + lado, py + lado, px, py + lado, 1f, c2, c2);
                        b.seg(px, py + lado, px, py, 1f, c2, c2);
                    }
                }
            }
            if (t >= T_DESFAZ && t <= T_DESFAZ + DURACAO_DESFAZ * 1.15f) {
                float fr2 = (t - T_DESFAZ) / DURACAO_DESFAZ;
                float linhaR = 31f - cl(fr2 / 0.85f) * 31f;
                float yb = oy + (Y0 + linhaR * PX + PX / 2f) * s;
                b.rect(ox + (X0 - 50f) * s, yb - 8f * s, (16 * PX + 100f) * s, 16f * s, col(CIANO, 0.22f * env));
                b.rect(ox + (X0 - 50f) * s, yb - 1f * s, (16 * PX + 100f) * s, 2f * s, col(QUENTE, 0.85f * env));
            }

            b.vinheta(w, h, Math.max(50f, 110f * s), 0x000000, 0.6f * env);
            b.flush();
            normal();

            // ---------------- chuva de código
            float intensidade = 0.35f + 0.5f * eo((t - 1.8f) / 2.0f);
            float esc = Math.max(0.7f, 1.0f * s);
            for (int i = 0; i < N_COLUNAS; i++) {
                for (int j = 0; j < N_GLIFOS_CHUVA; j++) {
                    float yy = CHUVA_Y[i] - j * 16f;
                    if (yy < -16f || yy > DH + 16f) {
                        continue;
                    }
                    int idx = (int) Math.floor(yy / 16f);
                    float hh = fr(sin((i * 131 + idx * 17 + 1) * 12.9898f) * 43758.5453f);
                    char ch = GLIFOS.charAt((int) (hh * GLIFOS.length()) % GLIFOS.length());
                    float a = (1f - j / (float) N_GLIFOS_CHUVA) * 0.5f * intensidade * env;
                    int cor = j == 0 ? QUENTE : CIANO;
                    txt(g, f, String.valueOf(ch), ox + CHUVA_X[i] * s, oy + yy * s, esc,
                            (int) (a * 255) << 24 | (cor & 0xFFFFFF));
                }
            }

            // ---------------- anéis de código (metade de trás antes, da frente depois)
            for (int ri = 0; ri < 5; ri++) {
                anel(g, f, ri, false, ox, oy, s, t, env);
            }
            for (Glifo p : PARTES) {
                float a = cl(p.vida / p.max) * env;
                txt(g, f, String.valueOf(p.ch), ox + p.x * s, oy + p.y * s, esc,
                        (int) (a * 255) << 24 | ((a > 0.7f ? QUENTE : CIANO) & 0xFFFFFF));
            }
            for (int ri = 0; ri < 5; ri++) {
                anel(g, f, ri, true, ox, oy, s, t, env);
            }

            // ================= UI
            // título do topo
            float ft = eo((t - 0.3f) / 0.5f) * env;
            if (ft > 0.02f) {
                String titulo;
                if (t < T_TEXTO) {
                    int pontos = (int) (t * 3f) % 4;
                    titulo = "SAINDO DO JOGO" + ".".repeat(pontos);
                } else {
                    titulo = "SESSÃO ENCERRADA";
                }
                float escT = Math.max(1.0f, 1.7f * s);
                float lar = f.width(titulo) * escT;
                txt(g, f, titulo, w / 2f - lar / 2f, oy + 52f * s - 10f, escT,
                        (int) (ft * 235) << 24 | (QUENTE & 0xFFFFFF));
            }

            // log de terminal (canto inferior esquerdo)
            String[] log = {
                    "> encerrando sessão de " + jogador + "...",
                    "> salvando inventário ............ [OK]",
                    "> salvando posição ............... [OK]",
                    "> sincronizando progresso ........ [OK]",
                    "> desconectando do servidor ...... [OK]"
            };
            float[] logT = {0.8f, 1.4f, 1.9f, 2.4f, 3.0f};
            float escL = Math.max(0.65f, 0.95f * s);
            for (int i = 0; i < log.length; i++) {
                if (t < logT[i]) {
                    continue;
                }
                int n = Math.min(log[i].length(), (int) ((t - logT[i]) * 55f));
                String linha = log[i].substring(0, n);
                boolean ok = linha.endsWith("[OK]");
                float yy = h - (96f - i * 16f) * s - 6f;
                txt(g, f, ok ? linha.substring(0, linha.length() - 4) : linha, ox + 40f * s, yy, escL,
                        (int) (0.9f * env * 255) << 24 | (CIANO & 0xFFFFFF));
                if (ok) {
                    float larOk = f.width(linha.substring(0, linha.length() - 4)) * escL;
                    txt(g, f, "[OK]", ox + 40f * s + larOk, yy, escL,
                            (int) (0.9f * env * 255) << 24 | (VERDE & 0xFFFFFF));
                }
            }

            // barra de progresso da sessão
            if (t > 0.8f) {
                float pct = cl((t - 0.8f) / (T_DESFAZ + DURACAO_DESFAZ - 0.8f));
                float bx = ox + 580f * s, by = h - 62f * s;
                txt(g, f, "SESSÃO", bx, by, escL, (int) (env * 255) << 24 | (CIANO & 0xFFFFFF));
                preparar(g);
                Buf b2 = new Buf(g.pose().last().pose());
                b2.rect(bx, by + 10f * s, 330f * s, 12f * s, col(CIANO, 0.20f * env));
                b2.rect(bx, by + 10f * s, 330f * s * pct, 12f * s, col(pct >= 1f ? VERDE : CIANO, 0.95f * env));
                b2.flush();
                normal();
                txt(g, f, String.format("%d%%", (int) (pct * 100f)), bx + 338f * s, by + 12f * s, escL,
                        (int) (env * 255) << 24 | (QUENTE & 0xFFFFFF));
            }

            // texto final
            if (t >= T_TEXTO) {
                float glitch = (t - T_TEXTO < 0.6f) ? 8f * (1f - (t - T_TEXTO) / 0.6f) : 0f;
                String grande = decodificar(t, "DESCONECTADO", T_TEXTO, 0.05f, 0.3f);
                float escG = Math.max(1.4f, 3.2f * s);
                if (f.width(grande) * escG > w * 0.92f) {
                    escG = Math.max(1.2f, w * 0.92f / Math.max(1, f.width(grande)));
                }
                float lar = f.width(grande) * escG;
                float gx = w / 2f - lar / 2f, gy = h * 0.52f;
                txt(g, f, grande, gx - 2f * s - glitch * s, gy, escG, (int) (0.55f * env * 255) << 24 | 0xFF4696);
                txt(g, f, grande, gx + 2f * s + glitch * s, gy, escG, (int) (0.55f * env * 255) << 24 | (CIANO & 0xFFFFFF));
                txt(g, f, grande, gx, gy, escG, (int) (env * 250) << 24 | (QUENTE & 0xFFFFFF));

                String sub = decodificar(t, "Até a próxima, " + jogador + ".", T_TEXTO + 0.7f, 0.04f, 0.2f);
                float escS = Math.max(0.85f, 1.3f * s);
                float larS = f.width(sub) * escS;
                txt(g, f, sub, w / 2f - larS / 2f, gy + escG * 18f + 10f * s, escS,
                        (int) (env * 235) << 24 | (0xAAEBFF & 0xFFFFFF));
            }

            // escurecer do começo e do fim
            float preto = (1f - eo(t / 0.6f)) * 0.9f + (1f - sai) * 0.95f;
            if (preto > 0.02f) {
                preparar(g);
                Buf b3 = new Buf(g.pose().last().pose());
                b3.rect(0, 0, w, h, col(0x000000, cl(preto)));
                b3.flush();
                normal();
            }
        } catch (Throwable t) {
            inicio = -1L;
            dev.rptag.RPTagMod.LOGGER.warn("RP Tag: animacao de saida desligada ({})", t.toString());
        }
    }

    // --------------------------------------------------------------- atualizar
    private static void atualizar(float t, float passo) {
        float boost = 1f + 3f * eo((t - 2.0f) / 2.0f);
        giro += passo * boost;

        float chuvaBoost = 1f + 2f * eo((t - 2.0f) / 2.5f);
        for (int i = 0; i < N_COLUNAS; i++) {
            CHUVA_Y[i] += CHUVA_V[i] * chuvaBoost * passo;
            if (CHUVA_Y[i] - 14f * 16f > DH) {
                CHUVA_Y[i] = -SORTE.nextFloat() * 200f;
            }
        }

        // "conexão instável": as linhas do player tremem antes de desfazer
        proximaLinha -= passo;
        if (proximaLinha <= 0f) {
            for (int r = 0; r < 32; r++) {
                DESLOC[r] = 0f;
            }
            if (t > 0.8f && t < T_DESFAZ && SORTE.nextFloat() < 0.55f) {
                int n = 1 + SORTE.nextInt(3);
                for (int i = 0; i < n; i++) {
                    DESLOC[SORTE.nextInt(32)] = (SORTE.nextBoolean() ? 1f : -1f) * PX * (0.5f + SORTE.nextFloat() * 2f);
                }
            }
            proximaLinha = 0.06f + SORTE.nextFloat() * 0.12f;
        }

        // solta os glifos
        for (int r = 0; r < 32; r++) {
            for (int c = 0; c < 16; c++) {
                if (SPRITE[r][c] != 0 && !SOLTO[r][c] && t >= CV[r][c] + 0.35f) {
                    SOLTO[r][c] = true;
                    Glifo p = new Glifo();
                    p.x = X0 + c * PX + PX / 2f;
                    p.y = Y0 + r * PX + PX / 2f;
                    p.vx = (SORTE.nextFloat() - 0.5f) * 30f;
                    p.vy = -(30f + SORTE.nextFloat() * 90f);
                    p.max = p.vida = 1.2f + SORTE.nextFloat() * 1.6f;
                    p.fase = SORTE.nextFloat() * 6.28f;
                    p.ch = glifo();
                    PARTES.add(p);
                }
            }
        }
        for (int i = PARTES.size() - 1; i >= 0; i--) {
            Glifo p = PARTES.get(i);
            p.vida -= passo;
            p.x += (p.vx + sin(t * 4f + p.fase) * 14f) * passo;
            p.y += p.vy * passo;
            p.vy -= 25f * passo;
            if (SORTE.nextFloat() < 0.05f) {
                p.ch = glifo();
            }
            if (p.vida <= 0f) {
                PARTES.remove(i);
            }
        }
    }

    private static char glifo() {
        return GLIFOS.charAt(SORTE.nextInt(GLIFOS.length()));
    }

    /** texto "decodificando": glifos aleatórios até a letra se resolver. */
    private static String decodificar(float t, String s, float inicio, float porLetra, float embaralha) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            float ti = inicio + i * porLetra;
            if (ch == ' ' || t < ti) {
                sb.append(' ');
            } else if (t < ti + embaralha) {
                sb.append(glifo());
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- desenho
    /** um anel de texto girando; front=false desenha a metade de trás. */
    private static void anel(GuiGraphics g, Font f, int ri, boolean frente, float ox, float oy,
            float s, float t, float env) {
        float ra = eo((t - 0.2f) / 0.8f) * (1f - eo((t - (T_DESFAZ + DURACAO_DESFAZ + 0.1f)) / 0.9f));
        if (ra <= 0.01f) {
            return;
        }
        String texto = ANEIS[ri];
        float dir = (ri % 2 == 0) ? 1f : -1f;
        float escalaAnel = 1f - 0.38f * eo((t - 1.8f) / (T_DESFAZ + DURACAO_DESFAZ - 1.8f));
        escalaAnel += 0.9f * eo((t - (T_DESFAZ + DURACAO_DESFAZ)) / 1.0f);
        float r = ANEL_RX[ri] * escalaAnel * (1f + 0.03f * sin(t * 2f + ri));
        float ry = ANEL_RY[ri] * escalaAnel;
        float cyA = DCY + ANEL_OFFY[ri] * (1f - 0.15f * eo((t - 1.8f) / 2.5f));
        for (int k = 0; k < ANEL_N; k++) {
            char ch = texto.charAt(k % texto.length());
            if (ch == ' ') {
                continue;
            }
            float th = TAU * k / ANEL_N + dir * giro * (0.55f + 0.12f * ri);
            float z = sin(th);
            if ((z >= 0f) != frente) {
                continue;
            }
            float x = DCX + cos(th) * r;
            float y = cyA + z * ry;
            float esc = (z > 0.5f ? 1.05f : (z > -0.3f ? 0.95f : 0.8f)) * s;
            float a = cl((0.30f + 0.70f * (z + 1f) / 2f) * ra) * env;
            int cor = z > 0.8f ? QUENTE : CIANO;
            txt(g, f, String.valueOf(ch), ox + x * s - 4f * s, oy + y * s, Math.max(0.65f, esc),
                    (int) (a * 255) << 24 | (cor & 0xFFFFFF));
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
