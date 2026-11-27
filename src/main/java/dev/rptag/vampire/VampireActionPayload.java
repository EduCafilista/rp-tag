package dev.rptag.vampire;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: 0 = morder, 1 = forma de morcego, 2 = visão vampírica. */
public record VampireActionPayload(int action) implements CustomPacketPayload {
    public static final Type<VampireActionPayload> TYPE = new Type<>(Vampire.rl("vampire_action"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, VampireActionPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, VampireActionPayload::action, VampireActionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
