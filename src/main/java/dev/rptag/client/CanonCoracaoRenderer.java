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
 * (3.86.0) O CORAÇÃO 3D do bloco canônico, REDESENHADO: em vez de fatias
 * retangulares empilhadas (que viravam uma "pirâmide listrada" sem
 * silhueta), agora é uma MÁSCARA pixel-art de verdade (13px de largura,
 * 10 de altura): DOIS LÓBULOS com vale no meio em cima, corpo largo e
 * PONTA EM V embaixo, extrudada 5px de profundidade. Cada linha tem
 * gradiente próprio (rosa claro no topo, vinho na ponta), paredes
 * escuras (volume), tampa clara e um reflexo brilhante no lóbulo
 * esquerdo — leitura 3D de pixel-art volumétrica.
 * Continua GIRANDO, BATENDO e FLUTUANDO (com balanço sutil no eixo Z).
 * RenderType.lightning() vanilla (a escolha comprovada do Portais da
 * Lore: cor por vértice, sem textura, amiga de shaders); cada quad é
 * desenhado frente+verso (o lightning tem cull).
 */
public class CanonCoracaoRenderer implements BlockEntityRenderer<CanonCoracaoBlockEntity> {

    /**
     * Silhueta do coração, de CIMA pra baixo: cada linha tem os vãos
     * {x0, x1, ...} em pixels (fim exclusivo) numa grade de 13px — as
     * linhas 0-1 já trazem os DOIS lóbulos separados (o vale do coração)
     * e a última afunila até a ponta.
     */
    private static final int[][] LINHAS = {
            {2, 5, 8, 11},  // topo dos lóbulos (vale no meio)
            {1, 6, 7, 12},  // lóbulos cheios
            {1, 12},        // corpo largo
            {1, 12},
            {2, 11},
            {2, 11},
            {3, 10},
            {4, 9},
            {5, 8},
            {6, 7},         // ponta em V
    };
    private static final int LINHAS_N = LINHAS.length;
    private static final float PX = 1.0F / 16.0F;
    private static final int Z0 = -2, Z1 = 3; // 5px de profundidade (volume)

    /** gradiente da face da frente (por linha, de cima pra baixo). */
    private static final int[] FRENTE = {
            0xFF7A93, 0xFF6B88, 0xF4496A, 0xE8385C, 0xE23357,
            0xD82B50, 0xCE2649, 0xC22244, 0xB81E40, 0xAE1A3C
    };
    private static final int LADO = 0x9E1234;   // paredes (escuras = volume)
    private static final int TRAS = 0x70102A;   // costas (mais fundo)
    private static final int BRILHO = 0xFFC9D6; // reflexo no lóbulo

    public CanonCoracaoRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(CanonCoracaoBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        try {
            long t = System.currentTimeMillis();
            float ang = (t * 0.03F) % 360.0F;                 // giro contínuo
            float pulso = 1.0F + 0.10F * Mth.sin(t * 0.006F); // batendo
            float dy = Mth.sin(t * 0.0022F) * 0.03F;          // flutuando
            float balanco = 4.0F * Mth.sin(t * 0.0016F);      // balanço sutil

            pose.pushPose();
            pose.translate(0.5, 0.02 + dy, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(ang));
            pose.mulPose(Axis.ZP.rotationDegrees(balanco));
            pose.scale(pulso, pulso, pulso);

            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            for (int r = 0; r < LINHAS_N; r++) {
                float y0 = (LINHAS_N - 1 - r) * PX;
                float y1 = (LINHAS_N - r) * PX;
                int[] linha = LINHAS[r];
                for (int s = 0; s + 1 < linha.length; s += 2) {
                    float x0 = (linha[s] - 6) * PX;
                    float x1 = (linha[s + 1] - 6) * PX;
                    caixa(vc, pose, x0, y0, Z0 * PX, x1, y1, Z1 * PX,
                            clareia(FRENTE[r], 0.22F), FRENTE[r], LADO, TRAS);
                }
            }
            // reflexo brilhante no lóbulo esquerdo (2x2px, colado na frente)
            quad(vc, pose, -4 * PX, 8 * PX, Z1 * PX + 0.002F,
                    -2 * PX, 8 * PX, Z1 * PX + 0.002F,
                    -2 * PX, 10 * PX, Z1 * PX + 0.002F,
                    -4 * PX, 10 * PX, Z1 * PX + 0.002F, BRILHO);
            pose.popPose();
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: erro no coracao canonico", t);
        }
    }

    /** mistura a cor pro branco (f = 0..1) — tampa de cima mais clara. */
    private static int clareia(int rgb, float f) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += Math.round((255 - r) * f);
        g += Math.round((255 - g) * f);
        b += Math.round((255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** caixa de 6 faces com cor por face (frente clara, paredes escuras). */
    private void caixa(VertexConsumer vc, PoseStack pose, float x0, float y0, float z0,
            float x1, float y1, float z1, int corTopo, int corFrente, int corLado,
            int corTras) {
        // topo (tampa clara) e base (escura)
        quad(vc, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, corTopo);
        quad(vc, pose, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, corLado);
        // frente (gradiente da linha) e costas (fundo)
        quad(vc, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, corFrente);
        quad(vc, pose, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, corTras);
        // leste / oeste
        quad(vc, pose, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, corLado);
        quad(vc, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, corLado);
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
