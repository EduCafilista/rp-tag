package dev.rptag;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (3.77.0) Pacote S2C: BANNER da vida canônica (o "❀ LORE ON ❀ /
 * ❀ HARD-RP ON | VIDAS CANNON ON ❀" verde ou o OFF vermelho) — aparece
 * pra quem RECEBEU o efeito do admin (mesma idéia do medo: mira player).
 *
 * @param title   linha de cima (grande, texturizada estática)
 * @param sub     linha de baixo (menor)
 * @param seconds duração em segundos (servidor clampa 1..30)
 * @param on      true = verde (concedeu) / false = vermelho (tirou)
 */
public record CanonBannerPayload(String title, String sub, int seconds, boolean on)
        implements CustomPacketPayload {

    public static final Type<CanonBannerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "canon_banner"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, CanonBannerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.title());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.sub());
                        ByteBufCodecs.VAR_INT.encode(buf, p.seconds());
                        ByteBufCodecs.BOOL.encode(buf, p.on());
                    },
                    buf -> new CanonBannerPayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
