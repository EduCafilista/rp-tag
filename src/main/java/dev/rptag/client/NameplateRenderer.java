package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import dev.rptag.RPTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.joml.Matrix4f;

/**
 * Desenha o nome dos jogadores com uma pastilha arredondada ("bolinho")
 * do lado do nome: verde com "RP" ou cinza com "OFF RP".
 *
 * <p>Cancela o nametag padrao e renderiza manualmente, imitando fielmente o
 * vanilla (ancora no attachment NAME_TAG, placa virada para a camera,
 * escala 0.025 e as duas passadas: see-through apagada + normal).
 *
 * <p>Para voltar ao estilo texto simples ("Alex (RP)"), mude
 * {@link #BADGE_ENABLED} para {@code false}.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class NameplateRenderer {

    /** Mude para true para desenhar a tag como pastilha arredondada ("bolinho"). */
    public static final boolean BADGE_ENABLED = false;

    private static final float PILL_HEIGHT = 11.0F;
    private static final float PILL_RADIUS = PILL_HEIGHT / 2.0F;
    private static final float PILL_PAD_X = 3.0F;
    private static final float GAP = 3.0F;

    /** Cores (ARGB) das pastilhas (fundo escuro p/ o texto colorido brilhar). */
    private static final int COLOR_NAME_BG = 0x8A101014;
    private static final int COLOR_TAG_ON = 0xC0113038;
    private static final int COLOR_TAG_OFF = 0xC0303438;

    /** Branco apagado, como o vanilla usa na passada see-through (0x20FFFFFF). */
    private static final int COLOR_TEXT_DIMMED = 0x20FFFFFF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    /** Pontas da curva por canto (mais = mais redondinho). */
    private static final int CORNER_SEGMENTS = 4;

    private NameplateRenderer() {
    }

    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!BADGE_ENABLED || !ClientRPStates.hasData()) {
            return; // deixa o vanilla (ou o fallback de texto) agir
        }
        if (!(event.getEntity() instanceof Player player)) {
            return; // so mudamos o nome de JOGADORES
        }
        if (player.isSpectator()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        double distanceSq = minecraft.getEntityRenderDispatcher().distanceToSqr(player);
        if (distanceSq > 4096.0) {
            return;
        }
        Vec3 anchor = player.getAttachments().getNullable(
                EntityAttachment.NAME_TAG, 0, player.getViewYRot(event.getPartialTick()));
        if (anchor == null) {
            return;
        }

        boolean inRp = ClientRPStates.isInRp(player.getUUID());
        boolean notSneaking = !player.isDiscrete();
        int packedLight = event.getPackedLight();
        Font font = minecraft.font;
        Component name = event.getContent();
        String tagText = inRp ? RPTags.TAG_ON : RPTags.TAG_OFF;

        float nameW = font.width(name);
        float tagW = font.width(tagText);
        float namePillW = nameW + PILL_PAD_X * 2.0F;
        float tagPillW = tagW + PILL_PAD_X * 2.0F;
        float total = namePillW + GAP + tagPillW;
        float x0 = -total / 2.0F;

        event.setCanRender(TriState.FALSE); // cancela o nametag padrao; desenhamos o nosso

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();

        poseStack.pushPose();
        poseStack.translate(anchor.x, anchor.y + 0.5, anchor.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        Matrix4f matrix = poseStack.last().pose();

        // Passada 1 (vanilla: sempre feita; ve-se atraves de paredes quando nao agachado)
        Font.DisplayMode firstMode = notSneaking ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
        RenderType firstPill = notSneaking ? RenderType.guiOverlay() : RenderType.gui();
        drawPills(matrix, buffers, font, firstPill, firstMode, x0, name, tagText,
                namePillW, tagPillW, inRp, COLOR_TEXT_DIMMED, packedLight);

        // Passada 2 (vanilla: so quando nao esta agachado; texto cheio e pastilha com depth test)
        if (notSneaking) {
            drawPills(matrix, buffers, font, RenderType.gui(), Font.DisplayMode.NORMAL, x0, name, tagText,
                    namePillW, tagPillW, inRp, COLOR_TEXT, packedLight);
        }

        poseStack.popPose();
    }

    private static void drawPills(Matrix4f matrix, MultiBufferSource buffers, Font font,
            RenderType pillType, Font.DisplayMode textMode, float x0, Component name, String tagText,
            float namePillW, float tagPillW, boolean inRp, int textColor, int packedLight) {

        // pastilha escura com o nome
        pill(matrix, buffers.getBuffer(pillType), x0, 0.0F, namePillW, COLOR_NAME_BG);
        font.drawInBatch(name, x0 + PILL_PAD_X, 1.0F, textColor, false, matrix, buffers,
                textMode, 0, packedLight);

        // pastilha colorida com a tag (mesmas cores do estilo texto: ciano/cinza)
        float tagX = x0 + namePillW + GAP;
        pill(matrix, buffers.getBuffer(pillType), tagX, 0.0F, tagPillW,
                inRp ? COLOR_TAG_ON : COLOR_TAG_OFF);
        int tagColor = inRp ? 0xFF55FFFF : 0xFFAAAAAA;
        font.drawInBatch(tagText, tagX + PILL_PAD_X, 1.0F, tagColor, false, matrix, buffers,
                textMode, 0, packedLight);
    }

    /** Desenha uma capsula (retangulo arredondado) de altura fixa, comecando em (x, y). */
    private static void pill(Matrix4f matrix, VertexConsumer vc, float x, float y, float w, int argb) {
        float h = PILL_HEIGHT;
        float r = PILL_RADIUS;
        float x1 = x + w;
        float y1 = y + h;

        // centro + laterais
        quad(vc, matrix, argb, x + r, y, x1 - r, y1);
        quad(vc, matrix, argb, x, y + r, x + r, y1 - r);
        quad(vc, matrix, argb, x1 - r, y + r, x1, y1 - r);

        // cantos arredondados (leques)
        fan(vc, matrix, argb, x + r, y + r, r, 180.0F, 270.0F);
        fan(vc, matrix, argb, x1 - r, y + r, r, 270.0F, 360.0F);
        fan(vc, matrix, argb, x1 - r, y1 - r, r, 0.0F, 90.0F);
        fan(vc, matrix, argb, x + r, y1 - r, r, 90.0F, 180.0F);
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

    /** Triangulo emitido como quad degenerado (modo QUADS do RenderType.gui). */
    private static void tri(VertexConsumer vc, Matrix4f m, int argb,
            float x0, float y0, float x1, float y1, float x2, float y2) {
        vc.addVertex(m, x0, y0, 0.0F).setColor(argb);
        vc.addVertex(m, x1, y1, 0.0F).setColor(argb);
        vc.addVertex(m, x2, y2, 0.0F).setColor(argb);
        vc.addVertex(m, x2, y2, 0.0F).setColor(argb);
    }
}
