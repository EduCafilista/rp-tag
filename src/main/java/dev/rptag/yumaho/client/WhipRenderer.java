package dev.rptag.yumaho.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.aura.client.AuraRenderTypes;
import dev.rptag.yumaho.WhipEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Desenha o Blackwhip: corpo preto afilado (grosso na mão, fino na ponta) que ondula ao estender,
 * fica esticado ao prender e faz laços ao retrair; veios amarelos em espiral, faíscas ramificadas e brilho na ponta.
 * Tudo procedural, sem textura. A entidade é a ponta; a mão do dono é calculada a cada frame.
 */
public class WhipRenderer extends EntityRenderer<WhipEntity> {
    private static final int N = 32;

    public WhipRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override
    public ResourceLocation getTextureLocation(WhipEntity e) {
        // (5.78.5) textura branca do proprio mod (o chicote e desenhado com RenderType proprio;
        // esta textura so existe para o contrato do EntityRenderer).
        return ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "textures/misc/white.png");
    }

    /** A ponta pode estar fora da tela enquanto o chicote ainda aparece: nunca corta. */
    @Override
    public boolean shouldRender(WhipEntity e, Frustum f, double x, double y, double z) { return true; }

    @Override
    public void render(WhipEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Entity owner = e.level().getEntity(e.ownerId());
        if (owner == null) return;
        Vec3 tip = e.getPosition(pt);
        Vec3 hand = WhipEntity.hand(owner, pt);
        Vec3 d = tip.subtract(hand);
        double L = d.length();
        if (L < 0.2) return;
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition().subtract(tip);            // câmera em coordenadas locais (origem = ponta)
        Matrix4f m = ps.last().pose();
        float power = e.power(), age = e.age() + pt;
        int st = e.state();

        // ---- eixo e base de ondulação
        Vec3 dir = d.scale(1.0 / L);
        Vec3 u = dir.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1e-4) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = dir.cross(u).normalize();

        double ampBase = Math.min(L, 14) * 0.045;
        double amp, phase, sag;
        switch (st) {
            case WhipEntity.EXTEND -> { amp = ampBase * (0.45 + 0.55 * Math.exp(-age * 0.10)); phase = -age * 0.95; sag = L * 0.012; }
            case WhipEntity.RETRACT -> { amp = ampBase * 1.5; phase = age * 1.35; sag = L * 0.02; }
            default -> { amp = ampBase * (0.08 + 0.05 * Math.sin(age * 1.7)); phase = age * 0.4; sag = 0; }   // esticado: só um tremor
        }
        double waves = 2.2 + Math.min(L, 24) * 0.10;

        // ---- pontos (relativos à ponta)
        Vec3[] p = new Vec3[N + 1];
        Vec3[] side = new Vec3[N + 1];
        for (int i = 0; i <= N; i++) {
            double s = (double) i / N, env = Math.pow(Math.sin(Math.PI * s), 0.8);
            Vec3 off = u.scale(Math.sin(s * waves * Math.PI * 2 + phase) * amp * env)
                    .add(v.scale(Math.cos(s * waves * Math.PI * 1.6 + phase * 1.1) * amp * 0.7 * env))
                    .add(0, -sag * Math.sin(Math.PI * s), 0);
            p[i] = hand.add(d.scale(s)).add(off).subtract(tip);
        }
        for (int i = 0; i <= N; i++) {
            Vec3 tan = p[Math.min(N, i + 1)].subtract(p[Math.max(0, i - 1)]);
            side[i] = tan.cross(p[i].subtract(cam)).normalize();
        }
        float thick = 0.8f + 0.5f * power;

        // ---- corpo preto afilado (opaco-escuro)
        VertexConsumer body = buf.getBuffer(AuraRenderTypes.INK);
        for (int i = 0; i < N; i++) {
            float w0 = width(i, thick), w1 = width(i + 1, thick);
            quad(body, m, p[i].add(side[i].scale(w0 * 0.5)), p[i].subtract(side[i].scale(w0 * 0.5)),
                    p[i + 1].subtract(side[i + 1].scale(w1 * 0.5)), p[i + 1].add(side[i + 1].scale(w1 * 0.5)),
                    0.02f, 0.02f, 0.03f, 0.96f);
        }

        // ---- brilho elétrico amarelo (aditivo) por cima
        VertexConsumer glow = buf.getBuffer(RenderType.lightning());
        for (int i = 0; i < N; i++) {
            float w0 = width(i, thick), w1 = width(i + 1, thick);
            quad(glow, m, p[i].add(side[i].scale(w0 * 1.6)), p[i].subtract(side[i].scale(w0 * 1.6)),
                    p[i + 1].subtract(side[i + 1].scale(w1 * 1.6)), p[i + 1].add(side[i + 1].scale(w1 * 1.6)),
                    1f, 0.78f, 0.1f, 0.10f + 0.06f * power);
        }
        // veios amarelos em espiral ao redor do corpo (2 fios que se cruzam)
        for (int strand = 0; strand < 2; strand++) {
            for (int i = 0; i < N; i++) {
                double s0 = (double) i / N, s1 = (double) (i + 1) / N;
                double o0 = Math.sin(s0 * 18 + age * 1.4 + strand * Math.PI) * 0.42 * width(i, thick);
                double o1 = Math.sin(s1 * 18 + age * 1.4 + strand * Math.PI) * 0.42 * width(i + 1, thick);
                Vec3 a = p[i].add(side[i].scale(o0)), b = p[i + 1].add(side[i + 1].scale(o1));
                float al = (float) (0.55 + 0.45 * Math.sin(age * 0.9 + i * 0.5));
                ribbon(glow, m, cam, a, b, 0.022f, 1f, 0.92f, 0.45f, 0.85f * Mth.clamp(al, 0.3f, 1f));
            }
        }
        // faíscas ramificadas que saltam do corpo (mudam a cada 2 ticks)
        Random r = new Random(e.getId() * 31L + (long) (age * 0.5f) * 104729L);
        int branches = 3 + (int) (power * 6);
        for (int k = 0; k < branches; k++) {
            int idx = 2 + r.nextInt(N - 3);
            Vec3 a = p[idx];
            Vec3 dirB = side[idx].scale(r.nextBoolean() ? 1 : -1).add(v.scale(r.nextDouble() - 0.5)).normalize();
            double len = 0.25 + r.nextDouble() * 0.55;
            Vec3 prev = a;
            for (int j = 1; j <= 3; j++) {
                Vec3 nxt = a.add(dirB.scale(len * j / 3.0)).add((r.nextDouble() - 0.5) * 0.12, (r.nextDouble() - 0.5) * 0.12, (r.nextDouble() - 0.5) * 0.12);
                ribbon(glow, m, cam, prev, nxt, 0.03f * (1.2f - 0.3f * j), 1f, 0.9f, 0.4f, 0.9f - 0.2f * j);
                prev = nxt;
            }
        }

        // ---- brilho na ponta e na mão (billboards)
        Vector3f lf = camera.getLeftVector(), up = camera.getUpVector();
        float tipSize = (0.30f + 0.22f * power) * (st == WhipEntity.RETRACT ? 0.6f : 1f) * (0.85f + 0.15f * Mth.sin(age * 1.8f));
        billboard(glow, m, Vec3.ZERO, lf, up, tipSize * 1.6f, 1f, 0.8f, 0.12f, 0.35f);
        billboard(glow, m, Vec3.ZERO, lf, up, tipSize * 0.7f, 1f, 1f, 0.9f, 0.9f);
        if (st == WhipEntity.EXTEND && age < 7) {                  // clarão na mão quando dispara
            float k = 1f - age / 7f;
            Vec3 h = hand.subtract(tip);
            billboard(glow, m, h, lf, up, 0.9f * k + 0.2f, 1f, 0.85f, 0.2f, 0.5f * k);
            billboard(glow, m, h, lf, up, 0.35f * k + 0.1f, 1f, 1f, 0.95f, 0.9f * k);
        }
    }

    private static float width(int i, float thick) {
        float s = (float) i / N;
        return Mth.lerp(s * s * 0.6f + s * 0.4f, 0.17f, 0.04f) * thick;
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g, float bl, float al) {
        vtx(vc, m, a, r, g, bl, al); vtx(vc, m, b, r, g, bl, al); vtx(vc, m, c, r, g, bl, al); vtx(vc, m, d, r, g, bl, al);
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 p0, Vec3 p1, float w, float r, float g, float bl, float al) {
        Vec3 side = p1.subtract(p0).cross(p0.add(p1).scale(0.5).subtract(cam)).normalize().scale(w * 0.5);
        if (side.lengthSqr() < 1e-9) return;
        quad(vc, m, p0.add(side), p0.subtract(side), p1.subtract(side), p1.add(side), r, g, bl, al);
    }

    private static void billboard(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float s, float r, float g, float bl, float al) {
        Vec3 l = new Vec3(left.x, left.y, left.z).scale(s * 0.5), u = new Vec3(up.x, up.y, up.z).scale(s * 0.5);
        quad(vc, m, c.add(l).add(u), c.subtract(l).add(u), c.subtract(l).subtract(u), c.add(l).subtract(u), r, g, bl, al);
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, Mth.clamp(a, 0f, 1f));
    }
}
