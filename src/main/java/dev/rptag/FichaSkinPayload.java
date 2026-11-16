package dev.rptag;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.61.0) cliente -> servidor: "a skin que EU estou usando agora e esta" (link + modelo).
 * O servidor confere ({@link SkinFicha}) e guarda na ficha do jogador.
 */
public record FichaSkinPayload(String url, String modelo) implements CustomPacketPayload {
    public static final Type<FichaSkinPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "ficha_skin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FichaSkinPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.url == null ? "" : p.url, SkinFicha.URL_MAX).writeUtf(p.modelo == null ? "" : p.modelo, 16),
            buf -> new FichaSkinPayload(buf.readUtf(SkinFicha.URL_MAX), buf.readUtf(16)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
