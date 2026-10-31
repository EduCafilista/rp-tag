package net.minecraft.client.gui;

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;

/** STUB: guarda o texto e a transformação corrente (translate+scale). */
public class GuiGraphics {
    public static int W = 640, H = 360;
    public static final class Texto {
        public final String s; public final float x, y, esc; public final int cor; public final boolean sombra;
        Texto(String s, float x, float y, float esc, int cor, boolean sombra) { this.s = s; this.x = x; this.y = y; this.esc = esc; this.cor = cor; this.sombra = sombra; }
    }
    public static final List<Texto> TEXTOS = new ArrayList<>();
    private final List<Float> pilha = new ArrayList<>();
    private float tx, ty, sx = 1, sy = 1;
    private final Matrix4f ident = new Matrix4f();

    public int guiWidth() { return W; }
    public int guiHeight() { return H; }
    public void flush() { }

    public PoseStack pose() { return POSE; }
    private final PoseStack POSE = new PoseStack(this);

    void push() { pilha.add(tx); pilha.add(ty); pilha.add(sx); pilha.add(sy); }
    void pop() { int n = pilha.size(); sy = pilha.get(--n); sx = pilha.get(--n); ty = pilha.get(--n); tx = pilha.get(--n); pilha.subList(n - 0, n).clear(); }
    void translate(float x, float y) { tx += x * sx; ty += y * sy; }
    void scale(float a, float b) { sx *= a; sy *= b; }

    public int drawString(Font f, String s, int x, int y, int cor, boolean sombra) {
        TEXTOS.add(new Texto(s, tx + x * sx, ty + y * sy, sx, cor, sombra));
        return x + f.width(s);
    }

    public static final class PoseStack {
        private final GuiGraphics g;
        private final List<Object> st = new ArrayList<>();
        PoseStack(GuiGraphics g) { this.g = g; }
        public void pushPose() { g.push(); }
        public void popPose() { g.pop(); }
        public void translate(float x, float y, float z) { g.translate(x, y); }
        public void scale(float a, float b, float c) { g.scale(a, b); }
        public Last last() { return LAST; }
        private final Last LAST = new Last();
    }
    public static final class Last {
        private final Matrix4f m = new Matrix4f();
        public Matrix4f pose() { return m; }
    }
}
