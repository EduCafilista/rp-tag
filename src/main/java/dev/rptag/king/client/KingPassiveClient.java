package dev.rptag.king.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.aura.client.AuraRenderTypes;
import dev.rptag.king.King;
import dev.rptag.king.KingAbilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * Lado de quem está perto do Rei (Presença do Rei). Só animações: símbolos amarelos flutuando, vultos que somem,
 * tela escurecendo/distorcendo. Faixas: 15 blocos = ilusões e sussurros, 8 = distorção, 4 = peso total.
 */
@EventBusSubscriber(modid = King.MODID, value = Dist.CLIENT)
public final class KingPassiveClient {
    static final double FAR = 15, MID = 8, NEAR = 4;
    private static float level, smooth;       // 0..1: proximidade ao Rei mais próximo
    private static double dist = 99;
    private static int confuse;

    static void confuse(int ticks) { confuse = Math.max(confuse, ticks); }
    static float level() { return smooth; }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (confuse > 0) confuse--;
        if (me == null || mc.level == null) { smooth = 0; return; }
        double best = 99;
        if (!KingAbilities.isKing(me)) {
            for (AbstractClientPlayer p : mc.level.players()) {
                if (p == me || !KingAbilities.isKing(p) || p.isSpectator()) continue;
                best = Math.min(best, p.distanceTo(me));
            }
        }
        dist = best;
        level = best >= FAR ? 0f : (float) (1.0 - best / FAR);
        smooth += (level - smooth) * 0.12f;
    }

    // ------------------------------------------------------------------ HUD (tela)
    public static void hud(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        float f = smooth, cf = Mth.clamp(confuse / 40f, 0f, 1f);
        float strength = Math.max(dist <= MID ? Mth.clamp((float) ((MID - dist) / MID) + 0.35f, 0f, 1f) : f * 0.25f, cf);
        if (strength < 0.02f) return;
        int w = g.guiWidth(), h = g.guiHeight();
        float t = (mc.level == null ? 0 : mc.level.getGameTime()) + pt;
        int a = (int) (strength * 46 * (0.75f + 0.25f * Mth.sin(t * 0.15f)));
        g.fill(0, 0, w, h, (a << 24) | 0x8A6A10);                                          // véu sépia amarelado
        int e = (int) (strength * 150), bw = (int) (Math.min(w, h) * 0.22f);
        g.fillGradient(0, 0, w, bw, e << 24, 0);                                           // bordas escuras
        g.fillGradient(0, h - bw, w, h, 0, e << 24);
        g.fillGradient(0, 0, bw, h, e << 24, 0);
        g.fillGradient(w - bw, 0, w, h, 0, e << 24);
        long slot = (long) (t / 3f);                                                       // faixas de "glitch" amarelas
        int bars = strength > 0.5f ? 4 : 2;
        for (int i = 0; i < bars; i++) {
            float r1 = KingGfx.hash((int) (slot * 7 + i * 13)), r2 = KingGfx.hash((int) (slot * 11 + i * 5));
            if (r1 < 0.45f) continue;
            int y = (int) (r2 * h), bh = 1 + (int) (r1 * 5);
            g.fill(0, y, w, y + bh, ((int) (strength * 40) << 24) | 0xFFD020);
        }
        String set = "ΨΩΞΦΛΣ≋∴∵";                                                           // letras amarelas à deriva
        int n = strength > 0.45f ? 5 : 3;
        for (int i = 0; i < n; i++) {
            float life = ((t * 0.012f) + KingGfx.hash(i * 19)) % 1f;
            float al = (float) Math.sin(Math.PI * life) * strength;
            int x = (int) (KingGfx.hash(i * 7 + (int) (t * 0.012f + KingGfx.hash(i * 19)) * 31) * w);
            int y = (int) (h * (0.9f - life * 0.7f));
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(2.2f, 2.2f, 1f);
            g.drawString(mc.font, String.valueOf(set.charAt((i * 3 + (int) (t * 0.012f)) % set.length())), 0, 0, ((int) (al * 150) << 24) | 0xFFD020, false);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ câmera: leve oscilação
    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        float cf = Mth.clamp(confuse / 40f, 0f, 1f);
        float k = Math.max(dist <= MID ? (float) ((MID - dist) / MID) * 0.6f : 0f, cf);
        if (k <= 0.01f) return;
        float t = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + (float) e.getPartialTick();
        e.setRoll(e.getRoll() + Mth.sin(t * 0.07f) * 2.2f * k + Mth.sin(t * 0.31f) * 0.6f * k);
    }

    // ------------------------------------------------------------------ mundo: símbolos flutuantes e vultos ilusórios
    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (dev.rptag.client.FxQualidade.desligado()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float f = smooth, cf = Mth.clamp(confuse / 40f, 0f, 1f);
        float k = Math.max(f, cf);
        if (k < 0.04f) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float t = mc.player.tickCount + pt;
        Matrix4f m = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        Vec3 me = mc.player.position();

        int vultos = dev.rptag.client.FxQualidade.minimoOuMenos() ? 0 : dev.rptag.client.FxQualidade.escala(3, 1);
        if (vultos > 0) {
            VertexConsumer ink = bs.getBuffer(AuraRenderTypes.INK);                           // vultos escuros
            int slotLen = 90;
            for (int i = 0; i < vultos; i++) {
                long slot = (long) (t / slotLen) + i * 101L;
                float u = (t % slotLen) / slotLen;
                if (KingGfx.hash((int) slot * 3 + i) > 0.35f + 0.5f * k) continue;
                float ang = KingGfx.hash((int) slot * 5 + 1) * 6.283f, rad = 7f + KingGfx.hash((int) slot * 9 + 2) * 8f;
                double x = me.x + Math.cos(ang) * rad, z = me.z + Math.sin(ang) * rad;
                Vec3 p = new Vec3(x, KingGfx.gy(mc.level, x, z, me.y) - 0.04, z);
                float al = (float) Math.sin(Math.PI * u) * Math.min(1f, k * 1.6f);
                figure(ink, m, cam, p, al);
            }
            bs.endBatch(AuraRenderTypes.INK);
        }

        int qtdGlifos = dev.rptag.client.FxQualidade.escala(9, 3);
        VertexConsumer vc = bs.getBuffer(RenderType.lightning());
        for (int i = 0; i < qtdGlifos; i++) {                                                 // letras/símbolos amarelos no ar
            float life = (t * 0.016f + KingGfx.hash(i * 23)) % 1f;
            int gen = (int) (t * 0.016f + KingGfx.hash(i * 23));
            float ang = KingGfx.hash(i * 3 + gen * 17) * 6.283f, rad = 2.5f + KingGfx.hash(i * 5 + gen * 29) * 5f;
            Vec3 p = me.add(Math.cos(ang) * rad, 0.6 + life * 3.0 + KingGfx.hash(i + gen) * 1.5, Math.sin(ang) * rad);
            float al = (float) Math.sin(Math.PI * life) * k;
            KingGfx.glyph(vc, m, cam, i + gen, p, KingGfx.facing(cam, p), 0.7f, Mth.sin(t * 0.05f + i) * 0.3f, 0.05f, KingGfx.GOLD, 0.85f * al);
        }
        bs.endBatch(RenderType.lightning());
    }

    /** Silhueta encapuzada em pé (chapa voltada para a câmera), preta com borda amarela só no contorno. */
    private static void figure(VertexConsumer ink, Matrix4f m, Vec3 cam, Vec3 p, float a) {
        if (a <= 0.01f) return;
        Vec3[] b = KingGfx.facing(cam, p.add(0, 1, 0));
        Vec3 r = b[0];
        float[] blk = {0.01f, 0.008f, 0.0f, 1f};
        Vec3 bl = p.subtract(r.scale(0.55)), br = p.add(r.scale(0.55));
        Vec3 sl = p.add(0, 1.45, 0).subtract(r.scale(0.30)), sr = p.add(0, 1.45, 0).add(r.scale(0.30));
        KingGfx.v(ink, m, cam, bl, blk, a * 0.85f); KingGfx.v(ink, m, cam, br, blk, a * 0.85f);
        KingGfx.v(ink, m, cam, sr, blk, a * 0.85f); KingGfx.v(ink, m, cam, sl, blk, a * 0.85f);
        Vec3 hc = p.add(0, 1.75, 0);
        Vec3 h0 = hc.subtract(r.scale(0.2)).add(0, -0.3, 0), h1 = hc.add(r.scale(0.2)).add(0, -0.3, 0);
        Vec3 h2 = hc.add(r.scale(0.14)).add(0, 0.25, 0), h3 = hc.subtract(r.scale(0.14)).add(0, 0.25, 0);
        KingGfx.v(ink, m, cam, h0, blk, a * 0.85f); KingGfx.v(ink, m, cam, h1, blk, a * 0.85f);
        KingGfx.v(ink, m, cam, h2, blk, a * 0.85f); KingGfx.v(ink, m, cam, h3, blk, a * 0.85f);
    }

    private KingPassiveClient() {}
}
