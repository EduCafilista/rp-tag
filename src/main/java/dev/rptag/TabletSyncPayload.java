package dev.rptag;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TabletSyncPayload(UUID target, int requestId, FichaRegistro registro, boolean readOnly, boolean open, boolean retryable, String error)
        implements CustomPacketPayload {
    public static final Type<TabletSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "tablet_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TabletSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUUID(p.target).writeVarInt(p.requestId); p.registro.write(buf); buf.writeBoolean(p.readOnly).writeBoolean(p.open).writeBoolean(p.retryable).writeUtf(p.error, 256); },
            buf -> new TabletSyncPayload(buf.readUUID(), buf.readVarInt(), FichaRegistro.read(buf), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readUtf(256)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
