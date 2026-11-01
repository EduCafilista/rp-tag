package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import java.util.Random;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * AVISO ENERGIA — estilo 15 do /rp admin avisar (5.5.0).
 *
 * <p>Porte fiel do protótipo do dono (catbox {@code ywqg53.java},
 * {@code AvisoEnergiaOP}): a ENERGIA de Ordem Paranormal — caos, transformação,
 * inconsistência. Raios, plasma roxo, glitch e formas que não param quietas.
 *
 * <h2>Roteiro (em segundos)</h2>
 * <pre>
 *   0,0   CRT ligando: a tela abre numa fresta de luz e ganha scanlines
 *   0,5   o núcleo pulsa (4 formas que trocam de lobos sozinhas) e os primeiros raios saem dele
 *   0,7   "AVISO" com ícone de perigo e os cantos de cobre
 *   1,6   o texto DECODIFICA letra por letra (glifos aleatórios até resolver)
 *   6,0   o medidor de ENTROPIA entra e vive oscilando (55%..93%)
 *   ~2-10 glitches aleatórios (fatias deslocadas, tremor, estouro branco)
 *   fim   as voltas somem, o medidor cai e a tela apaga
 * </pre>
 *
 * <p>Sem mouse no jogo (o protótipo atraía raios pro cursor): aqui os raios
 * caem em volta do núcleo e nas bordas, que é o que dá a mesma sensação.
 *
 * <p>Regra de ouro do mod: qualquer erro DESLIGA o aviso, nunca o jogo.
 */
public final class EnergiaAvisoFx {

    private EnergiaAvisoFx() {
    }

    // ------------------------------------------------------------- constantes
    private static final float DUR = 11.0f;
    private static final float T_TEXTO = 1.6f;
    private static final float T_ENTROPIA = 4.0f;

    private static final int MG = 0xFF46EB;   // magenta
    private static final int VI = 0x9646FF;   // violeta
    private static final int CI = 0x5AE1FF;   // ciano elétrico
    private static final int BR = 0xFFF5FF;   // núcleo branco
    private static final int CO = 0xD68248;   // cobre (a "assinatura" da Energia)
    private static final String GLIFOS = "#%&01<>/\\|+*?!=~^";

    private static final int N_RAIOS = 14;    // raios vivos ao mesmo tempo
    private static final int N_FAISCAS = 180;
    private static final int N_ANEIS = 10;

    // ---------------------------------------------------------------- estado
    private static long inicio = -1L;
    private static float tAnt = 0.0f;
    private static String msg = "ENERGIA INSTÁVEL";
    private static boolean somEntrada, somTexto, somFim;
    private static float proxClarao, proximoRaio, proximoCaos, proxGlitch;
    private static float clarao, tremor, glitch;

    private static final class Raio {
        float[] seg = new float[0];
        int n;
        float vida, max, peso;
        int cor;
    }

    private static final class Faisca {
        float x, y, px, py, vx, vy, vida, max;
        int tipo;
    }

    private static final class Anel {
        float x, y, idade;
    }

    private static final Raio[] RAIOS = new Raio[N_RAIOS];
    private static final Faisca[] FAISCAS = new Faisca[N_FAISCAS];
    private static final Anel[] ANEIS = new Anel[N_ANEIS];
    private static final Random SORTE = new Random();

    static {
        for (int i = 0; i < N_RAIOS; i++) {
            RAIOS[i] = new Raio();
        }
        for (int i = 0; i < N_FAISCAS; i++) {
            FAISCAS[i] = new Faisca();
            FAISCAS[i].vida = 0f;
        }
        for (int i = 0; i < N_ANEIS; i++) {
            ANEIS[i] = new Anel();
            ANEIS[i].idade = 99f;
        }
    }

