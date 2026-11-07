package dev.rptag.client;

import dev.rptag.AxionPayload;
import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * (5.17.0) AXION — a gatinha do canto inferior direito.
 *
 * <p>Ela passa a maior parte do tempo <b>dormindo</b> (o desenho original do dono, com o "zzz"),
 * e <b>acorda</b> quando um ADM manda ({@code /rp admin axion acordar}). O ADM também pode
 * esconder ({@code esconder}) e mandar ela voltar a dormir ({@code dormir}).
 *
 * <p><b>Fundo removido de verdade:</b> a textura é PNG com transparência (o fundo do desenho foi
 * recortado por flood fill a partir das bordas, com decontaminação de cor nas bordas — nada de
 * retângulo escuro em volta dela).
 *
 * <p>A animação é feita por transformação, sem precisar de sprite sheet: ela <b>respira</b>
 * (escala 1.00↔1.015 no eixo Y), o rabo/orelha balançam (um giro de ±0.8° em volta da base) e o
 * "zzz" sobe devagar (três"z" desenhados em degradê que reaparecem). Acordada, os olhos piscam e
 * o corpo dá um pulinho quando ela é chamada.
 */
public final class AxionHud {

    private static final ResourceLocation DORMINDO =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/axion_dormindo.png");
    private static final ResourceLocation ACORDADA =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/axion_acordada.png");

    /** (5.17.1) ESCONDIDA por padrão: ela só aparece quando o ADM manda (acordar/dormir). */
    private static int estado = AxionPayload.ESCONDIDA;
    private static float escala = 1.0F;
    private static float px = 0.995F, py = 0.995F;     // canto inferior direito
    private static boolean falhou;
    private static long virou;                          // quando mudou de estado (para o "pulinho")
    private static final long NASCEU = System.currentTimeMillis();

    private AxionHud() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "axion_hud"),
                (g, delta) -> desenhar(g));
    }

    public static void receber(AxionPayload p) {
        if (p.estado() != estado) {
            virou = System.currentTimeMillis();
        }
        estado = p.estado();
        escala = p.escala() <= 0 ? 1.0F : p.escala();
        px = p.x();
        py = p.y();
        // (5.17.0) ela é sempre visível no canto por padrão; o pacote só diz COMO
    }

    // ------------------------------------------------------------------ desenho
    private static void desenhar(GuiGraphics g) {
        if (falhou || estado == AxionPayload.ESCONDIDA) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui) {
                return;
            }
            if (mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen()) {
                return;                      // no F3 a tela já está cheia: ela sai do caminho
            }

            boolean dorme = estado == AxionPayload.DORMINDO;
            long ms = System.currentTimeMillis();
            float t = (ms - NASCEU) / 1000F;

            // ---- pulinho de quando acorda (0.45 s)
            float pulo = 0;
            if (!dorme && virou > 0) {
                float u = (ms - virou) / 450F;
                if (u < 1F) {
                    pulo = AvisoFxUtil.sin(u * (float) Math.PI) * 5.0F;
                }
            }
            // ---- respiração
            float respira = dorme
                    ? 1.0F + 0.018F * AvisoFxUtil.sin(t * 1.15F)          // dormindo: lenta
                    : 1.0F + 0.010F * AvisoFxUtil.sin(t * 3.2F);          // acordada: ofegante

            int W = g.guiWidth(), H = g.guiHeight();
            // tamanho base: 1/6 da altura da tela (fica discreta em qualquer resolução)
            float base = (H / 6.0F) * escala;
            // proporção real das texturas (o recorte manteve o desenho inteiro)
            float ratio = dorme ? 637F / 518F : 554F / 624F;
            float w = base * ratio, h = base;
            int x = Math.round(AvisoFxUtil.cl(px) * W - w);
            int y = Math.round(AvisoFxUtil.cl(py) * H - h + pulo);

            g.pose().pushPose();
            // a respiração é um leve esticar vertical a partir da BASE dela (os pés não saem do chão)
            g.pose().translate(x + w / 2F, y + h, 0);
            g.pose().scale(1.0F, respira, 1.0F);
            // balanço (rabo/orelha) — giro minúsculo em volta da base
            float bal = (dorme ? 0.35F : 0.8F) * AvisoFxUtil.sin(t * (dorme ? 0.9F : 2.4F));
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(bal));
            g.pose().translate(-(x + w / 2F), -(y + h), 0);

            g.blit(dorme ? DORMINDO : ACORDADA, x, y, 0, 0,
                    Math.round(w), Math.round(h), Math.round(w), Math.round(h));
            g.pose().popPose();

            // ---- o "zzz" (só dormindo): três letras que sobem e somem
            if (dorme) {
                desenharZzz(g, x, y, w, t);
            }
        } catch (Throwable t) {
            falhou = true;
            RPTagMod.LOGGER.warn("RP Tag: Axion desligada nesta sessão", t);
        }
    }

    /** o "z z z" subindo ao lado da cabeça — some e volta (é o que dá vida ao sono). */
    private static void desenharZzz(GuiGraphics g, int x, int y, float w, float t) {
        var fonte = Minecraft.getInstance().font;
        for (int i = 0; i < 3; i++) {
            float ciclo = AvisoFxUtil.fr(t * 0.45F + i * 0.33F);          // 0..1
            float sobe = ciclo * 26F;
            float alfa = AvisoFxUtil.cl(AvisoFxUtil.sin(ciclo * (float) Math.PI) * 1.35F);
            if (alfa <= 0.02F) {
                continue;
            }
            String z = i == 0 ? "z" : i == 1 ? "Z" : "Z";
            float esc = i == 0 ? 1.0F : i == 1 ? 1.25F : 1.5F;
            int cor = AvisoFxUtil.col(0xC81E2E, alfa * 0.85F);
            AvisoRitual.texto(g, z, x + w * 0.62F + i * 6F + ciclo * 4F, y - sobe + 6F, esc, cor, true);
        }
    }

    /** fora do jogo (prévia/testes). */
    public static void forcar(int est, float esc, float fx, float fy) {
        estado = est;
        escala = esc;
        px = fx;
        py = fy;
    }
}
