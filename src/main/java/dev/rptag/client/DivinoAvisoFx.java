package dev.rptag.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;

/**
 * AVISO "ASCENSÃO" — "Ascensão Celestial" (~6 s), adaptado do protótipo
 * do dono do mod (3.94.0: VISUAL IDÊNTICO ao protótipo). Título fixo
 * "AVISO" cai letra a letra e a MENSAGEM configurável do comando vai no
 * subtítulo. Disparado pelo estilo 7 (divino) do /rp admin avisar e
 * do /lorezone aviso.
 *
 *  0.0s  clarão + vinheta escura, pilar de luz desce do céu
 *  0.1s  halo rúnico abre (anéis, tiques, hexagrama), 2 camadas de raios
 *        girando em sentidos opostos
 *  0.3s  asas de luz (2x9 penas) se abrem e batem devagar
 *  0.35s letras caem uma a uma, com brilho dourado varrendo o texto
 *  1.0s  ornamento + mensagem; faíscas em estrela sobem o tempo todo
 *  5.0s  tudo se recolhe e dissipa
 *
 * <p>(4.9.1) DE VOLTA AO ANTIGO: na 4.6.0 o Marcello trocou o fundo por um video
 * renderizado no Blender (fundo cinza/branco) e o dono achou esquisito — "quero o
 * antigo, estava melhor". O video do divino SAIU: este e o desenho procedural de
 * sempre (vinheta + pilar + raios + halo runico + asas + faiscas + ouro), sem
 * depender de configuracao nenhuma.
 *
 * USO:
 *   mod bus (cliente):  DivinoAvisoFx.registrar(e);  (em RegisterGuiLayersEvent)
 *   ao receber o pacote: DivinoAvisoFx.iniciar("AVISO", mensagem);
 *   no servidor (opcional): RPCommands.particulasAscensao(serverPlayer);
 */
public final class DivinoAvisoFx {
    private static final float DUR = 6.0f, DEG = (float) Math.PI / 180f, TAU = 6.2831853f;
    private static final int GOLD = 0xFFCD60, CREAM = 0xFFF0BE, WHITE = 0xFFFFFF, DARK = 0x0A0600;
    private static final float[] CT = {0.05f, 0.30f, 0.55f, 0.80f, 1.10f};   // tempos dos sinos
    private static final float[] CP = {0.79f, 1.00f, 1.19f, 1.50f, 2.00f};   // tons (acorde)

    /** true = sem clarão e menos partículas (acessibilidade / fotossensibilidade). */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String titulo = "AVISO", sub = "";
    private static int somIdx;
    private static boolean fimTocado;

