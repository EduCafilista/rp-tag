package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * AVISO RÚNICO — "Tradução Arcana" (~6 s)
 *  - CÍRCULO DE INVOCAÇÃO deitado no chão (elipse em perspectiva): 3 anéis, octagrama girando e 16 runas que
 *    acendem em sequência (cada uma com um sino ascendente)
 *  - PILAR de luz violeta sobe do círculo atravessando o texto; runas flutuam em espiral ao redor dele
 *  - CHUVA RÚNICA cai pelas laterais da tela, como código mágico
 *  - o TEXTO CHEGA EM LÍNGUA ENCANTADA (a fonte do encantamento do Minecraft, trocando de glifo) e vai se
 *    TRADUZINDO letra por letra, com um estouro de luz a cada letra revelada
 *
 * USO: modBus.addListener(RunicoAvisoFx::registrar);  RunicoAvisoFx.iniciar("AVISO", "runico");  RunicoAvisoFx.particulasMundo(p);
 */
public final class RunicoAvisoFx {
    private static final float DUR = 6.0f;
    private static final int VIO = 0x8C50FF, LAV = 0xD6C4FF, DEEP = 0x0C0420, CYAN = 0x96E6FF, WHITE = 0xFFFFFF;
    private static final Style ALT = Style.EMPTY.withFont(ResourceLocation.withDefaultNamespace("alt"));
    private static final float[] CP = {0.67f, 0.75f, 0.89f, 1.0f, 1.19f, 1.33f, 1.5f, 1.78f};   // pentatônica ascendente

    /** 16 runas (estilo Futhark) como traços {x1,y1,x2,y2} em [0,1]. */
    private static final float[][][] RUNAS = {
        {{.3f,0,.3f,1},{.3f,.35f,.8f,.05f},{.3f,.65f,.8f,.35f}},
        {{.2f,1,.2f,0},{.2f,0,.8f,.3f},{.8f,.3f,.8f,1}},
        {{.3f,0,.3f,1},{.3f,.25f,.75f,.5f},{.75f,.5f,.3f,.75f}},
        {{.3f,0,.3f,1},{.3f,.1f,.8f,.35f},{.3f,.4f,.8f,.65f}},
        {{.25f,0,.25f,1},{.25f,0,.75f,.25f},{.75f,.25f,.25f,.5f},{.25f,.5f,.8f,1}},
        {{.7f,.15f,.3f,.5f},{.3f,.5f,.7f,.85f}},
        {{.2f,.1f,.8f,.9f},{.8f,.1f,.2f,.9f}},
        {{.3f,0,.3f,1},{.3f,.05f,.75f,.3f},{.75f,.3f,.3f,.55f}},
        {{.25f,0,.25f,1},{.75f,0,.75f,1},{.25f,.35f,.75f,.65f}},
        {{.5f,0,.5f,1},{.2f,.35f,.8f,.6f}},
        {{.7f,0,.3f,.35f},{.3f,.35f,.7f,.65f},{.7f,.65f,.3f,1}},
        {{.5f,0,.5f,1},{.2f,.3f,.5f,0},{.8f,.3f,.5f,0}},
        {{.25f,0,.25f,1},{.25f,0,.7f,.2f},{.7f,.2f,.25f,.5f},{.25f,.5f,.75f,.75f},{.75f,.75f,.25f,1}},
        {{.2f,0,.2f,1},{.8f,0,.8f,1},{.2f,0,.5f,.3f},{.5f,.3f,.8f,0}},
        {{.3f,0,.3f,1},{.3f,0,.75f,.3f}},
        {{.15f,.1f,.15f,.9f},{.85f,.1f,.85f,.9f},{.15f,.1f,.85f,.9f},{.85f,.1f,.15f,.9f}}};

    /** true = sem chuva rúnica e sem estouros de luz. */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String msg = "AVISO";
    private static String sub = "";
    private static int chIdx;
    private static boolean abriu, fim;

