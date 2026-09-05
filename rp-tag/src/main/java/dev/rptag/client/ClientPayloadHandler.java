package dev.rptag.client;

import dev.rptag.SyncRPStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handler (cliente) do pacote de sincronizacao do estado RP.
 */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    public static void handleSync(final SyncRPStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientRPStates.set(payload.playerId(), payload.inRp());

            // Atualiza a nametag acima da cabeca imediatamente.
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                Player player = minecraft.level.getPlayerByUUID(payload.playerId());
                if (player != null) {
                    player.refreshDisplayName();
                }
            }
        });
    }
}
