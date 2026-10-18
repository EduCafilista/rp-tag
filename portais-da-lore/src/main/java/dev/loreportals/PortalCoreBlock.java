package dev.loreportals;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * NUCLEO DO PORTAL — o coracao do mod.
 *
 * <p>Clique com a mao vazia: valida o anel de molduras (formato redondo
 * 5x5) e ativa o portal grande; se nao houver anel, explica como ligar
 * pelo Cristal de Vinculo. Quando quebrado, desativa e desvincula o par.</p>
 */
public class PortalCoreBlock extends BaseEntityBlock {

    public static final MapCodec<PortalCoreBlock> CODEC = simpleCodec(PortalCoreBlock::new);

    public PortalCoreBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // bloco normal + o disco fica por conta do BER
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PortalBlockEntity portal)) {
            return InteractionResult.PASS;
        }
        if (portal.isAtivo()) {
            // silencioso (pedido do usuario: sem spam de chat) — o portal
            // tambem funciona so atravesando o oval
            return InteractionResult.CONSUME;
        } else {
            boolean ok = portal.tentarAtivarPorMoldura();
            if (ok) {
                player.sendSystemMessage(Component.literal(
                        "\u00a75\u00bb \u00a7dO portal da lore despertou! \u00a77(sem destino \u2014 conecte com o \u00a7dCristal de Vinculo\u00a77)"));
            } else {
                player.sendSystemMessage(Component.literal(
                        "\u00a75\u00bb \u00a77Formato nao fechado: cerque este Nucleo com Molduras Runicas dos dois lados "
                                + "e no teto (qualquer tamanho serve, sem buracos) \u2014 ou conecte dois nucleos com o Cristal."));
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PortalBlockEntity portal) {
                portal.desativarERemover();
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
