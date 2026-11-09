package dev.rptag.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPEffects;
import dev.rptag.RPTagMod;
import dev.rptag.VazioReino;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * (5.29.0) <b>A AURA DO VAZIO — "a aura em volta copia a do mod".</b>
 *
 * <p>Pedido do dono: <i>"a aura em volta copia a do mod"</i>, falando do Ronova (Ruler of
 * Death). Na arte daquele mod a criatura está envolta por <b>pétalas/penas carmesim em
 * camadas</b>, girando em volta do corpo, com um núcleo escuro no meio.
 *
 * <p>Aqui a aura é desenhada como <b>geometria</b> ({@code RenderType.debugQuads()}, cor por
 * vértice) e não como partícula: assim ela aparece mesmo com as partículas no mínimo, com
 * Sodium/Iris ou no jogo puro — a mesma razão que obrigou o portal a sair das partículas
 * (pedido 50).
 *
 * <p>Quem tem a aura: quem tem o efeito {@code rptag:poder_vazio} e está <b>no vazio</b>.
 * Ela também "acende" durante um poder (o raio/orbe dão um pulso).
 *
 * <p>Regra de ouro do mod: erro aqui some com a aura, nunca com o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class VazioAuraFx {

    private VazioAuraFx() {
    }

    /** 26 penas em duas coroas (a de cima mais fechada, a de baixo mais aberta). */
    private static final int PENAS = 26;

    /** pulsos curtos (0..1) que o raio/orbe deixam na aura. */
    private static float pulso;

    private static final Random SORTE = new Random(29L);
    private static final List<float[]> POEIRA = new ArrayList<>();   // x,y,z,vel

    /** chamado pelo FX dos poderes quando o jogador solta algo. */
    public static void pulsar() {
        pulso = 1.0F;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null || mc.level == null) {
                return;
            }
            if (!VazioReino.ehVazio(mc.level) || !p.hasEffect(RPEffects.PODER_VAZIO)) {
                return;
            }
            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());

            float t = (float) ((System.nanoTime() / 1_000_000_000.0) % 3600.0);
            pulso = Math.max(0.0F, pulso - 0.02F);

            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            var mat = pose.last().pose();

            double px = Mth.lerp(event.getPartialTick().getGameTimeDeltaPartialTick(false),
                    p.xo, p.getX());
            double py = Mth.lerp(event.getPartialTick().getGameTimeDeltaPartialTick(false),
                    p.yo, p.getY());
            double pz = Mth.lerp(event.getPartialTick().getGameTimeDeltaPartialTick(false),
                    p.zo, p.getZ());
            float corpo = p.getBbHeight();

            // ---- as duas coroas de penas carmesim, girando em sentidos opostos
            for (int i = 0; i < PENAS; i++) {
                boolean alta = i % 2 == 0;
                double volta = (i / 2) / (double) (PENAS / 2) * Math.PI * 2.0;
                double ang = volta + t * (alta ? 0.9 : -0.6);
                double raio = (alta ? 0.62 : 0.95) + 0.10 * Math.sin(t * 2.1 + i);
                double altura = (alta ? corpo * 0.72 : corpo * 0.34) + 0.12 * Math.sin(t * 1.7 + i * 0.7);
                float comp = (alta ? 0.85F : 1.15F) + pulso * 0.45F;
                float larg = 0.11F + 0.02F * (float) Math.sin(t * 3.0 + i);

                double cx = px + Math.cos(ang) * raio;
                double cz = pz + Math.sin(ang) * raio;
                double cy = py + altura;

                // a pena aponta pra FORA e pra cima (como as penas da arte)
                double dirx = Math.cos(ang) * comp;
                double dirz = Math.sin(ang) * comp;
                double diry = 0.35 * comp;

                int alfa = (int) (150 + 90 * pulso);
                int corBase = ((alfa & 0xFF) << 24) | 0x8E0B16;      // carmesim fundo
                int corPonta = (0x40 << 24) | 0xFF3A46;             // ponta quase transparente

                pena(vc, mat, cx, cy, cz, dirx, diry, dirz, larg, corBase, corPonta);
            }

            // ---- o núcleo escuro atrás do corpo (a "sombra" da arte)
            anel(vc, mat, px, py + 0.06, pz, 1.15F + pulso * 0.25F, (0x66 << 24) | 0x120208);

            // ---- poeira carmesim subindo (pouca e barata)
            if (POEIRA.isEmpty()) {
                for (int i = 0; i < 40; i++) {
                    POEIRA.add(new float[]{
                            (SORTE.nextFloat() - 0.5F) * 1.6F,
                            SORTE.nextFloat() * 2.2F,
                            (SORTE.nextFloat() - 0.5F) * 1.6F,
                            0.006F + SORTE.nextFloat() * 0.012F});
                }
            }
            for (float[] m : POEIRA) {
                m[1] += m[3];
                if (m[1] > 2.4F) {
                    m[1] = 0.0F;
                    m[0] = (SORTE.nextFloat() - 0.5F) * 1.6F;
                    m[2] = (SORTE.nextFloat() - 0.5F) * 1.6F;
                }
                float a = 0.55F * (1.0F - m[1] / 2.4F);
                billboard(vc, mat, px + m[0], py + m[1], pz + m[2], 0.055F,
                        ((int) (a * 255) << 24) | 0xD2202E);
            }

            pose.popPose();
            buf.endBatch(RenderType.debugQuads());
        } catch (Throwable t) {
            // silêncio: aura é enfeite
        }
    }

    // ---------------------------------------------------------------- desenho

    /** uma pena: losango comprido que nasce grosso na base e afina na ponta. */
    private static void pena(VertexConsumer vc, org.joml.Matrix4f mat,
            double x, double y, double z, double dx, double dy, double dz,
            float largura, int corBase, int corPonta) {
        // vetor perpendicular horizontal (a pena é "chata", sempre de frente pra câmera na horizontal)
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) {
            return;
        }
        double nx = -dz / len * largura;
        double nz = dx / len * largura;
        double tx = x + dx, ty = y + dy, tz = z + dz;
        vc.addVertex(mat, (float) (x + nx), (float) y, (float) (z + nz)).setColor(corBase);
        vc.addVertex(mat, (float) (x - nx), (float) y, (float) (z - nz)).setColor(corBase);
        vc.addVertex(mat, (float) tx, (float) ty, (float) tz).setColor(corPonta);
        vc.addVertex(mat, (float) tx, (float) ty, (float) tz).setColor(corPonta);
    }

    /** anel deitado no chão (16 lados). */
    private static void anel(VertexConsumer vc, org.joml.Matrix4f mat,
            double x, double y, double z, float raio, int cor) {
        int lados = 16;
        for (int i = 0; i < lados; i++) {
            double a0 = i / (double) lados * Math.PI * 2.0;
            double a1 = (i + 1) / (double) lados * Math.PI * 2.0;
            float x0 = (float) (x + Math.cos(a0) * raio), z0 = (float) (z + Math.sin(a0) * raio);
            float x1 = (float) (x + Math.cos(a1) * raio), z1 = (float) (z + Math.sin(a1) * raio);
            float interno = raio * 0.82F;
            float xi0 = (float) (x + Math.cos(a0) * interno), zi0 = (float) (z + Math.sin(a0) * interno);
            float xi1 = (float) (x + Math.cos(a1) * interno), zi1 = (float) (z + Math.sin(a1) * interno);
            vc.addVertex(mat, xi0, (float) y, zi0).setColor(cor);
            vc.addVertex(mat, x0, (float) y, z0).setColor(cor);
            vc.addVertex(mat, x1, (float) y, z1).setColor(cor);
            vc.addVertex(mat, xi1, (float) y, zi1).setColor(cor);
        }
    }

    /** quadradinho virado pra câmera. */
    static void billboard(VertexConsumer vc, org.joml.Matrix4f mat,
            double x, double y, double z, float r, int cor) {
        vc.addVertex(mat, (float) (x - r), (float) (y - r), (float) z).setColor(cor);
        vc.addVertex(mat, (float) (x + r), (float) (y - r), (float) z).setColor(cor);
        vc.addVertex(mat, (float) (x + r), (float) (y + r), (float) z).setColor(cor);
        vc.addVertex(mat, (float) (x - r), (float) (y + r), (float) z).setColor(cor);
    }
}
