package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.17.0) AXION — a gatinha do canto inferior direito da tela.
 *
 * <p>Pedido do dono: <i>"a Axion vai ter no cantinho da tela o tempo todo, na maior parte do tempo
 * vai ficar dormindo, e ela só vai acordar quando um adm ativar um comando, fica no canto inferior
 * direito, ela tem animação, e deve remover o fundo dela para ficar bonito de se ver"</i>.
 *
 * <p>Pacote S2C. {@code estado}: 0 = escondida, 1 = dormindo (padrão), 2 = acordada.
 * {@code escala} / {@code x} / {@code y} deixam o ADM ajustar tamanho e canto.
 */
public record AxionPayload(int estado, float escala, float x, float y)
        implements CustomPacketPayload {

    public static final int ESCONDIDA = 0;
    public static final int DORMINDO = 1;
    public static final int ACORDADA = 2;

    public static final Type<AxionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "axion_hud"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, AxionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, p.estado());
                        ByteBufCodecs.FLOAT.encode(buf, p.escala());
                        ByteBufCodecs.FLOAT.encode(buf, p.x());
                        ByteBufCodecs.FLOAT.encode(buf, p.y());
                    },
                    buf -> new AxionPayload(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.FLOAT.decode(buf),
                            ByteBufCodecs.FLOAT.decode(buf),
                            ByteBufCodecs.FLOAT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
