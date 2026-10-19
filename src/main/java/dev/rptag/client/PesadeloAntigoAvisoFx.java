package dev.rptag.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;

/**
 * AVISO PESADELO — "Sono Sem Descanso" (~6 s)
 *  - a visão fecha e abre como uma respiração (túnel que "respira")
 *  - tentáculos/mãos de sombra rastejam das bordas, com garras nas pontas
 *  - 16 olhos abrem pela tela em tempos diferentes, piscam e SEGUEM o título; um olho gigante vigia por cima
 *  - chuvisco de estática (barras que piscam)
 *  - título trêmulo e torto, com visão dupla (duas sombras orbitando) e falhas de sumiço
 *  - som: caixinha de música desafinada + coração lento + grito distante
 *
 * USO (adaptado ao RP Tag, 3.96.0 — VISUAL IDÊNTICO ao protótipo):
 *   camada:     PesadeloAntigoAvisoFx.registrar(e);   (ClientGuiLayers)
 *               ESTILO 11 = "pesadelo2" (/rp admin avisar @a pesadelo2 ...)
 *   disparo:    PesadeloAntigoAvisoFx.iniciar("AVISO", mensagem)
 *               (ClientEffects.startScreenText, estilo 11 pesadelo2 —
 *                título fixo "AVISO", mensagem configurável no subtítulo)
 *   servidor:   particulas de mundo via RPCommands.particulasPesadelo
 *               (server-safe; a Escuridão opcional do protótipo fica
 *                DESLIGADA — afetaria o gameplay, não só a tela)
 */
public final class PesadeloAntigoAvisoFx {
    private static final float DUR = 6.0f, TAU = 6.2831853f, DEG = (float) Math.PI / 180f;
    private static final int NIGHT = 0x12001C, TINT = 0x1E002D, DARKP = 0x28083C, PURP = 0x6E2896, SCLERA = 0xD7CD8C, IRIS = 0xBE2814, PUP = 0x050005, LID = 0x1E0028, WHITE = 0xFFFFFF;
    private static final float[] CT = {1.0f, 1.35f, 1.9f, 2.1f, 2.9f, 3.4f, 3.7f};
    private static final float[] CP = {0.89f, 0.84f, 0.75f, 0.84f, 0.67f, 0.75f, 0.63f};   // melodia desafinada
    private static final float[] HB = {1.4f, 2.8f, 4.2f};

    /** true = menos olhos/tentáculos e sem estática. */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String titulo = "AVISO", sub = "";
    private static int ctIdx, hbIdx;
    private static boolean shriek;

