package dev.rptag.yumaho.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.yumaho.YuFxPayload;
import dev.rptag.yumaho.YuMaho;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Raios amarelos procedurais (golpes, Fa Jin, Blackwhip, Gearshift) + partículas + tremor de câmera. */
@EventBusSubscriber(modid = YuMaho.MODID, value = Dist.CLIENT)
public final class YuFxClient {
    private static final Random RND = new Random();
    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static float shake;

    private static final class Bolt {
        final Vec3 a, b; final long seed; final int life; final float jag, w; int age;
        Bolt(Vec3 a, Vec3 b, int life, float jag, float w) {
            this.a = a; this.b = b; this.life = life; this.jag = jag; this.w = w; this.seed = RND.nextLong();
        }
    }

    private static void bolt(Vec3 a, Vec3 b, int life, float jag, float w) { BOLTS.add(new Bolt(a, b, life, jag, w)); }

    private static Vec3 randDir() {
        double z = RND.nextDouble() * 2 - 1, t = RND.nextDouble() * Math.PI * 2, r = Math.sqrt(1 - z * z);
        return new Vec3(r * Math.cos(t), z, r * Math.sin(t));
    }

    // ------------------------------------------------------------------ chegada do servidor
    public static void handle(YuFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        Vec3 a = new Vec3(p.x(), p.y(), p.z()), b = new Vec3(p.x2(), p.y2(), p.z2());
        float f = p.a();
        switch (p.kind()) {
            case YuFxPayload.HIT -> hit(lv, a, f, p.b() > 0.5f);
            case YuFxPayload.RELEASE -> release(lv, a, b, f, p.b());
            case YuFxPayload.WHIP -> whip(lv, a, b, f);
            case YuFxPayload.GEAR -> gear(lv, a, f);
            default -> {}
        }
    }

