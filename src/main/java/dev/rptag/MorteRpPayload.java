package dev.rptag;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.25.1) Pacote S2C: <b>esta morte é canônica?</b>
 *
 * <p>Pedido do dono: <i>"não quero essa animação na morte padrão dos players, faça algo mais
 * legal... a tela escurecendo quando ele morre, sobrando apenas os botões de respawn, lembrando
 * que nem sempre quando eles morrerem vai ser vida canônica, porque precisa ativar o canônico,
 * só morte fora de RP no caso"</i>.
 *
 * <p>O servidor sabe se o mundo está em evento canônico ({@code RPWorldData.isCanonEvent()}); o
 * cliente não. Então, no instante da morte, o servidor manda este recado:
 *
 * <ul>
 *   <li>{@code canon = false} → a morte é <b>fora de RP</b>: o cliente troca a tela do jogo pela
 *       {@code MorteTelaRp} (escurece, sobram só os botões de respawn);</li>
 *   <li>{@code canon = true} → morte de vida canônica: a tela padrão e o ritual do mod continuam
 *       como sempre foram.</li>
 * </ul>
 */
public record MorteRpPayload(boolean canon) implements CustomPacketPayload {

    public static final Type<MorteRpPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "morte_rp"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MorteRpPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, MorteRpPayload::canon,
                    MorteRpPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
