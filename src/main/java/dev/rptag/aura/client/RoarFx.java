package dev.rptag.aura.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.aura.RoarAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Visual do grito: anéis de onda sonora que correm pelo chão (seguindo o relevo), com ecos,
 * parede de pressão, clarão + pilar no início e tremor de câmera. Tudo procedural.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class RoarFx {
    private static final double TAU = Math.PI * 2.0;
    private static final int LIFETIME = RoarAbility.TRAVEL_TICKS + 50;

    // ecos: atraso (ticks), intensidade e cor
    private static final float[] DELAY = {0f, 7f, 14f, 21f};
    private static final float[] STRENGTH = {1f, 0.70f, 0.45f, 0.30f};
    private static final float[][] COLOR = {
            {0.85f, 0.95f, 1.00f}, {0.70f, 0.90f, 1.00f}, {0.90f, 0.85f, 1.00f}, {1.00f, 0.85f, 0.60f}};

    private static final class Wave {
        final double x, y, z; int age;
        Wave(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }

    private static final List<Wave> WAVES = new ArrayList<>();

    public static void start(double x, double y, double z) { WAVES.add(new Wave(x, y, z)); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
            if (++it.next().age > LIFETIME) it.remove();
        }
    }

    // ------------------------------------------------------------------ render
    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || WAVES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;

        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack ps = e.getPoseStack();
        Matrix4f m = ps.last().pose();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bs.getBuffer(RenderType.lightning());

        for (Wave w : WAVES) {
            float t = w.age + pt;
            flash(vc, m, cam, w, t);
            for (int i = 0; i < DELAY.length; i++) ring(vc, m, lv, cam, w, t, DELAY[i], STRENGTH[i], COLOR[i]);
        }
        bs.endBatch(RenderType.lightning());
    }

    private static void ring(VertexConsumer vc, Matrix4f m, ClientLevel lv, Vec3 cam, Wave w,
                             float t, float delay, float strength, float[] col) {
        float tt = t - delay;
        if (tt <= 0f || tt > RoarAbility.TRAVEL_TICKS + 30) return;
        double r = RoarAbility.radiusAt(tt);
        if (r < 0.8) return;
        float u = Math.min(1f, tt / RoarAbility.TRAVEL_TICKS);
        float fade = (1f - u * u * u) * strength;
        if (tt > RoarAbility.TRAVEL_TICKS) fade *= Math.max(0f, 1f - (tt - RoarAbility.TRAVEL_TICKS) / 30f);
        if (fade <= 0.01f) return;

        int n = (int) Mth.clamp(r * 4.0, 48.0, 240.0);
        float half = 0.9f + (float) r * 0.025f;
        double[] cs = new double[n + 1], sn = new double[n + 1], ys = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double a = i * TAU / n;
            cs[i] = Math.cos(a); sn[i] = Math.sin(a);
            ys[i] = groundY(lv, w, w.x + cs[i] * r, w.z + sn[i] * r) + 0.12;   // acompanha o relevo
        }
        double ri = r - half, ro = r + half;
        float A = 0.8f * fade;
        for (int i = 0; i < n; i++) {
            int j = i + 1;
            // faixa brilhante no chão (borda transparente -> centro forte -> borda transparente)
            quad(vc, m, cam, col,
                    w.x + cs[i] * ri, ys[i], w.z + sn[i] * ri, 0f,
                    w.x + cs[i] * r,  ys[i], w.z + sn[i] * r,  A,
                    w.x + cs[j] * r,  ys[j], w.z + sn[j] * r,  A,
                    w.x + cs[j] * ri, ys[j], w.z + sn[j] * ri, 0f);
            quad(vc, m, cam, col,
                    w.x + cs[i] * r,  ys[i], w.z + sn[i] * r,  A,
                    w.x + cs[i] * ro, ys[i], w.z + sn[i] * ro, 0f,
                    w.x + cs[j] * ro, ys[j], w.z + sn[j] * ro, 0f,
                    w.x + cs[j] * r,  ys[j], w.z + sn[j] * r,  A);
            // parede de pressão ondulando, sobe e some
            double hw = (2.5 + 1.2 * Math.sin(i * 0.7 + tt * 0.5)) * (0.5 + 0.5 * (1f - u));
            quad(vc, m, cam, col,
                    w.x + cs[i] * r, ys[i],      w.z + sn[i] * r, 0.55f * fade,
                    w.x + cs[j] * r, ys[j],      w.z + sn[j] * r, 0.55f * fade,
                    w.x + cs[j] * r, ys[j] + hw, w.z + sn[j] * r, 0f,
                    w.x + cs[i] * r, ys[i] + hw, w.z + sn[i] * r, 0f);
        }
    }

    /** Clarão no chão + pilar de luz no momento do grito. */
    private static void flash(VertexConsumer vc, Matrix4f m, Vec3 cam, Wave w, float t) {
        if (t > 16f) return;
        float k = 1f - t / 16f;
        float rad = 9f * (float) Math.sqrt(t / 16f) + 1f;
        float[] c = {1f, 0.97f, 0.85f};
        for (int i = 0; i < 32; i++) {
            double a0 = i * TAU / 32, a1 = (i + 1) * TAU / 32;
            quad(vc, m, cam, c,
                    w.x, w.y + 0.15, w.z, 0.9f * k,
                    w.x, w.y + 0.15, w.z, 0.9f * k,
                    w.x + Math.cos(a1) * rad, w.y + 0.15, w.z + Math.sin(a1) * rad, 0f,
                    w.x + Math.cos(a0) * rad, w.y + 0.15, w.z + Math.sin(a0) * rad, 0f);
        }
        for (int i = 0; i < 24; i++) {
            double a0 = i * TAU / 24, a1 = (i + 1) * TAU / 24;
            quad(vc, m, cam, c,
                    w.x + Math.cos(a0) * 1.6, w.y, w.z + Math.sin(a0) * 1.6, 0.7f * k,
                    w.x + Math.cos(a1) * 1.6, w.y, w.z + Math.sin(a1) * 1.6, 0.7f * k,
                    w.x + Math.cos(a1) * 0.9, w.y + 40, w.z + Math.sin(a1) * 0.9, 0f,
                    w.x + Math.cos(a0) * 0.9, w.y + 40, w.z + Math.sin(a0) * 0.9, 0f);
        }
    }

    private static double groundY(ClientLevel lv, Wave w, double x, double z) {
        int h = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        return h <= lv.getMinBuildHeight() ? w.y : h;   // chunk não carregado: usa a altura do Macaco
    }

    // ------------------------------------------------------------ tremor de câmera
    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (WAVES.isEmpty()) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = (float) e.getPartialTick();
        float amp = 0f;
        for (Wave w : WAVES) {
            float t = w.age + pt;
            double d = Math.hypot(cam.x - w.x, cam.z - w.z);
            float env = (float) Math.exp(-t / 22.0) * Math.min(1f, t / 2f);
            float near = (float) Mth.clamp(1.0 - d / 110.0, 0.0, 1.0);
            amp = Math.max(amp, env * near);
        }
        if (amp < 0.002f) return;
        float tm = Minecraft.getInstance().level == null ? 0f : Minecraft.getInstance().level.getGameTime() + pt;
        e.setYaw(e.getYaw() + amp * 2.2f * Mth.sin(tm * 2.3f));
        e.setPitch(e.getPitch() + amp * 2.0f * Mth.sin(tm * 3.1f + 1f));
        e.setRoll(e.getRoll() + amp * 3.0f * Mth.sin(tm * 1.7f + 2f));
    }

    // ------------------------------------------------------------------ helpers
    private static void vtx(VertexConsumer vc, Matrix4f m, Vec3 cam, double x, double y, double z, float[] c, float a) {
        vc.addVertex(m, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .setColor(c[0], c[1], c[2], Mth.clamp(a, 0f, 1f));
    }

    /** Quad nas duas faces, com alpha por vértice. */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 cam, float[] c,
                             double x0, double y0, double z0, float a0,
                             double x1, double y1, double z1, float a1,
                             double x2, double y2, double z2, float a2,
                             double x3, double y3, double z3, float a3) {
        vtx(vc, m, cam, x0, y0, z0, c, a0); vtx(vc, m, cam, x1, y1, z1, c, a1);
        vtx(vc, m, cam, x2, y2, z2, c, a2); vtx(vc, m, cam, x3, y3, z3, c, a3);
        vtx(vc, m, cam, x3, y3, z3, c, a3); vtx(vc, m, cam, x2, y2, z2, c, a2);
        vtx(vc, m, cam, x1, y1, z1, c, a1); vtx(vc, m, cam, x0, y0, z0, c, a0);
    }

    private RoarFx() {}
}
