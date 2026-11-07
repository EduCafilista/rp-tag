package dev.rptag.client;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * (5.17.0) ESTILO 37 — FITA. O aviso que a FITA CASSETE toca.
 *
 * <p>Pedido do dono: <i>"essa mensagem aparece como o avisar FITA"</i>.
 *
 * <p>Como é: a tela ganha uma <b>vinheta de VHS</b> (linhas de varredura, chiado de cor e as
 * faixas escuras rolando na vertical), uma <b>fita cassete desenhada por geometria</b> que
 * <b>gira</b> (os dois carretéis rodando e a fita passando de um pro outro) e a frase aparecendo
 * <b>como se estivesse sendo reproduzida</b> — letra por letra, como uma legenda de fita.
 *
 * <p>Nada aqui carrega arquivo: é tudo triângulo e texto, então o aviso custa ~0 KB no jar.
 * O desenho segue o padrão da casa: cada erro desliga o efeito na sessão; o TEXTO é sempre
 * desenhado fora do buffer de triângulos (o bug do 5.16.4).
 */
public final class FitaAvisoFx {

    private static final float DUR = 8.0F;       // segundos (o mesmo do aviso)

    private static boolean ativo, falhou;
    private static long inicio;
    private static String fala = "";
    private static String[] linhas = new String[0];

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
        linhas = quebrar(fala.replaceAll("\u00a7.", ""), 38);

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
            float dp = AvisoFxUtil.ss(0F, 0.55F, T) * (1F - AvisoFxUtil.ss(DUR - 0.7F, DUR, T));

            // ---------------------------------------------------------- VHS de fundo
            AvisoFxUtil.preparar(g);
            AvisoFxUtil.Buf b = AvisoRitual.comecar(g);
            // véu escuro (a fita apaga o mundo em volta)
            b.rect(0, 0, W, H, AvisoFxUtil.col(0x08060A, 0.55F * dp));
            // vinheta
            b.vinheta(W, H, 1.15F, 0x000000, 0.72F * dp);
            // faixas de varredura horizontais (linhas finas a cada 3 px)
            for (int y = 0; y < H; y += 3) {
                b.rect(0, y, W, 1, AvisoFxUtil.col(0xFFFFFF, 0.035F * dp));
            }
            // faixa de chiado rolando na vertical (o "tracking" da fita)
            float faixa = AvisoFxUtil.fr(T * 0.35F) * (H + 160F) - 80F;
            // degradê suave da faixa (topo e base transparentes, meio branco)
            for (int i = 0; i < 8; i++) {
                float a1 = AvisoFxUtil.cl(1F - Math.abs((i + 0.5F) / 8F - 0.5F) * 2F) * 0.09F * dp;
                b.rect(0, faixa + i * 46F / 8F, W, 46F / 8F + 0.6F, AvisoFxUtil.col(0xFFFFFF, a1));
            }
            // aberração cromática nas beiradas (duas tarjas finas vermelho/azul)
            b.rect(0, faixa + 20, W, 2, AvisoFxUtil.col(0xD02030, 0.22F * dp));
            b.rect(0, faixa + 26, W, 2, AvisoFxUtil.col(0x2050D0, 0.18F * dp));
            b.flush();

            // ------------------------------------------------------------- a CASSETE
            float cx = W * 0.5F, cy = H * 0.30F;
            float ap = AvisoFxUtil.ss(0.2F, 1.0F, T) * dp;
            desenharCassete(g, cx, cy, 150F * Math.min(1F, W / 420F), ap, T);

