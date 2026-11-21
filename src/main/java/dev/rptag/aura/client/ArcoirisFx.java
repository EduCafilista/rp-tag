package dev.rptag.aura.client;

import dev.rptag.aura.AuraAnexos;
import dev.rptag.aura.AuraEstilo;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * (5.69.0 / refeito na 5.78.7) O VISUAL DO {@code /arcoiris}.
 *
 * <p><b>5.78.7 — pacote novo do dono:</b> <i>"estilo luz RGB: só as cores em volta do corpo, sem
 * clarão na pele, sem raios e sem anel no chão"</i>. Então esta classe ficou só com o teste da
 * aura: nada de estrela de 34 raios no peito, nada de anel/onda no chão e nada de partículas.
 *
 * <p>Quem desenha o arco-íris agora é <b>somente</b> a casca
 * ({@link AuraShellLayer}, método {@code rainbow}): 4 cópias finas com cor pura em volta do corpo.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class ArcoirisFx {

    /** true = este jogador está com a aura arco-íris ligada. */
    public static boolean ativa(Player p) {
        try {
            return p.getData(AuraAnexos.AURA.get()) == AuraEstilo.ARCOIRIS.index();
        } catch (RuntimeException erro) {
            return false;
        }
    }

    /** (5.78.7) Sem efeito de mundo: o arco-íris é só a casca colorida colada no corpo. */
    public static void render(RenderPlayerEvent.Post e, Player p, float t) {
        // nada a desenhar de propósito — ver o comentário da classe
    }

    /** (5.78.7) Sem partículas: nada de poeira, brilho ou fagulha. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        // desligado de propósito — ver o comentário da classe
    }

    private ArcoirisFx() {}
}
