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
    // (5.74.0) PEDIDO 131: "consegue diminuir o brilho da iluminacao? nao da para ver a skin".
    // Metade da forca de antes em cada anel: o brilho continua, mas vira contorno e nao um borrao
    // branco por cima do corpo — da para ver a skin, a armadura e o nome.
    private static final float[][] GOLD = {
            {0.045f, 1.00f, 0.98f, 0.78f, 0.13f},   // borda branco-amarelada
            {0.100f, 1.00f, 0.86f, 0.35f, 0.09f},   // dourado
            {0.180f, 1.00f, 0.62f, 0.14f, 0.06f},   // laranja
            {0.290f, 1.00f, 0.38f, 0.10f, 0.035f},  // laranja-avermelhado
            {0.430f, 0.95f, 0.28f, 0.30f, 0.02f},   // halo rosado
    };

    // Vazio: {raio, alfa por cópia}
    private static final float[][] VOID = {
            {0.035f, 0.30f}, {0.090f, 0.26f}, {0.170f, 0.20f}, {0.280f, 0.15f}, {0.430f, 0.10f}, {0.600f, 0.07f},
    };

    // (5.67.0) Radiacao (chamas azuis): {raio, r, g, b, forca}, do branco-ciano ao azul profundo
    private static final float[][] RAD_GLOW = {
            {0.050f, 0.85f, 1.00f, 1.00f, 0.30f},
            {0.110f, 0.25f, 0.90f, 1.00f, 0.20f},
            {0.200f, 0.10f, 0.60f, 1.00f, 0.14f},
            {0.320f, 0.06f, 0.35f, 1.00f, 0.09f},
            {0.460f, 0.05f, 0.20f, 0.90f, 0.05f},
    };
    // manchas azul-marinho escuras entre as chamas: {raio, alfa}
    private static final float[][] RAD_SHADOW = {{0.15f, 0.14f}, {0.30f, 0.12f}, {0.50f, 0.10f}, {0.72f, 0.07f}};

    // (5.69.0) Arco-iris: {raio, alfa por copia} — a cor de cada copia vem do hsv() girando
    private static final float[][] RB = {
            {0.05f, 0.34f}, {0.11f, 0.26f}, {0.20f, 0.18f}, {0.32f, 0.12f}, {0.48f, 0.08f}, {0.68f, 0.05f},
    };

    /** HSV -> RGB (h em 0..1). Usado pela aura arco-iris. */
    static float[] hsv(float h, float s) {
        float r = Mth.clamp(Math.abs(h * 6f - 3f) - 1f, 0f, 1f);
        float g = Mth.clamp(2f - Math.abs(h * 6f - 2f), 0f, 1f);
        float b = Mth.clamp(2f - Math.abs(h * 6f - 4f), 0f, 1f);
        return new float[]{1f + (r - 1f) * s, 1f + (g - 1f) * s, 1f + (b - 1f) * s};
    }

    // (5.73.0) Gravidade: contorno branco-azulado bem translucido {raio, r, g, b, forca}
    private static final float[][] GR = {
            {0.04f, 0.85f, 0.95f, 1.00f, 0.14f}, {0.10f, 0.60f, 0.80f, 1.00f, 0.09f},
            {0.20f, 0.40f, 0.65f, 1.00f, 0.05f},
    };

    /**
     * (5.77.0) OTIMIZACAO — LOD da casca. Cada anel desenha o MODELO INTEIRO do jogador em 10
     * direcoes: a aura dourada, por exemplo, fazia 50 desenhos de modelo por jogador por quadro.
     * Numa cena com 10 players de aura isso e meio milhar de modelos por quadro — era o maior
     * custo do mod. Agora quem esta longe recebe menos copias (e some depois do corte da
     * {@link dev.rptag.client.FxQualidade}). De perto NADA muda.
     */
    private int passo = 1;
    private int maxAneis = 99;

    public AuraShellLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (p.isSpectator() || p.isInvisible()) return;
        // (5.77.0) OTIMIZACAO: longe demais (pela qualidade atual) nem entra no desenho.
        if (dev.rptag.client.FxQualidade.longe(p)) return;
        int id = p.getData(AuraAnexos.AURA.get());
        if (id == 0 && !dev.rptag.client.VazioAuraFx.ativa(p)) return;   // sem aura: nao gasta nada
        this.passo = dev.rptag.client.FxQualidade.passoDir();            // (5.77.0) LOD
        this.maxAneis = dev.rptag.client.FxQualidade.aneis(6);
        float t = p.tickCount + partialTick;
        // (5.66.0) Ouro = /iluminacao. Vazio = /vazio ligar (quem manda e o RaiosVazioFx, que ja
        // recebe o aviso do servidor) — e /vazio desligar apaga tudo na hora.
        boolean ouro = id == AuraEstilo.ILUMINACAO.index();
        // (5.67.0) /radiacao: chamas azuis. Vazio = /vazio ligar. /iluminacao off ou /radiacao off = nada.
        boolean radiacao = id == AuraEstilo.RADIACAO.index();
        boolean arcoiris = id == AuraEstilo.ARCOIRIS.index();          // (5.69.0)
        boolean vazio = !ouro && !radiacao && !arcoiris && dev.rptag.client.VazioAuraFx.ativa(p);
        if (ouro) gold(ps, buf, t);
        else if (radiacao) radiation(ps, buf, t);
        else if (arcoiris) rainbow(ps, buf, t);
        else if (id == AuraEstilo.GRAVIDADE.index()
                && dev.rptag.aura.GravState.aura(dev.rptag.aura.GravState.get(p))) gravity(ps, buf, t);
        else if (vazio) shadow(ps, buf, t);
    }

    private void gold(PoseStack ps, MultiBufferSource buf, float t) {
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        float pulse = 0.92f + 0.08f * Mth.sin(t * 0.25f);
        // (5.74.0) a "pele alaranjada" cobria a skin: caiu de 0.55 para 0.12, so um calor de leve
        model.renderToBuffer(ps, buf.getBuffer(AuraRenderTypes.GLOW_TINT), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, argb(0.12f, 0.34f * pulse, 0.15f * pulse, 0.02f * pulse));

        VertexConsumer vc = buf.getBuffer(AuraRenderTypes.GLOW_HULL);
        for (int ring = 0; ring < Math.min(GOLD.length, maxAneis); ring++) {
            float[] g = GOLD[ring];
            for (int d = 0; d < DIRS.length; d += passo) {
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
        for (int ring = 0; ring < Math.min(VOID.length, maxAneis); ring++) {
            float[] v = VOID[ring];
            for (int d = 0; d < DIRS.length; d += passo) {
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

    /** (5.67.0) A casca da radiacao: chamas azuis ondulando e subindo + manchas escuras. */
    private void radiation(PoseStack ps, MultiBufferSource buf, float t) {
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        float pulse = 0.90f + 0.10f * Mth.sin(t * 0.22f);

        VertexConsumer sh = buf.getBuffer(AuraRenderTypes.SHADOW_HULL);
        for (int ring = 0; ring < Math.min(RAD_SHADOW.length, maxAneis); ring++) {
            float[] v = RAD_SHADOW[ring];
            for (int d = 0; d < DIRS.length; d += passo) {
                float rad = v[0] * (0.70f + 0.50f * Mth.sin(t * 0.11f + d * 2.1f + ring * 1.3f));
                float rise = v[0] * 0.6f * (1f + Mth.sin(t * 0.08f + d));
                ps.pushPose();
                ps.translate(DIRS[d][0] * rad, DIRS[d][1] * rad - rise, DIRS[d][2] * rad);
                model.renderToBuffer(ps, sh, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        argb(v[1], 0.01f, 0.03f, 0.28f));
                ps.popPose();
            }
        }

        model.renderToBuffer(ps, buf.getBuffer(AuraRenderTypes.GLOW_TINT), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, argb(0.60f, 0.04f * pulse, 0.22f * pulse, 0.34f * pulse));

        VertexConsumer vc = buf.getBuffer(AuraRenderTypes.GLOW_HULL);
        for (int ring = 0; ring < Math.min(RAD_GLOW.length, maxAneis); ring++) {
            float[] g = RAD_GLOW[ring];
            for (int d = 0; d < DIRS.length; d += passo) {
                float rad = g[0] * (0.70f + 0.30f * Mth.sin(t * 0.30f + d * 1.9f + ring * 2.1f));
                float up = g[0] * 0.5f;                                   // chamas sobem
                float k = g[4] * pulse;
                ps.pushPose();
                ps.translate(DIRS[d][0] * rad, DIRS[d][1] * rad - up, DIRS[d][2] * rad);
                model.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        argb(1f, g[1] * k, g[2] * k, g[3] * k));
                ps.popPose();
            }
        }
    }

    /** (5.69.0) A casca do arco-iris: 6 copias com as cores girando em volta do corpo. */
    private void rainbow(PoseStack ps, MultiBufferSource buf, float t) {
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        float pulse = 0.90f + 0.10f * Mth.sin(t * 0.30f);
        // corpo incandescente: luz branco-dourada por cima da pele
        model.renderToBuffer(ps, buf.getBuffer(AuraRenderTypes.GLOW_TINT), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, argb(0.60f, 0.55f * pulse, 0.45f * pulse, 0.22f * pulse));

        VertexConsumer vc = buf.getBuffer(AuraRenderTypes.GLOW_HULL);
        for (int ring = 0; ring < Math.min(RB.length, maxAneis); ring++) {
            float[] g = RB[ring];
            for (int d = 0; d < DIRS.length; d += passo) {
                float hue = (t * 0.010f + ring * 0.13f + d * 0.045f) % 1f;      // faixas de cor girando
                float[] c = hsv(hue, ring == 0 ? 0.25f : 0.85f);                // 1o anel quase branco
                float rad = g[0] * (0.75f + 0.25f * Mth.sin(t * 0.33f + d * 1.9f + ring * 2.1f));
                float k = g[1] * pulse;
                ps.pushPose();
                ps.translate(DIRS[d][0] * rad, DIRS[d][1] * rad - g[0] * 0.4f, DIRS[d][2] * rad);
                model.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        argb(1f, c[0] * k, c[1] * k, c[2] * k));
                ps.popPose();
            }
        }
    }

    /** (5.73.0) A casca da gravidade: contorno palido branco-azulado, quase transparente. */
    private void gravity(PoseStack ps, MultiBufferSource buf, float t) {
        PlayerModel<AbstractClientPlayer> model = getParentModel();
        float pulse = 0.90f + 0.10f * Mth.sin(t * 0.15f);
        model.renderToBuffer(ps, buf.getBuffer(AuraRenderTypes.GLOW_TINT), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, argb(1f, 0.05f * pulse, 0.07f * pulse, 0.10f * pulse));
        VertexConsumer vc = buf.getBuffer(AuraRenderTypes.GLOW_HULL);
        for (int ring = 0; ring < Math.min(GR.length, maxAneis); ring++) {
            float[] g = GR[ring];
            for (int d = 0; d < DIRS.length; d += passo) {
                float rad = g[0] * (0.75f + 0.25f * Mth.sin(t * 0.20f + d * 1.7f + ring * 2.3f));
                float k = g[4] * pulse;
                ps.pushPose();
                ps.translate(DIRS[d][0] * rad, DIRS[d][1] * rad, DIRS[d][2] * rad);
                model.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        argb(1f, g[1] * k, g[2] * k, g[3] * k));
                ps.popPose();
            }
        }
    }

    private static int argb(float a, float r, float g, float b) {
        return (c(a) << 24) | (c(r) << 16) | (c(g) << 8) | c(b);
    }

    private static int c(float v) { return Mth.clamp((int) (v * 255f + 0.5f), 0, 255); }
}
