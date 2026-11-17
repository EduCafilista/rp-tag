package dev.rptag.aura.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.aura.PrismaCelestial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * (5.70.0) O VISUAL DO PRISMA CELESTIAL (zip do pedido 127):
 *  1) círculo mágico arco-íris (anéis + heptagrama de 7 cores) se formando na frente do jogador;
 *  2) raio branco incandescente envolto por 7 hélices coloridas, brilho que corre pelo espectro e anéis viajando;
 *  3) clarão com estrela de espinhos arco-íris e onda no ponto de impacto, com fagulhas.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class PrismaFx {
    private static final double TAU = Math.PI * 2.0;
    private static final int CHARGE = PrismaCelestial.CHARGE_TICKS, FIRE = PrismaCelestial.FIRE_TICKS, FADE = 10;

    private static final class Cast {
        final int id; int age;
        Cast(int id) { this.id = id; }
    }

    private static final List<Cast> CASTS = new ArrayList<>();

    public static void comeca(int quemLanca) { CASTS.add(new Cast(quemLanca)); }

    // ---------------------------------------------------------------- geometria
    private record V(double x, double y, double z, float[] c, float a) {}

    private record Geo(Vec3 o, Vec3 d, Vec3 u, Vec3 v, Vec3 end, double len, float sc) {}

    private static Geo geo(ClientLevel lv, Entity e, float pt) {
        float sc = e instanceof LivingEntity le ? le.getScale() : 1f;
        Vec3 d = e.getViewVector(pt);
        Vec3 o = e.getEyePosition(pt).add(d.scale(0.7 * sc)).add(0, -0.25 * sc, 0);
        Vec3 up = Math.abs(d.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = d.cross(up).normalize();
        Vec3 v = d.cross(u).normalize();
        Vec3 far = o.add(d.scale(PrismaCelestial.RANGE));
        HitResult hit = lv.clip(new ClipContext(o, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        return new Geo(o, d, u, v, end, o.distanceTo(end), sc);
    }

    // ------------------------------------------------------------------- tick
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) { CASTS.clear(); return; }
        for (Iterator<Cast> it = CASTS.iterator(); it.hasNext(); ) {
            Cast c = it.next();
            if (++c.age > CHARGE + FIRE + FADE) { it.remove(); continue; }
            Entity en = lv.getEntity(c.id);
            if (en == null || c.age < CHARGE || c.age > CHARGE + FIRE) continue;
            Geo g = geo(lv, en, 1f);
            var rnd = lv.random;
            for (int i = 0; i < 3; i++) {      // fagulhas arco-íris no impacto
                float[] col = AuraShellLayer.hsv(rnd.nextFloat(), 0.85f);
                lv.addParticle(new DustParticleOptions(new Vector3f(col[0], col[1], col[2]), 1.3f),
                        g.end.x, g.end.y, g.end.z, rnd.nextGaussian() * 0.25, rnd.nextGaussian() * 0.25 + 0.1, rnd.nextGaussian() * 0.25);
            }
            lv.addParticle(ParticleTypes.END_ROD, g.end.x, g.end.y, g.end.z,
                    rnd.nextGaussian() * 0.2, rnd.nextGaussian() * 0.2, rnd.nextGaussian() * 0.2);
            Vec3 along = g.o.add(g.d.scale(rnd.nextDouble() * g.len));   // faíscas ao longo do raio
            float[] col = AuraShellLayer.hsv(rnd.nextFloat(), 0.8f);
            lv.addParticle(new DustParticleOptions(new Vector3f(col[0], col[1], col[2]), 1.0f),
                    along.x, along.y, along.z, rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05);
        }
    }

    // ----------------------------------------------------------------- render
    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || CASTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Matrix4f m = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bs.getBuffer(RenderType.lightning());

        for (Cast c : CASTS) {
            Entity en = lv.getEntity(c.id);
            if (!(en instanceof LivingEntity)) continue;
            Geo g = geo(lv, en, pt);
            float t = c.age + pt;
            if (t < CHARGE + 4) circle(vc, m, cam, g, t);
            if (t >= CHARGE) beam(vc, m, cam, g, t, t - CHARGE);
        }
        bs.endBatch(RenderType.lightning());
    }

    // ------------------------------------------------- círculo mágico (concentração)
    private static void circle(VertexConsumer vc, Matrix4f m, Vec3 cam, Geo g, float t) {
        float k = Math.min(1f, t / CHARGE);
        float ease = 1f - (1f - k) * (1f - k);
        float alpha = t < CHARGE ? Math.min(1f, t / 6f) : Math.max(0f, 1f - (t - CHARGE) / 4f);
        if (alpha <= 0.01f) return;
        double R = (0.25 + 0.85 * ease) * g.sc;
        Vec3 C = g.o.add(g.d.scale(1.0 * g.sc));

        // brilho central
        float[] glow = AuraShellLayer.hsv(hue01(t * 0.02f), 0.35f);
        for (int i = 0; i < 24; i++) {
            double a0 = i * TAU / 24, a1 = (i + 1) * TAU / 24;
            q2(vc, m, cam, v(C, glow, 0.55f * alpha), v(C, glow, 0.55f * alpha),
                    v(pl(C, g, Math.cos(a1) * R * 1.1, Math.sin(a1) * R * 1.1), glow, 0f),
                    v(pl(C, g, Math.cos(a0) * R * 1.1, Math.sin(a0) * R * 1.1), glow, 0f));
        }
        // anéis arco-íris
        planeRing(vc, m, cam, C, g, R * 0.93, R * 1.0, 64, t * 0.02f, 0.95f * alpha);
        planeRing(vc, m, cam, C, g, R * 0.55, R * 0.58, 48, -t * 0.03f, 0.7f * alpha);
        // anel tracejado girando ao contrário (cada traço numa cor)
        for (int j = 0; j < 14; j++) {
            double a0 = -t * 0.05 + j * TAU / 14;
            float[] c = AuraShellLayer.hsv(hue01(j / 14f + t * 0.01f), 0.85f);
            arc(vc, m, cam, C, g, R * 1.12, R * 1.20, a0, a0 + TAU / 14 * 0.6, 4, c, 0.85f * alpha);
        }
        // heptagrama {7/3}: uma linha para cada cor do arco-íris
        double hr = t * 0.03;
        for (int i = 0; i < 7; i++) {
            int j = (i + 3) % 7;
            double a0 = hr + i * TAU / 7, a1 = hr + j * TAU / 7;
            line(vc, m, cam, C, g, Math.cos(a0) * R * 0.9, Math.sin(a0) * R * 0.9,
                    Math.cos(a1) * R * 0.9, Math.sin(a1) * R * 0.9, 0.03 * g.sc, AuraShellLayer.hsv(i / 7f, 0.9f), 0.95f * alpha);
        }
    }

    // -------------------------------------------------------------------- raio
    private static void beam(VertexConsumer vc, Matrix4f m, Vec3 cam, Geo g, float t, float bt) {
        float grow = Math.min(1f, bt / 4f);
        float fade = bt > FIRE ? Math.max(0f, 1f - (bt - FIRE) / FADE) : 1f;
        float W = grow * fade * g.sc;
        if (W <= 0.01f) return;
        double L = g.len;
        float[] white = {1f, 1f, 1f};

        // núcleo branco incandescente (duas fitas cruzadas)
        double cw = 0.11 * W * (1 + 0.15 * Math.sin(t * 1.3));
        Vec3 a1 = g.o.add(g.d.scale(L));
        for (int k = 0; k < 2; k++) {
            Vec3 w = (k == 0 ? g.u : g.v).scale(cw);
            q2(vc, m, cam, v(g.o.subtract(w), white, 1f), v(g.o.add(w), white, 1f), v(a1.add(w), white, 1f), v(a1.subtract(w), white, 1f));
        }
        // brilho largo cuja cor corre pelo espectro ao longo do raio
        int K = Math.max(4, (int) (L / 2));
        for (int i = 0; i < K; i++) {
            double s0 = L * i / K, s1 = L * (i + 1) / K;
            float[] c = AuraShellLayer.hsv(hue01(i * 0.07f - t * 0.04f), 0.7f);
            for (int k = 0; k < 2; k++) {
                Vec3 w = (k == 0 ? g.u : g.v).scale(0.6 * W);
                Vec3 p0 = at(g, s0), p1 = at(g, s1);
                q2(vc, m, cam, v(p0.subtract(w), c, 0.30f * fade), v(p0.add(w), c, 0.30f * fade),
                        v(p1.add(w), c, 0.30f * fade), v(p1.subtract(w), c, 0.30f * fade));
            }
        }
        // 7 hélices, uma para cada cor do arco-íris, girando ao redor do raio
        final double STEP = 0.6;
        int n = (int) Math.min(100, L / STEP);
        for (int j = 0; j < 7; j++) {
            float[] c = AuraShellLayer.hsv(j / 7f, 0.9f);
            Vec3 pP = null, pR = null, pT = null;
            float pa = 0f;
            for (int i = 0; i <= n; i++) {
                double s = i * STEP;
                double ang = j * TAU / 7 + s * 1.1 - t * 0.45;
                double rad = 0.42 * W * (1 + 0.1 * Math.sin(s * 0.7 + t * 0.3));
                Vec3 rv = g.u.scale(Math.cos(ang)).add(g.v.scale(Math.sin(ang)));
                Vec3 tv = g.u.scale(-Math.sin(ang)).add(g.v.scale(Math.cos(ang)));
                Vec3 P = at(g, s).add(rv.scale(rad));
                float al = (float) (0.85 * (1 - 0.35 * s / L) * Math.min(1.0, s / 1.0));
                if (pP != null) {
                    double w = 0.09 * W;
                    q2(vc, m, cam, v(pP.subtract(pR.scale(w)), c, pa), v(pP.add(pR.scale(w)), c, pa),
                            v(P.add(rv.scale(w)), c, al), v(P.subtract(rv.scale(w)), c, al));
                    q2(vc, m, cam, v(pP.subtract(pT.scale(w)), c, pa), v(pP.add(pT.scale(w)), c, pa),
                            v(P.add(tv.scale(w)), c, al), v(P.subtract(tv.scale(w)), c, al));
                }
                pP = P; pR = rv; pT = tv; pa = al;
            }
        }
        // anéis viajando ao longo do raio
        int R = (int) (L / 3);
        for (int k = 0; k < R; k++) {
            double s = (k * 3.0 + (bt * 1.6)) % L;
            float e = (float) Math.min(1.0, Math.min(s / 2.0, (L - s) / 2.0));
            if (e <= 0f) continue;
            float[] c = AuraShellLayer.hsv(hue01((float) (s * 0.05 - t * 0.04)), 0.85f);
            double rad = 0.78 * W * (0.9 + 0.1 * Math.sin(t * 0.5 + k));
            band(vc, m, cam, g, s, rad, 0.05 * W, c, 0.75f * e * fade);
        }
        // boca do raio e ponto de impacto
        flare(vc, m, cam, g.o, g, 1.0 * W, AuraShellLayer.hsv(hue01(t * 0.03f), 0.4f), 0.7f);
        Vec3 imp = g.end.subtract(g.d.scale(0.1));
        flare(vc, m, cam, imp, g, 1.6 * W, AuraShellLayer.hsv(hue01(t * 0.03f + 0.5f), 0.35f), 0.7f);
        for (int i = 0; i < 14; i++) {   // estrela de espinhos arco-íris
            double ang = i * TAU / 14 + t * 0.05;
            double len = (1.0 + 1.4 * frac(Math.sin(i * 7.7) * 43758.5)) * W * (0.7 + 0.3 * Math.sin(t * 0.5 + i));
            Vec3 dv = g.u.scale(Math.cos(ang)).add(g.v.scale(Math.sin(ang)));
            Vec3 pv = g.u.scale(-Math.sin(ang)).add(g.v.scale(Math.cos(ang))).scale(0.12 * W);
            float[] c = AuraShellLayer.hsv(hue01(i / 14f + t * 0.02f), 0.85f);
            Vec3 tip = imp.add(dv.scale(len));
            q2(vc, m, cam, v(imp.subtract(pv), c, 0.8f * fade), v(imp.add(pv), c, 0.8f * fade), v(tip, c, 0f), v(tip, c, 0f));
        }
        float f = (bt * 0.1f) % 1f;   // onda que expande no impacto
        planeRing(vc, m, cam, imp, g, f * 2.8 * W, f * 2.8 * W + 0.12 * W, 48, t * 0.02f, (1f - f) * 0.7f * fade);
    }

    // ----------------------------------------------------------------- helpers
    private static Vec3 at(Geo g, double s) { return g.o.add(g.d.scale(s)); }

    private static Vec3 pl(Vec3 c, Geo g, double a, double b) { return c.add(g.u.scale(a)).add(g.v.scale(b)); }

    private static float hue01(float x) { x = x % 1f; return x < 0 ? x + 1f : x; }

    private static double frac(double x) { return x - Math.floor(x); }

    private static V v(Vec3 p, float[] c, float a) { return new V(p.x, p.y, p.z, c, a); }

    private static void ev(VertexConsumer vc, Matrix4f m, Vec3 cam, V p) {
        vc.addVertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                .setColor(p.c[0], p.c[1], p.c[2], Mth.clamp(p.a, 0f, 1f));
    }

    private static void q2(VertexConsumer vc, Matrix4f m, Vec3 cam, V a, V b, V c, V d) {
        ev(vc, m, cam, a); ev(vc, m, cam, b); ev(vc, m, cam, c); ev(vc, m, cam, d);
        ev(vc, m, cam, d); ev(vc, m, cam, c); ev(vc, m, cam, b); ev(vc, m, cam, a);
    }

    /** Anel no plano (u,v) com a cor percorrendo o arco-íris pelo ângulo. */
    private static void planeRing(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 C, Geo g,
                                  double r0, double r1, int seg, float hueOff, float al) {
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            float[] c0 = AuraShellLayer.hsv(hue01((float) (a0 / TAU) + hueOff), 0.85f);
            float[] c1 = AuraShellLayer.hsv(hue01((float) (a1 / TAU) + hueOff), 0.85f);
            q2(vc, m, cam, v(pl(C, g, Math.cos(a0) * r0, Math.sin(a0) * r0), c0, al), v(pl(C, g, Math.cos(a0) * r1, Math.sin(a0) * r1), c0, al),
                    v(pl(C, g, Math.cos(a1) * r1, Math.sin(a1) * r1), c1, al), v(pl(C, g, Math.cos(a1) * r0, Math.sin(a1) * r0), c1, al));
        }
    }

    private static void arc(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 C, Geo g, double r0, double r1,
                            double a0, double a1, int seg, float[] c, float al) {
        for (int i = 0; i < seg; i++) {
            double u0 = a0 + (a1 - a0) * i / seg, u1 = a0 + (a1 - a0) * (i + 1) / seg;
            q2(vc, m, cam, v(pl(C, g, Math.cos(u0) * r0, Math.sin(u0) * r0), c, al), v(pl(C, g, Math.cos(u0) * r1, Math.sin(u0) * r1), c, al),
                    v(pl(C, g, Math.cos(u1) * r1, Math.sin(u1) * r1), c, al), v(pl(C, g, Math.cos(u1) * r0, Math.sin(u1) * r0), c, al));
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 C, Geo g,
                             double x0, double y0, double x1, double y1, double w, float[] c, float al) {
        double dx = x1 - x0, dy = y1 - y0, l = Math.sqrt(dx * dx + dy * dy);
        if (l < 1e-6) return;
        double nx = -dy / l * w / 2, ny = dx / l * w / 2;
        q2(vc, m, cam, v(pl(C, g, x0 + nx, y0 + ny), c, al), v(pl(C, g, x0 - nx, y0 - ny), c, al),
                v(pl(C, g, x1 - nx, y1 - ny), c, al), v(pl(C, g, x1 + nx, y1 + ny), c, al));
    }

    /** Anel de faixa vertical ao redor do raio na posição s. */
    private static void band(VertexConsumer vc, Matrix4f m, Vec3 cam, Geo g, double s, double rad, double half,
                             float[] c, float al) {
        Vec3 h = g.d.scale(half), base = at(g, s);
        for (int i = 0; i < 28; i++) {
            double a0 = i * TAU / 28, a1 = (i + 1) * TAU / 28;
            Vec3 p0 = base.add(pl(Vec3.ZERO, g, Math.cos(a0) * rad, Math.sin(a0) * rad));
            Vec3 p1 = base.add(pl(Vec3.ZERO, g, Math.cos(a1) * rad, Math.sin(a1) * rad));
            q2(vc, m, cam, v(p0.subtract(h), c, al), v(p0.add(h), c, al), v(p1.add(h), c, al), v(p1.subtract(h), c, al));
        }
    }

    /** Disco de luz: forte no centro, transparente na borda. */
    private static void flare(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 C, Geo g, double r, float[] c, float al) {
        for (int i = 0; i < 16; i++) {
            double a0 = i * TAU / 16, a1 = (i + 1) * TAU / 16;
            q2(vc, m, cam, v(C, c, al), v(C, c, al),
                    v(pl(C, g, Math.cos(a1) * r, Math.sin(a1) * r), c, 0f), v(pl(C, g, Math.cos(a0) * r, Math.sin(a0) * r), c, 0f));
        }
    }

    private PrismaFx() {}
}