    private RunicoAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_runico"), RunicoAvisoFx::render);
    }

    public static void iniciar(String titulo, String mensagem) {
        msg = (titulo == null || titulo.isEmpty()) ? "AVISO" : titulo;
        sub = (mensagem == null) ? "" : mensagem;
        inicio = System.nanoTime();
        chIdx = 0; abriu = false; fim = false;
    }

    /** SERVIDOR: as partículas ENCHANT são runas de verdade, voando em direção ao jogador. */
    public static void particulasMundo(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(ParticleTypes.ENCHANT, x, y + 1.5, z, 80, 1.5, 1.0, 1.5, 0.5);
        lv.sendParticles(ParticleTypes.WITCH, x, y + 1.0, z, 25, 0.8, 0.8, 0.8, 0.05);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            lv.sendParticles(ParticleTypes.END_ROD, x + Math.cos(a) * 2.5, y + 0.1, z + Math.sin(a) * 2.5, 0, 0, 0.05, 0, 1.0);
        }
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t >= DUR) { inicio = -1; return; }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h * 0.45f, cy0 = cy + 34 * (Math.min(w, h) / 240f), k = Math.min(w, h) / 240f;
        float env = ss(0, 0.5f, t) * (1 - ss(DUR - 1.2f, DUR, t));
        float rev = eo(cl((t - 0.1f) / 1.0f));
        sons(t);

        Font f = mc.font;
        int len = msg.length();
        float sp = 2f, tot = 0;
        float[] cw = new float[len], xs = new float[len];
        for (int i = 0; i < len; i++) { cw[i] = f.width(msg.substring(i, i + 1)); tot += cw[i] + (i < len - 1 ? sp : 0); }
        float S = Math.min(k * 1.9f, w * 0.86f / Math.max(1, tot)), ty = cy - 4 * S, x0 = cx - tot * S / 2, acc = 0;
        for (int i = 0; i < len; i++) { xs[i] = x0 + acc * S; acc += cw[i] + sp; }

        preparar(g);
        Buf b = new Buf(g.pose().last().pose());                         // 1) penumbra arcana
        b.rect(0, 0, w, h, col(DEEP, 0.40f * env));
        b.vinheta(w, h, Math.min(w, h) * 0.34f, DEEP, 0.75f * env);
        b.brilho(cx, cy, 80 * k, DEEP, 0.30f * env);
        b.flush();

        aditivo();                                                       // 2) magia
        b = new Buf(g.pose().last().pose());
        float Rc = 95 * k * rev;
        b.brilho(cx, cy0, Rc * 1.1f, VIO, 0.22f * env);                  // pilar de luz
        float pp = ss(0.4f, 1.2f, t), yt = cy0 - cy0 * pp, pw = 16 * k;
        for (int s = -1; s <= 1; s += 2) {
            b.quad(cx + s * pw, cy0, col(VIO, 0), cx, cy0, col(LAV, 0.35f * env), cx, yt, col(LAV, 0.03f * env), cx + s * pw, yt, col(VIO, 0));
            b.quad(cx + s * 4 * k, cy0, col(WHITE, 0), cx, cy0, col(WHITE, 0.45f * env), cx, yt, col(WHITE, 0.0f), cx + s * 4 * k, yt, col(WHITE, 0));
        }
        elAnel(b, cx, cy0, Rc, 90, 1.6f * k * 0.7f, VIO, 0.9f * env);
        elAnel(b, cx, cy0, Rc * 0.9f, 90, 1.0f * k * 0.6f, LAV, 0.5f * env);
        elAnel(b, cx, cy0, Rc * 0.72f, 72, 1.0f * k * 0.6f, VIO, 0.6f * env);
        for (int q = 0; q < 2; q++) {                                    // octagrama
            float rot = (q == 0 ? 1 : -1) * t * 0.35f + q * 0.3927f;
            for (int i = 0; i < 4; i++) {
                float a0 = rot + i * TAU / 4, a1 = rot + (i + 1) * TAU / 4, rr = Rc * 0.62f;
                float ax = cos(a0) * rr, ay = sin(a0) * rr, bx = cos(a1) * rr, by = sin(a1) * rr;
                b.seg(cx + ax, cy0 + ay * 0.30f, cx + bx, cy0 + by * 0.30f, 1.0f * k * 0.7f, col(LAV, 0.6f * env), col(LAV, 0.6f * env));
            }
        }
        for (int i = 0; i < 16; i++) {                                   // 16 runas no chão, acendem em sequência
            float ti = 0.5f + 0.1f * i, lit = t < ti ? 0.15f : 0.45f + 0.55f * (float) Math.exp(-(t - ti) * 2.5f) + 0.15f * sin(t * 3 - i * 0.6f);
            float th = i * TAU / 16 + t * 0.12f;
            runaChao(b, RUNAS[i], cx, cy0, th, Rc * 0.82f, 14 * k * rev, col(lit > 0.7f ? LAV : VIO, cl(lit) * env), 1.2f * k * 0.7f);
        }
        for (int i = 0; i < 14; i++) {                                   // runas flutuantes em espiral
            float p = fr(t * 0.12f + i / 14f), an = t * 1.2f + i * 1.7f, rr = (30 + 14 * sin(i)) * k;
            float px = cx + cos(an) * rr, py = cy0 - p * (cy0 + 10), front = 0.5f + 0.5f * sin(an), sz = 6 * k * (1 - 0.3f * p);
            runa(b, RUNAS[(i * 5) % 16], px, py, sz, sin(t + i) * 0.3f, col(front > 0.5f ? LAV : VIO, sin(3.1416f * p) * env * (0.4f + 0.6f * front)), 1.0f * k * 0.6f);
        }
        if (!reduzido) {                                                 // chuva rúnica
            for (int c = 0; c < 14; c++) {
                float xc = (c < 7 ? 0.02f + 0.26f * (c / 6f) : 0.72f + 0.26f * ((c - 7) / 6f)) * w;
                float head = fr(t * (0.18f + 0.2f * rnd(c, 1)) + rnd(c, 2)) * (h + 40 * k) - 10 * k;
                for (int j = 0; j < 7; j++) {
                    float py = head - j * 5 * k, a = (j == 0 ? 0.9f : 0.5f * (1 - j / 7f)) * env * 0.8f;
                    if (py < 0 || py > h) continue;
                    runa(b, RUNAS[(int) (rnd(c * 9 + j, (int) (t * 4)) * 16) % 16], xc, py, 3.2f * k, 0, col(j == 0 ? WHITE : VIO, a), 0.8f * k * 0.6f);
                }
            }
        }
        for (int i = 0; i < len; i++) {                                  // estouro de luz a cada letra traduzida
            float tr = t - (1.4f + 0.1f * i);
            if (tr > 0 && tr < 0.5f && !reduzido && msg.charAt(i) != ' ')
                b.anel(xs[i] + cw[i] * S / 2, ty + 4 * S, 3 * k + 12 * k * tr * 2, 24, 1.4f * k * 0.6f, 0, CYAN, (1 - tr * 2) * 0.9f * env);
        }
        b.flush();

        normal();                                                        // 3) texto: língua encantada -> tradução
        for (int i = 0; i < len; i++) {
            char c = msg.charAt(i);
            if (c == ' ') continue;
            float tt = t - (0.6f + 0.05f * i), tr = t - (1.4f + 0.1f * i);
            if (tt < 0) continue;
            int a8 = (int) (cl(tt / 0.3f) * env * 255);
            if (a8 < 8) continue;
            g.pose().pushPose();
            if (tr < 0) {
                Component sg = Component.literal(String.valueOf((char) ('a' + ((int) (t * 16 + i * 7)) % 26))).withStyle(ALT);
                float gwid = f.width(sg);
                g.pose().translate(xs[i] + (cw[i] - gwid) * S / 2, ty, 0);
                g.pose().scale(S, S, 1);
                g.drawString(f, sg, 0, 0, ((int) (a8 * 0.85f) << 24) | VIO, false);
            } else {
                g.pose().translate(xs[i], ty, 0);
                g.pose().scale(S, S, 1);
                float fl = (float) Math.exp(-tr * 5);
                int cc = mix(LAV, WHITE, fl), glow = ((int) (a8 * (0.35f + 0.3f * fl)) << 24) | VIO;
                String ch = String.valueOf(c);
                g.drawString(f, ch, -1, 0, glow, false); g.drawString(f, ch, 1, 0, glow, false);
                g.drawString(f, ch, 0, -1, glow, false); g.drawString(f, ch, 0, 1, glow, false);
                g.drawString(f, ch, 0, 0, (a8 << 24) | cc, false);
            }
            g.pose().popPose();
        }
        // (3.97.0) MENSAGEM do comando — SEMPRE EMBAIXO do titulo (padrao 3.96.4)
        if (!sub.isEmpty()) {
            float S2 = Math.min(k * 0.75f, w * 0.85f / Math.max(1, f.width(sub)));
            float sa2 = ss(1.0f, 1.6f, t) * env;
            int a8s = (int) (sa2 * 255);
            if (a8s >= 8) {
                g.pose().pushPose();
                g.pose().translate(cx - f.width(sub) * S2 / 2, cy + 25 * k, 0);
                g.pose().scale(S2, S2, 1);
                g.drawString(f, sub, 0, 0, (a8s << 24) | 0xD6C4FF, true);
                g.pose().popPose();
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ peças
    /** runa em tela (de frente), centro (x,y), lado "s", girada por rot. */
    private static void runa(Buf b, float[][] r, float x, float y, float s, float rot, int c, float wd) {
        float cr = cos(rot), sr = sin(rot);
        for (float[] st : r) {
            float ax = (st[0] - 0.5f) * s, ay = (st[1] - 0.5f) * s, bx = (st[2] - 0.5f) * s, by = (st[3] - 0.5f) * s;
            b.seg(x + ax * cr - ay * sr, y + ax * sr + ay * cr, x + bx * cr - by * sr, y + bx * sr + by * cr, wd, c, c);
        }
    }

    /** runa sobre o plano do chão: tangente = eixo x, raio = eixo y, achatada pela perspectiva (0.30). */
    private static void runaChao(Buf b, float[][] r, float cx, float cy0, float th, float R, float gs, int c, float wd) {
        float Px = cos(th) * R, Py = sin(th) * R, Tx = -sin(th), Ty = cos(th), Nx = cos(th), Ny = sin(th);
        for (float[] st : r) {
            float u0 = (st[0] - 0.5f) * gs, v0 = (st[1] - 0.5f) * gs, u1 = (st[2] - 0.5f) * gs, v1 = (st[3] - 0.5f) * gs;
            float ax = Px + Tx * u0 + Nx * v0, ay = Py + Ty * u0 + Ny * v0, bx = Px + Tx * u1 + Nx * v1, by = Py + Ty * u1 + Ny * v1;
            b.seg(cx + ax, cy0 + ay * 0.30f, cx + bx, cy0 + by * 0.30f, wd, c, c);
        }
    }

    private static void elAnel(Buf b, float cx, float cy0, float r, int n, float w, int rgb, float a) {
        for (int i = 0; i < n; i++) {
            float a0 = i * TAU / n, a1 = (i + 1) * TAU / n;
            b.seg(cx + cos(a0) * r, cy0 + sin(a0) * r * 0.30f, cx + cos(a1) * r, cy0 + sin(a1) * r * 0.30f, w, col(rgb, a), col(rgb, a));
        }
    }

    private static void sons(float t) {
        if (!abriu) { abriu = true; tocar(SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 1.0f); tocar(SoundEvents.BEACON_ACTIVATE, 1.3f, 0.5f); }
        while (chIdx < 8 && t >= 0.5f + 0.2f * chIdx) { tocar(SoundEvents.AMETHYST_BLOCK_CHIME, CP[chIdx], 0.8f); chIdx++; }
        float fimTrad = 1.4f + 0.1f * msg.length();
        if (!fim && t >= fimTrad) { fim = true; tocar(SoundEvents.END_PORTAL_FRAME_FILL, 1.2f, 0.8f); }
    }
}
