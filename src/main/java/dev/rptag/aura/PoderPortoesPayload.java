package dev.rptag.aura;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.71.0) cliente -> servidor: "apertei / soltei a tecla do poder da iluminacao".
 *
 * <p>Antes era um pacote vazio (um clique = uma rajada de 1,5 s). Agora ele carrega
 * {@code segurando}: enquanto for {@code true} os portoes nao param de lancar armas, e quando o
 * jogador solta a tecla chega {@code false} e a chuva de lancas para na hora.
 */
public record PoderPortoesPayload(boolean segurando) implements CustomPacketPayload {
    public static final Type<PoderPortoesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "iluminacao_portoes"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, PoderPortoesPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, PoderPortoesPayload::segurando,
                    PoderPortoesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
