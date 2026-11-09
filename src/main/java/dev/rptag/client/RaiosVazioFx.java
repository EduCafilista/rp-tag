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
 * RAIOS VERMELHOS DO VAZIO — geometria 3D (5.11.1).
 *
 * <h2>A ideia é a do dono, não a minha</h2>
 * <i>"quero os raios vermelhos saindo de deus, se não como vai funcionar quando ele
 * estiver voando? quero essa aura em volta dele para ser algo bizarro"</i>.
 *
 * <p>Então: <b>raios vermelhos saindo do corpo</b> — para todo lado, em 3D, e
 * <b>presos no jogador</b> (nunca no chão). Voando, de pé, dentro d'água, no
 * Nether ou no exílio: os raios vão junto com ele, porque tudo é medido a partir
 * do corpo (pé / peito / cabeça) e não do bloco embaixo.
 *
 * <h2>Por que os raios de antes "sumiam com shader" (e o que mudou aqui)</h2>
 * A versão 5.7.4 desenhava em {@link RenderType#lightning()}, que é
 * <b>blend ADITIVO</b> ({@code SRC_ALPHA, ONE}): soma luz na tela. Num pack de
 * shader claro a soma estoura em branco e o vermelho desaparece.
 *
 * <p>Agora é {@link RenderType#debugQuads()}: {@code POSITION_COLOR}, QUADS,
 * <b>blend NORMAL</b> ({@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}) e <b>escreve
 * profundidade</b>. Vermelho por cima de céu claro continua vermelho, porque
 * não soma — ele <i>substitui</i> a cor por uma mistura. Funciona com Iris,
 * Sodium e no jogo puro.
 *
 * <h2>Como cada raio é desenhado (3 passadas, sem z-fighting)</h2>
 * <ol>
 *   <li><b>capa</b> vermelho-escuro, larga (2,4×), alpha ~0,55 — pega o "corpo" do raio;</li>
 *   <li><b>corpo</b> vermelho vivo, largura normal, alpha ~0,9 — é o que lê como raio;</li>
 *   <li><b>núcleo</b> laranja-quente, fino (0,38×), opaco — estala por dentro.</li>
 * </ol>
 * As três passadas ficam a poucos milímetros uma da outra <b>na direção da
 * câmera</b> (não no plano da tela), então nunca dão z-fighting e o núcleo
 * sempre aparece em cima do corpo.
 *
 * <h2>"Bizarro"</h2>
 * <ul>
 *   <li>duas famílias: <b>longos</b> (1,6 a 6 blocos, 10 raios) e <b>cerdas</b>
 *       curtas (0,35 a 1,1 bloco, 20 raios) grudadas no corpo — é o que faz
 *       parecer que a coisa está <i>fervendo</i> em volta dele;</li>
 *   <li>o serrilhado é <b>re-sorteado a cada ~110 ms</b>, então os raios
 *       estalam sem parar (não é um desenho congelado);</li>
 *   <li>o conjunto <b>gira devagar</b> em volta do corpo;</li>
 *   <li>quem <b>é</b> o vazio vê menos e mais fino (precisa mirar); quem olha de
 *       fora vê tudo.</li>
 * </ul>
 *
 * <p>Regra de ouro do mod: erro aqui desliga os raios, nunca o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class RaiosVazioFx {

    private RaiosVazioFx() {
    }

    /** raios longos vivos ao mesmo tempo. */
    private static final int LONGOS = 10;
    /** cerdas curtas coladas no corpo. */
    private static final int CERDAS = 20;
    private static final int TOTAL = LONGOS + CERDAS;

    private static final long VIDA_MIN = 130L, VIDA_MAX = 420L;
    /** de quanto em quanto tempo o serrilhado é re-sorteado (o "estalo"). */
    private static final long ESTALO = 110L;
    /** além disso não vale a pena desenhar (blocos). */
    private static final double ALCANCE = 96.0;

    private static final Random SORTE = new Random();

    /** Um raio: direção, tamanho, e quando estala / morre. */
    private static final class Raio {
        float ax, ay, az;       // direção (unidade), 3D — funciona voando
        float alcance;          // comprimento em blocos
        float largura;          // espessura do corpo
        float origem;           // altura de saída no corpo (0 = pé, 1 = peito, 1,85 = cabeça)
        float semente;          // fase do serrilhado
        float curva;            // desvio tangencial (o raio "entorta" pelo ar)
        int pontos;             // segmentos do zigue-zague
        long nasceu;
        long vida;
        long proxEstalo;
    }

    private static final Raio[] LISTA = new Raio[TOTAL];
    private static boolean ativo;
    private static boolean euSou;
    private static UUID alvo;
    private static long ultimoQuadro = 0L;
    /** (5.11.1) giro lento do conjunto em volta do corpo (radianos). */
    private static float giro = 0.0F;
    /** surto da transformação: tudo mais grosso e mais comprido por 2,5 s. */
    private static long surtoAte = 0L;

    static {
        for (int i = 0; i < TOTAL; i++) {
            LISTA[i] = new Raio();
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
            for (Raio r : LISTA) {
                r.nasceu = 0L;      // todos estalam juntos
                r.vida = 1L;
            }
            surtoAte = System.currentTimeMillis() + 2500L;   // transformação
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

    /** (5.13.0) quem é o vazio (o manto de escuridão usa o mesmo alvo dos raios). */
    public static UUID alvoUuid() {
        return ativo ? alvo : null;
    }

    /** (5.13.0) o surto da transformação ainda está de pé? */
    public static boolean emSurto() {
        return System.currentTimeMillis() < surtoAte;
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
                return;   // longe demais
            }

            boolean primeiraPessoa = mc.options.getCameraType().isFirstPerson();
            boolean f1 = mc.options.hideGui && euSou;
            boolean perto = base.distanceToSqr(cam) < 3.0 * 3.0;

            long agora = System.currentTimeMillis();
            if (agora == ultimoQuadro) {
                return;   // mesmo quadro
            }
            ultimoQuadro = agora;

            giro += 0.012F;                       // o conjunto roda devagar
            if (giro > Mth.TWO_PI) {
                giro -= Mth.TWO_PI;
            }
            atualizar(agora, base, cam, primeiraPessoa && euSou, f1);

            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());

            pose.pushPose();
            pose.translate(base.x - cam.x, base.y - cam.y, base.z - cam.z);

            // (5.13.1) O MANTO SAIU. O dono: "esses quadrados grandes ficaram esquisitos,
            // melhore isso". Eram 12 "nuvens" desenhadas como quads virados para a câmera —
            // de perto viravam caixas pretas flutuando em volta do corpo. A aura agora é
            // SÓ o que ele aprovou: os raios vermelhos (e o rastro no chão, RastroVazioFx).
            // (5.13.2) "não tem nada preto mais nos efeitos em volta dele, está quase
            // lá": volta a ESCURIDÃO no corpo — mas em geometria VERTICAL em volta dele
            // (cilindro de 10 lados), nunca quadrado virado pra câmera.
            try {
                desenharSombra(vc, pose.last().pose(), base, cam, agora);
            } catch (Throwable ignorado) {
                // sombra é enfeite: se falhar, os raios seguem
            }

            int idx = 0;
            for (Raio r : LISTA) {
                idx++;
                // quem É o vazio vê 2/3 dos raios (a tela dele tem que ficar legível)
                if (euSou && (idx % 3 == 0)) {
                    continue;
                }
                try {
                    desenhar(vc, pose.last().pose(), r, base, cam, agora);
                } catch (Throwable ignorado) {
                    // um raio com problema não derruba os outros
                }
            }

            pose.popPose();
            buf.endBatch(RenderType.debugQuads());
        } catch (Throwable erro) {
            ativo = false;
            RPTagMod.LOGGER.error("RP Tag: raios do vazio desligados nesta sessao", erro);
        }
    }

    /**
     * (5.13.2) A ESCURIDÃO DO CORPO — o "manto" que o dono sentiu falta, feito do jeito
     * que não dá quadrado na cara.
     *
     * <p>Dez faixas <b>verticais</b> em volta do jogador (como as tábuas de um barril):
     * cada faixa nasce no pé, sobe até a cabeça e é mais opaca embaixo. Girando devagar,
     * com alpha baixo (0,10–0,22), elas leem como uma coluna de fumaça preta em volta
     * do corpo — e, por serem verticais e coladas nele, nunca viram aqueles quadrados
     * grandes que apareciam no F5.
     */
    private static void desenharSombra(VertexConsumer vc, org.joml.Matrix4f mat,
            Vec3 base, Vec3 cam, long agora) {
        int lados = 10;
        float altura = 1.95F;
        float raio = 0.62F + 0.06F * Mth.sin(agora * 0.0025F);
        float pulso = 0.5F + 0.5F * Mth.sin(agora * 0.0017F);
        int escuro = cor(0.015F, 0.010F, 0.03F, 0.16F + 0.08F * pulso);
        int some = cor(0.02F, 0.015F, 0.04F, 0.0F);
        for (int i = 0; i < lados; i++) {
            double a0 = giro * 0.6 + i * (Mth.TWO_PI / lados);
            double a1 = giro * 0.6 + (i + 1) * (Mth.TWO_PI / lados);
            float r0 = raio * (0.85F + 0.3F * Math.abs(Mth.sin(i * 1.7F + giro)));
            float r1 = raio * (0.85F + 0.3F * Math.abs(Mth.sin((i + 1) * 1.7F + giro)));
            float x0 = (float) (base.x + Math.cos(a0) * r0);
            float z0 = (float) (base.z + Math.sin(a0) * r0);
            float x1 = (float) (base.x + Math.cos(a1) * r1);
            float z1 = (float) (base.z + Math.sin(a1) * r1);
            float y0 = (float) base.y - 0.05F;
            float y1 = (float) base.y + altura;
            // base escura -> topo transparente
            vc.addVertex(mat, x0, y0, z0).setColor(escuro);
            vc.addVertex(mat, x1, y0, z1).setColor(escuro);
            vc.addVertex(mat, x1, y1, z1).setColor(some);
            vc.addVertex(mat, x0, y1, z0).setColor(some);
            // e o mesmo par invertido (visível dos dois lados)
            vc.addVertex(mat, x1, y0, z1).setColor(escuro);
            vc.addVertex(mat, x0, y0, z0).setColor(escuro);
            vc.addVertex(mat, x0, y1, z0).setColor(some);
            vc.addVertex(mat, x1, y1, z1).setColor(some);
        }
    }

    /** Re-sorteia o que venceu: raio novo em outra direção, ou só o estalo. */
    private static void atualizar(long agora, Vec3 base, Vec3 cam, boolean f1Proprio, boolean f1) {
        boolean surto = agora < surtoAte;
        for (int i = 0; i < TOTAL; i++) {
            Raio r = LISTA[i];
            // ------- estalo: só re-sorteia o serrilhado (o raio não muda de lugar)
            if (agora >= r.proxEstalo) {
                r.proxEstalo = agora + ESTALO + (long) (SORTE.nextFloat() * 70.0F);
                r.semente = SORTE.nextFloat() * 100.0F;
            }
            // ------- fim da vida: raio novo
            if (agora - r.nasceu > r.vida) {
                r.nasceu = agora;
                r.vida = VIDA_MIN + (long) (SORTE.nextFloat() * (VIDA_MAX - VIDA_MIN));
                r.proxEstalo = agora + ESTALO;
                r.semente = SORTE.nextFloat() * 100.0F;

                // direção 3D: 360° no plano + inclinação pra cima/baixo.
                // Em 3D porque ele pode estar VOANDO — nada aqui depende do chão.
                float az = SORTE.nextFloat() * Mth.TWO_PI + giro;
                float el = (SORTE.nextFloat() - 0.5F) * 2.3F;      // -1,15..1,15 rad
                float cy = Mth.cos(el);
                r.ax = Mth.cos(az) * cy;
                r.ay = Mth.sin(el);
                r.az = Mth.sin(az) * cy;

                boolean cerda = i >= LONGOS;
                if (cerda) {
                    // cerdas: curtas, coladas no corpo — é a "aura fervendo"
                    r.alcance = 0.35F + SORTE.nextFloat() * 0.75F;
                    r.largura = 0.013F + SORTE.nextFloat() * 0.016F;
                } else {
                    r.alcance = 1.6F + SORTE.nextFloat() * 4.4F;
                    r.largura = 0.024F + SORTE.nextFloat() * 0.030F;
                }
                if (surto) {
                    r.alcance *= 1.28F;
                    r.largura *= 1.40F;
                }
                r.curva = (SORTE.nextFloat() - 0.5F) * 0.9F;
                r.pontos = cerda ? 4 + SORTE.nextInt(3) : 7 + SORTE.nextInt(4);

                // 3 alturas de saída no corpo: pé, peito, cabeça
                float sorteio = SORTE.nextFloat();
                r.origem = sorteio < 0.30F ? 0.10F : (sorteio < 0.85F ? 1.05F : 1.85F);

                // no F1 do PRÓPRIO vazio: nada de raio atravessando a mira
                if (f1Proprio) {
                    Vec3 dir = new Vec3(r.ax, r.ay, r.az);
                    Vec3 paraCam = cam.subtract(base.add(0, r.origem, 0)).normalize();
                    if (dir.dot(paraCam) > 0.30) {
                        r.alcance *= 0.30F;
                    }
                }
            }
        }
        // F1 do próprio: os raios só "respiram" longe do rosto (não cega)
        if (f1) {
            // nada a sortear aqui; só garante que o laço acima rodou uma vez
            // (mantido por clareza do contrato com o método)
        }
    }

    /** Desenha UM raio em 3 passadas (capa escura, corpo vermelho, núcleo quente). */
    private static void desenhar(VertexConsumer vc, org.joml.Matrix4f mat, Raio r,
            Vec3 base, Vec3 cam, long agora) {
        float idade = (agora - r.nasceu) / (float) Math.max(1L, r.vida);
        if (idade >= 1.0F) {
            return;
        }
        // o raio "pisca": nasce, fica forte e morre
        float forca = (float) Math.sin(Math.PI * Math.min(1.0F, idade * 1.10F));
        if (forca < 0.06F) {
            return;
        }
        boolean surto = agora < surtoAte;

        // ---------- pontos do zigue-zague (relativos ao pé do jogador) ----------
        int n = r.pontos;
        Vec3[] pts = new Vec3[n + 1];
        float ox = 0.16F * Mth.sin(r.semente * 2.3F);
        float oz = 0.16F * Mth.cos(r.semente * 1.7F);
        // vetor tangencial (pro "curva" entortar o raio pelo ar)
        double tx = -r.az, tz = r.ax;
        for (int i = 0; i <= n; i++) {
            float f = i / (float) n;
            float jx = Mth.sin(r.semente + f * 9.1F) * 0.34F + (hash(r.semente, i) - 0.5F) * 0.30F;
            float jy = Mth.sin(r.semente * 1.7F + f * 7.3F) * 0.20F;
            float jz = Mth.cos(r.semente + f * 8.4F) * 0.34F + (hash(r.semente + 3.1F, i) - 0.5F) * 0.30F;
            double entorta = r.curva * f * f * r.alcance * 0.20;
            pts[i] = new Vec3(
                    ox + r.ax * r.alcance * f + jx * f + tx * entorta,
                    r.origem + r.ay * r.alcance * f + jy * f,
                    oz + r.az * r.alcance * f + jz * f + tz * entorta);
        }

        for (int i = 0; i < n; i++) {
            Vec3 a = pts[i];
            Vec3 b = pts[i + 1];
            Vec3 dir = b.subtract(a);
            if (dir.lengthSqr() < 1e-7) {
                continue;
            }
            dir = dir.normalize();
            Vec3 meio = base.add(a.add(b).scale(0.5));
            Vec3 paraCam = cam.subtract(meio);
            if (paraCam.lengthSqr() < 1e-7) {
                continue;
            }
            paraCam = paraCam.normalize();
            Vec3 perp = dir.cross(paraCam);
            if (perp.lengthSqr() < 1e-7) {
                continue;
            }
            perp = perp.normalize();

            float f0 = i / (float) n;
            float f1 = (i + 1) / (float) n;
            // afina na ponta
            float afina0 = 1.0F - f0 * 0.78F;
            float afina1 = 1.0F - f1 * 0.78F;
            float w = r.largura * (surto ? 1.35F : 1.0F);

            // deslocamento em direção à câmera (evita z-fighting entre as passadas)
            Vec3 e1 = paraCam.scale(0.012);
            Vec3 e2 = paraCam.scale(0.024);

            // (5.27.0) MAIS ESCURO, "MAIS VAZIO" — pedido do dono: "acho que os raios vermelho
            // que saem no vazio ligar, podem ser mais escuros, mais vazio né?". As três camadas
            // desceram de tom: a capa virou sangue seco, o corpo um vermelho fundo de breu e o
            // núcleo deixou de ser quase branco (era o que fazia parecer "energia", não vazio).
            // ---------------- 1) CAPA vermelho-escura, larga ----------------
            // (é a que segura o raio contra céu claro / shader claro: alpha normal,
            //  não soma, então o vermelho "mancha" a tela em vez de lavar)
            fita(vc, mat, a, b, perp, w * 2.35F, afina0, afina1,
                    cor(0.22F, 0.005F, 0.012F, 0.42F * forca),
                    cor(0.28F, 0.008F, 0.018F, 0.36F * forca));

            // ---------------- 2) CORPO vermelho vivo ----------------
            Vec3 a2 = a.add(e1), b2 = b.add(e1);
            fita(vc, mat, a2, b2, perp, w, afina0, afina1,
                    cor(0.44F, 0.012F, 0.025F, 0.80F * forca),
                    cor(0.52F, 0.020F, 0.030F, 0.72F * forca));

            // ---------------- 3) NÚCLEO quente, quase branco ----------------
            Vec3 a3 = a.add(e2), b3 = b.add(e2);
            fita(vc, mat, a3, b3, perp, w * 0.38F, afina0, afina1,
                    cor(0.66F, 0.10F, 0.08F, 0.85F * forca),
                    cor(0.58F, 0.06F, 0.05F, 0.78F * forca));
        }

        // ponta "estalando": um losango quente no fim do raio
        Vec3 p = pts[n];
        float t = r.largura * (surto ? 3.0F : 2.4F);
        if (p.distanceToSqr(cam.subtract(base)) > 2.0) {
            quadPequeno(vc, mat, p, t, cor(0.62F, 0.08F, 0.06F, 0.80F * forca));
        }
    }

    /** Uma fita (quad) de a até b, com largura w e cor em cada ponta. */
    private static void fita(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 a, Vec3 b, Vec3 perp,
            float w, float afina0, float afina1, int ca, int cb) {
        float w0 = w * afina0;
        float w1 = w * afina1;
        float px = (float) perp.x, py = (float) perp.y, pz = (float) perp.z;
        vc.addVertex(mat, (float) a.x - px * w0, (float) a.y - py * w0, (float) a.z - pz * w0).setColor(ca);
        vc.addVertex(mat, (float) a.x + px * w0, (float) a.y + py * w0, (float) a.z + pz * w0).setColor(ca);
        vc.addVertex(mat, (float) b.x + px * w1, (float) b.y + py * w1, (float) b.z + pz * w1).setColor(cb);
        vc.addVertex(mat, (float) b.x - px * w1, (float) b.y - py * w1, (float) b.z - pz * w1).setColor(cb);
    }

    /** Um losango (2 quads cruzados) no ponto p — a "faísca" da ponta. */
    private static void quadPequeno(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 p, float t, int c) {
        float x = (float) p.x, y = (float) p.y, z = (float) p.z;
        vc.addVertex(mat, x - t, y, z).setColor(c);
        vc.addVertex(mat, x, y, z - t).setColor(c);
        vc.addVertex(mat, x + t, y, z).setColor(c);
        vc.addVertex(mat, x, y, z + t).setColor(c);
        vc.addVertex(mat, x, y - t, z).setColor(c);
        vc.addVertex(mat, x, y, z - t).setColor(c);
        vc.addVertex(mat, x, y + t, z).setColor(c);
        vc.addVertex(mat, x, y, z + t).setColor(c);
    }

    /** cor ARGB a partir de floats 0..1. */
    private static int cor(float r, float g, float b, float a) {
        int ai = (int) (Mth.clamp(a, 0.0F, 1.0F) * 255.0F);
        int ri = (int) (Mth.clamp(r, 0.0F, 1.0F) * 255.0F);
        int gi = (int) (Mth.clamp(g, 0.0F, 1.0F) * 255.0F);
        int bi = (int) (Mth.clamp(b, 0.0F, 1.0F) * 255.0F);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    /** ruído estável (mesmo valor pro mesmo i) — o serrilhado não "ferve" sozinho. */
    private static float hash(float s, int i) {
        float x = Mth.sin(s * 12.9898F + i * 78.233F) * 43758.5453F;
        return x - (float) Math.floor(x);
    }
}
