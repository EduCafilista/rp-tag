package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.rptag.CanonCoracaoBlockEntity;
import dev.rptag.RPTagMod;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.util.Mth;

/**
 * (4.4.0) O CORAÇÃO 3D do bloco canônico — agora um CORPO DE VERDADE.
 *
 * <p><b>Como o giro funciona no chão.</b> Um bloco comum não gira: o modelo dele é
 * estático e só mudaria por blockstate (16 rotações fixas, exigindo updateBlock a
 * cada tick — caro e travado). O que gira é o <b>BER</b> ({@link BlockEntityRenderer}):
 * o jogo chama {@link #render} a cada quadro, e a rotação vem de um RELÓGIO DE PAREDE
 * ({@code System.nanoTime()}), não de tick. Consequências:
 * <ul>
 *   <li>gira mesmo sem ticker no BlockEntity (nada de {@code getTicker});</li>
 *   <li>não trava com lag, não "pula" quando o mundo pausa e continua de onde estava;</li>
 *   <li>continua girando com o chunk carregado e para sozinho quando o jogador se afasta
 *       ({@code getViewDistance}).</li>
 * </ul>
 *
 * <p><b>O que mudou em relação ao "máscara extrudada" (3.86):</b> antes cada linha da
 * pixel-art virava uma CAIXA de 5px de profundidade — de frente parecia um coração, mas
 * ao girar virava um amontoado de tábuas. Agora o coração é um <b>sólido voxel</b>: a
 * silhueta 15×13 é inflada por uma <i>distance transform</i> (o miolo fica gordo, as
 * bordas finas), gerando 9 fatias de profundidade. Resultado: é um coração de verdade
 * visto de QUALQUER ângulo.
 *
 * <p><b>Qualidade:</b>
 * <ul>
 *   <li>só as faces EXPOSTAS são desenhadas (faces internas descartadas);</li>
 *   <li>sombreamento por normal + luz de cima à esquerda, assado na cor (o
 *       {@code RenderType.lightning()} não usa normal nem luz do mundo);</li>
 *   <li>gradiente vertical (rosa claro no topo → vinho na ponta);</li>
 *   <li>brilho especular no lóbulo esquerdo (acompanha o giro, porque é geometria);</li>
 *   <li>batida em dois tempos ("tum-tum") + flutuação + inclinação leve;</li>
 *   <li>tudo pré-computado uma vez em arrays — o render só percorre os quads.</li>
 * </ul>
 */
public class CanonCoracaoRenderer implements BlockEntityRenderer<CanonCoracaoBlockEntity> {

    /** silhueta 15×13 (topo → ponta em V), a mesma leitura do coração da lore. */
    private static final String[] MASCARA = {
            "..####...####..",
            ".######.######.",
            "###############",
            "###############",
            "###############",
            ".#############.",
            ".#############.",
            "..###########..",
            "...#########...",
            "....#######....",
            ".....#####.....",
            "......###......",
            ".......#......."
    };
    private static final int W = 15, H = 13, NY = 13;
    /** quanto a inflação abre em profundidade (1.0 = esfera; >1 = gordinho). */
    private static final float FATOR_PROF = 1.35F;
    private static final float PX = 1.0F / 16.0F;

    /** sólido[x][y][fatia] — inflado pela distance transform (miolo gordo, borda fina). */
    private static final boolean[][][] SOLIDO = new boolean[W][H][NY];

    /** cores: topo rosa claro → ponta vinho. */
    private static final int TOPO = 0xF75C7C, MEIO = 0xDC183E, PONTA = 0x840826;

    // ---- quads pré-computados (4 vértices × 3 coords + cor) ----
    private static final int MAXQ = 3000;
    private static final float[] VX = new float[MAXQ * 4];
    private static final float[] VY = new float[MAXQ * 4];
    private static final float[] VZ = new float[MAXQ * 4];
    private static final int[] VC = new int[MAXQ];
    private static int NQ;

    static {
        construirSolido();
        construirFaces();
    }

