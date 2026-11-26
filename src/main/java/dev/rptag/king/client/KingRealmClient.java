package dev.rptag.king.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.rptag.king.King;
import dev.rptag.king.KingFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * O Reino de Carcosa no cliente: quem está na região vê o céu da imagem (dois sóis-eclipse negros com anéis luminosos,
 * planetas pequenos, rastro de nebulosa, céu sépia turvo, silhueta da cidade), névoa dourada, símbolos no chão e torres espectrais.
 * Servidor manda REALM uma vez por segundo; ao acabar manda raio 0.
 */
@EventBusSubscriber(modid = King.MODID, value = Dist.CLIENT)
public final class KingRealmClient {
    private static boolean active;
    private static Vec3 center = Vec3.ZERO;
    private static double radius = 32;
    private static long startTick, endTick;
    private static float intensity;          // 0..1 suavizado, para quem está olhando
    private static final float SKY_R = 100f;

    static void handle(KingFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        if (p.a() <= 0f) {                                        // fim: some em 1 s
            if (active) endTick = Math.min(endTick, now + 20);
            return;
        }
        if (!active) startTick = now - (long) p.x2();
        active = true;
        center = new Vec3(p.x(), p.y(), p.z());
        radius = p.a();
        endTick = now + (long) p.b();
    }

