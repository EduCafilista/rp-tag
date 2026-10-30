package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * (4.9.4) MÁSCARA DE PEDRA NA CABEÇA — MODELO FEITO À MÃO.
 *
 * <p>Antes eram 3 painéis retos colados na frente do rosto (ficava chapado,
 * "papelão"). Agora é uma <b>casca curva</b> gerada ponto a ponto:
 *
 * <ul>
 *   <li>a superfície é um arco de círculo em volta do rosto (centro atrás da
 *       cabeça, raio fixo) — por isso ela ABRAÇA a cara em vez de ficar plana;</li>
 *   <li>a largura do arco diminui no queixo e na testa ({@link #angMax}) — é o
 *       recorte da máscara do JoJo (largo nas maçãs, estreito embaixo);</li>
 *   <li>o <b>relevo</b> ({@link #relevo}) empurra e afunda a casca: testa
 *       saliente, órbitas fundas, nariz comprido, maçãs do rosto, boca
 *       afundada e queixo recuado — tudo em gaussianas somadas;</li>
 *   <li>malha de {@value #COLS}×{@value #ROWS} pontos ({@value #COLS} - 1) *
 *       ({@value #ROWS} - 1) * 2 triângulos, com <b>normal calculada por
 *       diferença finita</b> — é isso que faz a luz do jogo bater na pedra
 *       como se fosse volume de verdade.</li>
 * </ul>
 *
 * <p>Coordenadas: depois de {@code head.translateAndRotate} a cabeça é um cubo
 * de 8³ com o pivô em (0,24,0) — 1 unidade = 1 pixel do modelo. A frente do
 * modelo é <b>-Z</b> (é por isso que o rosto fica no plano z = -4).
 */
public class CamadaMascara extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/item/mascara_pedra.png");

    // ---- malha
    private static final int COLS = 19;          // pontos na largura
    private static final int ROWS = 17;          // pontos na altura
    // ---- casca (arco em volta do rosto)
    private static final float Z_CENTRO = -0.35f;   // centro do arco (atrás do rosto)
    private static final float RAIO = 4.55f;        // raio: frente da máscara em z ≈ -4.9
    private static final float Y_BASE = 0.62f;      // embaixo (abaixo do queixo)
    private static final float Y_TOPO = -8.55f;     // em cima (acima da testa)

    public CamadaMascara(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> pai) {
        super(pai);
    }

    // ============================================================ superfície

    /**
     * Meia-abertura do arco na altura {@code v} (0 = queixo, 1 = testa).
     * Largo nas maçãs (~1,34 rad ≈ 77°) e estreito nas pontas (~0,78 rad).
     */
    static float angMax(float v) {
        float s = (float) Math.sin(Math.PI * Math.max(0f, Math.min(1f, v)));
        return 0.78f + 0.56f * (float) Math.pow(Math.max(0f, s), 0.55);
    }

    private static float gauss(float x) {
        return (float) Math.exp(-x * x);
    }

    /**
     * Relevo da máscara, em unidades do modelo (1 = 1 pixel). Positivo empurra
     * para FORA (na direção de quem olha), negativo afunda.
     *
     * @param u de -1 (lado esquerdo) a +1 (lado direito)
     * @param v de 0 (queixo) a 1 (testa)
     */
    static float relevo(float u, float v) {
        float a = Math.abs(u);
        float r = 0f;
        r += 0.16f * gauss(u / 0.20f);                                            // quilha central (a costura da pedra)
        r += 0.46f * gauss(u / 0.26f) * gauss((v - 0.435f) / 0.115f);             // nariz: comprido e pontudo
        r += 0.20f * gauss(u / 0.90f) * gauss((v - 0.665f) / 0.045f);             // testa/sobrancelha saliente
        r -= 0.17f * gauss((a - 0.33f) / 0.15f) * gauss((v - 0.605f) / 0.055f);   // órbitas fundas
        r += 0.13f * gauss((a - 0.55f) / 0.19f) * gauss((v - 0.375f) / 0.085f);   // maçãs do rosto
        r -= 0.13f * gauss(u / 0.34f) * gauss((v - 0.245f) / 0.045f);             // boca afundada
        r -= 0.19f * gauss((v - 0.02f) / 0.055f);                                 // queixo recuado
        r -= 0.06f * gauss((v - 0.97f) / 0.04f);                                  // topo da testa vira pra trás
        return r;
    }

    /** Um ponto da casca. {@code out = {x, y, z}}. */
    static void ponto(float u, float v, float[] out) {
        float th = u * angMax(v);
        float re = RAIO + relevo(u, v);
        out[0] = re * (float) Math.sin(th);
        out[1] = Y_BASE + (Y_TOPO - Y_BASE) * v;
        out[2] = Z_CENTRO - re * (float) Math.cos(th);
    }

    /** Normal (para fora) por diferença finita — é o que dá o volume na luz. */
    static void normal(float u, float v, float[] out) {
        final float e = 0.004f;
        float[] a = new float[3], b = new float[3], c = new float[3], d = new float[3];
        float uc = Math.max(-1f, Math.min(1f, u));
        float vc = Math.max(0f, Math.min(1f, v));
        ponto(Math.min(1f, uc + e), vc, a);
        ponto(Math.max(-1f, uc - e), vc, b);
        ponto(uc, Math.min(1f, vc + e), c);
        ponto(uc, Math.max(0f, vc - e), d);
        float ux = a[0] - b[0], uy = a[1] - b[1], uz = a[2] - b[2];
        float vx = c[0] - d[0], vy = c[1] - d[1], vz = c[2] - d[2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-5f) {
            nx = 0f; ny = 0f; nz = -1f;
        } else {
            nx /= len; ny /= len; nz /= len;
        }
        // a normal tem de apontar para FORA (longe do centro do arco)
        float th = u * angMax(v);
        float ox = (float) Math.sin(th), oz = -(float) Math.cos(th);
        if (nx * ox + nz * oz < 0f) {
            nx = -nx; ny = -ny; nz = -nz;
        }
        out[0] = nx; out[1] = ny; out[2] = nz;
    }

    // ============================================================ render

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int luz, AbstractClientPlayer jogador,
                       float membroSwing, float membroSwingAmount, float tickParcial, float idadeEntidade,
                       float rotacaoCabeca, float inclinacaoCabeca) {
        ItemStack pilha = jogador.getItemBySlot(EquipmentSlot.HEAD);
        if (pilha.isEmpty() || !(pilha.getItem() instanceof dev.rptag.MascaraPedraItem) || jogador.isInvisible()) {
            return;
        }

        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEX));

        // ---- monta a malha uma vez (posições, UVs e normais por ponto)
        float[][][] pos = new float[COLS][ROWS][];
        float[][][] nor = new float[COLS][ROWS][];
        float[][] tu = new float[COLS][ROWS];
        float[][] tv = new float[COLS][ROWS];
        float[] tmp = new float[3];
        for (int j = 0; j < COLS; j++) {
            float u = -1f + 2f * j / (COLS - 1f);
            for (int i = 0; i < ROWS; i++) {
                float v = i / (ROWS - 1f);
                ponto(u, v, tmp);
                pos[j][i] = new float[]{tmp[0], tmp[1], tmp[2]};
                normal(u, v, tmp);
                nor[j][i] = new float[]{tmp[0], tmp[1], tmp[2]};
                // UV: a região do ROSTO na textura 64x64 (0..0.5 nos dois eixos)
                tu[j][i] = 0.5f * j / (COLS - 1f);
                tv[j][i] = 0.5f * (1f - v);
            }
        }

        // ---- emite os triângulos
        for (int j = 0; j < COLS - 1; j++) {
            for (int i = 0; i < ROWS - 1; i++) {
                vert(vc, m, luz, pos[j][i], nor[j][i], tu[j][i], tv[j][i]);
                vert(vc, m, luz, pos[j + 1][i], nor[j + 1][i], tu[j + 1][i], tv[j + 1][i]);
                vert(vc, m, luz, pos[j + 1][i + 1], nor[j + 1][i + 1], tu[j + 1][i + 1], tv[j + 1][i + 1]);

                vert(vc, m, luz, pos[j][i], nor[j][i], tu[j][i], tv[j][i]);
                vert(vc, m, luz, pos[j + 1][i + 1], nor[j + 1][i + 1], tu[j + 1][i + 1], tv[j + 1][i + 1]);
                vert(vc, m, luz, pos[j][i + 1], nor[j][i + 1], tu[j][i + 1], tv[j][i + 1]);
            }
        }

        pose.popPose();
    }

    private static void vert(VertexConsumer vc, Matrix4f m, int luz, float[] p, float[] n, float u, float v) {
        vc.addVertex(m, p[0], p[1], p[2])
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(luz)
                .setNormal(n[0], n[1], n[2]);
    }

    // ============================================================ registro

    /**
     * Atalho pro registro no {@code EntityRenderersEvent.AddLayers}. Só o
     * PlayerRenderer chega aqui (AbstractClientPlayer + PlayerModel), então o
     * cast é seguro — mas os genéricos do vanilla obrigam a fazer na mão.
     */
    @SuppressWarnings("unchecked")
    public static void registrarEm(net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> renderer) {
        ((net.minecraft.client.renderer.entity.LivingEntityRenderer<AbstractClientPlayer,
                PlayerModel<AbstractClientPlayer>>) renderer)
                .addLayer(new CamadaMascara((net.minecraft.client.renderer.entity.LivingEntityRenderer<
                        AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) renderer));
    }
}
