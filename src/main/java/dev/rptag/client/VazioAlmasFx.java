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

    /** paleta lavanda: núcleo, meio, borda. */
    private static final float[] NUCLEO = { 0.86F, 0.89F, 0.98F };
    private static final float[] MEIO = { 0.62F, 0.66F, 0.86F };
    private static final float[] BORDA = { 0.34F, 0.30F, 0.52F };

    /** posições recebidas do servidor (x, y, z por alma). */
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

        // flutua e respira: cada alma tem o seu ritmo
        float subiu = 0.06F * Mth.sin(t * 0.9F + fase);
        float tremor = 0.035F * Mth.sin(t * 1.7F + fase * 2.0F);
        float cx = x + tremor;
        float cy = y + subiu;
        float cz = z + 0.04F * Mth.cos(t * 1.1F + fase);

        // a distância some suave: 140 -> 40 blocos ela vai apagando
        float perto = (float) Mth.clamp((140.0 - dist) / 60.0, 0.0, 1.0);
        float base = 0.55F * perto;
        if (base <= 0.02F) {
            return;
        }

        // vetores do billboard (o quad sempre de frente para a câmera)
        Vec3 dir = new Vec3(cx - cam.x, cy - cam.y, cz - cam.z);
        if (dir.lengthSqr() < 1.0e-4) {
            return;
        }
        dir = dir.normalize();
        Vec3 direita = new Vec3(0.0, 1.0, 0.0).cross(dir).normalize();
        Vec3 cima = dir.cross(direita).normalize();

        // 1) o NÚCLEO: três camadas, da maior e fraca para a menor e forte
        float pulso = 0.88F + 0.12F * Mth.sin(t * 1.6F + fase);
        quad(vc, mat, cx, cy + 0.55F, cz, direita, cima, 0.34F * pulso, 0.46F * pulso,
                cor(BORDA, base * 0.34F));
        quad(vc, mat, cx, cy + 0.55F, cz, direita, cima, 0.21F * pulso, 0.30F * pulso,
                cor(MEIO, base * 0.60F));
        quad(vc, mat, cx, cy + 0.55F, cz, direita, cima, 0.10F * pulso, 0.15F * pulso,
                cor(NUCLEO, base * 0.92F));

        // 2) o VÉU: 5 tiras que descem e se desfazem (o manto gasto)
        int tiras = 5;
        float alturaVe = 0.95F;
        for (int s = 0; s < tiras; s++) {
            float f = s / (float) (tiras - 1) - 0.5F;            // -0.5 .. 0.5
            float onda = 0.05F * Mth.sin(t * 1.3F + fase + s * 1.1F);
            float x0 = cx + (float) direita.x * (f * 0.30F + onda);
            float z0 = cz + (float) direita.z * (f * 0.30F + onda);
            float largura = 0.075F * (1.0F - Math.abs(f) * 0.8F);
            float yTop = cy + 0.42F;
            float yBot = cy + 0.42F - alturaVe * (0.55F + 0.45F * (1.0F - Math.abs(f)));
            // duas metades do véu para ele escurecer/afinar na ponta
            quadVertical(vc, mat, direita, cima, x0, z0, yTop, (yTop + yBot) * 0.5F,
                    largura, cor(BORDA, base * 0.30F));
            quadVertical(vc, mat, direita, cima, x0, z0, (yTop + yBot) * 0.5F, yBot,
                    largura * 0.55F, cor(BORDA, base * 0.12F));
        }

        // 3) os OLHOS: dois pontos fracos acima do centro
        if (dist < 48.0) {
            float olho = 0.035F;
            for (int l = -1; l <= 1; l += 2) {
                float ox = cx + (float) direita.x * (l * 0.075F);
                float oz = cz + (float) direita.z * (l * 0.075F);
                quad(vc, mat, ox, cy + 0.70F, oz, direita, cima, olho, olho * 0.75F,
                        cor(new float[] { 0.92F, 0.94F, 1.0F }, base * 0.55F));
            }
        }

        // 4) o RASTRO: poeirinha subindo (a alma está se desfazendo)
        for (int k = 0; k < 3; k++) {
            float faseR = (t * 0.35F + k * 0.33F + fase * 0.1F) % 1.0F;
            float ry = cy + 0.5F + faseR * 1.1F;
            float rx = cx + 0.06F * Mth.sin(t * 0.8F + k * 2.0F + fase);
            float rz = cz + 0.06F * Mth.cos(t * 0.7F + k * 2.3F + fase);
            quad(vc, mat, rx, ry, rz, direita, cima, 0.022F, 0.022F,
                    cor(NUCLEO, base * 0.20F * (1.0F - faseR)));
        }
    }

    // ---------------------------------------------------------------- helpers
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
