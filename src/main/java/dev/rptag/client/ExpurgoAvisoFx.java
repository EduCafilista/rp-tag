package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * AVISO "HORA DO EXPURGO" (~11.8 s) — transmissão de emergência.
 *   0.0 s  a TV LIGA (linha de luz que abre), neve de estática, linhas de varredura (CRT), cabeçalho "TRANSMISSÃO DE EMERGÊNCIA" + ● REC
 *   1.3 s  o anúncio é DIGITADO: "ESTE NÃO É UM TESTE." + a sua mensagem
 *   2.6 s  CONTAGEM REGRESSIVA 5-4-3-2-1 (cada número estoura e solta uma onda vermelha, com bipe)
 *   5.4 s  clarão + SIRENE: a tela fica vermelha, luzes giram nos cantos, o título HORA DO EXPURGO despenca letra por letra
 *          (separação RGB, tremor), listras de perigo deslizam, MÁSCARAS sorridentes sobem dos cantos com olhos acesos
 *   9.6 s  a sirene morre, "QUE DEUS NOS ABENÇOE" e a TV se DESLIGA (colapsa numa linha e some)
 *
 * <p>(4.9.2) "EXPURGO" — estilo 13 do /rp admin avisar, vindo do protótipo do dono
 * (catbox 7l0m00.java). Adaptado so o encanamento (pacote, AvisoFxUtil do mod e
 * parar()), o desenho e os tempos sao os do protótipo.
 *
 * USO: ClientGuiLayers -> ExpurgoAvisoFx.registrar(e);
 *      ClientEffects.startScreenText(..., 13) -> ExpurgoAvisoFx.iniciar(mensagem);
 *      RPCommands -> ExpurgoAvisoFx.particulasMundo(p);  (opcional, no servidor)
 * (a mensagem é opcional: vazia usa o texto padrão de 12 horas)
 */
public final class ExpurgoAvisoFx {
    private static final float DUR = 11.8f, T_CONT = 2.6f, T_SLAM = 5.4f, T_FIM = 9.6f;
    private static final int RED = 0xE01414, DRED = 0x5A0000, BLOOD = 0xB40A0A, BLUE = 0x2850FF, YEL = 0xFFC800, WHITE = 0xFFFFFF, BLACK = 0x000000;

    /** true = sem clarões, sem luzes alternando e com menos estática/tremor. */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String msg = "TODOS OS CRIMES SÃO LEGAIS DURANTE 12 HORAS";
    private static final float[] EV_T = new float[40];
    private static final int[] EV_K = new int[40];
    private static int nEv, evIdx;
    static {
        for (int i = 0; i < 3; i++) add(0.3f + 0.5f * i, 0);                      // tom contínuo de emergência
        for (int n = 0; n < 5; n++) add(T_CONT + 0.55f * n, 1);                    // bipes da contagem
        add(T_SLAM, 2);
        for (int i = 0; i < 8; i++) add(T_SLAM + 0.1f + 0.6f * i, 3 + (i & 1));    // sirene alternando
        add(T_FIM + 0.2f, 5);
        for (int i = 0; i < nEv; i++) for (int j = i + 1; j < nEv; j++) if (EV_T[j] < EV_T[i]) { float tt = EV_T[i]; EV_T[i] = EV_T[j]; EV_T[j] = tt; int kk = EV_K[i]; EV_K[i] = EV_K[j]; EV_K[j] = kk; }
    }
    private static void add(float t, int k) { EV_T[nEv] = t; EV_K[nEv] = k; nEv++; }

