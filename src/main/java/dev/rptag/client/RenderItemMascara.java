package dev.rptag.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * (5.1.0) O ITEM É O MODELO 3D DE VERDADE.
 *
 * <p>Antes o item era um JSON com blocos achatados (e pior: faixas que
 * esticavam o rosto). Agora o item desenha a MESMA malha do modelo do dono
 * ({@code geo/mascara_bb.bin} — 1.392 triângulos, do .bbmodel do dono), então na mão, no chão e no
 * inventário ele é a própria máscara.
 *
 * <p>(5.2.1) O ITEM NA MÃO estava "enorme e feio" porque cada contexto tinha um
 * translate/scale inventado à mão. Agora só existe UMA transformação — a mesma
 * do crânio vanilla no bytecode — e todo o posicionamento por contexto vem do
 * {@code display} do JSON, copiado de {@code minecraft:item/template_skull}.
 *
 * <p>(5.3.1) O INVENTÁRIO estava bugado (item gigante saindo do slot, deslocado
 * pra cima/esquerda) porque o `display` foi escrito em PIXELS. No bytecode do
 * 1.21.1 ({@code ItemTransform$Deserializer}) a `translation` vem em 1/16 de
 * bloco ({@code mul(0.0625F)}, limitada a ±5) e a {@code scale} é multiplicador
 * direto (limitada a ±4) — então "48" virava 3 BLOCOS (80 px!) e "16" batia no
 * teto de 4. Os números agora são os do crânio vanilla, e o único ajuste de
 * posição é o centramento da malha aqui.
 */
public class RenderItemMascara extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "textures/entity/mascara_bb.png");

    private static RenderItemMascara instancia;
    private MalhaBin malha;
    /** Deslocamento (em px) que leva o CENTRO da malha pro centro do cubo 0..1. */
    private float offX, offY, offZ;

    private RenderItemMascara() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static RenderItemMascara get() {
        if (instancia == null) {
            instancia = new RenderItemMascara();
        }
        return instancia;
    }

    private MalhaBin malha() {
        if (malha == null) {
            malha = MalhaBin.carregar("/assets/rptag/geo/mascara_bb.bin");
            if (malha != null && malha.n > 0) {
                // (5.3.1) O espaço do item é o CUBO 0..1 (o vanilla faz
                // translate(-0.5,-0.5,-0.5) antes de chamar a gente). A malha,
                // porém, mora em px da cabeça (centro em y=-4, z=-5,65), então
                // sem isto o item aparece deslocado pra cima/esquerda da mira e
                // do slot. Calculo o centro da bbox e levo em cima do (8,8,8).
                float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE;
                float x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
                for (int q = 0; q < malha.n; q++) {
                    x0 = Math.min(x0, malha.px[q]); x1 = Math.max(x1, malha.px[q]);
                    y0 = Math.min(y0, malha.py[q]); y1 = Math.max(y1, malha.py[q]);
                    z0 = Math.min(z0, malha.pz[q]); z1 = Math.max(z1, malha.pz[q]);
                }
                offX = 8f - (x0 + x1) * 0.5f;
                offY = 8f - (y0 + y1) * 0.5f;
                offZ = 8f - (z0 + z1) * 0.5f;
            }
        }
        return malha;
    }

    @Override
    public void renderByItem(ItemStack pilha, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buffer, int luz, int overlay) {
        MalhaBin m = malha();
        if (m == null || m.n < 3) {
            return;                       // sem malha: não desenha (o jogo segue)
        }
        try {
            pose.pushPose();
            // (5.2.2) A MALHA ESTÁ EM PIXELS DO MODELO -> 1/16, igual à camada.
            //
            // (5.3.1) E o centro da malha vai pro centro do cubo 0..1 (ver offX/Y/Z):
            // o `display` do JSON é o do crânio vanilla, que já é centrado.
            pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
            pose.translate(offX, offY, offZ);          // px (já dentro da escala 1/16)

            PoseStack.Pose entrada = pose.last();
            Matrix4f mat = entrada.pose();
            Matrix3f nor = entrada.normal();
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEX));
            for (int q = 0; q < m.n; q++) {
                vc.addVertex(mat, m.px[q], m.py[q], m.pz[q])
                        .setColor(255, 255, 255, 255)
                        .setUv(m.u[q], m.v[q])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(luz)
                        .setNormal(entrada, m.nx[q], m.ny[q], m.nz[q]);
            }
            pose.popPose();
        } catch (Throwable falha) {
            dev.rptag.RPTagMod.LOGGER.error("RP Tag: falha ao desenhar o item mascara (o jogo segue)", falha);
            try {
                pose.popPose();
            } catch (Throwable ignorada) {
            }
        } finally {
            RenderSystem.enableDepthTest();
        }
    }
}
