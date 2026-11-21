package dev.rptag.king;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Envia animações a quem está perto. */
public final class KingFx {
    public static void send(ServerLevel lv, int type, Vec3 p, Vec3 q, float a, float b, int id) {
        send(lv, type, p, q, a, b, id, 128.0);
    }

    public static void send(ServerLevel lv, int type, Vec3 p, Vec3 q, float a, float b, int id, double radius) {
        PacketDistributor.sendToPlayersNear(lv, null, p.x, p.y, p.z, radius,
                new KingFxPayload(type, id, p.x, p.y, p.z, q.x, q.y, q.z, a, b));
    }

    private KingFx() {}
}
