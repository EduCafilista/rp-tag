package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;
import static dev.rptag.client.FxDraw.*;

import java.util.Random;

import dev.rptag.RPTagMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * AVISO MORTE OP — estilo <b>20</b> (5.7.9).
 *
 * <p>Porte do protótipo <b>AvisoMorte</b> do dono (<code>OjqYnxyx.java</code>):
 * uma <b>espiral de três braços</b> girando atrás de um <b>relógio</b> de
 * ponteiros rápidos; no meio, um <b>crânio</b> que se forma, encara e depois
 * se desmancha em cinzas — enquanto o mundo enche de cinza caindo e ossos
 * passando. Fecha com "Tudo o que começa, termina. E recomeça.".
 *
 * <p>É o irmão "op" do estilo 4 (Linha Reta): lá é o monitor cardíaco, aqui é
 * o tempo. Os dois convivem — nada antigo foi substituído.
 */
public final class MorteOpAvisoFx {

    private MorteOpAvisoFx() {
    }

    private static final float DUR = 11.0F, TEXT_T = 2.6F, CPS = 22F;
    private static final float W = 960F, H = 640F;

    private static final int BONE = 0xE8E2D2, BONE2 = 0x9A927E;
    private static final int ASH = 0x6E6A62, RUST = 0x8B3A1E;

    private static final String[] LINES = {
            "TUDO O QUE COMEÇA, TERMINA.",
            "E recomeça."
    };
    private static final String[] FRASES = {
            "ninguém virá", "de novo", "você já sabia", "conte os minutos",
            "o tempo não perdoa", "mais uma volta", "não olhe pra trás"
    };

    private static final Random SORTE = new Random();

    private static boolean ativo, falhou;
    private static long inicio;
    private static String linha1 = LINES[0];

