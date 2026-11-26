package dev.rptag.aura.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.RPTagMod;
import dev.rptag.aura.AuraEstilo;
import dev.rptag.aura.AuraHitPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Efeitos de golpe das auras (sem partículas, tudo desenhado): Radiação = pulso ciano com trifólio,
 * Arco-íris = anel e raios coloridos suaves, Vazio = respingo de tinta com tentáculos, Iluminação/Gilgamesh = cortes dourados.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class AuraHitClient {
    private static final class Hit {
        final AuraEstilo style;
        final Vec3 c;
        final float dx, dz;
        final long seed;
        final int life;
        int age;

        Hit(AuraEstilo s, Vec3 c, float dx, float dz, int life) {
            style = s;
            this.c = c;
            this.dx = dx;
            this.dz = dz;
            this.life = life;
            seed = new Random().nextLong();
        }
    }

    private static final List<Hit> HITS = new ArrayList<>();
    private static final float[] CYAN = {0.15f, 0.85f, 1f}, PALE = {0.85f, 1f, 1f}, GOLD = {1f, 0.80f, 0.15f}, WARM = {1f, 0.95f, 0.65f};
    private static final float[] INKC = {0.015f, 0f, 0.04f}, VIOLET = {0.40f, 0.22f, 0.55f};

    public static void start(AuraHitPayload p) {
        if (dev.rptag.client.FxQualidade.desligado()) return;
        if (HITS.size() > (dev.rptag.client.FxQualidade.baixoOuMenos() ? 16 : 40)) return;
        AuraEstilo s = AuraEstilo.byIndex(p.style());
        if (s == null) return;
        int life = switch (s) {
            case RADIACAO -> 18;
            case ARCOIRIS -> 16;
            case VAZIO -> 24;
            default -> 14;
        };
        HITS.add(new Hit(s, new Vec3(p.x(), p.y(), p.z()), p.dx(), p.dz(), life));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        for (Hit h : HITS) h.age++;
        HITS.removeIf(h -> h.age > h.life);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || HITS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Matrix4f m = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer glow = bs.getBuffer(RenderType.lightning());
        for (Hit h : HITS) {
            if (dev.rptag.client.FxQualidade.longePoder(cam, h.c, 48.0)) continue;
            float t = h.age + pt, u = Mth.clamp(t / h.life, 0f, 1f);
            switch (h.style) {
                case RADIACAO -> radiation(glow, m, cam, h, t, u);
                case ARCOIRIS -> rainbow(glow, m, cam, h, t, u);
                case ILUMINACAO -> gold(glow, m, cam, h, t, u);
                default -> {}
            }
        }
        bs.endBatch(RenderType.lightning());
        VertexConsumer ink = bs.getBuffer(AuraRenderTypes.INK);
        for (Hit h : HITS) {
            if (h.style == AuraEstilo.VAZIO && !dev.rptag.client.FxQualidade.longePoder(cam, h.c, 48.0)) {
                void_(ink, m, cam, h, h.age + pt, Mth.clamp((h.age + pt) / h.life, 0f, 1f));
            }
        }
        bs.endBatch(AuraRenderTypes.INK);
        if (!dev.rptag.client.FxQualidade.minimoOuMenos()) {
            VertexConsumer rim = bs.getBuffer(RenderType.lightning());                          // contorno violeta fraco do respingo
            for (Hit h : HITS) {
                if (h.style == AuraEstilo.VAZIO && !dev.rptag.client.FxQualidade.longePoder(cam, h.c, 48.0)) {
                    voidRim(rim, m, cam, h, h.age + pt, Mth.clamp((h.age + pt) / h.life, 0f, 1f));
                }
            }
            bs.endBatch(RenderType.lightning());
        }
    }

    // ------------------------------------------------------------------ helpers
    private static float ease(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 p, float[] c, float a) {
        vc.addVertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                .setColor(c[0], c[1], c[2], Mth.clamp(a, 0f, 1f));
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float w0, float w1, float[] c, float al) {
        if (al <= 0.004f) return;
        Vec3 side = b.subtract(a).cross(a.add(b).scale(0.5).subtract(cam));
        if (side.lengthSqr() < 1e-12) return;
        side = side.normalize();
        vtx(vc, m, cam, a.add(side.scale(w0 * 0.5)), c, al);
        vtx(vc, m, cam, a.subtract(side.scale(w0 * 0.5)), c, al);
        vtx(vc, m, cam, b.subtract(side.scale(w1 * 0.5)), c, al);
        vtx(vc, m, cam, b.add(side.scale(w1 * 0.5)), c, al);
    }

    private static Vec3[] facing(Vec3 cam, Vec3 at) {
        Vec3 n = cam.subtract(at);
        n = n.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : n.normalize();
        Vec3 r = new Vec3(0, 1, 0).cross(n);
        r = r.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : r.normalize();
        return new Vec3[]{r, n.cross(r).normalize()};
    }

    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 o, Vec3[] b, double r, float w, int segs, float[] c, float al, boolean hue, float hueOff) {
        Vec3 prev = null;
        for (int k = 0; k <= segs; k++) {
            double a = Math.PI * 2 * k / segs;
            Vec3 p = o.add(b[0].scale(Math.cos(a) * r)).add(b[1].scale(Math.sin(a) * r));
            if (prev != null) ribbon(vc, m, cam, prev, p, w, w, hue ? hsv((hueOff + (float) k / segs) % 1f) : c, al);
            prev = p;
        }
    }

    private static float[] hsv(float h) {
        return new float[]{
                Mth.clamp(Math.abs(h * 6f - 3f) - 1f, 0f, 1f),
                Mth.clamp(2f - Math.abs(h * 6f - 2f), 0f, 1f),
                Mth.clamp(2f - Math.abs(h * 6f - 4f), 0f, 1f)
        };
    }

    private static Vec3 randDir(Random r) {
        double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        return new Vec3(s * Math.cos(a), z, s * Math.sin(a));
    }

    // ------------------------------------------------------------------ Radiação
    private static void radiation(VertexConsumer vc, Matrix4f m, Vec3 cam, Hit h, float t, float u) {
        float e = ease(u), fade = (1f - u) * (1f - u);
        Vec3[] fb = facing(cam, h.c), fl = {new Vec3(1, 0, 0), new Vec3(0, 0, 1)};
        ring(vc, m, cam, h.c, fb, 0.3 + 1.9 * e, 0.16f * (1 - u) + 0.03f, 28, CYAN, 0.75f * fade, false, 0);
        ring(vc, m, cam, h.c, fl, 0.2 + 2.3 * e, 0.14f * (1 - u) + 0.03f, 28, CYAN, 0.55f * fade, false, 0);
        Random r = new Random(h.seed);
        for (int i = 0; i < 10; i++) {                                                      // faíscas em linha saindo do golpe
            Vec3 d = randDir(r);
            double s0 = 0.3 + 0.4 * e, s1 = s0 + 0.5 + 0.9 * e;
            ribbon(vc, m, cam, h.c.add(d.scale(s0)), h.c.add(d.scale(s1)), 0.05f, 0.015f, i % 3 == 0 ? PALE : CYAN, 0.8f * fade);
        }
        float spin = t * 0.35f;                                                              // trifólio de radiação girando
        float tf = Mth.clamp(1f - u * 1.3f, 0f, 1f);
        for (int k = 0; k < 3; k++) {
            double a0 = spin + k * 2.0944, a1 = a0 + 1.0;
            Vec3 prev = null;
            for (int j = 0; j <= 6; j++) {
                double a = a0 + (a1 - a0) * j / 6.0;
                double rr = 0.55 + 0.1 * e;
                Vec3 p = h.c.add(fb[0].scale(Math.cos(a) * rr)).add(fb[1].scale(Math.sin(a) * rr));
                if (prev != null) ribbon(vc, m, cam, prev, p, 0.16f, 0.16f, CYAN, 0.7f * tf);
                prev = p;
            }
        }
        ring(vc, m, cam, h.c, fb, 0.14, 0.12f, 10, PALE, 0.8f * tf, false, 0);
    }

    // ------------------------------------------------------------------ Arco-íris (suave)
    private static void rainbow(VertexConsumer vc, Matrix4f m, Vec3 cam, Hit h, float t, float u) {
        float e = ease(u), fade = (1f - u) * (1f - u);
        Vec3[] fb = facing(cam, h.c);
        ring(vc, m, cam, h.c, fb, 0.3 + 1.6 * e, 0.14f * (1 - u) + 0.03f, 32, null, 0.55f * fade, true, t * 0.04f);
        ring(vc, m, cam, h.c, fb, 0.2 + 1.0 * e, 0.09f * (1 - u) + 0.02f, 24, null, 0.40f * fade, true, -t * 0.05f + 0.3f);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + h.seed % 7;
            Vec3 d = fb[0].scale(Math.cos(a)).add(fb[1].scale(Math.sin(a)));
            ribbon(vc, m, cam, h.c.add(d.scale(0.25 + 0.5 * e)), h.c.add(d.scale(0.7 + 1.5 * e)), 0.06f, 0.015f, hsv(i / 12f), 0.55f * fade);
        }
    }

    // ------------------------------------------------------------------ Vazio (tinta)
    private static void void_(VertexConsumer ink, Matrix4f m, Vec3 cam, Hit h, float t, float u) {
        float e = ease(u * 1.4f), fade = 1f - u * u;
        Random r = new Random(h.seed);
        Vec3 base = h.c;
        for (int i = 0; i < 9; i++) {                                                       // tentáculos de tinta, curvados e pingando
            Vec3 d = randDir(r).add(h.dx * 0.8, 0, h.dz * 0.8).normalize();
            double len = (1.0 + r.nextDouble() * 1.3) * e;
            Vec3 prev = base;
            for (int j = 1; j <= 7; j++) {
                float f = j / 7f;
                Vec3 p = base.add(d.scale(len * f)).add(0, -0.9 * f * f * len + Math.sin(t * 0.3 + i + j) * 0.03, 0);
                ribbon(ink, m, cam, prev, p, 0.22f * (1 - f * 0.8f), 0.22f * (1 - (j + 1) / 7f * 0.8f), INKC, 0.85f * fade);
                prev = p;
            }
        }
        Vec3[] fb = facing(cam, base);
        for (int i = 0; i < 3; i++) ring(ink, m, cam, base, fb, 0.12 + 0.35 * e * (1 + i * 0.3), 0.3f * (1 - u * 0.5f), 14, INKC, 0.85f * fade, false, 0);
    }

    private static void voidRim(VertexConsumer vc, Matrix4f m, Vec3 cam, Hit h, float t, float u) {
        float e = ease(u * 1.4f), fade = (1f - u) * (1f - u);
        Vec3[] fb = facing(cam, h.c);
        ring(vc, m, cam, h.c, fb, 0.4 + 1.5 * e, 0.07f, 30, VIOLET, 0.35f * fade, false, 0);
    }

    // ------------------------------------------------------------------ Iluminação / Gilgamesh (cortes dourados)
    private static void gold(VertexConsumer vc, Matrix4f m, Vec3 cam, Hit h, float t, float u) {
        float e = ease(u * 2f), fade = (1f - u) * (1f - u);
        Vec3[] fb = facing(cam, h.c);
        Random r = new Random(h.seed);
        for (int i = 0; i < 2; i++) {                                                       // dois cortes cruzados
            double a = r.nextDouble() * Math.PI + (i == 0 ? 0 : 1.2);
            Vec3 d = fb[0].scale(Math.cos(a)).add(fb[1].scale(Math.sin(a)));
            Vec3 p0 = h.c.subtract(d.scale(1.5 * e)), p1 = h.c.add(d.scale(1.5 * e));
            ribbon(vc, m, cam, p0, h.c, 0.02f, 0.16f * fade + 0.02f, GOLD, 0.9f * fade);
            ribbon(vc, m, cam, h.c, p1, 0.16f * fade + 0.02f, 0.02f, GOLD, 0.9f * fade);
            ribbon(vc, m, cam, p0.scale(0.6).add(h.c.scale(0.4)), p1.scale(0.6).add(h.c.scale(0.4)), 0.05f, 0.05f, WARM, fade);
        }
        ring(vc, m, cam, h.c, fb, 0.2 + 1.3 * e, 0.12f * (1 - u) + 0.02f, 26, GOLD, 0.7f * fade, false, 0);
        for (int i = 0; i < 8; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            Vec3 d = fb[0].scale(Math.cos(a)).add(fb[1].scale(Math.sin(a)));
            ribbon(vc, m, cam, h.c.add(d.scale(0.4 + 0.5 * e)), h.c.add(d.scale(0.8 + 1.0 * e)), 0.05f, 0.01f, i % 2 == 0 ? WARM : GOLD, 0.8f * fade);
        }
    }

    private AuraHitClient() {}
}
