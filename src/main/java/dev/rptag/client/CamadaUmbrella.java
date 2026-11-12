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

    /**
     * (5.40.0) <b>ONDE a cúpula fica</b>, em BLOCOS acima do pivô da cabeça.
     *
     * <p>O erro que fez o guarda-chuva aberto "não existir": o {@code render} traduzia
     * {@code -8,9} achando que estava em pixels, mas o espaço da camada — depois de
     * {@code head.translateAndRotate} — já está em <b>blocos</b> (o {@code ModelPart} divide
     * as coordenadas dos cubos por 16 sozinho). Resultado: a cúpula era desenhada 8,9 BLOCOS
     * abaixo do jogador, enterrada, e ninguém nunca a viu.
     *
     * <p>O topo do chapéu do jogador fica a 0,53 bloco acima do pivô da cabeça; 0,72 deixa a
     * aba passando por cima dele. O número também está em {@code arte/dome_geometria.json},
     * e o {@code check.py} compara os dois.
     */
    public static final float TRANSLATE_Y = 0.72F;

    /** quantos setores girados em Y formam a cúpula (22,5° cada). */
    public static final int SETORES = 16;

    /**
     * Monta a cúpula de uma cor (feito uma vez por cor, no cliente).
     *
     * <p>Cada setor é um "raio": vai do centro para fora em +X, fino em Z. A profundidade em Z
     * cresce junto com o raio porque a corda do setor também cresce — assim os 16 setores se
     * encostam e a cúpula fecha, em vez de virar uma estrela de varões soltos.
     *
     * <p>Estes números têm de ser os mesmos de {@code arte/dome_geometria.json} (gerado por
     * {@code arte/gerar_umbrella.py}); o {@code check.py} compara os dois.
     */
    public static ModelPart construir(int cor) {
        MeshDefinition malha = new MeshDefinition();
        PartDefinition raiz = malha.getRoot();
        for (int setor = 0; setor < SETORES; setor++) {
            double ang = 2.0D * Math.PI * setor / SETORES;
            CubeListBuilder cubos = CubeListBuilder.create();
            // tampa do miolo (fecha o buraco do centro)
            cubos.texOffs(0, 0).addBox(0.0F, -0.6F, -1.4F, 2.8F, 1.0F, 2.8F);
            // anel de dentro
            cubos.texOffs(4, 3).addBox(2.6F, -2.0F, -1.6F, 3.8F, 1.0F, 3.2F);
            // anel do meio
            cubos.texOffs(4, 6).addBox(5.8F, -3.6F, -2.2F, 3.8F, 1.0F, 4.4F);
            // aba: a borda que cai (é o que dá a silhueta de guarda-chuva de lado)
            cubos.texOffs(4, 9).addBox(8.8F, -5.2F, -2.9F, 3.8F, 1.0F, 5.8F);
            // vareta pendurada na ponta da aba (faixa escura da textura)
            cubos.texOffs(0, 12).addBox(11.4F, -8.4F, -0.4F, 0.8F, 3.2F, 0.8F);
            raiz.addOrReplaceChild("setor_" + setor, cubos,
                    PartPose.rotation(0.0F, (float) ang, 0.0F));
        }
        // ponteira de metal no alto, no eixo (não gira com os setores)
        raiz.addOrReplaceChild("ponteira", CubeListBuilder.create().texOffs(4, 12)
                .addBox(-0.6F, 0.4F, -0.6F, 1.2F, 2.4F, 1.2F), PartPose.ZERO);
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
                // (5.40.0) em BLOCOS (aqui 1,0 = 1 bloco; o ModelPart já divide os cubos
                // por 16 sozinho). Antes eram -8,9 "pixels" -> a cúpula ia pra 8,9 blocos
                // abaixo do jogador e era isso que fazia o guarda-chuva aberto "não existir".
                pose.translate(0.0F, TRANSLATE_Y, 0.0F);
                pose.scale(1.0F, 1.0F, 1.0F);
                cupula.render(pose, vc, luz, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: desenhar o umbrella na cabeca falhou", t);
            }
        }
    }

}
