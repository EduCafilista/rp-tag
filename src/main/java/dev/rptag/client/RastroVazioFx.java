package dev.rptag.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * (5.13.1) O RASTRO DO VAZIO — o chão rachando por onde o Deus do Vazio passa.
 *
 * <h2>O que o dono pediu</h2>
 * <i>"os raios vermelhos ficaram daoras, continue melhorando o rastro dele"</i> — e, no mesmo
 * pedido: <i>"tira as partículas cinzas, as partículas pretas, e esses quadrados grandes"</i>.
 *
 * <p>Então o rastro deixou de ser fumaça/cinza e virou <b>geometria</b>:
 * <ul>
 *   <li>a cada ~0,55 bloco andado, o chão ganha uma <b>rachadura</b> — 6 lascas que saem de um
 *       ponto, vermelho-sangue no começo e esfriando até vinho/preto;</li>
 *   <li>as rachaduras ficam <b>8 segundos</b> e apagam sozinhas (a cidade lembra por onde ele
 *       passou, mas não vira um mapa permanente);</li>
 *   <li>entre uma e outra, uma <b>linha fina</b> costura os dois pontos — o rastro fica contínuo,
 *       como se o chão tivesse sido cortado com um estilete;</li>
 *   <li>tudo em {@link RenderType#debugQuads()} (blend normal), então <b>aparece com shader e
 *       com partículas desligadas</b>.</li>
 * </ul>
 *
 * <p>Quem é o alvo vem do {@link RaiosVazioFx} (o mesmo jogador que tem os raios). Regra de ouro
 * do mod: erro aqui desliga o rastro, nunca o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class RastroVazioFx {

    private RastroVazioFx() {
    }

    /** quanto tempo cada rachadura fica no chão (ms). */
    private static final long VIDA = 8000L;
    /** distância mínima (blocos) entre duas rachaduras. */
    private static final double PASSO = 0.55;
    /** quantas rachaduras no máximo (memória/desenho). */
    private static final int MAX = 260;
    /** rachaduras além disso não são desenhadas (blocos). */
    private static final double ALCANCE = 96.0;

    /** uma rachadura no chão. */
    private static final class Marca {
        double x, y, z;
        long nasceu;
        float giro;
        float tamanho;
    }

    /**
     * (5.48.0) AS TEXTURAS DO RASTRO.
     *
     * <p>O defeito que o dono viu: <i>"o rastro do vazio está ficando bugado e deixando as
     * coisas atrás invisível"</i>. A causa era o tipo de desenho — {@code debugQuads()} desenha
     * <b>sem transparência</b> (o alfa do vértice é jogado fora), então as manchas de escuridão
     * saíam PRETAS E OPACAS por cima do chão, escondendo o que estava atrás. Agora o rastro usa
     * {@code RenderType.text(textura)}, que mistura de verdade: sombra é sombra.
     */
    private static final ResourceLocation TEX_MANCHA = ResourceLocation.fromNamespaceAndPath(
            RPTagMod.MODID, "textures/environment/rastro_mancha.png");
    private static final ResourceLocation TEX_LASCA = ResourceLocation.fromNamespaceAndPath(
            RPTagMod.MODID, "textures/environment/rastro_lasca.png");
    private static final RenderType TIPO_MANCHA = RenderType.text(TEX_MANCHA);
    private static final RenderType TIPO_LASCA = RenderType.text(TEX_LASCA);

    private static final List<Marca> MARCAS = new ArrayList<>();
    private static UUID alvo;
    private static double ultX, ultY, ultZ;
    private static boolean temUltimo;
    private static long ultimoQuadro;

    /** registra o ponto atual do alvo (chamado todo quadro). */
    private static void alimentar(Player dono, long agora) {
        double x = dono.getX(), y = dono.getY(), z = dono.getZ();
        if (!temUltimo) {
            ultX = x; ultY = y; ultZ = z;
            temUltimo = true;
            return;
        }
        // a rachadura nasce no CHÃO, na altura dos pés
        double d = Math.hypot(x - ultX, z - ultZ);
        if (d < PASSO && Math.abs(y - ultY) < 2.0) {
            return;
        }
        ultX = x; ultY = y; ultZ = z;
        Marca m = new Marca();
        m.x = x; m.y = y + 0.02; m.z = z;
        m.nasceu = agora;
        m.giro = (float) (Math.random() * Mth.TWO_PI);
        m.tamanho = 0.55F + (float) Math.random() * 0.55F;
        MARCAS.add(m);
        while (MARCAS.size() > MAX) {
            MARCAS.remove(0);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        UUID agoraAlvo = RaiosVazioFx.alvoUuid();
        if (agoraAlvo == null) {
            if (alvo != null) {
                limpar();
            }
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            Player dono = mc.level.getPlayerByUUID(agoraAlvo);
            if (dono == null || dono.isSpectator()) {
                limpar();
                return;
            }
            long agora = System.currentTimeMillis();
            if (agora == ultimoQuadro) {
                return;
            }
            ultimoQuadro = agora;

            if (!agoraAlvo.equals(alvo)) {
                limpar();
                alvo = agoraAlvo;
            }
            alimentar(dono, agora);

            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            VertexConsumer mancha = buf.getBuffer(TIPO_MANCHA);
            VertexConsumer lasca = buf.getBuffer(TIPO_LASCA);

            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            org.joml.Matrix4f mat = pose.last().pose();

            MARCAS.removeIf(m -> agora - m.nasceu > VIDA);
            Marca anterior = null;
            for (Marca m : MARCAS) {
                try {
                    float idade = (agora - m.nasceu) / (float) VIDA;
                    if (idade >= 1.0F) {
                        anterior = m;
                        continue;
                    }
                    // o rastro esfria: vermelho vivo -> vinho -> quase preto
                    float força = (1.0F - idade);
                    float forca2 = força * força;
                    int quente = cor(0.62F * força + 0.10F, 0.04F * força, 0.06F * força,
                            0.85F * forca2);
                    int frio = cor(0.20F * força, 0.02F * força, 0.05F * força, 0.65F * forca2);

                    double dist = Math.hypot(m.x - cam.x, m.z - cam.z);
                    if (dist > ALCANCE) {
                        anterior = m;
                        continue;
                    }
                    // (5.13.2) "eu ainda quero o rastro de ESCURIDÃO atrás do deus": cada marca
                    // ganha uma mancha escura irregular por baixo das lascas.
                    // (5.48.0) as duas coisas agora são QUADS DE TEXTURA com alfa de verdade —
                    // antes o tipo de desenho ignorava o alfa e a mancha virava um borrão preto
                    // opaco que escondia o mundo atrás (a reclamação do dono).
                    quad(mancha, mat, m.x, m.y + 0.010D, m.z, m.tamanho * 2.4F, m.giro,
                            cor(0.03F, 0.01F, 0.05F, 0.80F * forca2));
                    quad(lasca, mat, m.x, m.y + 0.016D, m.z, m.tamanho * 2.1F,
                            m.giro * 1.7F + 0.4F, quente);
                    if (anterior != null && Math.hypot(m.x - anterior.x, m.z - anterior.z) < 2.5) {
                        costura(lasca, mat, anterior, m, cor(0.45F * força, 0.03F * força,
                                0.05F * força, 0.55F * forca2));
                    }
                } catch (Throwable ignorado) {
                    // uma marca com problema não derruba as outras
                }
                anterior = m;
            }

            pose.popPose();
            buf.endBatch(TIPO_MANCHA);
            buf.endBatch(TIPO_LASCA);
        } catch (Throwable erro) {
            limpar();
            RPTagMod.LOGGER.warn("RP Tag: rastro do vazio desligado nesta sessao", erro);
        }
    }

    /**
     * (5.48.0) UM QUAD NO CHÃO, DEITADO, COM TEXTURA.
     *
     * <p>É a peça do rastro: recebe a cor (que carrega o ALFA de verdade) e o giro, e escreve os
     * quatro cantos no plano XZ com a luz do dia inteira — o rastro é brilho próprio, não recebe
     * sombra do mundo.
     */
    private static void quad(VertexConsumer vc, org.joml.Matrix4f mat, double cx, double y,
            double cz, float raio, float angulo, int argb) {
        float c = (float) Math.cos(angulo) * raio;
        float s = (float) Math.sin(angulo) * raio;
        float x0 = (float) cx - c, z0 = (float) cz - s;
        float x1 = (float) cx + s, z1 = (float) cz - c;
        float x2 = (float) cx + c, z2 = (float) cz + s;
        float x3 = (float) cx - s, z3 = (float) cz + c;
        canto(vc, mat, x0, (float) y, z0, 0.0F, 0.0F, argb);
        canto(vc, mat, x1, (float) y, z1, 1.0F, 0.0F, argb);
        canto(vc, mat, x2, (float) y, z2, 1.0F, 1.0F, argb);
        canto(vc, mat, x0, (float) y, z0, 0.0F, 0.0F, argb);
        canto(vc, mat, x2, (float) y, z2, 1.0F, 1.0F, argb);
        canto(vc, mat, x3, (float) y, z3, 0.0F, 1.0F, argb);
    }

    private static void canto(VertexConsumer vc, org.joml.Matrix4f mat, float x, float y, float z,
            float u, float v, int argb) {
        vc.addVertex(mat, x, y, z).setColor(argb).setUv(u, v)
                .setUv1(OverlayTexture.NO_OVERLAY & 0xFFFF, (OverlayTexture.NO_OVERLAY >> 16) & 0xFFFF)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /** a linha fina que costura duas rachaduras (o rastro fica contínuo). */
    private static void costura(VertexConsumer vc, org.joml.Matrix4f mat,
            Marca a, Marca b, int corte) {
        float x0 = (float) a.x, z0 = (float) a.z, x1 = (float) b.x, z1 = (float) b.z;
        float y = (float) Math.min(a.y, b.y) + 0.014F;
        float dx = x1 - x0, dz = z1 - z0;
        float len = (float) Math.hypot(dx, dz);
        if (len < 0.001F) {
            return;
        }
        float px = -dz / len * 0.075F, pz = dx / len * 0.075F;
        canto(vc, mat, x0 + px, y, z0 + pz, 0.0F, 0.5F, corte);
        canto(vc, mat, x0 - px, y, z0 - pz, 0.0F, 0.5F, corte);
        canto(vc, mat, x1 - px, y, z1 - pz, 1.0F, 0.5F, corte);
        canto(vc, mat, x0 + px, y, z0 + pz, 0.0F, 0.5F, corte);
        canto(vc, mat, x1 - px, y, z1 - pz, 1.0F, 0.5F, corte);
        canto(vc, mat, x1 + px, y, z1 + pz, 1.0F, 0.5F, corte);
    }

    /** zera o rastro (quando o vazio desliga). */
    public static void limpar() {
        MARCAS.clear();
        temUltimo = false;
        alvo = null;
    }

    /** cor ARGB a partir de floats 0..1. */
    private static int cor(float r, float g, float b, float a) {
        int ai = (int) (Mth.clamp(a, 0.0F, 1.0F) * 255.0F);
        int ri = (int) (Mth.clamp(r, 0.0F, 1.0F) * 255.0F);
        int gi = (int) (Mth.clamp(g, 0.0F, 1.0F) * 255.0F);
        int bi = (int) (Mth.clamp(b, 0.0F, 1.0F) * 255.0F);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }
}
