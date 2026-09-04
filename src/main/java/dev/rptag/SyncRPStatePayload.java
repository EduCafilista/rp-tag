package dev.rptag;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Pacote S2C: avisa o cliente sobre o estado (RP) de um jogador.
 *
 * <p>Enviado a todos os jogadores quando alguem liga/desliga o RP
 * e para o jogador que acabou de entrar (com o estado de todos os online).
 */
public record SyncRPStatePayload(UUID playerId, boolean inRp) implements CustomPacketPayload {

    public static final Type<SyncRPStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "sync_rp_state"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SyncRPStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, SyncRPStatePayload::playerId,
                    ByteBufCodecs.BOOL, SyncRPStatePayload::inRp,
                    SyncRPStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
