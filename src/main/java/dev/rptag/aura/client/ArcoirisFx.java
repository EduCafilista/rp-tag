package dev.rptag.aura.client;

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
 * (5.69.0) O VISUAL DO {@code /arcoiris} (zip do pedido 124).
 *
 * <p>A casca colorida no corpo e da {@link AuraShellLayer}; aqui fica o que e do MUNDO:
 * <ul>
 *   <li><b>estrela de luz no peito</b>: 34 raios saindo em todas as direcoes (espalhados pelo
 *       angulo aureo), cada um numa cor do arco-iris, girando devagar;</li>
 *   <li><b>anel arco-iris no chao</b> + uma <b>onda branca</b> que expande;</li>
 *   <li>particulas: poeira colorida aleatoria, brilhos END_ROD e fagulhas de fogos.</li>
 * </ul>
 *
 * <p>So desenha pra quem esta com a aura arco-iris ligada; desligar apaga tudo. Tudo em
 * {@code try/catch}: erro aqui nunca derruba o jogo.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class ArcoirisFx {
    private static final float TAU = (float) (Math.PI * 2.0);

    /** true = esta com a aura arco-iris ligada. */
    public static boolean ativa(Player p) {
        try {
            return p.getData(AuraAnexos.AURA.get()) == AuraEstilo.ARCOIRIS.index();
        } catch (RuntimeException erro) {
            return false;
        }
    }

    /** Chamado pelo render do jogador (junto com a casca). */
    public static void render(RenderPlayerEvent.Post e, Player p, float t) {
        if (p.isSpectator() || p.isInvisible()) return;
        if (dev.rptag.client.FxQualidade.longe(p)) return;   // (5.77.0) OTIMIZACAO
        try {
            float sc = p.getScale();                 // acompanha o tamanho (Macaco 5x)
            e.getPoseStack().pushPose();
            e.getPoseStack().scale(sc, sc, sc);
            Matrix4f m = e.getPoseStack().last().pose();
            VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.lightning());
            estrelaDeLuz(vc, m, t);
            anelNoChao(vc, m, t);
            e.getPoseStack().popPose();
        } catch (RuntimeException erro) {
            // some com o efeito, nunca com o jogo
        }
    }

    /** Raios de luz em estrela saindo do peito, cada um numa cor do arco-iris. */
    private static void estrelaDeLuz(VertexConsumer vc, Matrix4f m, float t) {
        final int N = 34;
        final float AUREO = 2.399963f;               // espalha as direcoes na esfera
        final float oy = 1.0f;                       // altura do peito
        for (int k = 0; k < N; k++) {
            float y = 1f - (k + 0.5f) * 2f / N;
            float rr = Mth.sqrt(1f - y * y);
            float th = k * AUREO + t * 0.004f;
            float dx = Mth.cos(th) * rr, dy = y, dz = Mth.sin(th) * rr;
            // dois vetores perpendiculares (fitas cruzadas: visiveis de qualquer angulo)
            float p1x = -dz, p1z = dx, l = Mth.sqrt(p1x * p1x + p1z * p1z);
            if (l < 1e-3f) { p1x = 1f; p1z = 0f; l = 1f; }
            p1x /= l; p1z /= l;
            float p2x = dy * p1z, p2y = dz * p1x - dx * p1z, p2z = -dy * p1x;
            float fl = 0.6f + 0.4f * Mth.sin(t * 0.2f + k);
            float len = (1.6f + 2.2f * frac(Mth.sin(k * 12.9898f) * 437.585f)) * (0.7f + 0.3f * fl);
            float w = 0.09f, s0 = 0.45f;
            float[] c = AuraShellLayer.hsv((k / (float) N + t * 0.010f) % 1f, 0.80f);
            float a0 = 0.75f * fl;
            quad2(vc, m, cv(dx * s0 - p1x * w, oy + dy * s0, dz * s0 - p1z * w, c, a0),
                    cv(dx * s0 + p1x * w, oy + dy * s0, dz * s0 + p1z * w, c, a0),
                    cv(dx * len, oy + dy * len, dz * len, c, 0f),
                    cv(dx * len, oy + dy * len, dz * len, c, 0f));
            quad2(vc, m, cv(dx * s0 - p2x * w, oy + dy * s0 - p2y * w, dz * s0 - p2z * w, c, a0),
                    cv(dx * s0 + p2x * w, oy + dy * s0 + p2y * w, dz * s0 + p2z * w, c, a0),
                    cv(dx * len, oy + dy * len, dz * len, c, 0f),
                    cv(dx * len, oy + dy * len, dz * len, c, 0f));
        }
    }

    /** Anel arco-iris no chao + onda branca que expande. */
    private static void anelNoChao(VertexConsumer vc, Matrix4f m, float t) {
        final int N = 72;
        float y = 0.04f;
        for (int i = 0; i < N; i++) {
            float a0 = i * TAU / N, a1 = (i + 1) * TAU / N;
            float[] c0 = AuraShellLayer.hsv((a0 / TAU + t * 0.01f) % 1f, 0.85f);
            float[] c1 = AuraShellLayer.hsv((a1 / TAU + t * 0.01f) % 1f, 0.85f);
            float[][] bandas = {{1.20f, 1.30f, 0f, 0.8f}, {1.30f, 1.42f, 0.8f, 0f}};
            for (float[] b : bandas) {
                quad2(vc, m, cv(Mth.cos(a0) * b[0], y, Mth.sin(a0) * b[0], c0, b[2]),
                        cv(Mth.cos(a0) * b[1], y, Mth.sin(a0) * b[1], c0, b[3]),
                        cv(Mth.cos(a1) * b[1], y, Mth.sin(a1) * b[1], c1, b[3]),
                        cv(Mth.cos(a1) * b[0], y, Mth.sin(a1) * b[0], c1, b[2]));
            }
        }
        float f = (t * 0.02f) % 1f;
        float[] branco = {1f, 1f, 1f};
        for (int i = 0; i < N; i++) {
            float a0 = i * TAU / N, a1 = (i + 1) * TAU / N;
            float r0 = 2.6f * f, r1 = r0 + 0.08f, al = (1f - f) * 0.5f;
            quad2(vc, m, cv(Mth.cos(a0) * r0, y + 0.01f, Mth.sin(a0) * r0, branco, al),
                    cv(Mth.cos(a0) * r1, y + 0.01f, Mth.sin(a0) * r1, branco, al),
                    cv(Mth.cos(a1) * r1, y + 0.01f, Mth.sin(a1) * r1, branco, al),
                    cv(Mth.cos(a1) * r0, y + 0.01f, Mth.sin(a1) * r0, branco, al));
        }
    }

    /** Particulas: poeira colorida, brilho e fagulha de fogos. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        if (!p.level().isClientSide() || p.isSpectator()) return;
        if (!ativa(p)) return;
        try {
            RandomSource rnd = p.getRandom();
            float sc = p.getScale();
            for (int i = 0, n = dev.rptag.client.FxQualidade.particulas(3); i < n; i++) {   // (5.77.0)
                double ang = rnd.nextDouble() * TAU, rad = (0.35 + rnd.nextDouble() * 0.5) * sc;
                double x = p.getX() + Math.cos(ang) * rad, z = p.getZ() + Math.sin(ang) * rad;
                double y = p.getY() + rnd.nextDouble() * 1.9 * sc;
                double vy = (0.03 + rnd.nextDouble() * 0.05) * Math.sqrt(sc);
                float[] c = AuraShellLayer.hsv(rnd.nextFloat(), 0.85f);
                p.level().addParticle(new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), 1.2f),
                        x, y, z, 0, vy, 0);
                if (i == 0) {
                    p.level().addParticle(ParticleTypes.END_ROD, x, y, z,
                            rnd.nextGaussian() * 0.02, vy, rnd.nextGaussian() * 0.02);
                }
                if (i == 1 && rnd.nextFloat() < 0.5f) {
                    p.level().addParticle(ParticleTypes.FIREWORK, x, y, z,
                            rnd.nextGaussian() * 0.03, vy * 1.2, rnd.nextGaussian() * 0.03);
                }
            }
        } catch (RuntimeException erro) {
            // sem particula, mas sem crash
        }
    }

    // ------------------------------------------------------------------ helpers
    private static float frac(float x) { return x - (float) Math.floor(x); }

    private static float[] cv(float x, float y, float z, float[] c, float a) {
        return new float[]{x, y, z, c[0], c[1], c[2], a};
    }

    private static void ev(VertexConsumer vc, Matrix4f m, float[] v) {
        vc.addVertex(m, v[0], v[1], v[2]).setColor(v[3], v[4], v[5], Mth.clamp(v[6], 0f, 1f));
    }

    private static void quad1(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d) {
        ev(vc, m, a); ev(vc, m, b); ev(vc, m, c); ev(vc, m, d);
    }

    private static void quad2(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d) {
        quad1(vc, m, a, b, c, d);
        ev(vc, m, d); ev(vc, m, c); ev(vc, m, b); ev(vc, m, a);
    }

    private ArcoirisFx() {}
}
