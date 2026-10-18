package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.rptag.CanonCoracaoBlockEntity;
import dev.rptag.RPTagMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.util.Mth;

/**
 * (3.84.0) O CORAÇÃO 3D do bloco canônico: desenho PROCEDURAL — 9 fatias
 * voxel empilhadas (1px de altura cada, sem sobreposição = sem
 * z-fighting) formando um coração de ~12px. GIRA, FLUTUA e BATE (pulso).
 * RenderType.lightning() vanilla (a mesma escolha comprovada do Portais
 * da Lore: cor por vértice, sem textura, amiga de shaders); cada quad é
 * desenhado frente+verso (o lightning tem cull).
 */
public class CanonCoracaoRenderer implements BlockEntityRenderer<CanonCoracaoBlockEntity> {

    /** fatias {x0, x1, z0, z1} de baixo (ponta) pra cima (lóbulos). */
    private static final float[][] FATIAS = {
            {-1, 1, -1, 1},   // y0 ponta
            {-2, 2, -1, 1},
            {-3, 3, -2, 2},
            {-4, 4, -2, 2},
            {-5, 5, -2, 2},
            {-6, 6, -2, 2},   // corpo cheio
            {-6, 6, -2, 2},
            {-6, -1, -2, 2},  // vale entre os lobulos
            {1, 6, -2, 2},
    };
    private static final float PX = 1.0F / 16.0F;
    private static final int TOPO = 0xFF6B80;   // rosa claro (highlight)
    private static final int LATERAL = 0xD9274A; // vermelho vivo
    private static final int BASE = 0x7A122E;    // vinho (sombra)

    public CanonCoracaoRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(CanonCoracaoBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        try {
            long t = System.currentTimeMillis();
            float ang = (t * 0.03F) % 360.0F;                 // giro continuo
            float pulso = 1.0F + 0.10F * Mth.sin(t * 0.006F); // batendo
            float dy = Mth.sin(t * 0.0022F) * 0.03F;          // flutuando

            pose.pushPose();
            pose.translate(0.5, 0.5 + dy, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(ang));
            pose.scale(pulso, pulso, pulso);

            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            float yBase = -FATIAS.length * PX / 2.0F;
            for (int i = 0; i < FATIAS.length; i++) {
                float[] f = FATIAS[i];
                box(vc, pose, f[0] * PX, yBase + i * PX, f[2] * PX,
                        f[1] * PX, yBase + (i + 1) * PX, f[3] * PX);
            }
            pose.popPose();
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: erro no coracao canonico", t);
        }
    }

    /** caixa de 6 faces (cada quad desenhado frente+verso = a prova de cull). */
    private void box(VertexConsumer vc, PoseStack pose, float x0, float y0, float z0,
            float x1, float y1, float z1) {
        // topo
        quad(vc, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, TOPO);
        // base
        quad(vc, pose, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, BASE);
        // leste / oeste
        quad(vc, pose, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, LATERAL);
        quad(vc, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, LATERAL);
        // sul / norte
        quad(vc, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, LATERAL);
        quad(vc, pose, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, LATERAL);
    }

    private void quad(VertexConsumer vc, PoseStack pose, float ax, float ay, float az,
            float bx, float by, float bz, float cx, float cy, float cz,
            float dx, float dy, float dz, int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        // frente
        vc.addVertex(pose.last(), ax, ay, az).setColor(r, g, b, 255);
        vc.addVertex(pose.last(), bx, by, bz).setColor(r, g, b, 255);
        vc.addVertex(pose.last(), cx, cy, cz).setColor(r, g, b, 255);
        vc.addVertex(pose.last(), dx, dy, dz).setColor(r, g, b, 255);
        // verso (lightning tem cull — garante visibilidade dos 2 lados)
        vc.addVertex(pose.last(), dx, dy, dz).setColor(r, g, b, 255);
        vc.addVertex(pose.last(), cx, cy, cz).setColor(r, g, b, 255);
        vc.addVertex(pose.last(), bx, by, bz).setColor(r, g, b, 255);
        vc.addVertex(pose.last(), ax, ay, az).setColor(r, g, b, 255);
    }
}
