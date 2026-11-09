package dev.rptag;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.26.0) Pacote S2C: <b>o vampiro está na noite dele?</b>
 *
 * <p>Pedido do dono: <i>"a night vision do vampiro está falhando"</i>. O efeito de visão noturna
 * do jogo continua sendo dado (é ele que ilumina e mostra o ícone), mas ele depende do cliente —
 * e com shader/embalagem gráfica o brilho às vezes simplesmente não aparece. Então o servidor
 * avisa o cliente quando o vampiro está <b>de noite</b>, e o cliente desenha a
 * <b>visão de vampiro</b> do mod por cima (um clarão prateado fraco, que só acende no escuro de
 * verdade). Assim ele enxerga mesmo que o efeito do jogo não pinte nada.
 */
public record VampiroNoitePayload(boolean noite) implements CustomPacketPayload {

    public static final Type<VampiroNoitePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "vampiro_noite"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VampiroNoitePayload> STREAM_CODEC =
            StreamCodec.composite(
                    net.minecraft.network.codec.ByteBufCodecs.BOOL, VampiroNoitePayload::noite,
                    VampiroNoitePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
