package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;
import static dev.rptag.client.FxDraw.*;

import java.util.Random;

import dev.rptag.RPTagMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * AVISO ONE FOR ALL — estilo <b>19</b> (5.7.9).
 *
 * <p>Porte do protótipo <b>AvisoSol</b> que o dono mandou (<code>SfUpFekf.java</code>):
 * um <b>SOL dourado pintado à mão</b> no lugar da cabeça de uma silhueta
 * encapuzada, com oito silhuetas antigas em volta (as vidas que vieram antes),
 * uma tocha pulando de cabeça em cabeça, brasas subindo e sussurros.
 *
 * <p>A frase do aviso é a do protótipo: <i>"AS VOZES NA SUA CABEÇA / SEMPRE
 * FORAM UMA PESSOA. / Agora ela tem forma. E está olhando para você."</i> — e o
 * texto que o admin digitar no comando entra no lugar do primeiro verso.
 *
 * <p>Regra de ouro: qualquer erro aqui desliga o aviso e o jogo continua.
 */
public final class SolAvisoFx {

    private SolAvisoFx() {
    }

    // ------------------------------------------------------------- constantes
    private static final float DUR = 13.0F, FORM_T = 3.6F, TEXT_T = 4.4F, CPS = 24F;
    private static final float W = 960F, H = 640F;
    private static final float HEAD_X = W / 2F, HEAD_Y = 168F;

    private static final int GOLD = 0xFFD640, AMBER = 0xFFA01E, HOT = 0xFFF8DE;
    private static final int FIG = 0x2A1206, FIG2 = 0x140700;

    private static final String[] LINES = {
            "AS VOZES NA SUA CABEÇA",
            "SEMPRE FORAM UMA PESSOA.",
            "Agora ela tem forma. E está olhando para você."
    };
    private static final String[] FRASES = {
            "você não está sozinho", "continue", "somos o seu fogo", "passe adiante",
            "nunca desista", "ouça com atenção", "estou com você", "ela agora é sua"
    };

    private static final Random SORTE = new Random();

    // ------------------------------------------------------------------ estado
    private static boolean ativo;
    private static boolean falhou;
    private static long inicio;
    private static String linha1 = LINES[0];

