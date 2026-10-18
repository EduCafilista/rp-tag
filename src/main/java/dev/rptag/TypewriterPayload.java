package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (3.65.0) TYPEWRITER do balao como OPCAO do jogador: C2S = o jogador
 * pediu ligar/desligar (comando ou tela); S2C = o servidor confirma o
 * estado (o cliente so desenho obey o static local).
 */
public record TypewriterPayload(boolean on) implements CustomPacketPayload {

    public static final Type<TypewriterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "typewriter"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, TypewriterPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> ByteBufCodecs.BOOL.encode(buf, p.on()),
                    buf -> new TypewriterPayload(ByteBufCodecs.BOOL.decode(buf)));

    @Override
    public Type<? extends TypewriterPayload> type() {
        return TYPE;
    }
}
