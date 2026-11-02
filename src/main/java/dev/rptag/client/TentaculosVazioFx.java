package dev.rptag.client;

import java.util.Random;
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
 * TENTÁCULOS DE PEDRA DO VAZIO — 5.11.0.
 *
 * <h2>Por que essa classe existe (pedido do dono)</h2>
 * Até a 5.10.1 a aura do vazio desenhava RAIOS vermelhos ({@code RaiosVazioFx},
 * 5.7.4). O dono reclamou de três coisas de uma vez:
 * <ul>
 *   <li><i>"os tentáculos não aparecem para quem estiver usando shaders, fica
 *       muito claro"</i> — os raios usavam {@link RenderType#lightning()}, que é
 *       <b>blend ADITIVO</b> (soma luz). Com shader/pack claro o vermelho
 *       saturado some no branco: o raio literalmente deixava de aparecer;</li>
 *   <li><i>"não quero mais aquelas partículas vermelhas em volta do personagem,
 *       quero apenas partículas pétreas"</i>;</li>
 *   <li><i>"melhora as partículas para ser algo mais concreto do que apenas
 *       partículas"</i>.</li>
 * </ul>
 *
 * <h2>O que faz agora</h2>
 * Nada de partícula e nada de aditivo: são <b>tentáculos de PEDRA com volume</b>
 * — cada um é um tubo de seção quadrada (4 faces por segmento, mais a ponta em
 * bico), desenhado com {@link RenderType#debugQuads()}, que usa
 * <b>blend NORMAL</b> ({@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}) e escreve
 * profundidade. Ou seja: a pedra é geometria opaca de verdade — escurece com o
 * ambiente, respeita o z-buffer e <b>aparece igual com ou sem shader</b>.
 *
 * <p>O relevo vem do sombreamento por face (a face virada pro sol é mais clara,
 * a de trás é quase preta), dos "nós" ao longo do tubo (a espessura pulsa), dos
 * veios escuros (rachaduras) e do bico de pedra na ponta. Nada disso depende de
 * blending, então nada "lava" em pack de shader.
 *
 * <h2>Ciclo de vida</h2>
 * Cada tentáculo <b>brota</b> do chão (800 ms), fica de pé por 4–9 s e
 * <b>afunda</b> (700 ms); ao afundar, renasce em outro ângulo. Assim a coisa
 * nunca fica parada nem "congela" como acontecia quando a aura era só um pacote
 * enviado uma vez no {@code /vazio ligar}.
 *
 * <p>Regra de ouro do mod: erro aqui desliga os tentáculos, nunca o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class TentaculosVazioFx {

    private TentaculosVazioFx() {
    }

    /** quantos tentáculos ficam de pé ao mesmo tempo. */
    private static final int QTD = 9;
    /** além disso não vale a pena desenhar (blocos). */
    private static final double ALCANCE = 72.0;
    /** quantos segmentos tem o tubo (mais = mais liso, mais caro). */
    private static final int SEGMENTOS = 8;

    private static final Random SORTE = new Random();

    private static boolean ativo;
    private static boolean euSou;
    private static UUID alvo;
    private static long ultimoQuadro = 0L;

    /** Um tentáculo: onde nasce, quanto sobe, quanto entorta e quando morre. */
    private static final class Tentaculo {
        float ang;          // ângulo no anel em volta do corpo
        float raio;         // distância do centro (blocos)
        float altura;       // quanto sobe
        float grosso;       // espessura da base
        float curva;        // quanto abre pra fora no alto
        float fase;         // fase da ondulação (cada um respira diferente)
        float semente;      // tom da pedra e posição dos veios
        int veios;          // contador pros veios escuros
        long nasceu;        // ms
        long vida;          // ms de vida (brota + de pé + afunda)

        float crescido(long agora) {
            long idade = agora - nasceu;
            if (idade < 0L) {
                return 0.0F;
            }
            float brota = Mth.clamp(idade / 800.0F, 0.0F, 1.0F);
            float afunda = Mth.clamp((vida - idade) / 700.0F, 0.0F, 1.0F);
            float k = Math.min(brota, afunda);
            // easeOutQuad: sobe rápido e "assenta" — pedra pesada não é mola
            return 1.0F - (1.0F - k) * (1.0F - k);
        }
    }

    private static final Tentaculo[] LISTA = new Tentaculo[QTD];

    static {
        for (int i = 0; i < QTD; i++) {
            LISTA[i] = new Tentaculo();
            LISTA[i].vida = 1L;
        }
    }

    // ------------------------------------------------------------- estado
    /** Chamado pelo pacote de aura: quem é o vazio e se sou eu. */
    public static void definir(UUID id, boolean souEu) {
        if (id == null) {
            desligar();
            return;
        }
        if (!id.equals(alvo) || !ativo) {
            for (Tentaculo t : LISTA) {
                t.nasceu = 0L;      // renascem todos do chão
                t.vida = 1L;
            }
        }
        alvo = id;
        euSou = souEu;
        ativo = true;
    }

    public static void desligar() {
        ativo = false;
        alvo = null;
    }

    public static boolean ligado() {
        return ativo && alvo != null;
    }

    // ------------------------------------------------------------- render
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!ligado() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            Player dono = mc.level.getPlayerByUUID(alvo);
            if (dono == null || dono.isSpectator()) {
                return;
            }
            Vec3 cam = event.getCamera().getPosition();
            float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Vec3 base = dono.getPosition(pt);
            if (base.distanceToSqr(cam) > ALCANCE * ALCANCE) {
                return;   // longe demais: não gasta quadro
            }
            boolean primeiraPessoa = mc.options.getCameraType().isFirstPerson();
            boolean discreto = euSou;                       // quem É o vazio vê menos
            boolean f1 = mc.options.hideGui && euSou;       // F1 do próprio: só o eco

            long agora = System.currentTimeMillis();
            if (agora == ultimoQuadro) {
                return;   // mesmo quadro
            }
            ultimoQuadro = agora;

            reciclar(agora, discreto);

            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());

            pose.pushPose();
            pose.translate(base.x - cam.x, base.y - cam.y, base.z - cam.z);

            int idx = 0;
            for (Tentaculo t : LISTA) {
                // quem é o vazio vê 2/3 deles (a tela dele tem que ficar legível)
                if (discreto && (idx++ % 3 == 2)) {
                    continue;
                }
                try {
                    desenhar(vc, pose.last().pose(), t, base, cam, agora, discreto, f1, primeiraPessoa);
                } catch (Throwable ignorado) {
                    // um tentáculo com problema não derruba os outros
                }
            }

            pose.popPose();
            buf.endBatch(RenderType.debugQuads());
        } catch (Throwable erro) {
            ativo = false;
            RPTagMod.LOGGER.error("RP Tag: tentaculos do vazio desligados nesta sessao", erro);
        }
    }

    /** Re-sorteia o tentáculo quando ele vence (ele renasce em outro lugar). */
    private static void reciclar(long agora, boolean discreto) {
        for (Tentaculo t : LISTA) {
            if (agora - t.nasceu <= t.vida) {
                continue;
            }
            t.nasceu = agora;
            t.vida = 4200L + (long) (SORTE.nextFloat() * 4800.0F);   // 4,2 a 9,0 s
            t.ang = SORTE.nextFloat() * Mth.TWO_PI;
            t.raio = 0.85F + SORTE.nextFloat() * 1.35F;              // 0,85 a 2,2
            t.altura = 1.05F + SORTE.nextFloat() * 1.15F;            // 1,05 a 2,2
            t.grosso = 0.085F + SORTE.nextFloat() * 0.065F;          // 0,085 a 0,15
            t.curva = (SORTE.nextFloat() - 0.35F) * 0.55F;           // abre ou fecha
            t.fase = SORTE.nextFloat() * 10.0F;
            t.semente = SORTE.nextFloat() * 100.0F;
            t.veios = 1 + SORTE.nextInt(3);
            if (discreto) {
                t.altura *= 0.78F;                                   // mais baixo pra ele
            }
        }
    }

    /**
     * Desenha UM tentáculo: tubo de seção quadrada, ponta em bico e umas pedras
     * soltas na base. Tudo com cor opaca (alpha 255) — pedra não é névoa.
     */
    private static void desenhar(VertexConsumer vc, org.joml.Matrix4f mat, Tentaculo t,
            Vec3 base, Vec3 cam, long agora, boolean discreto, boolean f1, boolean primeiraPessoa) {
        float c = t.crescido(agora);
        if (c <= 0.02F) {
            return;
        }
        float tempo = agora * 0.001F;

        // a base do tentáculo no chão (relativo ao pé do jogador, a pose já está no pé)
        double ax = Math.cos(t.ang) * t.raio;
        double az = Math.sin(t.ang) * t.raio;

        // não nasce embaixo da câmera de quem é o vazio (atravessaria a mira)
        if (euSou) {
            Vec3 mundo = base.add(ax, 0.0, az);
            if (mundo.distanceToSqr(cam) < 1.30 * 1.30) {
                return;
            }
            if (f1 && primeiraPessoa) {
                return;      // F1 do próprio: a tela fica limpa
            }
        }

        // ---------------- a espinha do tentáculo ----------------
        int n = SEGMENTOS;
        double[] px = new double[n + 1];
        double[] py = new double[n + 1];
        double[] pz = new double[n + 1];
        float[] pw = new float[n + 1];

        for (int i = 0; i <= n; i++) {
            float s = i / (float) n;                       // 0 (chão) -> 1 (ponta)
            // sobe com desaceleração (pedra cresce e fecha; não é antena)
            double h = t.altura * c * (1.0 - Math.pow(1.0 - s, 1.7));
            // abre pra fora no alto
            double abre = t.curva * s * s * t.altura * 0.75;
            // ondulação viva (lenta, como se estivesse se mexendo de leve)
            double ond = Math.sin(tempo * 0.9F + t.fase + s * 3.4F) * 0.085 * s;
            double lado = t.ang + Math.PI * 0.5;
            px[i] = ax + Math.cos(t.ang) * abre + Math.cos(lado) * ond;
            pz[i] = az + Math.sin(t.ang) * abre + Math.sin(lado) * ond;
            py[i] = h;
            // espessura: afina na ponta e PULSA (os "nós" da pedra)
            double no = 1.0 + 0.16 * Math.sin(s * 17.0 + t.fase * 2.1);
            pw[i] = (float) (t.grosso * c * (1.0 - 0.80 * s) * no);
        }

        // ---------------- as 4 faces de cada segmento ----------------
        float tomT = 0.86F + 0.26F * fraq(t.semente);        // pedra de cada tentáculo
        for (int i = 0; i < n; i++) {
            float s0 = i / (float) n;
            float s1 = (i + 1) / (float) n;
            // veios: faixas escuras de rachadura
            float veio = (((i + t.veios) % 5) == 0) ? 0.52F : 1.0F;
            float x0 = (float) px[i], y0 = (float) py[i], z0 = (float) pz[i], w0 = pw[i];
            float x1 = (float) px[i + 1], y1 = (float) py[i + 1], z1 = (float) pz[i + 1], w1 = pw[i + 1];

            // quatro cantos de cada anel (seção quadrada alinhada ao mundo)
            float[] c0 = { x0 - w0, x0 + w0, x0 + w0, x0 - w0 };
            float[] d0 = { z0 - w0, z0 - w0, z0 + w0, z0 + w0 };
            float[] c1 = { x1 - w1, x1 + w1, x1 + w1, x1 - w1 };
            float[] d1 = { z1 - w1, z1 - w1, z1 + w1, z1 + w1 };
            // luz falsa por face: +Z na luz, -Z na sombra, laterais no meio
            float[] luz = { 0.72F, 0.90F, 1.00F, 0.58F };

            for (int f = 0; f < 4; f++) {
                int g = (f + 1) % 4;
                float k = luz[f] * tomT * veio;
                // o alto fica um tico mais claro (pedra batida de sol)
                float k0 = k * (0.92F + 0.16F * s0);
                float k1 = k * (0.92F + 0.16F * s1);
                quad(vc, mat,
                        c0[f], y0, d0[f], k0,
                        c0[g], y0, d0[g], k0,
                        c1[g], y1, d1[g], k1,
                        c1[f], y1, d1[f], k1);
            }
        }

        // ---------------- o bico de pedra da ponta ----------------
        float xt = (float) px[n], yt = (float) py[n], zt = (float) pz[n], wt = pw[n];
        float bico = wt * 2.4F + 0.02F;
        float fT = 0.95F * tomT;
        // quatro triângulos (quads com a ponta repetida), um por face
        quad(vc, mat, xt - wt, yt, zt - wt, fT, xt + wt, yt, zt - wt, fT,
                xt, yt + bico, zt, fT * 1.05F, xt, yt + bico, zt, fT * 1.05F);
        quad(vc, mat, xt + wt, yt, zt - wt, fT, xt + wt, yt, zt + wt, fT,
                xt, yt + bico, zt, fT * 1.05F, xt, yt + bico, zt, fT * 1.05F);
        quad(vc, mat, xt + wt, yt, zt + wt, fT, xt - wt, yt, zt + wt, fT,
                xt, yt + bico, zt, fT * 1.05F, xt, yt + bico, zt, fT * 1.05F);
        quad(vc, mat, xt - wt, yt, zt + wt, fT, xt - wt, yt, zt - wt, fT,
                xt, yt + bico, zt, fT * 1.05F, xt, yt + bico, zt, fT * 1.05F);

        // ---------------- pedras soltas em volta da base ----------------
        // (o "chão rachou e subiu": pedaços de pedra no pé do tentáculo)
        int pedras = 3;
        for (int i = 0; i < pedras; i++) {
            float sa = t.semente + i * 2.399F;                 // ângulo áureo
            double ra = t.raio + 0.28 + 0.34 * fraq(sa * 3.1F);
            double aa = t.ang + (fraq(sa) - 0.5F) * 1.5F;
            float cx = (float) (Math.cos(aa) * ra);
            float cz = (float) (Math.sin(aa) * ra);
            float tam = (0.06F + 0.09F * fraq(sa * 1.7F)) * c;
            float giro = sa * 1.7F;
            float sobe = tam * (1.0F + 0.6F * fraq(sa * 5.3F));
            cubo(vc, mat, cx, 0.0F, cz, tam, sobe, giro, tomT * (0.78F + 0.3F * fraq(sa * 2.3F)));
        }
    }

    /** Um quad (4 vértices) com a cor em ARGB opaca. */
    private static void quad(VertexConsumer vc, org.joml.Matrix4f mat,
            float x1, float y1, float z1, float k1,
            float x2, float y2, float z2, float k2,
            float x3, float y3, float z3, float k3,
            float x4, float y4, float z4, float k4) {
        vc.addVertex(mat, x1, y1, z1).setColor(cinza(k1));
        vc.addVertex(mat, x2, y2, z2).setColor(cinza(k2));
        vc.addVertex(mat, x3, y3, z3).setColor(cinza(k3));
        vc.addVertex(mat, x4, y4, z4).setColor(cinza(k4));
    }

    /** Um "cubo" de pedra girado em torno do Y (6 faces, opaco). */
    private static void cubo(VertexConsumer vc, org.joml.Matrix4f mat,
            float cx, float y0, float cz, float raio, float altura, float giro, float tom) {
        float ca = Mth.cos(giro), sa = Mth.sin(giro);
        // quatro cantos da base girados
        float[] bx = new float[4];
        float[] bz = new float[4];
        for (int i = 0; i < 4; i++) {
            float ang = (float) (Math.PI * 0.25 + i * Math.PI * 0.5);
            float x = Mth.cos(ang) * raio, z = Mth.sin(ang) * raio;
            bx[i] = cx + x * ca - z * sa;
            bz[i] = cz + x * sa + z * ca;
        }
        float y1 = y0 + altura;
        // laterais
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            float k = tom * (0.62F + 0.12F * i);
            quad(vc, mat, bx[i], y0, bz[i], k, bx[j], y0, bz[j], k,
                    bx[j], y1, bz[j], k * 1.10F, bx[i], y1, bz[i], k * 1.10F);
        }
        // topo (mais claro — é o que pega a luz)
        float kt = tom * 1.05F;
        quad(vc, mat, bx[0], y1, bz[0], kt, bx[1], y1, bz[1], kt,
                bx[2], y1, bz[2], kt, bx[3], y1, bz[3], kt);
    }

    /** Cor da pedra: cinza-neutro com leve azul, opaco (alpha 255). */
    private static int cinza(float k) {
        float r = Mth.clamp(0.40F * k, 0.0F, 1.0F);
        float g = Mth.clamp(0.40F * k, 0.0F, 1.0F);
        float b = Mth.clamp(0.44F * k, 0.0F, 1.0F);
        int ri = (int) (r * 255.0F), gi = (int) (g * 255.0F), bi = (int) (b * 255.0F);
        return (255 << 24) | (ri << 16) | (gi << 8) | bi;
    }

    /** parte fracionária 0..1 (ruído barato e estável a partir de uma semente). */
    private static float fraq(float s) {
        float x = Mth.sin(s * 12.9898F) * 43758.5453F;
        return x - (float) Math.floor(x);
    }
}
