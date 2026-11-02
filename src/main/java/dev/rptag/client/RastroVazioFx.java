package dev.rptag.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
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
            VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());

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
                    desenharRachadura(vc, mat, m, quente, frio);
                    if (anterior != null && Math.hypot(m.x - anterior.x, m.z - anterior.z) < 2.5) {
                        costura(vc, mat, anterior, m, cor(0.45F * força, 0.03F * força,
                                0.05F * força, 0.55F * forca2));
                    }
                } catch (Throwable ignorado) {
                    // uma marca com problema não derruba as outras
                }
                anterior = m;
            }

            pose.popPose();
            buf.endBatch(RenderType.debugQuads());
        } catch (Throwable erro) {
            limpar();
            RPTagMod.LOGGER.warn("RP Tag: rastro do vazio desligado nesta sessao", erro);
        }
    }

    /** as 6 lascas da rachadura, deitadas no chão. */
    private static void desenharRachadura(VertexConsumer vc, org.joml.Matrix4f mat,
            Marca m, int quente, int frio) {
        int lascas = 6;
        for (int i = 0; i < lascas; i++) {
            double a = m.giro + i * (Mth.TWO_PI / lascas) + Math.sin(i * 2.3 + m.giro) * 0.22;
            float comp = m.tamanho * (0.55F + 0.45F * Math.abs(Mth.sin(m.giro + i * 1.7F)));
            float larg = 0.045F + 0.02F * Math.abs(Mth.cos(i * 1.3F));
            float dx = (float) Math.cos(a), dz = (float) Math.sin(a);
            float px = -dz, pz = dx;         // perpendicular (a largura da lasca)

            float x0 = (float) m.x, y0 = (float) m.y, z0 = (float) m.z;
            float x1 = x0 + dx * comp, z1 = z0 + dz * comp;

            vc.addVertex(mat, x0 + px * larg, y0, z0 + pz * larg).setColor(quente);
            vc.addVertex(mat, x0 - px * larg, y0, z0 - pz * larg).setColor(quente);
            vc.addVertex(mat, x1 - px * larg * 0.25F, y0, z1 - pz * larg * 0.25F).setColor(frio);
            vc.addVertex(mat, x1 + px * larg * 0.25F, y0, z1 + pz * larg * 0.25F).setColor(frio);
        }
        // um miolo mais quente, bem pequeno, no ponto de onde as lascas saem
        float t = 0.09F;
        vc.addVertex(mat, (float) m.x - t, (float) m.y, (float) m.z).setColor(quente);
        vc.addVertex(mat, (float) m.x, (float) m.y, (float) m.z - t).setColor(quente);
        vc.addVertex(mat, (float) m.x + t, (float) m.y, (float) m.z).setColor(quente);
        vc.addVertex(mat, (float) m.x, (float) m.y, (float) m.z + t).setColor(quente);
    }

    /** a linha fina que costura duas rachaduras (o rastro fica contínuo). */
    private static void costura(VertexConsumer vc, org.joml.Matrix4f mat,
            Marca a, Marca b, int cor) {
        float x0 = (float) a.x, z0 = (float) a.z, x1 = (float) b.x, z1 = (float) b.z;
        float y = (float) Math.min(a.y, b.y) + 0.005F;
        float dx = x1 - x0, dz = z1 - z0;
        float len = (float) Math.hypot(dx, dz);
        if (len < 0.001F) {
            return;
        }
        float px = -dz / len * 0.032F, pz = dx / len * 0.032F;
        vc.addVertex(mat, x0 + px, y, z0 + pz).setColor(cor);
        vc.addVertex(mat, x0 - px, y, z0 - pz).setColor(cor);
        vc.addVertex(mat, x1 - px, y, z1 - pz).setColor(cor);
        vc.addVertex(mat, x1 + px, y, z1 + pz).setColor(cor);
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
