package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * (5.37.0) <b>A CÚPULA 3D SOBRE A CABEÇA.</b>
 *
 * <p>Pedido do dono: <i>"tenho que fazer um umbrella 3d, para os vampiros nao tomar dano do
 * sol"</i> — e ele escolheu que o guarda-chuva apareça <b>aberto sobre o personagem</b>.
 *
 * <h2>A geometria</h2>
 * São <b>16 setores</b> girando em volta do eixo da cabeça (rotação em Y aplicada no
 * {@link PartPose} do setor, então a cúpula é redonda de verdade, sem "quadrado"). Cada setor
 * tem dois anéis de cubos — o de dentro mais alto, o de fora mais baixo — e uma <b>vareta
 * pendurada</b> na ponta, que dá o desenho de guarda-chuva visto de lado. No topo, a ponteira.
 *
 * <p>Material: {@code textures/entity/umbrella/dome_<cor>.png} (uma textura por cor, geradas
 * em {@code arte/gerar_umbrella.py}). A cor vem do {@code custom_model_data} do item, então
 * as 8 variedades aparecem na cabeça do jogador cada uma do seu jeito.
 *
 * <p>Só aparece quando o item está <b>aberto</b> (em uso) — é o mesmo estado que faz o sol
 * parar de queimar no servidor.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CamadaUmbrella {

    private CamadaUmbrella() {
    }

    @SubscribeEvent
    public static void aoRegistrarCamadas(EntityRenderersEvent.AddLayers evento) {
        try {
            for (net.minecraft.client.resources.PlayerSkin.Model pele : evento.getSkins()) {
                var renderer = evento.getSkin(pele);
                if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                    pr.addLayer(new Camada(pr));
                }
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: a camada do umbrella nao pode ser registrada", t);
        }
    }

    /** monta os 8 setores de uma cor (feito uma vez por cor, no cliente). */
    public static ModelPart construir(int cor) {
        MeshDefinition malha = new MeshDefinition();
        PartDefinition raiz = malha.getRoot();
        for (int setor = 0; setor < 16; setor++) {
            double ang = 2.0D * Math.PI * setor / 16.0D;
            CubeListBuilder cubos = CubeListBuilder.create().texOffs(0, 0);
            // anel de dentro (o miolo, colado no topo): raio 2,4 -> 5,4
            cubos.addBox(2.4F, -0.5F, -1.0F, 3, 1, 2);
            // anel do meio: raio 5,4 -> 8,0
            cubos.addBox(5.4F, -1.1F, -1.5F, 3, 1, 3);
            // aba (cai fundo e passa da largura da cabeça): raio 8,0 -> 10,4
            cubos.addBox(8.0F, -1.9F, -1.9F, 3, 1, 4);
            // vareta pendurada na ponta da aba
            cubos.addBox(10.2F, -0.9F, -0.25F, 1, 3, 1);
            raiz.addOrReplaceChild("setor_" + setor, cubos,
                    PartPose.rotation(0.0F, (float) ang, 0.0F));
        }
        // ponteira em cima e o cabo que desce até a cabeça
        raiz.addOrReplaceChild("ponteira", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.5F, -3.0F, -0.5F, 1, 2, 1), PartPose.ZERO);
        raiz.addOrReplaceChild("cabo", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-0.4F, 0.4F, -0.4F, 1, 1, 1), PartPose.ZERO);
        return malha.getRoot().bake(16, 16);
    }

    /** o desenho, em espaço de modelo, ancorado na cabeça. */
    static final class Camada extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        private final ModelPart[] porCor = new ModelPart[UmbrellaHud.CORES];

        Camada(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> pai) {
            super(pai);
        }

        private ModelPart cupula(int cor) {
            int i = Math.floorMod(cor, UmbrellaHud.CORES);
            if (porCor[i] == null) {
                porCor[i] = construir(i);
            }
            return porCor[i];
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int luz, AbstractClientPlayer p,
                float pernaBalanco, float pernaAngulo, float tick, float idade, float giro, float inclinacao) {
            ItemStack aberto = UmbrellaHud.aberto(p);
            if (aberto == null) {
                return;
            }
            try {
                int cor = UmbrellaHud.corDo(aberto);
                ModelPart cupula = cupula(cor);
                ResourceLocation tex = UmbrellaHud.texturaCupula(cor);
                RenderType tipo = RenderType.entitySolid(tex);
                VertexConsumer vc = buffers.getBuffer(tipo);
                pose.pushPose();
                // acima da cabeça, girando com ela
                getParentModel().head.translateAndRotate(pose);
                pose.translate(0.0F, -8.9F, 0.0F);   // modelo usa 1 unidade = 1/16 de bloco
                pose.scale(1.0F, 1.0F, 1.0F);
                cupula.render(pose, vc, luz, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: desenhar o umbrella na cabeca falhou", t);
            }
        }
    }

}
