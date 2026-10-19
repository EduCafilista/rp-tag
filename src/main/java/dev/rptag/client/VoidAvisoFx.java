package dev.rptag.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;

/**
 * AVISO VOID — "Queda Sem Fundo" (~6 s)
 *  - onda de choque lavanda abre a tela; o centro vira um BURACO NEGRO com anel de acreção inclinado
 *  - túnel infinito de anéis ondulantes (você cai para dentro) com cores do fundo do End
 *  - estrelas esticadas em listras sugadas para o centro
 *  - estilhaços escuros da "realidade" giram à deriva rumo ao buraco
 *  - o título flutua em órbita lenta, com aberração cromática, e tem REFLEXO invertido embaixo
 *
 * USO (adaptado ao RP Tag, 3.96.0 — VISUAL IDÊNTICO ao protótipo):
 *   camada:     VoidAvisoFx.registrar(e);   (ClientGuiLayers)
 *   disparo:    VoidAvisoFx.iniciar("AVISO", mensagem)
 *               (ClientEffects.startScreenText, estilo 8 void — título
 *                fixo "AVISO", mensagem configurável no subtítulo)
 *   servidor:   particulas de mundo via RPCommands.particulasVoid
 *               (server-safe, sem classes client)
 */
public final class VoidAvisoFx {
    private static final float DUR = 6.0f, TAU = 6.2831853f;
    private static final int VIO = 0x7828C8, CYAN = 0x78E6FF, LAV = 0xDCD2FF, NIGHT = 0x06000F, DEEPB = 0x140A40, WHITE = 0xFFFFFF;
    private static final float[] RES = {1.5f, 2.7f, 3.9f};

    /** true = menos anéis/estrelas. */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String titulo = "AVISO", sub = "";
    private static int resIdx;
    private static boolean fimTocado;

