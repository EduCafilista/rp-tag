package dev.rptag.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.rptag.RPTagMod;
import dev.rptag.VarinhaSombraItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * (5.50.0) <b>O GUARDA-CHUVA NA MÃO — vertical no mundo, cúpula acima da cabeça.</b>
 *
 * <h2>O que o dono pediu (e o que ele recusou)</h2>
 * <i>"não quero comando para configurar a umbrella, quero que você arrume para mim, faça para
 * ficar no local certo da mão, e quando aberto PARA CIMA, E ACIMA DA CABEÇA"</i>.
 *
 * <p>Então: <b>nada de comando, nada de arquivo de ajuste</b>. A pose é calculada, não escolhida
 * no olho.
 *
 * <h2>As três contas (e por que cada uma é exata)</h2>
 * <ol>
 *   <li><b>A MÃO.</b> {@code PlayerRenderer.getModel().translateToHand(braco, pose)} é a mesma
 *       função que o Minecraft usa para pôr QUALQUER item na mão. A origem do modelo (o pé do
 *       cabo) cai exatamente onde o jogo desenha a mão — não existe "quase na mão".</li>
 *   <li><b>PARA CIMA, de verdade.</b> A pose da mão vem girada (o braço cai, o corpo gira, o
 *       modelo do jogador é espelhado). Em vez de adivinhar o ângulo, a gente lê a MATRIZ da
 *       mão, pergunta "onde é para cima?" e gira o guarda-chuva para lá:
 *       {@code u = M⁻¹ · (0,-1,0)} (no espaço do modelo do jogador o "para cima" do mundo é −Y,
 *       por causa do espelhamento -1,-1,1 do renderizador de entidades) e
 *       {@code Q = rotationTo((0,1,0), u)}. O +Y do guarda-chuva (cabo → cúpula) passa a apontar
 *       para o céu <b>seja qual for a pose do braço</b> — andando, agachado, com o braço ocupado.
 *       Isto é o que faz ele ficar "PARA CIMA" sem nenhum ajuste manual.</li>
 *   <li><b>ACIMA DA CABEÇA.</b> O modelo cru tem 0,906 bloco de altura e a mão fica a ~0,75.
 *       Com a escala de {@link #ESCALA_ABERTA} a cúpula fica em 1,75..2,10 — acima da cabeça
 *       (que acaba em 1,80) <b>sem soltar o cabo da mão</b> (o cabo continua na origem, na mão:
 *       quem cresce é o guarda-chuva, não a distância até ele).</li>
 * </ol>
 *
 * <p>No modelo, o {@code thirdperson_*} está com escala ZERO de propósito: senão o vanilla
 * desenharia um segundo guarda-chuva por cima deste.
 *
 * <p>Regra de ouro do mod: erro aqui não desenha o guarda-chuva, nunca derruba o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class UmbrellaNaMao {

    private UmbrellaNaMao() {
    }

    /**
     * tamanho do guarda-chuva ABERTO.
     *
     * <p>0,906 (altura crua) × 1,50 = 1,36 bloco. Com a mão a 0,75, o topo da cúpula chega a
     * ~2,11 e o centro dela a ~1,80: acima da cabeça do jogador (1,80), que era o pedido.
     */
    private static final float ESCALA_ABERTA = 1.50F;

    /** o FECHADO é um bastão: 0,954 × 1,15 = 1,10 bloco de comprimento na mão. */
    private static final float ESCALA_FECHADA = 1.15F;

    /** "para cima" no espaço do modelo do jogador (o renderizador de entidades espelha em Y). */
    private static final Vector3f PARA_CIMA_NO_MUNDO = new Vector3f(0.0F, -1.0F, 0.0F);

    @SubscribeEvent
    public static void aoDesenharJogador(RenderPlayerEvent.Post event) {
        try {
            Player p = event.getEntity();
            if (p == null || p.isSpectator()) {
                return;
            }
            ItemStack naMao = UmbrellaHud.naMao(p);
            if (naMao == null || naMao.isEmpty()) {
                return;
            }
            // qual braço está segurando (respeita quem é canhoto: getMainArm)
            HumanoidArm braco = p.getMainHandItem() == naMao
                    ? p.getMainArm() : p.getMainArm().getOpposite();

            PoseStack pose = event.getPoseStack();
            pose.pushPose();

            // 1) A MÃO — a função do próprio jogo
            event.getRenderer().getModel().translateToHand(braco, pose);

            // 2) PARA CIMA — girá-lo para o "cima" do MUNDO, lido da matriz da mão
            Matrix3f giro = new Matrix3f(pose.last().pose());
            giro.invert();
            Vector3f cima = giro.transform(new Vector3f(PARA_CIMA_NO_MUNDO)).normalize();
            Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), cima);
            pose.mulPose(q);

            // 3) ACIMA DA CABEÇA — o tamanho certo pra cúpula passar da cabeça (o cabo fica na mão)
            boolean aberto = VarinhaSombraItem.aberto(naMao);
            float escala = aberto ? ESCALA_ABERTA : ESCALA_FECHADA;
            pose.scale(escala, escala, escala);

            Minecraft mc = Minecraft.getInstance();
            mc.getItemRenderer().renderStatic(naMao, ItemDisplayContext.FIXED,
                    event.getPackedLight(), OverlayTexture.NO_OVERLAY, pose,
                    event.getMultiBufferSource(), p.level(), p.getId());

            pose.popPose();
        } catch (Throwable t) {
            // o guarda-chuva é enfeite: se falhar, o jogador continua inteiro
        }
    }
}
