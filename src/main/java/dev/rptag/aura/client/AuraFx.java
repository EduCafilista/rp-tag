package dev.rptag.aura.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.aura.AuraAnexos;
import dev.rptag.aura.AuraEstilo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * (5.64.0) OS DETALHES EM VOLTA DO CORPO, alem do contorno da {@link AuraShellLayer}.
 *
 * <p>No zip original tinha fogo dourado (GILGAMESH) e tinta preta (VOID). <b>Aqui ficou so a
 * TINTA do VAZIO:</b> o dono ja tinha pedido (pedido 118) pra tirar os efeitos em volta da
 * iluminacao e deixar so a aura brilhante — entao o dourado nao ganha linguas de fogo.
 *
 * <p>VAZIO: tentaculos de tinta negra rasgados com espinhos + mancha de sombra no chao +
 * fumaça de tinta. E a "animacao de vazio" que substitui a antiga.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class AuraFx {
    private static final float TAU = (float) (Math.PI * 2.0);

    @EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers e) {
            for (PlayerSkin.Model skin : e.getSkins()) {
                Object r = e.getSkin(skin);
                if (r instanceof PlayerRenderer pr) pr.addLayer(new AuraShellLayer(pr));
            }
        }
    }

    /** (5.64.0) este jogador esta com a aura do VAZIO? (anexo = vazio, ou a aura antiga acesa) */
    public static boolean ehVazio(Player p) {
        // (5.66.0) vazio = /vazio ligar (nada de comando novo); /vazio desligar apaga tudo
        int id = p.getData(AuraAnexos.AURA.get());
        if (id == AuraEstilo.ILUMINACAO.index()) return false;
        if (id == AuraEstilo.RADIACAO.index()) return false;   // (5.67.0) radiacao tem o dela
        if (id == AuraEstilo.ARCOIRIS.index()) return false;   // (5.69.0) arco-iris tem o dele
        return dev.rptag.client.VazioAuraFx.ativa(p);
    }

    // ------------------------------------------------------------------ mundo
    /** Desenha os tentaculos de tinta + a mancha de sombra (so a aura do VAZIO). */
    public static void renderVazio(RenderPlayerEvent.Post e, Player p, float t) {
        if (p.isSpectator() || p.isInvisible()) return;
        try {
            PoseStack ps = e.getPoseStack();
            float sc = p.getScale();                   // acompanha o tamanho (Macaco 5x)
            ps.pushPose();
            ps.scale(sc, sc, sc);
            ink(e.getMultiBufferSource().getBuffer(AuraRenderTypes.INK), ps.last().pose(), t);
            ps.popPose();
        } catch (RuntimeException erro) {
            // regra de ouro do mod: erro aqui some com o efeito, nunca com o jogo
        }
    }

    /**
     * (5.73.0) O VISUAL DA GRAVIDADE (zip do pedido 130): anel do campo no chao (cor por modo,
     * com ondas entrando/saindo em atrair/repelir), aneis de bolhas, fissuras no ar e fumaca
     * conforme o modo (pesada/leve). Sempre preso ao jogador.
     */
    public static void renderGravidade(RenderPlayerEvent.Post e, Player p, float t) {
        if (p.isSpectator() || p.isInvisible()) return;
        try {
            int bits = dev.rptag.aura.GravState.get(p);
            PoseStack ps = e.getPoseStack();
            MultiBufferSource buf = e.getMultiBufferSource();
            int campo = dev.rptag.aura.GravState.field(bits);
            if (campo != 0) {                       // campo: tamanho fixo, segue o jogador
                ps.pushPose();
                fieldRing(buf.getBuffer(RenderType.lightning()), ps.last().pose(), p, t, campo);
                ps.popPose();
            }
            float sc = p.getScale();
            ps.pushPose();
            ps.scale(sc, sc, sc);
            if (dev.rptag.aura.GravState.aura(bits)) {
                VertexConsumer gv = buf.getBuffer(RenderType.lightning());
                Matrix4f gm = ps.last().pose();
                bubbleRings(gv, gm, t);
                cracks(gv, gm, t);
            }
            ps.popPose();
        } catch (RuntimeException erro) {
            // erro aqui some com o efeito, nunca com o jogo
        }
    }

    /** Anel do campo gravitacional no chao (cor por modo) + ondas entrando/saindo. */
    private static void fieldRing(VertexConsumer vc, Matrix4f m, Player p, float t, int modo) {
        float R = (float) dev.rptag.aura.GravityPowers.FIELD_RADIUS;
        float[][] COL = {{0.55f, 0.35f, 1f}, {0.70f, 0.95f, 1f}, {0.35f, 0.50f, 1f}, {1f, 1f, 1f}};
        float[] c = COL[Math.max(0, Math.min(3, modo - 1))];
        terrainRing(vc, m, p, R, 0.14f, c, 0.55f, 1.2f, t);
        if (modo == 3 || modo == 4) {
            float f = (t * 0.03f) % 1f;
            float r = modo == 3 ? R * (1f - f) : R * f;
            terrainRing(vc, m, p, Math.max(0.5f, r), 0.10f, c, 0.45f * (1f - f), 0.6f, t);
        }
    }

    private static void terrainRing(VertexConsumer vc, Matrix4f m, Player p, float R, float half,
                                    float[] c, float alpha, float wallH, float t) {
        final int N = 96;
        float[] ys = new float[N + 1];
        for (int i = 0; i <= N; i++) {
            float a = i * TAU / N;
            int h = p.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(p.getX() + Mth.cos(a) * R), Mth.floor(p.getZ() + Mth.sin(a) * R));
            ys[i] = (h <= p.level().getMinBuildHeight() ? (float) p.getY() : h) - (float) p.getY() + 0.1f;
        }
        float r0 = R - half, r1 = R + half;
        for (int i = 0; i < N; i++) {
            int j = i + 1;
            float a0 = i * TAU / N, a1 = j * TAU / N;
            float al = alpha * (0.6f + 0.4f * Mth.sin(a0 * 18f - t * 0.2f));
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            quad2(vc, m, gcv(c0 * r0, ys[i], s0 * r0, c, 0f), gcv(c0 * R, ys[i], s0 * R, c, al),
                    gcv(c1 * R, ys[j], s1 * R, c, al), gcv(c1 * r0, ys[j], s1 * r0, c, 0f));
            quad2(vc, m, gcv(c0 * R, ys[i], s0 * R, c, al), gcv(c0 * r1, ys[i], s0 * r1, c, 0f),
                    gcv(c1 * r1, ys[j], s1 * r1, c, 0f), gcv(c1 * R, ys[j], s1 * R, c, al));
            quad2(vc, m, gcv(c0 * R, ys[i], s0 * R, c, 0.4f * al), gcv(c1 * R, ys[j], s1 * R, c, 0.4f * al),
                    gcv(c1 * R, ys[j] + wallH, s1 * R, c, 0f), gcv(c0 * R, ys[i] + wallH, s0 * R, c, 0f));
        }
    }

    /** Aneis de bolhas ao redor do corpo (aura ligada). */
    private static void bubbleRings(VertexConsumer vc, Matrix4f m, float t) {
        float[] c = {0.80f, 0.92f, 1.00f};
        final int N = 40;
        final float A = 0.42f, B = 0.72f;
        for (int k = 0; k < 2; k++) {
            float ang = t * 0.012f + k * (float) Math.PI;
            float cx = Mth.cos(ang) * 1.7f, cz = Mth.sin(ang) * 1.7f;
            float cy = 1.25f + 0.15f * Mth.sin(t * 0.05f + k * 3f);
            float ux = -Mth.sin(ang), uz = Mth.cos(ang);
            for (int i = 0; i < N; i++) {
                float a0 = i * TAU / N, a1 = (i + 1) * TAU / N;
                quad2(vc, m, gep(cx, cy, cz, ux, uz, 0, 0, c, 0.14f), gep(cx, cy, cz, ux, uz, 0, 0, c, 0.14f),
                        gep(cx, cy, cz, ux, uz, Mth.cos(a1) * A, Mth.sin(a1) * B, c, 0.02f),
                        gep(cx, cy, cz, ux, uz, Mth.cos(a0) * A, Mth.sin(a0) * B, c, 0.02f));
                quad2(vc, m, gep(cx, cy, cz, ux, uz, Mth.cos(a0) * A * 0.93f, Mth.sin(a0) * B * 0.93f, c, 0.45f),
                        gep(cx, cy, cz, ux, uz, Mth.cos(a0) * A, Mth.sin(a0) * B, c, 0.45f),
                        gep(cx, cy, cz, ux, uz, Mth.cos(a1) * A, Mth.sin(a1) * B, c, 0.45f),
                        gep(cx, cy, cz, ux, uz, Mth.cos(a1) * A * 0.93f, Mth.sin(a1) * B * 0.93f, c, 0.45f));
            }
            for (int j = 0; j < 12; j++) {
                float phi = j * TAU / 12 + t * 0.02f;
                float bx = Mth.cos(phi) * A * 1.12f, by = Mth.sin(phi) * B * 1.12f;
                float br = 0.05f + 0.03f * Mth.sin(j * 2.3f + t * 0.1f);
                for (int q = 0; q < 6; q++) {
                    float b0 = q * TAU / 6, b1 = (q + 1) * TAU / 6;
                    quad2(vc, m, gep(cx, cy, cz, ux, uz, bx + Mth.cos(b0) * br * 0.8f, by + Mth.sin(b0) * br * 0.8f, c, 0.6f),
                            gep(cx, cy, cz, ux, uz, bx + Mth.cos(b0) * br, by + Mth.sin(b0) * br, c, 0.6f),
                            gep(cx, cy, cz, ux, uz, bx + Mth.cos(b1) * br, by + Mth.sin(b1) * br, c, 0.6f),
                            gep(cx, cy, cz, ux, uz, bx + Mth.cos(b1) * br * 0.8f, by + Mth.sin(b1) * br * 0.8f, c, 0.6f));
                }
            }
        }
    }

    /** Fissuras de espaco rachando em volta do corpo. */
    private static void cracks(VertexConsumer vc, Matrix4f m, float t) {
        float[] c = {0.90f, 0.97f, 1.00f};
        for (int k = 0; k < 4; k++) {
            float cyc = t * 0.016f + k * 0.25f;
            int n = (int) Math.floor(cyc);
            float vis = Mth.sin((cyc - n) * (float) Math.PI);
            if (vis < 0.05f) continue;
            float s2 = k * 17.3f + n * 5.7f;
            float ang = frac(Mth.sin(s2) * 437.58f) * TAU;
            float cx = Mth.cos(ang) * 2.0f, cz = Mth.sin(ang) * 2.0f;
            float cy = 0.8f + frac(Mth.sin(s2 * 1.9f) * 951.2f) * 1.2f;
            float ux = -Mth.sin(ang), uz = Mth.cos(ang);
            float x = 0f, y = 0f, w = 0.025f * vis, al = 0.8f * vis;
            for (int i = 0; i < 7; i++) {
                float dx = (frac(Mth.sin(s2 + i * 3.1f) * 437.58f) - 0.5f) * 0.38f;
                float dy = (frac(Mth.sin(s2 + i * 5.3f) * 951.2f) - 0.5f) * 0.38f;
                pline(vc, m, cx, cy, cz, ux, uz, x, y, x + dx, y + dy, w, c, al);
                if (i == 2 || i == 4) {
                    float bx = x, by = y;
                    for (int b = 0; b < 3; b++) {
                        float ex = (frac(Mth.sin(s2 + i * 7f + b * 2.1f) * 321.7f) - 0.3f) * 0.3f;
                        float ey = (frac(Mth.sin(s2 + i * 4f + b * 3.7f) * 611.3f) - 0.5f) * 0.3f;
                        pline(vc, m, cx, cy, cz, ux, uz, bx, by, bx + ex, by + ey, w * 0.7f, c, al * 0.8f);
                        bx += ex; by += ey;
                    }
                }
                x += dx; y += dy;
            }
        }
    }

    /** Particulas da gravidade (aura, modo proprio e campo). */
    private static void gravityParticles(Player p, RandomSource rnd) {
        int bits = dev.rptag.aura.GravState.get(p);
        var lv = p.level();
        float sc = p.getScale();
        if (dev.rptag.aura.GravState.aura(bits) && rnd.nextFloat() < 0.25f) {
            double a = rnd.nextDouble() * TAU, r = (0.4 + rnd.nextDouble() * 0.4) * sc;
            lv.addParticle(ParticleTypes.END_ROD, p.getX() + Math.cos(a) * r,
                    p.getY() + rnd.nextDouble() * 1.9 * sc, p.getZ() + Math.sin(a) * r, 0, 0.01, 0);
        }
        int self = dev.rptag.aura.GravState.self(bits);
        if (self == 1) {
            lv.addParticle(new DustParticleOptions(new Vector3f(0.35f, 0.40f, 0.75f), 1.2f),
                    p.getX() + rnd.nextDouble() - 0.5, p.getY() + 1.0 + rnd.nextDouble(),
                    p.getZ() + rnd.nextDouble() - 0.5, 0, -0.06, 0);
        } else if (self == 2 && rnd.nextFloat() < 0.5f) {
            lv.addParticle(ParticleTypes.END_ROD, p.getX() + rnd.nextDouble() - 0.5, p.getY() + 0.1,
                    p.getZ() + rnd.nextDouble() - 0.5, 0, 0.05, 0);
        }
        int campo = dev.rptag.aura.GravState.field(bits);
        if (campo != 0) {
            double R = dev.rptag.aura.GravityPowers.FIELD_RADIUS;
            for (int i = 0; i < 2; i++) {
                double a = rnd.nextDouble() * TAU, r = Math.sqrt(rnd.nextDouble()) * R;
                double x = p.getX() + Math.cos(a) * r, z = p.getZ() + Math.sin(a) * r;
                double y = p.getY() + rnd.nextDouble() * 5.0;
                double ix = -Math.cos(a), iz = -Math.sin(a);
                switch (campo) {
                    case 1 -> lv.addParticle(new DustParticleOptions(new Vector3f(0.55f, 0.35f, 1f), 1.2f), x, y, z, 0, -0.12, 0);
                    case 2 -> lv.addParticle(ParticleTypes.END_ROD, x, p.getY() + rnd.nextDouble() * 1.5, z, 0, 0.10, 0);
                    case 3 -> lv.addParticle(new DustParticleOptions(new Vector3f(0.35f, 0.5f, 1f), 1.1f), x, y, z, ix * 0.18, 0, iz * 0.18);
                    default -> lv.addParticle(new DustParticleOptions(new Vector3f(1f, 1f, 1f), 1.1f),
                            p.getX() + Math.cos(a) * 1.5, y, p.getZ() + Math.sin(a) * 1.5, -ix * 0.25, 0, -iz * 0.25);
                }
            }
        }
    }

    /** Vertice de gravidade (posicao absoluta). */
    private static float[] gcv(float x, float y, float z, float[] c, float a) {
        return new float[]{x, y, z, c[0], c[1], c[2], a};
    }

    /** Ponto no plano tangente (x) + vertical (y) ao redor do centro. */
    private static float[] gep(float cx, float cy, float cz, float ux, float uz, float x, float y,
                               float[] c, float a) {
        return new float[]{cx + ux * x, cy + y, cz + uz * x, c[0], c[1], c[2], a};
    }

    private static void pline(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float ux, float uz,
                              float x0, float y0, float x1, float y1, float w, float[] c, float a) {
        float dx = x1 - x0, dy = y1 - y0, l = Mth.sqrt(dx * dx + dy * dy);
        if (l < 1e-5f) return;
        float nx = -dy / l * w / 2, ny = dx / l * w / 2;
        quad2(vc, m, gep(cx, cy, cz, ux, uz, x0 + nx, y0 + ny, c, a), gep(cx, cy, cz, ux, uz, x0 - nx, y0 - ny, c, a),
                gep(cx, cy, cz, ux, uz, x1 - nx, y1 - ny, c, a), gep(cx, cy, cz, ux, uz, x1 + nx, y1 + ny, c, a));
    }

    /** Tentáculos de tinta negra (rasgados, com espinhos) + sombra no chão. */
    private static void ink(VertexConsumer vc, Matrix4f m, float t) {
        // mancha de sombra irregular no chão
        for (int i = 0; i < 28; i++) {
            float a0 = i * TAU / 28, a1 = (i + 1) * TAU / 28;
            float r0 = 1.9f * (1f + 0.22f * Mth.sin(a0 * 5f + t * 0.05f));
            float r1 = 1.9f * (1f + 0.22f * Mth.sin(a1 * 5f + t * 0.05f));
            quad1(vc, m, iv(0, 0.03f, 0, 0.60f), iv(0, 0.03f, 0, 0.60f),
                    iv(Mth.cos(a1) * r1, 0.03f, Mth.sin(a1) * r1, 0f), iv(Mth.cos(a0) * r0, 0.03f, Mth.sin(a0) * r0, 0f));
        }
        final int N = 16, M = 20;
        for (int k = 0; k < N; k++) {
            float seed = k * 7.31f;
            float f1 = frac(Mth.sin(seed) * 43758.55f), f2 = frac(Mth.sin(seed * 1.7f + 3.1f) * 24634.63f);
            float a0 = k * TAU / N + f1 * 0.6f;
            float h0 = 0.15f + f2 * 1.7f;
            float reach = 1.2f + 1.4f * f1;
            float curl = ((k & 1) == 0 ? 1f : -1f) * (1f + f2);
            float[][] P = new float[M + 1][];
            for (int i = 0; i <= M; i++) {
                float u = i / (float) M;
                float ang = a0 + curl * u * 1.1f + 0.35f * Mth.sin(t * 0.06f + seed + u * 4f);
                float rad = 0.38f + u * reach * 0.9f;
                float y = h0 + u * (0.7f + 0.9f * f2) + 0.28f * Mth.sin(u * 6f - t * 0.08f + seed) + 0.5f * u * u;
                float w = 0.20f * (float) Math.pow(1f - u, 0.7f) * (0.65f + 0.35f * Mth.sin(u * 31f + seed * 3f)); // rasgado
                float al = 0.92f * (1f - u * u);
                P[i] = new float[]{Mth.cos(ang) * rad, y, Mth.sin(ang) * rad, -Mth.sin(ang), Mth.cos(ang), w, al,
                        Mth.cos(ang), Mth.sin(ang)};
            }
            for (int i = 0; i < M; i++) {
                float[] p = P[i], q = P[i + 1];
                // fita vertical + fita tangencial (volume de qualquer ângulo)
                quad1(vc, m, iv(p[0], p[1] - p[5], p[2], p[6]), iv(p[0], p[1] + p[5], p[2], p[6]),
                        iv(q[0], q[1] + q[5], q[2], q[6]), iv(q[0], q[1] - q[5], q[2], q[6]));
                quad1(vc, m, iv(p[0] - p[3] * p[5], p[1], p[2] - p[4] * p[5], p[6]),
                        iv(p[0] + p[3] * p[5], p[1], p[2] + p[4] * p[5], p[6]),
                        iv(q[0] + q[3] * q[5], q[1], q[2] + q[4] * q[5], q[6]),
                        iv(q[0] - q[3] * q[5], q[1], q[2] - q[4] * q[5], q[6]));
                // espinhos rasgados saindo do tentáculo
                if ((i + k) % 3 == 0 && i > 2 && i < 15) {
                    float L = 0.30f * (1f - i / (float) M) * (0.6f + 0.4f * Mth.sin(seed + i));
                    float sgn = ((i + k) & 1) == 0 ? 1f : -1f;
                    float tx = p[0] + p[7] * L * 0.6f + p[3] * L * sgn, ty = p[1] + L * 0.7f, tz = p[2] + p[8] * L * 0.6f + p[4] * L * sgn;
                    quad1(vc, m, iv(p[0] - p[3] * p[5] * 0.8f, p[1], p[2] - p[4] * p[5] * 0.8f, p[6]),
                            iv(p[0] + p[3] * p[5] * 0.8f, p[1], p[2] + p[4] * p[5] * 0.8f, p[6]),
                            iv(tx, ty, tz, 0f), iv(tx, ty, tz, 0f));
                }
            }
        }
    }

    // ------------------------------------------------------------ partículas
    /** Fumaca de tinta subindo (so a aura do vazio). */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        if (!p.level().isClientSide() || p.isSpectator()) return;
        if (p.getData(AuraAnexos.AURA.get()) == AuraEstilo.GRAVIDADE.index()) {
            try { gravityParticles(p, p.getRandom()); } catch (RuntimeException ignorado) { }
            return;
        }
        if (!ehVazio(p)) return;

        RandomSource rnd = p.getRandom();
        float sc = p.getScale();
        for (int i = 0; i < 3; i++) {
            double ang = rnd.nextDouble() * TAU, rad = (0.35 + rnd.nextDouble() * 0.5) * sc;
            double x = p.getX() + Math.cos(ang) * rad, z = p.getZ() + Math.sin(ang) * rad;
            double y = p.getY() + rnd.nextDouble() * 1.9 * sc;
            double vy = (0.03 + rnd.nextDouble() * 0.05) * Math.sqrt(sc);
            p.level().addParticle(new DustParticleOptions(new Vector3f(0.02f, 0.0f, 0.04f), 2.0f),
                    x, y, z, 0, vy * 0.7, 0);
            if (i == 0 && rnd.nextFloat() < 0.5f) {
                p.level().addParticle(ParticleTypes.SQUID_INK, x, y, z, 0, vy, 0);
            }
        }
    }

    // ------------------------------------------------------------------ helpers
    private static float frac(float x) { return x - (float) Math.floor(x); }

    /** Vértice de fogo: posição + offset, cor/alfa do ponto. */
    private static float[] pv(float[] p, float ox, float oy, float oz) {
        return new float[]{p[0] + ox, p[1] + oy, p[2] + oz, p[9], p[10], p[11], p[8]};
    }

    /** Vértice de tinta: preto com alfa. */
    private static float[] iv(float x, float y, float z, float a) {
        return new float[]{x, y, z, 0.01f, 0.0f, 0.02f, a};
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

    private AuraFx() {}
}
