package dev.rptag;

import java.util.UUID;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Pacote S2C: avisa o cliente sobre a persona (personagem) de um jogador.
 * Nome vazio = sem persona (mostra o nick normal).
 */
public record PersonaSyncPayload(UUID playerId, String name, int age, String desc)
        implements CustomPacketPayload {

    public static final Type<PersonaSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "sync_persona"));

    /** Codec manual (mapeia campo a campo, sem ambiguidade de overloads). */
    public static final StreamCodec<io.netty.buffer.ByteBuf, PersonaSyncPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        net.minecraft.core.UUIDUtil.STREAM_CODEC.encode(buf, p.playerId());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.name());
                        ByteBufCodecs.VAR_INT.encode(buf, p.age());
                        ByteBufCodecs.STRING_UTF8.encode(buf, p.desc());
                    },
                    buf -> new PersonaSyncPayload(
                            net.minecraft.core.UUIDUtil.STREAM_CODEC.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf)));

    public static PersonaSyncPayload of(UUID id, Persona persona) {
        return new PersonaSyncPayload(id, persona.name(), persona.age(), persona.desc());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
