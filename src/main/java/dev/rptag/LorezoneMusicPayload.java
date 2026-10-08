package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (3.50.0) Pacote S2C: MUSICA CUSTOM de Lore Zone — o cliente toca um
 * arquivo .ogg da pasta {@code config/rptag/musicas/<nome>.ogg} em LOOP
 * (API de musica propria: admin converte qualquer musica — por exemplo de
 * um video do YouTube, com yt-dlp — joga na pasta e usa
 * {@code /lorezone musica <id> @<nome>}).
 *
 * @param tocar true = tocar em loop · false = parar a musica atual
 * @param nome  nome do arquivo SEM extensao (letras, numeros, _ e -)
 */
public record LorezoneMusicPayload(boolean tocar, String nome) implements CustomPacketPayload {

    public static final Type<LorezoneMusicPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "lorezone_music"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, LorezoneMusicPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, LorezoneMusicPayload::tocar,
                    ByteBufCodecs.STRING_UTF8, LorezoneMusicPayload::nome,
                    LorezoneMusicPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
