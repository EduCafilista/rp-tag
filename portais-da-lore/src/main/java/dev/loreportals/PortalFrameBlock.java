package dev.loreportals;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * MOLDURA RUNICA — bloco do anel do portal grande.
 *
 * <p>O formato especifico e validado pelo nucleo (PortalBlockEntity): o
 * anel redondo 5x5 (11 molduras) com o nucleo no vao central de baixo.
 * A validacao periodica no servidor desliga o portal se o anel quebrar.</p>
 */
public class PortalFrameBlock extends Block {

    public static final MapCodec<PortalFrameBlock> CODEC = simpleCodec(PortalFrameBlock::new);

    public PortalFrameBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        // (qualquer moldura nova pode fechar um anel: avisa os nucleos proximos)
        PortalWorldTick.procurarNucleosDormantes(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        super.onRemove(state, level, pos, newState, moved);
        // (uma moldura quebrada abre o anel: avisa os nucleos proximos)
        PortalWorldTick.procurarNucleosDormantes(level, pos);
    }
}
