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
        level.playSound(null, pos, RPTagMod.SOM_CAIXA.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
        player.sendSystemMessage(Component.literal(
                "\u00a78A caixinha continua girando... \u00a77Não fique muito perto."));
        // zera o contador pra "chamar" quem estava longe
        NeptuneEventos.colocarCaixa((net.minecraft.server.level.ServerLevel) level, pos);
        return InteractionResult.CONSUME;
    }
}
