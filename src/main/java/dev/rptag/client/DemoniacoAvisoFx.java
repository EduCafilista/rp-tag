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
 * AVISO DEMONÍACO — "Chamado do Abismo" (~6 s)
 *  - a tela escurece em vinho e PULSA no ritmo de um coração (som e visual sincronizados)
 *  - dentes/garras negros fecham das bordas de cima e de baixo, como mandíbulas
 *  - fissuras de lava racham a tela a partir do centro
 *  - pentagrama invertido rúnico gira em brasa; dois chifres incandescentes o coroam
 *  - o texto sobe das trevas, treme e "glitcha" com sombras vermelha/laranja
 *  - um olho de fenda se abre sob o título; brasas sobem o tempo todo
 *
 * USO (adaptado ao RP Tag, 3.94.0 — VISUAL IDÊNTICO ao protótipo):
 *   camada:     DemoniacoAvisoFx.registrar(e);   (ClientGuiLayers)
 *   disparo:    DemoniacoAvisoFx.iniciar("AVISO", mensagem)
 *               (ClientEffects.startScreenText, estilo 10 demoniaco —
 *                título fixo "AVISO", mensagem configurável no subtítulo)
 *   servidor:   particulas de mundo via RPCommands.particulasAbismo
 *               (server-safe, sem classes client)
 */
public final class DemoniacoAvisoFx {
    private static final float DUR = 6.0f, DEG = (float) Math.PI / 180f, TAU = 6.2831853f;
    private static final int BLOOD = 0xC81414, EMBER = 0xFF6E1E, HOT = 0xFFBE50, WHITE = 0xFFFFFF, VOID = 0x0A0000, WINE = 0x500000;

    /** true = sem clarão e menos partículas (fotossensibilidade). */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String titulo = "AVISO", sub = "";
    private static int beatIdx;
    private static boolean fimTocado;

