package dev.rptag;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * (5.30.0) <b>O PORTAL DO REINO NÃO LEVA PRO OVERWORLD — LEVA PRA SALA DO JUÍZO.</b>
 *
 * <p>Pedido do dono: <i>"para o player entrar no portal em vez dele sair para o overworld ele
 * vai para a sala que eu te enviei"</i>.
 *
 * <p>O Reino do outro mod tem a própria saída (o portal dele). Em vez de reescrever o mod de
 * referência (que é fechado), o RP Tag <b>escuta a troca de dimensão</b>: se quem saiu do Reino
 * é um jogador que está no meio do Julgamento, o destino deixa de ser o overworld e passa a ser
 * a <b>arena do .schem</b> — a mesma sala da luta do boss.
 *
 * <p>Quem o ADM tirou do Julgamento ({@code /juizo sair}, vitória ou derrota) não é pego por
 * esta regra: a marca sai antes, e o jogador vai pra onde o portal dele mandar.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class JuizoPortalExterno {

    private JuizoPortalExterno() {
    }

    @SubscribeEvent
    public static void aoTrocarDeDimensao(PlayerEvent.PlayerChangedDimensionEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer p)) {
                return;
            }
            if (!JuizoReino.ehChave(event.getFrom())) {
                return;                       // não veio do reino: não é problema nosso
            }
            if (!JuizoFinal.estaProcurando(p.getUUID())) {
                return;                       // não está no meio do Julgamento
            }
            // o teleporte é no tick seguinte: mexer em dimensão no meio do evento do jogo é
            // pedir pra corromper o pacote de viagem do jogador
            p.server.execute(() -> {
                try {
                    JuizoFinal.achouSaida(p);
                } catch (Throwable t) {
                    RPTagMod.LOGGER.warn("RP Tag: desviar o portal do reino falhou", t);
                }
            });
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: regra do portal do reino falhou", t);
        }
    }
}
