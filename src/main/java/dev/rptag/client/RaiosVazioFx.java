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
 * RAIOS DO VAZIO — 5.7.4.
 *
 * <h2>Por que existe</h2>
 * Na 5.7.3 os "raios negros e vermelhos" eram feitos de PARTÍCULA (dust saindo do
 * corpo). O dono: <i>"os raios negros e vermelhos eu não quero apenas partículas,
 * quero animação de verdade, quero que em volta do vazio seja raios negros
 * vermelhos para todo lado, não atrapalhando quem é o vazio e dando medo a quem
 * ver"</i>.
 *
 * <p>Então agora são GEOMETRIA: fitas 3D (quads em {@link RenderType#lightning()})
 * desenhadas no mundo em volta do jogador do vazio, com zigue-zague de verdade.
 * Cada raio tem vida de 0,10–0,26 s e depois RE-ESTALA em outra direção — é
 * animação, não nuvem de ponto.
 *
 * <h2>Regras que o dono pediu</h2>
 * <ul>
 *   <li><b>para todo lado</b>: as direções são espalhadas em 360° e também pra
 *       cima e pra baixo (não é só um anel na horizontal);</li>
 *   <li><b>não atrapalhar quem é o vazio</b>: se o jogador local é o próprio
 *       vazio, os raios ficam mais finos e transparentes (e no F1 só 1/3 deles
 *       aparece, começando longe do rosto) — ele precisa mirar;</li>
 *   <li><b>dar medo a quem vê</b>: para os OUTROS jogadores os raios são
 *       grandes, vermelhos por fora e pretos por dentro, com estalo de trovão
 *       grave vindo do servidor.</li>
 * </ul>
 *
 * <h2>Desenho</h2>
 * Cada segmento do zigue-zague vira uma fita: o vetor do segmento é cruzado
 * com a direção até a câmera para achar a "largura" (assim a fita sempre fica
 * de frente pra quem olha, sem depender da posição). São duas passadas: primeiro
 * a capa VERMELHA (mais larga, alpha menor) e depois o núcleo NEGRO.
 *
 * <p>Regra de ouro do mod: erro aqui desliga os raios, nunca o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class RaiosVazioFx {

    private RaiosVazioFx() {
    }

    /** quantos raios ficam vivos ao mesmo tempo. */
    private static final int RAIOS = 14;
    /** vida de cada raio (ms). */
    private static final long VIDA_MIN = 95L, VIDA_MAX = 260L;

    private static final Random SORTE = new Random();

    /** Um raio: direção, serrilhado, quando nasceu e quanto vive. */
    private static final class Raio {
        float ax, ay, az;      // direção normalizada (unidade)
        float alcance;         // quanto anda (blocos)
        float largura;         // espessura do núcleo
        long nasceu, vida;
        float semente;         // fase do serrilhado
        int pontos;            // quantos segmentos
        float origemY;         // altura de saída (corpo / chão / alto)
    }

    private static final Raio[] LISTA = new Raio[RAIOS];
    private static boolean ativo;
    private static boolean euSou;         // o jogador local É o vazio
    private static UUID alvo;
    private static long ultimoQuadro = 0L;
    /**
     * (5.7.6) SURTO DA TRANSFORMAÇÃO — nos 2,5 s seguintes ao "ligar", os raios
     * ficam MAIS VERMELHOS, mais grossos e mais longos (o dono: "as particulas
     * vermelhas devem virar raios vermelhos mais intensos, para dar uma
     * diferenciada na transformação"). Depois volta ao normal.
     */
    private static long surtoAte = 0L;

    static {
        for (int i = 0; i < RAIOS; i++) {
            LISTA[i] = new Raio();
            LISTA[i].vida = 1L;   // nasce no primeiro quadro
        }
    }

    /** Chamado pelo pacote de aura: quem é o vazio e se sou eu. */
    public static void definir(UUID id, boolean souEu) {
        if (id == null) {
            ativo = false;
            alvo = null;
            return;
        }
        if (!id.equals(alvo) || !ativo) {
            // trocou de vazio (ou ligou agora): todos os raios re-estalam juntos
            for (Raio r : LISTA) {
                r.nasceu = 0L;
                r.vida = 1L;
            }
            surtoAte = System.currentTimeMillis() + 2500L;   // (5.7.6) transformação
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

    // ------------------------------------------------------------------ render
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!ligado()) {
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
            Player dono = mc.level.getPlayerByUUID(alvo);
            if (dono == null || dono.isSpectator()) {
                return;
            }
            Vec3 cam = event.getCamera().getPosition();
            // interpola a posição (senão os raios "pulam" entre ticks)
            float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            Vec3 base = dono.getPosition(pt);
            if (base.distanceToSqr(cam) > 96.0 * 96.0) {
                return;   // longe demais: não gasta quadro
            }
            boolean primeiraPessoa = mc.options.getCameraType().isFirstPerson();
            boolean perto = base.distanceToSqr(cam) < 3.0 * 3.0;
            boolean suave = euSou;                  // quem é o vazio vê mais discreto

            long agora = System.currentTimeMillis();
            if (agora == ultimoQuadro) {
                return;   // mesmo quadro (não re-sorteia duas vezes)
            }
            ultimoQuadro = agora;

            atualizar(agora, base, cam, primeiraPessoa && euSou);

            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buf.getBuffer(RenderType.lightning());

            pose.pushPose();
            pose.translate(base.x - cam.x, base.y - cam.y, base.z - cam.z);

            int idx = 0;
            for (Raio r : LISTA) {
                // (5.7.4) quem É o vazio vê metade dos raios: a tela dele fica
                // legível (ele precisa mirar). Quem olha de fora vê todos.
                if (suave && (idx++ % 2 == 1)) {
                    continue;
                }
                desenharRaio(pose, vc, r, base, cam, suave, primeiraPessoa && euSou, perto);
            }

            pose.popPose();
            buf.endBatch(RenderType.lightning());
        } catch (Throwable erro) {
            ativo = false;
            RPTagMod.LOGGER.error("RP Tag: raios do vazio desligados nesta sessao", erro);
        }
    }

    /** Re-estala os raios vencidos: direção/alcance/serrilhado novos. */
    private static void atualizar(long agora, Vec3 base, Vec3 cam, boolean f1Proprio) {
        for (Raio r : LISTA) {
            if (agora - r.nasceu <= r.vida) {
                continue;
            }
            r.nasceu = agora;
            r.vida = VIDA_MIN + (long) (SORTE.nextFloat() * (VIDA_MAX - VIDA_MIN));

            // direção: 360° no plano + inclinação pra cima/baixo (para todo lado)
            float az = SORTE.nextFloat() * Mth.TWO_PI;
            float el = (SORTE.nextFloat() - 0.5F) * 1.5F;      // -0.75..0.75 rad
            float cy = Mth.cos(el);
            r.ax = Mth.cos(az) * cy;
            r.ay = Mth.sin(el);
            r.az = Mth.sin(az) * cy;

            r.alcance = 1.8F + SORTE.nextFloat() * 4.6F;        // 1,8 a 6,4 blocos
            r.largura = 0.022F + SORTE.nextFloat() * 0.030F;
            // (5.7.6) durante o surto da transformação tudo é maior
            if (System.currentTimeMillis() < surtoAte) {
                r.alcance *= 1.25F;
                r.largura *= 1.45F;
            }
            r.semente = SORTE.nextFloat() * 100F;
            r.pontos = 6 + SORTE.nextInt(5);
            // 3 alturas de saída: chão, corpo, cabeça — dá volume à coisa
            float sorte = SORTE.nextFloat();
            r.origemY = sorte < 0.35F ? 0.05F : (sorte < 0.85F ? 1.0F : 1.85F);

            // no F1 do PRÓPRIO vazio: nada de raio atravessando a mira
            if (f1Proprio) {
                Vec3 dir = new Vec3(r.ax, r.ay, r.az);
                Vec3 paraCam = cam.subtract(base.add(0, r.origemY, 0)).normalize();
                if (dir.dot(paraCam) > 0.35) {
                    // aponta pra câmera: encurta e joga pra trás (fica só o eco)
                    r.alcance *= 0.35F;
                }
            }
        }
    }

    private static void desenharRaio(PoseStack pose, VertexConsumer vc, Raio r, Vec3 base, Vec3 cam,
            boolean suave, boolean f1Proprio, boolean perto) {
        long agora = System.currentTimeMillis();
        float vida = (agora - r.nasceu) / (float) Math.max(1L, r.vida);   // 0..1
        if (vida > 1.0F) {
            return;
        }
        // o raio "pisca": aparece, fica forte e some (como um relâmpago)
        float forca = (float) Math.sin(Math.PI * Math.min(1.0F, vida * 1.15F));
        if (forca < 0.05F) {
            return;
        }

        // quem é o vazio vê tudo mais discreto; quem olha de fora vê grande
        float alphaMult = suave ? (f1Proprio ? 0.30F : 0.55F) : 1.0F;
        if (perto && !suave) {
            alphaMult *= 0.5F;    // alguém colou no vazio: não cega a pessoa
        }
        float larguraMult = suave ? 0.85F : 1.0F;

        // pontos do zigue-zague (relativos ao pé do jogador)
        int n = r.pontos;
        Vec3[] pts = new Vec3[n + 1];
        float ox = 0.18F * Mth.sin(r.semente * 2.3F);
        float oz = 0.18F * Mth.cos(r.semente * 1.7F);
        for (int i = 0; i <= n; i++) {
            float f = i / (float) n;
            // serrilhado: dois senos fora de fase + um "degrau" aleatório estável
            float jx = Mth.sin(r.semente + f * 9.1F) * 0.35F + (hash(r.semente, i) - 0.5F) * 0.30F;
            float jy = Mth.sin(r.semente * 1.7F + f * 7.3F) * 0.22F;
            float jz = Mth.cos(r.semente + f * 8.4F) * 0.35F + (hash(r.semente + 3.1F, i) - 0.5F) * 0.30F;
            pts[i] = new Vec3(
                    ox + r.ax * r.alcance * f + jx * f,
                    r.origemY + r.ay * r.alcance * f + jy * f,
                    oz + r.az * r.alcance * f + jz * f);
        }

        Vec3 centroMundo = base;
        for (int i = 0; i < n; i++) {
            Vec3 a = pts[i];
            Vec3 b = pts[i + 1];
            float f0 = i / (float) n;
            float f1 = (i + 1) / (float) n;

            Vec3 dir = b.subtract(a);
            if (dir.lengthSqr() < 1e-6) {
                continue;
            }
            dir = dir.normalize();
            // direção do meio do segmento até a câmera
            Vec3 meio = centroMundo.add(a.add(b).scale(0.5));
            Vec3 paraCam = cam.subtract(meio);
            if (paraCam.lengthSqr() < 1e-6) {
                continue;
            }
            paraCam = paraCam.normalize();
            Vec3 perp = dir.cross(paraCam);
            if (perp.lengthSqr() < 1e-6) {
                continue;
            }
            perp = perp.normalize();

            // núcleo preto-roxo por dentro, vermelho por fora (2 passadas)
            float baseW = r.largura * larguraMult;
            org.joml.Matrix4f mat = pose.last().pose();
            // (5.7.6) no SURTO a capa vermelha fica maior e mais quente; fora
            // dele o vermelho continua forte mas o miolo preto domina mais.
            boolean surto = System.currentTimeMillis() < surtoAte;
            float capa = surto ? 3.4F : 2.6F;
            float aCapa = (surto ? 0.72F : 0.50F) * forca * alphaMult;
            float aPonta = (surto ? 0.95F : 0.72F) * forca * alphaMult;
            fita(vc, mat, a, b, perp, baseW * capa,
                    cor(surto ? 0.72F : 0.52F, 0.02F, 0.03F, aCapa),
                    cor(1.0F, surto ? 0.22F : 0.06F, 0.05F, aPonta), f0, f1);
            fita(vc, mat, a, b, perp, baseW,
                    cor(0.015F, 0.0F, 0.02F, 0.95F * forca * alphaMult),
                    cor(0.08F, 0.0F, 0.10F, 0.60F * forca * alphaMult), f0, f1);
        }
    }

    /** Uma fita (quad) de a até b, com largura w, cor em a e cor em b. */
    private static void fita(VertexConsumer vc, org.joml.Matrix4f mat, Vec3 a, Vec3 b, Vec3 perp,
            float w, int ca, int cb, float f0, float f1) {
        float w0 = w * (1.0F - f0 * 0.75F);    // afina na ponta
        float w1 = w * (1.0F - f1 * 0.75F);
        float px = (float) perp.x, py = (float) perp.y, pz = (float) perp.z;
        vc.addVertex(mat, (float) a.x - px * w0, (float) a.y - py * w0, (float) a.z - pz * w0).setColor(ca);
        vc.addVertex(mat, (float) a.x + px * w0, (float) a.y + py * w0, (float) a.z + pz * w0).setColor(ca);
        vc.addVertex(mat, (float) b.x + px * w1, (float) b.y + py * w1, (float) b.z + pz * w1).setColor(cb);
        vc.addVertex(mat, (float) b.x - px * w1, (float) b.y - py * w1, (float) b.z - pz * w1).setColor(cb);
    }

    /** cor ARGB a partir de floats 0..1. */
    private static int cor(float r, float g, float b, float a) {
        int ai = (int) (Mth.clamp(a, 0.0F, 1.0F) * 255.0F);
        int ri = (int) (Mth.clamp(r, 0.0F, 1.0F) * 255.0F);
        int gi = (int) (Mth.clamp(g, 0.0F, 1.0F) * 255.0F);
        int bi = (int) (Mth.clamp(b, 0.0F, 1.0F) * 255.0F);
        return (ai << 24) | (ri << 16) | (gi << 8) | bi;
    }

    /** ruído estável (mesmo valor pro mesmo i) — o serrilhado não "ferve". */
    private static float hash(float s, int i) {
        float x = Mth.sin(s * 12.9898F + i * 78.233F) * 43758.5453F;
        return x - (float) Math.floor(x);
    }
}
