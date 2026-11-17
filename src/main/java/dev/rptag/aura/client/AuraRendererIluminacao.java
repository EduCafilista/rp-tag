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
 * (5.60.0) A AURA DE ILUMINACAO — e a partir da 5.63.0 SO O BRILHO.
 *
 * <p>O dono, no pedido 118: <i>"no iluminacao tira os efeitos em volta, deixa apenas a 'aura' em
 * volta do player brilhante"</i>. Saíram o círculo mágico (runas + hexagrama), a coluna de luz, as
 * hélices, os anéis ascendentes, os 16 raios e os 5 Portões em volta do corpo. Ficou só o que ele
 * pediu: um BRILHO dourado em volta do jogador — tres cascas de luz aditivas (uma clara e quente
 * colada no corpo, uma média e uma larga e fraca) com um pulsar leve, mais as partículas douradas
 * subindo.
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
        float pulse = 0.82f + 0.18f * Mth.sin(t * 0.16f);   // respira devagar
        float h = p.getBbHeight() + 0.35f;

        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.lightning());

        // (5.63.0) SO O BRILHO: tres cascas em volta do corpo (a de dentro e a mais forte)
        brilho(vc, m, 0.52f, h * 0.92f, t, 44, s.r2, s.g2, s.b2, 0.42f * pulse);
        brilho(vc, m, 0.70f, h, t + 21f, 44, s.r, s.g, s.b, 0.26f * pulse);
        brilho(vc, m, 0.92f, h * 1.06f, t + 43f, 40, s.r, s.g, s.b, 0.13f * pulse);

        ps.popPose();
    }

    // ------------------------------------------------------------- o brilho
    /**
     * Uma "casca" de luz em volta do corpo: alpha forte no meio do corpo, sumindo no chão e no
     * topo (nunca fica um cilindro duro com corte reto).
     */
    private static void brilho(VertexConsumer vc, Matrix4f m, float raio, float h, float t, int seg,
            float r, float g, float b, float a) {
        float meio = h * 0.55f;
        for (int i = 0; i < seg; i++) {
            float u0 = i * TAU / seg, u1 = (i + 1) * TAU / seg;
            // ondulação bem leve: o brilho mexe, mas continua "redondo"
            float rad0 = raio * (1f + 0.05f * Mth.sin(u0 * 3f + t * 0.17f));
            float rad1 = raio * (1f + 0.05f * Mth.sin(u1 * 3f + t * 0.17f));
            float x0 = Mth.cos(u0) * rad0, z0 = Mth.sin(u0) * rad0;
            float x1 = Mth.cos(u1) * rad1, z1 = Mth.sin(u1) * rad1;
            // de baixo até o meio (apaga embaixo)
            quad(vc, m, pt(x0, 0.02f, z0, a * 0.30f), pt(x0, meio, z0, a),
                    pt(x1, meio, z1, a), pt(x1, 0.02f, z1, a * 0.30f), r, g, b);
            // do meio até o topo (apaga em cima)
            quad(vc, m, pt(x0, meio, z0, a), pt(x0, h, z0, 0f),
                    pt(x1, h, z1, 0f), pt(x1, meio, z1, a), r, g, b);
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
        for (int i = 0; i < 4; i++) {
            double ang = rnd.nextDouble() * TAU;
            double rad = 0.35 + rnd.nextDouble() * 0.75;
            double x = p.getX() + Math.cos(ang) * rad;
            double z = p.getZ() + Math.sin(ang) * rad;
            p.level().addParticle(new DustParticleOptions(new Vector3f(s.r, s.g, s.b), 1.2f),
                    x, p.getY() + 0.05, z, 0, 0.04 + rnd.nextDouble() * 0.05, 0);
        }
        if (rnd.nextFloat() < 0.7f) {
            double ang = rnd.nextDouble() * TAU;
            double rad = 0.25 + rnd.nextDouble() * 0.6;
            p.level().addParticle(ParticleTypes.END_ROD,
                    p.getX() + Math.cos(ang) * rad, p.getY() + rnd.nextDouble() * 1.3,
                    p.getZ() + Math.sin(ang) * rad, 0, 0.03 + rnd.nextDouble() * 0.03, 0);
        }
    }

    // ------------------------------------------------------------- helpers
    private static float[] pt(float x, float y, float z, float a) {
        return new float[]{x, y, z, a};
    }

    private static void emit(VertexConsumer vc, Matrix4f m, float[] p, float r, float g, float b) {
        vc.addVertex(m, p[0], p[1], p[2]).setColor(r, g, b, Mth.clamp(p[3], 0f, 1f));
    }

    /** Desenha o quad nas duas faces (o RenderType faz culling). */
    private static void quad(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float[] c, float[] d,
            float r, float g, float bl) {
        emit(vc, m, a, r, g, bl); emit(vc, m, b, r, g, bl); emit(vc, m, c, r, g, bl); emit(vc, m, d, r, g, bl);
        emit(vc, m, d, r, g, bl); emit(vc, m, c, r, g, bl); emit(vc, m, b, r, g, bl); emit(vc, m, a, r, g, bl);
    }

    private AuraRendererIluminacao() {}
}