            // ------------------------------------------------------------- a FALA
            if (T > 1.1F && linhas.length > 0) {
                float tp = AvisoFxUtil.ss(1.1F, 2.2F, T);
                int total = 0;
                for (String l : linhas) {
                    total += l.length();
                }
                int mostradas = Math.round(total * AvisoFxUtil.cl((T - 1.1F) / Math.max(1.2F,
                        Math.min(4.0F, total * 0.045F))));
                int usadas = 0;
                var fonte = Minecraft.getInstance().font;
                float y0 = H * 0.56F;
                for (int i = 0; i < linhas.length; i++) {
                    String l = linhas[i];
                    int corta = Math.max(0, Math.min(l.length(), mostradas - usadas));
                    usadas += l.length();
                    if (corta <= 0) {
                        break;
                    }
                    String mostra = l.substring(0, corta);
                    boolean cursor = corta < l.length() && AvisoFxUtil.fr(T * 2.4F) > 0.5F;
                    String comCursor = cursor ? mostra + "_" : mostra;
                    float esc = 1.5F;
                    float lx = cx - (fonte.width(l) * esc) / 2F;
                    AvisoRitual.texto(g, comCursor, lx, y0 + i * 20F, esc,
                            AvisoFxUtil.col(0xF2E9E4, tp), true);
                }
                // rodapé: "▶ REPRODUZINDO" piscando
                if (AvisoFxUtil.fr(T * 1.6F) > 0.35F) {
                    AvisoRitual.texto(g, "\u25B6 REPRODUZINDO", cx - 56F, H * 0.56F
                            + linhas.length * 20F + 16F, 1.0F,
                            AvisoFxUtil.col(0xC81E2E, 0.9F * dp), true);
                }
            }
        } catch (Throwable t) {
            falhou = true;
            RPTagMod.LOGGER.warn("RP Tag: aviso FITA desligado nesta sessão", t);
        }
    }

    // ----------------------------------------------------------------- a cassete
    private static void desenharCassete(GuiGraphics g, float cx, float cy, float w, float a, float T) {
        float h = w * 0.64F;
        AvisoFxUtil.preparar(g);
        AvisoFxUtil.Buf b = AvisoRitual.comecar(g);
        // sombra
        b.rect(cx - w / 2F + 4, cy - h / 2F + 5, w, h, AvisoFxUtil.col(0x000000, 0.5F * a));
        // corpo
        b.rect(cx - w / 2F, cy - h / 2F, w, h, AvisoFxUtil.col(0x1B1720, 0.96F * a));
        // bordas de aço
        b.rect(cx - w / 2F, cy - h / 2F, w, 3, AvisoFxUtil.col(0x6A6274, 0.9F * a));
        b.rect(cx - w / 2F, cy + h / 2F - 3, w, 3, AvisoFxUtil.col(0x3A3444, 0.9F * a));
        // etiqueta
        b.rect(cx - w * 0.40F, cy - h * 0.34F, w * 0.80F, h * 0.16F, AvisoFxUtil.col(0x2A2430, 0.95F * a));
        b.rect(cx - w * 0.40F, cy - h * 0.34F, w * 0.80F, 2, AvisoFxUtil.col(0xC81E2E, 0.85F * a));
        // janela
        float jw = w * 0.46F, jh = h * 0.36F;
        b.rect(cx - jw / 2F, cy - jh / 2F, jw, jh, AvisoFxUtil.col(0x060508, 0.96F * a));
        b.brilho(cx, cy, jw * 0.62F, 0x3A0A12, 0.35F * a);
        // os dois carretéis girando
        float r = jh * 0.40F;
        for (int lado = -1; lado <= 1; lado += 2) {
            float ox = cx + lado * jw * 0.26F;
            b.circ(ox, cy, r, AvisoFxUtil.col(0xE8E2DA, 0.92F * a));       // disco de fita
            b.circ(ox, cy, r * 0.66F, AvisoFxUtil.col(0xC81E2E, 0.95F * a));
            b.circ(ox, cy, r * 0.30F, AvisoFxUtil.col(0x100C12, 0.95F * a));
            // dentes girando (é o que mostra que está rodando)
            float giro = T * (lado > 0 ? 3.1F : 2.4F);
            for (int d = 0; d < 6; d++) {
                float an = giro + d * AvisoFxUtil.TAU / 6F;
                b.seg(ox + AvisoFxUtil.cos(an) * r * 0.30F, cy + AvisoFxUtil.sin(an) * r * 0.30F,
                        ox + AvisoFxUtil.cos(an) * r * 0.62F, cy + AvisoFxUtil.sin(an) * r * 0.62F,
                        1.6F, AvisoFxUtil.col(0x2A0810, 0.8F * a), AvisoFxUtil.col(0x2A0810, 0.8F * a));
            }
        }
        // a fita passando de um carretel pro outro (mexe com o tempo)
        float onda = AvisoFxUtil.sin(T * 2.0F) * 1.5F;
        b.rect(cx - jw * 0.26F, cy + r * 0.72F + onda, jw * 0.52F, 2, AvisoFxUtil.col(0x8A1A22, 0.75F * a));
        // parafusos dos cantos
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                b.circ(cx + sx * (w / 2F - 8), cy + sy * (h / 2F - 8), 2.2F,
                        AvisoFxUtil.col(0x6A6274, 0.8F * a));
            }
        }
        b.flush();
    }
}
