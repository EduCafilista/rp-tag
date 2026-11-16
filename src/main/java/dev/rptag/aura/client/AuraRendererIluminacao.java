package dev.rptag.aura.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.aura.AuraAnexos;
import dev.rptag.aura.AuraEstilo;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * (5.60.0) A AURA DE ILUMINACAO (pedido do dono): círculo mágico no chão (anel de runas + hexagrama girando),
 * coluna de mana em chamas, hélices de luz subindo, anéis ascendentes e partículas.
 * Tudo procedural (sem texturas), desenhado em RenderType.lightning() (brilho aditivo).
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class AuraRendererIluminacao {
    private static final float TAU = (float) (Math.PI * 2.0);

    // ------------------------------------------------------------------ render
    @SubscribeEvent
    public static void onRender(RenderPlayerEvent.Post e) {
        Player p = e.getEntity();
        if (p.isSpectator() || p.isInvisible()) return;
        // (5.60.0) a aura mora no ANEXO do jogador: ligada = desenha
        if (!AuraAnexos.ligada(p)) return;

        AuraEstilo s = AuraEstilo.ILUMINACAO;
        float t = p.tickCount + e.getPartialTick();
        float pulse = 0.85f + 0.15f * Mth.sin(t * 0.15f);
        float h = p.getBbHeight() + 0.9f;

        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.lightning());

        magicCircle(vc, m, s, t, pulse);
        helix(vc, m, s, t, pulse, h);
        risingRings(vc, m, s, t, pulse, h);
        // (5.60.0) os raios e os PORTES DA BABILONIA sao a assinatura desta aura
        rays(vc, m, s, t, pulse, h);
        gates(vc, m, s, t, pulse);
        // colunas por último (maiores e translúcidas)
        column(vc, m, 0.95f, h, t, 32, s.r, s.g, s.b, 0.30f * pulse);
        column(vc, m, 0.60f, h * 0.9f, t + 40f, 24, s.r2, s.g2, s.b2, 0.22f * pulse);

        ps.popPose();
    }

    // ------------------------------------------------------------ círculo mágico
    private static void magicCircle(VertexConsumer vc, Matrix4f m, AuraEstilo s, float t, float pulse) {
        float y = 0.03f;

        glowDisc(vc, m, 2.0f, y, 48, s.r, s.g, s.b, 0.35f * pulse);

        // anéis externos
        arc(vc, m, 1.92f, 2.00f, y, 0, TAU, 64, s.r, s.g, s.b, 0.95f * pulse);
        arc(vc, m, 1.76f, 1.80f, y + 0.002f, 0, TAU, 64, s.r2, s.g2, s.b2, 0.70f * pulse);

        // faixa de runas (24 blocos girando ao contrário)
        float rot = -t * 0.012f;
        float step = TAU / 24f;
        for (int k = 0; k < 24; k++) {
            float a0 = rot + k * step;
            if ((k & 1) == 0) {
                arc(vc, m, 1.50f, 1.70f, y + 0.002f, a0, a0 + step * 0.55f, 2, s.r, s.g, s.b, 0.55f * pulse);
            } else {
                arc(vc, m, 1.60f, 1.70f, y + 0.002f, a0, a0 + step * 0.35f, 2, s.r2, s.g2, s.b2, 0.70f * pulse);
                arc(vc, m, 1.50f, 1.53f, y + 0.002f, a0, a0 + step * 0.70f, 2, s.r, s.g, s.b, 0.50f * pulse);
            }
        }

        // hexagrama girando
        float R = 1.42f, hr = t * 0.012f;
        for (int i = 0; i < 6; i++) {
            int j = (i + 2) % 6;
            float xa = Mth.cos(hr + i * TAU / 6f) * R, za = Mth.sin(hr + i * TAU / 6f) * R;
            float xb = Mth.cos(hr + j * TAU / 6f) * R, zb = Mth.sin(hr + j * TAU / 6f) * R;
            groundLine(vc, m, xa, za, xb, zb, 0.07f, y + 0.004f, s.r2, s.g2, s.b2, 0.85f * pulse);
        }

        // anel interno tracejado girando ao contrário do hexagrama
        for (int k = 0; k < 12; k++) {
            float a0 = t * 0.02f + k * TAU / 12f;
            arc(vc, m, 0.70f, 0.76f, y + 0.004f, a0, a0 + TAU / 12f * 0.6f, 3, s.r, s.g, s.b, 0.75f * pulse);
        }
        arc(vc, m, 0.30f, 0.33f, y + 0.004f, 0, TAU, 32, s.r2, s.g2, s.b2, 0.60f * pulse);

        // onda que expande do centro
        float f = (t * 0.02f) % 1f;
        arc(vc, m, 2.0f * f, 2.0f * f + 0.05f, y + 0.006f, 0, TAU, 48, s.r2, s.g2, s.b2, (1f - f) * 0.5f);
    }

    // --------------------------------------------------------- coluna de mana
    private static void column(VertexConsumer vc, Matrix4f m, float rad, float h, float t, int seg,
                               float cr, float cg, float cb, float aBase) {
        for (int i = 0; i < seg; i++) {
            float u0 = i * TAU / seg, u1 = (i + 1) * TAU / seg;
            float w0 = 1f + 0.12f * Mth.sin(u0 * 5f + t * 0.20f);
            float w1 = 1f + 0.12f * Mth.sin(u1 * 5f + t * 0.20f);
            float h0 = h * (0.88f + 0.12f * Mth.sin(u0 * 3f - t * 0.17f));
            float h1 = h * (0.88f + 0.12f * Mth.sin(u1 * 3f - t * 0.17f));
            float top = 0.55f; // afunila no topo, como chama
            quad(vc, m,
                    pt(Mth.cos(u0) * rad * w0, 0.03f, Mth.sin(u0) * rad * w0, aBase),
                    pt(Mth.cos(u1) * rad * w1, 0.03f, Mth.sin(u1) * rad * w1, aBase),
                    pt(Mth.cos(u1) * rad * w1 * top, h1, Mth.sin(u1) * rad * w1 * top, 0f),
                    pt(Mth.cos(u0) * rad * w0 * top, h0, Mth.sin(u0) * rad * w0 * top, 0f),
                    cr, cg, cb);
        }
    }

    // ------------------------------------------------------- hélices de luz
    private static void helix(VertexConsumer vc, Matrix4f m, AuraEstilo s, float t, float pulse, float h) {
        int n = 40;
        for (int k = 0; k < 3; k++) {
            for (int i = 0; i < n; i++) {
                float f0 = i / (float) n, f1 = (i + 1) / (float) n;
                float a0 = t * 0.09f + f0 * 7f + k * TAU / 3f;
                float a1 = t * 0.09f + f1 * 7f + k * TAU / 3f;
                float r0 = 0.80f - f0 * 0.15f, r1 = 0.80f - f1 * 0.15f;
                float y0 = f0 * h, y1 = f1 * h;
                float w = 0.07f * (1f - f0 * 0.6f);
                float al0 = (1f - f0) * 0.9f * pulse, al1 = (1f - f1) * 0.9f * pulse;
                quad(vc, m,
                        pt(Mth.cos(a0) * r0, y0 - w, Mth.sin(a0) * r0, al0),
                        pt(Mth.cos(a0) * r0, y0 + w, Mth.sin(a0) * r0, al0),
                        pt(Mth.cos(a1) * r1, y1 + w, Mth.sin(a1) * r1, al1),
                        pt(Mth.cos(a1) * r1, y1 - w, Mth.sin(a1) * r1, al1),
                        s.r2, s.g2, s.b2);
            }
        }
    }

    // ------------------------------------------------------ anéis ascendentes
    private static void risingRings(VertexConsumer vc, Matrix4f m, AuraEstilo s, float t, float pulse, float h) {
        for (int k = 0; k < 4; k++) {
            float f = (t * 0.012f + k * 0.25f) % 1f;
            float y = f * h;
            float rad = 0.90f - f * 0.25f;
            float alpha = (1f - f) * Math.min(1f, f * 8f) * 0.8f * pulse;
            band(vc, m, rad, y, 0.035f, 36, s.r, s.g, s.b, alpha);
        }
    }


    // ------------------------------------------- Gilgamesh: raios de luz dourada
    private static void rays(VertexConsumer vc, Matrix4f m, AuraEstilo s, float t, float pulse, float h) {
        for (int i = 0; i < 16; i++) {
            float a = i * TAU / 16f + t * 0.004f;
            float r = 1.0f + 0.9f * ((i * 37) % 10) / 10f;
            float hh = h * (1f + 0.8f * Math.abs(Mth.sin(i * 3f + t * 0.05f)));
            float x = Mth.cos(a) * r, z = Mth.sin(a) * r;
            float tx = -Mth.sin(a) * 0.035f, tz = Mth.cos(a) * 0.035f;
            float al = 0.4f * (0.5f + 0.5f * Mth.sin(t * 0.1f + i * 1.7f)) * pulse;
            quad(vc, m, pt(x - tx, 0.03f, z - tz, al), pt(x + tx, 0.03f, z + tz, al),
                    pt(x + tx * .2f, hh, z + tz * .2f, 0f), pt(x - tx * .2f, hh, z - tz * .2f, 0f), s.r, s.g, s.b);
        }
    }

    // ------------------------------- Gilgamesh: Portões da Babilônia com armas saindo
    private record Fr(float gx, float gy, float gz, float ux, float uz, float nx, float nz) {
        float[] p(float a, float b, float c, float al) {
            return pt(gx + ux * a + nx * c, gy + b, gz + uz * a + nz * c, al);
        }
    }

    private static void gRing(VertexConsumer vc, Matrix4f m, Fr f, float r0, float r1, float a0, float a1,
                              int seg, float cr, float cg, float cb, float al) {
        for (int i = 0; i < seg; i++) {
            float u0 = a0 + (a1 - a0) * i / seg, u1 = a0 + (a1 - a0) * (i + 1) / seg;
            quad(vc, m, f.p(Mth.cos(u0) * r0, Mth.sin(u0) * r0, 0, al), f.p(Mth.cos(u0) * r1, Mth.sin(u0) * r1, 0, al),
                    f.p(Mth.cos(u1) * r1, Mth.sin(u1) * r1, 0, al), f.p(Mth.cos(u1) * r0, Mth.sin(u1) * r0, 0, al), cr, cg, cb);
        }
    }

    private static void gates(VertexConsumer vc, Matrix4f m, AuraEstilo s, float t, float pulse) {
        for (int k = 0; k < 5; k++) {
            float th = t * 0.006f + k * TAU / 5f;
            float cx = Mth.cos(th), sz = Mth.sin(th);
            Fr f = new Fr(cx * 2.3f, 1.4f + 0.45f * Mth.sin(k * 2f + t * 0.03f), sz * 2.3f, sz, -cx, -cx, -sz);
            float dir = (k % 2 == 0) ? -1f : 1f;

            // brilho do portal
            for (int i = 0; i < 24; i++) {
                float u0 = i * TAU / 24f, u1 = (i + 1) * TAU / 24f;
                quad(vc, m, f.p(0, 0, 0, 0.55f * pulse), f.p(0, 0, 0, 0.55f * pulse),
                        f.p(Mth.cos(u1) * .85f, Mth.sin(u1) * .85f, 0, 0), f.p(Mth.cos(u0) * .85f, Mth.sin(u0) * .85f, 0, 0),
                        s.r, s.g, s.b);
            }
            gRing(vc, m, f, 0.50f, 0.57f, 0, TAU, 40, s.r, s.g, s.b, 0.95f * pulse);
            gRing(vc, m, f, 0.62f, 0.64f, 0, TAU, 40, s.r2, s.g2, s.b2, 0.60f * pulse);
            for (int j = 0; j < 8; j++) {
                float a0 = t * 0.05f * dir + j * TAU / 8f;
                gRing(vc, m, f, 0.38f, 0.43f, a0, a0 + TAU / 8f * 0.6f, 3, s.r2, s.g2, s.b2, 0.85f * pulse);
            }
            // octagrama girando
            for (int j = 0; j < 8; j++) {
                float aa = -t * 0.03f * dir + j * TAU / 8f, bb = -t * 0.03f * dir + ((j + 3) % 8) * TAU / 8f;
                float x0 = Mth.cos(aa) * .34f, y0 = Mth.sin(aa) * .34f, x1 = Mth.cos(bb) * .34f, y1 = Mth.sin(bb) * .34f;
                float l = Mth.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
                float nx = -(y1 - y0) / l * .015f, ny = (x1 - x0) / l * .015f, al = 0.9f * pulse;
                quad(vc, m, f.p(x0 + nx, y0 + ny, 0, al), f.p(x0 - nx, y0 - ny, 0, al),
                        f.p(x1 - nx, y1 - ny, 0, al), f.p(x1 + nx, y1 + ny, 0, al), s.r2, s.g2, s.b2);
            }
            // ondulação expandindo (a "água dourada" do portal)
            float rip = (t * 0.03f + k * 0.2f) % 1f;
            gRing(vc, m, f, 0.57f + rip * 0.4f, 0.60f + rip * 0.4f, 0, TAU, 40, s.r2, s.g2, s.b2, (1f - rip) * 0.55f);

            // arma saindo do portal (espada / lança alternadas)
            float ext = Math.max(0f, Mth.sin(t * 0.05f + k * 1.7f));
            if (ext > 0.02f) {
                boolean spear = (k % 2) == 1;
                float L = (spear ? 2.0f : 1.5f) * ext, w = spear ? 0.045f : 0.08f;
                quad(vc, m, f.p(0, -w * 3, 0, 0), f.p(0, w * 3, 0, 0), f.p(0, w * 3, L, .5f * ext), f.p(0, -w * 3, L, .5f * ext), s.r, s.g, s.b);
                quad(vc, m, f.p(0, -w, .05f, 1), f.p(0, w, .05f, 1), f.p(0, w, L * .78f, 1), f.p(0, -w, L * .78f, 1), s.r2, s.g2, s.b2);
                quad(vc, m, f.p(0, -w, L * .78f, 1), f.p(0, w, L * .78f, 1), f.p(0, 0, L, 1), f.p(0, 0, L, 1), s.r2, s.g2, s.b2);
                quad(vc, m, f.p(0, -w * 2.4f, .05f, .9f), f.p(0, w * 2.4f, .05f, .9f), f.p(0, w * 2.4f, .12f, .9f), f.p(0, -w * 2.4f, .12f, .9f), s.r, s.g, s.b);
            }
        }
    }

    // ------------------------------------------------------------ partículas
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        if (!p.level().isClientSide() || p.isSpectator()) return;
        if (!AuraAnexos.ligada(p)) return;

        AuraEstilo s = AuraEstilo.ILUMINACAO;
        RandomSource rnd = p.getRandom();
        int n = 4;
        for (int i = 0; i < n; i++) {
            double ang = rnd.nextDouble() * TAU;
            double rad = 0.4 + rnd.nextDouble() * 1.4;
            double x = p.getX() + Math.cos(ang) * rad;
            double z = p.getZ() + Math.sin(ang) * rad;
            p.level().addParticle(new DustParticleOptions(new Vector3f(s.r, s.g, s.b), 1.2f),
                    x, p.getY() + 0.05, z, 0, 0.04 + rnd.nextDouble() * 0.05, 0);
        }
        if (rnd.nextFloat() < 0.7f) {
            double ang = rnd.nextDouble() * TAU;
            double rad = 0.3 + rnd.nextDouble() * 0.8;
            p.level().addParticle(ParticleTypes.END_ROD,
                    p.getX() + Math.cos(ang) * rad, p.getY() + rnd.nextDouble() * 1.2, p.getZ() + Math.sin(ang) * rad,
                    0, 0.03 + rnd.nextDouble() * 0.03, 0);
        }
    }

    // ------------------------------------------------------------- helpers
    private static float[] pt(float x, float y, float z, float a) { return new float[]{x, y, z, a}; }

    private static void emit(VertexConsumer vc, Matrix4f m, float[] p, float r, float g, float b) {
        vc.addVertex(m, p[0], p[1], p[2]).setColor(r, g, b, Mth.clamp(p[3], 0f, 1f));
    }

    /** Desenha o quad nas duas faces (o RenderType faz culling). */
    private static void quad(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d,
                             float r, float g, float bl) {
        emit(vc, m, a, r, g, bl); emit(vc, m, b, r, g, bl); emit(vc, m, c, r, g, bl); emit(vc, m, d, r, g, bl);
        emit(vc, m, d, r, g, bl); emit(vc, m, c, r, g, bl); emit(vc, m, b, r, g, bl); emit(vc, m, a, r, g, bl);
    }

    /** Arco de anel no plano XZ. */
    private static void arc(VertexConsumer vc, Matrix4f m, float r0, float r1, float y,
                            float a0, float a1, int seg, float r, float g, float b, float alpha) {
        for (int i = 0; i < seg; i++) {
            float u0 = a0 + (a1 - a0) * i / seg, u1 = a0 + (a1 - a0) * (i + 1) / seg;
            quad(vc, m,
                    pt(Mth.cos(u0) * r0, y, Mth.sin(u0) * r0, alpha),
                    pt(Mth.cos(u0) * r1, y, Mth.sin(u0) * r1, alpha),
                    pt(Mth.cos(u1) * r1, y, Mth.sin(u1) * r1, alpha),
                    pt(Mth.cos(u1) * r0, y, Mth.sin(u1) * r0, alpha),
                    r, g, b);
        }
    }

    /** Disco com brilho forte no centro e transparente na borda. */
    private static void glowDisc(VertexConsumer vc, Matrix4f m, float rad, float y, int seg,
                                 float r, float g, float b, float alphaCenter) {
        for (int i = 0; i < seg; i++) {
            float u0 = i * TAU / seg, u1 = (i + 1) * TAU / seg;
            quad(vc, m,
                    pt(0, y, 0, alphaCenter), pt(0, y, 0, alphaCenter),
                    pt(Mth.cos(u1) * rad, y, Mth.sin(u1) * rad, 0f),
                    pt(Mth.cos(u0) * rad, y, Mth.sin(u0) * rad, 0f),
                    r, g, b);
        }
    }

    /** Linha achatada no chão. */
    private static void groundLine(VertexConsumer vc, Matrix4f m, float x0, float z0, float x1, float z1,
                                   float w, float y, float r, float g, float b, float a) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 1e-4f) return;
        float nx = -dz / len * w * 0.5f, nz = dx / len * w * 0.5f;
        quad(vc, m,
                pt(x0 + nx, y, z0 + nz, a), pt(x0 - nx, y, z0 - nz, a),
                pt(x1 - nx, y, z1 - nz, a), pt(x1 + nx, y, z1 + nz, a),
                r, g, b);
    }

    /** Faixa vertical (anel visto de qualquer ângulo). */
    private static void band(VertexConsumer vc, Matrix4f m, float rad, float y, float half, int seg,
                             float r, float g, float b, float alpha) {
        for (int i = 0; i < seg; i++) {
            float u0 = i * TAU / seg, u1 = (i + 1) * TAU / seg;
            quad(vc, m,
                    pt(Mth.cos(u0) * rad, y - half, Mth.sin(u0) * rad, alpha),
                    pt(Mth.cos(u0) * rad, y + half, Mth.sin(u0) * rad, alpha),
                    pt(Mth.cos(u1) * rad, y + half, Mth.sin(u1) * rad, alpha),
                    pt(Mth.cos(u1) * rad, y - half, Mth.sin(u1) * rad, alpha),
                    r, g, b);
        }
    }

    private AuraRendererIluminacao() {}
}
