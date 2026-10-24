package dev.rptag;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * (4.8.0) SERVIDOR -> CLIENTE: abre o TABLET com a ficha de um jogador.
 *
 * @param dono     de quem é a ficha (no single player é o próprio)
 * @param nomeDono nick da conta (para o cabeçalho)
 * @param ficha    o conteúdo
 * @param editar   true = o dono pode escrever; false = só leitura (ver a de outro)
 */
public record FichaOpenPayload(UUID dono, String nomeDono, FichaRp ficha, boolean editar)
        implements CustomPacketPayload {

    public static final Type<FichaOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "ficha_abrir"));

    public static final StreamCodec<FriendlyByteBuf, FichaOpenPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.encode(buf), FichaOpenPayload::new);

    public FichaOpenPayload(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUtf(64), FichaCodec.ler(buf), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(dono);
        buf.writeUtf(nomeDono, 64);
        FichaCodec.escrever(buf, ficha);
        buf.writeBoolean(editar);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void enviar(ServerPlayer sp, FichaRp f, String nomeDono, boolean editar) {
        PacketDistributor.sendToPlayer(sp, new FichaOpenPayload(sp.getUUID(), nomeDono, f.copia(), editar));
    }
}
