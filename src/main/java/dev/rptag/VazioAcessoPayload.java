package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.41.0) Pacote S2C: <b>"este jogador pode usar os poderes do vazio?"</b>
 *
 * <p>O dono: <i>"a powerwheel aparece mesmo sem o player ter o vazio ligado, corrija isso"</i>.
 *
 * <p>Até a 5.40.0 o leque (tecla R) abria para <b>qualquer</b> jogador do servidor — o mod
 * desenhava a roda sempre, e só na hora de soltar o poder o servidor dizia "os poderes só
 * respondem no vazio". Agora a roda <b>só existe</b> para quem realmente tem o dom, e quem
 * decide isso continua sendo o servidor ({@link VazioPoderes#temPoder}), que é a única fonte
 * de verdade — o cliente só obedece.
 *
 * <p>O servidor manda este pacote quando o estado <b>muda</b> (entrou/saiu do vazio, do reino
 * do Juízo, virou ADM, conectou) e uma vez de tempo em tempo como rede de segurança.
 *
 * @param pode {@code true} = tem o dom (roda abre); {@code false} = roda nem aparece
 */
public record VazioAcessoPayload(boolean pode) implements CustomPacketPayload {

    public static final Type<VazioAcessoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "vazio_acesso"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, VazioAcessoPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeBoolean(p.pode()),
                    buf -> new VazioAcessoPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
