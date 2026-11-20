package dev.rptag.yumaho;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: pedido de ação (tecla/UI). O servidor valida tudo. */
public record YuActionPayload(int action, int arg) implements CustomPacketPayload {
    public static final Type<YuActionPayload> TYPE = new Type<>(YuMaho.rl("action"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, YuActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, YuActionPayload::action,
            ByteBufCodecs.INT, YuActionPayload::arg,
            YuActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
