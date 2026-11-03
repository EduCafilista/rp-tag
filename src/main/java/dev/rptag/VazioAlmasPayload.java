package dev.rptag;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.13.6) AS ALMAS VAZIAS — servidor para cliente.
 *
 * <p>Pedido do dono: <i>"faça almas vazias que ficaram perdidas naquele local"</i>. No servidor
 * cada alma é um suporte de armadura invisível (que anda, flutua e se inclina para quem chega);
 * o <b>corpo</b> dela é desenhado no cliente por geometria — {@code VazioAlmasFx} — pelo mesmo
 * motivo do portal: <b>partícula desligada não pode sumir com o bicho</b>.
 *
 * <p>O pacote leva um punhado de posições (x, y, z por alma), mandado uma vez por segundo. Se o
 * cliente for antigo, o pior que acontece é a alma ficar só com a névoa em partícula — nada de
 * erro.
 */
public record VazioAlmasPayload(float[] pontos) implements CustomPacketPayload {

    public static final Type<VazioAlmasPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "vazio_almas"));

    /** quantas almas cabem num pacote (cada uma gasta 12 bytes). */
    public static final int MAX = 32;

    public static final StreamCodec<ByteBuf, VazioAlmasPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int n = Math.min(MAX, p.pontos().length / 3);
                buf.writeByte(n);
                for (int i = 0; i < n * 3; i++) {
                    buf.writeFloat(p.pontos()[i]);
                }
            },
            buf -> {
                int n = buf.readUnsignedByte();
                float[] a = new float[n * 3];
                for (int i = 0; i < a.length; i++) {
                    a[i] = buf.readFloat();
                }
                return new VazioAlmasPayload(a);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** quantas almas vieram. */
    public int quantas() {
        return Math.min(MAX, pontos.length / 3);
    }
}
