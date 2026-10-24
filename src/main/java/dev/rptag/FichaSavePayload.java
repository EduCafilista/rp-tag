package dev.rptag;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (4.8.0) CLIENTE -> SERVIDOR: grava a ficha.
 *
 * <p>É enviado a CADA capítulo (o jogador pode fechar no meio que não perde
 * nada) e uma última vez com {@code concluir = true} quando ele confirma.
 */
public record FichaSavePayload(FichaRp ficha, boolean concluir) implements CustomPacketPayload {

    public static final Type<FichaSavePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "ficha_salvar"));

    public static final StreamCodec<FriendlyByteBuf, FichaSavePayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.encode(buf), FichaSavePayload::new);

    public FichaSavePayload(FriendlyByteBuf buf) {
        this(FichaCodec.ler(buf), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        FichaCodec.escrever(buf, ficha);
        buf.writeBoolean(concluir);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
