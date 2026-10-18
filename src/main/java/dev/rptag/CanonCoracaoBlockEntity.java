package dev.rptag;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** (3.84.0) BlockEntity do Coração Canônico (a animação vive no BER). */
public class CanonCoracaoBlockEntity extends BlockEntity {

    public CanonCoracaoBlockEntity(BlockPos pos, BlockState state) {
        super(RPTagMod.CORACAO_BE.get(), pos, state);
    }
}
