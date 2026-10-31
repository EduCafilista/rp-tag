package dev.rptag.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

/**
 * STUB do harness: mesma matemática do AvisoFxUtil real, mas o Buf rasteriza os
 * triângulos num framebuffer em memória (nada de OpenGL). Serve para VER o FX
 * exatamente como o Java o desenha.
 */
public final class AvisoFxUtil {
    public static final float TAU = 6.2831853f, DEG = (float) Math.PI / 180f;

    private AvisoFxUtil() {}

    public static void preparar(GuiGraphics g) { }
    public static void normal() { RenderSystem.aditivo = false; }
    public static void aditivo() { RenderSystem.aditivo = true; }
    public static void tocar(net.minecraft.sounds.SoundEvent ev, float pitch, float vol) { }

    // ---------------------------------------------------------------- framebuffer
    public static int W, H;
    public static float[] FB;

    public static void novoFrame(int w, int h) {
        W = w; H = h; FB = new float[w * h * 3];
    }

    static void pix(int x, int y, float r, float g, float b, float a) {
        if (x < 0 || y < 0 || x >= W || y >= H || a <= 0.0005f) return;
        if (a > 1) a = 1;
        int i = (y * W + x) * 3;
        if (RenderSystem.aditivo) { FB[i] += r * a; FB[i + 1] += g * a; FB[i + 2] += b * a; }
        else { FB[i] = FB[i] * (1 - a) + r * a; FB[i + 1] = FB[i + 1] * (1 - a) + g * a; FB[i + 2] = FB[i + 2] * (1 - a) + b * a; }
    }

    public static final class Buf {
        final Matrix4f m;
        public Buf(Matrix4f m) { this.m = m; }

