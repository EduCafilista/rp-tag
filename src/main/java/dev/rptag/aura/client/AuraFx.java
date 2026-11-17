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
