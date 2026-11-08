package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * (5.17.0 · refeita na 5.24.3) ESTILO 37 — FITA. O aviso que a FITA CASSETE toca.
 *
 * <p>Pedido do dono: <i>"essa mensagem aparece como o avisar FITA"</i> e, no pedido 77,
 * <i>"coloque esse como animação da fita cassete"</i> — o protótipo novo ({@code CassetteAnimation},
 * JavaFX) veio com a fita em <b>pixel art numa grade de 200×112</b> (a cassete ocupa 84×54),
 * carretéis que <b>passam a fita de um lado pro outro</b> (o pacote de um cresce enquanto o do
 * outro diminui), LED de gravação, rebobinar com borrão de movimento, o <b>OSD de videocassete</b>
 * (PLAY ▶ / ▶▶ REW / STOP ■ / PAUSE ❚❚ + contador), tracking, grão, linhas de varredura e a
 * TV ligando no começo.
 *
 * <h2>O que é diferente de antes (5.17.0 → 5.24.3)</h2>
 * <ul>
 *   <li>a cassete agora é a <b>pixel art do protótipo</b> e não mais uma caixa de retângulos lisos:
 *       corpo, aço, etiqueta com a tarja vermelha, janela, parafusos, a fita aparecendo entre os
 *       carretéis, a nervura de baixo e os quatro furos;</li>
 *   <li>os carretéis <b>giram de verdade</b> — o ângulo é integrado quadro a quadro (como no
 *       protótipo: {@code ang -= 9 * velocidade / pacote * dt}), então eles aceleram, param e
 *       <b>rebobinam ao contrário</b> no fim;</li>
 *   <li>entrou o <b>OSD</b> com o estado do transporte e o contador de fita;</li>
 *   <li>a fita <b>liga como uma TV</b> nos primeiros 0,7 s e apaga no fim.</li>
 * </ul>
 *
 * <p>Continua valendo o de sempre: nada de arquivo (é triângulo e texto, ~0 KB no jar), cada erro
 * desliga o efeito na sessão e o TEXTO é sempre desenhado <b>depois</b> do {@code flush()} do
 * buffer de triângulos (a lição do 5.16.4).
 */
public final class FitaAvisoFx {

    private static final float DUR = 8.0F;       // segundos (o mesmo do aviso)

    private static boolean ativo, falhou;
    private static long inicio;
    private static String fala = "";
    private static String[] linhas = new String[0];

    // ---- estado do transporte (o giro é INTEGRADO, como no protótipo) ----
    private static float angL, angR, fase, ultimoT;

    // ---- grade da cassete (coordenadas do protótipo, 84 x 54 "pixels") ----
    private static float U = 1F, OX, OY;

