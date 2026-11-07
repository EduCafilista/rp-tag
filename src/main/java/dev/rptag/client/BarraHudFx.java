package dev.rptag.client;


import dev.rptag.BarraPayload;
import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * (5.17.0) A BARRA QUE FICA SEMPRE NA TELA.
 *
 * <p>Desenhada como CAMADA de HUD (a mesma pipeline do medo/aviso), então aparece com shaders
 * ligados e por cima de tudo. O estado vem inteiro do servidor ({@link BarraPayload}); aqui só
 * desenhamos — inclusive a porcentagem grande no canto.
 *
 * <p>Regras de ouro que valem aqui:
 * <ul>
 *   <li>qualquer erro desliga a barra na sessão (nunca derruba o jogo);</li>
 *   <li>o texto é desenhado FORA do buffer de triângulos (bug do 5.16.4 — o texto troca o shader
 *       e o próximo flush manda triângulo com o shader errado).</li>
 * </ul>
 */
public final class BarraHudFx {

    private static boolean visivel;
    private static String rotulo = "ANÁLISE";
    private static int valor = 10;
    private static int corTexto = 0xE8EDF5;
    private static int corBarra = 0xC81E2E;
    private static int corFundo = 0x141018;
    private static float px = 0.985F, py = 0.80F;   // acima da Axion (o canto é dela)
    private static int largura = 120;

    private static boolean falhou;
    private static float mostraValor = -1;      // anima o preenchimento até o valor real

    private BarraHudFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                RPTagMod.MODID, "barra_hud"), (g, delta) -> desenhar(g));
    }

    /** estado novo chegou do servidor. */
    public static void receber(BarraPayload p) {
        visivel = p.visivel();
        rotulo = p.rotulo() == null ? "" : p.rotulo();
        valor = Math.max(0, Math.min(100, p.valor()));
        corTexto = p.corTexto() & 0xFFFFFF;
        corBarra = p.corBarra() & 0xFFFFFF;
        corFundo = p.corFundo() & 0xFFFFFF;
        px = p.x();
        py = p.y();
        largura = Math.max(40, Math.min(400, p.largura()));
    }

    public static boolean visivel() {
        return visivel;
    }

    // ------------------------------------------------------------------ desenho
    private static void desenhar(GuiGraphics g) {
        if (!visivel || falhou) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) {
                return;
            }
            int W = g.guiWidth(), H = g.guiHeight();
            int bw = largura, bh = 8;
            int x = Math.round(AvisoFxUtil.cl(px) * (W - bw));
            int y = Math.round(AvisoFxUtil.cl(py) * H);
            if (y + 26 > H) y = H - 26;

            // animação do preenchimento (chega suave no valor novo)
            if (mostraValor < 0) mostraValor = valor;
            mostraValor = AvisoFxUtil.lerp(mostraValor, valor, 0.18F);

            // ---- fundo (moldura + interior) usando o buffer de cor
            AvisoFxUtil.preparar(g);
            AvisoFxUtil.Buf b = AvisoRitual.comecar(g);
            // sombra
            b.rect(x - 3, y - 3, bw + 6, bh + 15, 0x66000000);
            // fundo
            b.rect(x, y, bw, bh, AvisoFxUtil.col(corFundo, 0.92F));
            // borda
            b.rect(x, y, bw, 1, AvisoFxUtil.col(0xFFFFFF, 0.18F));
            b.rect(x, y + bh - 1, bw, 1, AvisoFxUtil.col(0x000000, 0.55F));
            // preenchimento (com brilho no topo)
            float f = AvisoFxUtil.cl(mostraValor / 100F);
            if (f > 0.001F) {
                b.rect(x + 1, y + 1, (bw - 2) * f, bh - 2, AvisoFxUtil.col(corBarra, 0.95F));
                b.rect(x + 1, y + 1, (bw - 2) * f, 2, AvisoFxUtil.col(0xFFFFFF, 0.22F));
                // ponta acesa
                float pxp = x + 1 + (bw - 2) * f;
                b.rect(pxp - 1.5F, y, 1.5F, bh, AvisoFxUtil.col(0xFFFFFF, 0.85F));
            }
            // marquinhas de 25/50/75
            for (int i = 1; i < 4; i++) {
                b.rect(x + (bw * i / 4F), y + bh - 2, 1, 2, AvisoFxUtil.col(0xFFFFFF, 0.10F));
            }
            b.flush();

            // ---- TEXTO (sempre fora do buffer de triângulos)
            var fonte = mc.font;
            if (!rotulo.isEmpty()) {
                AvisoRitual.texto(g, rotulo, x, y - 11, 1.0F, AvisoFxUtil.col(corTexto, 0.95F), true);
            }
            String pct = valor + "%";
            int corPct = AvisoFxUtil.mix(corTexto, 0xFFFFFF, 0.35F);
            AvisoRitual.texto(g, pct, x + bw - fonte.width(pct), y + bh + 3, 1.0F,
                    AvisoFxUtil.col(corPct, 0.9F), true);
        } catch (Throwable t) {
            falhou = true;
            RPTagMod.LOGGER.warn("RP Tag: barra do HUD desligada nesta sessão", t);
        }
    }

    /** fora do jogo (prévia/testes): força um estado. */
    public static void forcar(boolean on, String rot, int val, int cTexto, int cBarra, int cFundo) {
        visivel = on;
        rotulo = rot;
        valor = val;
        corTexto = cTexto;
        corBarra = cBarra;
        corFundo = cFundo;
    }
}