        public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
            float minx = Math.min(x1, Math.min(x2, x3)), maxx = Math.max(x1, Math.max(x2, x3));
            float miny = Math.min(y1, Math.min(y2, y3)), maxy = Math.max(y1, Math.max(y2, y3));
            int x0 = Math.max(0, (int) Math.floor(minx)), x9 = Math.min(W - 1, (int) Math.ceil(maxx));
            int y0 = Math.max(0, (int) Math.floor(miny)), y9 = Math.min(H - 1, (int) Math.ceil(maxy));
            float d = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3);
            if (Math.abs(d) < 1e-9f) { return; }
            for (int y = y0; y <= y9; y++) {
                for (int x = x0; x <= x9; x++) {
                    float px = x + 0.5f, py = y + 0.5f;
                    float w0 = ((y2 - y3) * (px - x3) + (x3 - x2) * (py - y3)) / d;
                    float w1 = ((y3 - y1) * (px - x3) + (x1 - x3) * (py - y3)) / d;
                    float w2 = 1 - w0 - w1;
                    if (w0 < -0.002f || w1 < -0.002f || w2 < -0.002f) continue;
                    float a1 = (c1 >>> 24) / 255f, a2 = (c2 >>> 24) / 255f, a3 = (c3 >>> 24) / 255f;
                    float a = w0 * a1 + w1 * a2 + w2 * a3;
                    float r = (w0 * ((c1 >> 16) & 255) + w1 * ((c2 >> 16) & 255) + w2 * ((c3 >> 16) & 255)) / 255f;
                    float g = (w0 * ((c1 >> 8) & 255) + w1 * ((c2 >> 8) & 255) + w2 * ((c3 >> 8) & 255)) / 255f;
                    float b = (w0 * (c1 & 255) + w1 * (c2 & 255) + w2 * (c3 & 255)) / 255f;
                    pix(x, y, r, g, b, a);
                }
            }
        }

        public void quad(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3, float x4, float y4, int c4) {
            tri(x1, y1, c1, x2, y2, c2, x3, y3, c3); tri(x1, y1, c1, x3, y3, c3, x4, y4, c4);
        }
        public void rect(float x, float y, float w, float h, int c) { quad(x, y, c, x + w, y, c, x + w, y + h, c, x, y + h, c); }
        public void seg(float ax, float ay, float bx, float by, float w, int ca, int cb) {
            float dx = by - ay, dy = ax - bx, l = (float) Math.hypot(dx, dy);
            if (l < 1e-4f) return;
            dx = dx / l * w / 2; dy = dy / l * w / 2;
            quad(ax + dx, ay + dy, ca, ax - dx, ay - dy, ca, bx - dx, by - dy, cb, bx + dx, by + dy, cb);
        }
        public void circ(float cx, float cy, float r, int c) { elipse(cx, cy, 1, 0, r, r, c); }
        public void elipse(float cx, float cy, float ux, float uy, float a, float bb2, int c) {
            float nx = -uy, ny = ux;
            for (int i = 0; i < 12; i++) {
                float p0 = i * TAU / 12, p1 = (i + 1) * TAU / 12;
                tri(cx, cy, c, cx + ux * a * cos(p0) + nx * bb2 * sin(p0), cy + uy * a * cos(p0) + ny * bb2 * sin(p0), c,
                        cx + ux * a * cos(p1) + nx * bb2 * sin(p1), cy + uy * a * cos(p1) + ny * bb2 * sin(p1), c);
            }
        }
        public void diamante(float cx, float cy, float rx, float ry, int c) {
            tri(cx - rx, cy, c, cx, cy - ry, c, cx + rx, cy, c); tri(cx - rx, cy, c, cx, cy + ry, c, cx + rx, cy, c);
        }
        public void brilho(float cx, float cy, float r, int rgb, float a) {
            for (int i = 0; i < 32; i++) {
                float a0 = i * TAU / 32, a1 = (i + 1) * TAU / 32;
                tri(cx, cy, col(rgb, a), cx + cos(a0) * r, cy + sin(a0) * r, col(rgb, 0), cx + cos(a1) * r, cy + sin(a1) * r, col(rgb, 0));
            }
        }
        public void anel(float cx, float cy, float r, int n, float w, float rot, int rgb, float a) {
            for (int i = 0; i < n; i++) {
                float a0 = rot + i * TAU / n, a1 = rot + (i + 1) * TAU / n;
                seg(cx + cos(a0) * r, cy + sin(a0) * r, cx + cos(a1) * r, cy + sin(a1) * r, w, col(rgb, a), col(rgb, a));
            }
        }
        public void vinheta(int w, int h, float d, int rgb, float a) {
            quad(0, 0, col(rgb, a), w, 0, col(rgb, a), w, d, col(rgb, 0), 0, d, col(rgb, 0));
            quad(0, h, col(rgb, a), w, h, col(rgb, a), w, h - d, col(rgb, 0), 0, h - d, col(rgb, 0));
            quad(0, 0, col(rgb, a), 0, h, col(rgb, a), d, h, col(rgb, 0), d, 0, col(rgb, 0));
            quad(w, 0, col(rgb, a), w, h, col(rgb, a), w - d, h, col(rgb, 0), w - d, 0, col(rgb, 0));
        }
        public void flush() { }
    }

    public static int mix(int c1, int c2, float u) {
        int r = (int) lerp((c1 >> 16) & 255, (c2 >> 16) & 255, u), g = (int) lerp((c1 >> 8) & 255, (c2 >> 8) & 255, u), b = (int) lerp(c1 & 255, c2 & 255, u);
        return (r << 16) | (g << 8) | b;
    }
    public static int col(int rgb, float a) { return ((int) (cl(a) * 255) << 24) | (rgb & 0xFFFFFF); }
    public static float cl(float x) { return x < 0 ? 0 : Math.min(x, 1); }
    public static float ss(float a, float b, float x) { float u = cl((x - a) / (b - a)); return u * u * (3 - 2 * u); }
    public static float eo(float x) { return 1 - (1 - x) * (1 - x) * (1 - x); }
    public static float lerp(float a, float b, float u) { return a + (b - a) * u; }
    public static float fr(float x) { return x - (float) Math.floor(x); }
    public static float rnd(int i, int s) { return fr((float) Math.sin(i * 12.9898 + s * 78.233) * 43758.547f); }
    public static float sin(float x) { return (float) Math.sin(x); }
    public static float cos(float x) { return (float) Math.cos(x); }
}
