package dev.rptag.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

/**
 * Balao de fala (design "cartao", 2.5.0): um cartao suave com cantos
 * chanfrados, SOMBRA, barra de destaque com a cor do jogador e texto com
 * contraste automatico.
 *
 * <p>COMO E DESENHADO (a prova de bug): cada faixa do cartao e uma linha de
 * ESPACOS desenhada com font.drawInBatch e cor de fundo — o MESMO mecanismo
 * do fundo das nametags do Minecraft. Nenhum poligono custom, nenhuma
 * textura propria: impossivel ficar invisivel e impossivel crashar.
 *
 * <p>A PROVA DE CRASH: qualquer erro desliga os baloes nesta sessao com
 * aviso no log — o jogo NUNCA fecha por causa do balao.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class BubbleRenderer {

    private static final int MAX_TEXT_WIDTH = 110;
    private static final int LINE_HEIGHT = 10;

    /** O rabicho do balao: um triangulo da propria fonte do jogo. */
    private static final String TAIL_GLYPH = "▼";

    private BubbleRenderer() {
    }

    private static boolean disabled = false;

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (disabled) {
            return;
        }
        try {
            renderBubble(event);
        } catch (Throwable t) {
            // A PROVA DE CRASH: qualquer erro desliga os baloes desta sessao
            // (a tag RP, chat e comandos continuam funcionando) e registra o motivo.
            disabled = true;
            RPTagMod.LOGGER.error("RP Tag: erro ao desenhar balao de fala - baloes desligados ate reiniciar o jogo", t);
        }
    }

    /**
     * Desenha uma faixa do cartao: uma linha de espacos com cor de fundo.
     * A faixa fica com 4*n+2 px de largura e 10px de altura.
     */
    private static void band(Font font, Matrix4f matrix, net.minecraft.client.renderer.MultiBufferSource buffers,
            int light, float x, float y, int spaces, int argb) {
        if (spaces <= 0 || (argb >>> 24) == 0) {
            return;
        }
        font.drawInBatch(" ".repeat(spaces), x, y, 0, false, matrix,
                buffers, Font.DisplayMode.NORMAL, argb, light);
    }

    private static void renderBubble(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ClientBubbleCache.Bubble bubble = ClientBubbleCache.get(player.getUUID());
        if (bubble == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getEntityRenderDispatcher().distanceToSqr(player) > 64.0 * 64.0) {
            return;
        }

        Font font = minecraft.font;
        List<FormattedCharSequence> lines = font.split(FormattedText.of(bubble.text()), MAX_TEXT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }

        int background = bubble.background();
        int colorRGB = bubble.colorRGB() & 0xFFFFFF;
        int fillBase = BubbleBackgrounds.bgColorFor(background, colorRGB);
        int accent = BubbleBackgrounds.accentColorFor(background, colorRGB);
        int barColor = BubbleBackgrounds.borderColorFor(background, colorRGB, bubble.borderRGB());
        int accentOrBar = barColor != 0 ? barColor : accent;
        int textColor = BubbleBackgrounds.textColorFor(background, colorRGB);
        int dimmedText = (textColor & 0x00FFFFFF) | 0x50000000;

        // medidas do cartao (faixas de 4*n+2 px de largura)
        int pillW = Math.max(34, textWidth(font, lines) + 14);
        int sFull = Math.max(2, (pillW - 2) / 4);
        int n = lines.size();
        int top = 2 - 10 * n; // base do cartao fica ~2px acima da origem (nametag)

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0, player.getBbHeight() + 0.95, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();
        var buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();

        // ---- cartao: sombra, faixas de fundo (com gradiente real) e barra de cor ----
        for (int j = 0; j < n; j++) {
            boolean chamfer = (j == 0 || j == n - 1);
            float bx = (chamfer ? 4 : 0) - pillW / 2.0F;
            float by = top + 1 + 10 * j;
            int s = sFull - (chamfer ? 2 : 0);

            // sombra deslocada (profundidade)
            band(font, matrix, buffers, light, bx + 2, by + 2, s, 0x5A000000);

            // fundo (gradiente pinta linha a linha com a cor do jogador)
            int rowFill = background == BubbleBackgrounds.BG_GRADIENTE
                    ? BubbleBackgrounds.gradientColorFor(colorRGB, n == 1 ? 0.5F : j / (float) (n - 1))
                    : fillBase;
            band(font, matrix, buffers, light, bx, by, s, rowFill);

            // barra de destaque com a cor escolhida
            band(font, matrix, buffers, light, bx + 2, by, 1, accentOrBar);
        }

        // rabicho ▼ logo abaixo do cartao
        int tailW = font.width(TAIL_GLYPH);
        font.drawInBatch(TAIL_GLYPH, -tailW / 2.0F, top + 10 * n + 3, fillBase,
                false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);

        // ---- texto (passada apagada atravessando paredes + passada com sombra) ----
        int penY = top + 3;
        for (FormattedCharSequence line : lines) {
            float lineX = -font.width(line) / 2.0F;
            font.drawInBatch(line, lineX, penY, dimmedText, false, matrix,
                    buffers, Font.DisplayMode.SEE_THROUGH, 0, light);
            font.drawInBatch(line, lineX, penY, textColor, true, matrix,
                    buffers, Font.DisplayMode.NORMAL, 0, light);
            penY += LINE_HEIGHT;
        }

        poseStack.popPose();
    }

    private static int textWidth(Font font, List<FormattedCharSequence> lines) {
        int w = 0;
        for (FormattedCharSequence line : lines) {
            w = Math.max(w, font.width(line));
        }
        return w;
    }
}
