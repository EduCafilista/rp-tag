package dev.loreportals.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import dev.loreportals.PortalBlockEntity;

/**
 * O PORTAL VISUAL.
 *
 * <p>COORDENADAS (importante!): o LevelRenderer JA traduz o desenho pro
 * bloco — aqui TUDO e LOCAL (0..1, origem no canto do bloco). E TODO
 * vertice TEM que passar pela POSE: vc.addVertex(pose, x, y, z). O
 * addVertex(x,y,z) cru IGNORA o PoseStack e desenha no espaco da CAMERA
 * (era o bug "portal na cabeca / risca no ceu" das 1.0.0-1.0.3).</p>
 *
 * <p>MOLDE: galaxia roxa VERTICAL (plano X-Y local, centro y+2.45, r 2.05)
 * dentro do anel de molduras.</p>
 *
 * <p>PAR: portal OVAL VERTICAL girando sobre o nucleo (centro y+1.7,
 * rx 0.95, ry 1.05) — como um portal de verdade que voce atravessa,
 * nao um disco na cabeca.</p>
 *
 * <p>RenderType.lightning = POSITION_COLOR aditivo COM CULL (veja licao no quad4/DUPLO-LADO) (sem texturas,
 * visivel dos dois lados, funciona com shaders).</p>
 */
public class PortalRenderer implements BlockEntityRenderer<PortalBlockEntity> {

    private static final int SEGS = 48;

    // RenderType: RenderType.lightning() (vanilla) — e o caminho PROVADO:
    // conhecido pelo Iris/shaders (a frente "bonita" da 1.0.x usava ele)
    // e o cull ativo dele separa frente/verso no quad4 (duplo-emite).
    // NAO voltar a RenderType.create custom: tipo desconhecido do Iris
    // pode perder o blending aditivo (brilho estourado).

public PortalRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /** Se algo no desenho falhar, desiste DESSA portal (loga 1x) sem derrubar o jogo. */
    private static boolean JA_LOGOU_ERRO = false;

    @Override
    public void render(PortalBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        try {
            desenhar(be, partialTick, pose, buffers);
        } catch (Throwable t) {
            if (!JA_LOGOU_ERRO) {
                JA_LOGOU_ERRO = true;
                dev.loreportals.LorePortalsMod.LOG.error(
                        "Portais: falha ao desenhar portal (portal oculto; jogo continua)", t);
            }
        }
    }

