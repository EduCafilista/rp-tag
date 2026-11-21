package dev.rptag.king.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.king.King;
import dev.rptag.king.KingFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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

/** Animações das habilidades do Rei (anéis, correntes, portais, raio, meteoros…). Só desenho, sem partículas. */
@EventBusSubscriber(modid = King.MODID, value = Dist.CLIENT)
public final class KingFxClient {
    private static final class Fx {
        final int type, id, life; final Vec3 a, b; final float f1, f2; int age;
        Fx(int type, int id, Vec3 a, Vec3 b, float f1, float f2, int life) {
            this.type = type; this.id = id; this.a = a; this.b = b; this.f1 = f1; this.f2 = f2; this.life = life;
        }
    }

    private static final List<Fx> FX = new ArrayList<>();
    private static final int MAX = 220;
    static float shake;

    // ------------------------------------------------------------------ chegada do servidor
    public static void handle(KingFxPayload p) {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) return;
        Vec3 a = new Vec3(p.x(), p.y(), p.z()), b = new Vec3(p.x2(), p.y2(), p.z2());
        switch (p.kind()) {
            case KingFxPayload.DECREE -> { add(new Fx(p.kind(), p.id(), a, b, p.a(), p.b(), 90)); shake = Math.max(shake, 0.7f); }
            case KingFxPayload.CHAINS -> add(new Fx(p.kind(), p.id(), a, b, p.a(), p.b(), Math.max(10, (int) p.a()) + 8));
            case KingFxPayload.PORTAL -> add(new Fx(p.kind(), p.id(), a, b, p.a(), p.b(), 18));
            case KingFxPayload.CONFUSE -> {
                if (Minecraft.getInstance().player != null && p.id() == Minecraft.getInstance().player.getId()) KingPassiveClient.confuse((int) p.a());
            }
            case KingFxPayload.POOF -> add(new Fx(p.kind(), p.id(), a, b, 0, 0, 12));
            case KingFxPayload.CHARGE -> add(new Fx(p.kind(), p.id(), a, b, p.a(), p.b(), Math.max(4, (int) p.a()) + 2));
            case KingFxPayload.BEAM -> { add(new Fx(p.kind(), p.id(), a, b, p.a(), p.b(), 12)); shake = Math.max(shake, 0.9f); }
            case KingFxPayload.METEOR -> add(new Fx(p.kind(), p.id(), a, b, p.a(), p.b(), (int) p.a() + 14));
            case KingFxPayload.ZONE -> add(new Fx(p.kind(), p.id(), a, b, p.a(), p.b(), Math.max(20, (int) p.b())));
            case KingFxPayload.REALM -> KingRealmClient.handle(p);
            case KingFxPayload.WAVE -> { add(new Fx(p.kind(), p.id(), a, b, p.a(), p.b(), 18)); shake = Math.max(shake, 0.45f); }
            default -> {}
        }
    }

    private static void add(Fx f) { if (FX.size() < MAX) FX.add(f); }

    // ------------------------------------------------------------------ tick / câmera
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        shake *= 0.82f;
        if (shake < 0.003f) shake = 0f;
        for (Iterator<Fx> it = FX.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            if (f.type == KingFxPayload.METEOR && f.age == (int) f.f1) shake = Math.max(shake, 0.35f);
            if (++f.age > f.life) it.remove();
        }
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (shake <= 0f) return;
        Minecraft mc = Minecraft.getInstance();
        float t = (mc.level == null ? 0f : mc.level.getGameTime()) + (float) e.getPartialTick();
        e.setYaw(e.getYaw() + shake * 1.5f * Mth.sin(t * 2.7f));
        e.setPitch(e.getPitch() + shake * 1.3f * Mth.sin(t * 3.5f + 1f));
    }

    // ------------------------------------------------------------------ desenho
    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FX.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Matrix4f m = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bs.getBuffer(RenderType.lightning());
        for (Fx f : FX) {
            float t = f.age + pt;
            switch (f.type) {
                case KingFxPayload.DECREE -> decree(vc, m, cam, f, t);
                case KingFxPayload.CHAINS -> chains(mc.level, vc, m, cam, f, t, pt);
                case KingFxPayload.PORTAL -> portal(vc, m, cam, f, t);
                case KingFxPayload.POOF -> poof(vc, m, cam, f, t);
                case KingFxPayload.CHARGE -> charge(mc.level, vc, m, cam, f, t, pt);
                case KingFxPayload.BEAM -> beam(vc, m, cam, f, t);
                case KingFxPayload.METEOR -> meteor(vc, m, cam, f, t);
                case KingFxPayload.ZONE -> zone(vc, m, cam, f, t);
                case KingFxPayload.WAVE -> wave(vc, m, cam, f, t);
                default -> {}
            }
        }
        bs.endBatch(RenderType.lightning());
    }

    private static Vec3 up(Vec3 p, double y) { return p.add(0, y, 0); }

    // ---- Decreto: três anéis dourados + marca amarela no chão
    private static void decree(VertexConsumer vc, Matrix4f m, Vec3 cam, Fx f, float t) {
        Vec3[] fl = KingGfx.flat(0);
        double R = f.f1;
        for (int k = 0; k < 3; k++) {
            float u = (t - k * 3f) / 12f;
            if (u < 0 || u > 1) continue;
            float e = KingGfx.ease(u);
            KingGfx.ring(vc, m, cam, up(f.a, 0.08 + k * 0.05), fl, R * e, R * e, 0.55f * (1 - u) + 0.12f, 56, 0, 0, 1f, KingGfx.GOLD, 0.95f * (1 - u));
        }
        float fade = Mth.clamp((f.life - t) / 30f, 0f, 1f) * KingGfx.ease(t / 6f);
        if (fade <= 0) return;
        Vec3 o = up(f.a, 0.07);
        float rot = t * 0.02f;
        KingGfx.ring(vc, m, cam, o, KingGfx.flat(0), R * 0.62, R * 0.62, 0.16f, 56, rot, 0, 1f, KingGfx.GOLD, 0.8f * fade);
        KingGfx.ring(vc, m, cam, o, KingGfx.flat(0), R * 0.55, R * 0.55, 0.10f, 48, -rot, 12, 0.6f, KingGfx.AMBER, 0.7f * fade);
        KingGfx.ring(vc, m, cam, o, KingGfx.flat(0), R * 0.30, R * 0.30, 0.12f, 36, -rot * 1.5f, 0, 1f, KingGfx.GOLD, 0.7f * fade);
        for (int i = 0; i < 6; i++) {
            float a = rot + i * (float) (Math.PI / 3);
            Vec3 p = o.add(Math.cos(a) * R * 0.46, 0, Math.sin(a) * R * 0.46);
            KingGfx.glyph(vc, m, cam, i, p, KingGfx.flat(a + 1.5708f), (float) R * 0.12f, 0f, 0.10f, KingGfx.GOLD, 0.85f * fade);
        }
        KingGfx.sign(vc, m, cam, o, KingGfx.flat(rot * 2), (float) R * 0.22f, 0f, 0.09f, 0.85f * fade);
    }

    // ---- Correntes: quatro elos em hélice subindo do chão em volta do alvo
    private static void chains(ClientLevel lv, VertexConsumer vc, Matrix4f m, Vec3 cam, Fx f, float t, float pt) {
        Entity en = lv.getEntity(f.id);
        Vec3 base = en != null ? new Vec3(Mth.lerp(pt, en.xo, en.getX()), Mth.lerp(pt, en.yo, en.getY()), Mth.lerp(pt, en.zo, en.getZ())) : f.a;
        float h = en != null ? en.getBbHeight() : 1.8f;
        float grow = KingGfx.ease(t / 8f), fade = Mth.clamp((f.life - t) / 8f, 0f, 1f);
        if (fade <= 0) return;
        Vec3[] fl = KingGfx.flat(0);
        KingGfx.ring(vc, m, cam, up(base, 0.06), fl, 1.0, 1.0, 0.16f, 28, t * 0.05f, 0, 1f, KingGfx.GOLD, 0.8f * fade);
        KingGfx.ring(vc, m, cam, up(base, 0.06), fl, 0.7, 0.7, 0.10f, 24, -t * 0.07f, 6, 0.6f, KingGfx.AMBER, 0.7f * fade);
        for (int s = 0; s < 4; s++) {
            Vec3 prev = null;
            final int N = 22;
            for (int k = 0; k <= N; k++) {
                float u = k / (float) N;
                float y = u * h * 1.05f * grow;
                float ang = s * 1.5708f + u * 5.2f + t * 0.06f;
                double r = (0.62 + 0.08 * Mth.sin(t * 0.3f + k)) * (1.0 - 0.15 * u);
                Vec3 p = base.add(Math.cos(ang) * r, y, Math.sin(ang) * r);
                if (prev != null && (k % 2 == 0)) {                                          // elos separados
                    KingGfx.ribbon(vc, m, cam, prev, p, 0.20f, KingGfx.AMBER, 0.35f * fade);
                    KingGfx.ribbon(vc, m, cam, prev, p, 0.11f, KingGfx.GOLD, 0.95f * fade);
                    KingGfx.ribbon(vc, m, cam, prev, p, 0.04f, KingGfx.PALE, fade);
                }
                prev = p;
            }
        }
    }

    // ---- Portal amarelo
    private static void portal(VertexConsumer vc, Matrix4f m, Vec3 cam, Fx f, float t) {
        float s = Mth.sin((float) Math.PI * Mth.clamp(t / f.life, 0f, 1f));
        s = Mth.clamp(s * 1.25f, 0f, 1f);
        Vec3 c = up(f.a, 1.0);
        Vec3 n = new Vec3(cam.x - c.x, 0, cam.z - c.z);
        n = n.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : n.normalize();
        Vec3[] b = {new Vec3(-n.z, 0, n.x), new Vec3(0, 1, 0)};
        for (int i = 0; i < 5; i++) {
            float k = 1f - i * 0.17f;
            KingGfx.ring(vc, m, cam, c, b, 0.9 * k * s, 1.5 * k * s, 0.30f, 32, 0, 0, 1f, i == 0 ? KingGfx.GOLD : KingGfx.AMBER, (i == 0 ? 0.95f : 0.28f) * s);
        }
        for (int a = 0; a < 3; a++) {                                                         // redemoinho dentro do portal
            Vec3 prev = null;
            for (int k = 0; k <= 12; k++) {
                float u = k / 12f, ang = t * 0.5f + a * 2.094f + u * 3.2f;
                Vec3 p = c.add(b[0].scale(Math.cos(ang) * 0.8 * u * s)).add(b[1].scale(Math.sin(ang) * 1.35 * u * s));
                if (prev != null) KingGfx.glow(vc, m, cam, prev, p, 0.08f, KingGfx.GOLD, 0.8f * s);
                prev = p;
            }
        }
        if (f.f1 > 0.5f) {                                                                    // chegada: pequeno estouro de energia no chão
            float u = t / f.life;
            KingGfx.ring(vc, m, cam, up(f.a, 0.08), KingGfx.flat(0), 3.5 * KingGfx.ease(u), 3.5 * KingGfx.ease(u), 0.35f * (1 - u) + 0.05f, 40, 0, 0, 1f, KingGfx.GOLD, 0.9f * (1 - u));
        }
    }

    // ---- Estalo ao sumir uma ilusão
    private static void poof(VertexConsumer vc, Matrix4f m, Vec3 cam, Fx f, float t) {
        float u = t / f.life;
        if (u > 1) return;
        Vec3[] b = KingGfx.facing(cam, f.a);
        KingGfx.ring(vc, m, cam, f.a, b, 0.3 + 1.6 * KingGfx.ease(u), 0.3 + 1.6 * KingGfx.ease(u), 0.12f, 24, 0, 0, 1f, KingGfx.GOLD, 0.9f * (1 - u));
        for (int i = 0; i < 5; i++) {
            float a = i * 1.2566f + 0.4f;
            Vec3 p = f.a.add(b[0].scale(Math.cos(a) * 1.0 * KingGfx.ease(u))).add(b[1].scale(Math.sin(a) * 1.0 * KingGfx.ease(u) + 0.3 * u));
            KingGfx.glyph(vc, m, cam, i + 2, p, b, 0.35f, a, 0.05f, KingGfx.GOLD, 0.9f * (1 - u));
        }
    }

    // ---- Olhar de Hastur: esfera de carga + raios convergindo + linha de aviso
    private static void charge(ClientLevel lv, VertexConsumer vc, Matrix4f m, Vec3 cam, Fx f, float t, float pt) {
        Entity owner = lv.getEntity(f.id);
        if (owner == null) return;
        float u = Mth.clamp(t / Math.max(1f, f.f1), 0f, 1f);
        Vec3 eye = owner.getEyePosition(pt);
        Vec3 orb = eye.add(owner.getViewVector(pt).scale(1.0)).add(0, -0.1, 0);
        Vec3[] b = KingGfx.facing(cam, orb);
        float r = 0.08f + 0.42f * u;
        for (int i = 0; i < 3; i++) KingGfx.ring(vc, m, cam, orb, b, r * (1 - i * 0.22), r * (1 - i * 0.22), 0.07f + 0.04f * u, 24, t * (0.3f + i * 0.2f) * (i % 2 == 0 ? 1 : -1), 6, 0.6f, i == 1 ? KingGfx.PALE : KingGfx.GOLD, 0.95f);
        KingGfx.sign(vc, m, cam, orb, b, r * 0.9f, t * 0.2f, 0.04f, 0.9f);
        for (int i = 0; i < 9; i++) {                                                          // raios sendo sugados para a esfera
            float h1 = KingGfx.hash(i * 31 + 7), h2 = KingGfx.hash(i * 17 + 3);
            float ph = (t * 0.12f + h1) % 1f;
            double ang = h1 * 6.283, el = (h2 - 0.5) * 2.2, d = 3.2 * (1 - ph);
            Vec3 far = orb.add(Math.cos(ang) * Math.cos(el) * d, Math.sin(el) * d, Math.sin(ang) * Math.cos(el) * d);
            Vec3 near = orb.add(Math.cos(ang) * Math.cos(el) * d * 0.7, Math.sin(el) * d * 0.7, Math.sin(ang) * Math.cos(el) * d * 0.7);
            KingGfx.glow(vc, m, cam, far, near, 0.05f, KingGfx.GOLD, 0.8f * ph);
        }
        Entity tg = lv.getEntity((int) f.f2);
        if (tg != null) {                                                                      // linha fina de mira
            Vec3 tc = tg.getBoundingBox().getCenter();
            KingGfx.ribbon(vc, m, cam, orb, tc, 0.04f, KingGfx.GOLD, 0.22f + 0.2f * Mth.sin(t * 1.3f));
            Vec3[] tb = KingGfx.facing(cam, tc);
            KingGfx.ring(vc, m, cam, tc, tb, 0.9 - 0.4 * u, 0.9 - 0.4 * u, 0.06f, 24, t * 0.1f, 8, 0.5f, KingGfx.GOLD, 0.7f);
            KingGfx.glyph(vc, m, cam, 1, tc, tb, 0.5f, 0f, 0.05f, KingGfx.GOLD, 0.7f);
        }
    }

    // ---- Raio reto amarelo
    private static void beam(VertexConsumer vc, Matrix4f m, Vec3 cam, Fx f, float t) {
        float u = Mth.clamp(t / f.life, 0f, 1f), fade = (1 - u) * (1 - u);
        if (fade <= 0.01f) return;
        float w = 0.9f * (0.5f + 0.5f * KingGfx.ease(u * 3f));
        KingGfx.ribbon(vc, m, cam, f.a, f.b, w * 2.4f, KingGfx.AMBER, 0.25f * fade);
        KingGfx.ribbon(vc, m, cam, f.a, f.b, w * 1.2f, KingGfx.GOLD, 0.7f * fade);
        KingGfx.ribbon(vc, m, cam, f.a, f.b, w * 0.55f, KingGfx.PALE, 0.95f * fade);
        Vec3 d = f.b.subtract(f.a);
        double len = d.length();
        if (len < 0.1) return;
        for (int i = 0; i < 7; i++) {                                                          // anéis viajando ao longo do raio
            float ph = (t * 0.22f + i / 7f) % 1f;
            Vec3 p = f.a.add(d.scale(ph));
            Vec3 dir = d.scale(1.0 / len);
            Vec3 r = dir.cross(new Vec3(0, 1, 0));
            if (r.lengthSqr() < 1e-6) r = new Vec3(1, 0, 0);
            r = r.normalize();
            Vec3[] b = {r, dir.cross(r).normalize()};
            KingGfx.ring(vc, m, cam, p, b, 0.9, 0.9, 0.10f, 16, 0, 0, 1f, KingGfx.GOLD, 0.7f * fade);
        }
        Vec3[] bb = KingGfx.facing(cam, f.b);
        KingGfx.ring(vc, m, cam, f.b, bb, 0.6 + 2.0 * KingGfx.ease(u), 0.6 + 2.0 * KingGfx.ease(u), 0.2f * (1 - u) + 0.03f, 28, 0, 0, 1f, KingGfx.GOLD, 0.8f * fade);
    }

    // ---- Queda de Carcosa: marca de aviso, meteoro caindo, impacto
    private static void meteor(VertexConsumer vc, Matrix4f m, Vec3 cam, Fx f, float t) {
        float d = f.f1, R = f.f2;
        Vec3 g = up(f.a, 0.08);
        Vec3[] fl = KingGfx.flat(0);
        if (t < d + 4) {                                                                       // aviso no chão
            float k = Mth.clamp(t / d, 0f, 1f);
            float pulse = 0.55f + 0.45f * Mth.sin(t * (0.4f + k * 0.8f));
            KingGfx.ring(vc, m, cam, g, fl, R, R, 0.18f, 36, t * 0.03f, 12, 0.6f, KingGfx.GOLD, (0.35f + 0.5f * k) * pulse);
            KingGfx.ring(vc, m, cam, g, fl, R * (1 - 0.85 * k), R * (1 - 0.85 * k), 0.12f, 32, -t * 0.05f, 0, 1f, KingGfx.AMBER, 0.8f);
            KingGfx.glyph(vc, m, cam, 6, g, KingGfx.flat(t * 0.04f), R * 0.8f, 0f, 0.10f, KingGfx.GOLD, 0.6f * pulse);
        }
        float fallLen = 16f;
        if (t > d - fallLen && t <= d) {
            float k = (t - (d - fallLen)) / fallLen;
            Vec3 dir = new Vec3(-0.35, 1, -0.28).normalize();
            Vec3 pos = f.a.add(dir.scale(46 * (1 - k * k)));
            Vec3 tail = pos.add(dir.scale(10));
            for (int i = 0; i < 6; i++) {
                Vec3 p0 = pos.add(dir.scale(i * 1.7)), p1 = pos.add(dir.scale((i + 1) * 1.7));
                KingGfx.ribbon(vc, m, cam, p0, p1, 1.5f * (1 - i / 6f), KingGfx.AMBER, 0.55f * (1 - i / 6f));
            }
            KingGfx.ribbon(vc, m, cam, pos, tail, 0.6f, KingGfx.GOLD, 0.7f);
            Vec3[] b = KingGfx.facing(cam, pos);
            for (int i = 0; i < 4; i++) KingGfx.ring(vc, m, cam, pos, b, 0.2 + i * 0.25, 0.2 + i * 0.25, 0.30f - i * 0.05f, 16, t, 0, 1f, i < 2 ? KingGfx.PALE : KingGfx.GOLD, 0.9f - i * 0.15f);
            KingGfx.glyph(vc, m, cam, 7, pos, b, 0.9f, t * 0.3f, 0.07f, KingGfx.PALE, 0.9f);
        }
        if (t > d) {
            float u = (t - d) / 14f;
            if (u > 1) return;
            float e = KingGfx.ease(u);
            KingGfx.ring(vc, m, cam, g, fl, R * e, R * e, 0.5f * (1 - u) + 0.08f, 40, 0, 0, 1f, KingGfx.GOLD, 0.95f * (1 - u));
            KingGfx.ring(vc, m, cam, g, fl, R * e * 0.6, R * e * 0.6, 0.3f * (1 - u) + 0.05f, 32, 0, 0, 1f, KingGfx.PALE, 0.8f * (1 - u));
            KingGfx.ribbon(vc, m, cam, g, up(g, 12 * (1 - u * 0.5)), 1.6f * (1 - u), KingGfx.GOLD, 0.65f * (1 - u));      // coluna de luz
            KingGfx.ribbon(vc, m, cam, g, up(g, 12 * (1 - u * 0.5)), 0.6f * (1 - u), KingGfx.PALE, 0.9f * (1 - u));
        }
    }

    // ---- Zona de corrupção: marca no chão que pulsa, com fiapos dourados subindo
    private static void zone(VertexConsumer vc, Matrix4f m, Vec3 cam, Fx f, float t) {
        float fade = Mth.clamp(t / 8f, 0f, 1f) * Mth.clamp((f.life - t) / 20f, 0f, 1f);
        if (fade <= 0) return;
        double R = f.f1;
        Vec3 g = up(f.a, 0.07);
        float pulse = 0.7f + 0.3f * Mth.sin(t * 0.25f);
        Vec3[] fl = KingGfx.flat(0);
        KingGfx.ring(vc, m, cam, g, fl, R, R, 0.14f, 32, t * 0.04f, 10, 0.55f, KingGfx.GOLD, 0.8f * fade * pulse);
        KingGfx.ring(vc, m, cam, g, fl, R * 0.6, R * 0.6, 0.10f, 24, -t * 0.06f, 0, 1f, KingGfx.AMBER, 0.7f * fade);
        KingGfx.glyph(vc, m, cam, (int) (f.a.x * 3 + f.a.z), g, KingGfx.flat(t * 0.03f), (float) R * 0.9f, 0f, 0.07f, KingGfx.GOLD, 0.6f * fade);
        for (int i = 0; i < 6; i++) {
            float ph = (t * 0.03f + i / 6f) % 1f;
            float ang = i * 1.047f + KingGfx.hash(i) * 2f;
            Vec3 base = g.add(Math.cos(ang) * R * 0.7, 0, Math.sin(ang) * R * 0.7);
            Vec3 p0 = up(base, ph * 2.5), p1 = up(base, ph * 2.5 + 0.9).add(Math.sin(t * 0.2f + i) * 0.15, 0, 0);
            KingGfx.glow(vc, m, cam, p0, p1, 0.06f, KingGfx.GOLD, 0.7f * fade * (1 - ph));
        }
    }

    // ---- Onda de energia periódica (ultimate)
    private static void wave(VertexConsumer vc, Matrix4f m, Vec3 cam, Fx f, float t) {
        float u = t / f.life;
        if (u > 1) return;
        float e = KingGfx.ease(u);
        double R = f.f1 * e;
        Vec3[] fl = KingGfx.flat(0);
        for (int i = 0; i < 3; i++) {
            double rr = Math.max(0.1, R - i * 1.2);
            KingGfx.ring(vc, m, cam, up(f.a, 0.1 + i * 0.5), fl, rr, rr, 0.5f * (1 - u) + 0.1f, 64, i, 0, 1f, i == 0 ? KingGfx.PALE : KingGfx.GOLD, (0.9f - i * 0.2f) * (1 - u));
        }
        for (int i = 0; i < 10; i++) {
            float a = i * 0.628f + t * 0.02f;
            Vec3 p = up(f.a, 1.0).add(Math.cos(a) * R, 0, Math.sin(a) * R);
            KingGfx.glyph(vc, m, cam, i, p, KingGfx.facing(cam, p), 0.9f, 0f, 0.06f, KingGfx.GOLD, 0.8f * (1 - u));
        }
    }

    private KingFxClient() {}
}
