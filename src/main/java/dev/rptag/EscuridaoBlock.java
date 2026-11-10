package dev.rptag;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * (5.13.2) ESCURIDÃO — o bloco negro do PORTAL do vazio.
 *
 * <h2>O que o dono pediu</h2>
 * <i>"não quero que seja black concrete, crie um bloco preto e não pode passar para
 * trás o bloco, continue arrumando e melhorando, um bloco negro chamado escuridão"</i>.
 *
 * <p>Então: o miolo do portal não é mais concreto preto — é este bloco, com nome
 * próprio (<b>Escuridão</b>), textura preta com veios violeta.
 *
 * <p>(5.13.5) <b>ATRAVESSÁVEL</b> — pedido 55: <i>"não quero que o portal seja bloco
 * físico, quero que o player possa passar, atravessar ele mesmo, como um portal"</i>.
 * O bloco perdeu a colisão ({@code noCollission}): dá para ANDAR por dentro dele, e
 * ficar dentro por um instante já leva a pessoa para o outro lado — como o portal do
 * Nether, mas com o clique funcionando também (nada de precisar clicar).
 *
 * <ul>
 *   <li><b>indestrutível</b> em survival (`strength(-1, 3600000)`) — o portal não é
 *       quebrado por acidente (nem por explosão);</li>
 *   <li>clique direito usa o portal (o {@link VazioReino} decide para onde levar);</li>
 *   <li>é o mesmo bloco no overworld e no vazio, então dá para construir o portal
 *       em qualquer lugar com {@code /setblock}.</li>
 * </ul>
 */
public class EscuridaoBlock extends Block {

    public EscuridaoBlock(Properties props) {
        super(props);
    }


    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;        // não deixa o braço "bater" no bloco
        }
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            // (5.33.0) na SALA DO JULGAMENTO a escuridão é a SAÍDA (pedido 90: o dono reclamou
            // do "aviso de morte" a cada clique direito — era esta mensagem). Quem clica nela
            // com o portão aberto volta à vida com +2 vidas.
            if (JuizoFinal.atravessarPortao(sp)) {
                return InteractionResult.CONSUME;
            }
            boolean usado = VazioReino.interagirPortal(sp, sl, pos);
            if (usado) {
                return InteractionResult.CONSUME;
            }
            // fora da sala: texto neutro, sem falar em "morte"
            sp.displayClientMessage(Component.literal(
                    "\u00a78Aqui a escuridão é só pedra. \u00a77Nada atravessa daqui."),
                    true);
        }
        return InteractionResult.CONSUME;
    }
}