    private void desenhar(PortalBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers) {
        // REVALIDACAO VISUAL NO CLIENTE: quebrou a moldura -> o portal SOME
        // NA HORA (reergueu -> VOLTA) sem depender da rede; editar a moldura
        // reajusta o disco ao vivo.
        if (be.renderAtivo && be.getLevel() != null && be.getLevel().isClientSide()
                && (be.getLevel().getGameTime() + be.getBlockPos().hashCode()) % 15 == 0) {
            be.revalidarVisualCliente();
        }
        if (!be.renderAtivo || be.getLevel() == null) {
            return;
        }
        float age = (be.getLevel().getGameTime()) + partialTick;
        boolean molde = PortalBlockEntity.TIPO_MOLDE.equals(be.renderTipo);
        BlockPos pos = be.getBlockPos();

        // JEITO VANILLA (igual EnchantTableRenderer): o dispatcher JA
        // posicionou a pilha neste bloco. So: pushPose -> centro/rotacao
        // -> desenhar -> popPose. EMPARELHAMENTO EXATO, SEM setIdentity
        // (setIdentity LIMPA a pilha e crasha o Sodium com
        // NoSuchElementException no pushPose — foi o crash 1.0.5/1.0.6).
        pose.pushPose();
        try {
            pose.translate(0.5F, 0.0F, 0.5F);
            if (molde) {
                // galaxia de frente pro plano do anel
                pose.mulPose(Axis.YP.rotationDegrees(-be.renderFacing * 90.0F));
                // disco moldado no FORMATO do jogador (medido na ativacao)
                pose.translate(be.renderCentroDu, 0.0F, 0.0F);
            } else {
                // oval gira devagar — visivel de qualquer lado
                pose.mulPose(Axis.YP.rotationDegrees(age * 12.0F));
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
            PoseStack.Pose p = pose.last();
            float rx;
            float ry;
            float cy;
            if (molde) {
                rx = be.renderRx > 0.5F ? be.renderRx : PortalBlockEntity.DISCO_RAIO_MOLDE;
                ry = be.renderRy > 0.5F ? be.renderRy : 2.05F;
                cy = be.renderCy > 0.1F ? be.renderCy : PortalBlockEntity.DISCO_Y;
                galaxia(p, vc, age, cy, rx, ry, pos);
            } else {
                rx = PortalBlockEntity.DISCO_RAIO_PAR;
                cy = PortalBlockEntity.DISCO_Y_PAR;
                ry = rx * 1.1F;
                oval(p, vc, age, cy, rx, pos);
            }
            // ANIMACAO DE TRAVESSIA: onda de choque + flash do nucleo
            pulso(p, vc, age, cy, rx, ry, be, partialTick);
        } finally {
            pose.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(PortalBlockEntity be) {
        // contrato em coords de MUNDO (so caixa de corte)
        return new AABB(be.getBlockPos()).inflate(5.0);
    }

    @Override
    public boolean shouldRenderOffScreen(PortalBlockEntity be) {
        return true;
    }

    // ==================================================================
    // MOLDE — galaxia vertical (plano X-Y, z = 0 local)
    // ==================================================================
    private static void galaxia(PoseStack.Pose pose, VertexConsumer vc,
            float age, float cy, float rx, float ry, BlockPos seed) {
        float t = age * 0.02F;

        // JANELA ARRUMADA (pedido do user: NAO circular): o disco inteiro
        // e um painel continuo de espaco profundo com gradiente vertical
        // (indigo -> violeta -> purpura), SEM aneis, SEM arcos, SEM borda.
        lequeGrad(pose, vc, cy, rx, ry,
                58, 50, 150,
                88, 64, 185,
                106, 58, 168,
                118);

        // nebulosas: 7 blobs macios em 3 cores (profundidade sem circulo)
        for (int n = 0; n < 7; n += 1) {
            float fase = hash(seed, n + 300);
            float ang = hash(seed, n + 310) * Mth.TWO_PI + t * 0.25F;
            float rr = 0.42F + 0.42F * hash(seed, n + 320);
            float px = su(Mth.cos(ang)) * rx * rr;
            float py = cy + su(Mth.sin(ang)) * ry * rr;
            float br = rx * (0.12F + 0.10F * fase);
            if (n % 3 == 0) {
                lequeBlob(pose, vc, px, py, br, br * 0.7F, 140, 60, 210, 20);
                lequeBlob(pose, vc, px, py, br * 0.55F, br * 0.38F, 165, 90, 225, 15);
            } else if (n % 3 == 1) {
                lequeBlob(pose, vc, px, py, br, br * 0.7F, 200, 70, 190, 17);
                lequeBlob(pose, vc, px, py, br * 0.55F, br * 0.38F, 220, 100, 205, 13);
            } else {
                lequeBlob(pose, vc, px, py, br, br * 0.7F, 60, 160, 230, 17);
                lequeBlob(pose, vc, px, py, br * 0.55F, br * 0.38F, 90, 185, 245, 13);
            }
        }

        // estrelas de 4 pontas (APROVADAS pelo user) cintilando
        for (int s = 0; s < 26; s += 1) {
            float fase = hash(seed, s);
            float ang = hash(seed, s + 40) * Mth.TWO_PI + t * (0.5F + fase);
            float rr = 0.12F + 0.85F * hash(seed, s + 80);
            float alpha = 0.3F + 0.6F * (0.5F + 0.5F * Mth.sin(age * 0.09F + fase * 6.28F));
            float st = 0.05F + 0.09F * hash(seed, s + 120);
            float px = su(Mth.cos(ang)) * rx * rr;
            float py = cy + su(Mth.sin(ang)) * ry * rr;
            if (s % 7 == 0) {
                estrela4(pose, vc, px, py, st, 255, 230, 170, (int) (alpha * 255));
            } else if (s % 3 == 0) {
                estrela4(pose, vc, px, py, st, 190, 235, 255, (int) (alpha * 255));
            } else {
                estrela4(pose, vc, px, py, st, 255, 244, 255, (int) (alpha * 255));
            }
        }

        // cometas: riscos retos com rastro (movimento, sem circulo)
        for (int c = 0; c < 2; c += 1) {
            float fase = hash(seed, c + 200);
            float ciclo = (age * 0.05F + fase) % 1.0F;
            float alpha = Mth.sin(ciclo * Mth.PI);
            if (alpha > 0.06F) {
                float ang = (0.35F + 0.9F * hash(seed, c + 210)) * Mth.PI;
                float ca = Mth.cos(ang);
                float sa = Mth.sin(ang);
                float pos = (ciclo - 0.5F) * 1.6F;
                float px = su(ca) * rx * pos;
                float py = cy + su(sa) * ry * pos;
                float len = rx * (0.22F + 0.14F * hash(seed, c + 220));
                rastro(pose, vc, px, py, ang, len, 0.035F,
                        210, 240, 255, (int) (alpha * 100.0F));
                estrela4(pose, vc, px, py, 0.06F, 255, 255, 255, (int) (alpha * 200.0F));
            }
        }

        // nucleo branco-quente pequeno pulsando (centro da janela)
        float pulso = 0.85F + 0.15F * Mth.sin(age * 0.11F);
        lequeR(pose, vc, cy, rx * 0.18F * pulso, ry * 0.18F * pulso, 235, 225, 255, 85);
        lequeR(pose, vc, cy, rx * 0.09F * pulso, ry * 0.09F * pulso, 255, 250, 245, 145);
    }

    // ==================================================================
    // formas e efeitos
    // ==================================================================
    /** SUPERELIPSE (2/n com n=3): a galaxia vira uma JANELA que acompanha a
     * abertura da moldura (elipse pura deixava ceu nos cantos — o "esquisito"
     * da print). */
    private static final float FORMA_E = 0.667F;

    private static float su(float v) {
        return (v < 0.0F ? -1.0F : 1.0F) * (float) Math.pow(Math.abs(v), FORMA_E);
    }

    /** Estrela de 4 PONTAS (cruz fina + ponto) — quadradinho lido como bug. */
    private static void estrela4(PoseStack.Pose pose, VertexConsumer vc, float px,
            float py, float r, int cr, int cg, int cb, int a) {
        float f = Math.max(r * 0.14F, 0.008F);
        quad4(pose, vc, px - r, py - f, 0.0F, px + r, py - f, 0.0F,
                px + r, py + f, 0.0F, px - r, py + f, 0.0F, cr, cg, cb, a);
        quad4(pose, vc, px - f, py - r, 0.0F, px + f, py - r, 0.0F,
                px + f, py + r, 0.0F, px - f, py + r, 0.0F, cr, cg, cb, a);
        quad4(pose, vc, px - f, py - f, 0.0F, px + f, py - f, 0.0F,
                px + f, py + f, 0.0F, px - f, py + f, 0.0F, cr, cg, cb, a);
    }

    /** Leque REDONDO (nucleo/pulso) — a forma de janela e so pro disco. */
    private static void lequeR(PoseStack.Pose pose, VertexConsumer vc, float cy,
            float rx, float ry, int r, int g, int b, int a) {
        float prevX = rx;
        float prevY = cy;
        for (int i = 1; i <= SEGS; i += 1) {
            float ang = i * (Mth.TWO_PI / SEGS);
            float x = Mth.cos(ang) * rx;
            float y = cy + Mth.sin(ang) * ry;
            quad4(pose, vc, 0.0F, cy, 0.0F, prevX, prevY, 0.0F, x, y, 0.0F,
                    x, y, 0.0F, r, g, b, a);
            prevX = x;
            prevY = y;
        }
    }

    /** ANIMACAO DE TRAVESSIA: anel de choque expandindo + flash do nucleo
     * (~1,1 s) — disparado nos DOIS portais quando alguem entra. */
    /** ANIMACAO DE TRAVESSIA: a JANELA INTEIRA acende e apaga (~1,1 s)
     * + nucleo responde — sem anel (user rejeitou o circulo). */
    private static void pulso(PoseStack.Pose pose, VertexConsumer vc, float age,
            float cy, float rx, float ry, PortalBlockEntity be, float partialTick) {
        if (be.renderPulso <= 0 || be.getLevel() == null) {
            return;
        }
        float f = (be.getLevel().getGameTime() + partialTick - be.renderPulso) / 22.0F;
        if (f < 0.0F || f > 1.0F) {
            return;
        }
        lequeGrad(pose, vc, cy, rx, ry,
                190, 225, 255,
                170, 205, 255,
                155, 190, 255,
                (int) ((1.0F - f) * 55));
        lequeR(pose, vc, cy, rx * 0.20F * (1.0F - f * 0.4F),
                ry * 0.20F * (1.0F - f * 0.4F), 255, 250, 240,
                (int) ((1.0F - f) * 90));
    }

    /** Painel SUPERELIPTICO com gradiente VERTICAL por segmento — a janela
     * "arrumada": sem aneis, preenche a abertura inteira com transicao
     * suave topo->meio->base. */
    private static void lequeGrad(PoseStack.Pose pose, VertexConsumer vc, float cy,
            float rx, float ry,
            int topR, int topG, int topB,
            int midR, int midG, int midB,
            int botR, int botG, int botB, int a) {
        float prevX = rx;
        float prevY = cy;
        for (int i = 1; i <= SEGS; i += 1) {
            float ang = i * (Mth.TWO_PI / SEGS);
            float s = Mth.sin(ang);
            float x = su(Mth.cos(ang)) * rx;
            float y = cy + su(s) * ry;
            float dv = su(s);
            int r;
            int g;
            int b;
            if (dv >= 0.0F) {
                r = (int) (midR + (topR - midR) * dv);
                g = (int) (midG + (topG - midG) * dv);
                b = (int) (midB + (topB - midB) * dv);
            } else {
                r = (int) (midR + (botR - midR) * (-dv));
                g = (int) (midG + (botG - midG) * (-dv));
                b = (int) (midB + (botB - midB) * (-dv));
            }
            quad4(pose, vc, 0.0F, cy, 0.0F, prevX, prevY, 0.0F, x, y, 0.0F,
                    x, y, 0.0F, r, g, b, a);
            prevX = x;
            prevY = y;
        }
    }

    /** Cruz de luz do nucleo: losango largo se lenX > lenY, alto se nao. */
    private static void flare(PoseStack.Pose pose, VertexConsumer vc, float cy,
            float lenX, float lenY, int r, int g, int b, int a) {
        quad4(pose, vc, -lenX, cy, 0.0F, 0.0F, cy + lenY, 0.0F,
                lenX, cy, 0.0F, 0.0F, cy - lenY, 0.0F, r, g, b, a);
    }

    /** Disco radial macio centrado em (px, py) — nebulosa sem cantos. */
    private static void lequeBlob(PoseStack.Pose pose, VertexConsumer vc, float px,
            float py, float rx, float ry, int r, int g, int b, int a) {
        float prevX = px + rx;
        float prevY = py;
        for (int i = 1; i <= 10; i += 1) {
            float ang = i * (Mth.TWO_PI / 10.0F);
            float x = px + Mth.cos(ang) * rx;
            float y = py + Mth.sin(ang) * ry;
            quad4(pose, vc, px, py, 0.0F, prevX, prevY, 0.0F, x, y, 0.0F,
                    x, y, 0.0F, r, g, b, a);
            prevX = x;
            prevY = y;
        }
    }

    /** Rastro de cometa: fita que afina da cabeca pra cauda (degrader). */
    private static void rastro(PoseStack.Pose pose, VertexConsumer vc, float px,
            float py, float ang, float len, float w, int r, int g, int b, int a) {
        float ca = Mth.cos(ang);
        float sa = Mth.sin(ang);
        float prevX = px;
        float prevY = py;
        for (int i = 1; i <= 4; i += 1) {
            float f = i / 4.0F;
            float x = px - ca * len * f;
            float y = py - sa * len * f;
            float ww = w * (1.0F - (f - 0.125F) * 0.8F);
            int aa = (int) (a * (1.0F - (f - 0.125F) * 0.85F));
            float tx = -sa * ww;
            float ty = ca * ww;
            quad4(pose, vc, prevX - tx, prevY - ty, 0.0F, x - tx, y - ty, 0.0F,
                    x + tx, y + ty, 0.0F, prevX + tx, prevY + ty, 0.0F,
                    r, g, b, aa);
            prevX = x;
            prevY = y;
        }
    }

    // ==================================================================
    // PAR — portal oval vertical (plano X-Y local, centro (0, cy, 0))
    // ==================================================================
    private static void oval(PoseStack.Pose pose, VertexConsumer vc,
            float age, float cy, float rx, BlockPos seed) {
        float ry = rx * 1.1F;
        float t = age * 0.045F;

        // espaco profundo + vidro interno (bem translucido pra ver atraves)
        lequeV(pose, vc, cy, rx, ry, 0.0F, 24, 20, 70, 55);
        lequeV(pose, vc, cy, rx * 0.99F, ry * 0.99F, 0.0F, 70, 60, 170, 30);

        // HALO + nucleo brilhante (mesma assinatura da galaxia MOLDE)
        faixaV(pose, vc, cy, rx * 0.16F, rx * 0.40F, ry * 0.16F, ry * 0.40F, 255, 240, 220, 50);
        faixaV(pose, vc, cy, rx * 0.40F, rx * 0.70F, ry * 0.40F, ry * 0.70F, 110, 205, 255, 26);
        float pulso = 0.85F + 0.15F * Mth.sin(age * 0.11F);
        lequeV(pose, vc, cy, rx * 0.28F * pulso, ry * 0.28F * pulso, 0.0F, 255, 240, 220, 32);
        lequeV(pose, vc, cy, rx * 0.14F * pulso, ry * 0.14F * pulso, 0.0F, 255, 250, 240, 88);
        flare(pose, vc, cy, rx * 0.18F * pulso, ry * 0.030F, 255, 244, 224, 32);

        // redemoinho: 2 bracos espirais elipticos
        for (int arm = 0; arm < 2; arm += 1) {
            bracoE(pose, vc, cy, rx, ry, arm * Mth.PI, t);
        }

        // borda do portal (oval claro)
        faixaV(pose, vc, cy, rx * 0.92F, rx, ry * 0.92F, ry, 175, 230, 255, 160);
        faixaV(pose, vc, cy, rx * 0.80F, rx * 0.88F, ry * 0.80F, ry * 0.88F, 110, 185, 255, 85);

        // faiscas subindo dentro do oval
        for (int s = 0; s < 7; s += 1) {
            float fase = hash(seed, s);
            float ang = hash(seed, s + 40) * Mth.TWO_PI;
            float rr = 0.85F * hash(seed, s + 80);
            float dy = (fase + age * 0.02F) % 1.0F;
            float py = cy + (dy * 2.0F - 1.0F) * ry * 0.9F;
            float px = Mth.cos(ang) * rx * rr * (1.0F - Math.abs(py - cy) / ry * 0.6F);
            float alpha = Mth.sin(dy * Mth.PI) * 0.7F;
            float st = 0.03F + 0.03F * hash(seed, s + 120);
            quad4(pose, vc, px - st, py - st, 0.0F, px + st, py - st, 0.0F,
                    px + st, py + st, 0.0F, px - st, py + st, 0.0F,
                    220, 245, 255, (int) (alpha * 255));
        }
    }

    private static void bracoE(PoseStack.Pose pose, VertexConsumer vc, float cy,
            float rx, float ry, float base, float t) {
        float prevA = base + t;
        float prevF = 0.1F;
        for (int i = 1; i <= 10; i += 1) {
            float f = i / 10.0F;
            float a = base + t + f * 2.6F;
            float ax = Mth.cos(prevA) * rx * prevF;
            float ay = cy + Mth.sin(prevA) * ry * prevF;
            float bx = Mth.cos(a) * rx * f;
            float by = cy + Mth.sin(a) * ry * f;
            float w = rx * 0.18F * (1.0F - f * 0.55F) * 0.5F;
            float dx = bx - ax;
            float dy = by - ay;
            float len = Mth.sqrt(dx * dx + dy * dy);
            float px = len > 0 ? -dy / len * w : 0.0F;
            float py = len > 0 ? dx / len * w : 0.0F;
            int alpha = (int) (110 * (1.0F - f * 0.65F));
            quad4(pose, vc, ax + px, ay + py, 0.0F, bx + px, by + py, 0.0F,
                    bx - px, by - py, 0.0F, ax - px, ay - py, 0.0F,
                    155, 135, 255, alpha);
            prevA = a;
            prevF = f;
        }
    }

    // ==================================================================
    // primitivas — TUDO no plano X-Y com centro na ORIGEM (coords locais!)
    // ==================================================================
    /** Leque (disco) ELIPTICO no plano X-Y a z=0, centro (0, cy). */
    private static void lequeV(PoseStack.Pose pose, VertexConsumer vc, float cy,
            float rx, float ry, float rot, int r, int g, int b, int a) {
        float prevX = su(Mth.cos(rot)) * rx;
        float prevY = cy + su(Mth.sin(rot)) * ry;
        for (int i = 1; i <= SEGS; i += 1) {
            float ang = rot + i * (Mth.TWO_PI / SEGS);
            float x = su(Mth.cos(ang)) * rx;
            float y = cy + su(Mth.sin(ang)) * ry;
            quad4(pose, vc, 0.0F, cy, 0.0F, prevX, prevY, 0.0F, x, y, 0.0F, x, y, 0.0F, r, g, b, a);
            prevX = x;
            prevY = y;
        }
    }

    /** Faixa (anel) ELIPTICA no plano X-Y a z=0, entre (rx0,ry0) e (rx1,ry1). */
    private static void faixaV(PoseStack.Pose pose, VertexConsumer vc, float cy,
            float rx0, float rx1, float ry0, float ry1, int r, int g, int b, int a) {
        float prevC = 1.0F;
        float prevS = 0.0F;
        for (int i = 1; i <= SEGS; i += 1) {
            float ang = i * (Mth.TWO_PI / SEGS);
            float c = Mth.cos(ang);
            float s = Mth.sin(ang);
            quad4(pose, vc, prevC * rx0, cy + prevS * ry0, 0.0F,
                    prevC * rx1, cy + prevS * ry1, 0.0F,
                    c * rx1, cy + s * ry1, 0.0F,
                    c * rx0, cy + s * ry0, 0.0F, r, g, b, a);
            prevC = c;
            prevS = s;
        }
    }

    /** Quad explicito de 4 vertices — a unica forma de mandar geometria. */
    // DUPLO-EMITE (frente+verso): o cull do lightning (PROVADO em 1.0.x —
    // o user via so a frente) corta sempre 1 copia por lado de visao —
    // cada pixel recebe 1 unica copia. O brilho estourado da 1.1.0/1.1.1
    // NAO era isto: era a PALETA pesada empilhada (corrigida acima).
    private static void quad4(PoseStack.Pose pose, VertexConsumer vc,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz,
            int r, int g, int b, int a) {
        vc.addVertex(pose, ax, ay, az).setColor(r, g, b, a);
        vc.addVertex(pose, bx, by, bz).setColor(r, g, b, a);
        vc.addVertex(pose, cx, cy, cz).setColor(r, g, b, a);
        vc.addVertex(pose, dx, dy, dz).setColor(r, g, b, a);
        vc.addVertex(pose, ax, ay, az).setColor(r, g, b, a);
        vc.addVertex(pose, dx, dy, dz).setColor(r, g, b, a);
        vc.addVertex(pose, cx, cy, cz).setColor(r, g, b, a);
        vc.addVertex(pose, bx, by, bz).setColor(r, g, b, a);
    }

    /** Hash deterministico — mesma estrela sempre na mesma posicao. */
    private static float hash(BlockPos pos, int n) {
        int h = pos.getX() * 73428767 ^ pos.getY() * 912931 ^ pos.getZ() * 38977317 ^ n * 274763;
        h = h ^ (h >>> 13) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65536.0F;
    }
}
