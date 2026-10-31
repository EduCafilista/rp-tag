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
 * ({@code geo/mascara_glb.bin} — 8.014 triângulos), então na mão, no chão e no
 * inventário ele é a própria máscara.
 *
 * <p>(5.2.1) O ITEM NA MÃO estava "enorme e feio" porque cada contexto tinha um
 * translate/scale inventado à mão. Agora só existe UMA transformação — a mesma
 * do crânio vanilla no bytecode — e todo o posicionamento por contexto vem do
 * {@code display} do JSON, copiado de {@code minecraft:item/template_skull}.
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
            malha = MalhaBin.carregar("/assets/rptag/geo/mascara_glb.bin");
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
            // (5.2.1) O ITEM NA MÃO — cópia do CRÂNIO vanilla, sem número inventado.
            //
            // Bytecode de SkullBlockRenderer.renderSkull(null, 180, 0, pose, ...):
            //     pose.translate(0.5f, 0f, 0.5f);
            //     pose.scale(-1f, -1f, 1f);
            //     modelo (em PIXELS, o Polygon divide por 16)
            // A nossa malha de cabeça ocupa o MESMO volume do modelo do crânio
            // (x ±2,7 dentro de ±4 · y -7,45..-0,45 dentro de -8..0 · z -5,05..-3,75
            // dentro de -4..4), então essa transformação põe a máscara exatamente onde
            // o crânio fica. O resto do posicionamento (mão, GUI, chão, quadro) vem do
            // `display` do JSON, copiado de minecraft:item/template_skull.
            pose.translate(0.5f, 0f, 0.5f);
            pose.scale(-1f / 16f, -1f / 16f, 1f / 16f);

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
