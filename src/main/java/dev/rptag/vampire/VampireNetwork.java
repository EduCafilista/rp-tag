package dev.rptag.vampire;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class VampireNetwork {
    /** Chamado de dentro de ModNetworking.onRegisterPayloads com o mesmo registrar. */
    public static void register(PayloadRegistrar r) {
        r.playToServer(VampireActionPayload.TYPE, VampireActionPayload.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) VampireLogic.action(sp, p.action());
                }));
        r.playToClient(VampireFxPayload.TYPE, VampireFxPayload.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> dev.rptag.vampire.client.VampireFxClient.handle(p)));
    }

    private VampireNetwork() {}
}
