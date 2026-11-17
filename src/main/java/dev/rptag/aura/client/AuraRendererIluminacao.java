package dev.rptag.aura.client;

import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * (5.60.0) A AURA DE ILUMINACAO — desde a 5.64.0 o desenho e da {@link AuraShellLayer}.
 *
 * <p>Historico: na 5.63.0 (pedido 118) saiu o circulo magico, a coluna, as helices, os aneis, os
 * raios e os 5 portoes — ficou "so a aura em volta do player brilhante", feita com 3 cascas de
 * cilindro. Na 5.64.0 o zip novo trouxe um jeito MELHOR: a {@code AuraShellLayer} redesenha o
 * proprio modelo do jogador dilatado em 10 direcoes (8 no plano + cima/baixo) e mostra so as faces
 * de TRAS — o brilho fica colado na silhueta, de espessura uniforme, sem cilindro flutuando.
 *
 * <p>(5.64.1) As particulas douradas antigas tambem sairam ("tira os raios amarelos antigo... fica
 * esquisito eles juntos com o novo"). A iluminacao e SO a casca da silhueta; esta classe ficou so
 * com a tinta do VAZIO, que e coisa do mundo.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class AuraRendererIluminacao {
    /** (5.64.0) o VAZIO ganha a animacao de tinta (tentaculos + mancha no chao). */
    @SubscribeEvent
    public static void onRender(RenderPlayerEvent.Post e) {
        Player p = e.getEntity();
        if (p.isSpectator() || p.isInvisible()) return;
        if (AuraFx.ehVazio(p)) {
            AuraFx.renderVazio(e, p, p.tickCount + e.getPartialTick());
        }
    }

    // (5.64.1) AS PARTICULAS DOURADAS ANTIGAS SAIRAM.
    // O dono: "tira os raios amarelos antigo, tire eles, fica esquisito eles juntos com o novo".
    // Era o que sobrava da aura de 5.63.0 (poeira dourada em anel + brilhos END_ROD subindo) e
    // brigava com a casca nova, que ja faz o contorno sozinha. Agora a iluminacao e SO a casca.

    private AuraRendererIluminacao() {}
}
