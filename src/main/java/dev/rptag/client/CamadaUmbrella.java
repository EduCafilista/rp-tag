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
import net.minecraft.util.Mth;
import net.minecraft.Util;
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
     * <p>O topo do chapéu do jogador fica a 0,53 bloco acima do pivô da cabeça. A aba da
     * cúpula fica a 1,8 pixel (0,11 bloco) e com 0,45 de subida ela passa a 0,56 — encostando
     * no chapéu, sem buraco no meio. (Na 5.41.0 era 0,72 e a cúpula flutuava um bloco acima.) O número também está em {@code arte/dome_geometria.json},
     * e o {@code check.py} compara os dois.
     */
    public static final float TRANSLATE_Y = 0.45F;

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
            // (5.41.1) aqui o Y CRESCE PRA BAIXO: "acima" é y negativo. A cúpula é o miolo no
            // alto, os anéis descendo e a aba caindo perto da cabeça — na 5.41.0 estava ao
            // contrário (aba em cima, miolo embaixo) e a cúpula aparecia como uma tigela.
            // tampa do miolo (fecha o buraco do centro)
            cubos.texOffs(0, 0).addBox(0.0F, -6.0F, -1.4F, 2.8F, 1.0F, 2.8F);
            // anel de dentro
            cubos.texOffs(4, 3).addBox(2.6F, -4.8F, -1.6F, 3.8F, 1.0F, 3.2F);
            // anel do meio
            cubos.texOffs(4, 6).addBox(5.8F, -3.4F, -2.2F, 3.8F, 1.0F, 4.4F);
            // aba: a borda que cai (é o que dá a silhueta de guarda-chuva de lado)
            cubos.texOffs(4, 9).addBox(8.8F, -1.8F, -2.9F, 3.8F, 1.0F, 5.8F);
            // vareta pendurada na ponta da aba (desce, y positivo)
            cubos.texOffs(0, 12).addBox(11.4F, -1.6F, -0.4F, 0.8F, 3.0F, 0.8F);
            raiz.addOrReplaceChild("setor_" + setor, cubos,
                    PartPose.rotation(0.0F, (float) ang, 0.0F));
        }
        // ponteira de metal no alto, no eixo (não gira com os setores)
        raiz.addOrReplaceChild("ponteira", CubeListBuilder.create().texOffs(4, 12)
                .addBox(-0.6F, -8.2F, -0.6F, 1.2F, 2.2F, 1.2F), PartPose.ZERO);
        return malha.getRoot().bake(16, 16);
    }

    // ---------------------------------------------------------------- a ANIMAÇÃO (5.41.1)
    /**
     * Pedido do dono: <i>"não está bonita a animação, melhore"</i>.
     *
     * <p>Até a 5.41.0 a cúpula simplesmente <b>aparecia</b> com o tamanho final no primeiro
     * quadro — um "pop" seco. Agora ela <b>se desdobra</b>:
     * <ul>
     *   <li>AO ABRIR (~{@value #ABRIR_MS} ms): começa pequena (18%) e girada 55°, e cresce
     *       girando até o tamanho cheio — o mesmo movimento de quem empurra a vareta e o pano
     *       abre. A curva é {@code 1-(1-t)³} (acelera no começo, alisa no fim), que é a que
     *       parece natural: abre rápido e "assenta" no fim.</li>
     *   <li>AO FECHAR (~{@value #FECHAR_MS} ms): encolhe de volta e some — antes desaparecia
     *       de um quadro pro outro.</li>
     *   <li>ABERTO: fica com um balanço leve (o pano respirando com o vento).</li>
     * </ul>
     *
     * <p>O estado vem do próprio item ({@code custom_model_data} >= 8), então a animação
     * acompanha exatamente o que protege do sol — nunca fica um desenho "mentindo".
     */
    public static final long ABRIR_MS = 260L;
    /** quanto tempo a cúpula leva pra encolher e sumir ao fechar. */
    public static final long FECHAR_MS = 200L;

    /** por jogador: quando ele andou abrindo e quando fechou (relógio do cliente, ms). */
    private static final java.util.Map<Integer, long[]> TEMPOS = new java.util.concurrent.ConcurrentHashMap<>();
    /** índice 0 do {@link #TEMPOS}. */
    private static final int INICIO_ABERTURA = 0;
    /** índice 1: quando ele fechou (0 = nunca/aberto agora). */
    private static final int INSTANTE_FECHOU = 1;

    /**
     * O quanto a cúpula está aberta agora (0 = fechada, 1 = aberta), já suavizado.
     * Devolve {@link Float#NaN} quando a cúpula não deve ser desenhada.
     */
    static float quantoAberta(int idJogador, boolean estaAberto, long agora) {
        long[] t = TEMPOS.computeIfAbsent(idJogador, k -> new long[2]);
        if (estaAberto) {
            if (t[INSTANTE_FECHOU] != 0L) {      // acabou de reabrir: recomeça do zero
                t[INSTANTE_FECHOU] = 0L;
                t[INICIO_ABERTURA] = agora;
            }
            if (t[INICIO_ABERTURA] == 0L) {
                t[INICIO_ABERTURA] = agora;
            }
            float u = Mth.clamp((agora - t[INICIO_ABERTURA]) / (float) ABRIR_MS, 0.0F, 1.0F);
            return 1.0F - (1.0F - u) * (1.0F - u) * (1.0F - u);   // ease-out cúbico
        }
        if (t[INICIO_ABERTURA] == 0L) {
            TEMPOS.remove(idJogador);
            return Float.NaN;
        }
        if (t[INSTANTE_FECHOU] == 0L) {          // fechou NESTE quadro
            t[INSTANTE_FECHOU] = agora;
        }
        float u = Mth.clamp((agora - t[INSTANTE_FECHOU]) / (float) FECHAR_MS, 0.0F, 1.0F);
        if (u >= 1.0F) {                         // terminou de sumir: esquece o jogador
            TEMPOS.remove(idJogador);
            return Float.NaN;
        }
        return (1.0F - u) * (1.0F - u);          // encolhe acelerando
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
            long agora = Util.getMillis();
            float abre = quantoAberta(p.getId(), aberto != null, agora);
            if (Float.isNaN(abre) || abre <= 0.0F || aberto == null) {
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
                // (5.41.1) a animação de desdobrar: pequena e girada quando começa a abrir,
                // crescendo até o tamanho cheio (ver quantoAberta). O giro sai de 55° e o
                // centro sobe um tico — é o "empurrão" da vareta.
                pose.translate(0.0F, TRANSLATE_Y - 0.10F * (1.0F - abre), 0.0F);
                float escala = 0.18F + 0.82F * abre;
                pose.scale(escala, escala, escala);
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(55.0F * (1.0F - abre)));
                // balanço de pano aberto (bem de leve, senão enjoa)
                float balanco = Mth.sin((agora % 4200L) / 4200.0F * 6.2831855F) * 1.6F;
                pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(balanco * abre));
                cupula.render(pose, vc, luz, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: desenhar o umbrella na cabeca falhou", t);
            }
        }
    }

}
