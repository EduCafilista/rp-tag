package dev.rptag;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (5.4.0) CAIXA DO BICHO — a caixinha de música de onde o bicho sai.
 *
 * <p>Não spawna no mundo e não aparece em craft: só existe por
 * {@code /bicho aqui} (ou {@code /bicho <x> <y> <z>}). Enquanto existe, toca a
 * música (ver {@link NeptuneEventos}); quem chega a 3 blocos leva o susto e a
 * caixa arrebenta. Clique direito também abre (útil pra testar na mão) e a
 * mensagem diz quantos segundos faltam.
 */
public class CaixaBichoBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0);

    public CaixaBichoBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
            BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // (5.7.3) ANTES: cada clique chamava colocarCaixa, que criava OUTRA
        // caixa na lista e tocava a música de novo -> 5 cliques = 5 músicas
        // juntas. Agora o clique só toca o som (com trava de 4 s) e abre a
        // caixa: o bicho pula na tela de quem clicou, uma vez só.
        if (!(level instanceof net.minecraft.server.level.ServerLevel nv)
                || !(player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return InteractionResult.CONSUME;
        }
        NeptuneEventos.colocarCaixa(nv, pos);       // garante que existe (dedupe)
        // (5.7.7) 1º clique = som; 2º clique (depois de 4 s) = susto do bicho
        NeptuneEventos.toqueNaCaixa(nv, pos, sp);
        return InteractionResult.CONSUME;
    }
}
