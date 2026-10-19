package dev.rptag.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.RPTagMod;
import dev.rptag.ZoneShowPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * (4.0.0) A "BARREIRA" da lorezone — SÓ NA TELA DO ADMIN que pediu.
 *
 * <p>Desenha um arame de esfera (meridianos + paralelos) e, em amarelo, as
 * duas "paredes" da faixa vertical (minY/maxY) — o pedaco onde o aviso
 * realmente dispara. Assim da pra ver se a zona cobre o evento (inclusive
 * quando o evento e numa caverna e nao pode disparar la em cima).
 *
 * <p>Nada e renderizado para players comuns: o servidor nunca manda o pacote.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class ZoneVisualizer {

    private ZoneVisualizer() {
    }

    private static boolean ativo = false;
    private static String zona = "";
    private static double cx, cy, cz, raio, minY, maxY;
    private static long expira = 0L;

    /** quantos segundos ficou ligado (0 = infinito ate desligar) */
    private static int segundos = 0;

    public static void aplicar(ZoneShowPayload p) {
        ativo = p.ligado();
        if (!ativo) {
            zona = "";
            return;
        }
        zona = p.zona();
        cx = p.cx();
        cy = p.cy();
        cz = p.cz();
        raio = p.raio();
        minY = p.minY();
        maxY = p.maxY();
        segundos = p.seg();
        expira = segundos <= 0 ? 0L : System.currentTimeMillis() + segundos * 1000L;
    }

    public static boolean ativo() {
        return ativo;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!ativo) {
            return;
        }
        if (expira > 0 && System.currentTimeMillis() > expira) {
            ativo = false;
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        // so desenha na dimensao certa? (o centro e no mundo todo — se estiver
        // longe, ainda vale como referencia; nada explode)
        PoseStack pose = event.getPoseStack();
        var cam = event.getCamera().getPosition();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        var normalPose = pose.last();

        // ---- esfera (ciano) ----
        float r = 0.55f, g = 0.90f, b = 1.0f, a = 0.75f;
        int meridianos = 12, passos = 48;
        // meridianos (de polo a polo)
        for (int md = 0; md < meridianos; md++) {
            double ang = Math.PI * md / meridianos;
            double dx = Math.cos(ang), dz = Math.sin(ang);
            float px = 0, py = 0, pz = 0;
            for (int i = 0; i <= passos; i++) {
                double t = Math.PI * i / passos;
                double x = cx + raio * Math.sin(t) * dx;
                double y = cy + raio * Math.cos(t);
                double z = cz + raio * Math.sin(t) * dz;
                if (i > 0) {
                    linha(vc, m, normalPose, px, py, pz, x, y, z, r, g, b, a);
                }
                px = (float) x; py = (float) y; pz = (float) z;
            }
        }
        // paralelos (anéis horizontais) — só dentro da faixa vertical
        for (int an = 1; an < 12; an++) {
            double yy = cy - raio + (2.0 * raio) * an / 12.0;
            if (yy < minY || yy > maxY) {
                continue;
            }
            double rr = Math.sqrt(Math.max(0, raio * raio - (yy - cy) * (yy - cy)));
            if (rr < 0.5) {
                continue;
            }
            float px = 0, py = 0, pz = 0;
            for (int i = 0; i <= passos; i++) {
                double t = 2 * Math.PI * i / passos;
                double x = cx + rr * Math.cos(t);
                double z = cz + rr * Math.sin(t);
                if (i > 0) {
                    linha(vc, m, normalPose, px, py, pz, x, yy, z, r, g, b, a);
                }
                px = (float) x; py = (float) yy; pz = (float) z;
            }
        }

        // ---- paredes da FAIXA VERTICAL (amarelo): onde o aviso dispara ----
        float yr = 1.0f, yg = 0.86f, yb = 0.25f, ya = 0.95f;
        for (double yy : new double[]{minY, maxY}) {
            double rr = Math.sqrt(Math.max(0, raio * raio - (yy - cy) * (yy - cy)));
            if (rr < 0.5) {
                rr = Math.max(1.0, raio * 0.15);
            }
            float px = 0, py = 0, pz = 0;
            for (int i = 0; i <= passos; i++) {
                double t = 2 * Math.PI * i / passos;
                double x = cx + rr * Math.cos(t);
                double z = cz + rr * Math.sin(t);
                if (i > 0) {
                    linha(vc, m, normalPose, px, py, pz, x, yy, z, yr, yg, yb, ya);
                }
                px = (float) x; py = (float) yy; pz = (float) z;
            }
        }
        // linha vertical de referencia no centro
        linha(vc, m, normalPose, cx, minY, cz, cx, maxY, cz, 1f, 0.55f, 0.15f, 0.9f);

        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void linha(VertexConsumer vc, Matrix4f m, PoseStack.Pose np,
            double x1, double y1, double z1, double x2, double y2, double z2,
            float r, float g, float b, float a) {
        float nx = (float) (x2 - x1), ny = (float) (y2 - y1), nz = (float) (z2 - z1);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-5f) {
            len = 1f;
        }
        nx /= len;
        ny /= len;
        nz /= len;
        vc.addVertex(m, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(np, nx, ny, nz);
        vc.addVertex(m, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a).setNormal(np, nx, ny, nz);
    }
}
