package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.RPTagMod;
import dev.rptag.VazioReino;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * (5.28.0) <b>O VAZIO NÃO TEM CÉU. NEM SOL, NEM LUA, NEM ESTRELAS. SÓ VAZIO.</b>
 *
 * <p>Pedido do dono: <i>"o vazio nao tem que ter um contato com ceu nem nada, apenas o vazio,
 * sem lua, sem sol, nada, vazio"</i>.
 *
 * <p>Do lado do servidor a dimensão já nasce com {@code has_skylight: false} (ver
 * {@code data/rptag/dimension_type/exilio.json}) — ou seja, não entra luz do alto e o relógio
 * não manda no céu. Só isso, porém, deixaria o céu do Nether (uma parede vermelho-acinzentada),
 * porque o efeito de dimensão escolhido é o do Nether (o único do jogo que não desenha sol nem
 * lua). Aqui o cliente FECHA a caixa: um cubo preto desenhado logo depois do céu, grande o
 * bastante para cobrir tudo o que está longe, e pequeno o bastante para não encostar no terreno.
 *
 * <p>Regra de ouro do mod: erro aqui escurece o céu, nunca o jogo.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class VazioCeuPreto {

    private VazioCeuPreto() {
    }

    /** lado do cubo (blocos) — maior que qualquer horizonte, menor que o "infinito" do céu. */
    private static final float RAIO = 512.0F;

    /** preto quase absoluto: deixa um fio de azul-noite pra não virar um buraco chapado. */
    private static final int COR = 0xFF05060A;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || !VazioReino.ehVazio(mc.level)) {
                return;
            }
            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());

            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            var mat = pose.last().pose();
            float r = RAIO;
            // cinco faces (o chão não precisa: o terreno cobre) — viradas pra DENTRO
            face(vc, mat, -r, -r, -r, r, -r, -r, r, r, -r, -r, r, -r);   // norte
            face(vc, mat, r, -r, r, -r, -r, r, -r, r, r, r, r, r);       // sul
            face(vc, mat, -r, -r, r, -r, -r, -r, -r, r, -r, -r, r, r);   // oeste
            face(vc, mat, r, -r, -r, r, -r, r, r, r, r, r, r, -r);       // leste
            face(vc, mat, -r, r, -r, r, r, -r, r, r, r, -r, r, r);       // teto
            pose.popPose();
            buf.endBatch(RenderType.debugQuads());
        } catch (Throwable ignorado) {
            // enfeite: se falhar, o céu do Nether continua (feio, mas o jogo segue)
        }
    }

    private static void face(VertexConsumer vc, org.joml.Matrix4f m,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3) {
        vc.addVertex(m, x0, y0, z0).setColor(COR);
        vc.addVertex(m, x1, y1, z1).setColor(COR);
        vc.addVertex(m, x2, y2, z2).setColor(COR);
        vc.addVertex(m, x3, y3, z3).setColor(COR);
    }
}
