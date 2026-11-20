package dev.rptag.yumaho;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class YuNetwork {
    /** Chamado de dentro de AuraNetwork.register com o MESMO registrar. */
    public static void register(PayloadRegistrar r) {
        r.playToServer(YuActionPayload.TYPE, YuActionPayload.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) YuAbilities.handle(sp, p.action(), p.arg());
                }));
        r.playToClient(YuFxPayload.TYPE, YuFxPayload.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> dev.rptag.yumaho.client.YuFxClient.handle(p)));
    }

    private YuNetwork() {}
}
