package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * AVISO REAL v3 — "Decreto da Coroa" (~6.5 s). Realeza de verdade, mas organizada (o centro é sempre a coroa e a mensagem).
 *  - COROA IMPERIAL bem desenhada: capa de veludo carmesim, borda de ARMINHO, aro de ouro com rubi/safiras/esmeraldas e pérolas,
 *    dois ARCOS com pérolas, orbe e cruz no topo; desce do alto com ar de cerimônia, reflexo corre pelo aro e as joias cintilam
 *  - FAIXA DE CETIM carmesim (com cantos de fita em "rabo de andorinha") que se DESENROLA a partir do centro, bordas de ouro
 *    e arminho; a mensagem é carimbada em ouro, com reflexo que passa pelas letras
 *  - os fios dourados do aviso original (com pontas quadradas) emolduram, agora com FLORES-DE-LIS
 *  - LOUROS dourados crescem sob a faixa; aura dourada, raios lentos e poeira de ouro
 *  - sem confete, sem cortinas, sem poluição visual
 *
 * USO: modBus.addListener(RealAvisoFx::registrar);
 *      RealAvisoFx.iniciar("AVISO", mensagem);  RealAvisoFx.parar();  RealAvisoFx.particulasMundo(p);
 */
public final class RealAvisoFx {
    private static final float DUR = 6.5f;
    private static final int GD = 0xB4781E, GM = 0xFFC83C, GL = 0xFFEB96, GW = 0xFFF8D8, GR = 0x3C2305, CRIM = 0x8C1226, CRIMD = 0x5A0818, CRIML = 0xC8284A,
            ERM = 0xF4F0EC, ERMD = 0xD6D0CC, PURP = 0x2A0A3C, RUBY = 0xE01C3C, SAFI = 0x2850C8, ESME = 0x28B450, PEARL = 0xF5F0FA;

    /** true = sem brilhos piscando. */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String titulo = "AVISO", msg = "";
    private static boolean s0, s1, s2, s3, s4;

