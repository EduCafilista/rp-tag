package dev.rptag.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;

/**
 * AVISO DE SANGUE — "Sangria" (~6 s)
 *  - IMPACTO em 0.15 s: o sangue espirra do centro (gotas esticadas em voo que depois escorrem pela tela)
 *  - uma crosta de sangue pinga do teto: ~22 filetes com cabeça de gota, brilho e aceleração (gravidade)
 *  - uma poça sobe do rodapé, com ondas, e drena no fim
 *  - veias/capilares crescem a partir das 4 bordas, pulsando; a tela fica avermelhada nas pontas (vinheta viva)
 *  - o texto "bate" na tela, trepida e depois PINGA: cada letra solta filetes de sangue
 *
 * USO (adaptado ao RP Tag, 3.95.0 — VISUAL IDÊNTICO ao protótipo):
 *   camada:     SangueAvisoFx.registrar(e);   (ClientGuiLayers)
 *   disparo:    SangueAvisoFx.iniciar("AVISO", mensagem)
 *               (ClientEffects.startScreenText, estilo 9 sangue —
 *                título fixo "AVISO", mensagem configurável no subtítulo)
 *   servidor:   particulas de mundo via RPCommands.particulasSangue
 *               (server-safe, sem classes client)
 */
public final class SangueAvisoFx {
    private static final float DUR = 6.0f, DEG = (float) Math.PI / 180f, TAU = 6.2831853f;
    private static final int BLOOD = 0xB40C16, DEEP = 0x5A0008, VEIN = 0x960A0F, GLOSS = 0xFF8C8C, WHITE = 0xFFFFFF, VOID = 0x0A0000, WINE = 0x780000;
    private static final float[] DR = {0.9f, 1.6f, 2.4f, 3.1f, 3.9f};   // pingos sonoros

    /** true = sem clarão de impacto e menos veias/gotas. */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String titulo = "AVISO", sub = "";
    private static int dripIdx;
    private static boolean impactoTocado;

