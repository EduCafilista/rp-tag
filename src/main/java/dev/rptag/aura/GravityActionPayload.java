package dev.rptag.aura;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Cliente -> servidor. action 0 = liga/desliga a aura; action 1 = usa o poder arg (0..7, índice da roda). */
public record GravityActionPayload(int action, int arg) implements CustomPacketPayload {
    public static final Type<GravityActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "gravity_action"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, GravityActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GravityActionPayload::action,
            ByteBufCodecs.VAR_INT, GravityActionPayload::arg,
            GravityActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