    // cinzas (240) e ossos (12)
    private static final int NCINZA = 200;
    private static final float[] ax = new float[NCINZA], ay = new float[NCINZA],
            av = new float[NCINZA], asz = new float[NCINZA];
    private static final int NOSSO = 12;
    private static final float[] ox = new float[NOSSO], oy = new float[NOSSO],
            ov = new float[NOSSO], oang = new float[NOSSO];

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_morte_op"),
                MorteOpAvisoFx::render);
    }

    public static void iniciar(String texto) {
        ativo = true;
        falhou = false;
        inicio = System.currentTimeMillis();
        linha1 = (texto == null || texto.isBlank()) ? LINES[0] : texto;
        for (int i = 0; i < NCINZA; i++) {
            ax[i] = SORTE.nextFloat() * W;
            ay[i] = SORTE.nextFloat() * H;
            av[i] = 6F + SORTE.nextFloat() * 22F;
            asz[i] = 0.8F + SORTE.nextFloat() * 2.0F;
        }
        for (int i = 0; i < NOSSO; i++) {
            ox[i] = SORTE.nextFloat() * W;
            oy[i] = SORTE.nextFloat() * H;
            ov[i] = 4F + SORTE.nextFloat() * 10F;
            oang[i] = SORTE.nextFloat() * TAU;
        }
    }

    public static void parar() {
        ativo = false;
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (!ativo || falhou) {
            return;
        }
        try {
            float t = (System.currentTimeMillis() - inicio) / 1000.0F;
            if (t > DUR) {
                ativo = false;
                return;
            }
            int pw = g.guiWidth(), ph = g.guiHeight();
            float sc = Math.min(pw / W, ph / H);
            float oxs = (pw - W * sc) / 2F, oys = (ph - H * sc) / 2F;
            float cx = oxs + (W / 2F) * sc, cy = oys + (H * 0.46F) * sc;

            float appear = ease(cl(t / 0.9F));
            float saida = 1F - ease(cl((t - (DUR - 1.0F)) / 1.0F));
            float alfa = appear * saida;

            preparar(g);
            Buf b = new Buf(g.pose().last().pose());

            // (1) FUNDO preto-azulado com um clarão frio no centro
            for (int i = 0; i < 20; i++) {
                float u = i / 19F;
                b.rect(0, oys + i * (H * sc / 20F), pw, H * sc / 20F + 1F,
                        col(mix(0x11131A, 0x04050A, u), 0.96F * alfa));
            }
            discoGrad(b, cx, cy, 340F * sc, 0x3A4356, 0x080910, 0.22F * alfa, 0.0F, 14);

            // (2) ESPIRAL de 3 braços girando devagar
            float rots = t * 0.35F;
            for (int braco = 0; braco < 3; braco++) {
                float base = rots + braco * TAU / 3F;
                float axp = cx, ayp = cy;
                float r = 0F;
                while (r < 320F * sc) {
                    r += 6F * sc;
                    float ang = base + r / (34F * sc);
                    float nx = cx + cos(ang) * r, ny = cy + sin(ang) * r * 0.86F;
                    float a = (1F - r / (330F * sc)) * 0.45F * alfa;
                    linha(b, axp, ayp, nx, ny, (2.4F - r / (200F * sc)) * sc, col(BONE2, a));
                    axp = nx;
                    ayp = ny;
                }
            }

            // (3) RELÓGIO: marcas dos 60 e três ponteiros
            float rc = 150F * sc;
            for (int i = 0; i < 60; i++) {
                float ang = i * TAU / 60F;
                float l = (i % 5 == 0) ? 12F : 5F;
                linha(b, cx + cos(ang) * (rc - l * sc), cy + sin(ang) * (rc - l * sc),
                        cx + cos(ang) * rc, cy + sin(ang) * rc,
                        (i % 5 == 0 ? 2.2F : 1.2F) * sc, col(BONE, (i % 5 == 0 ? 0.55F : 0.30F) * alfa));
            }
            anel(b, cx, cy, rc, 1.6F * sc, col(BONE2, 0.4F * alfa), 72);
            // ponteiros: segundos (rápido), minutos, horas
            float pSeg = t * 2.4F, pMin = t * 0.2F, pHor = t * 0.017F;
            linha(b, cx, cy, cx + cos(pSeg - (float) Math.PI / 2) * rc * 0.92F,
                    cy + sin(pSeg - (float) Math.PI / 2) * rc * 0.92F, 2.2F * sc, col(0xD64A2A, 0.85F * alfa));
            linha(b, cx, cy, cx + cos(pMin - (float) Math.PI / 2) * rc * 0.72F,
                    cy + sin(pMin - (float) Math.PI / 2) * rc * 0.72F, 3.4F * sc, col(BONE, 0.9F * alfa));
            linha(b, cx, cy, cx + cos(pHor - (float) Math.PI / 2) * rc * 0.48F,
                    cy + sin(pHor - (float) Math.PI / 2) * rc * 0.48F, 4.6F * sc, col(BONE, 0.9F * alfa));
            disco(b, cx, cy, 6F * sc, BONE, 0.9F * alfa, 12);

            // (4) CRÂNIO: forma (0–2s), encara (2–6,5s), desmancha (6,5s+)
            float forma = ease(cl(t / 2.0F));
            float desmancha = ease(cl((t - 6.5F) / 2.6F));
            float cr = 78F * sc * (0.35F + 0.65F * forma);
            float skullA = alfa * (1F - desmancha);
            if (skullA > 0.02F) {
                // caixa craniana
                elipse(b, cx, cy - cr * 0.18F, cr * 0.92F, cr * 0.98F,
                        col(BONE, 0.92F * skullA), 20);
                // maxilar
                elipse(b, cx, cy + cr * 0.62F, cr * 0.55F, cr * 0.40F,
                        col(BONE, 0.90F * skullA), 14);
                // olhos (buracos) + brilho vermelho
                for (int lado = -1; lado <= 1; lado += 2) {
                    float ex = cx + lado * cr * 0.38F, ey = cy - cr * 0.20F;
                    disco(b, ex, ey, cr * 0.26F, 0x05060A, 0.95F * skullA, 14);
                    float pulso = 0.6F + 0.4F * sin(t * 3.1F + lado);
                    disco(b, ex, ey, cr * 0.10F, 0xB02010, 0.75F * pulso * skullA, 10);
                }
                // nariz
                tri(b, cx, cy + cr * 0.06F, cx - cr * 0.11F, cy + cr * 0.30F,
                        cx + cr * 0.11F, cy + cr * 0.30F, col(0x05060A, 0.9F * skullA));
                // dentes
                for (int i = -3; i <= 3; i++) {
                    float dx = i * cr * 0.13F;
                    linha(b, cx + dx, cy + cr * 0.44F, cx + dx, cy + cr * 0.78F,
                            2.4F * sc, col(0x05060A, 0.55F * skullA));
                }
                // rachaduras conforme desmancha
                for (int i = 0; i < 10; i++) {
                    float a0 = hash(i * 7) * TAU;
                    float r0 = cr * 0.2F, r1 = cr * (0.6F + 0.5F * hash(i * 3));
                    linha(b, cx + cos(a0) * r0, cy + sin(a0) * r0,
                            cx + cos(a0) * r1, cy + sin(a0) * r1,
                            1.2F * sc, col(0x2A2620, 0.7F * desmancha * alfa));
                }
            }
            // a poeira do crânio subindo quando ele desmancha
            if (desmancha > 0.01F) {
                for (int i = 0; i < 40; i++) {
                    float a0 = hash(i * 11) * TAU;
                    float rr = hash(i * 5) * cr * 1.4F;
                    float py = cy + sin(a0) * rr * 0.9F - desmancha * (60F + 120F * hash(i));
                    float px = cx + cos(a0) * rr * 1.1F;
                    disco(b, px, py, (1.2F + 2F * hash(i * 13)) * sc, ASH,
                            0.5F * (1F - desmancha) * alfa, 6);
                }
            }

            // (5) CINZAS caindo no mundo todo
            for (int i = 0; i < NCINZA; i++) {
                ay[i] += av[i] / 60F;
                ax[i] += sin((t + i) * 0.7F) * 0.25F;
                if (ay[i] > H + 6F) {
                    ay[i] = -6F;
                    ax[i] = SORTE.nextFloat() * W;
                }
                disco(b, oxs + ax[i] * sc, oys + ay[i] * sc, asz[i] * sc, ASH,
                        (0.25F + 0.35F * hash(i)) * alfa, 6);
            }

            // (6) OSSOS à deriva
            for (int i = 0; i < NOSSO; i++) {
                ox[i] += ov[i] / 60F;
                oy[i] += sin(t + i) * 0.12F;
                if (ox[i] > W + 40F) {
                    ox[i] = -40F;
                    oy[i] = SORTE.nextFloat() * H;
                }
                oang[i] += 0.004F;
                float bx0 = oxs + ox[i] * sc, by0 = oys + oy[i] * sc;
                float dx = cos(oang[i]) * 16F * sc, dy = sin(oang[i]) * 16F * sc;
                linha(b, bx0 - dx, by0 - dy, bx0 + dx, by0 + dy, 3.0F * sc,
                        col(BONE, 0.30F * alfa));
                disco(b, bx0 - dx, by0 - dy, 2.6F * sc, BONE, 0.30F * alfa, 6);
                disco(b, bx0 + dx, by0 + dy, 2.6F * sc, BONE, 0.30F * alfa, 6);
            }

            // (7) vinheta
            b.vinheta(pw, ph, Math.min(pw, ph) * 0.28F, 0x000000, 0.55F * alfa);

            b.flush();

            // (8) TEXTOS
            desenhaTextos(g, t, oxs, oys, sc, alfa);
            desenhaSussurros(g, t, oxs, oys, sc, alfa);
            normal();
        } catch (Throwable erro) {
            falhou = true;
            ativo = false;
            RPTagMod.LOGGER.error("RP Tag: aviso MORTE OP desligado nesta sessao", erro);
        }
    }

    private static void desenhaTextos(GuiGraphics g, float t, float oxs, float oys, float sc,
            float alfa) {
        float ta = ease(cl((t - 0.4F) / 0.8F)) * alfa;
        if (ta > 0.02F) {
            texto(g, "M O R T E", oxs + (W / 2F) * sc, oys + 26F * sc, 2.4F * sc, col(BONE, ta));
        }
        if (t < TEXT_T) {
            return;
        }
        String[] linhas = { linha1, LINES[1] };
        float[] tam = { 1.55F, 1.35F };
        int[] cores = { 0xE8E2D2, 0xC8724E };
        float[] ys = { H - 74F, H - 34F };
        int restante = (int) ((t - TEXT_T) * CPS);
        for (int i = 0; i < 2 && restante > 0; i++) {
            int n = Math.min(restante, linhas[i].length());
            restante -= linhas[i].length();
            texto(g, linhas[i].substring(0, Math.max(0, n)), oxs + (W / 2F) * sc,
                    oys + ys[i] * sc, tam[i] * sc, col(cores[i], alfa));
        }
    }

    private static void desenhaSussurros(GuiGraphics g, float t, float oxs, float oys, float sc,
            float alfa) {
        for (int i = 0; i < 3; i++) {
            float ciclo = 3.8F + hash(i * 9) * 1.8F;
            float fase = fr(t / ciclo + hash(i * 3));
            float a = 0.26F * (1F - fase) * alfa;
            if (a <= 0.03F) {
                continue;
            }
            String txt = FRASES[(i * 2 + 1) % FRASES.length];
            float px = oxs + (W * 0.12F + hash(i * 19) * W * 0.76F) * sc;
            float py = oys + (H * 0.24F + hash(i * 23) * H * 0.46F + fase * 30F) * sc;
            texto(g, txt, px, py, 0.85F * sc, col(ASH, a), true);
        }
    }
}
