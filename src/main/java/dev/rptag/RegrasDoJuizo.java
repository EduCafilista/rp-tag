package dev.rptag;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * (5.29.0) <b>NINGUÉM CAVA PARA FORA DO JULGAMENTO (NEM PARA O VAZIO).</b>
 *
 * <p>Pedido do dono: <i>"os players nao devem conseguir quebrar para cima e nem para baixo"</i>.
 *
 * <p>Por que isso importa: no vazio o chão é bedrock e o teto é fechado justamente para não ter
 * saída por baixo (o void) nem por cima (o céu). No labirinto do Julgamento a mesma coisa — quem
 * cavasse para cima sairia do teto e escaparia do desafio; quem cavasse para baixo cairia no
 * vazio do mundo.
 *
 * <p>Regra:
 * <ul>
 *   <li><b>vazio</b> e <b>labirinto</b>: pode quebrar o bloco na MESMA altura dos pés (é a parede
 *       na frente do jogador); não pode quebrar nada <b>acima</b> nem <b>abaixo</b>;</li>
 *   <li><b>sala do julgamento</b>: não quebra nada — é cenário de cena, não mina.</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class RegrasDoJuizo {

    private RegrasDoJuizo() {
    }

    @SubscribeEvent
    public static void aoQuebrarBloco(BlockEvent.BreakEvent event) {
        try {
            if (!(event.getPlayer() instanceof ServerPlayer p)) {
                return;
            }
            if (!(p.level() instanceof net.minecraft.server.level.ServerLevel lv)) {
                return;
            }
            BlockPos alvo = event.getPos();
            // ---- a sala do Julgamento: cenário intocável
            if (JuizoFinal.ehSala(lv)) {
                event.setCanceled(true);
                avisar(p, "A sala do Julgamento não se deixa quebrar.");
                return;
            }
            // ---- o vazio e o labirinto: nada para cima, nada para baixo
            if (VazioReino.ehVazio(lv) || JuizoFinal.ehJuizo(lv)) {
                int pes = p.blockPosition().getY();
                if (alvo.getY() > pes) {
                    event.setCanceled(true);
                    avisar(p, "Aqui não se cava para CIMA.");
                } else if (alvo.getY() < pes) {
                    event.setCanceled(true);
                    avisar(p, "Aqui não se cava para BAIXO.");
                }
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: regra de quebrar bloco falhou (o jogo segue)", t);
        }
    }

    /** aviso curto na barra de ação, sem encher o chat. */
    private static void avisar(ServerPlayer p, String texto) {
        try {
            long agora = System.currentTimeMillis();
            Long antes = ULTIMO.get(p.getUUID());
            if (antes != null && agora - antes < 2500L) {
                return;   // não repete a cada bloco
            }
            ULTIMO.put(p.getUUID(), agora);
            p.displayClientMessage(Component.literal("\u00a78" + texto)
                    .withStyle(ChatFormatting.DARK_GRAY), true);
        } catch (Throwable ignorado) {
            // jogador de teste: sem chat
        }
    }

    private static final java.util.Map<java.util.UUID, Long> ULTIMO =
            new java.util.concurrent.ConcurrentHashMap<>();
}
