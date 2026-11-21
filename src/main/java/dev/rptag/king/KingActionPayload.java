package dev.rptag.king;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: escolheu uma habilidade na roda (0..5 habilidades, 6 ultimate). */
public record KingActionPayload(int ability) implements CustomPacketPayload {
    public static final Type<KingActionPayload> TYPE = new Type<>(King.rl("action"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, KingActionPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, KingActionPayload::ability, KingActionPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
