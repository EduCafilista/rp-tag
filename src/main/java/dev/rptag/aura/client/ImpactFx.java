package dev.rptag.aura.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
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

/** Impacto de queda gravitacional: anel de choque que segue o relevo, fissuras no chão, clarão, poeira e tremor. */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class ImpactFx {
    private static final double TAU = Math.PI * 2.0;
    private static final int LIFE = 50;
    private static final float[] C = {0.80f, 0.92f, 1.00f};

    private static final class W {
        final double x, y, z; final float R; int age;
        W(double x, double y, double z, float R) { this.x = x; this.y = y; this.z = z; this.R = R; }
    }

    private static final List<W> WAVES = new ArrayList<>();

    public static void start(double x, double y, double z, float radius) { WAVES.add(new W(x, y, z, radius)); }

    private static float ease(float t) { float u = Math.min(1f, t / 18f); return 1f - (1f - u) * (1f - u); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) { WAVES.clear(); return; }
        for (Iterator<W> it = WAVES.iterator(); it.hasNext(); ) {
            W w = it.next();
            if (++w.age > LIFE) { it.remove(); continue; }
            if (w.age > 20) continue;
            int nPart = dev.rptag.client.FxQualidade.particulas(6);
            if (nPart <= 0) continue;
            double r = w.R * ease(w.age);
            var rnd = lv.random;
            for (int i = 0; i < nPart; i++) {
                double a = rnd.nextDouble() * TAU, x = w.x + Math.cos(a) * r, z = w.z + Math.sin(a) * r;
                double y = groundY(lv, w, x, z) + 0.2;
                lv.addParticle(ParticleTypes.CLOUD, x, y, z, Math.cos(a) * 0.12, 0.10, Math.sin(a) * 0.12);
                if ((i & 1) == 0) lv.addParticle(new DustParticleOptions(new Vector3f(0.6f, 0.8f, 1f), 1.2f), x, y, z,
                        Math.cos(a) * 0.05, 0.15, Math.sin(a) * 0.05);
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || WAVES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Matrix4f m = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bs.getBuffer(RenderType.lightning());
        for (W w : WAVES) {
            if (dev.rptag.client.FxQualidade.longePoder(cam, new Vec3(w.x, w.y, w.z), 72.0)) continue;
            float t = w.age + pt, fade = Mth.clamp(1f - t / 45f, 0f, 1f);
            float en = ease(t);
            ring(vc, m, cam, lv, w, w.R * en, 0.5f + w.R * 0.04f, fade, 0.8f + w.R * 0.12f);
            if (!dev.rptag.client.FxQualidade.minimoOuMenos()) {
                ring(vc, m, cam, lv, w, w.R * ease(Math.max(0f, t - 5f)) * 0.7f, 0.3f, fade * 0.6f, 0.5f);   // eco
                cracks(vc, m, cam, lv, w, en, fade);
            }
            if (t < 8f) flash(vc, m, cam, w, t);
        }
        bs.endBatch(RenderType.lightning());
    }

    private static double groundY(ClientLevel lv, W w, double x, double z) {
        int h = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        return h <= lv.getMinBuildHeight() ? w.y : h;
    }

    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 cam, ClientLevel lv, W w, float r, float half, float fade, float wallH) {
        if (r < 0.5f || fade <= 0.01f) return;
        int n = dev.rptag.client.FxQualidade.segmentos((int) Mth.clamp(r * 4f, 40f, 200f));
        double[] cs = new double[n + 1], sn = new double[n + 1], ys = new double[n + 1];
        long chave = (((long) Mth.floor(w.x)) << 36) ^ ((((long) Mth.floor(w.z)) & 0xFFFFFL) << 16) ^ ((long) (r * 4) & 0xFFFFL);
        float[] cacheY = dev.rptag.client.FxAlturas.anelPos(lv, chave, w.x, w.y, w.z, r);
        int totalN = dev.rptag.client.FxAlturas.N;
        for (int i = 0; i <= n; i++) {
            double a = i * TAU / n;
            cs[i] = Math.cos(a); sn[i] = Math.sin(a);
            int idx = Math.min(totalN, (int) ((double) i / n * totalN + 0.5));
            ys[i] = w.y + cacheY[idx] + 0.08;
        }
        double ri = r - half, ro = r + half;
        float A = 0.8f * fade;
        for (int i = 0; i < n; i++) {
            int j = i + 1;
            q(vc, m, cam, w.x + cs[i] * ri, ys[i], w.z + sn[i] * ri, 0f, w.x + cs[i] * r, ys[i], w.z + sn[i] * r, A,
                    w.x + cs[j] * r, ys[j], w.z + sn[j] * r, A, w.x + cs[j] * ri, ys[j], w.z + sn[j] * ri, 0f);
            q(vc, m, cam, w.x + cs[i] * r, ys[i], w.z + sn[i] * r, A, w.x + cs[i] * ro, ys[i], w.z + sn[i] * ro, 0f,
                    w.x + cs[j] * ro, ys[j], w.z + sn[j] * ro, 0f, w.x + cs[j] * r, ys[j], w.z + sn[j] * r, A);
            q(vc, m, cam, w.x + cs[i] * r, ys[i], w.z + sn[i] * r, 0.5f * fade, w.x + cs[j] * r, ys[j], w.z + sn[j] * r, 0.5f * fade,
                    w.x + cs[j] * r, ys[j] + wallH, w.z + sn[j] * r, 0f, w.x + cs[i] * r, ys[i] + wallH, w.z + sn[i] * r, 0f);
        }
    }

    /** Fissuras irregulares que se abrem no chão a partir do ponto de impacto. */
    private static void cracks(VertexConsumer vc, Matrix4f m, Vec3 cam, ClientLevel lv, W w, float en, float fade) {
        final int M = dev.rptag.client.FxQualidade.baixoOuMenos() ? 5 : 10;
        int ramos = dev.rptag.client.FxQualidade.escala(14, 5);
        double yBase = groundY(lv, w, w.x, w.z) + 0.1;
        for (int j = 0; j < ramos; j++) {
            double a = j * TAU / ramos + 0.3 * Math.sin(j * 12.9);
            double len = w.R * 0.95 * en, px = -Math.sin(a), pz = Math.cos(a);
            double lx = w.x, ly = yBase, lz = w.z;
            for (int i = 1; i <= M; i++) {
                double d = len * i / M;
                double jit = (frac(Math.sin(j * 7.1 + i * 3.3) * 437.58) - 0.5) * 0.9;
                double x = w.x + Math.cos(a) * d + px * jit, z = w.z + Math.sin(a) * d + pz * jit;
                double y = dev.rptag.client.FxQualidade.baixoOuMenos() ? yBase : (groundY(lv, w, x, z) + 0.1);
                double wd = 0.14 * (1.0 - i / (double) M), al = 0.85 * fade * (1.0 - 0.6 * i / M);
                q(vc, m, cam, lx - px * wd, ly, lz - pz * wd, (float) al, lx + px * wd, ly, lz + pz * wd, (float) al,
                        x + px * wd, y, z + pz * wd, (float) al, x - px * wd, y, z - pz * wd, (float) al);
                lx = x; ly = y; lz = z;
            }
        }
    }

    private static void flash(VertexConsumer vc, Matrix4f m, Vec3 cam, W w, float t) {
        float k = 1f - t / 8f;
        double rad = 2.0 + w.R * 0.2, y = w.y + 0.15;
        for (int i = 0; i < 16; i++) {
            double a0 = i * TAU / 16, a1 = (i + 1) * TAU / 16;
            q(vc, m, cam, w.x, y, w.z, 0.8f * k, w.x, y, w.z, 0.8f * k,
                    w.x + Math.cos(a1) * rad, y, w.z + Math.sin(a1) * rad, 0f, w.x + Math.cos(a0) * rad, y, w.z + Math.sin(a0) * rad, 0f);
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (WAVES.isEmpty()) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = (float) e.getPartialTick(), amp = 0f;
        for (W w : WAVES) {
            float t = w.age + pt;
            double d = Math.hypot(cam.x - w.x, cam.z - w.z);
            float env = (float) Math.exp(-t / 14.0) * Math.min(1f, t / 2f) * Math.min(1f, w.R / 8f);
            amp = Math.max(amp, env * (float) Mth.clamp(1.0 - d / (w.R * 3.0 + 24.0), 0.0, 1.0));
        }
        if (amp < 0.002f) return;
        float tm = Minecraft.getInstance().level == null ? 0f : Minecraft.getInstance().level.getGameTime() + pt;
        e.setYaw(e.getYaw() + amp * 2.0f * Mth.sin(tm * 2.3f));
        e.setPitch(e.getPitch() + amp * 2.0f * Mth.sin(tm * 3.1f + 1f));
        e.setRoll(e.getRoll() + amp * 2.5f * Mth.sin(tm * 1.7f + 2f));
    }

    private static double frac(double x) { return x - Math.floor(x); }

    private static void vtx(VertexConsumer vc, Matrix4f m, Vec3 cam, double x, double y, double z, float a) {
        vc.addVertex(m, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(C[0], C[1], C[2], Mth.clamp(a, 0f, 1f));
    }

    private static void q(VertexConsumer vc, Matrix4f m, Vec3 cam,
                          double x0, double y0, double z0, float a0, double x1, double y1, double z1, float a1,
                          double x2, double y2, double z2, float a2, double x3, double y3, double z3, float a3) {
        vtx(vc, m, cam, x0, y0, z0, a0); vtx(vc, m, cam, x1, y1, z1, a1); vtx(vc, m, cam, x2, y2, z2, a2); vtx(vc, m, cam, x3, y3, z3, a3);
        vtx(vc, m, cam, x3, y3, z3, a3); vtx(vc, m, cam, x2, y2, z2, a2); vtx(vc, m, cam, x1, y1, z1, a1); vtx(vc, m, cam, x0, y0, z0, a0);
    }

    private ImpactFx() {}
}
