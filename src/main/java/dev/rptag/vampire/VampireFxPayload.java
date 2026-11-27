package dev.rptag.vampire;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> clientes próximos: gotas de sangue indo da vítima para o vampiro. */
public record VampireFxPayload(int victim, int vampire) implements CustomPacketPayload {
    public static final Type<VampireFxPayload> TYPE = new Type<>(Vampire.rl("vampire_fx"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, VampireFxPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VampireFxPayload::victim,
            ByteBufCodecs.VAR_INT, VampireFxPayload::vampire,
            VampireFxPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
