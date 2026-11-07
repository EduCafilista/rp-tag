package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.20.0) A AXION FALA — balão de fala vermelho com letras pretas saindo dela.
 *
 * <p>Pedido do dono: <i>"sera que teria alguma forma de ter um balao de fala vermelinho com
 * letras petras, que saia da axion? veja se tem como fazer isso kkkk seria legal para o adm
 * falar comigo"</i>.
 *
 * <p>Tem como, sim: a Axion é desenhada no <b>cliente</b> (o HUD do canto), então quem sabe
 * onde ela está é o cliente. O servidor só manda o TEXTO e por quanto tempo ele fica na tela
 * ({@code /rp admin axion falar <texto>}), e o cliente desenha o balãozinho colado nela,
 * com o rabicho apontando pra boca dela.
 *
 * <p>S2C: uma fala, para todo mundo (a Axion é a mesma pra todos os jogadores).
 *
 * @param texto    o que ela diz (já em texto puro; o cliente pinta de preto)
 * @param segundos quanto tempo o balão fica na tela (o servidor manda; 8 s por padrão)
 */
public record AxionFalaPayload(String texto, int segundos)
        implements CustomPacketPayload {

    public static final Type<AxionFalaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "axion_fala"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, AxionFalaPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.texto());
                        ByteBufCodecs.VAR_INT.encode(buf, p.segundos());
                    },
                    buf -> new AxionFalaPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
