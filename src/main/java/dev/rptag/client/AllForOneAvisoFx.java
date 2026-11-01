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
 * AVISO ALLFORONE — estilo 17 do /rp admin avisar (5.7.0).
 *
 * <p>Porte fiel do protótipo do dono ({@code xZomZtNl.java}, classe
 * {@code AvisoEnergia}): "AS VOZES NA SUA CABEÇA SEMPRE FORAM UMA PESSOA."
 * Um sol pintado à mão (pinceladas amarelas e arcos brancos) atrás de uma
 * SILHUETA DE FOGO — as cinco camadas de brasa tremem como chama, os olhos
 * abrem no rosto escuro, as faíscas convergem pra dentro do corpo e voltam
 * subindo, com sussurros nas beiradas e o pergaminho (papel envelhecido) por
 * cima do fundo vermelho.
 *
 * <h2>Roteiro (em segundos)</h2>
 * <pre>
 *   0,0   a tela abre no escuro e o sol nasce (brilho + raios + arcos)
 *   0,0   as faíscas chegam de longe e a silhueta se FORMA (até 3,4)
 *   0,4   "AVISO" no topo, com os dois riscos ao lado
 *   0,55  os OLHOS abrem no rosto
 *   3,4   a pessoa está formada; ondas de calor saem do rosto
 *   3,6   o texto entra digitado (24 letras/s)
 *   fim   tudo apaga
 * </pre>
 *
 * <p>Sem mouse no jogo: a "inclinação" da silhueta (que no protótipo seguia o
 * cursor) vem de um vaivém lento, e os olhos olham pra esse mesmo lado.
 *
 * <p>Regra de ouro do mod: qualquer erro DESLIGA o aviso, nunca o jogo.
 */
public final class AllForOneAvisoFx {

    private AllForOneAvisoFx() {
    }

    // ------------------------------------------------------------- constantes
    private static final float DUR = 12.5f;
    private static final float T_FORMACAO = 3.4f;   // a pessoa termina de se formar
    private static final float T_TEXTO = 3.6f;
    private static final float CPS = 24f;           // letras por segundo

    private static final int VERMELHO = 0xB01E0E;
    private static final int LARANJA = 0xEC5E0C;
    private static final int AMARELO = 0xFFB208;
    private static final int CLARO = 0xFFE846;
    private static final int NUCLEO = 0xFFFCCD;

    // espaço do protótipo
    private static final float DW = 960f, DH = 640f;
    private static final float SW = 420f, SH = 560f;
    private static final float FIG_X = (DW - SW) / 2f;
    private static final float FIG_Y = DH - SH + 40f;
    private static final float HEAD_X = FIG_X + 210f;
    private static final float HEAD_Y = FIG_Y + 150f;

    private static final float[] CAM_ESCALA = {1.14f, 1.07f, 1.00f, 0.90f, 0.72f};
    private static final float[] CAM_AMP = {11f, 8f, 6f, 4f, 3f};
    private static final float[] CAM_FREQ = {0.021f, 0.027f, 0.033f, 0.040f, 0.050f};

    private static final int N_PART = 240;
    private static final int N_BRASAS = 70;

    private static final String[] FRASES = {
            "olhe para mim", "eu sempre estive aqui", "você me ouve?", "não era a sua voz",
            "sou eu", "finalmente você me vê", "não desvie o olhar", "eu falo desde o início",
            "estou perto", "ouça com atenção"
    };

    // ---------------------------------------------------------------- estado
    private static long inicio = -1L;
    private static float tAnt;
    private static String msg = "AS VOZES NA SUA CABEÇA";
    private static float tremor;
    private static float proxSussurro = T_FORMACAO + 0.4f;
    private static int ultimoPulso = -1;

    private static final class Particula {
        float sx, sy, tx, ty, x, y, idade, dur, vida, fase, tam, vel;
        boolean chegando;   // true = voando até a silhueta
        int tipo;
    }

    private static final Particula[] PARTS = new Particula[N_PART];
    private static final float[] BRA_X = new float[N_BRASAS];
    private static final float[] BRA_Y = new float[N_BRASAS];
    private static final float[] BRA_V = new float[N_BRASAS];
    private static final float[] BRA_F = new float[N_BRASAS];

