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
 * AVISO SANGUE OP — estilo <b>21</b> (5.7.9).
 *
 * <p>Porte do protótipo <b>AvisoSangue</b> do dono (<code>vScmJibF.java</code>):
 * fundo vermelho que <b>pulsa na batida</b>, <b>veias</b> crescendo das bordas
 * pro centro, uma <b>gota</b> com degradê, um <b>anel tracejado</b> girando e as
 * palavras do que o sangue devora (DOR, FOME, ÓDIO, PAIXÃO, CORAÇÃO, VIDA,
 * FÚRIA) acendendo em volta — com um PULSO (ECG) embaixo.
 *
 * <p>Convive com o estilo 9 (Hemorragia): lá a tela é um corpo que se rompe,
 * aqui é um coração batendo.
 */
public final class SangueOpAvisoFx {

    private SangueOpAvisoFx() {
    }

    private static final float DUR = 10.5F, TEXT_T = 2.4F, CPS = 24F;
    private static final float W = 960F, H = 640F;
    private static final float BPM = 62F;

    private static final int CRU = 0xC01010, ESCURO = 0x2A0303, CLARO = 0xFF3A2A;
    private static final String[] PALAVRAS = {
            "DOR", "FOME", "ÓDIO", "PAIXÃO", "CORAÇÃO", "VIDA", "FÚRIA"
    };
    private static final String[] LINES = {
            "TUDO O QUE VOCÊ SENTE,",
            "ele devora."
    };

    private static final Random SORTE = new Random();

    private static boolean ativo, falhou;
    private static long inicio;
    private static String linha1 = LINES[0];

    // veias: 26 polilinhas de 9 pontos
    private static final int NVEIA = 26, PONTOS = 9;
    private static final float[][] vx = new float[NVEIA][PONTOS];
    private static final float[][] vy = new float[NVEIA][PONTOS];
    private static boolean veiasFeitas = false;

