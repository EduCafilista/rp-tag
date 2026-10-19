package dev.rptag.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import org.joml.Matrix4f;

/** Peças comuns de PesadeloAvisoFx, InfernoAvisoFx e GeloAvisoFx (desenho de triângulos coloridos + matemática). */
public final class AvisoFxUtil {
    public static final float TAU = 6.2831853f, DEG = (float) Math.PI / 180f;

    private AvisoFxUtil() {}

    /** Prepara o estado de render do GUI para desenhar geometria crua (chame no começo do render). */
    public static void preparar(GuiGraphics g) {
        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }
    public static void normal() { RenderSystem.defaultBlendFunc(); }
    public static void aditivo() { RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE); }

    public static void tocar(SoundEvent ev, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, vol));
    }

    /** Buffer de triângulos POSITION_COLOR. Crie um novo depois de cada flush(). */
    public static final class Buf {
        final Matrix4f m; BufferBuilder bb; int n;
        public Buf(Matrix4f m) { this.m = m; }
        void v(float x, float y, int c) {
            if (bb == null) bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            bb.addVertex(m, x, y, 0).setColor(c); n++;
        }
        public void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) { v(x1, y1, c1); v(x2, y2, c2); v(x3, y3, c3); }
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
        /** disco com centro opaco e borda transparente. */
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
        /** moldura em degradê nas 4 bordas: cor cheia na borda, transparente a "d" pixels para dentro. */
        public void vinheta(int w, int h, float d, int rgb, float a) {
            quad(0, 0, col(rgb, a), w, 0, col(rgb, a), w, d, col(rgb, 0), 0, d, col(rgb, 0));
            quad(0, h, col(rgb, a), w, h, col(rgb, a), w, h - d, col(rgb, 0), 0, h - d, col(rgb, 0));
            quad(0, 0, col(rgb, a), 0, h, col(rgb, a), d, h, col(rgb, 0), d, 0, col(rgb, 0));
            quad(w, 0, col(rgb, a), w, h, col(rgb, a), w - d, h, col(rgb, 0), w - d, 0, col(rgb, 0));
        }
        public void flush() { if (n > 0) BufferUploader.drawWithShader(bb.buildOrThrow()); }
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
