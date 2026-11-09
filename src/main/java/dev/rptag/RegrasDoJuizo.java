package dev.rptag;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * (5.29.0 / reforçada na 5.31.0) <b>A SALA DO JUÍZO NÃO SE QUEBRA. NEM PARA CIMA, NEM PARA BAIXO.</b>
 *
 * <p>Pedidos do dono: <i>"os players nao devem conseguir quebrar para cima e nem para baixo"</i>
 * (86) e, agora, <i>"o player nao deve conseguir quebrar a 'juizo sala'"</i>.
 *
 * <h2>O que a 5.31.0 reforça</h2>
 * A versão anterior cancelava só o {@code BlockEvent.BreakEvent}. Isso deixava três furos:
 * <ol>
 *   <li><b>colocar</b> bloco continuava liberado (dava pra emparedar a arena);</li>
 *   <li>outro mod (ou um evento com prioridade maior) podia devolver o evento como "não
 *       cancelado" depois da gente — por isso agora escutamos na <b>prioridade mais alta</b>;</li>
 *   <li>ferramenta que quebra em área (pá/escavadeira de outro mod) passava pelos blocos de
 *       baixo. Agora a regra olha <b>o bloco e a posição dos pés</b> no momento do evento.</li>
 * </ol>
 *
 * <p>Regra final:
 * <ul>
 *   <li><b>sala do Julgamento</b> ({@code rptag:sala_julgamento}): quebrar e colocar
 *       <b>cancelados sempre</b> — é cenário de cena, não mina;</li>
 *   <li><b>vazio / reino do Juízo / labirinto</b>: pode mexer na altura dos pés (a parede na
 *       frente); <b>acima</b> e <b>abaixo</b> dos pés, cancelado.</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class RegrasDoJuizo {

    private RegrasDoJuizo() {
    }

    /** (5.31.0) quebrar bloco. Prioridade HIGHEST: ninguém desfaz a nossa decisão. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void aoQuebrarBloco(BlockEvent.BreakEvent event) {
        try {
            if (!(event.getPlayer() instanceof ServerPlayer p)) {
                return;
            }
            // (5.31.0) usa o nível DO EVENTO (não o do jogador): se outro mod mexer no bloco
            // de outro mundo, a regra continua valendo para o bloco certo
            if (!(eventoNivel(event) instanceof ServerLevel lv)) {
                return;
            }
            BlockPos alvo = event.getPos();
            if (JuizoFinal.ehSala(lv) || dentroDaArena(lv, alvo)) {
                event.setCanceled(true);
                avisar(p, "A sala do Julgamento não se deixa quebrar.");
                return;
            }
            if (VazioReino.ehVazio(lv) || JuizoFinal.ehJuizo(lv) || JuizoReino.eh(lv)) {
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

    /** (5.31.0) COLOCAR bloco na sala: também não. (senão dá pra emparedar a arena) */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void aoColocarBloco(BlockEvent.EntityPlaceEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer p)) {
                return;
            }
            if (!(eventoNivel(event) instanceof ServerLevel lv)) {
                return;
            }
            if (JuizoFinal.ehSala(lv) || dentroDaArena(lv, event.getPos())) {
                event.setCanceled(true);
                avisar(p, "A sala do Julgamento não aceita blocos.");
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: regra de colocar bloco falhou (o jogo segue)", t);
        }
    }

    /** (5.31.0) balde/líquido e afins: a sala é seca. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void aoUsarBalde(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer p)) {
                return;
            }
            if (!(event.getLevel() instanceof ServerLevel lv)) {
                return;
            }
            if (JuizoFinal.ehSala(lv) && p.getMainHandItem().getItem() instanceof
                    net.minecraft.world.item.BucketItem) {
                event.setCanceled(true);
                avisar(p, "A sala do Julgamento não aceita líquidos.");
            }
        } catch (Throwable t) {
            // sem drama: o pior caso é jogar água no chão da arena
        }
    }

    /** o nível do evento (BlockEvent), sem depender do nível em que o jogador está. */
    private static net.minecraft.world.level.LevelAccessor eventoNivel(BlockEvent event) {
        try {
            return event.getLevel();
        } catch (Throwable t) {
            return null;
        }
    }

    /** (5.31.0) o bloco está dentro do retângulo da arena do dono? */
    private static boolean dentroDaArena(ServerLevel lv, BlockPos p) {
        if (!JuizoFinal.ehSala(lv)) {
            return false;
        }
        int x = p.getX(), z = p.getZ(), y = p.getY();
        return x >= JuizoSala.ORIGEM_X - 2 && x <= JuizoSala.ORIGEM_X + JuizoSala.LARGURA + 2
                && z >= JuizoSala.ORIGEM_Z - 2 && z <= JuizoSala.ORIGEM_Z + JuizoSala.COMPRIMENTO + 2
                && y >= -2 && y <= JuizoSala.ALTURA + 4;
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
