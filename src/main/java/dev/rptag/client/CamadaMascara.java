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

/**
 * (4.9.6) MÁSCARA DE PEDRA — réplica do modelo 3D do JOJO (Sketchfab "MASCARA DE
 * PIEDRA JOJO", de guerrerogrd), feita à mão ponto a ponto.
 *
 * <p><b>O que mudou nesta versão:</b> a silhueta deixou de ser um ovo e passou a
 * ser a do modelo — <b>escudo</b>: ponta fina no queixo, largo e quase reto no
 * meio do rosto, topo levemente estreitado (tabela {@link #W_LARG}). A cara
 * ganhou os traços que a referência tem e a anterior não tinha:
 * <ul>
 *   <li><b>testa em Y</b> — duas cristas que descem para o meio do rosto;</li>
 *   <li><b>sobrancelha em V</b> (brava), por cima das órbitas;</li>
 *   <li><b>órbitas em fenda</b> — fundas, finas e inclinadas (canto de fora sobe);</li>
 *   <li><b>nariz</b> com ponte, dorso saliente e asas;</li>
 *   <li><b>focinho</b> saltando para frente, <b>boca funda com presas</b> e queixo
 *       projetado com a borda de baixo recuada.</li>
 * </ul>
 *
 * <p><b>Geometria (volume fechado, 100% 3D):</b>
 * <ul>
 *   <li><b>placa de rosto</b> — frente quase plana (z = {@value #Z_FRENTE}) e só o
 *       extremo ({@code |u| > 0,88}) enrola para trás, abraçando a cabeça;</li>
 *   <li><b>casca externa</b> com o relevo esculpido em gaussianas
 *       ({@link #relevo});</li>
 *   <li><b>casca interna</b> deslocada {@value #ESPESSURA} + <b>aba</b> ligando as
 *       bordas — a máscara tem grossura e não some vista de lado ou por trás;</li>
 *   <li>malha {@value #COLS}×{@value #ROWS} nas duas cascas, <b>normais por
 *       diferença finita</b>, tudo pré-computado no {@code static}.</li>
 * </ul>
 *
 * <p><b>Coordenadas:</b> depois de {@code head.translateAndRotate} a cabeça é um
 * cubo de 8³ — 1 unidade = 1 pixel do modelo, <b>+Y para cima</b> (queixo em
 * {@code Y_BASE = -4,35}, testa em {@code Y_TOPO = +4,15}) e a frente em -Z.
 * O render de conferência ({@code arte/previa_mascara3d.py}) desenha a CAIXA DA
 * CABEÇA junto justamente para não repetir o erro da 4.9.4 (eixo Y invertido).
 *
 * <p>A mesma matemática está em {@code arte/previa_mascara3d.py}, que também
 * gera a textura {@code textures/entity/mascara_pedra.png}.
 */
