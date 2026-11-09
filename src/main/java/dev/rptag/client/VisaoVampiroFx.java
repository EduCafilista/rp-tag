package dev.rptag.client;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * (5.26.0) A VISÃO DE VAMPIRO — a segunda camada da visão noturna.
 *
 * <p>Pedido do dono: <i>"a night vision do vampiro está falhando"</i>. O efeito
 * {@code NIGHT_VISION} continua sendo dado pelo servidor (ele ilumina e mostra o ícone), mas ele
 * depende do cliente: com shader/embalagem gráfica o brilho às vezes não aparece, e o vampiro fica
 * no escuro do mesmo jeito.
 *
 * <p>Então o servidor avisa quando o vampiro está <b>na noite dele</b>
 * ({@code VampiroNoitePayload}) e esta camada acende — <b>no cliente</b>, sem depender de shader —
 * um <b>clarão prateado</b> no meio da tela, do tipo "os olhos se ajustaram":
 *
 * <ul>
 *   <li>só acende quando está <b>escuro de verdade</b> onde o jogador está (com o sol batendo ou
 *       com tocha do lado, não muda nada — nada de película na tela à toa);</li>
 *   <li>a força acompanha o escuro: no breu é o máximo, na meia-luz quase nada;</li>
 *   <li>é um brilho <b>radial</b> (mais forte no centro, sumindo nas bordas), então não "lava" a
 *       tela como um branco chapado;</li>
 *   <li>sem efeito colateral no jogo: nada de mexer no brilho do cliente dele.</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class VisaoVampiroFx {

    /** true = o servidor disse que ESTE jogador é vampiro e é noite no mundo dele. */
    private static volatile boolean noiteDoVampiro = false;

    private VisaoVampiroFx() {
    }

    public static void definir(boolean ativo) {
        noiteDoVampiro = ativo;
    }

    public static boolean ativo() {
        return noiteDoVampiro;
    }

    @SubscribeEvent
    public static void registrar(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "visao_vampiro"),
                VisaoVampiroFx::render);
    }

    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker parcial) {
        if (!noiteDoVampiro) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;   // no menu/tela de morte o brilho não serve pra nada
        }
        try {
            // quão escuro está AQUI (0 = breu total, 15 = sol na cara)
            int luz = mc.level.getMaxLocalRawBrightness(mc.player.blockPosition());
            float falta = 1F - Math.min(1F, luz / 11F);
            if (falta <= 0.05F) {
                return;   // tem luz de sobra: a visão de vampiro não faz falta
            }
            float forca = falta * 0.20F;   // teto baixo: é ajuste de olho, não lanterna
            desenharClarao(g, mc.getWindow().getGuiScaledWidth(),
                    mc.getWindow().getGuiScaledHeight(), forca);
        } catch (Throwable erro) {
            RPTagMod.LOGGER.warn("RP Tag: visao de vampiro falhou", erro);
        }
    }

    /**
     * clarão radial prateado: retângulos concêntricos do centro pra fora, cada vez mais fracos.
     * (o miolo é o mais claro; nas bordas a tela fica como está)
     */
    private static void desenharClarao(GuiGraphics g, int w, int h, float forca) {
        float raioMax = Math.max(w, h) * 0.62F;
        int passos = 26;
        int cor = 0xBFC8E8;   // prata-azulado: "olho de bicho que enxerga no escuro"
        for (int i = passos; i > 0; i--) {
            float u = i / (float) passos;          // 1 = borda, 0 = centro
            float a = (1F - u) * (1F - u) * forca; // cai rápido pro centro ficar mais claro
            int alfa = (int) (a * 255F);
            if (alfa <= 1) {
                continue;
            }
            int r = (int) (raioMax * u);
            // faixa horizontal de largura 2r e altura proporcional (elipse)
            int alt = (int) (r * (h / (float) Math.max(1, w)));
            g.fill(w / 2 - r, h / 2 - alt, w / 2 + r, h / 2 + alt, (alfa << 24) | cor);
        }
    }
}
