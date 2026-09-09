package dev.rptag.client;

import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

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
 * <p>Desenhado no RenderLivingEvent.Post (nao mexe no nametag): capsula
 * arredondada com rabicho apontando pra cabeca, texto quebrado em linhas
 * e cor personalizada por jogador ({@code /cor}).
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class BubbleRenderer {

    private static final int MAX_TEXT_WIDTH = 110;
    private static final int LINE_HEIGHT = 10;
    private static final int PAD_X = 4;
    private static final int PAD_Y = 3;
    private static final int TAIL = 6;
    private static final int CORNER_SEGMENTS = 4;
    private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

    private BubbleRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
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

        // quebra o texto em linhas
        List<FormattedCharSequence> lines = font.split(FormattedText.of(text), MAX_TEXT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }

        int textW = 0;
        for (FormattedCharSequence line : lines) {
            textW = Math.max(textW, font.width(line));
        }

        int pillW = textW + PAD_X * 2;
        int pillH = lines.size() * LINE_HEIGHT + PAD_Y * 2 - 4; // altura visual compacta
        int x0 = -pillW / 2;

        // cores: fundo na cor escolhida (levemente translucido), texto com contraste
        int bg = bubble.colorRGB() & 0xFFFFFF;
        int bgAlpha = 0xD2;
        int bgArgb = (bgAlpha << 24) | bg;
        int textColor = isLight(bg) ? 0xFF1A1A22 : 0xFFFFFFFF;
        int dimmedText = (textColor & 0x00FFFFFF) | 0x20000000;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // sobe acima do nametag (nametag fica ~0.5 acima da cabeca)
        poseStack.translate(0.0, player.getBbHeight() + 0.95, 0.0);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer vc = event.getMultiBufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        int packedLight = event.getPackedLight();

        // capsula principal (topo em -h .. base em 8, rabicho embaixo)
        int top = -(pillH - 8);
        pill(vc, matrix, bgArgb, x0, top, pillW, pillH);

        // rabicho apontando pra cabeca
        tri(vc, matrix, bgArgb, -3, 7, 3, 7, 0, 7 + TAIL);

        // linhas de texto: 1a passada apagada (see-through) + 1a cheia
        int penY = top + PAD_Y;
        for (FormattedCharSequence line : lines) {
            int lineW = font.width(line);
            float lineX = -lineW / 2.0F;
            font.drawInBatch(line, lineX, dimY(penY), dimmedText, false, matrix,
                    event.getMultiBufferSource(), Font.DisplayMode.SEE_THROUGH, 0, packedLight);
            font.drawInBatch(line, lineX, penY, textColor, false, matrix,
                    event.getMultiBufferSource(), Font.DisplayMode.NORMAL, 0, packedLight);
            penY += LINE_HEIGHT;
        }

        poseStack.popPose();
    }

    /** Com penY no espaco de desenho (negativo = cima), devolve o y da passada apagada. */
    private static int dimY(int penY) {
        return penY;
    }

    private static boolean isLight(int rgb) {
        double r = ((rgb >> 16) & 0xFF) / 255.0;
        double g = ((rgb >> 8) & 0xFF) / 255.0;
        double b = (rgb & 0xFF) / 255.0;
        return 0.299 * r + 0.587 * g + 0.114 * b > 0.62;
    }

    private static void pill(VertexConsumer vc, Matrix4f m, int argb, float x, float y, float w, float h) {
        float r = Math.min(h / 2.0F, 5.0F);
        float x1 = x + w;
        float y1 = y + h;

        quad(vc, m, argb, x + r, y, x1 - r, y1);
        quad(vc, m, argb, x, y + r, x + r, y1 - r);
        quad(vc, m, argb, x1 - r, y + r, x1, y1 - r);

        fan(vc, m, argb, x + r, y + r, r, 180.0F, 270.0F);
        fan(vc, m, argb, x1 - r, y + r, r, 270.0F, 360.0F);
        fan(vc, m, argb, x1 - r, y1 - r, r, 0.0F, 90.0F);
        fan(vc, m, argb, x + r, y1 - r, r, 90.0F, 180.0F);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, int argb,
            float ax, float ay, float bx, float by) {
        tri(vc, m, argb, ax, ay, bx, ay, ax, by);
        tri(vc, m, argb, ax, by, bx, ay, bx, by);
    }

    private static void fan(VertexConsumer vc, Matrix4f m, int argb, float cx, float cy, float r,
            float a0, float a1) {
        for (int i = 0; i < CORNER_SEGMENTS; i++) {
            float t0 = (float) Math.toRadians(a0 + (a1 - a0) * i / CORNER_SEGMENTS);
            float t1 = (float) Math.toRadians(a0 + (a1 - a0) * (i + 1) / CORNER_SEGMENTS);
            tri(vc, m, argb,
                    cx, cy,
                    cx + r * (float) Math.cos(t0), cy + r * (float) Math.sin(t0),
                    cx + r * (float) Math.cos(t1), cy + r * (float) Math.sin(t1));
        }
    }

    private static void tri(VertexConsumer vc, Matrix4f m, int argb,
            float x0, float y0, float x1, float y1, float x2, float y2) {
        vc.addVertex(m, x0, y0, 0.0F).setColor(argb);
        vc.addVertex(m, x1, y1, 0.0F).setColor(argb);
        vc.addVertex(m, x2, y2, 0.0F).setColor(argb);
        vc.addVertex(m, x2, y2, 0.0F).setColor(argb);
    }
}
