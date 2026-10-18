package dev.rptag;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * (3.84.0) BLOCO Coração Canônico: um coração 3D girando (BER) que SÓ
 * existe onde um admin coloca ({@code /give @s rptag:coracao_canon}) —
 * nunca spawna no mundo. Quem encontrar: SHIFT + clique direito = +1 vida
 * canônica (o coração se consuma). Sem shift = dica; vidas cheias (5/5)
 * = o coração fica lá pra quem precisar. Indestrutível em survival
 * (adm em creative quebra pra remover, sem drop).
 */
public class CanonCoracaoBlock extends Block implements EntityBlock {

    private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0);

    public CanonCoracaoBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
            BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CanonCoracaoBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            ServerPlayer sp = (ServerPlayer) player;
            RPWorldData wd = RPWorldData.get(level.getServer());
            int vidas = wd.getCanonLives(sp.getUUID());
            if (!sp.isShiftKeyDown()) {
                sp.displayClientMessage(Component.literal(
                        "Segure SHIFT e clique no coração pra receber a vida canônica")
                        .withStyle(ChatFormatting.YELLOW), true);
                return InteractionResult.CONSUME;
            }
            if (vidas >= 5) {
                sp.displayClientMessage(Component.literal(
                        "Suas vidas canônicas já estão CHEIAS (5/5) — o coração continua aqui")
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                return InteractionResult.CONSUME;
            }
            wd.setCanonLives(sp.getUUID(), vidas + 1);
            level.removeBlock(pos, false);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 1.2F, 1.1F);
            PacketDistributor.sendToPlayer(sp, new ScreenTextPayload(
                    "VOCÊ GANHOU UMA NOVA CHANCE — uma vida canônica foi restaurada ("
                            + (vidas + 1) + "/5)", 6, 0));
        }
        return InteractionResult.CONSUME;
    }
}
