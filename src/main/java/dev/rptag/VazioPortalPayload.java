package dev.rptag;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.13.0) ESTADO DO PORTAL DO VAZIO — servidor para cliente.
 *
 * <p>O portal e as colunas de luz são desenhados no <b>cliente</b> por geometria
 * ({@code VazioPortalFx}), não por partícula. É isso que faz a aura e o vórtice continuarem
 * aparecendo mesmo com as partículas do jogador no mínimo (pedido 50).
 *
 * <p>O servidor manda uma vez por segundo (e sempre que muda) só o essencial:
 * <ul>
 *   <li>{@code x,y,z} — onde o portal está (o da praça ou o invocado por comando);</li>
 *   <li>{@code pedais} — máscara de 8 bits dos pedestais que ELE já encheu;</li>
 *   <li>{@code santuarios} — quantos altares ele já esvaziou (mostra a dica no mundo);</li>
 *   <li>{@code estado} — 0 fechado, 1 abrindo (de baixo para cima), 2 aberto.</li>
 * </ul>
 */
public record VazioPortalPayload(int x, int y, int z, int pedais, int santuarios, int estado)
        implements CustomPacketPayload {

    public static final Type<VazioPortalPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "vazio_portal"));

    public static final StreamCodec<ByteBuf, VazioPortalPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.x());
                ByteBufCodecs.VAR_INT.encode(buf, p.y());
                ByteBufCodecs.VAR_INT.encode(buf, p.z());
                ByteBufCodecs.VAR_INT.encode(buf, p.pedais());
                ByteBufCodecs.VAR_INT.encode(buf, p.santuarios());
                ByteBufCodecs.VAR_INT.encode(buf, p.estado());
            },
            buf -> new VazioPortalPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** quantos pedestais estão cheios para quem recebeu. */
    public int cheios() {
        return Integer.bitCount(pedais & 0xFF);
    }
}
