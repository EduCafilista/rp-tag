package dev.rptag.aura.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.rptag.aura.ArmaDouradaEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * (5.60.0) Desenha a espada/lanca dourada com rastro de luz (procedural, brilho aditivo).
 * Aponta para +Z local. Id par = espada, impar = lanca.
 */
public class ArmaDouradaRenderer extends EntityRenderer<ArmaDouradaEntity> {
    private static final float[] OURO = {1.00f, 0.80f, 0.15f};
    private static final float[] CLARO = {1.00f, 0.96f, 0.70f};

    public ArmaDouradaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    /** Textura "pincel" do mod (1 branco puro): o brilho vem do RenderType aditivo. */
    private static final ResourceLocation BRANCO =
            ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "textures/misc/branco.png");

    @Override
    public ResourceLocation getTextureLocation(ArmaDouradaEntity e) {
        return BRANCO;
    }

    @Override
    public void render(ArmaDouradaEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        ps.pushPose();
        ps.translate(0, 0.2, 0);
        ps.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(pt, e.xRotO, e.getXRot())));
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());

        boolean lanca = (e.getId() & 1) == 1;
        for (int plano = 0; plano < 2; plano++) {   // dois planos em cruz: visivel de qualquer angulo
            boolean vert = plano == 1;
            plano(vc, m, vert, lanca ? -4.5f : -3.2f, 0.20f, 0f, 0f, 0.07f, 0.55f, OURO); // rastro
            if (lanca) {
                plano(vc, m, vert, -1.0f, 0.025f, 1f, 0.90f, 0.025f, 1f, OURO);            // haste
                plano(vc, m, vert, 0.90f, 0.025f, 1f, 1.10f, 0.10f, 1f, CLARO);           // ponta
                plano(vc, m, vert, 1.10f, 0.10f, 1f, 1.55f, 0.00f, 1f, CLARO);
            } else {
                plano(vc, m, vert, -0.40f, 0.03f, 1f, -0.05f, 0.03f, 1f, OURO);           // cabo
                plano(vc, m, vert, -0.05f, 0.22f, 1f, 0.05f, 0.22f, 1f, OURO);            // guarda
                plano(vc, m, vert, 0.05f, 0.07f, 1f, 0.95f, 0.07f, 1f, CLARO);            // lamina
                plano(vc, m, vert, 0.95f, 0.07f, 1f, 1.40f, 0.00f, 1f, CLARO);            // ponta
            }
        }
        ps.popPose();
    }

    /** Quad entre (z0, largura w0, alpha a0) e (z1, w1, a1). Plano horizontal (X) ou vertical (Y). */
    private static void plano(VertexConsumer vc, Matrix4f m, boolean vert,
            float z0, float w0, float a0, float z1, float w1, float a1, float[] c) {
        v(vc, m, vert, -w0, z0, a0, c); v(vc, m, vert, w0, z0, a0, c);
        v(vc, m, vert, w1, z1, a1, c);  v(vc, m, vert, -w1, z1, a1, c);
        v(vc, m, vert, -w1, z1, a1, c); v(vc, m, vert, w1, z1, a1, c);
        v(vc, m, vert, w0, z0, a0, c);  v(vc, m, vert, -w0, z0, a0, c);
    }

    private static void v(VertexConsumer vc, Matrix4f m, boolean vert, float w, float z, float a, float[] c) {
        vc.addVertex(m, vert ? 0f : w, vert ? w : 0f, z)
                .setColor(c[0], c[1], c[2], Mth.clamp(a, 0f, 1f));
    }
}