    private static void addShake(Vec3 at, float amp, double reach) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double d = mc.player.position().distanceTo(at);
        shake = Math.max(shake, amp * (float) Mth.clamp(1.0 - d / reach, 0.0, 1.0));
    }

    private static void sparks(ClientLevel lv, Vec3 c, int n, double speed) {
        for (int i = 0; i < n; i++) {
            Vec3 d = randDir().scale(speed * (0.4 + RND.nextDouble()));
            lv.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, d.x, d.y, d.z);
            if ((i & 1) == 0) lv.addParticle(new DustParticleOptions(new Vector3f(1f, 0.88f, 0.2f), 1.1f), c.x, c.y, c.z, d.x * 0.4, d.y * 0.4, d.z * 0.4);
        }
    }

    private static void hit(ClientLevel lv, Vec3 c, float f, boolean crit) {
        int n = 4 + (int) (6 * f) + (crit ? 6 : 0);
        float size = (1.2f + 2.6f * f) * (crit ? 1.6f : 1f);
        for (int i = 0; i < n; i++) {
            float len = size * (0.55f + RND.nextFloat() * 0.8f);
            bolt(c, c.add(randDir().scale(len)), 5 + RND.nextInt(5), len * 0.14f, 0.08f + 0.10f * f);
        }
        if (crit) bolt(c.add(0, 12, 0), c, 9, 0.9f, 0.30f);                   // raio que cai do céu
        bolt(c, c.add(randDir().scale(size * 0.5f)), 3, 0.2f, 0.55f + 0.4f * f); // clarão central
        sparks(lv, c, 8 + (int) (28 * f) + (crit ? 20 : 0), 0.25 + 0.4 * f);
        addShake(c, crit ? 0.7f : 0.12f + 0.3f * f, 18);
    }

    private static void release(ClientLevel lv, Vec3 a, Vec3 b, float f, float en) {
        float w = 0.22f + 0.3f * en;
        for (int i = 0; i < 3; i++) bolt(a, b, 9 + i * 2, 0.45f + 0.2f * i, w * (1f - 0.25f * i));
        int n = 8 + (int) (10 * en);
        for (int i = 0; i < n; i++) bolt(b, b.add(randDir().scale(1.5 + 2.5 * en)), 6 + RND.nextInt(5), 0.3f, 0.12f);
        Vec3 d = b.subtract(a);
        for (int i = 0; i < 14; i++) {
            Vec3 p = a.add(d.scale(i / 13.0));
            lv.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0, 0.02, 0);
        }
        lv.addParticle(ParticleTypes.EXPLOSION, b.x, b.y, b.z, 0, 0, 0);
        sparks(lv, b, 20 + (int) (30 * en), 0.5);
        addShake(a, 0.4f + 0.5f * en, 24);
    }

    private static void whip(ClientLevel lv, Vec3 a, Vec3 b, float f) {
        bolt(a, b, 8, 0.25f, 0.20f);
        bolt(a, b, 6, 0.55f, 0.10f);
        bolt(a, b, 7, 0.9f, 0.07f);
        sparks(lv, b, 10, 0.3);
        addShake(b, 0.12f, 12);
    }

    private static void gear(ClientLevel lv, Vec3 c, float f) {
        for (int i = 0; i < 10; i++) {
            double t = i * Math.PI * 2 / 10, r = 0.7;
            Vec3 base = c.add(Math.cos(t) * r, 0.05, Math.sin(t) * r);
            bolt(base, base.add(Math.cos(t) * 0.5, 1.6 + RND.nextDouble() * 0.6, Math.sin(t) * 0.5), 10, 0.25f, 0.10f);
        }
        sparks(lv, c.add(0, 1, 0), 24, 0.3);
        addShake(c, 0.18f, 10);
    }

    // ------------------------------------------------------------------ tick / câmera / desenho
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        shake *= 0.82f;
        if (shake < 0.003f) shake = 0f;
        for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            if (++b.age > b.life) it.remove();
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (shake <= 0f) return;
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level == null ? 0f : mc.level.getGameTime()) + (float) e.getPartialTick();
        e.setYaw(e.getYaw() + shake * 2.2f * Mth.sin(t * 2.9f));
        e.setPitch(e.getPitch() + shake * 2.0f * Mth.sin(t * 3.7f + 1f));
        e.setRoll(e.getRoll() + shake * 1.6f * Mth.sin(t * 2.1f + 2f));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BOLTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Matrix4f m = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bs.getBuffer(RenderType.lightning());
        for (Bolt b : BOLTS) draw(vc, m, cam, b, b.age + pt);
        bs.endBatch(RenderType.lightning());
    }

    private static void draw(VertexConsumer vc, Matrix4f m, Vec3 cam, Bolt b, float t) {
        float fade = Mth.clamp(1f - t / b.life, 0f, 1f);
        if (fade <= 0.01f) return;
        Vec3 d = b.b.subtract(b.a);
        double len = d.length();
        int n = Mth.clamp((int) (len * 2.2), 4, 40);
        Random r = new Random(b.seed + (long) (t * 0.5f) * 7919L);          // muda a cada 2 ticks: efeito de tremida
        Vec3[] pts = new Vec3[n + 1];
        pts[0] = b.a; pts[n] = b.b;
        for (int i = 1; i < n; i++) {
            double env = Math.sin(Math.PI * i / n);
            pts[i] = b.a.add(d.scale((double) i / n)).add(
                    (r.nextDouble() - 0.5) * 2 * b.jag * env, (r.nextDouble() - 0.5) * 2 * b.jag * env, (r.nextDouble() - 0.5) * 2 * b.jag * env);
        }
        for (int i = 0; i < n; i++) {
            ribbon(vc, m, cam, pts[i], pts[i + 1], b.w * 2.6f, 1f, 0.80f, 0.10f, 0.30f * fade);   // brilho amarelo
            ribbon(vc, m, cam, pts[i], pts[i + 1], b.w, 1f, 0.95f, 0.55f, 0.85f * fade);          // miolo
            ribbon(vc, m, cam, pts[i], pts[i + 1], b.w * 0.35f, 1f, 1f, 1f, fade);                // núcleo branco
        }
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 p0, Vec3 p1, float w,
                               float r, float g, float bl, float a) {
        Vec3 mid = p0.add(p1).scale(0.5).subtract(cam);
        Vec3 side = p1.subtract(p0).cross(mid).normalize().scale(w * 0.5);
        if (side.lengthSqr() < 1e-8) return;
        v(vc, m, cam, p0.add(side), r, g, bl, a);
        v(vc, m, cam, p0.subtract(side), r, g, bl, a);
        v(vc, m, cam, p1.subtract(side), r, g, bl, a);
        v(vc, m, cam, p1.add(side), r, g, bl, a);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 p, float r, float g, float b, float a) {
        vc.addVertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).setColor(r, g, b, Mth.clamp(a, 0f, 1f));
    }

    private YuFxClient() {}
}
