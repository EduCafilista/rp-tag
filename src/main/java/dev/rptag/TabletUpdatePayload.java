package dev.rptag;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TabletUpdatePayload(long revisao, int requestId, FichaPersonagem respostas, int etapa, boolean confirmar)
        implements CustomPacketPayload {
    public static final Type<TabletUpdatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "tablet_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletUpdatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarLong(p.revisao).writeVarInt(p.requestId); p.respostas.write(buf); buf.writeVarInt(p.etapa).writeBoolean(p.confirmar); },
            buf -> new TabletUpdatePayload(buf.readVarLong(), buf.readVarInt(), FichaPersonagem.read(buf), buf.readVarInt(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
