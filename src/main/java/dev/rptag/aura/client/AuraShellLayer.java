package dev.rptag.aura.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.aura.AuraAnexos;
import dev.rptag.aura.AuraEstilo;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * Aura que ABRAÇA o corpo: o próprio modelo do jogador é redesenhado várias vezes, deslocado em volta
 * (dilatação), mostrando só as faces de trás => contorno de luz/sombra de espessura uniforme na silhueta.
 */
public class AuraShellLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Direções de deslocamento: 8 no plano horizontal + cima/baixo. */
    private static final float[][] DIRS = new float[10][];
    static {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            DIRS[i] = new float[]{(float) Math.cos(a), 0f, (float) Math.sin(a)};
        }
        DIRS[8] = new float[]{0f, 1f, 0f};
        DIRS[9] = new float[]{0f, -1f, 0f};
    }

    // Dourado estilo Escanor: {raio(blocos), r, g, b, intensidade por cópia} do mais perto ao mais longe
    private static final float[][] GOLD = {
            {0.045f, 1.00f, 0.98f, 0.78f, 0.30f},   // borda branco-amarelada
            {0.100f, 1.00f, 0.86f, 0.35f, 0.20f},   // dourado
            {0.180f, 1.00f, 0.62f, 0.14f, 0.13f},   // laranja
            {0.290f, 1.00f, 0.38f, 0.10f, 0.08f},   // laranja-avermelhado
            {0.430f, 0.95f, 0.28f, 0.30f, 0.05f},   // halo rosado
    };

    // Vazio: {raio, alfa por cópia}
    private static final float[][] VOID = {
            {0.035f, 0.30f}, {0.090f, 0.26f}, {0.170f, 0.20f}, {0.280f, 0.15f}, {0.430f, 0.10f}, {0.600f, 0.07f},
    };

    public AuraShellLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (p.isSpectator() || p.isInvisible()) return;
        int id = p.getData(AuraAnexos.AURA.get());
        float t = p.tickCount + partialTick;
        // (5.64.0) A ILUMINACAO pendura no anexo; o VAZIO tambem acende pela aura antiga (pulsar()).
        boolean ouro = id == AuraEstilo.ILUMINACAO.index();
        boolean vazio = id == AuraEstilo.VAZIO.index() || (!ouro && dev.rptag.client.VazioAuraFx.ativa(p));
        if (ouro) gold(ps, buf, t);
        else if (vazio) shadow(ps, buf, t);
    }

    private void gold(PoseStack ps, MultiBufferSource buf, float t) {
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        float pulse = 0.92f + 0.08f * Mth.sin(t * 0.25f);
        // pele alaranjada como na imagem (por cima do corpo)
        model.renderToBuffer(ps, buf.getBuffer(AuraRenderTypes.GLOW_TINT), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, argb(0.55f, 0.34f * pulse, 0.15f * pulse, 0.02f * pulse));

        VertexConsumer vc = buf.getBuffer(AuraRenderTypes.GLOW_HULL);
        for (int ring = 0; ring < GOLD.length; ring++) {
            float[] g = GOLD[ring];
            for (int d = 0; d < DIRS.length; d++) {
                float flick = 0.75f + 0.25f * Mth.sin(t * 0.35f + d * 1.7f + ring * 2.3f);  // borda "queimando"
                float rad = g[0] * flick;
                float up = (ring >= 2) ? g[0] * 0.35f : 0f;                                    // chama sobe um pouco
                float k = g[4] * pulse;
                ps.pushPose();
                ps.translate(DIRS[d][0] * rad, DIRS[d][1] * rad - up, DIRS[d][2] * rad);
                model.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        argb(1f, g[1] * k, g[2] * k, g[3] * k));
                ps.popPose();
            }
        }
    }

    private void shadow(PoseStack ps, MultiBufferSource buf, float t) {
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        VertexConsumer vc = buf.getBuffer(AuraRenderTypes.SHADOW_HULL);
        for (int ring = 0; ring < VOID.length; ring++) {
            float[] v = VOID[ring];
            for (int d = 0; d < DIRS.length; d++) {
                float wob = 0.70f + 0.50f * Mth.sin(t * 0.13f + d * 2.1f + ring * 1.3f);       // borda de tinta ondulando
                float rad = v[0] * wob;
                float rise = v[0] * 0.5f * (1f + Mth.sin(t * 0.09f + d));                      // fumaça subindo
                ps.pushPose();
                ps.translate(DIRS[d][0] * rad, DIRS[d][1] * rad - rise, DIRS[d][2] * rad);
                model.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        argb(v[1], 0.01f, 0.0f, 0.02f));
                ps.popPose();
            }
        }
    }

    private static int argb(float a, float r, float g, float b) {
        return (c(a) << 24) | (c(r) << 16) | (c(g) << 8) | c(b);
    }

    private static int c(float v) { return Mth.clamp((int) (v * 255f + 0.5f), 0, 255); }
}
