package dev.rptag.client;

import dev.rptag.RPTagMod;

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
 * AVISO INFERNO — "Fornalha" (~6 s)
 *  - explosão: clarão + onda de choque laranja
 *  - MURALHA DE FOGO no rodapé (64 línguas em 2 fileiras, cada uma com núcleo amarelo/branco)
 *  - REDEMOINHO DE FOGO girando atrás do texto (8 braços em espiral)
 *  - METEOROS riscando a tela na diagonal, com rastro
 *  - ~100 brasas subindo em zigue-zague, fumaça escura no teto, brilho de forno nas bordas
 *  - o texto nasce BRANCO-INCANDESCENTE e esfria para laranja, com chamas lambendo cada letra
 *
 * USO: modBus.addListener(InfernoAvisoFx::registrar);  InfernoAvisoFx.iniciar("AVISO", "fadfafad");  InfernoAvisoFx.particulasMundo(p);
 */
public final class InfernoAvisoFx {
    private static final float DUR = 6.0f;
    /** (4.3.0) contorno escuro do texto no modo video (marrom-carvao). */
    private static final int CONTORNO_VIDEO = 0x200800;
    private static final int RED = 0xFF4600, ORG = 0xFF8C1E, YEL = 0xFFD23C, HOTW = 0xFFFAD2, SMOKE = 0x140804;

    /** true = sem clarão, menos meteoros/brasas. */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String msg = "AVISO";
    private static String sub = "";
    private static int crackIdx;
    private static boolean fimTocado, impacto;
    /** (4.3.0) true = fundo vem do video do Blender (VideoFx). */
    private static boolean video;

