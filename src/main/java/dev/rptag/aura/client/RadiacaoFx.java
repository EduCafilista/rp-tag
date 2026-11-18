package dev.rptag.aura.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.aura.AuraAnexos;
import dev.rptag.aura.AuraEstilo;
import dev.rptag.aura.Radiacao;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * (5.67.0) O VISUAL DO {@code /radiacao} (o zip novo do pedido 122).
 *
 * <p>A casca azul no corpo e da {@link AuraShellLayer}; aqui fica o que e do MUNDO:
 * <ul>
 *   <li>o <b>anel ciano</b> no chao marcando o raio da zona ({@link Radiacao#RAIO}), acompanhando
 *       o relevo, com pulsos correndo por ele e uma parede suave subindo;</li>
 *   <li>3 <b>rostos espectrais</b> de chamas azuis orbitando o jogador (olhos em fenda e dentes);</li>
 *   <li>as <b>linguas de chamas cianas</b> lambendo o corpo;</li>
 *   <li>as particulas: fogo de alma subindo e poeira ciano flutuando por toda a zona.</li>
 * </ul>
 *
 * <p>So desenha pra quem esta com a aura de radiacao ligada (anexo = {@code RADIACAO}) — e
 * desligar apaga tudo. Tudo em {@code try/catch}: erro aqui nunca derruba o jogo.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class RadiacaoFx {
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float[] CIANO = {0.20f, 0.85f, 1.00f};
    private static final float[] CIANO0 = {0.85f, 1.00f, 1.00f}, CIANO1 = {0.10f, 0.45f, 1.00f};
    /** (5.77.0) OTIMIZACAO: poeira ciano reaproveitada (antes era `new` por particula). */
    private static final net.minecraft.core.particles.DustParticleOptions POEIRA_CIANO =
            new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.2f, 0.85f, 1f), 1.0f);

    /** true = esta com a aura de radiacao ligada. */
    public static boolean ativa(Player p) {
        try {
            return p.getData(AuraAnexos.AURA.get()) == AuraEstilo.RADIACAO.index();
        } catch (RuntimeException erro) {
            return false;
        }
    }

    /** Chamado pelo render do jogador (junto com a casca). */
    public static void render(RenderPlayerEvent.Post e, Player p, float t) {
        if (p.isSpectator() || p.isInvisible()) return;
        if (dev.rptag.client.FxQualidade.longe(p)) return;   // (5.77.0) OTIMIZACAO
        try {
            // anel da zona: tamanho fixo no mundo, nao escala com o corpo
            e.getPoseStack().pushPose();
            anelDaZona(e.getMultiBufferSource().getBuffer(RenderType.lightning()),
                    e.getPoseStack().last().pose(), p, t);
            e.getPoseStack().popPose();

            // chamas + rostos acompanham o tamanho do corpo (Macaco 5x)
            float sc = p.getScale();
            e.getPoseStack().pushPose();
            e.getPoseStack().scale(sc, sc, sc);
            Matrix4f m = e.getPoseStack().last().pose();
            VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.lightning());
            chamas(vc, m, t, CIANO0, CIANO1, 36, 1.5f);
            rostos(vc, m, t);
            e.getPoseStack().popPose();
        } catch (RuntimeException erro) {
            // some com o efeito, nunca com o jogo
        }
    }

    /** Anel ciano no chao marcando o raio da radiacao (segue o relevo) + parede suave. */
    private static void anelDaZona(VertexConsumer vc, Matrix4f m, Player p, float t) {
        // (5.77.0) OTIMIZACAO: anel de altura em cache (era 96 consultas de heightmap por
        // quadro por jogador; agora recalcula a cada 4 ticks ou quando o jogador anda 1 bloco).
        final int N = dev.rptag.client.FxAlturas.N;
        final float R = (float) Radiacao.RAIO;
        float[] ys = dev.rptag.client.FxAlturas.anel(p, R);
        for (int i = 0; i < N; i++) {
            int j = i + 1;
            float a0 = i * TAU / N, a1 = j * TAU / N;
            float al = 0.30f + 0.25f * Mth.sin(a0 * 24f - t * 0.2f);       // pulsos correndo pelo anel
            // (5.77.0) OTIMIZACAO: antes era `for (float[] lado : new float[][]{...})` — dois
            // arrays novos a cada passo do anel, a cada quadro. Agora as duas faixas (dentro e
            // fora) saem na mao, sem alocar nada.
            float r0 = R - 0.14f, r1 = R;
            quad2(vc, m, cv(Mth.cos(a0) * r0, ys[i], Mth.sin(a0) * r0, CIANO, 0f),
                    cv(Mth.cos(a0) * r1, ys[i], Mth.sin(a0) * r1, CIANO, al),
                    cv(Mth.cos(a1) * r1, ys[j], Mth.sin(a1) * r1, CIANO, al),
                    cv(Mth.cos(a1) * r0, ys[j], Mth.sin(a1) * r0, CIANO, 0f));
            quad2(vc, m, cv(Mth.cos(a0) * r1, ys[i], Mth.sin(a0) * r1, CIANO, al),
                    cv(Mth.cos(a0) * (R + 0.14f), ys[i], Mth.sin(a0) * (R + 0.14f), CIANO, 0f),
                    cv(Mth.cos(a1) * (R + 0.14f), ys[j], Mth.sin(a1) * (R + 0.14f), CIANO, 0f),
                    cv(Mth.cos(a1) * r1, ys[j], Mth.sin(a1) * r1, CIANO, al));
            quad2(vc, m, cv(Mth.cos(a0) * R, ys[i], Mth.sin(a0) * R, CIANO, 0.20f),
                    cv(Mth.cos(a1) * R, ys[j], Mth.sin(a1) * R, CIANO, 0.20f),
                    cv(Mth.cos(a1) * R, ys[j] + 1.4f, Mth.sin(a1) * R, CIANO, 0f),
                    cv(Mth.cos(a0) * R, ys[i] + 1.4f, Mth.sin(a0) * R, CIANO, 0f));
        }
    }

    /** Rostos espectrais de chamas azuis orbitando o jogador: olhos em fenda e dentes serrilhados. */
    private static void rostos(VertexConsumer vc, Matrix4f m, float t) {
        float[] brilho = {0.15f, 0.70f, 1.00f}, claro = {0.90f, 1.00f, 1.00f};
        for (int k = 0; k < 3; k++) {
            float ang = t * 0.012f + k * TAU / 3f;
            float ca = Mth.cos(ang), sa = Mth.sin(ang);
            float S = 1.15f + 0.15f * Mth.sin(t * 0.07f + k * 2f);
            float cx = ca * 1.55f, cz = sa * 1.55f;
            float cy = 1.3f + 0.45f * Mth.sin(t * 0.03f + k * 2.1f);
            float fl = 0.65f + 0.35f * Mth.sin(t * 0.4f + k * 1.7f);
            float ux = -sa, uz = ca;
            // cabeca do espirito
            for (int i = 0; i < 12; i++) {
                float a0 = i * TAU / 12, a1 = (i + 1) * TAU / 12;
                quad2(vc, m, fp(cx, cy, cz, ux, uz, S, 0, 0, brilho, 0.30f * fl),
                        fp(cx, cy, cz, ux, uz, S, 0, 0, brilho, 0.30f * fl),
                        fp(cx, cy, cz, ux, uz, S, Mth.cos(a1) * 0.55f, Mth.sin(a1) * 0.65f, brilho, 0f),
                        fp(cx, cy, cz, ux, uz, S, Mth.cos(a0) * 0.55f, Mth.sin(a0) * 0.65f, brilho, 0f));
            }
            // olhos em fenda (inclinados, bravos)
            for (int lado = -1; lado <= 1; lado += 2) {
                quad2(vc, m, fp(cx, cy, cz, ux, uz, S, lado * 0.30f, 0.14f, claro, 0.95f * fl),
                        fp(cx, cy, cz, ux, uz, S, lado * 0.06f, 0.00f, claro, 0.95f * fl),
                        fp(cx, cy, cz, ux, uz, S, lado * 0.06f, 0.07f, claro, 0.95f * fl),
                        fp(cx, cy, cz, ux, uz, S, lado * 0.30f, 0.22f, claro, 0.95f * fl));
            }
            // dentes: serra de cima (apontam pra baixo) e de baixo (apontam pra cima)
            for (int i = 0; i < 7; i++) {
                float x = -0.30f + i * 0.10f;
                quad2(vc, m, fp(cx, cy, cz, ux, uz, S, x - 0.05f, -0.14f, claro, 0.85f * fl),
                        fp(cx, cy, cz, ux, uz, S, x + 0.05f, -0.14f, claro, 0.85f * fl),
                        fp(cx, cy, cz, ux, uz, S, x, -0.28f, brilho, 0.85f * fl),
                        fp(cx, cy, cz, ux, uz, S, x, -0.28f, brilho, 0.85f * fl));
            }
            for (int i = 0; i < 6; i++) {
                float x = -0.25f + i * 0.10f;
                quad2(vc, m, fp(cx, cy, cz, ux, uz, S, x - 0.05f, -0.40f, claro, 0.85f * fl),
                        fp(cx, cy, cz, ux, uz, S, x + 0.05f, -0.40f, claro, 0.85f * fl),
                        fp(cx, cy, cz, ux, uz, S, x, -0.26f, brilho, 0.85f * fl),
                        fp(cx, cy, cz, ux, uz, S, x, -0.26f, brilho, 0.85f * fl));
            }
        }
    }

    /** Linguas de chama: fitas cruzadas subindo ondulando, branco-ciano na base -> azul na ponta. */
    private static void chamas(VertexConsumer vc, Matrix4f m, float t, float[] c0, float[] c1,
                               int N, float lenMul) {
        final int M = 7;
        for (int k = 0; k < N; k++) {
            float seed = k * 12.9898f;
            float f = frac(Mth.sin(seed) * 43758.55f);
            float a = k * TAU / N + f * 0.5f;
            float y0 = 0.1f + ((k * 0.37f) % 1f) * 1.6f;
            float len = (0.55f + 0.55f * frac(Mth.sin(seed * 0.7f) * 9631.1f)) * lenMul;
            float r0 = 0.42f + 0.06f * f;
            float flick = 0.7f + 0.3f * Mth.sin(t * 0.5f + k * 1.3f);
            float[][] P = new float[M + 1][];
            for (int i = 0; i <= M; i++) {
                float u = i / (float) M;
                float ang = a + 0.35f * Mth.sin(t * 0.22f + k + u * 3.5f);
                float rad = r0 + u * 0.28f;
                float ca = Mth.cos(ang), sa = Mth.sin(ang);
                float w = 0.075f * (float) Math.pow(1f - u, 0.9f);
                float al = 0.95f * (1f - u) * flick;
                P[i] = new float[]{ca * rad, y0 + u * len * flick, sa * rad, -sa, ca, ca, sa, w, al,
                        Mth.lerp(u, c0[0], c1[0]), Mth.lerp(u, c0[1], c1[1]), Mth.lerp(u, c0[2], c1[2])};
            }
            for (int i = 0; i < M; i++) {
                float[] p = P[i], q = P[i + 1];
                quad2(vc, m, pv(p, -p[3] * p[7], 0, -p[4] * p[7]), pv(p, p[3] * p[7], 0, p[4] * p[7]),
                        pv(q, q[3] * q[7], 0, q[4] * q[7]), pv(q, -q[3] * q[7], 0, -q[4] * q[7]));
                quad2(vc, m, pv(p, -p[5] * p[7], 0, -p[6] * p[7]), pv(p, p[5] * p[7], 0, p[6] * p[7]),
                        pv(q, q[5] * q[7], 0, q[6] * q[7]), pv(q, -q[5] * q[7], 0, -q[6] * q[7]));
            }
        }
    }

    /** Particulas: fogo de alma subindo + poeira ciano flutuando por toda a zona. */
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
                p.level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0, vy * 0.6, 0);
                if (i == 0) {
                    double za = rnd.nextDouble() * TAU, zr = Math.sqrt(rnd.nextDouble()) * Radiacao.RAIO;
                    p.level().addParticle(POEIRA_CIANO,
                            p.getX() + Math.cos(za) * zr, p.getY() + rnd.nextDouble() * 2.5,
                            p.getZ() + Math.sin(za) * zr, 0, 0.02, 0);
                }
            }
        } catch (RuntimeException erro) {
            // sem particula, mas sem crash
        }
    }

    // ------------------------------------------------------------------ helpers
    private static float frac(float x) { return x - (float) Math.floor(x); }

    /** Vertice de fogo: posicao + offset, cor/alfa do ponto. */
    private static float[] pv(float[] p, float ox, float oy, float oz) {
        return new float[]{p[0] + ox, p[1] + oy, p[2] + oz, p[9], p[10], p[11], p[8]};
    }

    /** Vertice colorido (posicao absoluta). */
    private static float[] cv(float x, float y, float z, float[] c, float a) {
        return new float[]{x, y, z, c[0], c[1], c[2], a};
    }

    /** Ponto do rosto: plano tangente (a) + vertical (b) ao redor do centro, escala s. */
    private static float[] fp(float cx, float cy, float cz, float ux, float uz, float s,
                              float a, float b, float[] c, float al) {
        return new float[]{cx + ux * a * s, cy + b * s, cz + uz * a * s, c[0], c[1], c[2], al};
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

    private RadiacaoFx() {}
}
