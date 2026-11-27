package dev.rptag.vampire.client;

import dev.rptag.client.FxQualidade;
import dev.rptag.vampire.VampireFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** Mordida: filete de gotas de sangue sai da vítima e voa até a boca do vampiro (~0,6 s). */
public final class VampireFxClient {
    private static final class Sip { int victim, vamp, age; Sip(int v, int p) { victim = v; vamp = p; } }
    private static final List<Sip> LIST = new ArrayList<>();
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.65f, 0.02f, 0.06f), 1.1f);
    private static final int LIFE = 12;

    public static void handle(VampireFxPayload p) {
        if (FxQualidade.desligado()) return;
        LIST.add(new Sip(p.victim(), p.vampire()));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null || FxQualidade.desligado()) { LIST.clear(); return; }
        int gotas = Math.max(1, FxQualidade.particulas(3));
        for (int i = LIST.size() - 1; i >= 0; i--) {
            Sip s = LIST.get(i);
            Entity v = lv.getEntity(s.victim), b = lv.getEntity(s.vamp);
            if (v == null || b == null || ++s.age > LIFE) { LIST.remove(i); continue; }
            if (FxQualidade.longe(v)) continue;
            Vec3 from = v.position().add(0, v.getBbHeight() * 0.6, 0);
            Vec3 to = b.getEyePosition().add(0, -0.15, 0);
            for (int k = 0; k < gotas; k++) {
                float t = Math.min(1f, (s.age + k / (float) gotas) / LIFE);
                float arc = (float) Math.sin(t * Math.PI) * 0.4f;                    // sobe um pouco no meio do caminho
                double x = from.x + (to.x - from.x) * t + (lv.random.nextDouble() - 0.5) * 0.12;
                double y = from.y + (to.y - from.y) * t + arc + (lv.random.nextDouble() - 0.5) * 0.12;
                double z = from.z + (to.z - from.z) * t + (lv.random.nextDouble() - 0.5) * 0.12;
                lv.addParticle(BLOOD, x, y, z, 0, 0, 0);
            }
        }
    }

    private VampireFxClient() {}
}