    private DivinoAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_divino"), DivinoAvisoFx::render);
    }

    /** (3.94.0) igual ao protótipo: título fixo "AVISO" cai letra a letra;
     *  a mensagem configurável do comando vai no subtículo (auto-ajustável). */
    public static void iniciar(String t, String s) {
        titulo = (t == null || t.isEmpty()) ? "AVISO" : t;
        sub = s == null ? "" : s;
        inicio = System.nanoTime();
        somIdx = 0;
        fimTocado = false;
        tocar(SoundEvents.BEACON_ACTIVATE, 1.7f, 0.7f);
    }

    /** Encerra o aviso na hora (um aviso novo substitui este). */
    public static void parar() {
        inicio = -1;
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t >= DUR) { inicio = -1; return; }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h * 0.42f, k = Math.min(w, h) / 240f;
        float env = ss(0, 0.5f, t) * (1 - ss(DUR - 1.2f, DUR, t));
        float rev = eo(cl((t - 0.1f) / 1.1f));
        sons(t);

        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = g.pose().last().pose();

        // 1) vinheta escura (blend normal)
        RenderSystem.defaultBlendFunc();
        Buf b = new Buf(m);
        float d = Math.min(w, h) * 0.30f, va = 0.55f * env;
        b.quad(0, 0, col(DARK, va), w, 0, col(DARK, va), w, d, col(DARK, 0), 0, d, col(DARK, 0));
        b.quad(0, h, col(DARK, va), w, h, col(DARK, va), w, h - d, col(DARK, 0), 0, h - d, col(DARK, 0));
        b.quad(0, 0, col(DARK, va), 0, h, col(DARK, va), d, h, col(DARK, 0), d, 0, col(DARK, 0));
        b.quad(w, 0, col(DARK, va), w, h, col(DARK, va), w - d, h, col(DARK, 0), w - d, 0, col(DARK, 0));
        b.flush();

        // 2) tudo que brilha (blend aditivo)
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        b = new Buf(m);

        if (!reduzido && t < 0.3f)                                   // clarão de abertura
            b.quad(0, 0, col(WHITE, (1 - t / 0.3f) * 0.45f), w, 0, col(WHITE, (1 - t / 0.3f) * 0.45f),
                    w, h, col(WHITE, (1 - t / 0.3f) * 0.45f), 0, h, col(WHITE, (1 - t / 0.3f) * 0.45f));

        float yp = cy * eo(cl(t / 0.6f));                            // pilar de luz descendo
        for (int s = -1; s <= 1; s += 2) {
            float pw = 26 * k * rev * s, cw = 5 * k * s;
            b.quad(cx + pw, 0, col(GOLD, 0), cx, 0, col(CREAM, 0.30f * env), cx, yp, col(CREAM, 0.30f * env), cx + pw, yp, col(GOLD, 0));
            b.quad(cx + cw, 0, col(WHITE, 0), cx, 0, col(WHITE, 0.45f * env), cx, yp, col(WHITE, 0.45f * env), cx + cw, yp, col(WHITE, 0));
        }

        float diag = (float) Math.hypot(w, h);
        float rl = diag * (0.55f + 0.45f * rev) * (0.7f + 0.3f * env);
        raios(b, cx, cy, reduzido ? 10 : 16, rl, t * 4 * DEG, 0.028f, 0.40f * env, GOLD, t);
        raios(b, cx, cy, reduzido ? 8 : 12, rl * 0.9f, -t * 6 * DEG, 0.010f, 0.50f * env, CREAM, t);

        float r1 = 62 * k * rev;                                     // halo
        disco(b, cx, cy, r1 * 1.15f, 0.40f * env, GOLD);
        anel(b, cx, cy, r1, 90, 1.8f, 0, 0, GOLD, 0.90f * env);
        anel(b, cx, cy, r1 * 0.78f, 72, 1.0f, 0, 0, CREAM, 0.55f * env);
        anel(b, cx, cy, r1 * 1.28f, 96, 1.2f, t * 5 * DEG, 4, GOLD, 0.70f * env);
        for (int i = 0; i < 48; i++) {                                // tiques rúnicos
            float a = -t * 10 * DEG + i * TAU / 48, r0 = r1 * 1.28f + 2 * k, r2 = r0 + ((i & 3) == 0 ? 9 : 4) * k;
            b.seg(cx + cos(a) * r0, cy + sin(a) * r0, cx + cos(a) * r2, cy + sin(a) * r2, 1.2f, col(CREAM, 0.7f * env), col(GOLD, 0.0f));
        }
        for (int q = 0; q < 2; q++) {                                 // hexagrama
            float rot = (q == 0 ? 1 : -1) * t * 8 * DEG + q * 60 * DEG, rr = r1 * 0.78f;
            for (int i = 0; i < 3; i++) {
                float a0 = rot + i * TAU / 3, a1 = rot + (i + 1) * TAU / 3;
                b.seg(cx + cos(a0) * rr, cy + sin(a0) * rr, cx + cos(a1) * rr, cy + sin(a1) * rr, 1.0f, col(GOLD, 0.45f * env), col(GOLD, 0.45f * env));
            }
        }
        for (int i = 0; i < 4; i++) {                                 // losangos orbitando
            float a = t * 20 * DEG + i * TAU / 4;
            b.diamante(cx + cos(a) * r1, cy + sin(a) * r1, 3 * k, 3 * k, col(WHITE, 0.9f * env));
        }

        float wa = 0.30f * env * ss(0.3f, 1.1f, t);                   // asas
        for (int s = -1; s <= 1; s += 2) asa(b, cx + s * r1 * 1.05f, cy, s, r1 * 2.1f * rev, wa, t);

        int n = reduzido ? 20 : 50;                                   // faíscas em estrela
        for (int i = 0; i < n; i++) {
            float p = fr(t * (0.05f + 0.08f * rnd(i, 2)) + rnd(i, 3));
            float px = rnd(i, 1) * w + sin(t * 1.3f + i) * 12, py = h * (1.05f - p * 1.1f);
            float sz = (1.2f + 2.4f * rnd(i, 4)) * k * 0.9f;
            float al = env * (0.5f + 0.5f * sin(t * 4 + i * 2.1f)) * sin(3.1416f * p);
            b.diamante(px, py, sz * 0.28f, sz, col(CREAM, al));
            b.diamante(px, py, sz, sz * 0.28f, col(CREAM, al));
        }

        float S = k * 1.9f, ty = cy - 4 * S;                          // ornamento sob o título
        float oy = cy + 5.5f * S + 6 * k, ol = 70 * k * rev;
        b.seg(cx, oy, cx - ol, oy, 1.2f, col(GOLD, 0.9f * env * ss(0.8f, 1.4f, t)), col(GOLD, 0));
        b.seg(cx, oy, cx + ol, oy, 1.2f, col(GOLD, 0.9f * env * ss(0.8f, 1.4f, t)), col(GOLD, 0));
        b.diamante(cx, oy, 4 * k, 4 * k, col(WHITE, 0.95f * env * ss(0.8f, 1.4f, t)));
        b.flush();

        // 3) textos (normal)
        RenderSystem.defaultBlendFunc();
        Font f = mc.font;
        int len = titulo.length();
        float sp = lerp(8f, 1.5f, rev), tot = 0;
        for (int i = 0; i < len; i++) tot += f.width(titulo.substring(i, i + 1)) + (i < len - 1 ? sp : 0);
        float x0 = cx - tot * S / 2, acc = 0, sweep = -0.3f + 1.6f * fr((t - 1.2f) / 2.2f);
        for (int i = 0; i < len; i++) {
            String ch = titulo.substring(i, i + 1);
            float cw = f.width(ch), la = cl((t - 0.35f - 0.09f * i) / 0.35f), e = eo(la);
            float pos = len <= 1 ? 0.5f : (float) i / (len - 1);
            float mix = cl(1 - Math.abs(pos - sweep) / 0.18f);
            int r = (int) lerp(255, 255, mix), gg = (int) lerp(226, 255, mix), bb = (int) lerp(140, 255, mix);
            int al = (int) (la * env * 255);
            if (al >= 8) {
                float sc = S * (1 + 0.35f * (1 - e));
                g.pose().pushPose();
                g.pose().translate(x0 + acc * S, ty - (1 - e) * 10 * S, 0);
                g.pose().scale(sc, sc, 1);
                int glow = ((int) (al * 0.35f) << 24) | 0xFFBE3C;
                g.drawString(f, ch, -1, 0, glow, false);
                g.drawString(f, ch, 1, 0, glow, false);
                g.drawString(f, ch, 0, -1, glow, false);
                g.drawString(f, ch, 0, 1, glow, false);
                g.drawString(f, ch, 0, 0, (al << 24) | (r << 16) | (gg << 8) | bb, false);
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
            g.drawString(f, sub, 0, 0, (sa << 24) | CREAM, true);
            g.pose().popPose();
        }
        RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ peças
    private static void raios(Buf b, float cx, float cy, int n, float len, float rot, float ha, float a, int rgb, float t) {
        for (int i = 0; i < n; i++) {
            float ang = rot + i * TAU / n;
            float L = len * ((i & 1) == 0 ? 1f : 0.68f) * (0.75f + 0.25f * sin(t * 2.3f + i * 1.7f));
            b.tri(cx, cy, col(rgb, a),
                    cx + cos(ang - ha) * L, cy + sin(ang - ha) * L, col(rgb, 0),
                    cx + cos(ang + ha) * L, cy + sin(ang + ha) * L, col(rgb, 0));
        }
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

    private static void asa(Buf b, float ax, float ay, int side, float len, float a, float t) {
        int n = 9;
        for (int i = 0; i < n; i++) {
            float f = i / (float) (n - 1);
            float th = (-62 + 90 * f) * DEG + 6 * DEG * sin(t * 2.2f + i * 0.35f);
            float dx = side * cos(th), dy = sin(th);
            float L = len * (1 - 0.45f * f), wd = 0.12f * L;
            float mx = ax + dx * L * 0.35f, my = ay + dy * L * 0.35f;
            float tx = ax + dx * L, ty = ay + dy * L, px = -dy * wd, py = dx * wd;
            b.tri(ax, ay, col(CREAM, a), mx + px, my + py, col(GOLD, a * 0.8f), mx - px, my - py, col(GOLD, a * 0.8f));
            b.tri(mx + px, my + py, col(GOLD, a * 0.8f), tx, ty, col(GOLD, 0), mx - px, my - py, col(GOLD, a * 0.8f));
        }
    }

    private static void sons(float t) {
        while (somIdx < CT.length && t >= CT[somIdx]) { tocar(SoundEvents.AMETHYST_BLOCK_CHIME, CP[somIdx], 0.8f); somIdx++; }
        if (!fimTocado && t >= DUR - 0.9f) { fimTocado = true; tocar(SoundEvents.BEACON_DEACTIVATE, 1.9f, 0.4f); }
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