    private PesadeloAntigoAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_pesadelo2"), PesadeloAntigoAvisoFx::render);
    }

    public static void iniciar(String t, String s) {
        titulo = (t == null || t.isEmpty()) ? "AVISO" : t;
        sub = s == null ? "" : s;
        inicio = System.nanoTime();
        ctIdx = 0; hbIdx = 0; shriek = false;
        tocar(SoundEvents.ENDERMAN_SCREAM, 0.4f, 0.5f);
    }

    /** SERVIDOR. escuridao=true aplica o efeito Escuridão por 5 s (opcional: afeta o jogo, não só a tela). */
    public static void particulasMundo(ServerPlayer p, boolean escuridao) {
        ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.5, z, 30, 1.2, 0.8, 1.2, 0.03);
        lv.sendParticles(ParticleTypes.SQUID_INK, x, y + 1, z, 20, 0.8, 0.8, 0.8, 0.02);
        lv.sendParticles(ParticleTypes.SMOKE, x, y + 0.2, z, 40, 1.5, 0.1, 1.5, 0.01);
        if (escuridao) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
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
        float rev = eo(cl((t - 0.1f) / 1.2f));
        float breathe = 0.5f + 0.5f * sin(t * 1.6f);
        int frame = (int) (t * 30);
        sons(t);

        g.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Buf b = new Buf(g.pose().last().pose());

        // 1) tom + vinheta que respira
        b.quad(0, 0, col(TINT, 0.25f * env), w, 0, col(TINT, 0.25f * env), w, h, col(TINT, 0.25f * env), 0, h, col(TINT, 0.25f * env));
        float d = Math.min(w, h) * (0.30f + 0.12f * breathe), va = 0.92f * env;
        b.quad(0, 0, col(NIGHT, va), w, 0, col(NIGHT, va), w, d, col(NIGHT, 0), 0, d, col(NIGHT, 0));
        b.quad(0, h, col(NIGHT, va), w, h, col(NIGHT, va), w, h - d, col(NIGHT, 0), 0, h - d, col(NIGHT, 0));
        b.quad(0, 0, col(NIGHT, va), 0, h, col(NIGHT, va), d, h, col(NIGHT, 0), d, 0, col(NIGHT, 0));
        b.quad(w, 0, col(NIGHT, va), w, h, col(NIGHT, va), w - d, h, col(NIGHT, 0), w - d, 0, col(NIGHT, 0));

        // 2) estática
        if (!reduzido) {
            for (int i = 0; i < 10; i++) {
                if (rnd(i, frame / 2) <= 0.8f) continue;
                float y = rnd(i, 41 + frame / 2) * h, hh = (0.5f + 2.5f * rnd(i, 42)) * k, a = 0.25f * env;
                int c = col((i & 1) == 0 ? 0xC8B4FF : WHITE, a);
                b.quad(0, y, c, w, y, c, w, y + hh, c, 0, y + hh, c);
            }
        }

        // 3) tentáculos das bordas
        int NT = reduzido ? 6 : 12;
        for (int i = 0; i < NT; i++) {
            int side = i % 4;
            float fq = ((i / 4) + 0.5f) / (NT / 4f), sx, sy;
            if (side == 0) { sx = w * fq; sy = -6 * k; } else if (side == 1) { sx = w * fq; sy = h + 6 * k; }
            else if (side == 2) { sx = -6 * k; sy = h * fq; } else { sx = w + 6 * k; sy = h * fq; }
            float gr = eo(cl((t - 0.3f - 0.06f * i) / 1.2f));
            tentaculo(b, sx, sy, cx, cy, Math.min(w, h) * (0.32f + 0.12f * rnd(i, 12)), t, i, gr, k, 0.95f * env);
        }

        // 4) olhos pequenos que seguem o título
        int NE = reduzido ? 8 : 16;
        for (int i = 0; i < NE; i++) {
            float an = rnd(i, 1) * TAU, dist = (75 + 150 * rnd(i, 2)) * k;
            float ex = cx + cos(an) * dist, ey = cy + sin(an) * dist * 0.8f;
            if (ex < 10 * k || ex > w - 10 * k || ey < 10 * k || ey > h - 10 * k) continue;
            float tOpen = 0.6f + 1.8f * rnd(i, 3), open = ss(tOpen, tOpen + 0.35f, t);
            float per = 2.5f + 3 * rnd(i, 4), ph = fr(t / per + rnd(i, 5));
            if (ph < 0.06f) open *= Math.abs(ph / 0.03f - 1);
            float wI = (7 + 11 * rnd(i, 6)) * k;
            float lx = cx - ex, ly = cy - ey, ll = Math.max(1, (float) Math.hypot(lx, ly));
            olho(b, ex, ey, wI, wI * 0.55f * open, lx / ll + 0.15f * sin(t * 2 + i), ly / ll, env * 0.9f, k);
        }
        float bo = ss(1.2f, 1.8f, t);                                  // olho gigante
        olho(b, cx, cy - 44 * k, 30 * k, 14 * k * bo, 0.6f * cos(t * 0.7f), 0.5f * sin(t * 0.9f), env, k * 1.6f);
        b.flush();

        // 5) texto (torto, tremendo, visão dupla)
        Font f = mc.font;
        float S = k * 1.9f, ty = cy - 4 * S;
        int len = titulo.length();
        float sp = 2.5f, tot = 0;
        for (int i = 0; i < len; i++) tot += f.width(titulo.substring(i, i + 1)) + (i < len - 1 ? sp : 0);
        float x0 = cx - tot * S / 2, acc = 0;
        for (int i = 0; i < len; i++) {
            String ch = titulo.substring(i, i + 1);
            float cw = f.width(ch), la = cl((t - 0.5f - 0.12f * i) / 0.4f);
            int a8 = (int) (la * env * 255);
            boolean some = t > 1.5f && rnd(i, (int) (t * 12)) > 0.93f;
            if (a8 >= 8 && !some) {
                float ang = 6 * sin(t * 2.3f + i * 1.7f), sc = S * (1 + 0.06f * sin(t * 3 + i));
                float gx = cos(t * 2 + i) * 3, gy = sin(t * 2 + i) * 3;
                g.pose().pushPose();
                g.pose().translate(x0 + (acc + cw / 2) * S, ty + 4 * S + 2 * S * sin(t * 1.9f + i), 0);
                g.pose().mulPose(Axis.ZP.rotationDegrees(ang));
                g.pose().scale(sc, sc, 1);
                g.pose().translate(-cw / 2, -4, 0);
                g.drawString(f, ch, gx, gy, ((int) (a8 * 0.35f) << 24) | 0x8C3CC8, false);
                g.drawString(f, ch, -gx, -gy, ((int) (a8 * 0.35f) << 24) | 0x3CC85A, false);
                g.drawString(f, ch, 0, 0, (a8 << 24) | 0xEBE6AA, false);
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
            g.drawString(f, sub, 0, 0, (sa << 24) | 0xC8B4E6, true);
            g.pose().popPose();
        }
        RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ peças
    private static void tentaculo(Buf b, float sx, float sy, float tx, float ty, float len, float t, int i, float g, float k, float a) {
        float dx = tx - sx, dy = ty - sy, l = Math.max(1, (float) Math.hypot(dx, dy));
        dx /= l; dy /= l;
        float pxn = -dy, pyn = dx;
        int n = 14;
        float lx = sx, ly = sy, lastDx = dx, lastDy = dy;
        for (int j = 1; j <= n; j++) {
            float uj = j / (float) n, u = uj * g;
            float off = sin(t * 1.6f + j * 0.55f + i) * 18 * k * u;
            float nx = sx + dx * len * u + pxn * off, ny = sy + dy * len * u + pyn * off;
            float wd = (7 * (1 - uj) + 0.8f) * k * 0.7f;
            b.seg(lx, ly, nx, ny, wd, col(DARKP, a), col(DARKP, a));
            b.seg(lx, ly, nx, ny, wd * 0.3f, col(PURP, a * 0.35f), col(PURP, a * 0.35f));
            lastDx = nx - lx; lastDy = ny - ly; lx = nx; ly = ny;
        }
        float ll = Math.max(1e-3f, (float) Math.hypot(lastDx, lastDy)), bx = lastDx / ll, by = lastDy / ll;
        for (int c = -1; c <= 1; c++) {                                // garras
            float an = c * 0.55f, ux = bx * cos(an) - by * sin(an), uy = bx * sin(an) + by * cos(an);
            b.seg(lx, ly, lx + ux * 6 * k * g, ly + uy * 6 * k * g, 1.4f * k * 0.5f, col(DARKP, a), col(DARKP, 0));
        }
    }

    /** olho amendoado: esclera, íris vertical, pupila de fenda, brilho e veias. px/py em [-1,1] = para onde olha. */
    private static void olho(Buf b, float cx, float cy, float wI, float hh, float px, float py, float a, float k) {
        if (hh < 0.6f) { b.seg(cx - wI, cy, cx + wI, cy, 1.2f * k * 0.6f, col(LID, a), col(LID, a)); return; }
        int n = 14;
        int sc = col(SCLERA, 0.92f * a), lid = col(LID, a);
        for (int i = 0; i < n; i++) {
            float u0 = -1 + 2f * i / n, u1 = -1 + 2f * (i + 1) / n, y0 = hh * (1 - u0 * u0), y1 = hh * (1 - u1 * u1);
            b.quad(cx + u0 * wI, cy - y0, sc, cx + u1 * wI, cy - y1, sc, cx + u1 * wI, cy + y1, sc, cx + u0 * wI, cy + y0, sc);
        }
        for (int sg = -1; sg <= 1; sg += 2) {                          // veias
            b.seg(cx + sg * wI * 0.95f, cy, cx + sg * wI * 0.55f, cy - hh * 0.25f, 0.8f * k * 0.5f, col(0xAA1414, 0.6f * a), col(0xAA1414, 0));
            b.seg(cx + sg * wI * 0.95f, cy, cx + sg * wI * 0.55f, cy + hh * 0.30f, 0.8f * k * 0.5f, col(0xAA1414, 0.6f * a), col(0xAA1414, 0));
        }
        float ox = cx + px * wI * 0.28f, oy = cy + py * hh * 0.2f;
        b.elipse(ox, oy, 1, 0, wI * 0.20f, hh * 0.72f, col(IRIS, a));
        b.elipse(ox, oy, 1, 0, wI * 0.035f + 0.3f, hh * 0.68f, col(PUP, a));
        b.circ(ox - wI * 0.06f, oy - hh * 0.3f, Math.max(0.4f, hh * 0.1f), col(WHITE, 0.8f * a));
        for (int i = 0; i < n; i++) {                                   // contorno das pálpebras
            float u0 = -1 + 2f * i / n, u1 = -1 + 2f * (i + 1) / n, y0 = hh * (1 - u0 * u0), y1 = hh * (1 - u1 * u1);
            for (int sg = -1; sg <= 1; sg += 2)
                b.seg(cx + u0 * wI, cy + sg * y0, cx + u1 * wI, cy + sg * y1, 1.3f * k * 0.8f, lid, lid);
        }
    }

    private static void sons(float t) {
        if (!shriek && t >= 1.2f) { shriek = true; tocar(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5f, 0.5f); }
        while (ctIdx < CT.length && t >= CT[ctIdx]) { tocar(SoundEvents.AMETHYST_BLOCK_CHIME, CP[ctIdx], 0.6f); ctIdx++; }
        while (hbIdx < HB.length && t >= HB[hbIdx]) { tocar(SoundEvents.WARDEN_HEARTBEAT, 0.6f, 1.0f); hbIdx++; }
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
    private static float fr(float x) { return x - (float) Math.floor(x); }
    private static float rnd(int i, int s) { return fr((float) Math.sin(i * 12.9898 + s * 78.233) * 43758.547f); }
    private static float sin(float x) { return (float) Math.sin(x); }
    private static float cos(float x) { return (float) Math.cos(x); }
}
