package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.31.0) Pacote C2S: <b>o jogador apertou uma habilidade no leque</b> (tecla R).
 *
 * <p>O dono: <i>"eu não quero que os poderes sejam por comandos, quero que seja igual a do mod
 * que eu te mandei, uma keybind que mostra as habilidades e eu posso arremessar ataque"</i>.
 * O cliente só manda o número da habilidade; quem decide se pode, a recarga e o efeito é o
 * servidor (nunca confiar no cliente para dano).
 *
 * @param poder 0 = raio, 1 = orbe, 2 = onda, 3 = olhos, 4 = chuva de penas, 5 = banir
 */
public record VazioPoderC2S(int poder) implements CustomPacketPayload {

    public static final Type<VazioPoderC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "vazio_poder_usar"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, VazioPoderC2S> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> ByteBufCodecs.VAR_INT.encode(buf, p.poder()),
                    buf -> new VazioPoderC2S(ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