    private static final List<Sussurro> SUSSURROS = new ArrayList<>();
    private static final Random SORTE = new Random();

    private static final class Sussurro {
        String s;
        float x, y, idade, vida;
    }

    static {
        for (int i = 0; i < N_PART; i++) {
            PARTS[i] = new Particula();
        }
        for (int i = 0; i < N_BRASAS; i++) {
            BRA_X[i] = SORTE.nextFloat() * DW;
            BRA_Y[i] = SORTE.nextFloat() * DH;
            BRA_V[i] = 15f + SORTE.nextFloat() * 40f;
            BRA_F[i] = SORTE.nextFloat() * 6.28f;
        }
    }

    // ------------------------------------------------------------- ciclo de vida
    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_allforone"),
                AllForOneAvisoFx::render);
    }

    public static void iniciar(String mensagem) {
        msg = (mensagem == null || mensagem.isBlank()) ? "AS VOZES NA SUA CABEÇA"
                : mensagem.replaceAll("\u00a7.", "").toUpperCase(java.util.Locale.ROOT);
        inicio = System.nanoTime();
        tAnt = 0f;
        tremor = 0f;
        ultimoPulso = -1;
        proxSussurro = T_FORMACAO + 0.4f;
        SUSSURROS.clear();
        for (Particula p : PARTS) {
            nascer(p, 0f);
            p.idade = p.chegando ? -SORTE.nextFloat() * 2.4f : SORTE.nextFloat() * p.vida;
        }
    }

    public static void parar() {
        inicio = -1L;
    }

    // ---------------------------------------------------------------- partículas
    private static void nascer(Particula p, float t) {
        // ponto dentro da silhueta (sorteia até cair dentro do perfil)
        for (int tent = 0; tent < 40; tent++) {
            float px = SORTE.nextFloat() * SW, py = SORTE.nextFloat() * SH;
            if (py > 30f && px > SW / 2f - perfil(py) && px < SW / 2f + perfil(py)) {
                p.tx = FIG_X + px;
                p.ty = FIG_Y + py;
                break;
            }
        }
        p.fase = SORTE.nextFloat() * 6.28f;
        float r = SORTE.nextFloat();
        p.tipo = r < 0.5f ? 0 : (r < 0.85f ? 1 : 2);
        p.tam = 8f + SORTE.nextFloat() * 14f;
        p.idade = 0f;
        if (t < T_FORMACAO - 0.8f) {              // energia chegando de longe
            float a = SORTE.nextFloat() * 6.283f, rad = 380f + SORTE.nextFloat() * 250f;
            p.sx = HEAD_X + cos(a) * rad;
            p.sy = HEAD_Y + sin(a) * rad * 0.8f;
            p.dur = 1.2f + SORTE.nextFloat() * 1.6f;
            p.chegando = true;
            p.x = p.sx;
            p.y = p.sy;
        } else {                                   // chama estável
            p.chegando = false;
            p.x = p.tx;
            p.y = p.ty;
            p.vida = 0.7f + SORTE.nextFloat() * 1.1f;
            p.vel = 25f + SORTE.nextFloat() * 55f;
        }
    }

    /** meia-largura da silhueta em função da altura (cabeça → ombros → tronco). */
    private static float perfil(float y) {
        if (y < 38f) {
            return 0f;
        }
        if (y < 215f) {
            float u = (215f - y) / 177f;
            return 112f * (float) Math.pow(Math.max(0f, 1f - u * u), 0.6f);
        }
        if (y < 380f) {
            return lerp(112f, 192f, eo((y - 215f) / 165f));
        }
        return 192f;
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
            float ox = w / 2f - DW / 2f * s;
            float oy = h / 2f - DH / 2f * s;

            atualizar(t, passo);

            float entra = ss(0.02f, 1.2f, t);
            float sai = 1f - ss(DUR - 1.4f, DUR - 0.2f, t);
            float env = entra * sai;
            float formP = cl(t / T_FORMACAO);

            float tremX = tremor > 0f ? (SORTE.nextFloat() - 0.5f) * 5f * tremor * s : 0f;
            float tremY = tremor > 0f ? (SORTE.nextFloat() - 0.5f) * 5f * tremor * s : 0f;

            float cx = ox + HEAD_X * s + tremX;
            float cy = oy + HEAD_Y * s + tremY;

            preparar(g);
            Buf b = new Buf(g.pose().last().pose());

            // ---------------- fundo vermelho de papel
            b.rect(0, 0, w, h, col(0x420808, 0.96f * env));
            b.brilho(cx, cy, 640f * s, 0xFAB212, 0.55f * env);
            b.brilho(cx, cy, 420f * s, 0xC4460E, 0.55f * env);
            b.brilho(cx, cy, 240f * s, 0xFFEC78, 0.35f * env);
            // "pergaminho": manchas irregulares escuras (o ruído do protótipo)
            for (int i = 0; i < 40; i++) {
                float px = ox + (DW * fr(sin(i * 12.9898f) * 43758.5453f)) * s;
                float py = oy + (DH * fr(sin(i * 78.233f) * 12345.678f)) * s;
                float raio = (40f + 90f * fr(sin(i * 3.7f) * 999f)) * s;
                b.brilho(px, py, raio, 0x3A0402, 0.10f * env);
            }

            // ---------------- o SOL pintado
            float fsol = eo(t / 1.5f) * env;
            if (fsol > 0.01f) {
                b.brilho(cx, cy, 330f * s, 0xFFFFF0, 0.30f * fsol);
                b.brilho(cx, cy, 200f * s, 0xFFEE78, 0.35f * fsol);
                for (int i = 0; i < 13; i++) {
                    float base = i * TAU / 13f + t * 0.05f + sin(i * 12.9898f) * 0.12f;
                    float len = 120f + 90f * fr(sin(i * 78.233f) * 43758.5453f) + 12f * sin(t * 1.6f + i);
                    for (int k = 0; k < 6; k++) {
                        float a = base + (k - 2.5f) * 0.028f;
                        float r0 = 178f + (k % 3) * 6f;
                        float r1 = r0 + len * (0.55f + 0.45f * fr(sin((i * 7 + k) * 3.77f) * 9999f));
                        float al = (0.55f + 0.35f * fr(sin((i + k) * 5.1f) * 99f)) * fsol;
                        float esp = (2f + 3.5f * fr(sin((i * 3 + k) * 2.9f) * 77f)) * s;
                        int c = col(0xFFE214, al);
                        b.seg(cx + cos(a) * r0 * s, cy + sin(a) * r0 * s,
                                cx + cos(a) * r1 * s, cy + sin(a) * r1 * s, Math.max(1f, esp), c, c);
                        if (k == 2 || k == 3) {   // fiapos tremidos na ponta
                            float jx = sin(t * 6f + i + k) * 5f * s, jy = cos(t * 5f + i * k) * 5f * s;
                            b.seg(cx + cos(a) * r1 * s, cy + sin(a) * r1 * s,
                                    cx + cos(a) * (r1 + 22f) * s + jx, cy + sin(a) * (r1 + 22f) * s + jy,
                                    Math.max(1f, 1.2f * s), c, c);
                        }
                    }
                }
                // anel branco de pinceladas (arcos)
                for (int k = 0; k < 70; k++) {
                    float hh = fr(sin(k * 91.17f) * 4375.5f);
                    float rr = (148f + 34f * fr(sin(k * 17.3f) * 931.7f)) * s;
                    float a0 = hh * TAU + t * (8f + (k % 5) * 3f) * (k % 2 == 0 ? 1f : -1f) * DEG;
                    float ext = (20f + 60f * fr(sin(k * 3.3f) * 77.7f)) * DEG;
                    float al = (0.45f + 0.45f * fr(sin(k * 8.8f) * 55.5f)) * fsol;
                    float esp = Math.max(1f, (1.5f + 4f * fr(sin(k * 5.9f) * 33.1f)) * s);
                    arco(b, cx, cy, rr, a0, ext, esp, col(0xFFFFEE, al), 10);
                }
            }

            // ---------------- pulso de calor saindo do rosto
            if (t > T_FORMACAO) {
                float u = (t - T_FORMACAO) / 3.2f;
                float k = fr(u);
                if ((int) Math.floor(u) != ultimoPulso) {
                    ultimoPulso = (int) Math.floor(u);
                    tremor = 1f;
                }
                float r = (90f + 330f * k) * s;
                float al = 0.6f * (1f - k) * (1f - k) * env;
                b.anel(cx, cy, r, 64, Math.max(1f, (1f + 5f * (1f - k)) * s), 0f, 0xFFFAC8, al);
            }

            // ---------------- a SILHUETA em chamas (5 camadas que tremem)
            float aForm = eo(formP);
            float inclinacao = sin(t * 0.55f) * 10f;   // no protótipo vinha do mouse
            for (int i = 0; i < 5; i++) {
                float al = cl(aForm * (1.2f - i * 0.08f)) * env * (i == 4 ? 0.55f : 0.95f);
                if (al <= 0.01f) {
                    continue;
                }
                int cor = switch (i) {
                    case 0 -> VERMELHO;
                    case 1 -> LARANJA;
                    case 2 -> AMARELO;
                    case 3 -> CLARO;
                    default -> NUCLEO;
                };
                camada(b, ox, oy, s, CAM_ESCALA[i] * (0.94f + 0.06f * aForm), CAM_AMP[i],
                        CAM_FREQ[i], i * 1.7f, inclinacao, cor, al, tremX, tremY);
            }

            // ---------------- olhos
            float aOlho = cl((formP - 0.55f) / 0.45f);
            if (aOlho > 0.02f) {
                float ph = fr(t / 4.6f) * 4.6f;
                float aberto = aOlho;
                if (ph < 0.16f) {
                    aberto *= Math.abs(ph - 0.08f) / 0.08f;   // piscada
                }
                float dx = sin(t * 0.5f) * 8f * s, dy = cos(t * 0.4f) * 4f * s;
                for (int lado = -1; lado <= 1; lado += 2) {
                    float ex1 = cx + lado * 54f * s + dx, ey1 = cy - 14f * s + dy;
                    float ex2 = cx + lado * 10f * s + dx, ey2 = cy + 8f * s + dy;
                    float mx = (ex1 + ex2) / 2f, my = (ey1 + ey2) / 2f;
                    float hh = 13f * aberto * s;
                    b.brilho(mx, my, Math.max(3f, 22f * s), 0xFFFFBE, 0.35f * aberto * env);
                    // amêndoa: dois triângulos curvos
                    int c = col(0xFFFFEE, 0.97f * aberto * env);
                    b.tri(ex1, ey1, c, mx, my - hh, c, ex2, ey2, c);
                    b.tri(ex1, ey1, c, mx, my + hh * 0.5f, c, ex2, ey2, c);
                    b.seg(ex1, ey1, ex2, ey2, Math.max(1f, 1.2f * s),
                            col(0x8C3200, 0.55f * aberto * env), col(0x8C3200, 0.55f * aberto * env));
                }
            }

            // ---------------- faíscas (convergem e depois sobem como chama)
            for (Particula p : PARTS) {
                if (p.chegando && p.idade < 0f) {
                    continue;
                }
                float a, sz;
                if (p.chegando) {
                    float k = cl(p.idade / p.dur);
                    a = 0.15f + 0.6f * k;
                    sz = p.tam * (0.6f + 0.4f * k);
                } else {
                    float k = cl(p.idade / p.vida);
                    a = sin((float) Math.PI * k) * 0.8f;
                    sz = p.tam * (1f - 0.55f * k);
                }
                if (p.tipo == 2) {
                    sz *= 0.6f;
                }
                int cor = p.tipo == 0 ? 0xFFE45A : (p.tipo == 1 ? 0xFF8C1E : 0xFFFFE0);
                b.brilho(ox + p.x * s + tremX, oy + p.y * s + tremY, Math.max(1f, sz * s), cor, cl(a) * env);
            }

            // ---------------- brasas soltas
            for (int i = 0; i < N_BRASAS; i++) {
                float sz = (3f + 2.5f * sin(t * 5f + BRA_F[i])) * s;
                b.brilho(ox + BRA_X[i] * s, oy + BRA_Y[i] * s, Math.max(1f, sz), 0xFFE45A, 0.45f * env);
            }

            b.vinheta(w, h, Math.max(50f, 110f * s), 0x1A0000, 0.55f * env);
            b.flush();
            normal();

            // ---------------- sussurros
            for (Sussurro su : SUSSURROS) {
                float k = cl(su.idade / su.vida);
                float a = (float) Math.pow(Math.sin(Math.PI * k), 1.5) * 0.9f * env;
                if (a < 0.02f) {
                    continue;
                }
                float esc = Math.max(0.9f, 1.9f * s);
                txt(g, f, su.s, ox + su.x * s + sin(t * 9f + su.x) * 1.2f * s, oy + su.y * s,
                        esc, (int) (a * 255) << 24 | (0xFFF6D6 & 0xFFFFFF));
            }

            // ================= UI
            // título AVISO com os dois riscos
            float ft = eo((t - 0.4f) / 1.0f) * env * (0.85f + 0.15f * sin(t * 9f) * (sin(t * 1.3f) > 0.8f ? 1f : 0.2f));
            if (ft > 0.02f) {
                tituloEspacado(g, f, "AVISO", w / 2f, oy + 50f * s - (oy + 50f * s > h * 0.16f ? 0f : 0f),
                        Math.max(1.0f, 1.9f * s), (int) (ft * 255) << 24 | (0xFFFAD7 & 0xFFFFFF), 8f * s);
                preparar(g);
                Buf b2 = new Buf(g.pose().last().pose());
                int c = col(0xFFFAD7, 0.8f * ft);
                float yy = oy + 43f * s;
                b2.seg(w / 2f - 220f * s, yy, w / 2f - 90f * s, yy, Math.max(1f, 1.5f * s), c, c);
                b2.seg(w / 2f + 90f * s, yy, w / 2f + 220f * s, yy, Math.max(1f, 1.5f * s), c, c);
                b2.flush();
                normal();
            }

            // faixa escura do rodapé
            float p0 = eo((t - T_FORMACAO + 0.6f) / 1.2f) * env;
            if (p0 > 0.02f) {
                preparar(g);
                Buf b3 = new Buf(g.pose().last().pose());
                float topo = h - 200f * s;
                b3.quad(0, topo, col(0x190400, 0f), w, topo, col(0x190400, 0f),
                        w, h, col(0x120200, 0.93f * p0), 0, h, col(0x120200, 0.93f * p0));
                b3.rect(0, h - 70f * s, w, 70f * s, col(0x120200, 0.55f * p0));
                b3.flush();
                normal();
            }

            // texto digitado
            if (t >= T_TEXTO) {
                String[] linhas = {msg, "SEMPRE FORAM UMA PESSOA.", "Agora ela tem forma. E está olhando para você."};
                int restante = (int) ((t - T_TEXTO) * CPS);
                float[] esc = {Math.max(1.1f, 2.1f * s), Math.max(1.1f, 2.1f * s), Math.max(0.85f, 1.15f * s)};
                int[] cores = {0xFFF1C2, 0xFFE23C, 0xFFE8BE};
                float[] ys = {h - 112f * s, h - 68f * s, h - 30f * s};
                for (int i = 0; i < linhas.length && restante > 0; i++) {
                    int n = Math.min(restante, linhas[i].length());
                    restante -= linhas[i].length();
                    String trecho = linhas[i].substring(0, n);
                    float lar = f.width(trecho) * esc[i];
                    if (lar > w * 0.94f) {
                        esc[i] = Math.max(0.8f, w * 0.94f / Math.max(1, f.width(trecho)));
                        lar = f.width(trecho) * esc[i];
                    }
                    float yy = Math.min(ys[i], h - 12f);
                    if (i < 2) {
                        txt(g, f, trecho, w / 2f - lar / 2f - 1f * s, yy, esc[i],
                                (int) (env * 120) << 24 | (0xFF8200 & 0xFFFFFF));
                    }
                    txt(g, f, trecho, w / 2f - lar / 2f, yy, esc[i],
                            (int) (env * 245) << 24 | (cores[i] & 0xFFFFFF));
                }
            }

            // escurecer do começo e do fim
            float preto = (1f - eo(t / 1.2f)) * 0.9f + (1f - sai) * 0.9f;
            if (preto > 0.02f) {
                preparar(g);
                Buf b4 = new Buf(g.pose().last().pose());
                b4.rect(0, 0, w, h, col(0x000000, cl(preto)));
                b4.flush();
                normal();
            }
        } catch (Throwable t) {
            inicio = -1L;
            dev.rptag.RPTagMod.LOGGER.warn("RP Tag: aviso ALLFORONE desligado ({})", t.toString());
        }
    }

    // --------------------------------------------------------------- atualizar
    private static void atualizar(float t, float passo) {
        tremor = Math.max(0f, tremor - passo * 5f);

        for (Particula p : PARTS) {
            p.idade += passo;
            if (p.chegando) {
                if (p.idade < 0f) {
                    p.x = p.sx;
                    p.y = p.sy;
                    continue;
                }
                float k = p.idade / p.dur;
                if (k >= 1f) {
                    p.chegando = false;
                    p.idade = 0f;
                    p.x = p.tx;
                    p.y = p.ty;
                    p.vida = 0.7f + SORTE.nextFloat() * 1.1f;
                    p.vel = 25f + SORTE.nextFloat() * 55f;
                } else {
                    float e = k * k * (3 - 2 * k);
                    float redemoinho = (1f - e) * sin(k * 6f + p.fase) * 45f;
                    p.x = p.sx + (p.tx - p.sx) * e + redemoinho;
                    p.y = p.sy + (p.ty - p.sy) * e + redemoinho * 0.5f;
                }
            } else {
                p.x += (sin(p.idade * 7f + p.fase) * 18f) * passo;
                p.y -= p.vel * passo;
                if (p.idade > p.vida) {
                    nascer(p, t);
                }
            }
        }

        for (int i = 0; i < N_BRASAS; i++) {
            BRA_Y[i] -= BRA_V[i] * passo;
            BRA_X[i] += sin(t * 2f + BRA_F[i]) * 10f * passo;
            if (BRA_Y[i] < -10f) {
                BRA_Y[i] = DH + 10f;
                BRA_X[i] = SORTE.nextFloat() * DW;
            }
        }

        if (t > proxSussurro && SUSSURROS.size() < 5) {
            Sussurro su = new Sussurro();
            su.s = FRASES[SORTE.nextInt(FRASES.length)];
            boolean esq = SORTE.nextBoolean();
            su.x = esq ? 40f + SORTE.nextFloat() * 200f : 660f + SORTE.nextFloat() * 200f;
            su.y = 130f + SORTE.nextFloat() * 300f;
            su.vida = 3.6f + SORTE.nextFloat();
            SUSSURROS.add(su);
            proxSussurro = t + 0.9f + SORTE.nextFloat() * 1.2f;
        }
        for (int i = SUSSURROS.size() - 1; i >= 0; i--) {
            Sussurro su = SUSSURROS.get(i);
            su.idade += passo;
            su.y -= 7f * passo;
            if (su.idade > su.vida) {
                SUSSURROS.remove(i);
            }
        }
    }

    // ---------------------------------------------------------------- desenho
    /** uma camada da silhueta: tiras horizontais que tremem (o "drawWobble" do protótipo). */
    private static void camada(Buf b, float ox, float oy, float s, float escala, float amp,
            float freq, float fase, float inclinacao, int cor, float a, float tremX, float tremY) {
        float dx = FIG_X + SW * (1f - escala) / 2f;
        float dy = FIG_Y + SH * (1f - escala);
        float passo = 8f;
        for (float y = 0; y < SH; y += passo) {
            float topo = 1f - y / SH;
            float hw = perfil(y + passo * 0.5f);
            if (hw <= 0.5f) {
                continue;
            }
            float off = sin(y * freq + tAnt * 2.2f + fase) * amp * (0.30f + topo)
                    + sin(y * freq * 2.3f - tAnt * 3.1f + fase) * amp * 0.4f * topo
                    + inclinacao * topo * topo;
            float x1 = dx + off * escala - hw * escala;
            float y1 = dy + y * escala;
            float ww = 2f * hw * escala;
            float hh = passo * escala + 0.7f;
            b.rect(ox + x1 * s + tremX, oy + y1 * s + tremY, ww * s, hh * s, col(cor, a));
        }
    }

    /** arco de círculo aproximado por segmentos. */
    private static void arco(Buf b, float cx, float cy, float r, float a0, float ext, float esp,
            int cor, int passos) {
        int n = Math.max(2, passos);
        for (int i = 0; i < n; i++) {
            float u0 = a0 + ext * i / n, u1 = a0 + ext * (i + 1) / n;
            b.seg(cx + cos(u0) * r, cy + sin(u0) * r, cx + cos(u1) * r, cy + sin(u1) * r, esp, cor, cor);
        }
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
