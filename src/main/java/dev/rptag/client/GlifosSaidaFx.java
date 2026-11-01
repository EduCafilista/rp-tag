package dev.rptag.client;

import java.util.Random;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.rptag.NeptunePackets;
import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * CÓDIGOS EM VOLTA DO PLAYER — 5.7.7.
 *
 * <h2>Por que existe</h2>
 * O dono explicou o que entendia por "animação de saída": <i>"quando eu falei
 * de animação, eu quis dizer uns efeitos em volta do player, digitalizando
 * códigos em números verdes por exemplo, como se estivessem sendo computados
 * para sair do servidor"</i>. A animação de tela (o bonequinho se desfazendo,
 * {@link SaidaPlayerFx}) continua — mas agora os CÓDIGOS existem no MUNDO, em
 * volta do corpo, e <b>todo mundo vê</b>.
 *
 * <h2>O que é desenhado</h2>
 * <ul>
 *   <li><b>3 anéis</b> de glifos (0/1/A-F e pedaços de "session.close()") em
 *       alturas diferentes, girando em sentidos alternados em volta do corpo;</li>
 *   <li>os glifos <b>sobem</b> e desaparecem — é a "digitalização", como se o
 *       personagem estivesse sendo lido e apagado bit a bit;</li>
 *   <li>uma <b>coluna de scan</b> verde-claro sobe pelo corpo;</li>
 *   <li>um <b>anel de glifos no chão</b>, que gira mais rápido no começo;</li>
 *   <li>o nome de quem saiu, em verde, flutuando acima da cabeça.</li>
 * </ul>
 *
 * <p>O desenho é BILLBOARD: cada glifo é um texto 2D desenhado com a matriz da
 * câmera, então ele sempre encara quem olha (não fica de lado como uma placa).
 *
 * <p>Regra de ouro do mod: erro aqui desliga o efeito, nunca o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class GlifosSaidaFx {

    private GlifosSaidaFx() {
    }

    // ------------------------------------------------------------------ ajuste
    /** quantos glifos no total (anéis do corpo + chão). */
    private static final int GLIFOS = 44;
    /** duração padrão quando o pacote não manda (ms). */
    private static final int DUR_PADRAO = 6000;
    private static final String[] CODIGOS = {
            "0", "1", "0x1F", "01", "7E", "session.close();", "player.save();", "0101",
            "0xB4", "11", "EOF", "saving", "3C", "0x00", "10", "NULL", "A9", "01"
    };
    private static final int VERDE = 0x5BFF9A;
    private static final int VERDE_CLARO = 0xCFFFE2;
    private static final int VERDE_ESCURO = 0x1FA85C;

    private static final Random SORTE = new Random();

    // ------------------------------------------------------------------ estado
    private static boolean ativo;
    private static Vec3 pos = Vec3.ZERO;
    private static long inicio, fim;
    private static String nome = "";
    private static boolean falhou;

    /** Chamado pelo pacote {@link NeptunePackets.Codigos}. */
    public static void iniciar(NeptunePackets.Codigos p) {
        try {
            pos = new Vec3(p.x(), p.y(), p.z());
            inicio = System.currentTimeMillis();
            fim = inicio + Math.max(1200, p.ms() > 0 ? p.ms() : DUR_PADRAO);
            nome = p.nome() == null ? "" : p.nome();
            ativo = true;
            falhou = false;
        } catch (Throwable t) {
            ativo = false;
        }
    }

    public static void parar() {
        ativo = false;
    }

    public static boolean ligado() {
        return ativo;
    }

    // ------------------------------------------------------------------ render
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!ativo || falhou) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        long agora = System.currentTimeMillis();
        if (agora > fim) {
            ativo = false;
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            Vec3 cam = event.getCamera().getPosition();
            if (pos.distanceToSqr(cam) > 64 * 64) {
                return;                       // longe demais: não gasta quadro
            }
            float u = (agora - inicio) / (float) Math.max(1L, fim - inicio);   // 0..1

            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            Font fonte = mc.font;

            pose.pushPose();
            pose.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            pose.mulPose(event.getCamera().rotation());
            // texto 2D -> mundo: escala pequena e Y invertido (fonte cresce pra baixo)
            pose.scale(-0.0225F, -0.0225F, 0.0225F);
            Matrix4f mat = pose.last().pose();

            double t = (agora - inicio) / 1000.0;

            // ---- (1) TRÊS ANÉIS EM VOLTA DO CORPO -------------------------
            int porAnel = GLIFOS / 3;
            for (int anel = 0; anel < 3; anel++) {
                float raio = 0.62F + anel * 0.30F;
                float vel = (anel % 2 == 0 ? 1.0F : -0.75F);
                for (int i = 0; i < porAnel; i++) {
                    int idx = anel * porAnel + i;
                    float s1 = rnd(idx * 37 + 11), s2 = rnd(idx * 53 + 7), s3 = rnd(idx * 91 + 3);
                    // cada glifo tem sua "vida": nasce, sobe e some
                    float ciclo = 1.35F + s3 * 0.8F;
                    float fase = ((float) t / ciclo + s1) % 1.0F;
                    float sobe = fase * 2.15F;                       // 0 .. 2,15
                    float ang = s2 * Mth.TWO_PI + (float) t * vel;
                    float r = raio * (1.0F - 0.25F * fase);          // fecha em cima
                    float px = Mth.cos(ang) * r;
                    float pz = Mth.sin(ang) * r;
                    float py = 0.12F + sobe;
                    // some perto do fim da vida (fade + encolhe)
                    float alfa = (fase < 0.15F ? fase / 0.15F : (fase > 0.72F ? (1F - fase) / 0.28F : 1F));
                    alfa *= (1F - u * 0.55F);
                    if (alfa <= 0.05F) {
                        continue;
                    }
                    float tam = 0.85F + s2 * 0.55F;
                    pose.pushPose();
                    pose.translate(px, py, pz);
                    pose.scale(tam, tam, tam);
                    glifo(fonte, buf, pose, texto(idx), cor(alfa, idx), 0);
                    pose.popPose();
                }
            }

            // ---- (2) A COLUNA DE SCAN (sobe pelo corpo o tempo todo) -------
            float scan = (float) ((t * 0.8) % 1.0);
            for (int i = 0; i < 7; i++) {
                float yy = 0.05F + (scan * 2.2F + i * 0.05F) % 2.2F;
                float largura = 0.55F;
                pose.pushPose();
                pose.translate(-largura + i * (largura * 2F / 6F), yy, 0.0F);
                glifo(fonte, buf, pose, i % 2 == 0 ? "▌" : "|", cor(0.75F * (1F - u * 0.4F), 3), 0);
                pose.popPose();
                pose.pushPose();
                pose.translate(0.0F, yy, -largura + i * (largura * 2F / 6F));
                glifo(fonte, buf, pose, "▌", cor(0.6F * (1F - u * 0.4F), 5), 0);
                pose.popPose();
            }

            // ---- (3) O NOME, flutuando acima da cabeça --------------------
            if (!nome.isEmpty() && u < 0.85F) {
                pose.pushPose();
                pose.translate(0.0F, 2.55F, 0.0F);
                pose.scale(1.6F, 1.6F, 1.6F);
                glifo(fonte, buf, pose, nome.toUpperCase(), cor(1F - u, 7), 0);
                pose.popPose();
            }

            pose.popPose();
            buf.endBatch();
        } catch (Throwable erro) {
            falhou = true;
            ativo = false;
            RPTagMod.LOGGER.error("RP Tag: glifos da saida desligados nesta sessao", erro);
        }
    }

    /** um texto qualquer, centrado no zero, na matriz já preparada. */
    private static void glifo(Font fonte, MultiBufferSource.BufferSource buf, PoseStack pose,
            String txt, int cor, int sombra) {
        Component c = Component.literal(txt);
        int larg = fonte.width(c);
        fonte.drawInBatch(c.getVisualOrderText(), -larg / 2.0F, 0.0F, cor, false,
                pose.last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
    }

    /** texto do glifo i (estável). */
    private static String texto(int i) {
        return CODIGOS[Math.abs(i * 7 + 3) % CODIGOS.length];
    }

    /** cor verde com alpha 0..1. */
    private static int cor(float alfa, int idx) {
        int a = (int) (Mth.clamp(alfa, 0F, 1F) * 235F) << 24;
        int base = (idx % 5 == 0) ? VERDE_CLARO : ((idx % 3 == 0) ? VERDE_ESCURO : VERDE);
        return a | base;
    }

    /** ruído estável (mesmo valor pro mesmo i) — o glifo não "ferve". */
    private static float rnd(int i) {
        float x = Mth.sin(i * 12.9898F) * 43758.5453F;
        return x - (float) Math.floor(x);
    }
}
