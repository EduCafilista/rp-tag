package dev.rptag.king;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class KingNetwork {
    /** Chamado de dentro de AuraNetwork.register com o MESMO registrar. */
    public static void register(PayloadRegistrar r) {
        r.playToServer(KingActionPayload.TYPE, KingActionPayload.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> { if (ctx.player() instanceof ServerPlayer sp) KingAbilities.cast(sp, p.ability()); }));
        r.playToClient(KingFxPayload.TYPE, KingFxPayload.CODEC, (p, ctx) ->
                ctx.enqueueWork(() -> dev.rptag.king.client.KingFxClient.handle(p)));
    }

    private KingNetwork() {}
}