    private InfernoAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_inferno"), InfernoAvisoFx::render);
    }

    public static void iniciar(String titulo, String mensagem) {
        msg = (titulo == null || titulo.isEmpty()) ? "AVISO" : titulo;
        sub = (mensagem == null) ? "" : mensagem;
        inicio = System.nanoTime();
        crackIdx = 0; fimTocado = false; impacto = false;
        // (4.3.0) tenta o VIDEO renderizado no Blender; reduzido/fxVideo off/sem video -> procedural
        FxComando.garantirConfig();
        video = FxVideoPolitica.permitir(FxConfig.video(), reduzido) && VideoFx.abrir("inferno");
        RPTagMod.LOGGER.info("RP Tag: FORNALHA em {}", video ? "VIDEO" : "procedural");
    }

    /** (4.3.0) Encerra o aviso na hora (um aviso novo substitui este). */
    public static void parar() {
        if (video) VideoFx.fechar();
        video = false;
        inicio = -1;
    }

    /** SERVIDOR: tornado de chamas subindo em espiral + respingos de lava + fumaça. */
    public static void particulasMundo(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        for (int i = 0; i < 60; i++) {
            double a = i * 0.6, r = 1.5 + i * 0.04;
            lv.sendParticles(ParticleTypes.FLAME, x + Math.cos(a) * r, y + 0.1 + i * 0.05, z + Math.sin(a) * r, 0, 0, 0.12, 0, 1.0);
        }
        lv.sendParticles(ParticleTypes.LAVA, x, y + 1, z, 20, 1.2, 0.3, 1.2, 0);
        lv.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 2.5, z, 25, 1.0, 0.4, 1.0, 0.02);
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t >= DUR) {
            inicio = -1;
            if (video) VideoFx.fechar();
            video = false;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h * 0.45f, k = Math.min(w, h) / 240f, diag = (float) Math.hypot(w, h);
        float env = ss(0, 0.35f, t) * (1 - ss(DUR - 1.2f, DUR, t));
        float rev = eo(cl((t - 0.05f) / 0.9f));
        float flick = 0.8f + 0.2f * sin(t * 13f) * sin(t * 7.3f);

        // layout do texto (precisa antes: as chamas das letras dependem das posições)
        Font f = mc.font;
        int len = msg.length();
        float sp = 1.5f, tot = 0;
        float[] cw = new float[len], xs = new float[len];
        for (int i = 0; i < len; i++) { cw[i] = f.width(msg.substring(i, i + 1)); tot += cw[i] + (i < len - 1 ? sp : 0); }
        float S = Math.min(k * 1.9f, w * 0.86f / Math.max(1, tot)), ty = cy - 4 * S, x0 = cx - tot * S / 2, acc = 0;
        for (int i = 0; i < len; i++) { xs[i] = x0 + acc * S; acc += cw[i] + sp; }

        preparar(g);
        if (video) {
            // (4.3.0) fundo = VIDEO; texto, efeitos das letras e sons continuam no codigo
            VideoEstado ve = VideoFx.desenhar(g, t);
            if (ve == VideoEstado.CARREGANDO) {
                inicio = System.nanoTime(); // relogio parado ate ter quadros
                com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
                return;
            }
            if (ve == VideoEstado.FALHOU) {
                video = false; // segue procedural NESTE MESMO t
                RPTagMod.LOGGER.warn("RP Tag: video do aviso falhou — seguindo procedural");
            }
            preparar(g);
        }
        sons(t);
        // 1) fumaça + calor (normal)
        Buf b = new Buf(g.pose().last().pose());
        if (!video) {
        b.rect(0, 0, w, h, col(RED, 0.16f * env * flick));
        b.vinheta(w, h, Math.min(w, h) * 0.25f, SMOKE, 0.55f * env);
        for (int i = 0; i < 7; i++) {
            float sx = w * rnd(i, 71) + sin(t * 0.3f + i) * 60 * k, sy = h * 0.14f * rnd(i, 72) + sin(t * 0.5f + i * 2) * 10 * k;
            b.brilho(sx, sy, (60 + 50 * rnd(i, 73)) * k, SMOKE, 0.30f * env);
        }
        } // (4.3.0) fim do fundo procedural (camada 1)
        b.brilho(cx, cy, 85 * k, 0x000000, (video ? 0.45f : 0.30f) * env); // apoio de leitura do texto
        b.flush();

        // 2) fogo (aditivo)
        aditivo();
        b = new Buf(g.pose().last().pose());
        if (!video) {
        if (!reduzido && t < 0.2f) b.rect(0, 0, w, h, col(HOTW, (1 - t / 0.2f) * 0.5f));
        if (t < 0.7f) {                                                 // onda de choque
            float u = eo(t / 0.7f);
            b.anel(cx, cy, diag * 0.7f * u, 96, (12 * (1 - u) + 1) * k, 0, ORG, (1 - u) * 0.8f);
            b.anel(cx, cy, diag * 0.7f * u * 0.97f, 96, 1.5f * k, 0, HOTW, (1 - u) * 0.5f);
        }
        float ga = 0.5f * env * flick;                                  // brilho de forno nas bordas
        b.vinheta(w, h, Math.min(w, h) * 0.12f, RED, ga * 0.6f);
        b.quad(0, h, col(ORG, ga), w, h, col(ORG, ga), w, h - h * 0.35f, col(RED, 0), 0, h - h * 0.35f, col(RED, 0));

        b.brilho(cx, cy, 70 * k * rev, ORG, 0.45f * env);               // redemoinho de fogo
        for (int a = 0; a < 8; a++) {
            for (int j = 0; j < 16; j++) {
                float u = j / 15f, r = 95 * k * (0.15f + 0.95f * u) * rev;
                float ang = a * TAU / 8 + u * 2.4f - t * 1.4f;
                float sz = (7 - 5 * u) * k * (0.8f + 0.2f * sin(t * 9 + a * j));
                int c = mix(HOTW, RED, u);
                b.circ(cx + cos(ang) * r, cy + sin(ang) * r * 0.9f, sz, col(c, 0.55f * env * (1 - 0.5f * u)));
            }
        }

        for (int layer = 0; layer < 2; layer++) {                       // muralha de fogo
            int NF = 32;
            for (int i = 0; i < NF; i++) {
                float bx = (i + 0.5f + (layer == 1 ? 0.5f : 0)) / NF * w, sc = layer == 0 ? 1f : 0.7f;
                float hg = (40 + 70 * rnd(i + layer * 40, 1)) * k * rev * sc * (0.65f + 0.35f * sin(t * (5 + 7 * rnd(i, 2)) + i * 3));
                float sway = sin(t * 5 + i + layer * 2) * 8 * k;
                chama(b, bx, h + 2 * k, w / (float) NF * 1.6f * sc, hg, sway, 0.75f * env);
            }
        }

        int NM = reduzido ? 3 : 7;                                      // meteoros
        for (int i = 0; i < NM; i++) {
            float u = fr(t * 0.3f + rnd(i, 1)), hx = w * (0.4f + 0.8f * rnd(i, 2)) - u * w * 0.7f, hy = -0.15f * h + u * h * 1.3f;
            float L = (70 + 60 * rnd(i, 3)) * k, a = env * sin(3.1416f * u), dx = 0.55f, dy = -0.83f;   // cauda aponta para cima-direita
            b.seg(hx, hy, hx + dx * L, hy + dy * L, 7 * k * 0.8f, col(ORG, 0.4f * a), col(RED, 0));
            b.seg(hx, hy, hx + dx * L, hy + dy * L, 3 * k * 0.8f, col(HOTW, a), col(ORG, 0));
            b.brilho(hx, hy, 6 * k, ORG, 0.5f * a);
            b.circ(hx, hy, 2.4f * k, col(HOTW, a));
        }

        int NE = reduzido ? 50 : 100;                                   // brasas
        for (int i = 0; i < NE; i++) {
            float p = fr(t * (0.12f + 0.25f * rnd(i, 2)) + rnd(i, 3));
            float px = rnd(i, 1) * w + sin(t * (1 + rnd(i, 5)) + i) * 20 * k * p, py = h * (1.05f - p * 1.15f);
            float sz = (0.8f + 1.8f * rnd(i, 4)) * k * 0.8f, al = env * (1 - p) * (0.6f + 0.4f * sin(t * 12 + i));
            b.diamante(px, py, sz * 0.5f, sz * 1.3f, col(mix(YEL, ORG, p), al));
        }
        } // (4.3.0) fim do fundo procedural (camada 2)

        for (int i = 0; i < len; i++) {                                 // chamas lambendo cada letra
            float age = t - 0.3f - 0.06f * i;
            if (age <= 0) continue;
            for (int mm = 0; mm < 3; mm++) {
                float fx = xs[i] + cw[i] * S * (0.2f + 0.3f * mm);
                float hg = (4 + 2.5f * (0.5f + 0.5f * sin(t * 11 + i * 3 + mm * 2))) * S;
                chama(b, fx, ty + 1.5f * S, cw[i] * S * 0.4f, hg, sin(t * 8 + i + mm) * 2 * S, 0.75f * env * cl(age * 3));
            }
        }
        b.flush();

        // 3) texto de metal derretido (normal)
        normal();
        for (int i = 0; i < len; i++) {
            float age = t - 0.3f - 0.06f * i, la = cl(age / 0.3f);
            int a8 = (int) (la * env * 255);
            if (a8 < 8) continue;
            int heat = mix(HOTW, ORG, ss(0, 1.0f, age));
            int c = mix(heat, YEL, 0.5f + 0.5f * sin(t * 10 + i * 2) * ss(0.6f, 1.2f, age));
            String ch = msg.substring(i, i + 1);
            g.pose().pushPose();
            g.pose().translate(xs[i], ty + sin(t * 9 + i) * 0.5f * S + (1 - eo(la)) * 8 * S, 0);
            g.pose().scale(S, S, 1);
            int glow = ((int) (a8 * 0.40f) << 24) | 0xFF5A00;
            g.drawString(f, ch, -1, 0, glow, false); g.drawString(f, ch, 1, 0, glow, false);
            g.drawString(f, ch, 0, -1, glow, false); g.drawString(f, ch, 0, 1, glow, false);
            g.drawString(f, ch, 0, 0, (a8 << 24) | c, false);
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
                if (video) contornoEscuro(g, f, sub, 0, 0, a8s, CONTORNO_VIDEO);
                g.drawString(f, sub, 0, 0, (a8s << 24) | 0xFFD23C, true);
                g.pose().popPose();
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ peças
    /** língua de fogo de baixo para cima: camada vermelha, amarela e núcleo branco. */
    private static void chama(Buf b, float x, float baseY, float wd, float hg, float sway, float a) {
        b.tri(x - wd / 2, baseY, col(RED, a), x + wd / 2, baseY, col(RED, a), x + sway, baseY - hg, col(RED, 0));
        b.tri(x - wd * 0.3f, baseY, col(YEL, a), x + wd * 0.3f, baseY, col(YEL, a), x + sway * 0.8f, baseY - hg * 0.65f, col(YEL, 0));
        b.tri(x - wd * 0.15f, baseY, col(HOTW, a * 0.8f), x + wd * 0.15f, baseY, col(HOTW, a * 0.8f), x + sway * 0.6f, baseY - hg * 0.35f, col(HOTW, 0));
    }

    private static void sons(float t) {
        if (!impacto) { impacto = true; tocar(SoundEvents.FIRECHARGE_USE, 0.7f, 1.0f); tocar(SoundEvents.GHAST_SHOOT, 0.6f, 0.6f); tocar(SoundEvents.LAVA_AMBIENT, 0.8f, 1.0f); }
        while (t >= 0.5f + 0.45f * crackIdx && t < DUR - 0.8f) { tocar(SoundEvents.FIRE_AMBIENT, 0.85f + 0.1f * (crackIdx % 3), 0.8f); crackIdx++; }
        if (!fimTocado && t >= DUR - 0.9f) { fimTocado = true; tocar(SoundEvents.FIRE_EXTINGUISH, 0.6f, 0.8f); }
    }
}
