package dev.rptag.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.BichoEntity;
import dev.rptag.RPTagMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * RENDER DO BICHO — 5.7.7.
 *
 * <p>Ele é desenhado como um QUAD que sempre encara a câmera (billboard) com a
 * mesma arte do susto ({@code textures/gui/bicho.png}). É o jeito mais barato e
 * mais assustador: a cara gigante flutuando, agora como uma criatura de verdade
 * que dá pra bater.
 *
 * <p>Regra de ouro do mod: erro aqui desenha nada, nunca derruba o jogo.
 */
public final class BichoRenderer extends EntityRenderer<BichoEntity> {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bicho.png");
    /** tamanho do quad no mundo (blocos). */
    private static final float LADO = 1.5F;

    public BichoRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.45F;
        this.shadowStrength = 0.6F;
    }

    @Override
    public ResourceLocation getTextureLocation(BichoEntity entidade) {
        return TEX;
    }

    @Override
    public void render(BichoEntity entidade, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buf, int packedLight) {
        try {
            pose.pushPose();
            // encara a câmera e desenha centrado na altura dos olhos
            pose.translate(0.0F, 0.15F + LADO * 0.5F, 0.0F);
            pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
            pose.scale(LADO, LADO, LADO);

            Matrix4f mat = pose.last().pose();
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
            int luz = packedLight;
            float h = 0.5F;
            // quad no plano XY, virado pra câmera (a matriz já está orientada)
            vertice(vc, mat, -h, -h, 0.0F, 0.0F, 1.0F, luz);
            vertice(vc, mat, h, -h, 0.0F, 1.0F, 1.0F, luz);
            vertice(vc, mat, h, h, 0.0F, 1.0F, 0.0F, luz);
            vertice(vc, mat, -h, h, 0.0F, 0.0F, 0.0F, luz);
            pose.popPose();
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: falha ao desenhar o bicho (ignorado)", t);
        }
        super.render(entidade, yaw, partialTick, pose, buf, packedLight);
    }

    private static void vertice(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
            float u, float v, int luz) {
        vc.addVertex(mat, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(luz)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