    public CanonCoracaoRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx) {
    }

    // ============================================================== geometria
    /** infla a silhueta: cada pixel ganha profundidade conforme a distância até a borda. */
    private static void construirSolido() {
        int[][] dist = new int[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                dist[y][x] = MASCARA[y].charAt(x) == '#' ? 99 : 0;
            }
        }
        for (int pass = 0; pass < 16; pass++) {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    if (dist[y][x] == 0) continue;
                    int m = dist[y][x];
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || ny < 0 || nx >= W || ny >= H) continue;
                            m = Math.min(m, dist[ny][nx] + 1);
                        }
                    }
                    dist[y][x] = m;
                }
            }
        }
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (MASCARA[y].charAt(x) != '#') continue;
                float meio = Math.min((NY - 1) / 2.0F, Math.max(0.5F, (dist[y][x] - 0.5F) * FATOR_PROF));
                for (int z = 0; z < NY; z++) {
                    float off = Math.abs(z - (NY - 1) / 2.0F);
                    SOLIDO[x][y][z] = off <= meio;
                }
            }
        }
    }

    private static boolean tem(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < W && y < H && z < NY && SOLIDO[x][y][z];
    }

    /** desenha só as 6 faces de cada voxel que dão para o vazio. */
    private static void construirFaces() {
        NQ = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                for (int z = 0; z < NY; z++) {
                    if (!SOLIDO[x][y][z]) continue;
                    float x0 = (x - (W - 1) / 2.0F) * PX, x1 = x0 + PX;
                    float y0 = (H - 1 - y) * PX, y1 = y0 + PX;
                    float z0 = (z - (NY - 1) / 2.0F) * PX, z1 = z0 + PX;
                    int base = cor(x, y, z);
                    if (!tem(x, y - 1, z)) add(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, shade(base, 1.00F));
                    if (!tem(x, y + 1, z)) add(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, shade(base, 0.52F));
                    if (!tem(x, y, z - 1)) add(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, shade(base, 0.70F));
                    if (!tem(x, y, z + 1)) add(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, shade(base, 0.92F));
                    if (!tem(x - 1, y, z)) add(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, shade(base, 0.64F));
                    if (!tem(x + 1, y, z)) add(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, shade(base, 0.80F));
                }
            }
        }
    }

    private static void add(float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz, int cor) {
        if (NQ >= MAXQ) return;
        int o = NQ * 4;
        VX[o] = ax; VY[o] = ay; VZ[o] = az;
        VX[o + 1] = bx; VY[o + 1] = by; VZ[o + 1] = bz;
        VX[o + 2] = cx; VY[o + 2] = cy; VZ[o + 2] = cz;
        VX[o + 3] = dx; VY[o + 3] = dy; VZ[o + 3] = dz;
        VC[NQ] = cor;
        NQ++;
    }

    /** cor do voxel: gradiente vertical + brilho especular no lóbulo esquerdo. */
    private static int cor(int x, int y, int z) {
        float u = y / (float) (H - 1);
        int c = u < 0.5F ? mix(TOPO, MEIO, u * 2F) : mix(MEIO, PONTA, (u - 0.5F) * 2F);
        // luz ambiente: o miolo é mais claro que as bordas (volume)
        float dx = (x - (W - 1) / 2.0F) / (W / 2.0F);
        float dz = (z - (NY - 1) / 2.0F) / (NY / 2.0F);
        // o miolo é mais vivo que as bordas (volume), sem lavar a cor
        float miolo = 1.0F + 0.12F * (1.0F - (dx * dx + dz * dz)) - 0.06F * u;
        c = escala(c, miolo);
        // especular QUENTE no lóbulo esquerdo (x≈4, y≈1) — nunca branco
        float ex = (x - 4.0F) / 2.4F, ey = (y - 1.4F) / 2.2F;
        float spec = Math.max(0.0F, 1.0F - (ex * ex + ey * ey)) * 0.26F;
        return clareia(c, spec);
    }

    private static int escala(int rgb, float f) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((rgb & 0xFF) * f));
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float f) {
        f = Math.max(0F, Math.min(1F, f));
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * f);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * f);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * f);
        return (r << 16) | (g << 8) | bl;
    }

    private static int clareia(int rgb, float f) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += Math.round((255 - r) * f);
        g += Math.round((255 - g) * f);
        b += Math.round((255 - b) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int shade(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    // ============================================================== render
    @Override
    public void render(CanonCoracaoBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        try {
            double t = System.nanoTime() / 1.0e9;

            // fase por posição: dois corações no mundo não giram em sincronia
            int fase = (be.getBlockPos().getX() * 37 + be.getBlockPos().getZ() * 91) & 1023;
            float ang = (float) ((t * 24.0) % 360.0) + fase * 0.3515625F;   // ~15 s por volta

            // batida em dois tempos (tum-tum) em vez de seno puro
            double bt = (t * 1.15) % 1.0;
            float b1 = (float) Math.exp(-bt * 18.0);
            float b2 = (float) Math.exp(-Math.max(0, bt - 0.22) * 20.0) * (bt > 0.22 ? 1 : 0);
            float pulso = 1.0F + 0.055F * (b1 + 0.55F * b2);

            float dy = Mth.sin((float) (t * 0.9)) * 0.022F;                  // flutua
            float incl = Mth.sin((float) (t * 0.53)) * 5.0F;                 // inclina devagar
            float brilho = 0.90F + 0.10F * (b1 + 0.5F * b2);

            pose.pushPose();
            pose.translate(0.5, 0.03 + dy, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(ang));
            pose.mulPose(Axis.ZP.rotationDegrees(incl));
            pose.mulPose(Axis.XP.rotationDegrees(6.0F));
            pose.scale(pulso, pulso, pulso);

            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            for (int q = 0; q < NQ; q++) {
                int cor = shade(VC[q], brilho);
                int r = (cor >> 16) & 0xFF, g = (cor >> 8) & 0xFF, b = cor & 0xFF;
                int o = q * 4;
                // frente (ordem horária) + verso (o lightning tem cull)
                vc.addVertex(pose.last(), VX[o], VY[o], VZ[o]).setColor(r, g, b, 255);
                vc.addVertex(pose.last(), VX[o + 1], VY[o + 1], VZ[o + 1]).setColor(r, g, b, 255);
                vc.addVertex(pose.last(), VX[o + 2], VY[o + 2], VZ[o + 2]).setColor(r, g, b, 255);
                vc.addVertex(pose.last(), VX[o + 3], VY[o + 3], VZ[o + 3]).setColor(r, g, b, 255);
                vc.addVertex(pose.last(), VX[o + 3], VY[o + 3], VZ[o + 3]).setColor(r, g, b, 255);
                vc.addVertex(pose.last(), VX[o + 2], VY[o + 2], VZ[o + 2]).setColor(r, g, b, 255);
                vc.addVertex(pose.last(), VX[o + 1], VY[o + 1], VZ[o + 1]).setColor(r, g, b, 255);
                vc.addVertex(pose.last(), VX[o], VY[o], VZ[o]).setColor(r, g, b, 255);
            }
            pose.popPose();
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: erro no coracao canonico", t);
        }
    }

    /** quantos quads o coração tem (usado na verificação). */
    public static int quads() {
        return NQ;
    }
}
