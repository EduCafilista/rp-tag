package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.rptag.RPTagMod;
import dev.rptag.VazioGeometria;
import dev.rptag.VazioPortalPayload;
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
 * (5.13.0) O PORTAL NEGRO E AS COLUNAS DE LUZ DO VAZIO — GEOMETRIA, NÃO PARTÍCULA.
 *
 * <h2>Por que este arquivo existe (pedido 50)</h2>
 * O dono: <i>"se o player desativar as partículas, os efeitos somem — corrigir"</i> e
 * <i>"o player precisa ver a aura em volta do vazio"</i>. Partícula em configuração mínima
 * simplesmente não é enviada pelo cliente: o portal virava um buraco preto sem vida. Aqui tudo é
 * desenhado com {@link RenderType#debugQuads()} (POSITION_COLOR, <b>blend normal</b>, escreve
 * profundidade) — aparece com partículas desligadas, com Iris, com Sodium e no jogo puro.
 *
 * <h2>O que é desenhado</h2>
 * <ul>
 *   <li><b>o vórtice</b> dentro da moldura (5x7 blocos), subdividido em 15x21 células, com um
 *       redemoinho que gira e respira — a paleta é a do protótipo {@code KfElheRr.java} do dono
 *       ({@code #000000 → #06030a → #110718 → #1e0c2d → #351049 → #5a1a74});</li>
 *   <li><b>a abertura de baixo para cima</b>: cada linha do vórtice só acende quando a animação
 *       chega nela;</li>
 *   <li><b>brasas</b> vermelho-sangue ({@code #c0182c}) e violeta ({@code #8a3fd0}) subindo dentro
 *       do portal;</li>
 *   <li><b>as colunas de luz dos 8 pedestais</b> em volta da praça — violeta cheio quando o
 *       fragmento está encaixado, brasa vermelha fraca quando o pedestal está vazio;</li>
 *   <li><b>o anel no chão</b> em volta de cada pedestal (dá para ver a praça de longe no breu).</li>
 * </ul>
 *
 * <p>Regra de ouro do mod: erro aqui desliga o desenho, nunca o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class VazioPortalFx {

    private VazioPortalFx() {
    }

    private static final ResourceKey<Level> EXILIO = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "exilio"));

    /** paleta do vórtice (a do protótipo do dono). */
    private static final int[] VOID = { 0x000000, 0x06030a, 0x110718, 0x1e0c2d, 0x351049, 0x5a1a74 };
    /** brasa vermelha e brasa violeta. */
    private static final int BRASA_VERMELHA = 0xc0182c;
    private static final int BRASA_VIOLETA = 0x8a3fd0;

    /** subdivisão de cada bloco do miolo (3x3 = 15x21 células no total). */
    private static final int SUB = 3;

    // ------------------------------------------------------------- estado
    private static boolean temEstado;
    private static int px, py, pz;
    private static int pedais;
    private static int santuarios;
    /** 0 fechado, 1 abrindo, 2 aberto. */
    private static int estado;
    /** quando ESTE cliente viu a abertura começar (ms). */
    private static long abriuEm;

    private static long ultimoQuadro;

    /** o servidor mandou o estado (1x por segundo). */
    public static void receber(VazioPortalPayload payload) {
        boolean eraZero = estado == 0;
        if (payload.estado() == 1 && eraZero) {
            abriuEm = System.currentTimeMillis();     // começa a animação local
        }
        if (payload.estado() == 0) {
            abriuEm = 0L;
        }
        if (payload.x() != px || payload.y() != py || payload.z() != pz) {
            abriuEm = payload.estado() == 0 ? 0L : abriuEm;
        }
        px = payload.x();
        py = payload.y();
        pz = payload.z();
        pedais = payload.pedais();
        santuarios = payload.santuarios();
        estado = payload.estado();
        temEstado = true;
    }

    /** o cliente saiu do vazio: esquece o portal (não desenha no overworld). */
    public static void limpar() {
        temEstado = false;
        estado = 0;
        abriuEm = 0L;
    }

    /** progresso da abertura, de baixo para cima (0..1). */
    private static float progresso() {
        if (estado == 2) {
            return 1.0F;
        }
        if (estado == 0) {
            return 0.0F;
        }
        if (abriuEm <= 0L) {
            return 0.0F;
        }
        return Mth.clamp((System.currentTimeMillis() - abriuEm)
                / (float) VazioGeometria.ABERTURA_MS, 0.0F, 1.0F);
    }

    // ------------------------------------------------------------- render
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!temEstado || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            if (!mc.level.dimension().equals(EXILIO)) {
                return;
            }
            long agora = System.currentTimeMillis();
            if (agora == ultimoQuadro) {
                return;
            }
            ultimoQuadro = agora;

            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());

            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            org.joml.Matrix4f mat = pose.last().pose();

            double dPortal = cam.distanceTo(new Vec3(px + 0.5, py + 3.5, pz + 0.5));
            if (dPortal < 220.0) {
                try {
                    desenharPortal(vc, mat, cam, agora);
                } catch (Throwable ignorado) {
                    // um quadro perdido do portal não derruba o resto
                }
            }
            double dPraca = cam.distanceTo(new Vec3(0.5, py + 1.0, 0.5));
            if (dPraca < 150.0) {
                try {
                    desenharPedestais(vc, mat, cam, agora);
                } catch (Throwable ignorado) {
                    // idem
                }
            }

            pose.popPose();
            buf.endBatch(RenderType.debugQuads());
        } catch (Throwable erro) {
            temEstado = false;
            RPTagMod.LOGGER.warn("RP Tag: portal do vazio desligado nesta sessao", erro);
        }
    }

    // ------------------------------------------------------- o portal negro
    private static void desenharPortal(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 cam, long agora) {
        float prog = progresso();
        if (prog <= 0.001F) {
            // fechado: só um vinco vermelho no meio da moldura (dá pra ver que existe)
            float meio = py + VazioGeometria.ALTURA_MIOLO * 0.5F;
            linhaVertical(vc, mat, px + 0.5F, py + 0.2F, meio, pz + 0.48F, 0.035F,
                    cor(0.45F, 0.03F, 0.08F, 0.55F));
            return;
        }

        float t = (agora % 60_000L) / 1000.0F;      // relógio do redemoinho
        int larg = VazioGeometria.LARGURA_MIOLO;    // 5
        int alt = VazioGeometria.ALTURA_MIOLO;      // 7
        int cx = 0, cy = 0;
        float x0 = px + 0.5F - larg / 2.0F;
        float z = pz + 0.5F;

        // quanto do portal já abriu (linhas, de baixo para cima)
        int linhasAbertas = Math.min(alt, (int) Math.ceil(prog * alt - 1e-4F));

        // a câmera está na frente (+z) ou atrás (-z)? desenha ligeiro para o lado dela
        float desloca = cam.z > z ? 0.012F : -0.012F;

        for (int linha = 0; linha < linhasAbertas; linha++) {
            for (int col = 0; col < larg; col++) {
                for (int sy = 0; sy < SUB; sy++) {
                    for (int sx = 0; sx < SUB; sx++) {
                        float u0 = x0 + col + sx / (float) SUB;
                        float u1 = x0 + col + (sx + 1) / (float) SUB;
                        float v0 = py + linha + sy / (float) SUB;
                        float v1 = py + linha + (sy + 1) / (float) SUB;

                        // coordenadas relativas ao centro do portal
                        float rx = (u0 + u1) * 0.5F - (px + 0.5F);
                        float ry = (v0 + v1) * 0.5F - (py + alt * 0.5F);
                        double raio = Math.hypot(rx * 0.55F, ry);
                        double ang = Math.atan2(ry, rx);
                        // fbm simplificado do protótipo: girar + duas frequências
                        double onda = 0.5
                                + 0.34 * Math.sin(ang * 2.0 + raio * 2.6 - t * 0.9)
                                + 0.16 * Math.sin(ang * 5.0 - raio * 4.1 + t * 1.7);
                        // centro mais escuro, borda mais violeta
                        double borda = Mth.clamp(raio / (alt * 0.55), 0.0, 1.0);
                        double v = Mth.clamp(onda * (0.45 + 0.75 * borda), 0.0, 1.0);
                        int cor = paleta(v);
                        float alpha = 0.62F + 0.26F * (float) borda;
                        // a última linha que abriu nasce fraca (o "acendendo")
                        float fracaoDaLinha = Mth.clamp(prog * alt - linha, 0.0F, 1.0F);
                        alpha *= 0.25F + 0.75F * fracaoDaLinha;

                        quadPlanoZ(vc, mat, u0, v0, u1, v1, z + desloca, comAlpha(cor, alpha));
                    }
                }
            }
        }

        // ---- a moldura brilhando (4 tiras violetas por dentro do arco)
        int corMoldura = cor(0.42F, 0.10F, 0.55F, 0.34F);
        float m = 0.07F;
        quadPlanoZ(vc, mat, x0, py, x0 + m, py + alt, z + desloca * 2, corMoldura);
        quadPlanoZ(vc, mat, x0 + larg - m, py, x0 + larg, py + alt, z + desloca * 2, corMoldura);
        quadPlanoZ(vc, mat, x0, py, x0 + larg, py + m, z + desloca * 2, corMoldura);
        quadPlanoZ(vc, mat, x0, py + alt - m, x0 + larg, py + alt, z + desloca * 2, corMoldura);

        // ---- brasas subindo (sempre, com ou sem partículas)
        int quantas = 14;
        for (int i = 0; i < quantas; i++) {
            float fase = (t * 0.42F + i / (float) quantas) % 1.0F;
            float bx = px + 0.5F + (float) Math.sin(i * 2.3 + t * 0.6) * (larg * 0.42F);
            float by = py + fase * (alt + 1.2F) - 0.4F;
            if (by > py + linhasAbertas) {
                continue;                    // ainda não abriu aquela altura
            }
            float tam = 0.045F + 0.03F * (float) Math.sin(i * 1.7 + t);
            int cor = (i % 3 == 0) ? BRASA_VIOLETA : BRASA_VERMELHA;
            float alpha = 0.75F * (1.0F - fase * 0.7F);
            losango(vc, mat, bx, by, z + desloca * 3, tam, comAlpha(cor, alpha));
        }
    }

    // ------------------------------------------- as colunas dos 8 pedestais
    private static void desenharPedestais(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 cam, long agora) {
        float t = (agora % 40_000L) / 1000.0F;
        for (int i = 0; i < VazioGeometria.TOTAL; i++) {
            int[] p = VazioGeometria.pedestal(i);
            float bx = p[0] + 0.5F;
            float bz = p[1] + 0.5F;
            float base = py + 2.0F;
            boolean cheio = (pedais & (1 << i)) != 0;

            Vec3 alvo = new Vec3(bx, base + 2.0, bz);
            if (cam.distanceTo(alvo) > 130.0) {
                continue;
            }
            // "câmera-facing": a coluna é uma fita que sempre olha para quem vê
            Vec3 paraCam = cam.subtract(alvo);
            if (paraCam.lengthSqr() < 1e-6) {
                continue;
            }
            paraCam = paraCam.normalize();
            float perpx = (float) -paraCam.z;
            float perpz = (float) paraCam.x;
            float largura = 0.34F;

            float altura = cheio ? 6.0F : 3.2F;
            float pulso = cheio
                    ? 0.55F + 0.20F * (float) Math.sin(t * 2.1 + i)
                    : 0.20F + 0.05F * (float) Math.sin(t * 0.9 + i);
            int corBaixo = cheio ? cor(0.54F, 0.25F, 0.82F, pulso * 0.55F)
                    : cor(0.35F, 0.05F, 0.14F, pulso * 0.45F);
            int corCima = cheio ? cor(0.72F, 0.42F, 0.95F, 0.0F)
                    : cor(0.30F, 0.03F, 0.10F, 0.0F);

            // fita vertical (base -> topo)
            float px0 = perpx * largura, pz0 = perpz * largura;
            float px1 = perpx * largura * 0.45F, pz1 = perpz * largura * 0.45F;
            vc.addVertex(mat, bx - px0, base, bz - pz0).setColor(corBaixo);
            vc.addVertex(mat, bx + px0, base, bz + pz0).setColor(corBaixo);
            vc.addVertex(mat, bx + px1, base + altura, bz + pz1).setColor(corCima);
            vc.addVertex(mat, bx, base + altura, bz).setColor(corCima);

            // o "olho" no topo: losango aceso quando cheio
            if (cheio) {
                losango(vc, mat, bx, base, bz, 0.20F + 0.06F * (float) Math.sin(t * 3.3 + i),
                        cor(0.78F, 0.48F, 1.0F, 0.85F));
            } else {
                losango(vc, mat, bx, base, bz, 0.13F, cor(0.55F, 0.08F, 0.18F, 0.55F));
            }

            // anel no chão (12 segmentos) — a praça inteira fica legível de longe
            int anelCor = cheio ? cor(0.45F, 0.20F, 0.72F, 0.30F) : cor(0.28F, 0.04F, 0.12F, 0.22F);
            float raioAnel = 1.35F;
            float yAnel = py + 1.02F;
            int seg = 12;
            for (int s = 0; s < seg; s++) {
                double a0 = s * Math.PI * 2 / seg;
                double a1 = (s + 1) * Math.PI * 2 / seg;
                float ax0 = bx + (float) Math.cos(a0) * raioAnel;
                float az0 = bz + (float) Math.sin(a0) * raioAnel;
                float ax1 = bx + (float) Math.cos(a1) * raioAnel;
                float az1 = bz + (float) Math.sin(a1) * raioAnel;
                vc.addVertex(mat, ax0, yAnel, az0).setColor(anelCor);
                vc.addVertex(mat, ax1, yAnel, az1).setColor(anelCor);
                vc.addVertex(mat, ax1, yAnel + 0.09F, az1).setColor(anelCor);
                vc.addVertex(mat, ax0, yAnel + 0.09F, az0).setColor(anelCor);
            }
        }
    }

    // ------------------------------------------------------------- desenho
    /** quad reto no plano X-Y (o portal encara o norte/sul). */
    private static void quadPlanoZ(VertexConsumer vc, org.joml.Matrix4f mat,
            float x0, float y0, float x1, float y1, float z, int cor) {
        vc.addVertex(mat, x0, y0, z).setColor(cor);
        vc.addVertex(mat, x1, y0, z).setColor(cor);
        vc.addVertex(mat, x1, y1, z).setColor(cor);
        vc.addVertex(mat, x0, y1, z).setColor(cor);
    }

    /** losango (2 quads cruzados) — brasas e pontas. */
    private static void losango(VertexConsumer vc, org.joml.Matrix4f mat,
            float x, float y, float z, float t, int cor) {
        vc.addVertex(mat, x - t, y, z).setColor(cor);
        vc.addVertex(mat, x, y, z - t).setColor(cor);
        vc.addVertex(mat, x + t, y, z).setColor(cor);
        vc.addVertex(mat, x, y, z + t).setColor(cor);
        vc.addVertex(mat, x, y - t, z).setColor(cor);
        vc.addVertex(mat, x, y, z - t).setColor(cor);
        vc.addVertex(mat, x, y + t, z).setColor(cor);
        vc.addVertex(mat, x, y, z + t).setColor(cor);
    }

    /** verguinha vertical fina (portal fechado). */
    private static void linhaVertical(VertexConsumer vc, org.joml.Matrix4f mat,
            float x, float y0, float y1, float z, float w, int cor) {
        vc.addVertex(mat, x - w, y0, z).setColor(cor);
        vc.addVertex(mat, x + w, y0, z).setColor(cor);
        vc.addVertex(mat, x + w, y1, z).setColor(cor);
        vc.addVertex(mat, x - w, y1, z).setColor(cor);
    }

    /** a cor da paleta do vórtice para um valor 0..1. */
    private static int paleta(double v) {
        int i = (int) Math.floor(Mth.clamp(v, 0.0, 0.9999) * (VOID.length - 1));
        return VOID[Mth.clamp(i, 0, VOID.length - 1)];
    }

    /** troca o alpha de uma cor RGB da paleta. */
    private static int comAlpha(int rgb, float alpha) {
        int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        return (a << 24) | (rgb & 0xFFFFFF);
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