    private static float target(Minecraft mc, float pt) {
        if (!active || mc.player == null || mc.level == null) return 0f;
        float now = mc.level.getGameTime() + pt;
        float tin = Mth.clamp((now - startTick) / 30f, 0f, 1f), tout = Mth.clamp((endTick - now) / 30f, 0f, 1f);
        double dx = mc.player.getX() - center.x, dz = mc.player.getZ() - center.z, d = Math.sqrt(dx * dx + dz * dz);
        float region = (float) Mth.clamp(1.0 - (d - radius) / 16.0, 0.0, 1.0);          // dentro = 1; some em 16 blocos
        return tin * tout * region;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && active && mc.level.getGameTime() > endTick + 2) active = false;
        float tg = target(mc, 0f);
        intensity += (tg - intensity) * 0.12f;
        if (!active && intensity < 0.005f) intensity = 0f;
    }

    // ------------------------------------------------------------------ névoa dourada
    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor e) {
        if (intensity <= 0.01f) return;
        float k = intensity;
        e.setRed(Mth.lerp(k, e.getRed(), 0.46f));
        e.setGreen(Mth.lerp(k, e.getGreen(), 0.34f));
        e.setBlue(Mth.lerp(k, e.getBlue(), 0.10f));
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog e) {
        if (intensity <= 0.01f || e.getType() != FogType.NONE || e.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        e.setNearPlaneDistance(Mth.lerp(intensity, e.getNearPlaneDistance(), 2f));
        e.setFarPlaneDistance(Mth.lerp(intensity, e.getFarPlaneDistance(), 72f));
        e.setFogShape(FogShape.SPHERE);
        e.setCanceled(true);
    }

    // ------------------------------------------------------------------ céu
    @SubscribeEvent
    public static void sky(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || intensity <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float I = intensity;
        float t = mc.level.getGameTime() + e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Matrix4f m = new Matrix4f(e.getModelViewMatrix());
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        dome(m, I);
        nebula(m, I, t);
        eclipse(m, I, 0.55f, 0.62f, 0.165f, t);
        eclipse(m, I, -0.95f, 0.50f, 0.115f, t + 40f);
        planet(m, I, 1.35f, 0.30f, 0.030f, true);
        planet(m, I, 2.30f, 0.52f, 0.020f, false);
        planet(m, I, -2.1f, 0.38f, 0.040f, true);
        planet(m, I, 0.1f, 0.35f, 0.014f, false);
        skyline(m, I, t);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static Vec3 dir(double az, double el) {
        return new Vec3(Math.cos(el) * Math.sin(az), Math.sin(el), -Math.cos(el) * Math.cos(az));
    }

    private static void sv(BufferBuilder bb, Matrix4f m, Vec3 d, float r, float g, float b, float a) {
        bb.addVertex(m, (float) (d.x * SKY_R), (float) (d.y * SKY_R), (float) (d.z * SKY_R)).setColor(r, g, b, Mth.clamp(a, 0f, 1f));
    }

    private static void flush(BufferBuilder bb) {
        var md = bb.build();                       // null se nada foi desenhado
        if (md != null) BufferUploader.drawWithShader(md);
    }

    /** Céu sépia turvo: horizonte amarelado-sujo, zênite escuro. */
    private static void dome(Matrix4f m, float I) {
        final int SEG = 32, BANDS = 10;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int b = 0; b < BANDS; b++) {
            double e0 = Math.toRadians(-20 + 110.0 * b / BANDS), e1 = Math.toRadians(-20 + 110.0 * (b + 1) / BANDS);
            float k0 = (float) Mth.clamp(Math.sin(Math.max(0, e0)) * 1.6, 0, 1), k1 = (float) Mth.clamp(Math.sin(Math.max(0, e1)) * 1.6, 0, 1);
            for (int s = 0; s < SEG; s++) {
                double a0 = Math.PI * 2 * s / SEG, a1 = Math.PI * 2 * (s + 1) / SEG;
                sky(bb, m, dir(a0, e0), k0, I); sky(bb, m, dir(a1, e0), k0, I);
                sky(bb, m, dir(a1, e1), k1, I); sky(bb, m, dir(a0, e1), k1, I);
            }
        }
        flush(bb);
    }

    private static void sky(BufferBuilder bb, Matrix4f m, Vec3 d, float k, float I) {
        sv(bb, m, d, Mth.lerp(k, 0.60f, 0.09f), Mth.lerp(k, 0.47f, 0.065f), Mth.lerp(k, 0.17f, 0.02f), 0.985f * I);
    }

    /** Faixa de nebulosa atravessando o céu (duas tiras: centro->baixo e centro->cima). */
    private static void nebula(Matrix4f m, float I, float t) {
        final int N = 40;
        for (int half = 0; half < 2; half++) {
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= N; i++) {
                float u = i / (float) N;
                double az = Mth.lerp(u, -1.7, 1.9), el = 0.18 + 0.55 * Math.sin(u * 2.2) + 0.03 * Math.sin(u * 17 + t * 0.01);
                float wd = (0.05f + 0.10f * Mth.sin(u * 3.14159f)) * (half == 0 ? -1f : 1f);
                float al = 0.42f * I * Mth.sin(u * 3.14159f);
                sv(bb, m, dir(az, el), 0.95f, 0.78f, 0.42f, al);
                sv(bb, m, dir(az, el + wd), 0.8f, 0.62f, 0.3f, 0f);
            }
            flush(bb);
        }
    }

    private static Vec3[] basis(Vec3 d) {
        Vec3 u = d.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1e-6) u = new Vec3(1, 0, 0);
        u = u.normalize();
        return new Vec3[]{u, d.cross(u).normalize()};
    }

    private static Vec3 around(Vec3 d, Vec3[] b, double rho, double phi, double sx) {
        double tr = Math.tan(rho);
        return d.add(b[0].scale(Math.cos(phi) * tr * sx)).add(b[1].scale(Math.sin(phi) * tr)).normalize();
    }

    /** Sol-eclipse: disco negro, anel luminoso fino e corona dourada. */
    private static void eclipse(Matrix4f m, float I, float az, float el, float rho, float t) {
        Vec3 d = dir(az, el);
        Vec3[] b = basis(d);
        final int N = 48;
        BufferBuilder fan = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        sv(fan, m, d, 0.0f, 0.0f, 0.0f, I);
        for (int i = 0; i <= N; i++) sv(fan, m, around(d, b, rho, Math.PI * 2 * i / N, 1), 0.0f, 0.0f, 0.0f, I);
        flush(fan);
        float pulse = 0.9f + 0.1f * Mth.sin(t * 0.05f);
        float[][] layers = {{1.00f, 1.03f, 1f, 0.95f, 0.75f, 1f}, {1.03f, 1.55f, 0.95f, 0.70f, 0.20f, 0.0f}, {1.03f, 1.12f, 1f, 0.93f, 0.62f, 0.9f}};
        for (int li = 0; li < layers.length; li++) {
            float[] L = layers[li];
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= N; i++) {
                double ph = Math.PI * 2 * i / N;
                float wob = li == 1 ? 1f + 0.05f * Mth.sin((float) ph * 7 + t * 0.03f) : 1f;
                sv(bb, m, around(d, b, rho * L[0], ph, 1), L[2], L[3], L[4], (li == 1 ? 0.85f : L[5]) * I * pulse);
                sv(bb, m, around(d, b, rho * L[1] * wob, ph, 1), L[2], L[3], L[4], li == 1 ? 0f : L[5] * I * 0.5f);
            }
            flush(bb);
        }
    }

    private static void planet(Matrix4f m, float I, float az, float el, float rho, boolean ringed) {
        Vec3 d = dir(az, el);
        Vec3[] b = basis(d);
        final int N = 20;
        BufferBuilder fan = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        sv(fan, m, d, 0.30f, 0.22f, 0.08f, I);
        for (int i = 0; i <= N; i++) sv(fan, m, around(d, b, rho, Math.PI * 2 * i / N, 1), 0.10f, 0.07f, 0.03f, I);
        flush(fan);
        BufferBuilder rim = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= N; i++) {
            double ph = Math.PI * 2 * i / N;
            sv(rim, m, around(d, b, rho, ph, 1), 1f, 0.85f, 0.45f, 0.8f * I);
            sv(rim, m, around(d, b, rho * 1.12, ph, 1), 1f, 0.85f, 0.45f, 0f);
        }
        flush(rim);
        if (ringed) {                                                                       // anel inclinado
            BufferBuilder rg = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            double tr = Math.tan(rho);
            for (int i = 0; i <= 40; i++) {
                double ph = Math.PI * 2 * i / 40;
                Vec3 in = d.add(b[0].scale(Math.cos(ph) * tr * 1.9)).add(b[1].scale(Math.sin(ph) * tr * 0.45)).normalize();
                Vec3 out = d.add(b[0].scale(Math.cos(ph) * tr * 2.15)).add(b[1].scale(Math.sin(ph) * tr * 0.52)).normalize();
                sv(rg, m, in, 1f, 0.85f, 0.5f, 0.75f * I);
                sv(rg, m, out, 1f, 0.85f, 0.5f, 0f);
            }
            flush(rg);
        }
    }

    /** Silhueta da cidade de Carcosa no horizonte: duas camadas de torres e agulhas. */
    private static void skyline(Matrix4f m, float I, float t) {
        for (int layer = 0; layer < 2; layer++) {
            final int N = 150;
            float base = -0.12f;
            float[] col = layer == 0 ? new float[]{0.14f, 0.10f, 0.04f} : new float[]{0.015f, 0.011f, 0.005f};
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < N; i++) {
                double a0 = Math.PI * 2 * i / N, a1 = Math.PI * 2 * (i + 1) / N, am = (a0 + a1) / 2;
                float h = KingGfx.hash(i * 7 + layer * 311);
                float top = (layer == 0 ? 0.03f : 0.015f) + (h * h) * (layer == 0 ? 0.10f : 0.17f);
                float spire = KingGfx.hash(i * 13 + layer * 17) > 0.62f ? 0.05f + 0.07f * KingGfx.hash(i * 3) : 0f;
                float[] c = col;
                Vec3 b0 = dir(a0, base), b1 = dir(a1, base), t0 = dir(a0, top), t1 = dir(a1, top);
                sv(bb, m, b0, c[0], c[1], c[2], I); sv(bb, m, b1, c[0], c[1], c[2], I); sv(bb, m, t1, c[0], c[1], c[2], I);
                sv(bb, m, b0, c[0], c[1], c[2], I); sv(bb, m, t1, c[0], c[1], c[2], I); sv(bb, m, t0, c[0], c[1], c[2], I);
                if (spire > 0) {                                                               // agulha pontiaguda
                    sv(bb, m, t0, c[0], c[1], c[2], I); sv(bb, m, t1, c[0], c[1], c[2], I); sv(bb, m, dir(am, top + spire), c[0], c[1], c[2], I);
                } else if (h > 0.45f) {                                                         // degrau/cúpula
                    Vec3 s0 = dir(a0 + (a1 - a0) * 0.2, top + 0.018f), s1 = dir(a1 - (a1 - a0) * 0.2, top + 0.018f);
                    sv(bb, m, t0, c[0], c[1], c[2], I); sv(bb, m, t1, c[0], c[1], c[2], I); sv(bb, m, s1, c[0], c[1], c[2], I);
                    sv(bb, m, t0, c[0], c[1], c[2], I); sv(bb, m, s1, c[0], c[1], c[2], I); sv(bb, m, s0, c[0], c[1], c[2], I);
                }
            }
            flush(bb);
        }
        BufferBuilder win = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);   // janelas amarelas acesas
        for (int i = 0; i < 70; i++) {
            float h = KingGfx.hash(i * 5 + 900);
            if (KingGfx.hash(i * 3 + (int) (t / 40f) * 7) < 0.25f) continue;
            double az = Math.PI * 2 * KingGfx.hash(i + 55), el = -0.05 + 0.08 * h, w = 0.0035;
            float al = 0.7f * I;
            sv(win, m, dir(az - w, el), 1f, 0.8f, 0.2f, al); sv(win, m, dir(az + w, el), 1f, 0.8f, 0.2f, al);
            sv(win, m, dir(az + w, el + 0.01), 1f, 0.8f, 0.2f, al); sv(win, m, dir(az - w, el + 0.01), 1f, 0.8f, 0.2f, al);
        }
        flush(win);
    }

    // ------------------------------------------------------------------ chão e torres espectrais
    @SubscribeEvent
    public static void ground(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !active || intensity <= 0.02f) return;
        if (dev.rptag.client.FxQualidade.desligado()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float t = mc.level.getGameTime() + pt, I = intensity;
        Vec3 cam = e.getCamera().getPosition();
        Matrix4f m = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bs.getBuffer(RenderType.lightning());
        double R = radius;
        float grow = KingGfx.ease((t - startTick) / 50f);
        float rot = t * 0.004f;
        boolean min = dev.rptag.client.FxQualidade.minimoOuMenos();
        boolean baixo = dev.rptag.client.FxQualidade.baixoOuMenos();
        double[] rr = min ? new double[]{0.80} : new double[]{0.28, 0.52, 0.80};
        for (int i = 0; i < rr.length; i++) {
            KingGfx.ringGround(vc, m, cam, mc.level, center, R * rr[i] * grow, 0.20f, 96, (i % 2 == 0 ? rot : -rot) * (1 + i), 18 + i * 6, 0.55f, i == 1 ? KingGfx.GOLD : KingGfx.AMBER, 0.55f * I);
            if (!baixo) {
                KingGfx.ringGround(vc, m, cam, mc.level, center, R * rr[i] * grow * 1.03, 0.07f, 96, 0, 0, 1f, KingGfx.GOLD, 0.35f * I);
            }
        }
        int raios = min ? 0 : dev.rptag.client.FxQualidade.escala(12, 4);
        for (int i = 0; i < raios; i++)
            KingGfx.spokeGround(vc, m, cam, mc.level, center, rot * 2 + i * (float) (Math.PI * 2 / raios), R * 0.28 * grow, R * 0.80 * grow, 0.07f, KingGfx.AMBER, 0.28f * I);
        int aneisSimbolos = min ? 0 : (baixo ? 1 : 2);
        for (int ring = 0; ring < aneisSimbolos; ring++) {                          // símbolos amarelos gravados no chão
            int n = dev.rptag.client.FxQualidade.escala(ring == 0 ? 7 : 11, 4);
            double r = R * (ring == 0 ? 0.40 : 0.66) * grow;
            for (int i = 0; i < n; i++) {
                float a = (ring == 0 ? rot : -rot) * 3 + i * (float) (Math.PI * 2 / n);
                double x = center.x + Math.cos(a) * r, z = center.z + Math.sin(a) * r;
                Vec3 p = new Vec3(x, center.y + 0.06, z);
                float fl = 0.6f + 0.4f * Mth.sin(t * 0.08f + i * 1.7f);
                KingGfx.glyph(vc, m, cam, i + ring * 3, p, KingGfx.flat(a + 1.5708f), 1.8f, 0f, 0.12f, KingGfx.GOLD, 0.8f * I * fl);
            }
        }
        KingGfx.sign(vc, m, cam, new Vec3(center.x, center.y + 0.07, center.z), KingGfx.flat(t * 0.01f), (float) (R * 0.22), 0f, 0.14f, 0.7f * I);

        int torres = dev.rptag.client.FxQualidade.escala(14, 3);
        for (int s = 0; s < torres; s++) {                                          // torres espectrais subindo do chão
            float ang = KingGfx.hash(s * 3 + 1) * 6.2832f, rad = (float) (R * (0.45 + 0.5 * KingGfx.hash(s * 7 + 2)));
            double x = center.x + Math.cos(ang) * rad, z = center.z + Math.sin(ang) * rad;
            double y0 = center.y;
            float rise = KingGfx.ease((t - startTick - s * 3f) / 55f);
            float h = (9f + 16f * KingGfx.hash(s * 11 + 5)) * rise, w = 0.9f + 0.9f * KingGfx.hash(s * 5 + 9);
            float fl = (0.55f + 0.25f * Mth.sin(t * 0.07f + s * 2.3f)) * I * rise;
            if (fl <= 0.01f) continue;
            Vec3[] cn = {new Vec3(-w, 0, -w), new Vec3(w, 0, -w), new Vec3(w, 0, w), new Vec3(-w, 0, w)};
            Vec3 tip = new Vec3(x, y0 + h * 1.18, z);
            for (int c = 0; c < 4; c++) {
                Vec3 bot = new Vec3(x + cn[c].x, y0, z + cn[c].z), shoulder = new Vec3(x + cn[c].x * 0.55, y0 + h, z + cn[c].z * 0.55);
                KingGfx.glow(vc, m, cam, bot, shoulder, 0.12f, KingGfx.GOLD, 0.8f * fl);
                KingGfx.glow(vc, m, cam, shoulder, tip, 0.09f, KingGfx.AMBER, 0.8f * fl);
            }
            if (!baixo) {
                for (int lv = 1; lv <= 4; lv++) {                                  // aros horizontais
                    float u = lv / 5f, sc = 1f - 0.45f * u;
                    for (int c = 0; c < 4; c++) {
                        Vec3 a = new Vec3(x + cn[c].x * sc, y0 + h * u, z + cn[c].z * sc), b = new Vec3(x + cn[(c + 1) % 4].x * sc, y0 + h * u, z + cn[(c + 1) % 4].z * sc);
                        KingGfx.ribbon(vc, m, cam, a, b, 0.07f, KingGfx.GOLD, 0.55f * fl);
                    }
                }
                Vec3 win = new Vec3(x, y0 + h * 0.55f, z);
                KingGfx.glyph(vc, m, cam, s, win, KingGfx.facing(cam, win), 1.1f, 0f, 0.06f, KingGfx.PALE, 0.7f * fl);
            }
        }
        bs.endBatch(RenderType.lightning());
    }

    private KingRealmClient() {}
}
