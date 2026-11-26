package dev.rptag.king.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Ferramentas de desenho do Rei: fitas, anéis, glifos amarelos e o Sinal Amarelo. Tudo procedural, nada de partículas. */
final class KingGfx {
    static final float[] GOLD = {1.00f, 0.80f, 0.14f, 1f};
    static final float[] PALE = {1.00f, 0.94f, 0.58f, 1f};
    static final float[] AMBER = {0.85f, 0.52f, 0.04f, 1f};

    /** Glifos: lista de segmentos {x0,y0,x1,y1} em -0.5..0.5. */
    static final float[][][] GLYPH;
    static {
        List<float[][]> g = new ArrayList<>();
        g.add(new float[][]{{0, -.5f, 0, .5f}, {-.35f, .4f, -.35f, 0}, {-.35f, 0, 0, -.2f}, {0, -.2f, .35f, 0}, {.35f, 0, .35f, .4f}});          // Psi
        g.add(new float[][]{{-.4f, -.35f, .4f, -.35f}, {.4f, -.35f, 0, .4f}, {0, .4f, -.4f, -.35f}, {-.06f, 0, .06f, 0}});                       // triângulo-olho
        g.add(new float[][]{{0, -.5f, 0, .5f}, {0, 0, -.35f, .4f}, {0, 0, .35f, .4f}});                                                           // ᛉ
        g.add(new float[][]{{-.4f, -.3f, -.2f, .3f}, {-.2f, .3f, 0, -.3f}, {0, -.3f, .2f, .3f}, {.2f, .3f, .4f, -.3f}});                         // zigue-zague
        g.add(new float[][]{{-.3f, -.5f, -.3f, .5f}, {-.3f, 0, .3f, .5f}, {-.3f, 0, .3f, -.5f}});                                                // K
        g.add(new float[][]{{-.3f, .4f, .3f, .4f}, {.3f, .4f, -.3f, -.4f}, {-.3f, -.4f, .3f, -.4f}});                                            // Z
        List<float[]> circ = new ArrayList<>();                                                                                                   // ⊕
        for (int i = 0; i < 12; i++) {
            double a0 = i * Math.PI / 6, a1 = (i + 1) * Math.PI / 6;
            circ.add(new float[]{(float) Math.cos(a0) * .45f, (float) Math.sin(a0) * .45f, (float) Math.cos(a1) * .45f, (float) Math.sin(a1) * .45f});
        }
        circ.add(new float[]{-.45f, 0, .45f, 0}); circ.add(new float[]{0, -.45f, 0, .45f});
        g.add(circ.toArray(new float[0][]));
        List<float[]> eye = new ArrayList<>();                                                                                                    // olho
        for (int i = 0; i < 10; i++) {
            float x0 = -.5f + i * .1f, x1 = x0 + .1f;
            float y0 = .22f * (1 - (x0 * 2) * (x0 * 2)), y1 = .22f * (1 - (x1 * 2) * (x1 * 2));
            eye.add(new float[]{x0, y0, x1, y1}); eye.add(new float[]{x0, -y0, x1, -y1});
        }
        eye.add(new float[]{-.06f, -.08f, .06f, .08f}); eye.add(new float[]{-.06f, .08f, .06f, -.08f});
        g.add(eye.toArray(new float[0][]));
        GLYPH = g.toArray(new float[0][][]);
    }

