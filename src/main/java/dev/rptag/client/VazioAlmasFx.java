package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import dev.rptag.VazioAlmasPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * (5.13.6) AS ALMAS VAZIAS — o CORPO delas, desenhado em geometria.
 *
 * <p>Pedido do dono: <i>"faça almas vazias que ficaram perdidas naquele local"</i>.
 *
 * <h2>Por que geometria e não partícula</h2>
 * Regra antiga do mod (pedido 50): <i>"se o player desativar as partículas, os efeitos somem"</i>.
 * Uma alma feita só de {@code ParticleTypes.SOUL} desapareceria no mínimo de partículas — então o
 * corpo é desenhado com {@link RenderType#debugQuads()} (POSITION_COLOR, blend normal), igual ao
 * portal e à aura.
 *
 * <h2>Como a alma é desenhada</h2>
 * <ul>
 *   <li><b>núcleo</b>: três quads em billboard (sempre de frente para a câmera), do maior e mais
 *       fraco ao menor e mais forte — lavanda-azulado ({@code #b9c2e0}), quase branco no meio;</li>
 *   <li><b>véu</b>: tiras verticais que descem e se desfazem (o "manto" gasto da alma); elas
 *       ondulam com o tempo e ficam mais finas na ponta;</li>
 *   <li><b>olhos</b>: dois pontos fracos, um pouco acima do centro — de longe parece um rosto;</li>
 *   <li><b>rastro</b>: um fio de poeira subindo devagar (a alma está se desfazendo).</li>
 * </ul>
 *
 * <p>Elas aparecem até 140 blocos, somem suave na distância e nunca existem fora do vazio.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class VazioAlmasFx {

    private VazioAlmasFx() {
    }

    private static final ResourceKey<Level> EXILIO = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "exilio"));

    // (5.16.3) A SOMBRA DO PORTAL — o dono: "é uma sombra bizarra que quando vira
    // algo". Antes era um vulto BRANCO leitoso (parecia um emaranhado de papel).
    //
    // Agora: silhueta quase PRETA (a "boca do vazio"), com
    //  - CONTRA LUZ violeta bem fraco atrás (sem ele a sombra preta sumiria no breu),
    //  - CONTORNO 10% maior em violeta escuro (é o que desenha a borda do capuz),
    //  - duas BRASAS nos olhos, que só acendem quando ela vira de frente,
    //  - uma barra de FARRAPOS que balançam e FUMAÇA subindo da ponta do capuz.
    // A prévia offline (arte/previa_sombra_vazio.py) usa ESTES números: o que se vê
    // na imagem é o que o jogo desenha.
    private static final float[] CORPO = { 0.010F, 0.009F, 0.016F };
    private static final float[] CONTORNO = { 0.20F, 0.13F, 0.34F };
    private static final float[] LUZ = { 0.30F, 0.18F, 0.52F };
    private static final float[] OLHO = { 1.00F, 0.90F, 0.70F };
    private static final float[] FUMA = { 0.07F, 0.05F, 0.11F };

    /** altura total da sombra, em blocos (ela é ALTA: dois blocos). */
    private static final float H_SOMBRA = 2.00F;
    /** até onde o CORPO sobe: dali pra cima é o capuz. */
    private static final float CORPO_ATE = 0.78F;

    private static float[] pontos = new float[0];
    private static long recebidoEm;
    private static long ultimoQuadro;

    /** o servidor mandou as almas (1x por segundo). */
    public static void receber(VazioAlmasPayload payload) {
        try {
            pontos = payload.pontos() == null ? new float[0] : payload.pontos().clone();
            recebidoEm = System.currentTimeMillis();
        } catch (Throwable ignorado) {
            pontos = new float[0];
        }
    }

    /** saiu do vazio: esquece as almas. */
    public static void limpar() {
        pontos = new float[0];
        recebidoEm = 0L;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        try {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || pontos.length < 3) {
                return;
            }
            if (!mc.level.dimension().equals(EXILIO)) {
                return;                                     // alma só existe no vazio
            }
            long agora = System.currentTimeMillis();
            if (agora - recebidoEm > 6000L) {
                return;                                     // servidor parou de mandar: não deixa fantasma
            }
            if (agora == ultimoQuadro) {
                return;                                     // um desenho por quadro
            }
            ultimoQuadro = agora;

            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());

            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            org.joml.Matrix4f mat = pose.last().pose();

            int quantas = Math.min(VazioAlmasPayload.MAX, pontos.length / 3);
            for (int i = 0; i < quantas; i++) {
                float x = pontos[i * 3], y = pontos[i * 3 + 1], z = pontos[i * 3 + 2];
                double d = cam.distanceTo(new Vec3(x, y, z));
                if (d > 140.0) {
                    continue;
                }
                try {
                    desenharAlma(vc, mat, cam, x, y, z, i, d, agora);
                } catch (Throwable ignorado) {
                    // uma alma quebrada não derruba as outras
                }
            }

            pose.popPose();
            buf.endBatch(RenderType.debugQuads());
        } catch (Throwable erro) {
            pontos = new float[0];
            RPTagMod.LOGGER.warn("RP Tag: almas do vazio desligadas nesta sessao", erro);
        }
    }

    // ---------------------------------------------------------------- desenho
    private static void desenharAlma(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 cam,
            float x, float y, float z, int indice, double dist, long agora) {
        float t = (agora % 100_000L) / 1000.0F;
        float fase = indice * 1.7F;

        // flutua e treme: cada sombra tem o seu ritmo (nunca ficam iguais)
        float subiu = 0.09F * Mth.sin(t * 0.7F + fase);
        float tremor = 0.035F * Mth.sin(t * 1.3F + fase * 2.0F);
        float cx = x + tremor;
        float cy = y + subiu;
        float cz = z + 0.05F * Mth.cos(t * 1.1F + fase);

        // de longe ela apaga devagar (140 -> 80 blocos)
        float perto = (float) Mth.clamp((140.0 - dist) / 60.0, 0.0, 1.0);
        float base = 0.92F * perto;
        if (base <= 0.02F) {
            return;
        }

        // vetores do billboard: o quad sempre de frente pra câmera
        Vec3 dir = new Vec3(cx - cam.x, cy - cam.y, cz - cam.z);
        if (dir.lengthSqr() < 1.0e-4) {
            return;
        }
        dir = dir.normalize();
        Vec3 direita = new Vec3(0.0, 1.0, 0.0).cross(dir).normalize();
        Vec3 cima = dir.cross(direita).normalize();

        // ---------------------------------------------------------------- VIRA
        // "que quando vira algo": ela gira devagar no próprio eixo. De lado a
        // silhueta AFINA (vira uma lâmina); de frente o capuz abre e os olhos acendem.
        float vira = (t * 0.22F + fase * 0.37F) % 1.0F;
        float ang = vira * 6.2832F;
        float afina = 0.14F + 0.86F * Math.abs(Mth.cos(ang));
        float cara = Mth.clamp((Mth.cos(ang) - 0.50F) / 0.50F, 0.0F, 1.0F);

        float y0 = cy - 0.05F;
        float H = H_SOMBRA;
        float meio = y0 + H * 0.62F;

        // 1) CONTRA LUZ — o halo violeta que faz a sombra preta aparecer no breu
        halo(vc, mat, direita, cima, cx, meio, cz,
                0.95F * (0.55F + 0.45F * afina), LUZ, 0.30F * base, 6);

        // 2/3) CORPO: contorno 10% maior atrás, silhueta preta na frente
        int passos = 26;
        for (int i = 0; i < passos; i++) {
            float u0 = CORPO_ATE * i / (float) passos;
            float u1 = CORPO_ATE * (i + 1) / (float) passos;
            float w0c = perfil(u0) * 1.10F * afina;
            float w1c = perfil(u1) * 1.10F * afina;
            tira(vc, mat, direita, cima, cx, cz, y0 + H * u0, y0 + H * u1, w0c, w1c,
                    cor(CONTORNO, base * 0.55F * (0.5F + 0.5F * u0 / CORPO_ATE)));
        }
        for (int i = 0; i < passos; i++) {
            float u0 = CORPO_ATE * i / (float) passos;
            float u1 = CORPO_ATE * (i + 1) / (float) passos;
            tira(vc, mat, direita, cima, cx, cz, y0 + H * u0, y0 + H * u1,
                    perfil(u0) * afina, perfil(u1) * afina, cor(CORPO, base * 0.99F));
        }

        // 4) O CAPUZ — a ponta INCLINA pra um lado (é o que faz parecer que ela olha)
        float h0 = y0 + H * 0.74F;
        float h1 = y0 + H * 1.00F;
        float inclina = 0.055F * afina;
        int passosC = 10;
        for (int i = 0; i < passosC; i++) {
            float t0 = i / (float) passosC;
            float t1 = (i + 1) / (float) passosC;
            float u0 = h0 + (h1 - h0) * t0;
            float u1 = h0 + (h1 - h0) * t1;
            float w0 = (0.250F - 0.232F * (float) Math.pow(t0, 1.6)) * afina;
            float w1 = (0.250F - 0.232F * (float) Math.pow(t1, 1.6)) * afina;
            float ox0 = cx + (float) direita.x * inclina * t0 * t0;
            float oz0 = cz + (float) direita.z * inclina * t0 * t0;
            float ox1 = cx + (float) direita.x * inclina * t1 * t1;
            float oz1 = cz + (float) direita.z * inclina * t1 * t1;
            tira(vc, mat, direita, cima, ox0, oz0, u0, u1, w0 * 1.10F, w1 * 1.10F,
                    cor(CONTORNO, base * 0.45F * (1.0F - 0.4F * t0)));
            tira(vc, mat, direita, cima, ox1, oz1, u0, u1, w0, w1, cor(CORPO, base * 0.99F));
        }

        // a CARA: um vão em V (a boca do capuz), com as duas brasas dentro
        if (cara > 0.02F) {
            float yf0 = y0 + H * 0.795F;
            float yf1 = y0 + H * 0.945F;
            tira(vc, mat, direita, cima, cx, cz, yf0, yf1,
                    0.150F * afina, 0.070F * afina, cor(new float[] { 0.0F, 0.0F, 0.0F }, base * 0.94F * cara));

            float yo = y0 + H * 0.895F;
            float olho = (0.020F + 0.008F * cara) * (0.40F + 0.60F * afina);
            float sep = 0.055F * afina;
            for (int l = -1; l <= 1; l += 2) {
                float ox = cx + (float) direita.x * (l * sep);
                float oz = cz + (float) direita.z * (l * sep);
                halo(vc, mat, direita, cima, ox, yo, oz, olho * 4.5F, OLHO, 0.45F * base * cara, 4);
                quad(vc, mat, ox, yo, oz, direita, cima, olho, olho * 0.60F,
                        cor(OLHO, base * (0.35F + 0.65F * cara)));
            }

            // a boca: uma fenda fina logo abaixo do vão
            tira(vc, mat, direita, cima, cx, cz, y0 + H * 0.805F, y0 + H * 0.840F,
                    0.030F * afina * cara, 0.012F * afina * cara,
                    cor(new float[] { 0.0F, 0.0F, 0.0F }, base * cara));
        }

        // 5) FARRAPOS na barra (7 tiras de comprimento diferente, balançando)
        int tiras = 7;
        for (int sTira = 0; sTira < tiras; sTira++) {
            float f = sTira / (float) (tiras - 1) - 0.5F;
            float onda = 0.05F * Mth.sin(t * 1.1F + fase + sTira * 1.3F);
            float px = cx + (float) direita.x * (f * perfil(0.02F) * afina * 1.85F + onda);
            float pz = cz + (float) direita.z * (f * perfil(0.02F) * afina * 1.85F + onda);
            float comp = (0.26F + 0.30F * (1.0F - Math.abs(f)))
                    * (0.80F + 0.20F * Mth.sin(t * 0.9F + sTira * 2.3F));
            float w = 0.050F * (1.0F - Math.abs(f) * 0.55F) * (0.35F + 0.65F * afina);
            quadVertical(vc, mat, direita, cima, px, pz, y0 + 0.06F, y0 - comp * 0.55F, w,
                    cor(CORPO, base * 0.9F));
            quadVertical(vc, mat, direita, cima, px, pz, y0 - comp * 0.55F, y0 - comp, w * 0.45F,
                    cor(CONTORNO, base * 0.30F));
        }

        // 6) A FUMAÇA subindo da ponta do capuz
        float topo = y0 + H;
        for (int k = 0; k < 5; k++) {
            float fr = (t * 0.28F + k * 0.21F + fase * 0.1F) % 1.0F;
            float ry = topo - 0.06F + fr * 1.0F;
            float rx = cx + 0.11F * Mth.sin(t * 0.7F + k * 2.0F + fase) * (0.3F + fr);
            float rz = cz + 0.11F * Mth.cos(t * 0.6F + k * 2.3F + fase) * (0.3F + fr);
            float sz = 0.075F * (1.0F - fr * 0.55F);
            quad(vc, mat, rx, ry, rz, direita, cima, sz, sz * 1.4F,
                    cor(FUMA, base * 0.34F * (1.0F - fr)));
        }
    }

    /** halo macio: quads concêntricos com queda quadrática (o contraluz da sombra). */
    private static void halo(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 direita, Vec3 cima,
            float x, float y, float z, float raio, float[] rgb, float alpha, int passos) {
        for (int i = 0; i < passos; i++) {
            float f = (i + 1) / (float) passos;
            float a = alpha * (1.0F - f) * (1.0F - f) / passos * 3.0F;
            quad(vc, mat, x, y, z, direita, cima, raio * f, raio * f * 1.15F, cor(rgb, a));
        }
    }

    /** suaviza (smoothstep) entre dois valores. */
    private static float suave(float a, float b, float u) {
        u = Mth.clamp(u, 0.0F, 1.0F);
        u = u * u * (3.0F - 2.0F * u);
        return a + (b - a) * u;
    }

    /**
     * Largura da silhueta em cada altura {@code u} (0 = barra da saia, 1 = ponta do
     * capuz). Os mesmos números da prévia ({@code arte/previa_sombra_vazio.py}):
     * saia larga embaixo, cintura, ombros e o capuz fechando na ponta.
     */
    private static float perfil(float u) {
        if (u < 0.50F) {
            return suave(0.34F, 0.20F, u / 0.50F);
        }
        if (u < 0.68F) {
            return suave(0.20F, 0.26F, (u - 0.50F) / 0.18F);
        }
        if (u < 0.76F) {
            return suave(0.26F, 0.21F, (u - 0.68F) / 0.08F);
        }
        float v = (u - 0.76F) / 0.24F;
        return 0.21F * (float) Math.pow(1.0F - v, 1.6) + 0.012F;
    }

    // ---------------------------------------------------------------- helpers
    /**
     * Tira de silhueta entre duas alturas, com largura própria em cima e embaixo
     * (é o que dá a saia que alarga, a cintura e o capuz que fecha na ponta).
     */
    private static void tira(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 direita, Vec3 cima,
            float cx, float cz, float y0, float y1, float w0, float w1, int cor) {
        float rx0 = (float) direita.x * w0, rz0 = (float) direita.z * w0;
        float rx1 = (float) direita.x * w1, rz1 = (float) direita.z * w1;
        vc.addVertex(mat, cx - rx0, y0, cz - rz0).setColor(cor);
        vc.addVertex(mat, cx + rx0, y0, cz + rz0).setColor(cor);
        vc.addVertex(mat, cx + rx1, y1, cz + rz1).setColor(cor);
        vc.addVertex(mat, cx - rx1, y1, cz - rz1).setColor(cor);
    }

    /** quad em billboard centrado em (x,y,z). */
    private static void quad(VertexConsumer vc, org.joml.Matrix4f mat,
            float x, float y, float z, Vec3 direita, Vec3 cima, float hw, float hh, int cor) {
        float rx = (float) direita.x * hw, ry = (float) direita.y * hw, rz = (float) direita.z * hw;
        float ux = (float) cima.x * hh, uy = (float) cima.y * hh, uz = (float) cima.z * hh;
        vc.addVertex(mat, x - rx - ux, y - ry - uy, z - rz - uz).setColor(cor);
        vc.addVertex(mat, x + rx - ux, y + ry - uy, z + rz - uz).setColor(cor);
        vc.addVertex(mat, x + rx + ux, y + ry + uy, z + rz + uz).setColor(cor);
        vc.addVertex(mat, x - rx + ux, y - ry + uy, z - rz + uz).setColor(cor);
    }

    /** tira vertical do véu (de y0 até y1), largura fixa no eixo da câmera. */
    private static void quadVertical(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 direita,
            Vec3 cima, float x, float z, float y0, float y1, float hw, int cor) {
        float rx = (float) direita.x * hw, rz = (float) direita.z * hw;
        vc.addVertex(mat, x - rx, y0, z - rz).setColor(cor);
        vc.addVertex(mat, x + rx, y0, z + rz).setColor(cor);
        vc.addVertex(mat, x + rx, y1, z + rz).setColor(cor);
        vc.addVertex(mat, x - rx, y1, z - rz).setColor(cor);
    }

    /** cor ARGB a partir de float[3] e alpha. */
    private static int cor(float[] rgb, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        int r = (int) (Mth.clamp(rgb[0], 0.0F, 1.0F) * 255.0F);
        int g = (int) (Mth.clamp(rgb[1], 0.0F, 1.0F) * 255.0F);
        int b = (int) (Mth.clamp(rgb[2], 0.0F, 1.0F) * 255.0F);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
