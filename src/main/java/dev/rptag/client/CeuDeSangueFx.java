package dev.rptag.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * (5.46.0) <b>CÉU DE SANGUE — o terremoto virando lua de sangue.</b>
 *
 * <p>Pedido do dono: <i>"mexa no terremoto, o céu deve começar a ficar vermelho, como se
 * estivesse começando uma lua de sangue ... vai ser essa lua/SOL do local ... o céu tem que ficar
 * vermelho também além da mudança de sol/lua"</i>, e depois, vendo a 5.45.0:
 * <i>"a 'Lua' do terremoto, melhore isso, corrija esse erro, coloque essa lua"</i>.
 *
 * <h2>O que a 5.45.0 tinha de errado (e o que mudou)</h2>
 * <ul>
 *   <li>a lua era um monte de <b>discos desenhados em código</b> — no jogo virou uma bola
 *       vermelha chapada com uma estrela de rachaduras no meio. Agora é uma <b>TEXTURA</b>
 *       ({@code textures/environment/lua_de_sangue.png}, gerada por
 *       {@code arte/gerar_lua_de_sangue.py}): esfera de pedra, veios de lava brilhando e aro
 *       aceso, de verdade;</li>
 *   <li>o domo do céu era uma esfera de <b>12 anéis</b> e dava para ver as faixas concêntricas
 *       na tela. Agora são <b>48 anéis × 36 meridianos</b> com o alpha variando dezenas de
 *       vezes, então o degradê é liso.</li>
 * </ul>
 *
 * <h2>Como é desenhado</h2>
 * <ol>
 *   <li><b>DOMO VERMELHO</b> — casca de esfera de 150 blocos em volta da câmera, vinho no
 *       zênite e vermelho de sangue no horizonte;</li>
 *   <li><b>HALO</b> — anéis aditivos em volta do corpo celeste (o brilho difuso);</li>
 *   <li><b>LUA / SOL</b> — a textura, no plano e no lugar exatos do sol e da lua do vanilla
 *       (mesma conta: -90° em Y e o ângulo do dia em X), então nascem e se põem junto com
 *       o céu do mundo;</li>
 *   <li><b>NEBLINA</b> — {@link ViewportEvent.ComputeFogColor}: o horizonte fica vermelho de
 *       verdade.</li>
 * </ol>
 *
 * <p>Tudo é desenhado logo <b>depois do céu</b> ({@code AFTER_SKY}) e antes do terreno: o
 * relevo continua na frente (nada de céu pintado por cima do mundo) e nada disso aparece quando
 * o terremoto acaba — o estado vem do próprio {@link NeptuneFx}, que recebe o pacote do
 * servidor.
 *
 * <p>Regra de ouro do mod: erro aqui desliga o céu de sangue e o terremoto continua.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class CeuDeSangueFx {

    private CeuDeSangueFx() {
    }

    private static final ResourceLocation TEX_LUA = ResourceLocation.fromNamespaceAndPath(
            RPTagMod.MODID, "textures/environment/lua_de_sangue.png");
    private static final ResourceLocation TEX_SOL = ResourceLocation.fromNamespaceAndPath(
            RPTagMod.MODID, "textures/environment/sol_de_sangue.png");

    /** raio do domo (o céu do vanilla fica a ~100; o domo é maior pra ficar atrás de tudo). */
    private static final float RAIO = 150.0F;
    /** o domo: 48 anéis por 36 meridianos — liso o bastante pra não ver faixa. */
    private static final int ANEIS = 48;
    private static final int MERIDIANOS = 36;
    /** metade do lado da lua/sol no céu (o do vanilla é 15). */
    private static final float ASTRO = 17.0F;

    private static volatile boolean desligado = false;

    // ---------------------------------------------------------------- estado

    private static float forca() {
        try {
            if (!NeptuneFx.terremotoNaTela()) {
                return 0.0F;
            }
            return Math.max(0.0F, Math.min(1.0F, NeptuneFx.forcaDoTerremoto()));
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    // ---------------------------------------------------------------- névoa

    /** o horizonte fica vermelho de sangue enquanto o chão racha. */
    @SubscribeEvent
    public static void onNevoa(ViewportEvent.ComputeFogColor event) {
        try {
            float f = forca();
            if (f <= 0.01F) {
                return;
            }
            float mistura = 0.72F * f;
            event.setRed(Mth.lerp(mistura, event.getRed(), 0.42F));
            event.setGreen(Mth.lerp(mistura, event.getGreen(), 0.045F));
            event.setBlue(Mth.lerp(mistura, event.getBlue(), 0.035F));
        } catch (Throwable t) {
            // névoa é enfeite
        }
    }

    // ---------------------------------------------------------------- desenho

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (desligado || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        float f = forca();
        if (f <= 0.01F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        boolean tinhaDepth = false;
        try {
            float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            long agora = System.currentTimeMillis();
            // a lua "bate" como coração: ~2 pulsações por segundo
            float pulso = 0.5F + 0.5F * Mth.sin(agora * 0.0021F * Mth.PI);

            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            RenderType semTextura = RenderType.debugQuads();

            try {
                RenderSystem.disableDepthTest();
                tinhaDepth = true;
            } catch (Throwable ignorado) {
                // sem mexer no depth: desenha assim mesmo
            }

            pose.pushPose();

            // 1) DOMO + HALO (geometria sem textura)
            VertexConsumer vc = buf.getBuffer(semTextura);
            desenharDomo(vc, pose.last().pose(), f);

            // 2) CORPO CELESTE (textura) — na posição real do sol e da lua
            float tempoDoDia = mc.level.getTimeOfDay(pt);
            pose.pushPose();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90.0F));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(tempoDoDia * 360.0F));
            Matrix4f celeste = pose.last().pose();
            PoseStack.Pose poseCeleste = pose.last();

            // halo: três quads aditivos do mesmo tamanho, cada vez maiores e mais fracos
            for (int i = 3; i >= 1; i--) {
                float r = ASTRO * (1.0F + 0.42F * i);
                int alfa = (int) (255.0F * f * (0.10F / i));
                quad(vc, celeste, -100.0F, r, argb(alfa, 190, 24, 14));
                quad(vc, celeste, 100.0F, r, argb(alfa, 255, 96, 30));
            }
            buf.endBatch(semTextura);

            float bater = 1.0F + 0.03F * pulso;   // a lua respira de leve
            VertexConsumer lua = buf.getBuffer(RenderType.entityTranslucentEmissive(TEX_LUA));
            quadTextura(lua, poseCeleste, -100.0F, ASTRO * bater);
            buf.endBatch(RenderType.entityTranslucentEmissive(TEX_LUA));

            VertexConsumer sol = buf.getBuffer(RenderType.entityTranslucentEmissive(TEX_SOL));
            quadTextura(sol, poseCeleste, 100.0F, ASTRO * 1.05F * bater);
            buf.endBatch(RenderType.entityTranslucentEmissive(TEX_SOL));

            pose.popPose();
            pose.popPose();
        } catch (Throwable erro) {
            desligado = true;
            RPTagMod.LOGGER.error("RP Tag: ceu de sangue desligado nesta sessao", erro);
        } finally {
            if (tinhaDepth) {
                try {
                    RenderSystem.enableDepthTest();
                } catch (Throwable ignorado) {
                    // segue o jogo
                }
            }
        }
    }

    /**
     * A casca de esfera em volta da câmera, vermelho-sangue, mais densa no horizonte.
     *
     * <p>48 anéis: cada faixa tem o seu alpha, mas como são muitas e o alpha muda pouco de uma
     * para a outra, o degradê fica contínuo (na 5.45.0 eram 12 e dava para ver as faixas).
     */
    private static void desenharDomo(VertexConsumer vc, Matrix4f m, float f) {
        for (int i = 0; i < ANEIS; i++) {
            float p0 = (float) Math.PI * i / ANEIS;
            float p1 = (float) Math.PI * (i + 1) / ANEIS;
            // h = 1 no horizonte (p = pi/2), 0 no zênite e no nadir
            float h = 1.0F - Math.abs((float) (Math.PI * 0.5) - p0) / (float) (Math.PI * 0.5);
            h = h * h;
            int alfa = (int) (255.0F * f * (0.26F + 0.50F * h));
            if (alfa <= 1) {
                continue;
            }
            int cor = argb(alfa,
                    (int) Mth.lerp(h, 74.0F, 172.0F),
                    (int) Mth.lerp(h, 10.0F, 24.0F),
                    (int) Mth.lerp(h, 18.0F, 20.0F));
            for (int j = 0; j < MERIDIANOS; j++) {
                float t0 = (float) (Math.PI * 2.0) * j / MERIDIANOS;
                float t1 = (float) (Math.PI * 2.0) * (j + 1) / MERIDIANOS;
                float[] a = esfera(RAIO, p0, t0);
                float[] b = esfera(RAIO, p0, t1);
                float[] c = esfera(RAIO, p1, t1);
                float[] d = esfera(RAIO, p1, t0);
                quadrilatero(vc, m, a, b, c, d, cor);
            }
        }
    }

    private static float[] esfera(float r, float phi, float theta) {
        float sp = Mth.sin(phi), cp = Mth.cos(phi);
        return new float[] { r * sp * Mth.cos(theta), r * cp, r * sp * Mth.sin(theta) };
    }

    // ---------------------------------------------------------------- geometria

    private static int argb(int a, int r, int g, int b) {
        return (Math.max(0, Math.min(255, a)) << 24)
                | (Math.max(0, Math.min(255, r)) << 16)
                | (Math.max(0, Math.min(255, g)) << 8)
                | Math.max(0, Math.min(255, b));
    }

    /** um quadrilátero qualquer (4 cantos) com a matriz da pose. */
    private static void quadrilatero(VertexConsumer vc, Matrix4f m,
                                     float[] a, float[] b, float[] c, float[] d, int cor) {
        vc.addVertex(m, a[0], a[1], a[2]).setColor(cor);
        vc.addVertex(m, b[0], b[1], b[2]).setColor(cor);
        vc.addVertex(m, c[0], c[1], c[2]).setColor(cor);
        vc.addVertex(m, d[0], d[1], d[2]).setColor(cor);
    }

    /** um quad chato no plano XZ do referencial celeste, em {@code y} (o halo). */
    private static void quad(VertexConsumer vc, Matrix4f m, float y, float r, int cor) {
        // dois sentidos: de um lado ou do outro da câmera, um deles sempre aparece
        vc.addVertex(m, -r, y, -r).setColor(cor);
        vc.addVertex(m, r, y, -r).setColor(cor);
        vc.addVertex(m, r, y, r).setColor(cor);
        vc.addVertex(m, -r, y, r).setColor(cor);
        vc.addVertex(m, -r, y, r).setColor(cor);
        vc.addVertex(m, r, y, r).setColor(cor);
        vc.addVertex(m, r, y, -r).setColor(cor);
        vc.addVertex(m, -r, y, -r).setColor(cor);
    }

    /**
     * A LUA (ou o SOL) — a textura, num quad do plano celeste.
     *
     * <p>O formato do vértice dos tipos de entidade pede cor, UV, overlay, luz e normal; como é
     * emissivo, a luz é cheia (240,240) e não existe sombreamento.
     */
    private static void quadTextura(VertexConsumer vc, PoseStack.Pose pose, float y, float r) {
        int luz = 240;
        int overlayU = OverlayTexture.NO_OVERLAY & 0xFFFF;
        int overlayV = (OverlayTexture.NO_OVERLAY >> 16) & 0xFFFF;
        // os dois sentidos (o tipo de render pode ter culling)
        canto(vc, pose, -r, y, -r, 0.0F, 1.0F, luz, overlayU, overlayV);
        canto(vc, pose, r, y, -r, 1.0F, 1.0F, luz, overlayU, overlayV);
        canto(vc, pose, r, y, r, 1.0F, 0.0F, luz, overlayU, overlayV);
        canto(vc, pose, -r, y, r, 0.0F, 0.0F, luz, overlayU, overlayV);

        canto(vc, pose, -r, y, r, 0.0F, 0.0F, luz, overlayU, overlayV);
        canto(vc, pose, r, y, r, 1.0F, 0.0F, luz, overlayU, overlayV);
        canto(vc, pose, r, y, -r, 1.0F, 1.0F, luz, overlayU, overlayV);
        canto(vc, pose, -r, y, -r, 0.0F, 1.0F, luz, overlayU, overlayV);
    }

    private static void canto(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                              float u, float v, int luz, int ovU, int ovV) {
        vc.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setUv1(ovU, ovV)
                .setUv2(luz, luz)
                .setNormal(0.0F, -1.0F, 0.0F);
    }
}
