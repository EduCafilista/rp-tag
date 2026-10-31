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
 * ({@code geo/mascara_item.bin} — 8.014 triângulos), então na mão, no chão e no
 * inventário ele é a própria máscara.
 *
 * <p>As transformações de cada {@link ItemDisplayContext} são feitas aqui na mão
 * de propósito: assim o encaixe na mão não depende do JSON e é fácil de ajustar.
 * A frente da malha aponta para -Z, então tudo gira 180° em Y para olhar quem vê.
 */
public class RenderItemMascara extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "textures/entity/mascara_glb.png");

    private static RenderItemMascara instancia;
    private MalhaBin malha;

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
            malha = MalhaBin.carregar("/assets/rptag/geo/mascara_item.bin");
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
            // o modelo tem 1,0 de altura e está centrado; a frente olha para -Z
            switch (ctx) {
                case GUI -> {
                    pose.translate(0.5f, 0.5f, 0.5f);
                    pose.scale(0.62f, 0.62f, 0.62f);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
                    pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(0f));
                }
                case GROUND -> {
                    pose.translate(0.5f, 0.16f, 0.5f);
                    pose.scale(0.34f, 0.34f, 0.34f);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
                }
                case FIXED -> {          // moldura de item (item frame)
                    pose.translate(0.5f, 0.5f, 0.5f);
                    pose.scale(0.55f, 0.55f, 0.55f);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
                }
                case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                    pose.translate(0.5f, 0.54f, 0.55f);
                    pose.scale(0.40f, 0.40f, 0.40f);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
                    pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(8f));
                }
                case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                    pose.translate(0.58f, 0.62f, 0.62f);
                    pose.scale(0.36f, 0.36f, 0.36f);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
                }
                case HEAD -> {           // na cabeça (item frame/armor stand)
                    pose.translate(0.5f, 0.72f, 0.45f);
                    pose.scale(0.52f, 0.52f, 0.52f);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
                }
                default -> {
                    pose.translate(0.5f, 0.5f, 0.5f);
                    pose.scale(0.45f, 0.45f, 0.45f);
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180f));
                }
            }

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