    private RealAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_real"), RealAvisoFx::render);
    }

    /** (4.9.9) mesma assinatura dos outros FX: título em cima, mensagem na faixa. */
    public static void iniciar(String t, String mensagem) {
        titulo = (t == null || t.isEmpty()) ? "AVISO" : t;
        msg = (mensagem == null || mensagem.isEmpty()) ? "" : mensagem;
        inicio = System.nanoTime();
        s0 = s1 = s2 = s3 = s4 = false;
    }

    /** (4.9.9) corta o FX na hora (troca de aviso / sair do mundo). */
    public static void parar() {
        inicio = -1;
        s0 = s1 = s2 = s3 = s4 = false;
    }

    /** SERVIDOR: faíscas douradas e fogos discretos. */
    public static void particulasMundo(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        lv.sendParticles(ParticleTypes.WAX_ON, p.getX(), p.getY() + 1.4, p.getZ(), 50, 1.0, 1.0, 1.0, 0.0);
        lv.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 12, 0.8, 0.8, 0.8, 0.02);
        lv.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.2, p.getZ(), 15, 0.6, 0.8, 0.6, 0.25);
    }

    private static int ouro(float v, float a) { return col(v < 0.5f ? mix(GD, GM, v * 2) : mix(GM, GL, (v - 0.5f) * 2), a); }

    // ------------------------------------------------------------------ render
    /** (4.9.9) BLINDAGEM: FX de cliente NUNCA derruba o jogo. */
    private static void render(GuiGraphics g, DeltaTracker dt) {
        try {
            desenhar(g, dt);
        } catch (Throwable falha) {
            inicio = -1;
            try { com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc(); com.mojang.blaze3d.systems.RenderSystem.enableDepthTest(); } catch (Throwable ignorada) { }
            dev.rptag.RPTagMod.LOGGER.error("RP Tag: falha no FX REAL (o jogo segue)", falha);
        }
    }

    private static void desenhar(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t >= DUR) { inicio = -1; return; }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float k = Math.min(w, h) / 240f, cx = w / 2f, cy = h * 0.5f;
        float env = ss(0, 0.4f, t) * (1 - ss(DUR - 0.9f, DUR, t));
        float fa = env;                                                                           // opacidade dos elementos
        sons(t);

        Font f = mc.font;
        int len = msg.length();
        float sp = 1.5f, tot = 0;
        float[] cw = new float[len], xs = new float[len];
        for (int i = 0; i < len; i++) { cw[i] = f.width(msg.substring(i, i + 1)); tot += cw[i] + (i < len - 1 ? sp : 0); }
        float S = Math.min(k * 1.9f, w * 0.74f / Math.max(1, tot)), x0 = cx - tot * S / 2, acc = 0;
        for (int i = 0; i < len; i++) { xs[i] = x0 + acc * S; acc += cw[i] + sp; }
        float bcy = cy + 14 * k, hh = 11 * k;                                                      // centro e meia-altura da faixa
        float hwB = Math.min(w * 0.42f, Math.max(tot * S / 2 + 18 * k, 60 * k));
        float ty = bcy - 4 * S;
        float cd = eo(cl((t - 0.4f) / 1.2f));                                                      // descida da coroa (sem quique)
        float u = 27 * k, crownYb = lerp(cy - 150 * k, cy - 44 * k, cd);                           // y da base da coroa
        float ca = ss(0.4f, 0.9f, t) * fa, pulse = 0.5f + 0.5f * sin(t * 1.8f);

        preparar(g);
        Buf b = new Buf(g.pose().last().pose());
        b.rect(0, 0, w, h, col(PURP, 0.40f * env));
        b.vinheta(w, h, Math.min(w, h) * 0.36f, 0x0E0418, 0.70f * env);
        b.flush();

        aditivo();                                                                                 // aura, raios, poeira
        b = new Buf(g.pose().last().pose());
        float ay = crownYb - 0.8f * u;
        b.brilho(cx, ay, 2.6f * u, GM, (0.20f + 0.05f * pulse) * ca);
        b.brilho(cx, ay, 1.2f * u, GL, 0.22f * ca);
        for (int i = 0; i < 16; i++) {
            float an = i * TAU / 16 + t * 0.07f, L = (1.8f + 1.8f * rnd(i, 1)) * u * ss(0.9f, 1.8f, t), wd = 0.045f;
            b.tri(cx + cos(an - wd) * 1.1f * u, ay + sin(an - wd) * 1.1f * u, col(GL, 0.18f * ca), cx + cos(an + wd) * 1.1f * u, ay + sin(an + wd) * 1.1f * u, col(GL, 0.18f * ca),
                    cx + cos(an) * (1.1f * u + L), ay + sin(an) * (1.1f * u + L), col(GM, 0));
        }
        b.brilho(cx, bcy, hwB * 1.05f, GM, 0.10f * fa * ss(1.4f, 2.4f, t));
        int NM = reduzido ? 12 : 26;
        for (int i = 0; i < NM; i++) {
            float p = fr(t * (0.05f + 0.07f * rnd(i, 3)) + rnd(i, 4)), px = rnd(i, 5) * w + sin(t * 0.8f + i) * 9, py = h * (1.02f - p * 1.04f), sz = (0.8f + 1.5f * rnd(i, 6)) * k * 0.8f;
            b.diamante(px, py, sz * 0.6f, sz, col(i % 2 == 0 ? GL : GM, fa * ss(1.0f, 1.8f, t) * sin(3.1416f * p) * 0.9f));
        }
        b.flush();

        normal();
        b = new Buf(g.pose().last().pose());

        // fios dourados (do aviso original) com pontas quadradas e flores-de-lis
        float half = Math.min(w * 0.46f, Math.max(hwB + 26 * k, 90 * k)) * eo(cl((t - 1.2f) / 0.8f));
        fio(b, cx, bcy - hh - 17 * k, half, 1, fa, k);
        fio(b, cx, bcy + hh + 14 * k, half, -1, fa, k);

        // louros sob a faixa
        float lg = eo(cl((t - 2.4f) / 1.0f));
        louro(b, cx, bcy + hh + 2 * k, -1, hwB * 0.95f, 22 * k, lg, fa, k);
        louro(b, cx, bcy + hh + 2 * k, 1, hwB * 0.95f, 22 * k, lg, fa, k);

        // faixa de cetim
        float prog = eo(cl((t - 1.5f) / 0.8f)), tp = eo(cl((t - 2.0f) / 0.7f));
        faixa(b, cx, bcy, hwB * prog, hh, tp, fa, k, t, hwB);

        // flores-de-lis nas pontas da faixa
        float fl = eo(cl((t - 2.3f) / 0.5f));
        float fx = hwB + 22 * k * tp + 14 * k;
        if (fl > 0.01f) { lis(b, cx - fx, bcy, 8.5f * k * fl, fa); lis(b, cx + fx, bcy, 8.5f * k * fl, fa); }

        // coroa
        coroa(b, cx, crownYb, u, ca, t);
        b.flush();

        aditivo();                                                                                 // reflexos sobre ouro, joias e cetim
        b = new Buf(g.pose().last().pose());
        float sh = fr((t - 1.6f) / 2.6f);
        if (t > 1.6f) {
            float px = cx - 1.0f * u + 2.0f * u * sh;                                              // brilho corre pelo aro de ouro da coroa
            b.quad(px - 0.14f * u, crownYb - 0.48f * u, col(GW, 0), px, crownYb - 0.48f * u, col(GW, 0.55f * ca), px + 0.10f * u, crownYb - 0.14f * u, col(GW, 0.55f * ca), px - 0.04f * u, crownYb - 0.14f * u, col(GW, 0));
            b.quad(px, crownYb - 0.48f * u, col(GW, 0.55f * ca), px + 0.14f * u, crownYb - 0.48f * u, col(GW, 0), px + 0.24f * u, crownYb - 0.14f * u, col(GW, 0), px + 0.10f * u, crownYb - 0.14f * u, col(GW, 0.55f * ca));
            float bx = cx - hwB + 2 * hwB * fr((t - 2.2f) / 3.0f);                                 // reflexo de cetim na faixa
            if (prog > 0.9f) b.quad(bx - 5 * k, bcy - hh, col(GW, 0), bx, bcy - hh, col(GW, 0.22f * fa), bx + 8 * k, bcy + hh, col(GW, 0.22f * fa), bx - 2 * k, bcy + hh, col(GW, 0));
        }
        if (!reduzido) {
            float[][] jw = {{0, -0.31f}, {-0.52f, -0.31f}, {0.52f, -0.31f}, {0, -1.41f}};
            for (int i = 0; i < jw.length; i++) {
                float tw = (float) Math.pow(Math.max(0, sin(t * 2.3f + i * 1.9f)), 10);
                if (tw < 0.06f || t < 1.0f) continue;
                float s2 = (0.30f + 0.20f * rnd(i, 7)) * u * tw, jx = cx + jw[i][0] * u, jy = crownYb + jw[i][1] * u;
                b.diamante(jx, jy, s2 * 0.08f, s2, col(WHITE, ca * tw)); b.diamante(jx, jy, s2, s2 * 0.08f, col(WHITE, ca * tw));
            }
        }
        b.flush();

        // texto em ouro
        normal();

        // (4.9.9) TÍTULO em cima da faixa (regra: a mensagem fica EMBAIXO do título)
        if (!titulo.isEmpty()) {
            float tl0 = ss(1.1f, 1.6f, t) * fa;
            if (tl0 > 0.02f) {
                float spT = 3.2f, totT = 0;
                for (int i = 0; i < titulo.length(); i++) totT += f.width(titulo.substring(i, i + 1)) + (i < titulo.length() - 1 ? spT : 0);
                float ST = Math.min(k * 1.25f, w * 0.62f / Math.max(1, totT)), xT = cx - totT * ST / 2, aT = 0;
                float yT = bcy - hh - 32f * k;
                int alT = (int) (tl0 * 255);
                for (int i = 0; i < titulo.length(); i++) {
                    String ch = titulo.substring(i, i + 1);
                    g.pose().pushPose();
                    g.pose().translate(xT + aT * ST, yT, 0);
                    g.pose().scale(ST, ST, 1);
                    int borda = (alT << 24) | 0x2A0A0E;
                    g.drawString(f, ch, -1, 0, borda, false); g.drawString(f, ch, 1, 1, borda, false);
                    g.drawString(f, ch, 1, 0, borda, false); g.drawString(f, ch, -1, 1, borda, false);
                    g.drawString(f, ch, 0, 0, (alT << 24) | GL, false);
                    g.pose().popPose();
                    aT += f.width(ch) + spT;
                }
            }
        }

        float shine = -0.3f + 1.6f * fr((t - 2.8f) / 2.0f);
        // (4.9.9) o carimbo letra-por-letra acelera em mensagem longa: antes,
        // 0,06 s por letra fazia a última letra aparecer DEPOIS do FX terminar.
        float passo = Math.min(0.06f, 2.2f / Math.max(1, len));
        for (int i = 0; i < len; i++) {
            float lt = cl((t - 2.4f - passo * i) / 0.3f);
            int a8 = (int) (lt * fa * 255);
            if (a8 < 8) continue;
            float pos2 = len <= 1 ? 0.5f : (float) i / (len - 1), mxv = cl(1 - Math.abs(pos2 - shine) / 0.15f);
            int c = mix(GL, WHITE, mxv);
            float scl = S * (1 + 0.28f * (1 - eo(lt)));
            String ch = msg.substring(i, i + 1);
            g.pose().pushPose();
            g.pose().translate(xs[i] + cw[i] * S / 2 - cw[i] * scl / 2, ty - 4 * (scl - S), 0);
            g.pose().scale(scl, scl, 1);
            int dark = (a8 << 24) | 0x3C0A14;
            g.drawString(f, ch, -1, 0, dark, false); g.drawString(f, ch, 1, 1, dark, false);
            g.drawString(f, ch, 0, -1, dark, false); g.drawString(f, ch, 0, 1, dark, false);
            g.drawString(f, ch, 0, 0, (a8 << 24) | c, false);
            g.pose().popPose();
        }
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ coroa
    /** (cx, yb): centro e y da base (borda de baixo do arminho); u = meia-largura. */
    private static void coroa(Buf b, float cx, float yb, float u, float a, float t) {
        if (a < 0.01f) return;
        // 1) capa de veludo (domo) atrás de tudo
        int n = 32;
        for (int i = 0; i < n; i++) {
            float t0 = 3.1416f + i * 3.1416f / n, t1 = 3.1416f + (i + 1) * 3.1416f / n;
            b.tri(cx, yb - 0.80f * u, col(CRIML, 0.97f * a), cx + 0.88f * u * cos(t0), yb + (-0.48f + 0.66f * sin(t0)) * u, col(CRIMD, 0.97f * a), cx + 0.88f * u * cos(t1), yb + (-0.48f + 0.66f * sin(t1)) * u, col(CRIMD, 0.97f * a));
        }
        b.tri(cx, yb - 0.80f * u, col(CRIM, 0.97f * a), cx + 0.88f * u, yb - 0.48f * u, col(CRIMD, 0.97f * a), cx - 0.88f * u, yb - 0.48f * u, col(CRIMD, 0.97f * a));
        b.elipse(cx - 0.28f * u, yb - 0.98f * u, 1, 0, 0.20f * u, 0.10f * u, col(WHITE, 0.16f * a));                       // brilho de cetim
        // 2) arcos com pérolas
        for (int s = -1; s <= 1; s += 2) {
            float lx = cx + s * 0.92f * u, ly = yb - 0.48f * u;
            for (int j = 1; j <= 14; j++) {
                float th = j / 14f * 1.5708f, nx = cx + s * 0.92f * u * cos(th), ny = yb + (-0.48f - 0.82f * sin(th)) * u;
                b.seg(lx, ly, nx, ny, 0.15f * u, ouro(0.12f, a), ouro(0.12f, a));
                b.seg(lx, ly, nx, ny, 0.105f * u, ouro(0.55f, a), ouro(0.55f, a));
                b.seg(lx - s * 0.018f * u, ly - 0.018f * u, nx - s * 0.018f * u, ny - 0.018f * u, 0.032f * u, ouro(0.97f, 0.9f * a), ouro(0.97f, 0.9f * a));
                lx = nx; ly = ny;
            }
            for (int j : new int[]{3, 6, 9, 12}) {
                float th = j / 14f * 1.5708f, px = cx + s * 0.92f * u * cos(th), py = yb + (-0.48f - 0.82f * sin(th)) * u;
                b.circ(px, py, 0.052f * u, col(PEARL, a)); b.circ(px - 0.015f * u, py - 0.016f * u, 0.018f * u, col(WHITE, 0.9f * a));
            }
        }
        // 3) orbe e cruz no topo
        float my = yb - 1.41f * u;
        b.circ(cx, my, 0.15f * u, ouro(0.15f, a)); b.circ(cx, my, 0.125f * u, ouro(0.6f, a));
        b.seg(cx - 0.125f * u, my, cx + 0.125f * u, my, 0.02f * u, ouro(0.1f, a), ouro(0.1f, a));
        b.circ(cx - 0.04f * u, my - 0.04f * u, 0.03f * u, ouro(0.98f, 0.9f * a));
        float cy1 = my - 0.13f * u;                                                                        // cruz pattée
        b.rect(cx - 0.035f * u, cy1 - 0.34f * u, 0.07f * u, 0.34f * u, ouro(0.6f, a));
        b.rect(cx - 0.13f * u, cy1 - 0.22f * u, 0.26f * u, 0.07f * u, ouro(0.6f, a));
        b.tri(cx - 0.035f * u, cy1 - 0.34f * u, ouro(0.6f, a), cx + 0.035f * u, cy1 - 0.34f * u, ouro(0.6f, a), cx, cy1 - 0.40f * u, ouro(0.9f, a));
        b.tri(cx - 0.13f * u, cy1 - 0.22f * u, ouro(0.6f, a), cx - 0.13f * u, cy1 - 0.15f * u, ouro(0.6f, a), cx - 0.19f * u, cy1 - 0.185f * u, ouro(0.9f, a));
        b.tri(cx + 0.13f * u, cy1 - 0.22f * u, ouro(0.6f, a), cx + 0.13f * u, cy1 - 0.15f * u, ouro(0.6f, a), cx + 0.19f * u, cy1 - 0.185f * u, ouro(0.9f, a));
        b.circ(cx, cy1 - 0.185f * u, 0.028f * u, col(RUBY, a));
        // 4) aro de ouro com joias
        b.quad(cx - 0.98f * u, yb - 0.48f * u, ouro(0.80f, a), cx + 0.98f * u, yb - 0.48f * u, ouro(0.55f, a), cx + 0.98f * u, yb - 0.14f * u, ouro(0.18f, a), cx - 0.98f * u, yb - 0.14f * u, ouro(0.30f, a));
        b.quad(cx - 0.20f * u, yb - 0.48f * u, ouro(0.80f, 0), cx, yb - 0.48f * u, ouro(0.98f, 0.55f * a), cx, yb - 0.14f * u, ouro(0.80f, 0.55f * a), cx - 0.20f * u, yb - 0.14f * u, ouro(0.8f, 0));
        b.quad(cx, yb - 0.48f * u, ouro(0.98f, 0.55f * a), cx + 0.20f * u, yb - 0.48f * u, ouro(0.8f, 0), cx + 0.20f * u, yb - 0.14f * u, ouro(0.8f, 0), cx, yb - 0.14f * u, ouro(0.80f, 0.55f * a));
        b.seg(cx - 0.98f * u, yb - 0.48f * u, cx + 0.98f * u, yb - 0.48f * u, 0.03f * u, ouro(0.10f, a), ouro(0.10f, a));
        b.seg(cx - 0.98f * u, yb - 0.14f * u, cx + 0.98f * u, yb - 0.14f * u, 0.03f * u, ouro(0.10f, a), ouro(0.10f, a));
        b.seg(cx - 0.96f * u, yb - 0.445f * u, cx + 0.96f * u, yb - 0.445f * u, 0.014f * u, ouro(0.98f, 0.8f * a), ouro(0.98f, 0.8f * a));
        joia(b, cx, yb - 0.31f * u, 0.13f * u, 0.17f * u, RUBY, a);
        for (int s = -1; s <= 1; s += 2) {
            joia(b, cx + s * 0.52f * u, yb - 0.31f * u, 0.10f * u, 0.13f * u, SAFI, a);
            joia(b, cx + s * 0.80f * u, yb - 0.31f * u, 0.07f * u, 0.09f * u, ESME, a);
            for (float px : new float[]{0.27f, 0.68f}) { b.circ(cx + s * px * u, yb - 0.31f * u, 0.04f * u, col(PEARL, a)); b.circ(cx + s * px * u - 0.012f * u, yb - 0.322f * u, 0.014f * u, col(WHITE, 0.9f * a)); }
            float fx = cx + s * 0.62f * u, fy = yb - 0.54f * u;                                            // fleurons sobre o aro
            b.diamante(fx, fy - 0.06f * u, 0.06f * u, 0.13f * u, ouro(0.65f, a)); b.circ(fx, fy - 0.20f * u, 0.032f * u, ouro(0.9f, a));
        }
        // 5) arminho
        b.rect(cx - 1.00f * u, yb - 0.14f * u, 2.0f * u, 0.24f * u, col(ERM, a));
        b.rect(cx - 1.00f * u, yb + 0.04f * u, 2.0f * u, 0.06f * u, col(ERMD, 0.7f * a));
        b.circ(cx - 1.0f * u, yb - 0.02f * u, 0.12f * u, col(ERM, a)); b.circ(cx + 1.0f * u, yb - 0.02f * u, 0.12f * u, col(ERM, a));
        for (float px = -0.90f; px <= 0.91f; px += 0.20f) {
            b.elipse(cx + px * u, yb - 0.03f * u, 1, 0, 0.032f * u, 0.045f * u, col(0x14101A, a));
            for (int q = -1; q <= 1; q++) b.seg(cx + px * u + q * 0.03f * u, yb + 0.00f * u, cx + px * u + q * 0.05f * u, yb + 0.075f * u, 0.022f * u, col(0x14101A, a), col(0x14101A, a));
        }
    }

    /** joia lapidada: losango com faceta clara e engaste de ouro. */
    private static void joia(Buf b, float x, float y, float rx, float ry, int rgb, float a) {
        b.diamante(x, y, rx * 1.28f, ry * 1.28f, ouro(0.30f, a));
        b.diamante(x, y, rx, ry, col(mix(rgb, BLACK_, 0.35f), a));
        b.tri(x - rx, y, col(mix(rgb, WHITE, 0.30f), a), x, y - ry, col(mix(rgb, WHITE, 0.55f), a), x, y, col(rgb, a));
        b.tri(x, y - ry, col(mix(rgb, WHITE, 0.55f), a), x + rx, y, col(rgb, a), x, y, col(mix(rgb, BLACK_, 0.20f), a));
        b.tri(x, y, col(rgb, a), x + rx, y, col(mix(rgb, BLACK_, 0.25f), a), x, y + ry, col(mix(rgb, BLACK_, 0.4f), a));
        b.diamante(x - rx * 0.28f, y - ry * 0.32f, rx * 0.16f, ry * 0.16f, col(WHITE, 0.85f * a));
    }
    private static final int BLACK_ = 0x000000, WHITE = 0xFFFFFF;

    // ------------------------------------------------------------------ faixa, fio, lis, louro
    private static void faixa(Buf b, float cx, float cy, float hw, float hh, float tp, float a, float k, float t, float hwFull) {
        if (hw < 1) return;
        float tl = 22 * k * tp, dy = 5 * k, xl = cx - hw, xr = cx + hw;
        for (int s = -1; s <= 1; s += 2) {                                                         // rabos de andorinha (atrás)
            float xe = cx + s * hw, y1 = cy - hh + dy, y2 = cy + hh + dy, xo = xe + s * tl, no = s * 7 * k;
            if (tl > 1) {
                b.quad(xe, y1, col(CRIMD, a), xo, y1, col(CRIMD, a), xo - no, (y1 + y2) / 2, col(0x40040F, a), xe, (y1 + y2) / 2, col(0x40040F, a));
                b.quad(xe, (y1 + y2) / 2, col(0x40040F, a), xo - no, (y1 + y2) / 2, col(0x40040F, a), xo, y2, col(CRIMD, a), xe, y2, col(CRIMD, a));
                b.seg(xe, y1, xo, y1, 0.9f * k, ouro(0.5f, a), ouro(0.5f, a)); b.seg(xe, y2, xo, y2, 0.9f * k, ouro(0.5f, a), ouro(0.5f, a));
                b.seg(xo, y1, xo - no, (y1 + y2) / 2, 0.9f * k, ouro(0.5f, a), ouro(0.5f, a)); b.seg(xo - no, (y1 + y2) / 2, xo, y2, 0.9f * k, ouro(0.5f, a), ouro(0.5f, a));
                b.tri(xe, cy + hh, col(0x300310, a), xe + s * 5 * k, cy + hh + dy, col(0x300310, a), xe, cy + hh + dy, col(0x300310, a));       // dobra da fita
            }
        }
        b.quad(xl, cy - hh, col(CRIML, 0.98f * a), xr, cy - hh, col(CRIM, 0.98f * a), xr, cy + hh, col(CRIMD, 0.98f * a), xl, cy + hh, col(CRIMD, 0.98f * a));       // cetim
        b.quad(xl, cy - hh, col(WHITE, 0.10f * a), xr, cy - hh, col(WHITE, 0.10f * a), xr, cy - hh * 0.2f, col(WHITE, 0), xl, cy - hh * 0.2f, col(WHITE, 0));
        for (float px = xl + 12 * k; px < xr - 6 * k; px += 18 * k) b.quad(px, cy - hh, col(0x000000, 0), px + 5 * k, cy - hh, col(0x000000, 0.05f * a), px + 5 * k, cy + hh, col(0x000000, 0.05f * a), px, cy + hh, col(0x000000, 0));
        b.seg(xl, cy - hh + 1.6f * k, xr, cy - hh + 1.6f * k, 1.0f * k, ouro(0.65f, a), ouro(0.65f, a));
        b.seg(xl, cy + hh - 1.6f * k, xr, cy + hh - 1.6f * k, 1.0f * k, ouro(0.65f, a), ouro(0.65f, a));
        b.seg(xl, cy - hh + 3.4f * k, xr, cy - hh + 3.4f * k, 0.5f * k, ouro(0.3f, 0.8f * a), ouro(0.3f, 0.8f * a));
        b.seg(xl, cy + hh - 3.4f * k, xr, cy + hh - 3.4f * k, 0.5f * k, ouro(0.3f, 0.8f * a), ouro(0.3f, 0.8f * a));
        for (int s = -1; s <= 1; s += 2) { b.rect((s < 0 ? xl : xr) - 1.2f * k, cy - hh - 1.5f * k, 2.4f * k, 2 * hh + 3 * k, ouro(0.55f, a)); b.rect((s < 0 ? xl : xr) - 0.5f * k, cy - hh - 1.5f * k, 1f * k, 2 * hh + 3 * k, ouro(0.95f, a)); }
        for (int s = -1; s <= 1; s += 2) {                                                         // arminho em cima e embaixo
            float ey = s < 0 ? cy - hh - 3.2f * k : cy + hh;
            b.rect(xl, ey, 2 * hw, 3.2f * k, col(ERM, a)); b.rect(xl, ey + (s < 0 ? 2.4f * k : 0), 2 * hw, 0.8f * k, col(ERMD, 0.8f * a));
            for (float px = xl + 6 * k; px < xr - 3 * k; px += 9 * k) { b.elipse(px, ey + 1.5f * k, 1, 0, 0.9f * k, 1.2f * k, col(0x14101A, a)); b.seg(px, ey + (s < 0 ? 2.4f * k : 0.4f * k), px, ey + (s < 0 ? 3.2f * k : 2.8f * k), 0.5f * k, col(0x14101A, a), col(0x14101A, a)); }
        }
        float rc = 3.4f * k;                                                                       // rosetas de ouro nos cantos
        for (int s = -1; s <= 1; s += 2) { b.circ((s < 0 ? xl : xr) + s * -4 * k, cy, rc, ouro(0.4f, a)); b.circ((s < 0 ? xl : xr) + s * -4 * k, cy, rc * 0.62f, ouro(0.85f, a)); b.circ((s < 0 ? xl : xr) + s * -4 * k, cy, rc * 0.28f, col(RUBY, a)); }
    }

    /** fio dourado do aviso original: linha em degradê, pontas quadradas, losango no centro. */
    private static void fio(Buf b, float cx, float y, float half, int dir, float a, float k) {
        if (half < 1) return;
        b.seg(cx, y, cx - half, y, 1.0f * k, ouro(0.9f, a), ouro(0.45f, a));
        b.seg(cx, y, cx + half, y, 1.0f * k, ouro(0.9f, a), ouro(0.45f, a));
        b.seg(cx - half * 0.82f, y + dir * 2.2f * k, cx + half * 0.82f, y + dir * 2.2f * k, 0.5f * k, ouro(0.15f, 0.6f * a), ouro(0.15f, 0.6f * a));
        for (int s = -1; s <= 1; s += 2) { b.rect(cx + s * half - 1.6f * k, y - 1.6f * k, 3.2f * k, 3.2f * k, ouro(0.5f, a)); b.rect(cx + s * half - 0.7f * k, y - 0.7f * k, 1.4f * k, 1.4f * k, ouro(0.95f, a)); }
        b.diamante(cx, y, 3 * k, 3 * k, ouro(0.9f, a)); b.diamante(cx, y, 1.4f * k, 1.4f * k, ouro(0.15f, a));
    }

    /** flor-de-lis dourada. (x,y)=centro; s=tamanho (meia-altura). */
    private static void lis(Buf b, float x, float y, float s, float a) {
        if (s < 0.5f) return;
        for (int sd = -1; sd <= 1; sd += 2) {
            float ux = cos(sd * 0.62f), uy = sin(sd * 0.62f);
            b.elipse(x + sd * 0.42f * s, y - 0.18f * s, ux, -uy * 0 + uy, 0.46f * s, 0.22f * s, ouro(0.25f, a));
            b.elipse(x + sd * 0.42f * s, y - 0.18f * s, ux, uy, 0.40f * s, 0.17f * s, ouro(0.62f, a));
            b.elipse(x + sd * 0.55f * s, y + 0.22f * s, 1, 0, 0.20f * s, 0.14f * s, ouro(0.4f, a));
        }
        b.elipse(x, y - 0.30f * s, 0, 1, 0.78f * s, 0.20f * s, ouro(0.25f, a));
        b.elipse(x, y - 0.30f * s, 0, 1, 0.72f * s, 0.15f * s, ouro(0.65f, a));
        b.rect(x - 0.42f * s, y + 0.22f * s, 0.84f * s, 0.14f * s, ouro(0.45f, a));
        b.rect(x - 0.42f * s, y + 0.22f * s, 0.84f * s, 0.05f * s, ouro(0.95f, a));
        b.tri(x - 0.12f * s, y + 0.36f * s, ouro(0.4f, a), x + 0.12f * s, y + 0.36f * s, ouro(0.4f, a), x, y + 0.80f * s, ouro(0.3f, a));
        b.circ(x, y - 1.02f * s, 0.07f * s, ouro(0.9f, a));
    }

    /** ramo de louro em arco sob a faixa; cresce do centro para as pontas. */
    private static void louro(Buf b, float cx, float cy, int side, float rx, float ry, float g, float a, float k) {
        if (g < 0.01f) return;
        int n = 11;
        float lx = cx, ly = cy + ry;
        for (int j = 1; j <= n; j++) {
            float u = j / (float) n;
            if (u > g) break;
            float th = 1.5708f - u * 1.35f, px = cx + side * rx * cos(th) * 1.0f, py = cy + ry * sin(th);
            b.seg(lx, ly, px, py, 0.9f * k, ouro(0.35f, a), ouro(0.35f, a));
            float tx = side * -sin(th) * rx * -1, ty = ry * cos(th) * -1, tl = Math.max(1, (float) Math.hypot(tx, ty)); tx /= tl; ty /= tl;
            float tau = (float) Math.atan2(ty, tx);
            for (int sg = -1; sg <= 1; sg += 2) {
                float la = tau + sg * 0.75f, ux = cos(la), uy = sin(la), L = 6.5f * k * (1 - 0.35f * u);
                b.elipse(px + ux * L / 2, py + uy * L / 2, ux, uy, L / 2, L * 0.20f, ouro(sg < 0 ? 0.55f : 0.78f, 0.95f * a));
            }
            lx = px; ly = py;
        }
    }

    private static void sons(float t) {
        if (!s0) { s0 = true; tocar(SoundEvents.BELL_BLOCK, 0.6f, 0.9f); }
        if (!s1 && t >= 1.2f) { s1 = true; tocar(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 0.8f); }
        if (!s2 && t >= 1.6f) { s2 = true; tocar(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.8f); tocar(SoundEvents.WOOL_BREAK, 0.8f, 0.5f); }
        if (!s3 && t >= 2.4f) { s3 = true; tocar(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.7f); tocar(SoundEvents.STONE_PLACE, 0.8f, 0.4f); }
        if (!s4 && t >= DUR - 1.2f) { s4 = true; tocar(SoundEvents.BELL_BLOCK, 0.8f, 0.5f); }
    }
}
