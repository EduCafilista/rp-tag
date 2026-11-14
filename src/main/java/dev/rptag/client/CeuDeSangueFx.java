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
 * (5.47.0) <b>CÉU DE SANGUE — o terremoto como um céu caótico de lua de sangue.</b>
 *
 * <p>Pedido do dono (vendo a 5.46.0): <i>"esse céu está bonito pra você? tá horrível, muda o céu,
 * deixa mais caótico, a lua eu te mandei algo você mudou completamente, veja tudo para melhorar"</i>.
 *
 * <h2>O que estava feio (e o que mudou)</h2>
 * <ol>
 *   <li><b>Faixas concêntricas</b> no céu: o domo era feito de anéis de cor CONSTANTE — cada
 *       anel um degrau. Agora a cor é <b>por vértice</b> e o degradê interpola dentro de cada
 *       quadrilátero: não existe mais degrau nenhum;</li>
 *   <li><b>Um QUADRADO em volta da lua</b>: o halo eram três quads chapados — aparecia o
 *       retângulo na tela (foto dele). Agora o halo é feito de <b>discos circulares</b> que
 *       encolhem em anéis, sem canto nenhum;</li>
 *   <li><b>céu liso demais</b>: agora tem <b>nuvens de sangue rolando</b> (uma segunda casca com
 *       ruído animado), <b>veias escuras</b> atravessando o céu, <b>pulsação</b> e
 *       <b>clarões</b> de vez em quando — o caos que ele pediu;</li>
 *   <li><b>lua chapada</b>: virou textura com veios de lava (a receita da referência que ele
 *       mandou) e ganhou <b>aro aceso</b> e halo circular pulsando.</li>
 * </ol>
 *
 * <p>Desenhado em {@code AFTER_SKY} (antes do terreno), então nada é pintado por cima do mundo e
 * tudo desaparece quando o terremoto acaba — o estado vem do {@link NeptuneFx}.
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
    /**
     * O halo: degradê radial desenhado numa textura (alfa zero na borda). Foi assim que o
     * QUADRADO em volta da lua morreu de vez — 3 quads chapados mostravam o retângulo e discos
     * empilhados mostravam anéis; uma textura com queda contínua não mostra nem um nem outro.
     */
    private static final ResourceLocation TEX_HALO = ResourceLocation.fromNamespaceAndPath(
            RPTagMod.MODID, "textures/environment/halo_de_sangue.png");

    /** raio do domo (o céu do vanilla fica a ~100; o domo é maior pra ficar atrás de tudo). */
    private static final float RAIO = 150.0F;
    /** o domo: 44 anéis por 40 meridianos, com cor por vértice (sem degrau). */
    private static final int ANEIS = 44;
    private static final int MERIDIANOS = 40;
    /** a segunda casca (as nuvens rolando) fica um pouco mais perto. */
    private static final float RAIO_NUVENS = 142.0F;
    /** a terceira casca: as veias escuras (um pouco mais perto que as nuvens). */
    private static final float RAIO_VEIAS = 146.0F;
    /** metade do lado da lua/sol no céu (o do vanilla é 15). */
    private static final float ASTRO = 21.0F;

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
            float mistura = 0.76F * f;
            event.setRed(Mth.lerp(mistura, event.getRed(), 0.46F));
            event.setGreen(Mth.lerp(mistura, event.getGreen(), 0.035F));
            event.setBlue(Mth.lerp(mistura, event.getBlue(), 0.030F));
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
        boolean mexiNoDepth = false;
        try {
            float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float t = (System.currentTimeMillis() % 3_600_000L) * 0.001F;
            // a lua bate como coração (~2 pulsações por segundo)
            float pulso = 0.5F + 0.5F * Mth.sin(t * 2.1F * Mth.PI);
            // CLARÃO: a cada ~7 s o céu inteiro acende por um instante (o susto)
            float faseClarao = t % 7.0F;
            float clarao = faseClarao < 0.28F ? (1.0F - faseClarao / 0.28F) : 0.0F;
            clarao = clarao * clarao;

            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            RenderType semTextura = RenderType.debugQuads();

            try {
                RenderSystem.disableDepthTest();
                mexiNoDepth = true;
            } catch (Throwable ignorado) {
                // sem mexer no depth: desenha assim mesmo
            }

            pose.pushPose();

            VertexConsumer vc = buf.getBuffer(semTextura);

            // 1) O CÉU (degradê por vértice: nenhum degrau)
            desenharDomo(vc, pose.last().pose(), f, clarao);
            // 2) AS NUVENS DE SANGUE rolando por cima
            desenharNuvens(vc, pose.last().pose(), f, t);
            // 3) AS VEIAS ESCURAS atravessando o céu
            desenharVeias(vc, pose.last().pose(), f, t);

            // 4) HALO CIRCULAR + 5) LUA/SOL, na posição real do céu
            float tempoDoDia = mc.level.getTimeOfDay(pt);
            pose.pushPose();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-90.0F));
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(tempoDoDia * 360.0F));
            Matrix4f celeste = pose.last().pose();
            PoseStack.Pose poseCeleste = pose.last();

            float bater = 1.0F + 0.045F * pulso + 0.10F * clarao;
            // o aro: anel fino bem aceso, pulsando (é a coroa colada no disco)
            int alfaAro = (int) Math.min(255.0F, 255.0F * f * (0.30F + 0.45F * pulso + 0.5F * clarao));
            anel(vc, celeste, -100.0F, ASTRO * bater * 0.995F, ASTRO * bater * 1.035F, 64,
                    argb(alfaAro, 255, 70, 30));
            anel(vc, celeste, 100.0F, ASTRO * bater * 0.995F, ASTRO * bater * 1.035F, 64,
                    argb(alfaAro, 255, 190, 90));
            buf.endBatch(semTextura);

            // O HALO: um quad de textura com degradê radial contínuo — o alfa chega a ZERO na
            // borda, então não existe canto e não existe anel na tela.
            VertexConsumer halo = buf.getBuffer(RenderType.entityTranslucentEmissive(TEX_HALO));
            float raioHalo = ASTRO * 3.6F * (1.0F + 0.05F * pulso + 0.22F * clarao);
            int alfaHalo = (int) (255.0F * f * (0.72F + 0.28F * pulso + 0.45F * clarao));
            quadCor(halo, poseCeleste, -100.0F, raioHalo, alfaHalo, 255, 255, 255);
            quadCor(halo, poseCeleste, 100.0F, raioHalo * 1.12F, alfaHalo, 255, 246, 214);
            buf.endBatch(RenderType.entityTranslucentEmissive(TEX_HALO));

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
            if (mexiNoDepth) {
                try {
                    RenderSystem.enableDepthTest();
                } catch (Throwable ignorado) {
                    // segue o jogo
                }
            }
        }
    }

    // ---------------------------------------------------------------- o céu

    /**
     * A casca de esfera em volta da câmera.
     *
     * <p><b>A diferença que matou as faixas:</b> cada vértice recebe a SUA cor. Antes a cor era
     * calculada por anel e aplicada nos 4 cantos do quadrilátero, então cada anel virava um
     * degrau de cor — e na tela dava para contar os anéis. Agora o degradê interpola de um
     * vértice ao outro e o céu fica contínuo.
     */
    private static void desenharDomo(VertexConsumer vc, Matrix4f m, float f, float clarao) {
        for (int i = 0; i < ANEIS; i++) {
            float p0 = (float) Math.PI * i / ANEIS;
            float p1 = (float) Math.PI * (i + 1) / ANEIS;
            for (int j = 0; j < MERIDIANOS; j++) {
                float t0 = (float) (Math.PI * 2.0) * j / MERIDIANOS;
                float t1 = (float) (Math.PI * 2.0) * (j + 1) / MERIDIANOS;
                float[] a = esfera(RAIO, p0, t0);
                float[] b = esfera(RAIO, p0, t1);
                float[] c = esfera(RAIO, p1, t1);
                float[] d = esfera(RAIO, p1, t0);
                corCeu(vc, m, a, f, p0, clarao);
                corCeu(vc, m, b, f, p0, clarao);
                corCeu(vc, m, c, f, p1, clarao);
                corCeu(vc, m, d, f, p1, clarao);
            }
        }
    }

    /** a cor do céu na altura {@code phi} (0 = zênite, pi = nadir) e no meridiano do vértice. */
    private static void corCeu(VertexConsumer vc, Matrix4f m, float[] p, float f, float phi,
                               float clarao) {
        // h = 1 no horizonte, 0 nos polos
        float h = 1.0F - Math.abs((float) (Math.PI * 0.5) - phi) / (float) (Math.PI * 0.5);
        h = h * h * (3.0F - 2.0F * h);       // suaviza
        float r = Mth.lerp(h, 72.0F, 196.0F);
        float g = Mth.lerp(h, 5.0F, 26.0F);
        float b = Mth.lerp(h, 12.0F, 22.0F);
        // o clarão lava tudo para laranja
        r = Mth.lerp(clarao, r, 255.0F);
        g = Mth.lerp(clarao, g, 90.0F);
        b = Mth.lerp(clarao, b, 60.0F);
        // OPACIDADE ALTA: o céu de sangue tem que COBRIR o céu azul do vanilla (com alfa baixo
        // o azul vazava por baixo e o resultado ficava roxo — parte do "tá horrível").
        int alfa = (int) Math.min(255.0F, 255.0F * f * (0.90F + 0.10F * h) + 60.0F * clarao * f);
        vc.addVertex(m, p[0], p[1], p[2]).setColor(argb(alfa, (int) r, (int) g, (int) b));
    }

    /**
     * AS NUVENS DE SANGUE — uma segunda casca com manchas escuras que rolam devagar.
     *
     * <p>É o que tira o "degradê de papel de parede" e dá o caos: nuvens pesadas atravessando o
     * céu vermelho, como fumaça de incêndio.
     */
    private static void desenharNuvens(VertexConsumer vc, Matrix4f m, float f, float t) {
        for (int i = 0; i < ANEIS; i++) {
            float p0 = (float) Math.PI * i / ANEIS;
            float p1 = (float) Math.PI * (i + 1) / ANEIS;
            for (int j = 0; j < MERIDIANOS; j++) {
                float t0 = (float) (Math.PI * 2.0) * j / MERIDIANOS;
                float t1 = (float) (Math.PI * 2.0) * (j + 1) / MERIDIANOS;
                float n00 = nuvem(t0, p0, t), n01 = nuvem(t1, p0, t);
                float n11 = nuvem(t1, p1, t), n10 = nuvem(t0, p1, t);
                if (n00 < 0.40F && n01 < 0.40F && n11 < 0.40F && n10 < 0.40F) {
                    continue;                 // só desenha onde tem nuvem
                }
                // alfa POR VÉRTICE: a nuvem tem borda macia (alfa fixo por quad = mancha quadrada)
                float[] a = esfera(RAIO_NUVENS, p0, t0);
                float[] b = esfera(RAIO_NUVENS, p0, t1);
                float[] c = esfera(RAIO_NUVENS, p1, t1);
                float[] d = esfera(RAIO_NUVENS, p1, t0);
                quadrilateroCor(vc, m, a, b, c, d,
                        corNuvem(n00, f), corNuvem(n01, f), corNuvem(n11, f), corNuvem(n10, f));
            }
        }
    }

    /** a cor do vértice da nuvem (alfa cresce com a densidade: borda macia, sem degrau). */
    private static int corNuvem(float n, float f) {
        int alfa = (int) Math.min(255.0F, 255.0F * f * Math.max(0.0F, (n - 0.40F)) * 1.45F);
        return argb(alfa, 40, 0, 4);
    }

    /** ruído barato e animado (senos cruzados) — é o desenho das nuvens. */
    private static float nuvem(float theta, float phi, float t) {
        float a = Mth.sin(theta * 2.3F + t * 0.22F) * Mth.sin(phi * 3.1F - t * 0.16F);
        float b = Mth.sin(theta * 5.7F - t * 0.31F + phi * 4.3F);
        float c = Mth.sin(theta * 11.0F + t * 0.47F) * Mth.sin(phi * 6.0F + t * 0.13F);
        return 0.5F + 0.5F * (0.5F * a + 0.3F * b + 0.2F * c);
    }

    /**
     * AS VEIAS DO CÉU — faixas escuras compridas atravessando o vermelho.
     *
     * <p>Aqui é o "caótico" de verdade: elas entram e saem do zênite como rachaduras de vidro.
     */
    private static void desenharVeias(VertexConsumer vc, Matrix4f m, float f, float t) {
        for (int i = 0; i < ANEIS; i++) {
            float p0 = (float) Math.PI * i / ANEIS;
            float p1 = (float) Math.PI * (i + 1) / ANEIS;
            for (int j = 0; j < MERIDIANOS; j++) {
                float t0 = (float) (Math.PI * 2.0) * j / MERIDIANOS;
                float t1 = (float) (Math.PI * 2.0) * (j + 1) / MERIDIANOS;
                float v00 = veia(t0, p0, t), v01 = veia(t1, p0, t);
                float v11 = veia(t1, p1, t), v10 = veia(t0, p1, t);
                if (v00 < 0.52F && v01 < 0.52F && v11 < 0.52F && v10 < 0.52F) {
                    continue;
                }
                float[] a = esfera(RAIO_VEIAS, p0, t0);
                float[] b = esfera(RAIO_VEIAS, p0, t1);
                float[] c = esfera(RAIO_VEIAS, p1, t1);
                float[] d = esfera(RAIO_VEIAS, p1, t0);
                quadrilateroCor(vc, m, a, b, c, d,
                        corVeia(v00, f), corVeia(v01, f), corVeia(v11, f), corVeia(v10, f));
            }
        }
    }

    /**
     * O desenho das veias: faixas COMPRIDAS (variam rápido em {@code phi}, devagar em
     * {@code theta}) que se torcem com o tempo.
     *
     * <p>Na 5.46.0 eram 22 quadriláteros de alfa fixo — na tela isso vira justamente o que o
     * dono reprovou: faixas de borda dura atravessando o céu. Aqui a veia é uma função contínua
     * amostrada na casca, com alfa por vértice, então ela nasce e morre sem borda nenhuma.
     */
    private static float veia(float theta, float phi, float t) {
        float torcao = Mth.sin(theta * 2.0F + t * 0.07F) * 0.55F
                + Mth.sin(theta * 4.7F - t * 0.05F) * 0.25F;
        float a = Mth.sin((phi + torcao) * 11.0F + t * 0.12F);
        float b = Mth.sin((phi + torcao) * 23.0F - t * 0.19F + theta * 3.0F);
        return 0.5F + 0.5F * (0.62F * a + 0.38F * b);
    }

    private static int corVeia(float v, float f) {
        int alfa = (int) Math.min(255.0F, 255.0F * f * Math.max(0.0F, (v - 0.52F)) * 1.15F);
        return argb(alfa, 22, 0, 2);
    }

    // ---------------------------------------------------------------- geometria

    private static float[] esfera(float r, float phi, float theta) {
        float sp = Mth.sin(phi), cp = Mth.cos(phi);
        return new float[] { r * sp * Mth.cos(theta), r * cp, r * sp * Mth.sin(theta) };
    }

    private static int argb(int a, int r, int g, int b) {
        return (Math.max(0, Math.min(255, a)) << 24)
                | (Math.max(0, Math.min(255, r)) << 16)
                | (Math.max(0, Math.min(255, g)) << 8)
                | Math.max(0, Math.min(255, b));
    }

    /** um anel entre dois raios. */
    private static void anel(VertexConsumer vc, Matrix4f m, float y, float dentro, float fora,
                             int seg, int cor) {
        for (int i = 0; i < seg; i++) {
            float a0 = (float) (Mth.TWO_PI * i / seg);
            float a1 = (float) (Mth.TWO_PI * (i + 1) / seg);
            vc.addVertex(m, Mth.cos(a0) * dentro, y, Mth.sin(a0) * dentro).setColor(cor);
            vc.addVertex(m, Mth.cos(a1) * dentro, y, Mth.sin(a1) * dentro).setColor(cor);
            vc.addVertex(m, Mth.cos(a1) * fora, y, Mth.sin(a1) * fora).setColor(cor);
            vc.addVertex(m, Mth.cos(a0) * fora, y, Mth.sin(a0) * fora).setColor(cor);
        }
    }

    /** o quadrilátero com uma cor POR VÉRTICE (é o que faz a borda ser macia). */
    private static void quadrilateroCor(VertexConsumer vc, Matrix4f m, float[] a, float[] b,
                                        float[] c, float[] d, int ca, int cb, int cc, int cd) {
        vc.addVertex(m, a[0], a[1], a[2]).setColor(ca);
        vc.addVertex(m, b[0], b[1], b[2]).setColor(cb);
        vc.addVertex(m, c[0], c[1], c[2]).setColor(cc);
        vc.addVertex(m, d[0], d[1], d[2]).setColor(cd);
    }

    private static void quadrilatero(VertexConsumer vc, Matrix4f m,
                                     float[] a, float[] b, float[] c, float[] d, int cor) {
        vc.addVertex(m, a[0], a[1], a[2]).setColor(cor);
        vc.addVertex(m, b[0], b[1], b[2]).setColor(cor);
        vc.addVertex(m, c[0], c[1], c[2]).setColor(cor);
        vc.addVertex(m, d[0], d[1], d[2]).setColor(cor);
    }

    /**
     * A LUA (ou o SOL) — a textura, num quad do plano celeste.
     *
     * <p>O formato do vértice dos tipos de entidade pede cor, UV, overlay, luz e normal; como é
     * emissivo, a luz é cheia (240,240) e não existe sombreamento.
     */
    private static void quadTextura(VertexConsumer vc, PoseStack.Pose pose, float y, float r) {
        int luz = 240;
        int ovU = OverlayTexture.NO_OVERLAY & 0xFFFF;
        int ovV = (OverlayTexture.NO_OVERLAY >> 16) & 0xFFFF;
        canto(vc, pose, -r, y, -r, 0.0F, 1.0F, luz, ovU, ovV);
        canto(vc, pose, r, y, -r, 1.0F, 1.0F, luz, ovU, ovV);
        canto(vc, pose, r, y, r, 1.0F, 0.0F, luz, ovU, ovV);
        canto(vc, pose, -r, y, r, 0.0F, 0.0F, luz, ovU, ovV);

        canto(vc, pose, -r, y, r, 0.0F, 0.0F, luz, ovU, ovV);
        canto(vc, pose, r, y, r, 1.0F, 0.0F, luz, ovU, ovV);
        canto(vc, pose, r, y, -r, 1.0F, 1.0F, luz, ovU, ovV);
        canto(vc, pose, -r, y, -r, 0.0F, 1.0F, luz, ovU, ovV);
    }

    /** a mesma cortina do {@link #quadTextura}, mas tingida pela cor do vértice (o halo). */
    private static void quadCor(VertexConsumer vc, PoseStack.Pose pose, float y, float r,
                                int a, int rr, int gg, int bb) {
        int luz = 240;
        int ovU = OverlayTexture.NO_OVERLAY & 0xFFFF;
        int ovV = (OverlayTexture.NO_OVERLAY >> 16) & 0xFFFF;
        cantoCor(vc, pose, -r, y, -r, 0.0F, 1.0F, luz, ovU, ovV, a, rr, gg, bb);
        cantoCor(vc, pose, r, y, -r, 1.0F, 1.0F, luz, ovU, ovV, a, rr, gg, bb);
        cantoCor(vc, pose, r, y, r, 1.0F, 0.0F, luz, ovU, ovV, a, rr, gg, bb);
        cantoCor(vc, pose, -r, y, r, 0.0F, 0.0F, luz, ovU, ovV, a, rr, gg, bb);

        cantoCor(vc, pose, -r, y, r, 0.0F, 0.0F, luz, ovU, ovV, a, rr, gg, bb);
        cantoCor(vc, pose, r, y, r, 1.0F, 0.0F, luz, ovU, ovV, a, rr, gg, bb);
        cantoCor(vc, pose, r, y, -r, 1.0F, 1.0F, luz, ovU, ovV, a, rr, gg, bb);
        cantoCor(vc, pose, -r, y, -r, 0.0F, 1.0F, luz, ovU, ovV, a, rr, gg, bb);
    }

    private static void cantoCor(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                                 float u, float v, int luz, int ovU, int ovV,
                                 int a, int rr, int gg, int bb) {
        vc.addVertex(pose, x, y, z)
                .setColor(rr, gg, bb, a)
                .setUv(u, v)
                .setUv1(ovU, ovV)
                .setUv2(luz, luz)
                .setNormal(0.0F, -1.0F, 0.0F);
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