    private DemoniacoAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_demoniaco"), DemoniacoAvisoFx::render);
    }

    public static void iniciar(String t, String s) {
        titulo = (t == null || t.isEmpty()) ? "AVISO" : t;
        sub = s == null ? "" : s;
        inicio = System.nanoTime();
        beatIdx = 0;
        fimTocado = false;
        tocar(SoundEvents.ENDER_DRAGON_GROWL, 0.6f, 0.6f);
    }

    /** SERVIDOR: pentagrama de chamas almas no chão + coluna de fumaça e respingos de lava. */
    public static void particulasMundo(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY() + 0.1, z = p.getZ(), R = 3.2;
        for (int i = 0; i < 5; i++) {                       // pentagrama (cordas i -> i+2)
            double a0 = i * 72 * Math.PI / 180, a1 = ((i + 2) % 5) * 72 * Math.PI / 180;
            for (int s = 0; s <= 14; s++) {
                double u = s / 14.0;
                double px = x + R * (Math.cos(a0) * (1 - u) + Math.cos(a1) * u);
                double pz = z + R * (Math.sin(a0) * (1 - u) + Math.sin(a1) * u);
                lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, y, pz, 0, 0, 0.04, 0, 1.0);
            }
        }
        for (int i = 0; i < 40; i++) {                      // círculo
            double a = i * Math.PI * 2 / 40;
            lv.sendParticles(ParticleTypes.FLAME, x + Math.cos(a) * R, y, z + Math.sin(a) * R, 0, 0, 0.03, 0, 1.0);
        }
        for (int i = 0; i < 30; i++)
            lv.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + i * 0.15, z, 2, 0.4, 0.05, 0.4, 0.01);
        lv.sendParticles(ParticleTypes.LAVA, x, y + 1, z, 14, 1.2, 0.3, 1.2, 0);
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t >= DUR) { inicio = -1; return; }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h * 0.45f, k = Math.min(w, h) / 240f;
        float env = ss(0, 0.5f, t) * (1 - ss(DUR - 1.2f, DUR, t));
        float rev = eo(cl((t - 0.1f) / 1.1f));
        float beat = beat(t);
        sons(t);

        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = g.pose().last().pose();
        float R = 62 * k * rev * (1 + 0.03f * beat);

        // 1) escuridão, vinheta, disco atrás do texto, dentes (blend normal)
        RenderSystem.defaultBlendFunc();
        Buf b = new Buf(m);
        float tint = (0.30f + 0.15f * beat) * env;
        b.quad(0, 0, col(WINE, tint), w, 0, col(WINE, tint), w, h, col(WINE, tint), 0, h, col(WINE, tint));
        float d = Math.min(w, h) * 0.40f, va = 0.80f * env;
        b.quad(0, 0, col(VOID, va), w, 0, col(VOID, va), w, d, col(VOID, 0), 0, d, col(VOID, 0));
        b.quad(0, h, col(VOID, va), w, h, col(VOID, va), w, h - d, col(VOID, 0), 0, h - d, col(VOID, 0));
        b.quad(0, 0, col(VOID, va), 0, h, col(VOID, va), d, h, col(VOID, 0), d, 0, col(VOID, 0));
        b.quad(w, 0, col(VOID, va), w, h, col(VOID, va), w - d, h, col(VOID, 0), w - d, 0, col(VOID, 0));
        disco(b, cx, cy, R * 1.25f, 0.45f * env, VOID);
        int N = 14;
        float sw = w / (float) N;
        for (int side = 0; side < 2; side++) {                        // mandíbulas
            for (int i = 0; i < N; i++) {
                float x0 = i * sw, x1 = x0 + sw, tx = x0 + sw * (0.5f + 0.25f * (rnd(i, 5 + side) - 0.5f));
                float hg = (18 + 30 * rnd(i, 6 + side)) * k * rev * (1 + 0.06f * beat);
                float y0 = side == 0 ? 0 : h, ty = side == 0 ? hg : h - hg;
                b.tri(x0, y0, col(VOID, 0.95f * env), x1, y0, col(VOID, 0.95f * env), tx, ty, col(VOID, 0.95f * env));
            }
        }
        b.flush();

        // 2) brilho (blend aditivo)
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        b = new Buf(m);

        if (!reduzido && t < 0.25f) {
            float fa = (1 - t / 0.25f) * 0.35f;
            b.quad(0, 0, col(BLOOD, fa), w, 0, col(BLOOD, fa), w, h, col(BLOOD, fa), 0, h, col(BLOOD, fa));
        }
        for (int side = 0; side < 2; side++) {                        // contorno em brasa dos dentes
            for (int i = 0; i < N; i++) {
                float x0 = i * sw, x1 = x0 + sw, tx = x0 + sw * (0.5f + 0.25f * (rnd(i, 5 + side) - 0.5f));
                float hg = (18 + 30 * rnd(i, 6 + side)) * k * rev * (1 + 0.06f * beat);
                float y0 = side == 0 ? 0 : h, ty = side == 0 ? hg : h - hg;
                b.seg(x0, y0, tx, ty, 1.2f, col(BLOOD, 0.2f * env), col(EMBER, 0.8f * env));
                b.seg(x1, y0, tx, ty, 1.2f, col(BLOOD, 0.2f * env), col(EMBER, 0.8f * env));
            }
        }

        fissuras(b, cx, cy, (float) Math.hypot(w, h) * 0.6f * rev, t, env, k, reduzido);
        disco(b, cx, cy, R * 1.15f, (0.40f + 0.25f * beat) * env, BLOOD);

        float rot = t * 6 * DEG;                                      // pentagrama invertido
        anel(b, cx, cy, R, 90, 1.8f, 0, 0, BLOOD, 0.95f * env);
        anel(b, cx, cy, R * 1.12f, 90, 1.0f, 0, 0, EMBER, 0.60f * env);
        anel(b, cx, cy, R * 1.30f, 96, 1.2f, -t * 5 * DEG, 5, BLOOD, 0.75f * env);
        for (int i = 0; i < 5; i++) {
            float a0 = rot + (90 + 72 * i) * DEG, a1 = rot + (90 + 72 * ((i + 2) % 5)) * DEG;
            b.seg(cx + cos(a0) * R, cy + sin(a0) * R, cx + cos(a1) * R, cy + sin(a1) * R, 1.8f,
                    col(EMBER, (0.75f + 0.25f * beat) * env), col(EMBER, (0.75f + 0.25f * beat) * env));
        }
        for (int i = 0; i < 60; i++) {                                // runas
            float a = -t * 8 * DEG + i * TAU / 60, r0 = R * 1.34f, r2 = r0 + (3 + 7 * rnd(i, 11)) * k;
            b.seg(cx + cos(a) * r0, cy + sin(a) * r0, cx + cos(a) * r2, cy + sin(a) * r2, 1.2f, col(BLOOD, 0.8f * env), col(EMBER, 0.2f * env));
        }

        float hg = ss(0.3f, 1.0f, t);                                 // chifres
        chifre(b, cx - R * 0.5f, cy - R * 0.9f, -1, R, hg, 0.65f * env);
        chifre(b, cx + R * 0.5f, cy - R * 0.9f, 1, R, hg, 0.65f * env);

        int n = reduzido ? 30 : 70;                                   // brasas
        for (int i = 0; i < n; i++) {
            float p = fr(t * (0.10f + 0.16f * rnd(i, 2)) + rnd(i, 3));
            float px = rnd(i, 1) * w + sin(t * 2 + i) * 10, py = h * (1.05f - p * 1.1f);
            float sz = (1 + 2 * rnd(i, 4)) * k * 0.8f;
            float al = env * (0.5f + 0.5f * sin(t * 9 + i * 3.1f)) * sin(3.1416f * p);
            b.diamante(px, py, sz * 0.5f, sz * 1.2f, col((i & 1) == 0 ? EMBER : HOT, al));
        }

        float S = k * 1.9f, ty = cy - 4 * S;
        float oy = cy + 5.5f * S + 6 * k, ol = 70 * k * rev, oa = 0.9f * env * ss(0.8f, 1.4f, t);
        for (int s = -1; s <= 1; s += 2) {                            // ornamento com espinhos + olho
            b.seg(cx + s * 12 * k, oy, cx + s * ol, oy, 1.2f, col(EMBER, oa), col(BLOOD, 0));
            for (int mm = 1; mm <= 5; mm++) {
                float sx = cx + s * (12 * k + mm * (ol - 12 * k) / 6f), sh = (6 - mm) * 1.3f * k;
                b.tri(sx - 1.6f * k, oy, col(EMBER, oa), sx + 1.6f * k, oy, col(EMBER, oa), sx, oy + sh, col(BLOOD, 0));
            }
        }
        olho(b, cx, oy, 10 * k, 5.5f * k * ss(0.9f, 1.5f, t), oa);
        b.flush();

        // 3) textos (normal)
        RenderSystem.defaultBlendFunc();
        Font f = mc.font;
        int len = titulo.length();
        float sp = lerp(8f, 1.5f, rev), tot = 0;
        for (int i = 0; i < len; i++) tot += f.width(titulo.substring(i, i + 1)) + (i < len - 1 ? sp : 0);
        float x0 = cx - tot * S / 2, acc = 0;
        float gl = (t > 1f && fr(t / 1.3f) < 0.10f) ? 1f : 0f;        // rajada de glitch
        int frame = (int) (t * 30);
        for (int i = 0; i < len; i++) {
            String ch = titulo.substring(i, i + 1);
            float cw = f.width(ch), la = cl((t - 0.35f - 0.09f * i) / 0.35f), e = eo(la);
            float al = la * env * (0.85f + 0.15f * rnd(i, frame));
            int a8 = (int) (al * 255);
            if (a8 >= 8) {
                float jt = 0.4f + 1.6f * gl + 0.8f * beat;
                float jx = (rnd(i, frame) - 0.5f) * 2 * jt, jy = (rnd(i, frame + 99) - 0.5f) * 2 * jt;
                float sc = S * (1 + 0.5f * (1 - e)), gx = 1.5f + 4 * gl;
                g.pose().pushPose();
                g.pose().translate(x0 + acc * S, ty + (1 - e) * 12 * S, 0);
                g.pose().scale(sc, sc, 1);
                int glow = ((int) (a8 * 0.30f) << 24) | 0xC81414;
                g.drawString(f, ch, -1, 0, glow, false); g.drawString(f, ch, 1, 0, glow, false);
                g.drawString(f, ch, 0, -1, glow, false); g.drawString(f, ch, 0, 1, glow, false);
                g.drawString(f, ch, jx - gx, jy, ((int) (a8 * 0.55f) << 24) | 0xFF1E14, false);
                g.drawString(f, ch, jx + gx, jy, ((int) (a8 * 0.45f) << 24) | 0xFF8C28, false);
                int gg = (int) lerp(40, 130, cl(beat + gl)), bb = (int) lerp(28, 70, cl(beat + gl));
                g.drawString(f, ch, jx, jy, (a8 << 24) | (255 << 16) | (gg << 8) | bb, false);
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
            g.drawString(f, sub, 0, 0, (sa << 24) | 0xFF9678, true);
            g.pose().popPose();
        }
        RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ peças
    /** pulso de coração: 2 batidas por ciclo de 0.9 s, começando em 0.3 s (mesmo relógio do som). */
    private static float beat(float t) {
        if (t < 0.3f) return 0;
        float p = fr((t - 0.3f) / 0.9f) * 0.9f;
        return (float) (Math.exp(-p * 10) + (p > 0.28f ? 0.6 * Math.exp(-(p - 0.28f) * 10) : 0));
    }

    private static void fissuras(Buf b, float cx, float cy, float len, float t, float env, float k, boolean red) {
        int M = red ? 7 : 10, S = 9;
        for (int i = 0; i < M; i++) {
            float a = i * TAU / M + 0.25f * rnd(i, 7), x = cx, y = cy, step = len / S;
            for (int j = 0; j < S; j++) {
                a += (rnd(i * 17 + j, 8) - 0.5f) * 0.7f;
                float nx = x + cos(a) * step * (0.7f + 0.6f * rnd(i * 17 + j, 9));
                float ny = y + sin(a) * step * (0.7f + 0.6f * rnd(i * 17 + j, 9));
                float u = j / (float) S, u2 = (j + 1) / (float) S, wd = lerp(5, 1, u) * k * 0.6f;
                float fl = 0.75f + 0.25f * sin(t * 7 + i * 2 + j);
                b.seg(x, y, nx, ny, wd, col(j == 0 ? HOT : EMBER, (1 - u) * 0.9f * env * fl), col(EMBER, (1 - u2) * 0.9f * env * fl));
                b.seg(x, y, nx, ny, wd * 3f, col(BLOOD, (1 - u) * 0.25f * env), col(BLOOD, (1 - u2) * 0.25f * env));
                x = nx; y = ny;
            }
        }
    }

    private static void chifre(Buf b, float bx, float by, int s, float R, float g, float a) {
        int n = 10;
        float[] x = new float[n + 1], y = new float[n + 1], wd = new float[n + 1], nx = new float[n + 1], ny = new float[n + 1];
        for (int j = 0; j <= n; j++) {
            float u = j / (float) n * g;
            x[j] = bx + s * R * (1.1f * u - 0.9f * u * u);
            y[j] = by - R * (0.9f * u - 0.2f * u * u);
            wd[j] = 0.065f * R * (float) Math.pow(1 - j / (float) n, 0.8) + 0.4f;
        }
        for (int j = 0; j <= n; j++) {
            int a0 = Math.max(j - 1, 0), a1 = Math.min(j + 1, n);
            float tx = x[a1] - x[a0], ty = y[a1] - y[a0], l = Math.max(1e-4f, (float) Math.hypot(tx, ty));
            nx[j] = -ty / l; ny[j] = tx / l;
        }
        for (int j = 0; j < n; j++) {
            int c0 = col(mix(BLOOD, EMBER, j / (float) n), a * (0.7f + 0.3f * j / n));
            int c1 = col(mix(BLOOD, EMBER, (j + 1) / (float) n), a * (0.7f + 0.3f * (j + 1) / n));
            b.quad(x[j] + nx[j] * wd[j], y[j] + ny[j] * wd[j], c0, x[j] - nx[j] * wd[j], y[j] - ny[j] * wd[j], c0,
                    x[j + 1] - nx[j + 1] * wd[j + 1], y[j + 1] - ny[j + 1] * wd[j + 1], c1,
                    x[j + 1] + nx[j + 1] * wd[j + 1], y[j + 1] + ny[j + 1] * wd[j + 1], c1);
        }
    }

    private static void olho(Buf b, float cx, float cy, float wI, float hh, float a) {
        int n = 16;
        for (int i = 0; i < n; i++) {
            float u0 = -1 + 2f * i / n, u1 = -1 + 2f * (i + 1) / n, y0 = hh * (1 - u0 * u0), y1 = hh * (1 - u1 * u1);
            for (int sg = -1; sg <= 1; sg += 2) {
                b.seg(cx + u0 * wI, cy + sg * y0, cx + u1 * wI, cy + sg * y1, 1.4f, col(EMBER, a), col(EMBER, a));
                b.tri(cx + u0 * wI, cy + sg * y0, col(EMBER, a * 0.35f), cx + u1 * wI, cy + sg * y1, col(EMBER, a * 0.35f), cx, cy, col(HOT, a * 0.6f));
            }
        }
        b.diamante(cx, cy, Math.max(0.5f, wI * 0.07f), Math.max(0.5f, hh * 0.95f), col(WHITE, a));
    }

    private static void anel(Buf b, float cx, float cy, float r, int n, float w, float rot, int dash, int rgb, float a) {
        for (int i = 0; i < n; i++) {
            if (dash > 0 && i % dash == dash - 1) continue;
            float a0 = rot + i * TAU / n, a1 = rot + (i + 1) * TAU / n;
            b.seg(cx + cos(a0) * r, cy + sin(a0) * r, cx + cos(a1) * r, cy + sin(a1) * r, w, col(rgb, a), col(rgb, a));
        }
    }

    private static void disco(Buf b, float cx, float cy, float r, float a, int rgb) {
        for (int i = 0; i < 40; i++) {
            float a0 = i * TAU / 40, a1 = (i + 1) * TAU / 40;
            b.tri(cx, cy, col(rgb, a), cx + cos(a0) * r, cy + sin(a0) * r, col(rgb, 0), cx + cos(a1) * r, cy + sin(a1) * r, col(rgb, 0));
        }
    }

    private static void sons(float t) {
        // coração: batida forte em 0.3 + 0.9n e batida fraca 0.28 s depois
        while (true) {
            float bt = 0.3f + 0.9f * (beatIdx / 2) + (beatIdx % 2) * 0.28f;
            if (bt > DUR - 0.8f || t < bt) break;
            tocar(SoundEvents.WARDEN_HEARTBEAT, (beatIdx % 2 == 0) ? 0.9f : 0.8f, 1.0f);
            beatIdx++;
        }
        if (!fimTocado && t >= DUR - 0.9f) { fimTocado = true; tocar(SoundEvents.FIRE_EXTINGUISH, 0.5f, 0.8f); }
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
        void diamante(float cx, float cy, float rx, float ry, int c) {
            tri(cx - rx, cy, c, cx, cy - ry, c, cx + rx, cy, c); tri(cx - rx, cy, c, cx, cy + ry, c, cx + rx, cy, c);
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
