package dev.rptag;

import java.util.UUID;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.7.2) Pacote S2C: o TÍTULO de um jogador (etiqueta colorida + título
 * personalizado). Etiqueta e título vazios = o jogador não tem título (o
 * cliente apaga o que tinha).
 *
 * <p>É o que faz o título aparecer também no NAMETAG acima da cabeça — o TAB e
 * o chat quem monta é o servidor.
 */
public record TituloSyncPayload(UUID playerId, String etiqueta, int cor, String personalizado)
        implements CustomPacketPayload {

    public static final Type<TituloSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "sync_titulo"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, TituloSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        net.minecraft.core.UUIDUtil.STREAM_CODEC.encode(buf, p.playerId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.etiqueta() == null ? "" : p.etiqueta());
                        ByteBufCodecs.VAR_INT.encode(buf, p.cor());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.personalizado() == null ? "" : p.personalizado());
                    },
                    buf -> new TituloSyncPayload(
                            net.minecraft.core.UUIDUtil.STREAM_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
