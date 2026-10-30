package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * (4.9.3) MÁSCARA DE PEDRA na cabeça — o "modelo" da máscara do JoJo.
 *
 * <p>Desenha 3 painéis de pedra na frente do rosto (o do meio reto, os dois
 * laterais virados ~22° para trás, formando o "casco"): é o mesmo desenho da
 * textura do item (a região do ROSTO), com UV casando 1:1. Nada de OBJ: painel
 * desenhado na mão dá para ajustar milímetro a milímetro e não depende de
 * nenhum arquivo de modelo externo.
 *
 * <p>Só aparece quando o item equipado na cabeça é o {@code rptag:mascara_pedra}.
 */
public class CamadaMascara extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/item/mascara_pedra.png");

    /** UV do ROSTO dentro da textura 64x64 (0..0.5 nos dois eixos). */
    private static final float U0 = 0.0f, U1 = 0.5f, V0 = 0.0f, V1 = 0.5f;

    public CamadaMascara(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> pai) {
        super(pai);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int luz, AbstractClientPlayer jogador,
                       float membroSwing, float membroSwingAmount, float tickParcial, float idadeEntidade,
                       float rotacaoCabeca, float inclinacaoCabeca) {
        ItemStack pilha = jogador.getItemBySlot(EquipmentSlot.HEAD);
        if (pilha.isEmpty() || !(pilha.getItem() instanceof dev.rptag.MascaraPedraItem)) {
            return;
        }
        if (jogador.isInvisible()) {
            return;
        }

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEX));
        pose.pushPose();
        // a cabeça do modelo (PlayerModel.head) é 8x8x8 com o pivô em (0,24,0);
        // depois do translateAndRotate, 1 unidade = 1 pixel do modelo (1/16 bloco).
        getParentModel().head.translateAndRotate(pose);
        // leva a máscara pra FRENTE do rosto (o rosto fica no plano z = -4 do cubo)
        pose.translate(0.0f, 0.0f, -4.55f);

        // 3 painéis, em unidades do modelo: esquerdo, central e direito.
        // O central é mais largo (a cara); os laterais viram ~24° pra trás
        // (o "casco" da máscara de pedra do JoJo).
        // O painel do MEIO leva a cara INTEIRA (região do rosto, sem esticar
        // esquisito); os dois laterais levam a pedra estragada e viram pra trás,
        // como as abas da máscara do JoJo.
        float TOPO = 4.6f, BASE = -4.4f;                       // da testa ao queixo
        float STONE_V0 = 0.0f, STONE_U0 = U1, STONE_U1 = 1.0f; // região da pedra
        painel(pose, vc, luz, -4.05f, -2.45f, TOPO - 0.15f, BASE + 0.1f, 26.0f, STONE_U0, STONE_U0 + 0.22f);
        painel(pose, vc, luz, -2.45f, 2.45f, TOPO, BASE, 0.0f, U0, U1);
        painel(pose, vc, luz, 2.45f, 4.05f, TOPO - 0.15f, BASE + 0.1f, -26.0f, STONE_U1 - 0.22f, STONE_U1);

        pose.popPose();
    }

    /**
     * Um painel da máscara: retângulo em pé no plano z = 0 local, girado
     * {@code graus} em torno do eixo Y do seu canto de baixo.
     */
    private static void painel(PoseStack pose, VertexConsumer vc, int luz,
                               float xEsq, float xDir, float topo, float base, float graus, float uA, float uB) {
        pose.pushPose();
        pose.translate(xEsq, base, 0.0f);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(graus));
        var m = pose.last().pose();
        float larg = xDir - xEsq;
        float alt = topo - base;
        // 4 cantos, em sentido anti-horário visto de frente
        vertice(vc, m, luz, 0.0f, 0.0f, uA, V1);
        vertice(vc, m, luz, larg, 0.0f, uB, V1);
        vertice(vc, m, luz, larg, alt, uB, V0);
        vertice(vc, m, luz, 0.0f, alt, uA, V0);
        pose.popPose();
    }

    private static void vertice(VertexConsumer vc, org.joml.Matrix4f m, int luz,
                                float x, float y, float u, float v) {
        vc.addVertex(m, x, y, 0.0f)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(luz)
                .setNormal(0.0f, 0.0f, -1.0f);
    }

    /**
     * Atalho pro registro no {@code EntityRenderersEvent.AddLayers}. Só o
     * PlayerRenderer chega aqui (AbstractClientPlayer + PlayerModel), então o
     * cast é seguro — mas os genéricos do vanilla obrigam a fazer na mão.
     */
    @SuppressWarnings("unchecked")
    public static void registrarEm(net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> renderer) {
        ((net.minecraft.client.renderer.entity.LivingEntityRenderer<AbstractClientPlayer,
                PlayerModel<AbstractClientPlayer>>) renderer).addLayer(new CamadaMascara(
                (net.minecraft.client.renderer.entity.LivingEntityRenderer<AbstractClientPlayer,
                        PlayerModel<AbstractClientPlayer>>) renderer));
    }

    /** (4.9.3) atalho pra log (não usado no desenho; ajuda a depurar o registro). */
    static void logar() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            RPTagMod.LOGGER.debug("RP Tag: camada da mascara de pedra ativa");
        }
    }
}