    private FitaAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "aviso_fita"),
                FitaAvisoFx::render);
    }

    public static void iniciar(String texto) {
        ativo = true;
        falhou = false;
        inicio = System.currentTimeMillis();
        fala = texto == null ? "" : texto;
        linhas = quebrar(fala.replaceAll("\u00a7.", ""), 40);
        angL = angR = fase = ultimoT = 0F;
    }

    public static void parar() {
        ativo = false;
    }

    // ---------------------------------------------------------------- quebra de linha
    private static String[] quebrar(String s, int max) {
        if (s == null || s.isBlank()) {
            return new String[0];
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String par : s.split("\n")) {
            StringBuilder linha = new StringBuilder();
            for (String palavra : par.split(" ")) {
                if (linha.length() > 0 && linha.length() + palavra.length() + 1 > max) {
                    out.add(linha.toString());
                    linha.setLength(0);
                }
                if (linha.length() > 0) {
                    linha.append(' ');
                }
                linha.append(palavra);
            }
            if (linha.length() > 0) {
                out.add(linha.toString());
            }
        }
        return out.toArray(new String[0]);
    }

    // ------------------------------------------------------- o transporte da fita
    /** 0 parada · 1 tocando · -3 rebobinando (o mesmo desenho do protótipo, em 8 s). */
    private static float velocidade(float T) {
        if (T < 0.5F) return 0;
        if (T < 0.9F) return ss(0.5F, 0.9F, T);
        if (T < 5.9F) return 1;
        if (T < 6.1F) return 1 - ss(5.9F, 6.1F, T);
        if (T < 6.3F) return -3 * ss(6.1F, 6.3F, T);
        if (T < 7.0F) return -3;
        if (T < 7.4F) return -3 * (1 - ss(7.0F, 7.4F, T));
        return 0;
    }

    /** posição da fita (0 = tudo no carretel da esquerda · 1 = tudo no da direita). */
    private static float progresso(float T) {
        if (T < 0.5F) return 0;
        if (T < 5.9F) return 0.85F * (T - 0.5F) / 5.4F;
        if (T < 6.9F) return 0.85F - 0.70F * ss(5.9F, 6.9F, T);
        return 0.15F;
    }

    // -------------------------------------------------------------------- desenho
    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
        if (!ativo || falhou) {
            return;
        }
        try {
            float T = (System.currentTimeMillis() - inicio) / 1000F;
            if (T > DUR) {
                ativo = false;
                return;
            }
            int W = g.guiWidth(), H = g.guiHeight();
            float dp = ss(0F, 0.35F, T) * (1F - ss(DUR - 0.6F, DUR, T));
            if (dp <= 0.01F) {
                return;
            }

            // ---- o transporte anda (integração igual à do protótipo) ----
            float dtm = T - ultimoT;
            if (dtm < 0F || dtm > 0.35F) {
                angL = angR = fase = 0F;
                dtm = 0F;
            }
            ultimoT = T;
            float sp = velocidade(T), pr = progresso(T);
            float pacL = 5.7F - 2.4F * pr, pacR = 3.3F + 2.4F * pr;
            angL += -9F * sp / pacL * dtm;
            angR += -9F * sp / pacR * dtm;
            fase += sp * 12F * dtm;
            float pulse = 0.5F + 0.5F * sin(T * 2.2F);
            boolean rebobinando = sp < -0.5F;
            boolean led = (T > 0.9F && T < 5.9F && ((int) Math.floor(T * 1.6F)) % 2 == 0)
                    || (rebobinando && ((int) Math.floor(T * 8F)) % 2 == 0);
            boolean glitch = T > 0.9F && (rnd((int) Math.floor(T * 3F), 7) > 0.88F
                    || (T > 5.9F && T < 6.2F) || (T > 7.0F && T < 7.3F));

            // canto da cassete + unidade da grade
            U = Math.max(1.2F, Math.min(W / 235F, H / 155F));
            float cw = 84F * U, ch = 54F * U;
            float cx = W * 0.5F + (glitch ? (rnd((int) Math.floor(T * 30F), 3) - 0.5F) * 0.8F * U : 0F);
            float cy = H * 0.33F + (float) Math.round(sin(T * 1.3F)) * 0.5F * U;
            OX = cx - cw / 2F;
            OY = cy - ch / 2F;

            AvisoFxUtil.preparar(g);
            AvisoFxUtil.Buf b = AvisoRitual.comecar(g);

            // ---------------------------------------------------- fundo (VHS escuro)
            b.rect(0, 0, W, H, col(0x08070B, dp));
            // blocos sombrios à deriva (no protótipo eles giram; aqui vão deitados)
            for (int i = 0; i < 9; i++) {
                float bw = 110 + rnd(i, 5) * 220, bh = 60 + rnd(i, 7) * 130;
                float bx = fr((rnd(i, 1) * 1000 + T * (2F + rnd(i, 11) * 5F)) / (W + 400F)) * (W + 400F) - 200F;
                float by = rnd(i, 3) * H;
                b.rect(bx - bw / 2F, by - bh / 2F, bw, bh, col(0x5F1C28, 0.075F * dp));
                b.rect(bx - bw / 2F, by + bh / 2F - 12F, bw, 12F, col(0x000000, 0.18F * dp));
            }
            // brilho vermelho atrás da fita
            AvisoRitual.halo(b, cx, cy, 0F, H * 0.80F, 0xBE1E2D,
                    (0.20F + 0.10F * pulse) * ss(0.2F, 0.9F, T) * dp);

            // ------------------------------------------------------------- A CASSETE
            desenharCassete(b, sp, pr, led, dp);

            // ------------------------------------------------------------- VHS por cima
            // linhas de tracking (como no protótipo) + faixa que sobe
            float ly = H * 0.25F + sin(T * 0.6F) * 3F;
            b.rect(0, ly, W, 2, col(0xC82832, 0.35F * dp));
            b.rect(0, ly + 6F, W, 2, col(0x3C5AC8, 0.30F * dp));
            b.rect(0, ly - 5F, W, 16, col(0x7864A0, 0.08F * dp));
            float yb = H - fr(T * 0.22F) * (H + 120F) + 60F;
            b.rect(0, yb, W, 26, col(0xFFFFFF, 0.035F * dp));
            b.rect(0, yb + 24F, W, 2, col(0xDC3C46, 0.22F * dp));
            // grão, poeira e linhas de varredura
            int quadro = (int) Math.floor(T * 2F) % 7;
            for (int i = 0; i < 70; i++) {
                b.rect(rnd(i, 9) * W, rnd(i, 4) * H, rnd(i, 6) > 0.9F ? 2 : 1, 1,
                        col(0xFFFFFF, (0.05F + 0.25F * rnd(i + quadro * 13, 2)) * dp));
            }
            for (int y = 0; y < H; y += 3) {
                b.rect(0, y, W, 1, col(0x000000, 0.18F * dp));
            }
            // o "chiado" do glitch: tarjas claras atravessando a fita
            if (glitch) {
                for (int i = 0; i < 4; i++) {
                    float gy = fr(rnd(i + (int) Math.floor(T * 20F), 6)) * H;
                    b.rect(0, gy, W, 2 + rnd(i, 8) * 10, col(0xFFFFFF, 0.10F * dp));
                }
            }
            b.vinheta(W, H, H * 0.95F, 0x000000, 0.78F * dp);

            // a TV ligando: duas tarjas pretas fechando no meio
            if (T < 0.7F) {
                float op = ss(0.12F, 0.57F, T), barra = (1F - op) * H / 2F;
                b.rect(0, 0, W, barra, col(0x000000, 1F));
                b.rect(0, H - barra, W, barra, col(0x000000, 1F));
                if (op < 1F) {
                    b.rect(0, H / 2F - 1F, W, 2F, col(0xFFFFFF, (1F - op) * 0.9F));
                }
            }
            b.flush();

            // ------------------------------------------------------------------ OSD
            if (T > 0.9F) {
                float jx = glitch ? (rnd((int) Math.floor(T * 40F), 4) - 0.5F) * 8F : 0F;
                String etiqueta = rebobinando ? "\u25C0\u25C0 REW"
                        : sp > 0.5F ? "PLAY \u25B6" : T > 7.4F ? "STOP \u25A0" : "PAUSE \u275A\u275A";
                int segs = (int) Math.floor(pr * 420F);
                String cont = String.format("%d:%02d:%02d", segs / 3600, (segs / 60) % 60, segs % 60);
                float osdY = H * 0.10F;
                float esc = 1.9F;
                // aberração cromática do OSD (vermelho e azul deslocados, como no protótipo)
                AvisoRitual.texto(g, etiqueta, W * 0.045F + jx + 2F, osdY, esc, col(0xFF3C46, 0.55F * dp), false);
                AvisoRitual.texto(g, etiqueta, W * 0.045F + jx - 2F, osdY, esc, col(0x466EFF, 0.50F * dp), false);
                AvisoRitual.texto(g, etiqueta, W * 0.045F + jx, osdY, esc, col(0xF2EEF6, dp), true);
                float larg = Minecraft.getInstance().font.width(cont) * esc;
                AvisoRitual.texto(g, cont, W - W * 0.045F - larg, osdY, esc, col(0xF2EEF6, dp), true);
                if (led) {
                    b = AvisoRitual.comecar(g);
                    b.circ(W - W * 0.045F - larg - 14F, osdY + 4F, 5F, col(0xFF2A35, dp));
                    b.flush();
                }
            }

            // ------------------------------------------------------------- A FALA
            if (T > 1.1F && linhas.length > 0) {
                float tp = ss(1.1F, 2.2F, T);
                int total = 0;
                for (String l : linhas) {
                    total += l.length();
                }
                int mostradas = Math.round(total * cl((T - 1.1F) / Math.max(1.2F,
                        Math.min(4.0F, total * 0.045F))));
                int usadas = 0;
                var fonte = Minecraft.getInstance().font;
                float y0 = H * 0.60F;
                for (int i = 0; i < linhas.length; i++) {
                    String l = linhas[i];
                    int corta = Math.max(0, Math.min(l.length(), mostradas - usadas));
                    usadas += l.length();
                    if (corta <= 0) {
                        break;
                    }
                    String mostra = l.substring(0, corta);
                    boolean cursor = corta < l.length() && fr(T * 2.4F) > 0.5F;
                    String comCursor = cursor ? mostra + "_" : mostra;
                    float esc = 1.5F;
                    float lx = cx - (fonte.width(l) * esc) / 2F;
                    AvisoRitual.texto(g, comCursor, lx, y0 + i * 20F, esc,
                            col(0xF2E9E4, tp * dp), true);
                }
                // rodapé: "▶ REPRODUZINDO" piscando
                if (fr(T * 1.6F) > 0.35F) {
                    AvisoRitual.texto(g, "\u25B6 REPRODUZINDO", cx - 56F, y0
                            + linhas.length * 20F + 16F, 1.0F,
                            col(0xC81E2E, 0.9F * dp), true);
                }
            }

            // apaga no fim (o fade do protótipo)
            if (T > DUR - 0.9F) {
                float f = cl((T - (DUR - 0.9F)) / 0.9F);
                b = AvisoRitual.comecar(g);
                b.rect(0, 0, W, H, col(0x000000, f));
                b.flush();
            }
        } catch (Throwable t) {
            falhou = true;
            RPTagMod.LOGGER.warn("RP Tag: aviso FITA desligado nesta sessão", t);
        }
    }

    // =============================================================== a cassete
    // Todas as coordenadas abaixo são as do PROTÓTIPO DO DONO (grade 84 x 54, origem no
    // canto da cassete). O que era pixel lá vira U pixels na tela.

    private static void p(AvisoFxUtil.Buf b, float x, float y, float w, float h, int rgb, float a) {
        if (a <= 0.004F) {
            return;
        }
        b.rect(OX + x * U, OY + y * U, w * U, h * U, col(rgb, a));
    }

    private static void s(AvisoFxUtil.Buf b, float x1, float y1, float x2, float y2, float w, int rgb, float a) {
        b.seg(OX + x1 * U, OY + y1 * U, OX + x2 * U, OY + y2 * U, Math.max(1F, w * U), col(rgb, a), col(rgb, a));
    }

    private static void c(AvisoFxUtil.Buf b, float x, float y, float r, int rgb, float a) {
        if (r > 0.05F && a > 0.004F) {
            b.circ(OX + x * U, OY + y * U, r * U, col(rgb, a));
        }
    }

    /** carretel do protótipo: disco de fita, o pacote de fita (que cresce/diminui), 6 dentes e o cubo. */
    private static void carretel(AvisoFxUtil.Buf b, float x, float y, float ang, float pacote, float borrao, float a) {
        c(b, x, y, 7.6F, 0x8F8176, a);
        c(b, x, y, 7.0F, 0xE8DACD, a);
        c(b, x, y, pacote, 0xB8222C, a);
        c(b, x, y, Math.max(0.6F, pacote - 1.3F), 0x9C1A24, a);
        // os 6 raios do carretel (é o que mostra que ele está rodando)
        for (int k = 0; k < 6; k++) {
            float an = ang + k * TAU / 6F;
            s(b, x + cos(an) * 2.4F, y + sin(an) * 2.4F,
                    x + cos(an) * Math.max(2.6F, pacote - 0.3F), y + sin(an) * Math.max(2.6F, pacote - 0.3F),
                    0.55F, 0x2A0509, a);
        }
        c(b, x, y, 2.3F, 0x08030A, a);
        for (int k = 0; k < 6; k++) {
            float an = ang + k * TAU / 6F + 0.52F;
            c(b, x + cos(an) * 3.1F, y + sin(an) * 3.1F, 0.6F, 0xE8DACD, a);
        }
        // borrão do rebobinar
        if (borrao > 0F) {
            c(b, x, y, pacote, 0xE8DACD, 0.40F * borrao * a);
        }
    }

    private static void desenharCassete(AvisoFxUtil.Buf b, float sp, float pr, boolean led, float dp) {
        float a = ss(0.15F, 0.7F, (System.currentTimeMillis() - inicio) / 1000F) * dp;
        if (a <= 0.01F) {
            return;
        }
        // sombra
        p(b, 3, 3, 84, 54, 0x000000, 0.55F * a);
        // corpo (duas chapas: a de cima mais clara) + beiradas
        p(b, 0, 0, 84, 54, 0x1C1B24, a);
        p(b, 0, 2, 84, 50, 0x211F2B, a);
        p(b, 0, 0, 1, 54, 0x2F2D3A, a);
        p(b, 83, 0, 1, 54, 0x14131A, a);
        // aço de cima e de baixo
        p(b, 0, 0, 84, 2, 0x6B6974, a);
        p(b, 0, 52, 84, 2, 0x3B3946, a);
        // parafusos dos cantos
        int[][] parafusos = {{4, 5}, {77, 5}, {4, 46}, {77, 46}};
        for (int[] q : parafusos) {
            p(b, q[0], q[1], 3, 3, 0x8A8894, a);
            p(b, q[0], q[1], 1, 1, 0xBCBAC4, a);
        }
        // etiqueta com a tarja vermelha e as linhas escritas à mão
        p(b, 14, 8, 56, 1, 0xC0262F, a);
        p(b, 14, 9, 56, 8, 0x2C2A3A, a);
        p(b, 17, 11, 16, 1, 0x4C4A5C, a);
        p(b, 17, 14, 26, 1, 0x4C4A5C, a);
        // o LED REC
        p(b, 62, 11, 4, 4, led ? 0xFF2A35 : 0x4A1018, a);
        if (led) {
            p(b, 62, 11, 1, 1, 0xFFB0B0, a);
        }
        // janela
        p(b, 23, 17, 38, 20, 0x07050A, a);
        p(b, 23, 17, 38, 1, 0x0F0C14, a);
        // os dois carretéis: o pacote de fita PASSA de um pro outro
        float pacL = 5.7F - 2.4F * pr, pacR = 3.3F + 2.4F * pr;
        float borrao = cl((Math.abs(sp) - 1.5F) / 1.5F);
        carretel(b, 32, 27, angL, pacL, borrao, a);
        carretel(b, 52, 27, angR, pacR, borrao, a);
        // a fita aparecendo entre os carretéis (com o brilho correndo)
        int base = (int) Math.floor(fase);
        for (int x = 30; x <= 54; x++) {
            boolean brilho = ((x - base) % 5 + 5) % 5 < 1;
            p(b, x, 35, 1, 1, brilho ? 0xE04458 : 0x8A1C26, a);
        }
        p(b, 30, 30, 1, 5, 0x8A1C26, a);
        p(b, 54, 30, 1, 5, 0x8A1C26, a);
        // nervura de plástico e os quatro furos da base
        p(b, 18, 40, 48, 1, 0x2A2834, a);
        for (int x : new int[]{28, 36, 44, 52}) {
            p(b, x, 43, 2, 5, 0x13121A, a);
        }
    }
}
