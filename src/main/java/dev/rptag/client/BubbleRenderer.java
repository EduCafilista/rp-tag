package dev.rptag.client;

import java.util.List;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

/**
 * Balao de fala ("chat bubble") sobre a cabeca do jogador.
 *
 * <p>Desenhado no RenderLivingEvent.Post (nao mexe no nametag): capsula
 * arredondada COM BORDA opcional, rabicho, texto quebrado em linhas COM
 * SOMBRA (legibilidade), emojis decorativos e fundo personalizavel.
 *
 * <p>A PROVA DE CRASH (2.4.0): o fundo e desenhado num BUFFER PROPRIO do mod,
 * e o "flush" (que e onde qualquer problema de renderizacao explode) acontece
 * DENTRO do nosso try/catch. Se qualquer erro ocorrer, os baloes sao desligados
 * nesta sessao com um aviso no log — o jogo NUNCA fecha por causa do balao.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class BubbleRenderer {

    private static final int MAX_TEXT_WIDTH = 110;
    private static final int LINE_HEIGHT = 10;
    private static final int PAD_X = 4;
    private static final int PAD_Y = 3;
    private static final int TAIL = 6;
    private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

    /** Buffer exclusivo do balao — nao toca no buffer compartilhado do jogo. */
    private static final MultiBufferSource.BufferSource BUBBLE_BUFFERS =
            MultiBufferSource.immediate(new ByteBufferBuilder(786432));

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
            safeFlushDiscard();
            RPTagMod.LOGGER.error("RP Tag: erro ao desenhar balao de fala - baloes desligados ate reiniciar o jogo", t);
        }
    }

    /** Tenta descartar o buffer do balao sem lancar nova excecao. */
    private static void safeFlushDiscard() {
        try {
            BUBBLE_BUFFERS.endBatch();
        } catch (Throwable ignored) {
            // nao importa: os baloes ja estao desligados
        }
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
        if (minecraft.getEntityRenderDispatcher().distanceToSqr(player) > MAX_DISTANCE_SQ) {
            return;
        }

        Font font = minecraft.font;
        String text = bubble.text();

        List<FormattedCharSequence> lines = font.split(FormattedText.of(text), MAX_TEXT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }

        int textW = 0;
        for (FormattedCharSequence line : lines) {
            textW = Math.max(textW, font.width(line));
        }

        int pillW = textW + PAD_X * 2;
        int pillH = lines.size() * LINE_HEIGHT + PAD_Y * 2 - 3;
        int x0 = -pillW / 2;

        int background = bubble.background();
        int textColor = BubbleBackgrounds.textColorFor(background, bubble.colorRGB());
        int dimmedText = (textColor & 0x00FFFFFF) | 0x50000000;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // sobe acima do nametag
        poseStack.translate(0.0, player.getBbHeight() + 0.95, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();

        int packedLight = event.getPackedLight();
        int top = -(pillH - 8);

        // fundo + borda + rabicho: BUFFER PROPRIO, emissao identica a do vanilla
        var vcBg = BUBBLE_BUFFERS.getBuffer(BubbleBackgrounds.typeFor(background));
        BubbleBackgrounds.pillWithBorder(vcBg, matrix, x0, top, pillW, pillH,
                background, bubble.colorRGB(), bubble.borderRGB());
        BubbleBackgrounds.tail(vcBg, matrix, -3, 7, 3, 7, 0, 7 + TAIL, background, bubble.colorRGB());
        // flush DENTRO do try/catch: qualquer erro aqui e capturado (jogo nunca fecha)
        BUBBLE_BUFFERS.endBatch();

        // texto: 1a passada apagada (levemente visivel atraves de paredes) + cheia COM SOMBRA
        // (texto vai pelo buffer padrao das entidades, exatamente como a nametag do vanilla)
        int penY = top + PAD_Y;
        for (FormattedCharSequence line : lines) {
            int lineW = font.width(line);
            float lineX = -lineW / 2.0F;
            font.drawInBatch(line, lineX, penY, dimmedText, false, matrix,
                    event.getMultiBufferSource(), Font.DisplayMode.SEE_THROUGH, 0, packedLight);
            font.drawInBatch(line, lineX, penY, textColor, true, matrix,
                    event.getMultiBufferSource(), Font.DisplayMode.NORMAL, 0, packedLight);
            penY += LINE_HEIGHT;
        }

        poseStack.popPose();
    }
}