    private VoidAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_void"), VoidAvisoFx::render);
    }

    public static void iniciar(String t, String s) {
        titulo = (t == null || t.isEmpty()) ? "AVISO" : t;
        sub = s == null ? "" : s;
        inicio = System.nanoTime();
        resIdx = 0;
        fimTocado = false;
        tocar(SoundEvents.END_PORTAL_SPAWN, 0.6f, 0.6f);
    }

    /** SERVIDOR: partículas de portal sendo sugadas ao redor do jogador. */
    public static void particulasMundo(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        lv.sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 120, 1.5, 1.5, 1.5, 0.8);
        lv.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.8, 0.6, 0.05);
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t >= DUR) { inicio = -1; return; }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h * 0.45f, k = Math.min(w, h) / 240f, diag = (float) Math.hypot(w, h);
        float env = ss(0, 0.5f, t) * (1 - ss(DUR - 1.2f, DUR, t));
        float rev = eo(cl((t - 0.1f) / 1.2f));
        float Rmax = diag * 0.6f, Rh = 24 * k * rev;
        sons(t);

        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = g.pose().last().pose();

        // 1) escuridão, vinheta, estilhaços e o buraco (blend normal)
        RenderSystem.defaultBlendFunc();
        Buf b = new Buf(m);
        b.quad(0, 0, col(NIGHT, 0.55f * env), w, 0, col(NIGHT, 0.55f * env), w, h, col(NIGHT, 0.55f * env), 0, h, col(NIGHT, 0.55f * env));
        float d = Math.min(w, h) * 0.42f, va = 0.92f * env;
        b.quad(0, 0, col(NIGHT, va), w, 0, col(NIGHT, va), w, d, col(NIGHT, 0), 0, d, col(NIGHT, 0));
        b.quad(0, h, col(NIGHT, va), w, h, col(NIGHT, va), w, h - d, col(NIGHT, 0), 0, h - d, col(NIGHT, 0));
        b.quad(0, 0, col(NIGHT, va), 0, h, col(NIGHT, va), d, h, col(NIGHT, 0), d, 0, col(NIGHT, 0));
        b.quad(w, 0, col(NIGHT, va), w, h, col(NIGHT, va), w - d, h, col(NIGHT, 0), w - d, 0, col(NIGHT, 0));
        int NSH = reduzido ? 6 : 12;
        for (int i = 0; i < NSH; i++) {
            float[] s = estilhaco(i, t, cx, cy, Rmax, k);
            int c = col(0x080014, 0.9f * env * s[6]);
            b.tri(s[0], s[1], c, s[2], s[3], c, s[4], s[5], c);
        }
        buraco(b, cx, cy, Rh, Rh * 1.3f, 0.98f * env);
        b.flush();

        // 2) tudo que brilha (aditivo)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        b = new Buf(m);

        if (t < 0.55f) {                                              // onda de choque
            float u = eo(t / 0.55f);
            anel(b, cx, cy, diag * 0.55f * u, 96, 6 * k * (1 - u) + 1, 0, LAV, (1 - u) * 0.8f);
        }
        int NR = reduzido ? 10 : 16;                                  // túnel de anéis
        for (int i = 0; i < NR; i++) {
            float u = fr((float) i / NR - t * 0.16f);
            float r = Rmax * (float) Math.pow(u, 2.2) + Rh * 1.3f, a = sin(3.1416f * u) * 0.45f * env;
            if (a < 0.01f) continue;
            float wd = (0.4f + 2.6f * u) * k;
            int c = mix(CYAN, VIO, u);
            for (int j = 0; j < 64; j++) {
                float a0 = j * TAU / 64, a1 = (j + 1) * TAU / 64;
                float r0 = r * (1 + 0.03f * sin(3 * a0 + t * 1.7f + i)), r1 = r * (1 + 0.03f * sin(3 * a1 + t * 1.7f + i));
                b.seg(cx + cos(a0) * r0, cy + sin(a0) * r0, cx + cos(a1) * r1, cy + sin(a1) * r1, wd, col(c, a), col(c, a));
            }
        }
        int NS = reduzido ? 45 : 90;                                  // estrelas sugadas
        for (int i = 0; i < NS; i++) {
            float an = rnd(i, 1) * TAU, rho = fr(rnd(i, 2) - t * (0.10f + 0.2f * rnd(i, 3)));
            float dist = Rmax * (float) Math.pow(rho, 1.6) + Rh * 1.2f, d2 = dist * (1.12f + 0.25f * rho);
            float al = ss(0, 0.15f, rho) * (1 - ss(0.85f, 1, rho)) * env * 0.9f;
            int c = (i % 3 == 0) ? CYAN : (i % 3 == 1 ? LAV : VIO);
            b.seg(cx + cos(an) * dist, cy + sin(an) * dist, cx + cos(an) * d2, cy + sin(an) * d2, (0.6f + 0.8f * rnd(i, 4)) * k * 0.7f, col(c, al), col(c, 0));
        }
        for (int i = 0; i < NSH; i++) {                               // contorno violeta dos estilhaços
            float[] s = estilhaco(i, t, cx, cy, Rmax, k);
            int c = col(VIO, 0.6f * env * s[6]);
            b.seg(s[0], s[1], s[2], s[3], 1.0f, c, c); b.seg(s[2], s[3], s[4], s[5], 1.0f, c, c); b.seg(s[4], s[5], s[0], s[1], 1.0f, c, c);
        }
        anel(b, cx, cy, Rh * 1.05f, 90, 1.6f * k * 0.7f, 0, LAV, 0.95f * env);   // horizonte de eventos
        anel(b, cx, cy, Rh * 1.2f, 90, 1.0f * k * 0.6f, 0, CYAN, 0.5f * env);
        anel(b, cx, cy, Rh * 1.4f, 90, 1.0f * k * 0.5f, 0, VIO, 0.3f * env);
        float rot = -0.3f + 0.05f * sin(t);                           // disco de acreção inclinado
        anelEl(b, cx, cy, Rh * 2.6f, Rh * 0.55f, rot, 96, 1.6f * k * 0.6f, LAV, 0.75f * env);
        anelEl(b, cx, cy, Rh * 3.4f, Rh * 0.80f, rot, 96, 1.2f * k * 0.5f, CYAN, 0.35f * env);
        b.flush();

        // 3) textos (normal)
        RenderSystem.defaultBlendFunc();
        Font f = mc.font;
        float S = k * 1.9f, ty = cy - 4 * S;
        int len = titulo.length();
        float sp = lerp(30f, 5f, rev), tot = 0;
        for (int i = 0; i < len; i++) tot += f.width(titulo.substring(i, i + 1)) + (i < len - 1 ? sp : 0);
        float x0 = cx - tot * S / 2, acc = 0;
        for (int i = 0; i < len; i++) {
            String ch = titulo.substring(i, i + 1);
            float cw = f.width(ch), la = cl((t - 0.6f - 0.1f * i) / 0.5f);
            int a8 = (int) (la * env * 255);
            float bob = sin(t * 1.3f + i * 0.9f) * 3 * k;
            if (a8 >= 8) {
                float px = x0 + acc * S;
                g.pose().pushPose();
                g.pose().translate(px, ty + bob, 0);
                g.pose().scale(S, S, 1);
                int glow = ((int) (a8 * 0.35f) << 24) | 0x7828C8;
                g.drawString(f, ch, -1, 0, glow, false); g.drawString(f, ch, 1, 0, glow, false);
                g.drawString(f, ch, 0, -1, glow, false); g.drawString(f, ch, 0, 1, glow, false);
                g.drawString(f, ch, -2, 0, ((int) (a8 * 0.35f) << 24) | 0x78E6FF, false);
                g.drawString(f, ch, 0, 0, (a8 << 24) | LAV, false);
                g.pose().popPose();
                g.pose().pushPose();                                  // reflexo invertido
                g.pose().translate(px, ty + 15 * S - bob, 0);
                g.pose().scale(S, -S, 1);
                g.drawString(f, ch, 0, 0, ((int) (a8 * 0.22f) << 24) | LAV, false);
                g.pose().popPose();
            }
            acc += cw + sp;
        }
        int sa = (int) (ss(1.0f, 1.6f, t) * env * 255);
        if (!sub.isEmpty() && sa >= 8) {
            float S2 = Math.min(k * 0.75f, w * 0.85f / Math.max(1, f.width(sub)));
            g.pose().pushPose();
            g.pose().translate(cx - f.width(sub) * S2 / 2, cy + 25 * k, 0);
            g.pose().scale(S2, S2, 1);
            g.drawString(f, sub, 0, 0, (sa << 24) | 0xB4A0FF, true);
            g.pose().popPose();
        }
        RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ peças
    /** retorna {x1,y1,x2,y2,x3,y3,fadeAlpha} do estilhaço i. */
    private static float[] estilhaco(int i, float t, float cx, float cy, float Rmax, float k) {
        float rho = fr(rnd(i, 81) - t * 0.04f), dist = Rmax * (0.45f + 0.55f * rho);
        float a0 = rnd(i, 82) * TAU + t * (0.1f + 0.2f * (rnd(i, 83) - 0.5f));
        float sz = (14 + 26 * rnd(i, 84)) * k, r0 = rnd(i, 85) * TAU + t * (rnd(i, 86) - 0.5f) * 1.2f;
        float px = cx + cos(a0) * dist, py = cy + sin(a0) * dist;
        float[] o = new float[7];
        for (int v = 0; v < 3; v++) {
            float an = r0 + v * TAU / 3 + (rnd(i, 87 + v) - 0.5f) * 0.8f, rd = sz * (0.6f + 0.6f * rnd(i, 90 + v));
            o[v * 2] = px + cos(an) * rd; o[v * 2 + 1] = py + sin(an) * rd;
        }
        o[6] = ss(0, 0.1f, rho) * (1 - ss(0.9f, 1, rho));
        return o;
    }

    private static void buraco(Buf b, float cx, float cy, float rIn, float rOut, float a) {
        for (int i = 0; i < 48; i++) {
            float a0 = i * TAU / 48, a1 = (i + 1) * TAU / 48;
            b.tri(cx, cy, col(0x000000, a), cx + cos(a0) * rIn, cy + sin(a0) * rIn, col(0x000000, a), cx + cos(a1) * rIn, cy + sin(a1) * rIn, col(0x000000, a));
            b.quad(cx + cos(a0) * rIn, cy + sin(a0) * rIn, col(0x000000, a), cx + cos(a1) * rIn, cy + sin(a1) * rIn, col(0x000000, a),
                    cx + cos(a1) * rOut, cy + sin(a1) * rOut, col(0x000000, 0), cx + cos(a0) * rOut, cy + sin(a0) * rOut, col(0x000000, 0));
        }
    }

    private static void anel(Buf b, float cx, float cy, float r, int n, float w, float rot, int rgb, float a) {
        for (int i = 0; i < n; i++) {
            float a0 = rot + i * TAU / n, a1 = rot + (i + 1) * TAU / n;
            b.seg(cx + cos(a0) * r, cy + sin(a0) * r, cx + cos(a1) * r, cy + sin(a1) * r, w, col(rgb, a), col(rgb, a));
        }
    }

    private static void anelEl(Buf b, float cx, float cy, float rx, float ry, float rot, int n, float w, int rgb, float a) {
        float cr = cos(rot), sr = sin(rot);
        for (int i = 0; i < n; i++) {
            float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
            float x0 = cos(a0) * rx, y0 = sin(a0) * ry, x1 = cos(a1) * rx, y1 = sin(a1) * ry;
            b.seg(cx + x0 * cr - y0 * sr, cy + x0 * sr + y0 * cr, cx + x1 * cr - y1 * sr, cy + x1 * sr + y1 * cr, w, col(rgb, a), col(rgb, a));
        }
    }

    private static void sons(float t) {
        while (resIdx < RES.length && t >= RES[resIdx]) { tocar(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f - 0.05f * resIdx, 0.9f); resIdx++; }
        if (!fimTocado && t >= DUR - 0.9f) { fimTocado = true; tocar(SoundEvents.ENDERMAN_TELEPORT, 0.5f, 0.8f); }
    }

    private static void tocar(SoundEvent ev, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, vol));
    }

    // ------------------------------------------------------------------ util
    private static final class Buf {
        final Matrix4f m; BufferBuilder bb; int n;
        Buf(Matrix4f m) { this.m = m; }
        void v(float x, float y, int c) {
            if (bb == null) bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            bb.addVertex(m, x, y, 0).setColor(c); n++;
        }
        void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) { v(x1, y1, c1); v(x2, y2, c2); v(x3, y3, c3); }
        void quad(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3, float x4, float y4, int c4) {
            tri(x1, y1, c1, x2, y2, c2, x3, y3, c3); tri(x1, y1, c1, x3, y3, c3, x4, y4, c4);
        }
        void seg(float ax, float ay, float bx, float by, float w, int ca, int cb) {
            float dx = by - ay, dy = ax - bx, l = (float) Math.hypot(dx, dy);
            if (l < 1e-4f) return;
            dx = dx / l * w / 2; dy = dy / l * w / 2;
            quad(ax + dx, ay + dy, ca, ax - dx, ay - dy, ca, bx - dx, by - dy, cb, bx + dx, by + dy, cb);
        }
        void flush() { if (n > 0) BufferUploader.drawWithShader(bb.buildOrThrow()); }
    }

    private static int mix(int c1, int c2, float u) {
        int r = (int) lerp((c1 >> 16) & 255, (c2 >> 16) & 255, u), g = (int) lerp((c1 >> 8) & 255, (c2 >> 8) & 255, u), b = (int) lerp(c1 & 255, c2 & 255, u);
        return (r << 16) | (g << 8) | b;
    }
    private static int col(int rgb, float a) { return ((int) (cl(a) * 255) << 24) | (rgb & 0xFFFFFF); }
    private static float cl(float x) { return x < 0 ? 0 : Math.min(x, 1); }
    private static float ss(float a, float b, float x) { float u = cl((x - a) / (b - a)); return u * u * (3 - 2 * u); }
    private static float eo(float x) { return 1 - (1 - x) * (1 - x) * (1 - x); }
    private static float lerp(float a, float b, float u) { return a + (b - a) * u; }
    private static float fr(float x) { return x - (float) Math.floor(x); }
    private static float rnd(int i, int s) { return fr((float) Math.sin(i * 12.9898 + s * 78.233) * 43758.547f); }
    private static float sin(float x) { return (float) Math.sin(x); }
    private static float cos(float x) { return (float) Math.cos(x); }
}