    // ------------------------------------------------------------- ciclo de vida
    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_energia"),
                EnergiaAvisoFx::render);
    }

    public static void iniciar(String mensagem) {
        msg = (mensagem == null || mensagem.isBlank()) ? "ENERGIA INSTÁVEL"
                : mensagem.replaceAll("\u00a7.", "").toUpperCase(java.util.Locale.ROOT);
        inicio = System.nanoTime();
        tAnt = 0f;
        somEntrada = somTexto = somFim = false;
        proxClarao = 0.6f;
        proximoRaio = 0.45f;
        proximoCaos = 1.4f;
        proxGlitch = 2.2f;
        clarao = tremor = glitch = 0f;
        for (Raio r : RAIOS) {
            r.vida = 0f;
        }
        for (Anel a : ANEIS) {
            a.idade = 99f;
        }
        // (5.5.0) as faíscas nascem espalhadas (sem "pulo" no primeiro quadro)
        for (Faisca f : FAISCAS) {
            nascerFaisca(f, 480f, 285f);
            f.vida = (float) (SORTE.nextDouble() * f.max);
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
            float s = Math.min(w / 960f, h / 640f);
            float cx = w / 2f;
            float cy = h * 0.46f;

            atualizar(t, passo, cx, cy, s);
            sons(t);

            float entra = ss(0.05f, 0.55f, t);
            float sai = 1f - ss(DUR - 1.1f, DUR - 0.15f, t);
            float env = entra * sai;

            // ===================== fundo (sempre em coords de tela)
            preparar(g);
            Buf b = new Buf(g.pose().last().pose());

            // fundo escuro roxo (gradiente por discos, sem retângulo "chapado")
            float fundo = 0.86f * env;
            b.rect(0, 0, w, h, col(0x05020C, fundo));
            b.brilho(cx, cy, Math.max(w, h) * 0.62f, 0x2A0A44, 0.55f * env);
            b.brilho(cx, cy, Math.max(w, h) * 0.34f, 0x3A0C5E, 0.40f * env);

            // plasma lento (12 bolhas roxas passeando)
            for (int i = 0; i < 10; i++) {
                float px = cx + s * 340f * sin(t * 0.13f * (i % 5 + 1) + i * 1.7f);
                float py = cy + s * 210f * cos(t * 0.11f * (i % 4 + 1) + i * 2.3f);
                float sz = s * (300f + 160f * sin(t * 0.3f + i));
                b.brilho(px, py, sz * 0.5f, i % 2 == 0 ? MG : VI, 0.10f * env);
            }

            // ===================== HUD circular (medidor de entropia)
            float hudA = ss(0.4f, 1.2f, t) * env;
            if (hudA > 0.01f) {
                anelTracejado(b, cx, cy, s * 205f, 40, 5f, s * 2.2f, t * 26f, CO, 0.45f * hudA);
                anelTracejado(b, cx, cy, s * 225f, 56, 4f, s * 1.8f, -t * 40f, VI, 0.40f * hudA);
                for (int i = 0; i < 60; i++) {
                    float a = (float) Math.toRadians(i * 6f + t * 3f);
                    float r0 = s * 238f;
                    float r1 = s * ((i % 6 == 0) ? 254f : 246f);
                    int c = ((i % 6 == 0) ? CO : VI);
                    b.seg(cx + cos(a) * r0, cy + sin(a) * r0, cx + cos(a) * r1, cy + sin(a) * r1,
                            s * ((i % 6 == 0) ? 2.2f : 1.4f), col(c, 0.55f * hudA), col(c, 0.55f * hudA));
                }
            }

            // ===================== raios
            for (Raio r : RAIOS) {
                if (r.vida <= 0f || r.n == 0) {
                    continue;
                }
                float a = cl(r.vida / r.max);
                for (int pass = 0; pass < 2; pass++) {
                    float alfa = pass == 0 ? 0.22f * a : 0.85f * a;
                    float larg = pass == 0 ? r.peso * 5.2f * s : r.peso * 1.4f * s;
                    int cc = pass == 0 ? (r.cor == 2 ? CI : VI) : BR;
                    for (int i = 0; i < r.n; i++) {
                        int o = i * 4;
                        b.seg(r.seg[o] * s + cx - 480f * s, r.seg[o + 1] * s + cy - 285f * s,
                                r.seg[o + 2] * s + cx - 480f * s, r.seg[o + 3] * s + cy - 285f * s,
                                larg, col(cc, alfa), col(cc, alfa));
                    }
                }
            }

            // ===================== faíscas
            for (Faisca fa : FAISCAS) {
                if (fa.vida <= 0f) {
                    continue;
                }
                float a = cl(fa.vida / fa.max);
                int c = fa.tipo == 0 ? MG : fa.tipo == 1 ? VI : fa.tipo == 2 ? CI : BR;
                b.seg(fa.px * s + cx - 480f * s, fa.py * s + cy - 285f * s,
                        fa.x * s + cx - 480f * s, fa.y * s + cy - 285f * s,
                        s * 1.8f, col(c, a), col(c, a * 0.5f));
            }

            // ===================== núcleo
            nuclo(b, t, cx, cy, s, env);

            // ===================== anéis de descarga
            for (Anel an : ANEIS) {
                if (an.idade > 1f) {
                    continue;
                }
                float k = an.idade;
                float raio = s * (10f + 320f * eo(k));
                float a = (1f - k) * 0.8f;
                b.anel(an.x * s + cx - 480f * s, an.y * s + cy - 285f * s, raio, 64,
                        s * (1f + 6f * (1f - k)), 0f, MG, a);
            }

            // ===================== moldura (cantos de cobre + faísca correndo)
            moldura(b, t, w, h, s, env);

            // ===================== glitch: fatias deslocadas
            if (glitch > 0f) {
                for (int i = 0; i < 7; i++) {
                    float y0 = rnd(i, (int) (t * 12f)) * h;
                    float hh = (4f + 22f * rnd(i + 20, (int) (t * 12f))) * s;
                    b.rect(0, y0, w, hh, col(i % 2 == 0 ? CI : MG, 0.06f + 0.10f * glitch));
                }
            }

            b.flush();
            normal();

            // ===================== texto (aqui sim, escala de fonte)
            texto(g, f, t, w, h, s, cx, cy, env);

            // ===================== scanlines + vinheta + clarao + CRT
            preparar(g);
            Buf o = new Buf(g.pose().last().pose());
            for (int y = 0; y < h; y += 3) {
                o.rect(0, y, w, 1, 0x33000000);
            }
            o.vinheta(w, h, Math.min(w, h) * 0.42f, 0x000000, 0.62f * env);
            if (clarao > 0f) {
                o.rect(0, 0, w, h, col(BR, Math.min(0.55f, clarao * 0.55f)));
            }
            // CRT ligando: duas cortinas pretas se abrindo numa fresta
            float p = ss(0.12f, 0.85f, t);
            if (t < 0.95f) {
                float fresta = h / 2f * p;
                o.rect(0, 0, w, h / 2f - fresta, 0xFF000000);
                o.rect(0, h / 2f + fresta, w, h / 2f - fresta + 1, 0xFF000000);
                if (p < 0.45f) {
                    o.rect(0, h / 2f - 2f * s, w, 4f * s, col(BR, 1f - p * 2.2f));
                }
            }
            // fim: a tela apaga
            if (t > DUR - 1.0f) {
                o.rect(0, 0, w, h, col(0x000000, ss(DUR - 1.0f, DUR - 0.1f, t)));
            }
            o.flush();
            normal();
        } catch (Throwable erro) {
            inicio = -1L;
            dev.rptag.RPTagMod.LOGGER.error("RP Tag: aviso ENERGIA desligado (o jogo segue)", erro);
        }
    }

    // ------------------------------------------------------------------ peças
    private static void nuclo(Buf b, float t, float cx, float cy, float s, float env) {
        float pulso = 1f + 0.08f * sin(t * 6f) + 0.05f * sin(t * 13.7f);
        b.brilho(cx, cy, s * 310f * pulso, VI, 0.42f * env);
        b.brilho(cx, cy, s * 190f * pulso, MG, 0.55f * env);
        b.brilho(cx, cy, s * 100f * pulso, CI, 0.35f * env);
        b.brilho(cx, cy, s * 60f * pulso, BR, 0.80f * env);
        // 4 formas que nunca ficam iguais (lobos mudando de frequência)
        for (int anel = 0; anel < 4; anel++) {
            float r = s * (62f + anel * 26f);
            float kk = 3f + 2.5f * (1f + sin(t * 0.45f + anel * 1.3f));
            int k0 = (int) kk;
            float fr = ss(0f, 1f, kk - k0);
            float fase = t * (1.6f + anel * 0.7f);
            int n = 80;
            float rx0 = 0, ry0 = 0, rx1 = 0, ry1 = 0;
            boolean temAnt = false;
            for (int i = 0; i <= n; i++) {
                float th = i * TAU / n;
                float lobo = (1f - fr) * sin(k0 * th + fase) + fr * sin((k0 + 1) * th + fase);
                float rr = r * (1f + 0.14f * lobo + 0.07f * sin(7f * th - t * 2.4f)
                        + 0.02f * sin(i * 13.7f + t * 37f));
                float x = cx + cos(th) * rr;
                float y = cy + sin(th) * rr;
                if (temAnt) {
                    b.seg(rx0, ry0, x, y, s * (anel % 2 == 0 ? 2.0f : 1.3f),
                            col(anel % 2 == 0 ? BR : CI, 0.55f * env),
                            col(anel % 2 == 0 ? BR : CI, 0.55f * env));
                }
                rx0 = x;
                ry0 = y;
                temAnt = true;
            }
        }
    }

    private static void moldura(Buf b, float t, int w, int h, float s, float env) {
        float m = 22f * s;
        float L = 44f * s;
        float lw = 2.5f * s;
        int c = col(CO, 0.9f * env);
        b.seg(m, m, m + L, m, lw, c, c);
        b.seg(m, m, m, m + L, lw, c, c);
        b.seg(w - m, m, w - m - L, m, lw, c, c);
        b.seg(w - m, m, w - m, m + L, lw, c, c);
        b.seg(m, h - m, m + L, h - m, lw, c, c);
        b.seg(m, h - m, m, h - m - L, lw, c, c);
        b.seg(w - m, h - m, w - m - L, h - m, lw, c, c);
        b.seg(w - m, h - m, w - m, h - m - L, lw, c, c);
        // fio lilás do quadro
        float a2 = 0.30f * env;
        int cc = col(VI, a2);
        b.seg(m, m, w - m, m, 1f * s, cc, cc);
        b.seg(w - m, m, w - m, h - m, 1f * s, cc, cc);
        b.seg(w - m, h - m, m, h - m, 1f * s, cc, cc);
        b.seg(m, h - m, m, m, 1f * s, cc, cc);
        // faísca correndo pela borda
        float per = 2f * ((w - 2 * m) + (h - 2 * m));
        float pos = (t * 380f * s) % per;
        float[] px = new float[2];
        for (int i = 0; i < 10; i++) {
            perim(pos - i * 14f * s, w, h, m, px);
            float raio = s * (13f - i * 1.2f);
            if (raio <= 0.5f) {
                continue;
            }
            b.brilho(px[0], px[1], raio, i < 2 ? BR : CI, (0.9f - i * 0.07f) * env);
        }
    }

    /** posição (x,y) andando pelo perímetro a uma distância "d" do topo-esquerda. */
    private static void perim(float d, int w, int h, float m, float[] out) {
        float cw = w - 2 * m;
        float ch = h - 2 * m;
        float p = 2 * (cw + ch);
        d = ((d % p) + p) % p;
        if (d < cw) {
            out[0] = m + d;
            out[1] = m;
            return;
        }
        d -= cw;
        if (d < ch) {
            out[0] = m + cw;
            out[1] = m + d;
            return;
        }
        d -= ch;
        if (d < cw) {
            out[0] = m + cw - d;
            out[1] = m + ch;
            return;
        }
        d -= cw;
        out[0] = m;
        out[1] = m + ch - d;
    }

    private static void anelTracejado(Buf b, float cx, float cy, float r, int n, float vazio,
            float larg, float giro, int cor, float a) {
        for (int i = 0; i < n; i++) {
            float a0 = giro + i * TAU / n;
            float a1 = a0 + (TAU / n) * (1f - vazio / 10f);
            b.seg(cx + cos(a0) * r, cy + sin(a0) * r, cx + cos(a1) * r, cy + sin(a1) * r,
                    larg, col(cor, a), col(cor, a));
        }
    }

    // ------------------------------------------------------------------- texto
    private static void texto(GuiGraphics g, Font f, float t, int w, int h, float s,
            float cx, float cy, float env) {
        // ---- ícone + "AVISO"
        float fi = ss(0.7f, 1.3f, t) * env;
        if (fi > 0.02f) {
            iconeAviso(g, cx, h * 0.10f, s * 40f, fi, t);
            titulo(g, f, "AVISO", cx, h * 0.10f + s * 34f, s * 2.0f, fi, (int) (255 * fi) << 24 | (0xF0D7FF & 0xFFFFFF));
        }

        // ---- faixa de baixo (degradê escuro) para o texto respirar
        float pa = ss(1.1f, 1.9f, t) * env;
        if (pa > 0.02f) {
            preparar(g);
            Buf b = new Buf(g.pose().last().pose());
            int passos = 14;
            float hFaixa = h * 0.34f;
            for (int i = 0; i < passos; i++) {
                float u = i / (float) passos;
                b.rect(0, h - hFaixa + u * hFaixa, w, hFaixa / passos + 1f,
                        col(0x08010F, (0.10f + 0.55f * u) * pa));
            }
            b.flush();
            normal();
        }

        // ---- linha grande: a mensagem do comando, decodificando letra por letra
        float escBig = Math.max(1.6f, s * 3.4f);
        float largaMax = w * 0.92f;
        int largaFonte = Math.max(1, f.width(msg));
        if (largaFonte * escBig > largaMax) {
            escBig = Math.max(1.2f, largaMax / largaFonte);
        }
        String l1 = decodificar(t, msg, T_TEXTO, 0.055f);
        // (o bloco de texto sobe um pouco: assim os dois subtítulos NÃO
        //  encostam no medidor de entropia nem no "ENTENDIDO")
        float y1 = h * 0.66f;
        glitchTexto(g, f, l1, cx, y1, escBig, (int) (a8(env) ) << 24 | (0xF6E4FF & 0xFFFFFF), t, 10f * s);

        // ---- as duas linhas fixas do protótipo (menores, entram depois)
        float escMid = Math.max(1.0f, s * 1.55f);
        String l2 = decodificar(t, "ANOMALIA DE ENTROPIA DETECTADA NESTA ÁREA.", T_TEXTO + 1.0f, 0.03f);
        String l3 = decodificar(t, "A REALIDADE ESTÁ MUDANDO DE FORMA. NADA PERMANECE ESTÁVEL.", T_TEXTO + 2.2f, 0.03f);
        glitchTexto(g, f, l2, cx, y1 + s * 44f, escMid, (int) (a8(env) * 0.85f) << 24 | (0xD6B4FF & 0xFFFFFF), t, 0f);
        glitchTexto(g, f, l3, cx, y1 + s * 44f + escMid * 13f, escMid, (int) (a8(env) * 0.75f) << 24 | (0xBE96F0 & 0xFFFFFF), t, 0f);

        // ---- medidor de ENTROPIA
        float ga = ss(T_ENTROPIA, T_ENTROPIA + 0.8f, t) * env;
        if (ga > 0.02f) {
            float ent = cl(0.55f + 0.38f * Math.abs(sin(t * 0.9f) * cos(t * 0.31f)) + 0.04f * sin(t * 23f));
            int segs = 24;
            int acesos = Math.round(ent * segs);
            float x0 = Math.max(12f, s * 40f);
            float y0 = h - Math.max(26f, s * 52f);
            preparar(g);
            Buf b = new Buf(g.pose().last().pose());
            for (int i = 0; i < segs; i++) {
                float kk = i / (float) segs;
                boolean lit = i < acesos;
                int cor = lit ? mix(VI, MG, kk) : 0x5A3C82;
                b.rect(x0 + i * s * 12f, y0, s * 9f, s * 16f, col(cor, (lit ? 0.95f : 0.30f) * ga));
            }
            b.flush();
            normal();
            txt(g, f, "ENTROPIA", x0, y0 - s * 14f, Math.max(0.8f, s * 1.2f),
                    (int) (ga * 255) << 24 | (CO & 0xFFFFFF));
            txt(g, f, String.format("%02d%%", (int) (ent * 100f)),
                    x0 + segs * s * 12f + s * 10f, y0 + s * 4f, Math.max(0.8f, s * 1.2f),
                    (int) (ga * 255) << 24 | (0xF6E4FF & 0xFFFFFF));
            if (ent > 0.90f && ((int) (t * 6f)) % 2 == 0) {
                txt(g, f, "CRÍTICO", x0 + segs * s * 12f + s * 52f, y0 + s * 4f,
                        Math.max(0.8f, s * 1.2f), (int) (ga * 255) << 24 | (MG & 0xFFFFFF));
            }
        }

        // ---- o "botão" (decorativo no jogo: aviso não pede clique)
        if (t > 6.0f) {
            float ba = ss(6.0f, 6.5f, t) * env;
            float bw = s * 150f;
            float bh = s * 26f;
            float bx = w - bw - Math.max(14f, s * 40f);
            float by = h - bh - Math.max(14f, s * 34f);
            preparar(g);
            Buf b = new Buf(g.pose().last().pose());
            b.rect(bx, by, bw, bh, col(0x140428, 0.55f * ba));
            float lw = s * 1.8f;
            int c = col(CO, 0.9f * ba);
            b.seg(bx, by, bx + bw, by, lw, c, c);
            b.seg(bx + bw, by, bx + bw, by + bh, lw, c, c);
            b.seg(bx + bw, by + bh, bx, by + bh, lw, c, c);
            b.seg(bx, by + bh, bx, by, lw, c, c);
            b.flush();
            normal();
            String bt = "[ ENTENDIDO ]";
            txt(g, f, bt, bx + (bw - f.width(bt) * s * 1.2f) / 2f, by + bh * 0.32f,
                    Math.max(0.8f, s * 1.2f), (int) (ba * 255) << 24 | (0xFAEBFF & 0xFFFFFF));
        }
    }

    private static void iconeAviso(GuiGraphics g, float cx, float cy, float s, float a, float t) {
        preparar(g);
        Buf b = new Buf(g.pose().last().pose());
        b.brilho(cx, cy, s * 1.9f, MG, 0.35f * a * (0.75f + 0.25f * sin(t * 7f)));
        float[] xs = { cx, cx + s * 0.58f, cx - s * 0.58f };
        float[] ys = { cy - s * 0.5f, cy + s * 0.5f, cy + s * 0.5f };
        int c = col(BR, a);
        b.seg(xs[0], ys[0], xs[1], ys[1], Math.max(1.2f, s * 0.10f), c, c);
        b.seg(xs[1], ys[1], xs[2], ys[2], Math.max(1.2f, s * 0.10f), c, c);
        b.seg(xs[2], ys[2], xs[0], ys[0], Math.max(1.2f, s * 0.10f), c, c);
        // raio no meio do triângulo
        float k = s / 36f;
        b.seg(cx + 3 * k, cy - 12 * k, cx - 3 * k, cy + 1 * k, Math.max(1f, 2.6f * k), c, c);
        b.seg(cx - 3 * k, cy + 1 * k, cx + 4 * k, cy + 1 * k, Math.max(1f, 2.6f * k), c, c);
        b.seg(cx + 4 * k, cy + 1 * k, cx - 1 * k, cy + 13 * k, Math.max(1f, 2.6f * k), c, c);
        b.flush();
        normal();
    }

    private static void titulo(GuiGraphics g, Font f, String s, float cx, float y, float esc,
            float a, int cor) {
        // letras espaçadas (como o protótipo): 1 espaço e meio entre cada uma
        float espaco = 10f * esc;
        float total = 0f;
        for (int i = 0; i < s.length(); i++) {
            total += f.width(String.valueOf(s.charAt(i))) * esc + (i < s.length() - 1 ? espaco : 0f);
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

    private static void glitchTexto(GuiGraphics g, Font f, String s, float cx, float y, float esc,
            int cor, float t, float brilho) {
        if (((cor >>> 24) & 255) < 4) {
            return;
        }
        float x = cx - f.width(s) * esc / 2f;
        float desloc = (1.0f + glitch * 6f) * esc;
        if (brilho > 0f) {
            // cópia ciano à esquerda e magenta à direita (separação de cor)
            txt(g, f, s, x - desloc, y, esc, col(CI, 0.55f * ((cor >>> 24) & 255) / 255f));
            txt(g, f, s, x + desloc, y, esc, col(MG, 0.55f * ((cor >>> 24) & 255) / 255f));
        }
        txt(g, f, s, x, y, esc, cor);
    }

    /** texto que se resolve de glifos para letras (o "decode" do protótipo). */
    private static String decodificar(float t, String s, float inicio, float porLetra) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            float ti = inicio + i * porLetra;
            if (ch == ' ' || t < ti) {
                sb.append(' ');
            } else if (t < ti + 0.30f) {
                sb.append(GLIFOS.charAt((int) (rnd(i, (int) (t * 18f)) * GLIFOS.length())));
            } else if (glitch > 0f && rnd(i + 100, (int) (t * 18f)) < 0.07f) {
                sb.append(GLIFOS.charAt((int) (rnd(i + 7, (int) (t * 18f)) * GLIFOS.length())));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static int a8(float env) {
        return (int) (255 * cl(env));
    }

    // ------------------------------------------------------------------ update
    private static void atualizar(float t, float passo, float cx, float cy, float s) {
        float CXv = 480f, CYv = 285f;

        // faíscas: caos + atração pro núcleo
        for (Faisca fa : FAISCAS) {
            fa.vida -= passo;
            if (fa.vida <= 0f) {
                nascerFaisca(fa, CXv, CYv);
                continue;
            }
            fa.px = fa.x;
            fa.py = fa.y;
            fa.vx += (SORTE.nextFloat() - 0.5f) * 1500f * passo;
            fa.vy += (SORTE.nextFloat() - 0.5f) * 1500f * passo;
            float dx = CXv - fa.x;
            float dy = CYv - fa.y;
            float d = (float) Math.hypot(dx, dy) + 1f;
            if (d < 320f) {
                fa.vx += dx / d * 420f * passo;
                fa.vy += dy / d * 420f * passo;
            }
            fa.vx *= 1f - 0.9f * passo;
            fa.vy *= 1f - 0.9f * passo;
            fa.x += fa.vx * passo;
            fa.y += fa.vy * passo;
        }

        // raios novos saindo do núcleo
        if (t > 0.45f) {
            proximoRaio -= passo;
            if (proximoRaio <= 0f) {
                float ang = SORTE.nextFloat() * TAU;
                float r = 170f + SORTE.nextFloat() * 200f;
                criarRaio(CXv + cos(ang) * 60f, CYv + sin(ang) * 60f,
                        CXv + cos(ang) * r, CYv + sin(ang) * r * 0.85f,
                        SORTE.nextInt(3), 1.2f);
                proximoRaio = 0.06f + SORTE.nextFloat() * 0.20f;
            }
            proximoCaos -= passo;
            if (proximoCaos <= 0f) {
                // raio atravessando a tela inteira (a "anarquia" da Energia)
                if (SORTE.nextBoolean()) {
                    criarRaio(0, SORTE.nextFloat() * 640f, 960f, SORTE.nextFloat() * 640f, 2, 1.0f);
                } else {
                    criarRaio(SORTE.nextFloat() * 960f, 0, SORTE.nextFloat() * 960f, 640f, 0, 1.0f);
                }
                proximoCaos = 1.0f + SORTE.nextFloat() * 2.2f;
            }
        }

        // clarões (descargas) de vez em quando
        proxClarao -= passo;
        if (proxClarao <= 0f && t > 0.8f) {
            clarao = 0.9f;
            tremor = 1.4f;
            Anel an = anelLivre();
            if (an != null) {
                an.x = CXv + (SORTE.nextFloat() - 0.5f) * 300f;
                an.y = CYv + (SORTE.nextFloat() - 0.5f) * 200f;
                an.idade = 0f;
            }
            for (int i = 0; i < 7; i++) {
                float a = SORTE.nextFloat() * TAU;
                float len = 120f + SORTE.nextFloat() * 160f;
                criarRaio(an == null ? CXv : an.x, an == null ? CYv : an.y,
                        (an == null ? CXv : an.x) + cos(a) * len,
                        (an == null ? CYv : an.y) + sin(a) * len,
                        SORTE.nextInt(3), 1.5f);
            }
            proxClarao = 2.4f + SORTE.nextFloat() * 3.0f;
        }

        // glitch aleatório
        proxGlitch -= passo;
        if (proxGlitch <= 0f && t > 1.5f) {
            glitch = 0.18f + SORTE.nextFloat() * 0.2f;
            tremor = Math.max(tremor, 0.7f);
            proxGlitch = 1.8f + SORTE.nextFloat() * 3.0f;
        }

        for (Raio r : RAIOS) {
            if (r.vida > 0f) {
                r.vida -= passo;
            }
        }
        for (Anel an : ANEIS) {
            if (an.idade <= 1f) {
                an.idade += passo;
            }
        }
        glitch = Math.max(0f, glitch - passo);
        clarao = Math.max(0f, clarao - passo * 2.4f);
        tremor = Math.max(0f, tremor - passo * 3.4f);
    }

    private static Anel anelLivre() {
        for (Anel an : ANEIS) {
            if (an.idade > 1f) {
                return an;
            }
        }
        return null;
    }

    private static void nascerFaisca(Faisca fa, float cx, float cy) {
        float a = SORTE.nextFloat() * TAU;
        float r = 80f + SORTE.nextFloat() * 40f;
        float sp = 80f + SORTE.nextFloat() * 240f;
        fa.x = fa.px = cx + cos(a) * r;
        fa.y = fa.py = cy + sin(a) * r;
        fa.vx = cos(a) * sp;
        fa.vy = sin(a) * sp;
        fa.max = fa.vida = 0.6f + SORTE.nextFloat() * 1.4f;
        fa.tipo = SORTE.nextInt(4);
    }

    // um "raio" = linha quebrada recursiva
    private static void criarRaio(float x1, float y1, float x2, float y2, int cor, float peso) {
        Raio r = null;
        for (Raio cand : RAIOS) {
            if (cand.vida <= 0f) {
                r = cand;
                break;
            }
        }
        if (r == null) {
            return;                      // todos ocupados: paciência, é só um raio
        }
        float[] tmp = new float[4 * 64];
        int n = quebrar(tmp, 0, x1, y1, x2, y2, (float) Math.hypot(x2 - x1, y2 - y1) * 0.38f, 5);
        r.seg = new float[n * 4];
        System.arraycopy(tmp, 0, r.seg, 0, n * 4);
        r.n = n;
        r.max = r.vida = 0.10f + SORTE.nextFloat() * 0.16f;
        r.peso = peso;
        r.cor = cor;
    }

    private static int quebrar(float[] out, int n, float x1, float y1, float x2, float y2,
            float d, int prof) {
        if (n >= 60) {
            return n;
        }
        if (prof == 0) {
            out[n * 4] = x1;
            out[n * 4 + 1] = y1;
            out[n * 4 + 2] = x2;
            out[n * 4 + 3] = y2;
            return n + 1;
        }
        float mx = (x1 + x2) / 2f + (SORTE.nextFloat() - 0.5f) * d;
        float my = (y1 + y2) / 2f + (SORTE.nextFloat() - 0.5f) * d;
        n = quebrar(out, n, x1, y1, mx, my, d / 2f, prof - 1);
        n = quebrar(out, n, mx, my, x2, y2, d / 2f, prof - 1);
        if (prof >= 3 && SORTE.nextFloat() < 0.35f) {
            float ang = (float) Math.atan2(y2 - y1, x2 - x1) + (SORTE.nextFloat() - 0.5f) * 1.7f;
            float len = (float) Math.hypot(x2 - x1, y2 - y1) * 0.55f;
            n = quebrar(out, n, mx, my, mx + cos(ang) * len, my + sin(ang) * len, d / 2f, prof - 2);
        }
        return n;
    }

    // -------------------------------------------------------------------- sons
    private static void sons(float t) {
        if (!somEntrada && t >= 0.1f) {
            somEntrada = true;
            tocar(SoundEvents.BEACON_ACTIVATE, 0.6f, 0.5f);
        }
        if (!somTexto && t >= 1.5f) {
            somTexto = true;
            tocar(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 0.8f);
        }
        if (!somFim && t >= 6.0f) {
            somFim = true;
            tocar(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4f, 0.6f);
        }
        if (clarao > 0.85f) {
            tocar(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.6f + SORTE.nextFloat() * 0.4f, 0.18f);
        }
    }
}