public class CamadaMascara extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/entity/mascara_pedra.png");

    // ---- malha
    private static final int COLS = 29;          // pontos na largura
    private static final int ROWS = 25;          // pontos na altura
    // ---- forma
    private static final float Z_FRENTE = -4.48f;   // plano da frente (rosto em z = -4)
    private static final float ENROLA = 1.70f;      // quanto o extremo enrola para trás
    private static final float ESPESSURA = 0.62f;   // grossura da pedra
    private static final float Y_BASE = -4.35f;     // EMBAIXO (abaixo do queixo)
    private static final float Y_TOPO = 4.15f;      // EM CIMA (testa)

    // ---- triângulos prontos (posição, normal, uv) — montados no static init
    private static final int MAXV = 20000;
    private static final float[] VPX = new float[MAXV], VPY = new float[MAXV], VPZ = new float[MAXV];
    private static final float[] VNX = new float[MAXV], VNY = new float[MAXV], VNZ = new float[MAXV];
    private static final float[] VTU = new float[MAXV], VTV = new float[MAXV];
    private static int NV;

    static {
        construirMalha();
    }

    public CamadaMascara(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> pai) {
        super(pai);
    }

    // ============================================================ superfície

    /** alturas da tabela de largura (v = 0 queixo … 1 testa). */
    private static final float[] V_LARG = {0.00f, 0.06f, 0.16f, 0.30f, 0.46f, 0.64f, 0.78f, 0.90f, 0.97f, 1.00f};
    /** meia-largura em cada altura: ponta fina embaixo, largo no meio, topo arredondado. */
    private static final float[] W_LARG = {0.70f, 1.70f, 2.45f, 2.88f, 3.02f, 3.04f, 3.02f, 2.98f, 2.86f, 2.55f};

    /**
     * Meia-largura da máscara na altura {@code v} (0 = queixo, 1 = testa).
     * Silhueta de ESCUDO (a do modelo do Sketchfab): sobe rápido da ponta do
     * queixo, fica larga e quase reta no meio e só afina um pouco no topo.
     */
    static float largura(float v) {
        float c = Math.max(0f, Math.min(1f, v));
        for (int k = 1; k < V_LARG.length; k++) {
            if (c <= V_LARG[k]) {
                float t = (c - V_LARG[k - 1]) / (V_LARG[k] - V_LARG[k - 1]);
                return W_LARG[k - 1] + (W_LARG[k] - W_LARG[k - 1]) * t;
            }
        }
        return W_LARG[W_LARG.length - 1];
    }

    private static float gauss(float x) {
        return (float) Math.exp(-x * x);
    }

    /**
     * Relevo da máscara, em unidades do modelo (positivo = para fora / para -Z).
     *
     * @param u de -1 (lado esquerdo) a +1 (lado direito)
     * @param v de 0 (queixo) a 1 (testa)
     */
    static float relevo(float u, float v) {
        float a = Math.abs(u);
        float r = 0f;
        r += 0.12f * gauss(u / 0.55f) * gauss((v - 0.80f) / 0.22f);                        // testa inchada
        float vc = 0.735f - 0.10f * a;                                                     // TESTA em Y (cristas do JoJo)
        r += 0.34f * gauss((a - 0.15f) / 0.070f) * gauss((v - vc) / 0.045f);
        float vb = 0.645f + 0.055f * a;                                                    // sobrancelha em V (brava)
        r += 0.30f * gauss((a - 0.30f) / 0.16f) * gauss((v - vb) / 0.028f);
        float vo = 0.575f + 0.050f * a;                                                    // ÓRBITAS: fenda funda e fina
        r -= 0.72f * gauss((a - 0.32f) / 0.080f) * gauss((v - vo) / 0.022f);
        r += 0.30f * gauss(u / 0.10f) * gauss((v - 0.615f) / 0.060f);                      // ponte do nariz
        r += 0.72f * gauss(u / 0.14f) * gauss((v - 0.470f) / 0.070f);                      // dorso do nariz
        r += 0.26f * gauss((a - 0.17f) / 0.075f) * gauss((v - 0.405f) / 0.035f);           // asas do nariz
        r += 0.30f * gauss((a - 0.55f) / 0.15f) * gauss((v - 0.435f) / 0.075f);            // maçãs
        r += 0.34f * gauss(u / 0.42f) * gauss((v - 0.315f) / 0.085f);                      // focinho (salta pra frente)
        r -= 0.34f * gauss(u / 0.30f) * gauss((v - 0.243f) / 0.030f);                      // boca funda
        r += 0.16f * gauss(u / 0.34f) * gauss((v - 0.115f) / 0.06f);                       // queixo pra frente
        r -= 0.26f * gauss((v - 0.010f) / 0.045f);                                         // borda de baixo recuada
        r -= 0.05f * gauss((v - 0.985f) / 0.030f);                                         // topo
        return r;
    }

    /**
     * Um ponto da casca. {@code rel = 1} = casca externa; {@code rel = 0} = interna
     * (afundada {@value #ESPESSURA} e com relevo suave, para não furar a de fora).
     */
    static void ponto(float u, float v, float rel, float[] out) {
        float w = largura(v);
        float t = Math.max(0f, (Math.abs(u) - 0.88f) / 0.12f);
        float z = Z_FRENTE + ENROLA * (float) Math.pow(t, 1.5);
        float rel_ = rel > 0.5f ? 1.0f : 0.35f;
        z -= relevo(u, v) * rel_;
        if (rel <= 0.5f) {
            z += ESPESSURA;
        }
        out[0] = w * u;
        out[1] = Y_BASE + (Y_TOPO - Y_BASE) * v;
        out[2] = z;
    }

    /** Normal (para fora) por diferença finita — é o que dá o volume na luz. */
    static void normal(float u, float v, float rel, float[] out) {
        final float e = 0.004f;
        float uc = Math.max(-1f, Math.min(1f, u));
        float vc = Math.max(0.001f, Math.min(0.999f, v));
        float[] a = new float[3], b = new float[3], c = new float[3], d = new float[3];
        ponto(Math.min(1f, uc + e), vc, rel, a);
        ponto(Math.max(-1f, uc - e), vc, rel, b);
        ponto(uc, Math.min(1f, vc + e), rel, c);
        ponto(uc, Math.max(0f, vc - e), rel, d);
        float ux = a[0] - b[0], uy = a[1] - b[1], uz = a[2] - b[2];
        float vx = c[0] - d[0], vy = c[1] - d[1], vz = c[2] - d[2];
        float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) {
            nx = 0f; ny = 0f; nz = -1f;
        } else {
            nx /= len; ny /= len; nz /= len;
        }
        if (nz > 0f) {          // a frente olha para -Z
            nx = -nx; ny = -ny; nz = -nz;
        }
        if (rel <= 0.5f) {      // casca interna: normal para dentro
            nx = -nx; ny = -ny; nz = -nz;
        }
        out[0] = nx; out[1] = ny; out[2] = nz;
    }

    // ============================================================ montagem

    private static void vert(float[] p, float[] n, float u, float v) {
        if (NV >= MAXV) {
            return;
        }
        VPX[NV] = p[0]; VPY[NV] = p[1]; VPZ[NV] = p[2];
        VNX[NV] = n[0]; VNY[NV] = n[1]; VNZ[NV] = n[2];
        VTU[NV] = u; VTV[NV] = v;
        NV++;
    }

    /** Grade de quads (2 triângulos cada) da casca externa ou interna. */
    private static void grade(float rel, boolean inverte) {
        float[] p = new float[3], n = new float[3];
        float[][] pos = new float[4][3];
        float[][] nor = new float[4][3];
        float[] tx = new float[4], ty = new float[4];
        int[][] idx = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int j = 0; j < COLS - 1; j++) {
            for (int i = 0; i < ROWS - 1; i++) {
                for (int k = 0; k < 4; k++) {
                    int jj = j + idx[k][0], ii = i + idx[k][1];
                    float u = -1f + 2f * jj / (COLS - 1f);
                    float v = ii / (ROWS - 1f);
                    ponto(u, v, rel, p);
                    normal(u, v, rel, n);
                    pos[k] = new float[]{p[0], p[1], p[2]};
                    nor[k] = new float[]{n[0], n[1], n[2]};
                    tx[k] = 0.5f * (u + 1f);
                    ty[k] = 1f - v;
                }
                int[] tri = inverte ? new int[]{0, 2, 1, 0, 3, 2} : new int[]{0, 1, 2, 0, 2, 3};
                for (int k = 0; k < 6; k++) {
                    int q = tri[k];
                    vert(pos[q], nor[q], tx[q], ty[q]);
                }
            }
        }
    }

    /** Aba (rim): liga a borda externa à interna — é o que fecha o volume. */
    private static void tira(float[][] fora, float[][] dentro, float[] tu, float[] tv) {
        float[] nrm = new float[3];
        for (int k = 0; k < fora.length - 1; k++) {
            for (int t = 0; t < 2; t++) {
                float[] a = fora[k];
                float[] b = t == 0 ? fora[k + 1] : dentro[k + 1];
                float[] c = t == 0 ? dentro[k + 1] : dentro[k];
                float ax = b[0] - a[0], ay = b[1] - a[1], az = b[2] - a[2];
                float bx = c[0] - a[0], by = c[1] - a[1], bz = c[2] - a[2];
                nrm[0] = ay * bz - az * by;
                nrm[1] = az * bx - ax * bz;
                nrm[2] = ax * by - ay * bx;
                float len = (float) Math.sqrt(nrm[0] * nrm[0] + nrm[1] * nrm[1] + nrm[2] * nrm[2]);
                if (len < 1e-6f) {
                    nrm[0] = 0f; nrm[1] = 0f; nrm[2] = -1f;
                } else {
                    nrm[0] /= len; nrm[1] /= len; nrm[2] /= len;
                }
                float mx = (a[0] + b[0] + c[0]) / 3f;
                float my = (a[1] + b[1] + c[1]) / 3f;
                float mz = (a[2] + b[2] + c[2]) / 3f;
                // a normal tem de apontar para FORA (longe de um ponto atrás da máscara)
                float rx = 0f, ry = my, rz = Z_FRENTE + 0.9f;
                if (nrm[0] * (mx - rx) + nrm[1] * (my - ry) + nrm[2] * (mz - rz) < 0f) {
                    nrm[0] = -nrm[0]; nrm[1] = -nrm[1]; nrm[2] = -nrm[2];
                }
                float[] ta = t == 0 ? new float[]{tu[k], tv[k]} : new float[]{tu[k], tv[k]};
                float[] tb = new float[]{tu[k + 1], tv[k + 1]};
                float[] tc = t == 0 ? tb : new float[]{tu[k], tv[k]};
                vert(a, nrm, ta[0], ta[1]);
                vert(b, nrm, tb[0], tb[1]);
                vert(c, nrm, tc[0], tc[1]);
            }
        }
    }

    private static float[][] borda(int lado, float rel) {
        float[] p = new float[3];
        int n = lado == 0 || lado == 1 ? COLS : ROWS;
        float[][] r = new float[n][3];
        for (int k = 0; k < n; k++) {
            int j, i;
            switch (lado) {
                case 0 -> { j = k; i = 0; }                  // embaixo (esq -> dir)
                case 1 -> { j = COLS - 1 - k; i = ROWS - 1; } // em cima (dir -> esq)
                case 2 -> { j = 0; i = ROWS - 1 - k; }        // esquerda (cima -> baixo)
                default -> { j = COLS - 1; i = k; }           // direita (baixo -> cima)
            }
            float u = -1f + 2f * j / (COLS - 1f);
            float v = i / (ROWS - 1f);
            ponto(u, v, rel, p);
            r[k] = new float[]{p[0], p[1], p[2]};
        }
        return r;
    }

    private static void construirMalha() {
        NV = 0;
        grade(1.0f, false);   // casca externa
        grade(0.0f, true);    // casca interna (normais para dentro)

        for (int lado = 0; lado < 4; lado++) {
            float[][] fora = borda(lado, 1.0f);
            float[][] dentro = borda(lado, 0.0f);
            int n = fora.length;
            float[] tu = new float[n], tv = new float[n];
            for (int k = 0; k < n; k++) {
                int j, i;
                switch (lado) {
                    case 0 -> { j = k; i = 0; }
                    case 1 -> { j = COLS - 1 - k; i = ROWS - 1; }
                    case 2 -> { j = 0; i = ROWS - 1 - k; }
                    default -> { j = COLS - 1; i = k; }
                }
                float u = -1f + 2f * j / (COLS - 1f);
                float v = i / (ROWS - 1f);
                tu[k] = 0.5f * (u + 1f);
                tv[k] = 1f - v;
            }
            tira(fora, dentro, tu, tv);
        }
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
        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEX));
        for (int k = 0; k + 2 < NV; k += 3) {
            for (int v = 0; v < 3; v++) {
                int q = k + v;
                vc.addVertex(pose.last(), VPX[q], VPY[q], VPZ[q])
                        .setColor(255, 255, 255, 255)
                        .setUv(VTU[q], VTV[q])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(luz)
                        .setNormal(VNX[q], VNY[q], VNZ[q]);
            }
        }
        pose.popPose();
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

    /** quantos triângulos a máscara tem (usado no check.py / log). */
    public static int triangulos() {
        return NV / 3;
    }
}
