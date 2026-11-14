package dev.rptag.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * (5.45.0) <b>CÉU DE SANGUE — o terremoto tem que parecer o começo de uma lua de sangue.</b>
 *
 * <p>Pedido 101 do dono: <i>"mexa no terremoto, o céu deve começar a ficar vermelho, como se
 * estivesse começando uma lua de sangue... quanto ativar esse terremoto, vai ser essa lua/SOL do
 * local... o céu tem que ficar vermelho também além da mudança de sol/lua"</i> (ele mandou a
 * referência animada de uma lua rachada de lava).
 *
 * <h2>Como isso é desenhado (sem textura, sem shader, sem mixin)</h2>
 * <ol>
 *   <li><b>DOMO VERMELHO</b> — uma casca de esfera de 150 blocos em volta da câmera, com
 *       alpha maior perto do horizonte: o céu inteiro vira sangue, do zênite ao chão;</li>
 *   <li><b>LUA/SOL DE SANGUE</b> — desenhados <b>na posição exata</b> do sol e da lua do
 *       vanilla (mesma conta: -90° em Y e depois o ângulo do dia em X), então eles nascem e se
 *       põem no lugar certo, andando com o tempo do mundo;</li>
 *   <li>o corpo celeste é uma <b>esfera rachada de lava</b>: disco escuro, <b>rachaduras
 *       laranja que pulsam</b>, halo vermelho em volta e um brilho na borda — a mesma ideia da
 *       referência que ele mandou;</li>
 *   <li><b>NEBLINA</b> — {@link ViewportEvent.ComputeFogColor}: o horizonte fica vermelho de
 *       verdade (é o que dá a sensação de "o mundo mudou"), não só um filtro na tela.</li>
 * </ol>
 *
 * <p>Tudo é desenhado logo <b>depois do céu</b> ({@code AFTER_SKY}) e antes do terreno: assim o
 * relevo, as árvores e as construções continuam na frente (nada de céu pintado por cima do
 * mundo) e nada disso aparece quando o terremoto acaba — o estado vem do próprio
 * {@link NeptuneFx}, que recebe o pacote do servidor.
 *
 * <p>Regra de ouro do mod: erro aqui desliga o céu de sangue e o terremoto continua.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class CeuDeSangueFx {

    private CeuDeSangueFx() {
    }

    /** raio do domo (o céu do vanilla fica a ~100; o domo é maior pra ficar atrás de tudo). */
    private static final float RAIO = 150.0F;
    /** quantos meridianos/paralelos o domo tem (quanto mais, mais liso). */
    private static final int MERIDIANOS = 24;
    private static final int PARALELOS = 12;

    /** semente fixa: as rachaduras da lua não mudam de lugar a cada quadro. */
    private static final long SEMENTE = 0x5A9E11L;

    /** se uma renderização falhar feio, o céu de sangue se cala (o terremoto continua). */
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
            float r = Mth.lerp(mistura, event.getRed(), 0.42F);
            float g = Mth.lerp(mistura, event.getGreen(), 0.045F);
            float b = Mth.lerp(mistura, event.getBlue(), 0.035F);
            event.setRed(r);
            event.setGreen(g);
            event.setBlue(b);
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
        try {
            float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            long agora = System.currentTimeMillis();
            // a lua "bate" como coração: 2 pulsações por segundo
            float pulso = 0.5F + 0.5F * Mth.sin(agora * 0.0021F * Mth.PI);

            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());

            boolean tinhaDepth = true;
            try {
                RenderSystem.disableDepthTest();
                tinhaDepth = false;
            } catch (Throwable ignorado) {
                // sem mexer no depth: desenha assim mesmo
            }

            pose.pushPose();

            // 1) O DOMO VERMELHO — o céu inteiro.
            desenharDomo(vc, pose.last().pose(), f);

            // 2) O SOL E A LUA, na posição real do céu do mundo.
            float tempoDoDia = mc.level.getTimeOfDay(pt);
            pose.pushPose();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90.0F));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(tempoDoDia * 360.0F));
            Matrix4f celeste = pose.last().pose();
            // lua (y = -100) e sol (y = +100): os mesmos lugares do vanilla
            desenharAstro(vc, celeste, -100.0F, 15.0F, f, pulso, agora, true);
            desenharAstro(vc, celeste, 100.0F, 17.0F, f, pulso, agora, false);
            pose.popPose();

            pose.popPose();

            buf.endBatch(RenderType.debugQuads());
            if (!tinhaDepth) {
                try {
                    RenderSystem.enableDepthTest();
                } catch (Throwable ignorado) {
                    // segue o jogo
                }
            }
        } catch (Throwable erro) {
            desligado = true;
            RPTagMod.LOGGER.error("RP Tag: ceu de sangue desligado nesta sessao", erro);
        }
    }

    /**
     * Uma casca de esfera em volta da câmera, vermelho-sangue, mais densa no horizonte.
     *
     * <p>É o que transforma "um filtro vermelho" em "o céu mudou": com o alpha baixo no zênite
     * e alto embaixo, o alto do céu escurece e o horizonte queima — o desenho clássico de lua de
     * sangue.
     */
    private static void desenharDomo(VertexConsumer vc, Matrix4f m, float f) {
        for (int i = 0; i < PARALELOS; i++) {
            // phi de 0 (zênite) a pi (nadir)
            float p0 = (float) Math.PI * i / PARALELOS;
            float p1 = (float) Math.PI * (i + 1) / PARALELOS;
            // no horizonte (p = pi/2) o céu fica mais vermelho
            float h = 1.0F - Math.abs((float) (Math.PI * 0.5) - p0) / (float) (Math.PI * 0.5);
            int alfa = (int) (255.0F * f * (0.30F + 0.42F * h));
            if (alfa <= 2) {
                continue;
            }
            // vinho escuro no alto, vermelho vivo embaixo
            int cor = argb(alfa,
                    (int) Mth.lerp(h, 78.0F, 168.0F),
                    (int) Mth.lerp(h, 12.0F, 26.0F),
                    (int) Mth.lerp(h, 20.0F, 22.0F));
            for (int j = 0; j < MERIDIANOS; j++) {
                float t0 = (float) (Math.PI * 2.0) * j / MERIDIANOS;
                float t1 = (float) (Math.PI * 2.0) * (j + 1) / MERIDIANOS;
                float[] a = esfera(RAIO, p0, t0);
                float[] b = esfera(RAIO, p0, t1);
                float[] c = esfera(RAIO, p1, t1);
                float[] d = esfera(RAIO, p1, t0);
                quad(vc, m, a, b, c, d, cor);
            }
        }
    }

    private static float[] esfera(float r, float phi, float theta) {
        float sp = Mth.sin(phi), cp = Mth.cos(phi);
        return new float[] { r * sp * Mth.cos(theta), r * cp, r * sp * Mth.sin(theta) };
    }

    /**
     * A LUA (ou o SOL) DE SANGUE: disco escuro + rachaduras de lava + halo + borda acesa.
     *
     * <p>Fica no plano XZ do referencial celeste (o mesmo do vanilla), em {@code y} = ±100,
     * virado para o jogador. Raio em blocos daquele plano.
     */
    private static void desenharAstro(VertexConsumer vc, Matrix4f m, float y, float raio,
                                      float f, float pulso, long agora, boolean lua) {
        int SEG = 28;
        float bater = 1.0F + 0.06F * pulso;

        // halo externo (3 camadas, cai suave) — é o que faz "brilhar" no céu escuro
        for (int camada = 3; camada >= 1; camada--) {
            float rHalo = raio * (1.10F + 0.30F * camada) * bater;
            int alfa = (int) (255.0F * f * (0.13F / camada));
            int cor = lua ? argb(alfa, 190, 22, 14) : argb(alfa, 255, 96, 26);
            disco(vc, m, y, rHalo, SEG, cor);
        }

        // corpo: vinho escuro (lua) / brasa (sol)
        int corpo = lua ? argb((int) (255.0F * f * 0.97F), 92, 8, 10)
                        : argb((int) (255.0F * f * 0.97F), 214, 58, 16);
        disco(vc, m, y, raio * bater, SEG, corpo);

        // manchas escuras (as "crateras"/coágulos) — semente fixa, não piscam
        RandomSource sorte = RandomSource.create(SEMENTE + (lua ? 1L : 2L));
        for (int i = 0; i < 26; i++) {
            // ponto dentro do disco (distribuição uniforme na área)
            float ang = sorte.nextFloat() * Mth.TWO_PI;
            float dist = raio * 0.86F * Mth.sqrt(sorte.nextFloat());
            float r = raio * (0.06F + 0.16F * sorte.nextFloat());
            int alfa = (int) (255.0F * f * 0.55F);
            float x = Mth.cos(ang) * dist;
            float z = Mth.sin(ang) * dist;
            int cor = lua ? argb(alfa, 46, 2, 6) : argb(alfa, 120, 16, 4);
            discoEm(vc, m, x, y, z, r, 12, cor);
        }

        // RACHADURAS DE LAVA: linhas grossas saindo do meio, pulsando
        RandomSource racho = RandomSource.create(SEMENTE + 77L);
        for (int i = 0; i < 9; i++) {
            float ang = (float) (Mth.TWO_PI * (i + racho.nextFloat() * 0.45F) / 9.0F);
            float comp = raio * (0.42F + 0.52F * racho.nextFloat());
            float larg = raio * (0.035F + 0.030F * racho.nextFloat());
            int alfa = (int) (255.0F * f * (0.55F + 0.45F * pulso));
            int cor = lua ? argb(alfa, 255, 70, 16) : argb(alfa, 255, 176, 60);
            float dx = Mth.cos(ang), dz = Mth.sin(ang);
            // três segmentos com um desvio no meio = rachadura torta
            float desvio = (racho.nextFloat() - 0.5F) * raio * 0.22F;
            float mx = dx * comp * 0.55F - dz * desvio;
            float mz = dz * comp * 0.55F + dx * desvio;
            linha(vc, m, y, 0.0F, 0.0F, mx, mz, larg, cor);
            linha(vc, m, y, mx, mz, dx * comp, dz * comp, larg * 0.72F, cor);
        }

        // borda acesa: anel fino bem vivo (a "circunferência" que o dono vê na referência)
        int alfaBorda = (int) (255.0F * f * (0.55F + 0.45F * pulso));
        int borda = lua ? argb(alfaBorda, 255, 74, 34) : argb(alfaBorda, 255, 190, 90);
        anel(vc, m, y, raio * bater * 0.985F, raio * bater * 1.035F, SEG + 12, borda);
    }

    // ---------------------------------------------------------------- geometria

    private static int argb(int a, int r, int g, int b) {
        return (Math.max(0, Math.min(255, a)) << 24)
                | (Math.max(0, Math.min(255, r)) << 16)
                | (Math.max(0, Math.min(255, g)) << 8)
                | Math.max(0, Math.min(255, b));
    }

    /** disco cheio no plano XZ do referencial (centro em 0,y,0). */
    private static void disco(VertexConsumer vc, Matrix4f m, float y, float r, int seg, int cor) {
        discoEm(vc, m, 0.0F, y, 0.0F, r, seg, cor);
    }

    private static void discoEm(VertexConsumer vc, Matrix4f m, float cx, float y, float cz,
                                float r, int seg, int cor) {
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (Mth.TWO_PI * i / seg);
            float a1 = (float) (Mth.TWO_PI * (i + 1) / seg);
            float[] c = { cx, y, cz };
            float[] p0 = { cx + Mth.cos(a0) * r, y, cz + Mth.sin(a0) * r };
            float[] p1 = { cx + Mth.cos(a1) * r, y, cz + Mth.sin(a1) * r };
            quad(vc, m, c, p0, p1, c, cor);
        }
    }

    /** anel (borda) entre dois raios. */
    private static void anel(VertexConsumer vc, Matrix4f m, float y, float rDentro, float rFora,
                             int seg, int cor) {
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (Mth.TWO_PI * i / seg);
            float a1 = (float) (Mth.TWO_PI * (i + 1) / seg);
            float[] p0 = { Mth.cos(a0) * rDentro, y, Mth.sin(a0) * rDentro };
            float[] p1 = { Mth.cos(a1) * rDentro, y, Mth.sin(a1) * rDentro };
            float[] p2 = { Mth.cos(a1) * rFora, y, Mth.sin(a1) * rFora };
            float[] p3 = { Mth.cos(a0) * rFora, y, Mth.sin(a0) * rFora };
            quad(vc, m, p0, p1, p2, p3, cor);
        }
    }

    /** um segmento grosso (rachadura) de (x0,z0) a (x1,z1) no plano XZ. */
    private static void linha(VertexConsumer vc, Matrix4f m, float y,
                              float x0, float z0, float x1, float z1, float largura,
                              int cor) {
        float dx = x1 - x0, dz = z1 - z0;
        float len = Mth.sqrt(dx * dx + dz * dz);
        if (len < 0.001F) {
            return;
        }
        // perpendicular no plano
        float px = -dz / len * largura * 0.5F;
        float pz = dx / len * largura * 0.5F;
        // (a cor vem do chamador pelo wrapper abaixo)
        CRUZ[0] = x0 - px; CRUZ[1] = z0 - pz;
        CRUZ[2] = x0 + px; CRUZ[3] = z0 + pz;
        CRUZ[4] = x1 + px; CRUZ[5] = z1 + pz;
        CRUZ[6] = x1 - px; CRUZ[7] = z1 - pz;
        float[] a = { CRUZ[0], y, CRUZ[1] };
        float[] b = { CRUZ[2], y, CRUZ[3] };
        float[] c = { CRUZ[4], y, CRUZ[5] };
        float[] d = { CRUZ[6], y, CRUZ[7] };
        quad(vc, m, a, b, c, d, cor);
    }

    /** cantos do segmento em curso (o desenho é single-thread, no thread de render). */
    private static final float[] CRUZ = new float[8];

    /**
     * (5.45.0) o quadrilátero: 4 cantos no buffer, com a matriz da pose.
     *
     * <p>No 1.21.1 a API é {@code addVertex(Matrix4f, x, y, z).setColor(int)} — a mesma que os
     * raios do vazio usam ({@code RaiosVazioFx}).
     */
    private static void quad(VertexConsumer vc, Matrix4f m,
                             float[] a, float[] b, float[] c, float[] d, int cor) {
        canto(vc, m, a, cor);
        canto(vc, m, b, cor);
        canto(vc, m, c, cor);
        canto(vc, m, d, cor);
    }

    /** um canto (o 1.21.1 aplica a matriz no {@code addVertex}). */
    private static void canto(VertexConsumer vc, Matrix4f m, float[] p, int cor) {
        vc.addVertex(m, p[0], p[1], p[2]).setColor(cor);
    }
}