    private ExpurgoAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_expurgo"), ExpurgoAvisoFx::render);
    }

    public static void iniciar(String mensagem) {
        msg = (mensagem == null || mensagem.isEmpty()) ? "TODOS OS CRIMES SÃO LEGAIS DURANTE 12 HORAS" : mensagem.toUpperCase();
        inicio = System.nanoTime();
        evIdx = 0;
    }

    /** (4.9.2) Encerra o aviso na hora (um aviso novo substitui este). */
    public static void parar() {
        inicio = -1;
    }

    /** SERVIDOR: sangue (redstone) caindo, fumaça e fogo vermelho. */
    public static void particulasMundo(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), x, y + 1.0, z, 60, 1.2, 0.8, 1.2, 0.15);
        lv.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.REDSTONE_BLOCK.defaultBlockState()), x, y + 3.0, z, 40, 2.0, 0.3, 2.0, 0.0);
        lv.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.3, z, 40, 1.5, 0.2, 1.5, 0.02);
        lv.sendParticles(ParticleTypes.FLAME, x, y + 0.2, z, 25, 1.2, 0.1, 1.2, 0.02);
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t >= DUR) { inicio = -1; return; }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight(), frame = (int) (t * 30);
        float k = Math.min(w, h) / 240f, cx = w / 2f, cy = h * 0.5f;
        float on = ss(0.25f, 0.6f, t), off = ss(DUR - 0.9f, DUR - 0.35f, t), env = on * (1 - off);
        float red = ss(T_SLAM, T_SLAM + 0.5f, t) * (1 - ss(T_FIM, T_FIM + 1.0f, t));         // fase da sirene
        float pulse = 0.5f + 0.5f * sin((t - T_SLAM) * TAU * 0.9f);
        float amp = (reduzido ? 0.3f : 1f) * (t > T_SLAM ? 6 * k * (float) Math.exp(-(t - T_SLAM) * 5) : 0);
        for (int n = 0; n < 5; n++) { float tau = t - (T_CONT + 0.55f * n); if (tau > 0 && tau < 0.25f) amp += (reduzido ? 0.3f : 1f) * 1.6f * k * (1 - tau / 0.25f); }
        float shx = amp * sin(t * 70), shy = amp * cos(t * 61);
        sons(t);

        Font f = mc.font;
        preparar(g);
        Buf b = new Buf(g.pose().last().pose());

        // ===== 1) fundo: TV preta, onda vermelha, estática, listras, máscaras (blend normal)
        b.rect(0, 0, w, h, col(BLACK, 0.96f * on));
        b.rect(0, 0, w, h, col(DRED, (0.20f + 0.14f * pulse) * red));
        b.vinheta(w, h, Math.min(w, h) * 0.45f, 0x120000, (0.55f + 0.3f * red) * on);
        int NR = reduzido ? 90 : 260;
        float estat = (1 - ss(1.0f, 1.8f, t)) * 0.9f + 0.10f + 0.12f * red;
        for (int i = 0; i < NR; i++) {                                                             // neve de estática
            float px = rnd(i, frame * 7 + 1) * w, py = rnd(i, frame * 7 + 2) * h, sz = (1 + 5 * rnd(i, 3)) * k * 0.5f, lum = rnd(i, frame * 7 + 4);
            b.rect(px, py, sz * 2.2f, sz, col(mix(0x707070, WHITE, lum), 0.13f * estat * on));
        }
        if (rnd(frame / 3, 9) > 0.90f || t < 1.0f && rnd(frame, 10) > 0.8f) {                   // falhas horizontais
            float gy = rnd(frame, 11) * h, gh = (1 + 5 * rnd(frame, 12)) * k;
            b.rect(0, gy, w, gh, col(WHITE, 0.08f * on)); b.rect(0, gy + gh, w, gh * 0.4f, col(RED, 0.10f * on * (0.3f + red)));
        }
        // listras de perigo que deslizam para dentro
        float faixa = ss(T_SLAM, T_SLAM + 0.7f, t) * (1 - ss(T_FIM, T_FIM + 0.8f, t)), fh = 9 * k;
        if (faixa > 0.01f) {
            hazard(b, shx, -fh + fh * 2 * faixa * 0.5f - fh * (1 - faixa), fh, t * 22 * k, w, faixa);
            hazard(b, shx, h - fh * faixa, fh, -t * 22 * k, w, faixa);
        }
        // máscaras sorridentes subindo dos cantos
        float ma = ss(T_SLAM + 1.0f, T_SLAM + 2.6f, t) * (1 - ss(T_FIM - 0.2f, T_FIM + 0.7f, t));
        float[][] MS = {{0.12f, 1.00f, 1.00f}, {0.30f, 1.08f, 0.82f}, {0.70f, 1.08f, 0.82f}, {0.88f, 1.00f, 1.00f}};
        if (ma > 0.01f) for (int i = 0; i < MS.length; i++) {
            float rise = eo(cl((t - T_SLAM - 1.0f - 0.25f * i) / 1.2f)), s = 11 * k * MS[i][2];
            mascara(b, MS[i][0] * w + shx + sin(t * 0.8f + i) * 2 * k, h * MS[i][1] - rise * 0.30f * h - 0.1f * h + sin(t * 1.1f + i * 2) * 1.5f * k + shy, s, ma, i);
        }
        b.flush();

        // ===== 2) luz (aditivo): faixa de ligar, sirene, olhos das máscaras, clarão
        aditivo();
        b = new Buf(g.pose().last().pose());
        if (t < 0.7f) {                                                                              // a TV liga: linha de luz que abre
            float u = eo(cl(t / 0.3f)), a = 1 - ss(0.3f, 0.7f, t);
            b.rect(cx - w / 2f * u, cy - 1.2f * k, w * u, 2.4f * k, col(WHITE, a));
            b.quad(0, cy, col(WHITE, 0), w, cy, col(WHITE, 0), w, cy + 14 * k * (1 - u), col(WHITE, 0), 0, cy + 14 * k * (1 - u), col(WHITE, 0));
        }
        if (red > 0.01f) {
            float ph = fr((t - T_SLAM) / 1.0f);
            float aL = reduzido ? 0.5f : (ph < 0.5f ? 1f : 0.12f), aR = reduzido ? 0.5f : (ph < 0.5f ? 0.12f : 1f);
            b.brilho(0, h * 0.08f, 0.85f * h, RED, 0.40f * aL * red);
            b.brilho(w, h * 0.08f, 0.85f * h, ph < 0.5f ? BLUE : RED, (ph < 0.5f ? 0.28f : 0.40f) * aR * red);
            for (int q = 0; q < 2; q++) {                                                             // cones de luz girando
                float ox = q == 0 ? 0 : w, an = (q == 0 ? 1 : -1) * (t * 2.2f) + (q == 0 ? 0.3f : 3.1416f - 0.3f), wd = 0.20f, L = (float) Math.hypot(w, h) * 1.2f;
                b.tri(ox, h * 0.08f, col(q == 0 ? RED : BLUE, 0.22f * red), ox + cos(an - wd) * L, h * 0.08f + sin(an - wd) * L, col(RED, 0), ox + cos(an + wd) * L, h * 0.08f + sin(an + wd) * L, col(RED, 0));
            }
            b.vinheta(w, h, Math.min(w, h) * 0.30f, RED, (0.10f + 0.22f * pulse) * red);
        }
        if (ma > 0.01f) for (int i = 0; i < MS.length; i++) {                                         // olhos acesos
            float rise = eo(cl((t - T_SLAM - 1.0f - 0.25f * i) / 1.2f)), s = 11 * k * MS[i][2];
            float mx0 = MS[i][0] * w + shx, my0 = h * MS[i][1] - rise * 0.30f * h - 0.1f * h + shy;
            for (int e = -1; e <= 1; e += 2) b.brilho(mx0 + e * 0.38f * s, my0 - 0.22f * s, 0.9f * s, RED, 0.55f * ma * (0.7f + 0.3f * pulse));
        }
        if (!reduzido && t > T_SLAM && t < T_SLAM + 0.18f) b.rect(0, 0, w, h, col(WHITE, (1 - (t - T_SLAM) / 0.18f) * 0.55f));
        for (int n = 0; n < 5; n++) {                                                                 // onda vermelha a cada número
            float tau = t - (T_CONT + 0.55f * n);
            if (tau > 0 && tau < 0.5f) { float u = eo(tau / 0.5f); b.anel(cx, cy + 6 * k, 18 * k + 70 * k * u, 72, (5 * (1 - u) + 1) * k, 0, RED, (1 - u) * 0.7f * env); }
        }
        b.flush();

        // ===== 3) texto
        normal();
        float fim = ss(T_FIM, T_FIM + 0.5f, t);
        // cabeçalho
        float ha = ss(0.9f, 1.3f, t) * (1 - off);
        if (ha > 0.01f) {
            g.pose().pushPose();
            int a8 = (int) (ha * 255);
            Buf hb = new Buf(g.pose().last().pose());
            hb.rect(0, 0, w, 11 * k, col(BLACK, 0.55f * ha)); hb.rect(0, 11 * k, w, 1.2f * k, col(RED, 0.9f * ha));
            hb.flush();
            if (fr(t * 1.0f) < 0.6f) txt(g, f, "● REC", 5 * k, 2.6f * k, (a8 << 24) | RED, k * 0.9f);
            txt(g, f, "TRANSMISSÃO DE EMERGÊNCIA", 24 * k, 2.6f * k, (a8 << 24) | WHITE, k * 0.9f);
            float secs = Math.max(0, 43200 - Math.max(0, t - T_SLAM) * 240);
            String rel = String.format("%02d:%02d:%02d", (int) (secs / 3600), (int) (secs % 3600 / 60), (int) (secs % 60));
            txt(g, f, rel, w - (f.width(rel) * k * 0.9f) - 5 * k, 2.6f * k, (a8 << 24) | (red > 0.3f ? RED : WHITE), k * 0.9f);
            g.pose().popPose();
        }
        // anúncio digitado
        float an = ss(T_CONT - 0.3f, T_CONT, t);
        if (t > 1.3f && t < T_SLAM + 0.05f) {
            String l1 = "ESTE NÃO É UM TESTE.", l2 = msg, l3 = "O EXPURGO COMEÇA EM";
            float fa = (1 - ss(T_SLAM - 0.15f, T_SLAM, t)) * on;
            int s1 = Math.min(l1.length(), Math.max(0, (int) ((t - 1.3f) * 22))), s2 = Math.min(l2.length(), Math.max(0, (int) ((t - 1.3f - l1.length() / 22f - 0.2f) * 28)));
            float S1 = Math.min(k * 2.2f, w * 0.86f / Math.max(1, f.width(l1))), S2 = Math.min(k * 1.5f, w * 0.88f / Math.max(1, f.width(l2)));
            float yy = cy - 56 * k + an * 12 * k;
            txtc(g, f, l1.substring(0, s1), cx, yy, S1, ((int) (fa * 255) << 24) | WHITE, false);
            txtc(g, f, l2.substring(0, s2), cx, yy + 18 * k, S2, ((int) (fa * 255) << 24) | 0xFFD2D2, false);
            if (t > T_CONT - 0.4f) txtc(g, f, l3, cx, yy + 34 * k, Math.min(k * 1.5f, w * 0.7f / f.width(l3)), ((int) (fa * ss(T_CONT - 0.4f, T_CONT, t) * 255) << 24) | RED, false);
            if (t > T_CONT && t < T_CONT + 0.55f * 5) {                                                // dígitos grandes
                int n = 5 - (int) ((t - T_CONT) / 0.55f);
                float p = fr((t - T_CONT) / 0.55f), sd = k * 7.2f * (1 + 0.7f * (1 - eo(cl(p / 0.22f))));
                int ad = (int) ((1 - ss(0.82f, 1f, p)) * fa * 255);
                if (ad > 8) txtc(g, f, String.valueOf(n), cx + (rnd(frame, 21) - 0.5f) * 0.6f * k, cy + 6 * k + 10 * k, sd, (ad << 24) | RED, true);
            }
        }
        // título
        if (t > T_SLAM) {
            String ti = "HORA DO EXPURGO";
            int len = ti.length();
            float tot = 0; float[] cw = new float[len];
            for (int i = 0; i < len; i++) { cw[i] = f.width(ti.substring(i, i + 1)); tot += cw[i] + 1; }
            float S = Math.min(k * 4.6f, w * 0.92f / Math.max(1, tot)), x0 = cx - tot * S / 2 + shx, ty = cy - 4 * S - 8 * k + shy, acc = 0;
            float glit = (!reduzido && (fr(t / 1.3f) < 0.09f) && t > T_SLAM + 1) ? 1 : 0;
            float tA = (1 - fim) * 1f;
            for (int i = 0; i < len; i++) {
                String ch = ti.substring(i, i + 1);
                float lt = cl((t - T_SLAM - 0.045f * i) / 0.22f), e = eo(lt);
                int a8 = (int) (lt * tA * 255);
                if (a8 >= 8 && !ch.equals(" ")) {
                    float sc = S * (1 + 2.6f * (1 - e)), jx = (rnd(i, frame) - 0.5f) * (glit > 0 ? 6 : 0.8f) , jy = (rnd(i, frame + 9) - 0.5f) * (glit > 0 ? 4 : 0.6f);
                    int cc = mix(WHITE, 0xFF2A22, ss(0.0f, 0.9f, t - T_SLAM - 0.045f * i));
                    g.pose().pushPose();
                    g.pose().translate(x0 + acc * S + cw[i] * S / 2 - cw[i] * sc / 2, ty - 4 * (sc - S), 0);
                    g.pose().scale(sc, sc, 1);
                    int sh = (a8 << 24) | 0x000000;
                    for (int ox2 = -1; ox2 <= 1; ox2++) for (int oy2 = -1; oy2 <= 1; oy2++) if (ox2 != 0 || oy2 != 0) { g.drawString(f, ch, ox2 * 2, oy2 * 2, sh, false); }
                    float gs = (glit > 0 ? 5 : 1.6f);
                    txt(g, f, ch, jx - gs, jy, ((int) (a8 * 0.55f) << 24) | 0xFF0000, 1f);
                    txt(g, f, ch, jx + gs, jy, ((int) (a8 * 0.45f) << 24) | 0x00E5FF, 1f);
                    g.drawString(f, ch, 0, 0, (a8 << 24) | cc, false);
                    g.pose().popPose();
                }
                acc += cw[i] + 1;
            }
            float ma2 = ss(T_SLAM + 0.9f, T_SLAM + 1.4f, t) * (1 - fim);
            txtc(g, f, msg, cx + shx, ty + 12 * S + 4 * k + shy, Math.min(k * 1.6f, w * 0.86f / Math.max(1, f.width(msg))), ((int) (ma2 * 255) << 24) | 0xFFE6E6, true);
        }
        if (fim > 0.01f) txtc(g, f, "QUE DEUS NOS ABENÇOE", cx, cy + 20 * k, Math.min(k * 2.0f, w * 0.8f / f.width("QUE DEUS NOS ABENÇOE")), ((int) (ss(T_FIM, T_FIM + 0.6f, t) * (1 - off) * 255) << 24) | WHITE, true);

        // ===== 4) CRT por cima de tudo: varredura, cantos de câmera, TV desligando
        g.flush();
        preparar(g);
        b = new Buf(g.pose().last().pose());
        int stp = Math.max(2, Math.round(1.5f * k));
        for (float y = 0; y < h; y += stp) b.rect(0, y, w, stp * 0.45f, col(BLACK, 0.20f * on));
        float cb = 12 * k, cw2 = 1.4f * k;
        int cc = col(WHITE, 0.75f * ss(0.8f, 1.2f, t) * (1 - off));
        b.rect(6 * k, 15 * k, cb, cw2, cc); b.rect(6 * k, 15 * k, cw2, cb, cc);
        b.rect(w - 6 * k - cb, 15 * k, cb, cw2, cc); b.rect(w - 6 * k - cw2, 15 * k, cw2, cb, cc);
        b.rect(6 * k, h - 6 * k - cw2, cb, cw2, cc); b.rect(6 * k, h - 6 * k - cb, cw2, cb, cc);
        b.rect(w - 6 * k - cb, h - 6 * k - cw2, cb, cw2, cc); b.rect(w - 6 * k - cw2, h - 6 * k - cb, cw2, cb, cc);
        if (off > 0.01f) {                                                                           // TV desliga: colapsa numa linha e some
            float u = eo(off), hh2 = h / 2f * u;
            b.rect(0, 0, w, hh2, col(BLACK, 1f)); b.rect(0, h - hh2, w, hh2, col(BLACK, 1f));
        }
        b.flush();
        aditivo();
        b = new Buf(g.pose().last().pose());
        if (off > 0.01f) {
            float ll = 1 - ss(DUR - 0.35f, DUR, t), u = eo(off);
            b.rect(cx - w / 2f * (1 - ss(DUR - 0.35f, DUR, t) * 0.97f), cy - 1.2f * k, w * (1 - ss(DUR - 0.35f, DUR, t) * 0.97f), 2.4f * k, col(WHITE, ll * u));
        }
        b.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ peças
    /** drawString em posição fracionária, com escala própria (px de tela). */
    private static void txt(GuiGraphics g, Font f, String s, float x, float y, int color, float sc) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(sc, sc, 1);
        g.drawString(f, s, 0, 0, color, false);
        g.pose().popPose();
    }

    /** texto centrado em x, com topo em y e escala sc; contorno preto opcional. */
    private static void txtc(GuiGraphics g, Font f, String s, float cx, float y, float sc, int color, boolean contorno) {
        if (s.isEmpty() || ((color >>> 24) & 255) < 6) return;
        float w = f.width(s) * sc;
        g.pose().pushPose();
        g.pose().translate(cx - w / 2, y, 0);
        g.pose().scale(sc, sc, 1);
        if (contorno) {
            int sh = ((color >>> 24) << 24);
            g.drawString(f, s, -1, 0, sh, false); g.drawString(f, s, 1, 0, sh, false); g.drawString(f, s, 0, -1, sh, false); g.drawString(f, s, 0, 1, sh, false);
        }
        g.drawString(f, s, 0, 0, color, false);
        g.pose().popPose();
    }

    /** faixa de perigo amarela e preta em diagonal, deslizando. */
    private static void hazard(Buf b, float sx, float y, float hgt, float off, int w, float a) {
        b.rect(0, y, w, hgt, col(BLACK, 0.92f * a));
        float sw = hgt * 0.9f, per = sw * 2;
        float o = ((off % per) + per) % per;
        for (float x = -hgt * 2 + o; x < w + hgt; x += per)
            b.quad(x + sx, y + hgt, col(YEL, a), x + sw + sx, y + hgt, col(YEL, a), x + sw + hgt + sx, y, col(YEL, a), x + hgt + sx, y, col(YEL, a));
        b.rect(0, y, w, 0.8f * hgt / 9, col(YEL, a)); b.rect(0, y + hgt - 0.8f * hgt / 9, w, 0.8f * hgt / 9, col(YEL, a));
    }

    /** máscara sorridente: capuz escuro, rosto pálido, olhos em fenda, sorriso largo e bochechas. (x,y) = centro do rosto; s = unidade. */
    private static void mascara(Buf b, float x, float y, float s, float a, int seed) {
        int hood = col(0x0C080A, 0.97f * a);
        b.quad(x - 2.2f * s, y + 5f * s, hood, x + 2.2f * s, y + 5f * s, hood, x + 1.5f * s, y + 1.1f * s, hood, x - 1.5f * s, y + 1.1f * s, hood);   // ombros
        b.elipse(x, y - 0.1f * s, 1, 0, 1.38f * s, 1.72f * s, hood);                                                                               // capuz
        b.elipse(x, y, 1, 0, 0.98f * s, 1.32f * s, col(0xDCD0BC, a));                                                                             // rosto
        b.elipse(x - 0.10f * s, y - 0.12f * s, 1, 0, 0.78f * s, 1.05f * s, col(0xEFE6D2, 0.55f * a));                                              // luz
        b.elipse(x + 0.35f * s, y + 0.35f * s, 1, 0, 0.55f * s, 0.80f * s, col(0x9A8C78, 0.28f * a));                                               // sombra
        for (int e = -1; e <= 1; e += 2) {
            b.quad(x + e * 0.12f * s, y - 0.30f * s, col(0x120C0E, a), x + e * 0.66f * s, y - 0.46f * s, col(0x120C0E, a),
                    x + e * 0.62f * s, y - 0.20f * s, col(0x120C0E, a), x + e * 0.16f * s, y - 0.10f * s, col(0x120C0E, a));                           // olho em fenda
            b.circ(x + e * 0.64f * s, y + 0.20f * s, 0.20f * s, col(0xC85050, 0.55f * a));                                                          // bochecha
        }
        float lx = x - 0.58f * s, ly = y + 0.46f * s;
        for (int i = 1; i <= 14; i++) {                                                                                                            // sorriso
            float u = i / 14f, nx = x + (-0.58f + 1.16f * u) * s, ny = y + 0.46f * s + 0.40f * s * (1 - (2 * u - 1) * (2 * u - 1)) - 0.18f * s * (float) Math.pow(Math.abs(2 * u - 1), 6);
            b.seg(lx, ly, nx, ny, 0.12f * s, col(0x120C0E, a), col(0x120C0E, a));
            lx = nx; ly = ny;
        }
        b.seg(x - 0.58f * s, y + 0.46f * s, x - 0.66f * s, y + 0.38f * s, 0.10f * s, col(0x120C0E, a), col(0x120C0E, a));
        b.seg(x + 0.58f * s, y + 0.46f * s, x + 0.66f * s, y + 0.38f * s, 0.10f * s, col(0x120C0E, a), col(0x120C0E, a));
        b.seg(x + 0.15f * s, y - 0.9f * s, x + 0.30f * s, y - 0.25f * s, 0.05f * s, col(0x6A5A4A, 0.7f * a), col(0x6A5A4A, 0.3f * a));              // rachadura
    }

    private static void sons(float t) {
        while (evIdx < nEv && t >= EV_T[evIdx]) {
            int kd = EV_K[evIdx++];
            switch (kd) {
                case 0 -> tocar(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 0.6f);
                case 1 -> tocar(SoundEvents.BELL_BLOCK, 1.8f, 0.8f);
                case 2 -> { tocar(SoundEvents.ENDER_DRAGON_GROWL, 0.4f, 1.0f); tocar(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.7f); tocar(SoundEvents.BELL_BLOCK, 0.5f, 1.0f); }
                case 3 -> tocar(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.9f, 0.9f);
                case 4 -> tocar(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3f, 0.9f);
                case 5 -> { tocar(SoundEvents.ENDERMAN_TELEPORT, 0.5f, 0.8f); tocar(SoundEvents.FIRE_EXTINGUISH, 0.6f, 0.8f); }
                default -> { }
            }
        }
    }
}