    private static final int NBRASAS = 90;
    private static final float[] bx = new float[NBRASAS], by = new float[NBRASAS],
            bv = new float[NBRASAS];
    private static final int NLEGADO = 8;

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_sol"),
                SolAvisoFx::render);
    }

    public static void iniciar(String texto) {
        ativo = true;
        falhou = false;
        inicio = System.currentTimeMillis();
        linha1 = (texto == null || texto.isBlank()) ? LINES[0] : texto;
        for (int i = 0; i < NBRASAS; i++) {
            bx[i] = SORTE.nextFloat() * W;
            by[i] = SORTE.nextFloat() * H;
            bv[i] = 8F + SORTE.nextFloat() * 26F;
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

            float appear = ease(cl(t / 1.0F));
            float saida = 1.0F - ease(cl((t - (DUR - 1.0F)) / 1.0F));
            float alfa = appear * saida;

            preparar(g);
            aditivo();
            Buf b = new Buf(g.pose().last().pose());

            float sx = oxs + HEAD_X * sc, sy = oys + HEAD_Y * sc;

            // (1) FUNDO quente quase preto (faixas — degradê vertical)
            for (int i = 0; i < 22; i++) {
                float u = i / 21F;
                b.rect(0, oys + i * (H * sc / 22F), pw, H * sc / 22F + 1F,
                        col(mix(0x2A1008, 0x070302, u), 0.96F * alfa));
            }

            // (2) HALO do sol
            float beat = (float) Math.pow(Math.max(0, Math.sin(t * 2.4F)), 6);
            discoGrad(b, sx, sy, 400F * sc, 0xFF9A2A, 0x2A0600,
                    (0.30F + 0.08F * beat) * alfa, 0.0F, 16);
            discoGrad(b, sx, sy, 230F * sc, 0xFFC646, 0x7A2400, 0.30F * alfa, 0.0F, 12);

            // (3) RAIOS pintados (11 direções, cada uma com fiapos)
            float sSol = (0.35F + 0.65F * ease(cl(t / 1.8F))) * (1F + 0.012F * sin(t * 1.5F));
            for (int i = 0; i < 11; i++) {
                float base = i * TAU / 11F + sin(i * 5.1F) * 0.16F + t * 0.035F;
                float len = 110F + 110F * hash(i);
                for (int k = 0; k < 7; k++) {
                    float dlt = (k - 3F) * 0.028F;
                    float r0 = (185F + 18F * hash(i * 13 + k)) * sSol * sc;
                    float r1 = r0 + len * (0.45F + 0.55F * hash(i * 9 + k)) * sSol * sc;
                    float hw = (2.0F + 2.6F * hash(i * 5 + k * 3)) * sc;
                    float aB = base + dlt, aT = base + dlt * 0.3F;
                    float bxx = sx + cos(aB) * r0, byy = sy + sin(aB) * r0;
                    int cor = (k % 2 == 0) ? 0xFFDE1C : 0xFFC410;
                    float a = (0.50F + 0.28F * hash(i + k)) * alfa;
                    tri(b, bxx - sin(aB) * hw, byy + cos(aB) * hw,
                            bxx + sin(aB) * hw, byy - cos(aB) * hw,
                            sx + cos(aT) * r1, sy + sin(aT) * r1, col(cor, a));
                }
            }

            // (4) DISCO do sol (aquarela: anéis com jitter)
            float R = 150F * sSol * sc;
            for (int anel = 13; anel >= 0; anel--) {
                float u = anel / 13F;
                float rr = R * (1F - u * 0.88F);
                float jx = (hash(anel * 7) - 0.5F) * R * 0.05F;
                float jy = (hash(anel * 11) - 0.5F) * R * 0.05F;
                disco(b, sx + jx, sy + jy, rr, mix(0xFAD25A, 0xD63E18, u), 0.90F * alfa, 22);
            }
            disco(b, sx + R * 0.28F, sy + R * 0.28F, R * 0.90F, 0xC02A12, 0.12F * alfa, 20);

            // (5) ANEL de pinceladas brancas
            float Rr = R * 1.05F;
            for (int k = 0; k < 24; k++) {
                float a0 = hash(k * 3 + 1) * TAU + t * (0.05F + 0.12F * hash(k));
                arco(b, sx, sy, Rr + (hash(k * 9) - 0.5F) * 20F * sc,
                        a0, a0 + 0.25F + 0.7F * hash(k * 5),
                        (1.2F + 2.4F * hash(k * 13)) * sc,
                        col(0xFFFCEC, (0.30F + 0.35F * hash(k)) * alfa), 5);
            }

            // (6) RIPPLES saindo do sol a cada 2,4 s
            for (int n = 0; n < 3; n++) {
                float fase = fr(t / 2.4F + n / 3F);
                FxDraw.anel(b, sx, sy, (170 + 380 * fase) * sc, (1F + 3F * (1 - fase)) * sc,
                        col(GOLD, (1 - fase) * (1 - fase) * 0.45F * alfa), 56);
            }

            // (7) AS 8 SILHUETAS DO LEGADO em volta (acendem uma por uma)
            float form = ease(cl(t / FORM_T));
            for (int i = 0; i < NLEGADO; i++) {
                float ang = i * TAU / NLEGADO + TAU / 16F;
                float r = 268F * sc;
                float px = sx + cos(ang) * r, py = sy + sin(ang) * r * 0.62F + 70F * sc;
                float acende = ease(cl((t - 1.4F - i * 0.22F) / 0.9F));
                // tocha pulando de cabeça em cabeça
                float tocha = fr(t * 0.55F + i * 0.125F);
                float brilho = acende * (0.35F + 0.65F * Math.max(0F, 1F - Math.abs(tocha - 0.5F) * 2F));
                float altura = 92F * sc;
                disco(b, px, py - altura * 0.75F, 26F * sc, 0x1A0A03, (0.55F + 0.35F * acende) * alfa, 14);
                // corpo (capuz)
                tri(b, px - 26F * sc, py, px + 26F * sc, py, px, py - altura, col(FIG, 0.85F * alfa));
                if (brilho > 0.02F) {
                    disco(b, px, py - altura * 0.92F, 16F * sc, 0xFFB03A, brilho * 0.75F * alfa, 12);
                    disco(b, px, py - altura * 0.95F, 7F * sc, 0xFFF0C0, brilho * 0.9F * alfa, 10);
                }
            }

            // (8) A FIGURA central (silhueta encapuzada com o SOL na cabeça)
            if (form > 0.02F) {
                float sobe = (1.0F - form) * 80F * sc;
                float baseY = oys + H * sc + sobe;
                float capY = sy + 96F * sc + sobe;
                // ombros/tronco
                tri(b, sx - 200F * sc, baseY, sx + 200F * sc, baseY, sx, capY + 60F * sc, col(FIG, 0.97F * alfa));
                FxDraw.elipse(b, sx, capY + 150F * sc, 190F * sc, 200F * sc, col(FIG2, 0.97F * alfa), 18);
                // capuz (o sol cobre a cabeça)
                FxDraw.elipse(b, sx, capY - 6F * sc, 120F * sc, 118F * sc, col(FIG, 0.95F * alfa), 20);
                // brilho subindo no peito
                disco(b, sx, capY + 110F * sc, 120F * sc, 0xFF8C1E, 0.10F * alfa, 16);
                // OLHOS
                float olhoA = ease(cl((form - 0.5F) / 0.4F)) * alfa;
                if (olhoA > 0.02F) {
                    float pulso = 0.85F + 0.15F * sin(t * 3.2F);
                    for (int lado = -1; lado <= 1; lado += 2) {
                        float ex = sx + lado * 30F * sc, ey = capY - 22F * sc;
                        disco(b, ex, ey, 16F * sc * pulso, HOT, 0.28F * olhoA, 12);
                        disco(b, ex, ey, 6F * sc, 0xFFFFFF, 0.95F * olhoA, 10);
                    }
                }
                // faíscas da aura
                for (int i = 0; i < 14; i++) {
                    long agora = System.currentTimeMillis();
                    int sem = (int) ((agora / 110) % 997) + i * 41;
                    float ang = hash(sem) * TAU;
                    float rr = (120F + 90F * hash(sem + 5)) * sc;
                    float fx2 = sx + cos(ang) * rr, fy2 = capY + sin(ang) * rr * 0.9F;
                    disco(b, fx2, fy2, (1.4F + 2F * hash(sem + 9)) * sc, GOLD,
                            (0.35F + 0.4F * hash(sem + 3)) * alfa, 6);
                }
            }

            // (9) BRASAS subindo
            for (int i = 0; i < NBRASAS; i++) {
                by[i] -= bv[i] / 60F;
                if (by[i] < -6F) {
                    by[i] = H + 6F;
                    bx[i] = SORTE.nextFloat() * W;
                }
                float px = oxs + bx[i] * sc, py = oys + by[i] * sc;
                float rr = (1.1F + 2.0F * hash(i)) * sc;
                disco(b, px, py, rr * 2.4F, AMBER, (0.20F + 0.25F * hash(i * 3)) * alfa, 6);
                disco(b, px, py, rr, GOLD, (0.45F + 0.4F * hash(i * 3)) * alfa, 6);
            }

            b.flush();
            normal();

            // (10) SUSSURROS + TEXTOS (texto é sempre DEPOIS da geometria)
            desenhaSussurros(g, t, oxs, oys, sc, alfa);
            desenhaTextos(g, t, oxs, oys, sc, alfa);
            normal();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        } catch (Throwable erro) {
            falhou = true;
            ativo = false;
            RPTagMod.LOGGER.error("RP Tag: aviso ONE FOR ALL (sol) desligado nesta sessao", erro);
        }
    }

    private static void desenhaSussurros(GuiGraphics g, float t, float oxs, float oys, float sc,
            float alfa) {
        Font f = Minecraft.getInstance().font;
        for (int i = 0; i < 4; i++) {
            float ciclo = 3.6F + hash(i * 17) * 2.0F;
            float fase = fr(t / ciclo + hash(i * 5));
            float a = 0.30F * (1F - fase) * alfa;
            if (a <= 0.03F) {
                continue;
            }
            String txt = FRASES[(i * 3 + 1) % FRASES.length];
            float px = oxs + (W * 0.10F + hash(i * 31) * W * 0.80F) * sc;
            float py = oys + (H * 0.26F + hash(i * 7) * H * 0.50F - fase * 40F) * sc;
            desenhaTexto(g, "§o" + txt, px, py, 0.85F * sc, col(HOT, a), true);
        }
    }

    private static void desenhaTextos(GuiGraphics g, float t, float oxs, float oys, float sc,
            float alfa) {
        // título AVISO com os dois riscos
        float ta = ease(cl((t - 0.5F) / 0.9F)) * alfa;
        if (ta > 0.02F) {
            desenhaTexto(g, "A V I S O", oxs + (W / 2F) * sc, oys + 24F * sc, 1.7F * sc,
                    col(HOT, ta), false);
            preparar(g);
            Buf b2 = new Buf(g.pose().last().pose());
            FxDraw.linha(b2, oxs + (W / 2F - 250F) * sc, oys + 32F * sc,
                    oxs + (W / 2F - 120F) * sc, oys + 32F * sc, 1.5F * sc, col(0xFFF0C8, 0.7F * ta));
            FxDraw.linha(b2, oxs + (W / 2F + 120F) * sc, oys + 32F * sc,
                    oxs + (W / 2F + 250F) * sc, oys + 32F * sc, 1.5F * sc, col(0xFFF0C8, 0.7F * ta));
            b2.flush();
            normal();
        }
        if (t < TEXT_T) {
            return;
        }
        String[] linhas = { linha1, LINES[1], LINES[2] };
        float[] tam = { 2.0F, 2.2F, 1.15F };
        int[] cores = { 0xFFF1CC, 0xFFDE46, 0xFFE8BE };
        float[] ys = { H - 112F, H - 66F, H - 28F };
        int restante = (int) ((t - TEXT_T) * CPS);
        for (int i = 0; i < 3 && restante > 0; i++) {
            int n = Math.min(restante, linhas[i].length());
            restante -= linhas[i].length();
            String s = linhas[i].substring(0, Math.max(0, n));
            desenhaTexto(g, s, oxs + (W / 2F) * sc, oys + ys[i] * sc, tam[i] * sc,
                    col(cores[i], alfa), false);
        }
    }

    private static void desenhaTexto(GuiGraphics g, String s, float cx, float cy, float escala,
            int cor, boolean italico) {
        Font f = Minecraft.getInstance().font;
        String txt = italico ? "§o" + s : s;
        int larg = f.width(txt);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0F);
        g.pose().scale(escala, escala, 1F);
        g.drawString(f, txt, -larg / 2, 0, cor, false);
        g.pose().popPose();
    }

}