    static void v(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 p, float[] c, float a) {
        vc.addVertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).setColor(c[0], c[1], c[2], Mth.clamp(a, 0f, 1f));
    }

    /** Fita sempre voltada para a câmera. */
    static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 p0, Vec3 p1, float w, float[] c, float a) {
        if (a <= 0.004f) return;
        Vec3 mid = p0.add(p1).scale(0.5).subtract(cam);
        Vec3 side = p1.subtract(p0).cross(mid);
        if (side.lengthSqr() < 1e-12) return;
        side = side.normalize().scale(w * 0.5);
        v(vc, m, cam, p0.add(side), c, a);
        v(vc, m, cam, p0.subtract(side), c, a);
        v(vc, m, cam, p1.subtract(side), c, a);
        v(vc, m, cam, p1.add(side), c, a);
    }

    /** Fita com brilho: halo largo + miolo + núcleo claro (no modo baixo/minimo reduz para 1 ou 2 fitas). */
    static void glow(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 p0, Vec3 p1, float w, float[] c, float a) {
        if (dev.rptag.client.FxQualidade.minimoOuMenos()) {
            ribbon(vc, m, cam, p0, p1, w * 1.15f, c, a * 0.9f);
            return;
        }
        if (!dev.rptag.client.FxQualidade.baixoOuMenos()) {
            ribbon(vc, m, cam, p0, p1, w * 2.6f, c, a * 0.18f);
        }
        ribbon(vc, m, cam, p0, p1, w, c, a * 0.75f);
        ribbon(vc, m, cam, p0, p1, w * 0.35f, PALE, a);
    }

    /** Base (direita, cima) voltada para a câmera. */
    static Vec3[] facing(Vec3 cam, Vec3 at) {
        Vec3 n = cam.subtract(at);
        if (n.lengthSqr() < 1e-6) n = new Vec3(0, 0, 1);
        n = n.normalize();
        Vec3 r = new Vec3(0, 1, 0).cross(n);
        if (r.lengthSqr() < 1e-6) r = new Vec3(1, 0, 0);
        r = r.normalize();
        return new Vec3[]{r, n.cross(r).normalize()};
    }

    static Vec3[] flat(float rot) {
        return new Vec3[]{new Vec3(Mth.cos(rot), 0, Mth.sin(rot)), new Vec3(-Mth.sin(rot), 0, Mth.cos(rot))};
    }

    static void glyph(VertexConsumer vc, Matrix4f m, Vec3 cam, int idx, Vec3 o, Vec3[] b, float s, float rot, float w, float[] c, float a) {
        float cs = Mth.cos(rot), sn = Mth.sin(rot);
        for (float[] sg : GLYPH[Math.floorMod(idx, GLYPH.length)]) {
            float x0 = sg[0] * cs - sg[1] * sn, y0 = sg[0] * sn + sg[1] * cs, x1 = sg[2] * cs - sg[3] * sn, y1 = sg[2] * sn + sg[3] * cs;
            Vec3 p0 = o.add(b[0].scale(x0 * s)).add(b[1].scale(y0 * s)), p1 = o.add(b[0].scale(x1 * s)).add(b[1].scale(y1 * s));
            glow(vc, m, cam, p0, p1, w, c, a);
        }
    }

    /** Anel num plano qualquer (base b), opcionalmente tracejado. */
    static void ring(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 o, Vec3[] b, double rx, double ry, float w, int segs, float rot,
                     int dashes, float dashFrac, float[] c, float a) {
        segs = dev.rptag.client.FxQualidade.segmentos(segs);
        int per = dashes > 0 ? Math.max(1, segs / dashes) : segs;
        Vec3 prev = null;
        for (int k = 0; k <= segs; k++) {
            float ang = rot + (float) (Math.PI * 2 * k / segs);
            Vec3 p = o.add(b[0].scale(Math.cos(ang) * rx)).add(b[1].scale(Math.sin(ang) * ry));
            if (prev != null && (dashes <= 0 || ((k - 1) % per) < per * dashFrac)) ribbon(vc, m, cam, prev, p, w, c, a);
            prev = p;
        }
    }

    static double gy(Level lv, double x, double z, double cy) {
        if (lv == null) return cy + 0.04;
        int h = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        if (h <= lv.getMinBuildHeight() + 1 || Math.abs(h - cy) > 3.5) return cy + 0.04;
        return h + 0.04;
    }

    /** Anel colado no chão — usa cache de alturas em FxAlturas (antes fazia getHeight a cada frame!). */
    static void ringGround(VertexConsumer vc, Matrix4f m, Vec3 cam, Level lv, Vec3 c, double radius, float w, int segs, float rot,
                           int dashes, float dashFrac, float[] col, float a) {
        segs = dev.rptag.client.FxQualidade.segmentos(segs);
        int per = dashes > 0 ? Math.max(1, segs / dashes) : segs;
        long chave = (((long) Mth.floor(c.x * 2)) << 36) ^ ((((long) Mth.floor(c.z * 2)) & 0xFFFFFL) << 16) ^ ((long) (radius * 20) & 0xFFFFL);
        float[] ys = dev.rptag.client.FxAlturas.anelPos(lv, chave, c.x, c.y, c.z, (float) radius);
        int totalN = dev.rptag.client.FxAlturas.N;
        Vec3 prev = null;
        for (int k = 0; k <= segs; k++) {
            float ang = rot + (float) (Math.PI * 2 * k / segs);
            double x = c.x + Math.cos(ang) * radius, z = c.z + Math.sin(ang) * radius;
            float norm = ((ang % 6.2831855f) + 6.2831855f) % 6.2831855f;
            int idx = Math.min(totalN, (int) (norm / 6.2831855f * totalN + 0.5f));
            Vec3 p = new Vec3(x, c.y + ys[idx], z);
            if (prev != null && (dashes <= 0 || ((k - 1) % per) < per * dashFrac)) ribbon(vc, m, cam, prev, p, w, col, a);
            prev = p;
        }
    }

    /** Linha radial colada no chão. */
    static void spokeGround(VertexConsumer vc, Matrix4f m, Vec3 cam, Level lv, Vec3 c, float ang, double r0, double r1, float w, float[] col, float a) {
        Vec3 prev = null;
        for (int k = 0; k <= 6; k++) {
            double r = r0 + (r1 - r0) * k / 6.0, x = c.x + Math.cos(ang) * r, z = c.z + Math.sin(ang) * r;
            Vec3 p = new Vec3(x, gy(lv, x, z, c.y), z);
            if (prev != null) ribbon(vc, m, cam, prev, p, w, col, a);
            prev = p;
        }
    }

    /** O Sinal Amarelo estilizado: anel, três braços em espiral e um olho no centro. */
    static void sign(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 o, Vec3[] b, float s, float rot, float w, float a) {
        ring(vc, m, cam, o, b, s, s, w, 28, rot, 0, 1f, GOLD, a);
        ring(vc, m, cam, o, b, s * 0.82, s * 0.82, w * 0.6f, 24, -rot, 8, 0.55f, AMBER, a * 0.8f);
        for (int arm = 0; arm < 3; arm++) {
            Vec3 prev = null;
            for (int k = 0; k <= 10; k++) {
                float f = k / 10f, ang = rot + arm * (float) (Math.PI * 2 / 3) + f * 2.4f, r = s * (0.18f + 0.62f * f);
                Vec3 p = o.add(b[0].scale(Math.cos(ang) * r)).add(b[1].scale(Math.sin(ang) * r));
                if (prev != null) glow(vc, m, cam, prev, p, w, GOLD, a);
                prev = p;
            }
        }
        glyph(vc, m, cam, 7, o, b, s * 0.42f, rot * 0.3f, w * 0.8f, PALE, a);
    }

    static float ease(float t) { t = Mth.clamp(t, 0f, 1f); return 1f - (1f - t) * (1f - t) * (1f - t); }
    static float hash(int n) { float s = Mth.sin(n * 12.9898f + 78.233f) * 43758.5453f; return s - (float) Math.floor(s); }

    private KingGfx() {}
}