    private static final int NGOTAS = 40;
    private static final float[] gx = new float[NGOTAS], gy = new float[NGOTAS],
            gv = new float[NGOTAS];

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_sangue_op"),
                SangueOpAvisoFx::render);
    }

    public static void iniciar(String texto) {
        ativo = true;
        falhou = false;
        inicio = System.currentTimeMillis();
        linha1 = (texto == null || texto.isBlank()) ? LINES[0] : texto;
        for (int i = 0; i < NGOTAS; i++) {
            gx[i] = SORTE.nextFloat() * W;
            gy[i] = SORTE.nextFloat() * H;
            gv[i] = 10F + SORTE.nextFloat() * 30F;
        }
        veiasFeitas = false;
    }

    public static void parar() {
        ativo = false;
    }

    /** constrói as veias uma vez (caminhada aleatória da borda pro centro). */
    private static void prepararVeias() {
        if (veiasFeitas) {
            return;
        }
        veiasFeitas = true;
        for (int v = 0; v < NVEIA; v++) {
            float ang = hash(v * 13) * TAU;
            float px = W / 2F + cos(ang) * W * 0.72F;
            float py = H / 2F + sin(ang) * H * 0.72F;
            vx[v][0] = px;
            vy[v][0] = py;
            for (int k = 1; k < PONTOS; k++) {
                // anda em direção ao centro com desvio lateral
                float alvoX = W / 2F, alvoY = H / 2F;
                float dx = alvoX - px, dy = alvoY - py;
                float len = (float) Math.hypot(dx, dy) + 0.001F;
                dx /= len;
                dy /= len;
                float desvio = (hash(v * 7 + k * 3) - 0.5F) * 0.9F;
                float nx = dx - dy * desvio, ny = dy + dx * desvio;
                float passo = len / (PONTOS - k + 0.5F);
                px += nx * passo;
                py += ny * passo;
                vx[v][k] = px;
                vy[v][k] = py;
            }
        }
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
            prepararVeias();
            int pw = g.guiWidth(), ph = g.guiHeight();
            float sc = Math.min(pw / W, ph / H);
            float oxs = (pw - W * sc) / 2F, oys = (ph - H * sc) / 2F;
            float cx = oxs + (W / 2F) * sc, cy = oys + (H * 0.46F) * sc;

            float appear = ease(cl(t / 0.8F));
            float saida = 1F - ease(cl((t - (DUR - 1.0F)) / 1.0F));
            float alfa = appear * saida;

            // batida: dois toques por ciclo (lub-dub)
            float ciclo = t * BPM / 60F;
            float fase = fr(ciclo);
            float beat = (float) (Math.exp(-fase * 9F) + 0.55F * Math.exp(-Math.abs(fase - 0.22F) * 14F));

            preparar(g);
            Buf b = new Buf(g.pose().last().pose());

            // (1) FUNDO: vermelho radial que pulsa com a batida
            b.rect(0, 0, pw, ph, col(ESCURO, 0.97F * alfa));
            discoGrad(b, cx, cy, Math.max(pw, ph) * 0.85F, CRU, ESCURO,
                    (0.55F + 0.30F * beat) * alfa, 0.05F * alfa, 18);

            // (2) VEIAS crescendo das bordas pro centro (uma por vez)
            for (int v = 0; v < NVEIA; v++) {
                float cresce = ease(cl((t - 0.4F - v * 0.05F) / 2.6F));
                if (cresce <= 0.01F) {
                    continue;
                }
                int ate = Math.max(1, (int) (cresce * (PONTOS - 1)));
                for (int k = 0; k < ate; k++) {
                    float a = (0.30F + 0.30F * hash(v * 5 + k)) * (0.7F + 0.5F * beat) * alfa;
                    float wdt = (3.4F - k * 0.22F) * sc;
                    linha(b, oxs + vx[v][k] * sc, oys + vy[v][k] * sc,
                            oxs + vx[v][k + 1] * sc, oys + vy[v][k + 1] * sc,
                            Math.max(1F, wdt), col(0x8B0A0A, a));
                    // filete claro no meio da veia
                    linha(b, oxs + vx[v][k] * sc, oys + vy[v][k] * sc,
                            oxs + vx[v][k + 1] * sc, oys + vy[v][k + 1] * sc,
                            1.0F * sc, col(0xE8483A, a * 0.55F));
                }
            }

            // (3) ANEL TRACEJADO girando + 2º anel mais interno
            float R = 150F * sc * (1F + 0.03F * beat);
            float rot = t * 0.55F;
            for (int k = 0; k < 48; k++) {
                float a0 = rot + k * TAU / 48F;
                if (k % 3 == 0) {
                    continue;                       // tracejado
                }
                arco(b, cx, cy, R, a0, a0 + TAU / 60F, 2.6F * sc,
                        col(0xFF5A40, 0.55F * alfa), 2);
            }
            float rot2 = -t * 0.9F;
            for (int k = 0; k < 36; k++) {
                float a0 = rot2 + k * TAU / 36F;
                if (k % 2 == 0) {
                    continue;
                }
                arco(b, cx, cy, R * 1.22F, a0, a0 + TAU / 48F, 1.6F * sc,
                        col(0xC02020, 0.40F * alfa), 2);
            }

            // (4) A GOTA (corpo com degradê + ponta)
            float gotaR = 62F * sc * (1F + 0.10F * beat);
            float gotaY = cy - 6F * sc;
            discoGrad(b, cx, gotaY + gotaR * 0.32F, gotaR, 0xFF5540, 0x6E0505,
                    0.95F * alfa, 0.85F * alfa, 14);
            // ponta da gota (triângulo em cima)
            tri(b, cx - gotaR * 0.55F, gotaY - gotaR * 0.10F,
                    cx + gotaR * 0.55F, gotaY - gotaR * 0.10F,
                    cx, gotaY - gotaR * 1.35F, col(CLARO, 0.92F * alfa));
            // brilho especular
            disco(b, cx - gotaR * 0.30F, gotaY + gotaR * 0.10F, gotaR * 0.20F,
                    0xFFD8C8, 0.55F * alfa, 10);

            // (5) GOTAS caindo devagar (chão de sangue)
            for (int i = 0; i < NGOTAS; i++) {
                gy[i] += gv[i] / 60F;
                if (gy[i] > H + 8F) {
                    gy[i] = -8F;
                    gx[i] = SORTE.nextFloat() * W;
                }
                float rr = (1.4F + 2.6F * hash(i * 7)) * sc;
                FxDraw.elipse(b, oxs + gx[i] * sc, oys + gy[i] * sc, rr * 0.7F, rr,
                        col(0x9A0C0C, (0.35F + 0.35F * hash(i)) * alfa), 8);
            }

            // (6) ECG embaixo (a linha do pulso correndo)
            float baseY = oys + (H - 132F) * sc;
            float largura = W * 0.62F * sc;
            float x0 = oxs + (W * 0.19F) * sc;
            float desloc = (t * 260F) % largura;
            for (int i = 0; i < 96; i++) {
                float u0 = i / 96F, u1 = (i + 1) / 96F;
                float px0 = x0 + ((u0 * largura + desloc) % largura);
                float px1 = x0 + ((u1 * largura + desloc) % largura);
                if (px1 < px0) {
                    continue;
                }
                float v0 = ecg(u0 + desloc / largura), v1 = ecg(u1 + desloc / largura);
                linha(b, px0, baseY - v0 * 26F * sc, px1, baseY - v1 * 26F * sc,
                        1.8F * sc, col(0xFF4A38, 0.55F * alfa));
            }
            linha(b, x0, baseY, x0 + largura, baseY, 1.0F * sc, col(0x8B0A0A, 0.35F * alfa));

            // (7) vinheta
            b.vinheta(pw, ph, Math.min(pw, ph) * 0.30F, 0x1A0000, 0.55F * alfa);

            b.flush();

            // (8) PALAVRAS em volta do anel
            for (int i = 0; i < PALAVRAS.length; i++) {
                float ang = i * TAU / PALAVRAS.length + t * 0.10F;
                float px = cx + cos(ang) * R * 1.42F;
                float py = cy + sin(ang) * R * 1.42F * 0.92F;
                float acende = 0.35F + 0.65F * Math.max(0F,
                        1F - Math.abs(fr(t * 0.5F + i * 0.14F) - 0.5F) * 2F);
                texto(g, PALAVRAS[i], px, py, 1.0F * sc,
                        col(i % 2 == 0 ? 0xFF6A52 : 0xD82A18, acende * alfa));
            }
            // (9) PULSO + título
            texto(g, "P U L S O", oxs + (W / 2F) * sc, oys + (H - 168F) * sc, 1.1F * sc,
                    col(0xFF7A60, 0.75F * alfa));
            texto(g, "S A N G U E", oxs + (W / 2F) * sc, oys + 30F * sc, 2.3F * sc,
                    col(0xFFD8CC, ease(cl((t - 0.4F) / 0.8F)) * alfa));

            desenhaTextos(g, t, oxs, oys, sc, alfa);
            normal();
        } catch (Throwable erro) {
            falhou = true;
            ativo = false;
            RPTagMod.LOGGER.error("RP Tag: aviso SANGUE OP desligado nesta sessao", erro);
        }
    }

    /** batimento sintético (0 fora do pico, 1 no pico). */
    private static float ecg(float u) {
        float x = fr(u * 4F);
        float p = 0F;
        p += (float) Math.exp(-Math.pow((x - 0.18F) / 0.035F, 2)) * 0.25F;
        p -= (float) Math.exp(-Math.pow((x - 0.30F) / 0.020F, 2)) * 0.55F;
        p += (float) Math.exp(-Math.pow((x - 0.36F) / 0.020F, 2)) * 1.05F;
        p -= (float) Math.exp(-Math.pow((x - 0.42F) / 0.022F, 2)) * 0.45F;
        p += (float) Math.exp(-Math.pow((x - 0.70F) / 0.070F, 2)) * 0.30F;
        return clamp(p, -1F, 1F);
    }

    private static void desenhaTextos(GuiGraphics g, float t, float oxs, float oys, float sc,
            float alfa) {
        if (t < TEXT_T) {
            return;
        }
        String[] linhas = { linha1, LINES[1] };
        float[] tam = { 1.6F, 1.5F };
        int[] cores = { 0xFFE0D6, 0xFF5A40 };
        float[] ys = { H - 84F, H - 46F };
        int restante = (int) ((t - TEXT_T) * CPS);
        for (int i = 0; i < 2 && restante > 0; i++) {
            int n = Math.min(restante, linhas[i].length());
            restante -= linhas[i].length();
            texto(g, linhas[i].substring(0, Math.max(0, n)), oxs + (W / 2F) * sc,
                    oys + ys[i] * sc, tam[i] * sc, col(cores[i], alfa));
        }
    }
}