    private SangueAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_sangue"), SangueAvisoFx::render);
    }

    public static void iniciar(String t, String s) {
        titulo = (t == null || t.isEmpty()) ? "AVISO" : t;
        sub = s == null ? "" : s;
        inicio = System.nanoTime();
        dripIdx = 0;
        impactoTocado = false;
    }

    /** SERVIDOR: espirro de sangue no peito + chuva de gotas caindo de cima. */
    public static void particulasMundo(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        BlockParticleOption blk = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
        BlockParticleOption fall = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.REDSTONE_BLOCK.defaultBlockState());
        lv.sendParticles(blk, x, y + 1.0, z, 60, 0.45, 0.6, 0.45, 0.25);
        for (int i = 0; i < 24; i++)
            lv.sendParticles(fall, x + (Math.random() - 0.5) * 2.4, y + 3.2, z + (Math.random() - 0.5) * 2.4, 1, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t >= DUR) { inicio = -1; return; }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h * 0.45f, k = Math.min(w, h) / 240f;
        float env = ss(0, 0.4f, t) * (1 - ss(DUR - 1.2f, DUR, t));
        float rev = eo(cl((t - 0.1f) / 0.9f));
        float pulse = 0.5f + 0.5f * sin(t * 5f);
        sons(t);

        // layout do título (precisa antes: os filetes das letras dependem das posições)
        Font f = mc.font;
        float S = k * 1.9f, ty = cy - 4 * S;
        int len = titulo.length();
        float sp = lerp(8f, 1.5f, rev), tot = 0;
        float[] cw = new float[len], xs = new float[len];
        for (int i = 0; i < len; i++) { cw[i] = f.width(titulo.substring(i, i + 1)); tot += cw[i] + (i < len - 1 ? sp : 0); }
        float x0 = cx - tot * S / 2, acc = 0;
        for (int i = 0; i < len; i++) { xs[i] = x0 + acc * S; acc += cw[i] + sp; }

        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Buf b = new Buf(g.pose().last().pose());

        // 1) tom avermelhado + vinheta viva
        b.quad(0, 0, col(WINE, 0.12f * env), w, 0, col(WINE, 0.12f * env), w, h, col(WINE, 0.12f * env), 0, h, col(WINE, 0.12f * env));
        float d = Math.min(w, h) * 0.34f, va = 0.75f * (0.8f + 0.2f * pulse) * env;
        b.quad(0, 0, col(DEEP, va), w, 0, col(DEEP, va), w, d, col(DEEP, 0), 0, d, col(DEEP, 0));
        b.quad(0, h, col(DEEP, va), w, h, col(DEEP, va), w, h - d, col(DEEP, 0), 0, h - d, col(DEEP, 0));
        b.quad(0, 0, col(DEEP, va), 0, h, col(DEEP, va), d, h, col(DEEP, 0), d, 0, col(DEEP, 0));
        b.quad(w, 0, col(DEEP, va), w, h, col(DEEP, va), w - d, h, col(DEEP, 0), w - d, 0, col(DEEP, 0));
        disco(b, cx, cy, 80 * k, 0.40f * env, VOID);                  // apoio de leitura atrás do título

        // 2) veias nas 4 bordas
        float vg = eo(cl((t - 0.2f) / 1.4f)), vl = Math.min(w, h) * 0.30f, vA = (0.55f + 0.25f * pulse) * env;
        float[] fr4 = reduzido ? new float[]{0.3f, 0.7f} : new float[]{0.12f, 0.37f, 0.63f, 0.88f};
        for (int e = 0; e < 4; e++) {
            for (int q = 0; q < fr4.length; q++) {
                int sd = e * 4 + q;
                float u = fr4[q] + 0.05f * (rnd(sd, 71) - 0.5f), ja = (rnd(sd, 72) - 0.5f) * 40 * DEG;
                float vx = e < 2 ? w * u : (e == 2 ? 0 : w), vy = e < 2 ? (e == 0 ? 0 : h) : h * u;
                float ang = (e == 0 ? 90 : e == 1 ? -90 : e == 2 ? 0 : 180) * DEG + ja;
                veia(b, vx, vy, ang, vl, 3, sd * 31 + 5, vg, vA, k);
            }
        }

        // 3) poça no rodapé
        float lf = ss(0.4f, 1.4f, t) * (1 - ss(DUR - 1.4f, DUR - 0.2f, t)), pa = cl(lf * 4);
        if (pa > 0.01f) {
            for (float xa = 0; xa < w; xa += 8) {
                float xb = Math.min(w, xa + 8), ya = poolY(xa, h, k, t, lf), yb = poolY(xb, h, k, t, lf);
                b.quad(xa, ya, col(BLOOD, 0.95f * pa), xb, yb, col(BLOOD, 0.95f * pa), xb, h, col(DEEP, 0.98f * pa), xa, h, col(DEEP, 0.98f * pa));
                b.seg(xa, ya + k * 0.6f, xb, yb + k * 0.6f, 1.2f, col(GLOSS, 0.25f * pa), col(GLOSS, 0.25f * pa));
            }
        }

        // 4) crosta no teto + filetes que escorrem
        for (float xa = 0; xa < w; xa += 8) {
            float xb = Math.min(w, xa + 8);
            float ha = bandH(xa, k, t, rev), hb = bandH(xb, k, t, rev);
            b.quad(xa, 0, col(BLOOD, 0.95f * env), xb, 0, col(BLOOD, 0.95f * env), xb, hb, col(BLOOD, 0.95f * env), xa, ha, col(BLOOD, 0.95f * env));
            b.seg(xa, ha - 0.5f * k, xb, hb - 0.5f * k, 1.0f, col(GLOSS, 0.20f * env), col(GLOSS, 0.20f * env));
        }
        int ND = reduzido ? 12 : 22;
        for (int i = 0; i < ND; i++) {
            float xx = (i + 0.15f + 0.7f * rnd(i, 31)) / ND * w, tau = t - (0.05f + 0.7f * rnd(i, 32));
            if (tau <= 0) continue;
            float r = (2.0f + 1.8f * rnd(i, 33)) * k * 0.8f;
            float v0 = (6 + 10 * rnd(i, 34)) * k, ga = (10 + 10 * rnd(i, 35)) * k;
            float yh = Math.min(h * 0.93f, v0 * tau + 0.5f * ga * tau * tau + bandH(xx, k, t, rev));
            int c = col(BLOOD, 0.95f * env);
            b.quad(xx - 0.45f * r, 0, c, xx + 0.45f * r, 0, c, xx + 0.8f * r, yh, c, xx - 0.8f * r, yh, c);
            b.seg(xx - 0.3f * r, 0, xx - 0.3f * r, yh, 0.18f * r, col(WHITE, 0.18f * env), col(WHITE, 0.18f * env));
            b.circ(xx, yh, r * 1.05f, c);
            b.circ(xx - 0.3f * r, yh - 0.3f * r, r * 0.28f, col(WHITE, 0.40f * env));
        }

        // 5) respingo do impacto: gotas esticadas em voo, que depois escorrem
        float spl = eo(cl((t - 0.15f) / 0.45f));
        if (t > 0.15f) {
            int NS = reduzido ? 18 : 36;
            for (int i = 0; i < NS; i++) {
                float an = rnd(i, 51) * TAU, df = 0.25f + 0.75f * (float) Math.pow(rnd(i, 52), 1.3);
                float dist = (34 + 105 * df) * k * spl;
                float sz = (1 + 9 * (float) Math.pow(rnd(i, 53), 3)) * k * (1.1f - 0.4f * df);
                float ux = cos(an), uy = sin(an), px = cx + ux * dist, py = cy + uy * dist;
                float slide = t > 0.8f ? (float) Math.pow(t - 0.8f, 1.5) * 10 * k * rnd(i, 54) : 0;
                int c = col(BLOOD, 0.95f * env);
                if (spl < 1) b.seg(cx + ux * dist * 0.6f, cy + uy * dist * 0.6f, px, py, sz * 0.9f, col(BLOOD, 0), col(BLOOD, 0.9f * env * (1 - spl)));
                if (slide > 0) b.seg(px, py, px, py + slide, sz * 0.7f, c, c);
                b.elipse(px, py + slide, ux, uy, sz * (1 + 3 * (1 - spl)), sz, c);
                b.circ(px - sz * 0.3f, py + slide - sz * 0.3f, sz * 0.25f, col(WHITE, 0.35f * env));
            }
        }
        if (!reduzido && t > 0.15f && t < 0.35f) {
            float fa = (1 - (t - 0.15f) / 0.2f) * 0.30f;
            b.quad(0, 0, col(BLOOD, fa), w, 0, col(BLOOD, fa), w, h, col(BLOOD, fa), 0, h, col(BLOOD, fa));
        }

        // 6) filetes que saem das letras
        for (int i = 0; i < len; i++) {
            float la = cl((t - 0.2f - 0.07f * i) / 0.18f);
            for (int dd = 0; dd < 2; dd++) {
                if (dd == 1 && rnd(i, 61) < 0.5f) continue;
                float tau = t - (0.8f + 0.25f * i + 0.5f * rnd(i * 3 + dd, 62));
                if (tau <= 0) continue;
                float maxL = (14 + 30 * rnd(i * 3 + dd, 63)) * k, L = Math.min(maxL, 8 * k * tau + 3 * k * tau * tau);
                float dx = xs[i] + cw[i] * S * (0.2f + 0.6f * rnd(i * 3 + dd, 64)), y0 = ty + 7 * S;
                int c = col(BLOOD, 0.95f * env * la);
                b.quad(dx - 0.8f * k, y0, c, dx + 0.8f * k, y0, c, dx + 0.55f * k, y0 + L, c, dx - 0.55f * k, y0 + L, c);
                b.circ(dx, y0 + L, 1.5f * k, c);
                b.circ(dx - 0.45f * k, y0 + L - 0.45f * k, 0.4f * k, col(WHITE, 0.4f * env * la));
            }
        }
        b.flush();

        // 7) texto
        int frame = (int) (t * 30);
        for (int i = 0; i < len; i++) {
            String ch = titulo.substring(i, i + 1);
            float la = cl((t - 0.2f - 0.07f * i) / 0.18f), e = eo(la);
            int a8 = (int) (la * env * 255);
            if (a8 < 8) continue;
            float shake = (1 - ss(0.2f, 0.9f, t)) * 1.5f;
            float jx = (rnd(i, frame) - 0.5f) * 2 * shake, jy = (rnd(i, frame + 99) - 0.5f) * 2 * shake;
            float sc = S * (1 + 1.0f * (1 - e));
            g.pose().pushPose();
            g.pose().translate(xs[i] + cw[i] * S / 2 - cw[i] * sc / 2, ty - 4 * (sc - S), 0);
            g.pose().scale(sc, sc, 1);
            int dark = (a8 << 24) | 0x3C0004;
            g.drawString(f, ch, jx - 1, jy, dark, false); g.drawString(f, ch, jx + 1, jy, dark, false);
            g.drawString(f, ch, jx, jy - 1, dark, false); g.drawString(f, ch, jx, jy + 1, dark, false);
            g.drawString(f, ch, jx, jy, (a8 << 24) | 0xC81019, false);
            g.drawString(f, ch, jx - 0.5f, jy - 0.5f, ((int) (a8 * 0.35f) << 24) | 0xFF7878, false);
            g.pose().popPose();
        }
        int sa = (int) (ss(0.8f, 1.4f, t) * env * 255);
        if (!sub.isEmpty() && sa >= 8) {
            float S2 = Math.min(k * 0.75f, w * 0.85f / Math.max(1, f.width(sub)));
            g.pose().pushPose();
            g.pose().translate(cx - f.width(sub) * S2 / 2, cy + 25 * k, 0);
            g.pose().scale(S2, S2, 1);
            g.drawString(f, sub, 0, 0, (sa << 24) | 0xFFAAAA, true);
            g.pose().popPose();
        }
        RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ peças
    private static float poolY(float x, int h, float k, float t, float lf) {
        return h - (0.02f + 0.09f * lf) * h - 3 * k * sin(x * 0.03f + t * 2.2f) - 1.5f * k * sin(x * 0.08f - t * 3.1f);
    }

    private static float bandH(float x, float k, float t, float rev) {
        return (7 * k + 3 * k * sin(x * 0.04f + t * 1.3f) + 2 * k * sin(x * 0.11f - t * 2f)) * rev;
    }

    /** veia ramificada: 4 segmentos por galho, bifurca nos nós 1 e 3. */
    private static void veia(Buf b, float x, float y, float ang, float len, int dep, int seed, float g, float a, float k) {
        if (dep <= 0 || g <= 0) return;
        float L = len * Math.min(1, g), px = x, py = y, an = ang;
        for (int i = 0; i < 4; i++) {
            an += (rnd(seed + i, 21) - 0.5f) * 0.6f;
            float nx = px + cos(an) * L / 4, ny = py + sin(an) * L / 4, wd = dep * 0.5f * k;
            b.seg(px, py, nx, ny, wd, col(VEIN, a), col(VEIN, a * 0.8f));
            if ((i == 1 || i == 3) && g > 0.4f)
                veia(b, nx, ny, an + (rnd(seed + i, 22) > 0.5f ? 0.7f : -0.7f), len * 0.6f, dep - 1, seed * 7 + i * 13 + dep, cl((g - 0.4f) / 0.6f), a * 0.9f, k);
            px = nx; py = ny;
        }
    }

    private static void disco(Buf b, float cx, float cy, float r, float a, int rgb) {
        for (int i = 0; i < 40; i++) {
            float a0 = i * TAU / 40, a1 = (i + 1) * TAU / 40;
            b.tri(cx, cy, col(rgb, a), cx + cos(a0) * r, cy + sin(a0) * r, col(rgb, 0), cx + cos(a1) * r, cy + sin(a1) * r, col(rgb, 0));
        }
    }

    private static void sons(float t) {
        if (!impactoTocado && t >= 0.15f) {
            impactoTocado = true;
            tocar(SoundEvents.SLIME_BLOCK_BREAK, 0.45f, 1.0f);
            tocar(SoundEvents.GENERIC_SPLASH, 0.6f, 0.7f);
        }
        while (dripIdx < DR.length && t >= DR[dripIdx]) {
            tocar(SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 0.6f + 0.08f * (dripIdx % 3), 0.9f);
            dripIdx++;
        }
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
        void circ(float cx, float cy, float r, int c) { elipse(cx, cy, 1, 0, r, r, c); }
        /** elipse com eixo "a" na direção (ux,uy) e eixo "b" perpendicular. */
        void elipse(float cx, float cy, float ux, float uy, float a, float bb2, int c) {
            float nx = -uy, ny = ux;
            for (int i = 0; i < 14; i++) {
                float p0 = i * TAU / 14, p1 = (i + 1) * TAU / 14;
                tri(cx, cy, c,
                        cx + ux * a * cos(p0) + nx * bb2 * sin(p0), cy + uy * a * cos(p0) + ny * bb2 * sin(p0), c,
                        cx + ux * a * cos(p1) + nx * bb2 * sin(p1), cy + uy * a * cos(p1) + ny * bb2 * sin(p1), c);
            }
        }
        void flush() { if (n > 0) BufferUploader.drawWithShader(bb.buildOrThrow()); }
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
