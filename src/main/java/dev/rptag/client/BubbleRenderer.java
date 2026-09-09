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
 * Balao de fala ("chat bubble") sobre a cabeca do jogador.
 *
 * <p>RENDERIZACAO 100% VANILLA (2.4.1): o fundo usa o MESMO mecanismo do fundo
 * das nametags do Minecraft (a cor de fundo do drawInBatch), o rabicho e o
 * caractere ▼ da fonte do jogo e o texto e o drawInBatch de sempre. Nenhum
 * poligono desenhado a mao — nada que possa ficar invisivel ou crashar.
 *
 * <p>A PROVA DE CRASH: qualquer erro desliga os baloes desta sessao com aviso
 * no log — o jogo NUNCA fecha por causa do balao.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class BubbleRenderer {

    private static final int MAX_TEXT_WIDTH = 110;
    private static final int LINE_HEIGHT = 10;
    private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

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

        List<FormattedCharSequence> lines = font.split(FormattedText.of(bubble.text()), MAX_TEXT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }

        int pillH = lines.size() * LINE_HEIGHT + 2;
        int top = -(pillH - 8);

        int background = bubble.background();
        int textColor = BubbleBackgrounds.textColorFor(background, bubble.colorRGB());
        int bgARGB = BubbleBackgrounds.bgColorFor(background, bubble.colorRGB());
        int dimmedText = (textColor & 0x00FFFFFF) | 0x50000000;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // sobe acima do nametag
        poseStack.translate(0.0, player.getBbHeight() + 0.95, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();

        int packedLight = event.getPackedLight();

        // rabicho ▼ (fonte do jogo), colado abaixo do bloco de falas
        int tailW = font.width(TAIL_GLYPH);
        font.drawInBatch(TAIL_GLYPH, -tailW / 2.0F, top + pillH - 1, bgARGB, false, matrix,
                event.getMultiBufferSource(), Font.DisplayMode.NORMAL, 0, packedLight);

        // linhas: 1a passada apagada (atraves de paredes) + passada com SOMBRA e FUNDO
        // (o fundo e o mecanismo nativo do jogo, o mesmo das nametags)
        int penY = top + 3;
        for (FormattedCharSequence line : lines) {
            int lineW = font.width(line);
            float lineX = -lineW / 2.0F;
            font.drawInBatch(line, lineX, penY, dimmedText, false, matrix,
                    event.getMultiBufferSource(), Font.DisplayMode.SEE_THROUGH, 0, packedLight);
            font.drawInBatch(line, lineX, penY, textColor, true, matrix,
                    event.getMultiBufferSource(), Font.DisplayMode.NORMAL, bgARGB, packedLight);
            penY += LINE_HEIGHT;
        }

        poseStack.popPose();
    }
}
