package dev.rptag.client;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * RAIOS VERMELHOS DO VAZIO — geometria 3D (5.11.1).
 *
 * <h2>A ideia é a do dono, não a minha</h2>
 * <i>"quero os raios vermelhos saindo de deus, se não como vai funcionar quando ele
 * estiver voando? quero essa aura em volta dele para ser algo bizarro"</i>.
 *
 * <p>Então: <b>raios vermelhos saindo do corpo</b> — para todo lado, em 3D, e
 * <b>presos no jogador</b> (nunca no chão). Voando, de pé, dentro d'água, no
 * Nether ou no exílio: os raios vão junto com ele, porque tudo é medido a partir
 * do corpo (pé / peito / cabeça) e não do bloco embaixo.
 *
 * <h2>Por que os raios de antes "sumiam com shader" (e o que mudou aqui)</h2>
 * A versão 5.7.4 desenhava em {@link RenderType#lightning()}, que é
 * <b>blend ADITIVO</b> ({@code SRC_ALPHA, ONE}): soma luz na tela. Num pack de
 * shader claro a soma estoura em branco e o vermelho desaparece.
 *
 * <p>Agora é {@link RenderType#debugQuads()}: {@code POSITION_COLOR}, QUADS,
 * <b>blend NORMAL</b> ({@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}) e <b>escreve
 * profundidade</b>. Vermelho por cima de céu claro continua vermelho, porque
 * não soma — ele <i>substitui</i> a cor por uma mistura. Funciona com Iris,
 * Sodium e no jogo puro.
 *
 * <h2>Como cada raio é desenhado (3 passadas, sem z-fighting)</h2>
 * <ol>
 *   <li><b>capa</b> vermelho-escuro, larga (2,4×), alpha ~0,55 — pega o "corpo" do raio;</li>
 *   <li><b>corpo</b> vermelho vivo, largura normal, alpha ~0,9 — é o que lê como raio;</li>
 *   <li><b>núcleo</b> laranja-quente, fino (0,38×), opaco — estala por dentro.</li>
 * </ol>
 * As três passadas ficam a poucos milímetros uma da outra <b>na direção da
 * câmera</b> (não no plano da tela), então nunca dão z-fighting e o núcleo
 * sempre aparece em cima do corpo.
 *
 * <h2>"Bizarro"</h2>
 * <ul>
 *   <li>duas famílias: <b>longos</b> (1,6 a 6 blocos, 10 raios) e <b>cerdas</b>
 *       curtas (0,35 a 1,1 bloco, 20 raios) grudadas no corpo — é o que faz
 *       parecer que a coisa está <i>fervendo</i> em volta dele;</li>
 *   <li>o serrilhado é <b>re-sorteado a cada ~110 ms</b>, então os raios
 *       estalam sem parar (não é um desenho congelado);</li>
 *   <li>o conjunto <b>gira devagar</b> em volta do corpo;</li>
 *   <li>quem <b>é</b> o vazio vê menos e mais fino (precisa mirar); quem olha de
 *       fora vê tudo.</li>
 * </ul>
 *
 * <p>Regra de ouro do mod: erro aqui desliga os raios, nunca o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class RaiosVazioFx {

    private RaiosVazioFx() {
    }

    private static boolean ativo;
    private static boolean euSou;
    private static UUID alvo;
    /** surto da transformação: a aura de penas fica mais forte por 2,5 s. */
    private static long surtoAte = 0L;

    // ------------------------------------------------------------- estado
    /** Chamado pelo pacote de aura: quem é o vazio e se sou eu. */
    public static void definir(UUID id, boolean souEu) {
        if (id == null) {
            desligar();
            return;
        }
        if (!id.equals(alvo) || !ativo) {
            surtoAte = System.currentTimeMillis() + 2500L;   // transformação
        }
        alvo = id;
        euSou = souEu;
        ativo = true;
    }

    public static void desligar() {
        ativo = false;
        alvo = null;
    }

    public static boolean ligado() {
        return ativo && alvo != null;
    }

    /** (5.13.0) quem é o vazio (o manto de escuridão usa o mesmo alvo dos raios). */
    public static UUID alvoUuid() {
        return ativo ? alvo : null;
    }

    /** (5.13.0) o surto da transformação ainda está de pé? */
    public static boolean emSurto() {
        return System.currentTimeMillis() < surtoAte;
    }

    // ------------------------------------------------------------- render
    /**
     * (5.33.0) <b>AQUI NÃO SE DESENHA MAIS NADA.</b>
     *
     * <p>O dono: <i>"no vazio ligar, pode tirar esses espinhos que ficam na minha volta, achei
     * horrível"</i> e <i>"mesmo após desligar continua circulando em volta de mim os espinhos
     * vermelhos"</i>. Eram as <b>cerdas</b> de {@code dust} vermelho coladas no corpo (10 raios
     * longos + 20 cerdas curtas). Saíram de vez: o modo deus usa só a aura de penas pretas do
     * {@link VazioAuraFx}, que aparece e some sozinha.
     *
     * <p>A classe continua existindo (e assinando o evento) só para não quebrar quem a chama —
     * mas o desenho é um retorno imediato.
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // nada. ver o comentário acima.
    }

}
