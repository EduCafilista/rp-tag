package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Pacote C2S: jogador ligou/desligou o MODO BALAO na tela de personalizacao.
 */
public record SetBubbleModePayload(boolean on) implements CustomPacketPayload {

    public static final Type<SetBubbleModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "set_bubble_mode"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SetBubbleModePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> ByteBufCodecs.BOOL.encode(buf, p.on()),
                    buf -> new SetBubbleModePayload(ByteBufCodecs.BOOL.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
