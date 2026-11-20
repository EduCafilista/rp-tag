package dev.rptag.yumaho;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Atalho do servidor para mandar efeitos visuais a quem está perto. */
public final class YuFx {
    public static void send(ServerLevel lv, int type, Vec3 a, Vec3 b, float fa, float fb) {
        PacketDistributor.sendToPlayersNear(lv, null, a.x, a.y, a.z, 96.0,
                new YuFxPayload(type, a.x, a.y, a.z, b.x, b.y, b.z, fa, fb));
    }

    private YuFx() {}
}
