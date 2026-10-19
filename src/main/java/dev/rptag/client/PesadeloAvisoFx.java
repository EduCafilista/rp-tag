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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * AVISO PESADELO — "Seguido" (~6 s). Estilo: found-footage / terror minimalista.
 *  - a tela quase apaga; moldura vermelha fina + 2 linhas dividindo em terços, que piscam e falham
 *  - uma FIGURA PÁLIDA de olhos brancos aparece lá no fundo e, a cada "corte", chega mais perto (stop-motion)
 *  - a mensagem é DIGITADA letra por letra em vermelho; às vezes corrompe/duplica
 *  - coração acelerando + passos se aproximando, depois silêncio e o olhar do Enderman
 *
 * USO: modBus.addListener(PesadeloAvisoFx::registrar);  PesadeloAvisoFx.iniciar("AVISO", "VOCÊ ESTÁ SENDO SEGUIDO");
 *      PesadeloAvisoFx.particulasMundo(p, false);  // true = também aplica Escuridão por 5 s (afeta o jogo!)
 */
public final class PesadeloAvisoFx {
    private static final float DUR = 6.0f;
    private static final int RED = 0xFF2020, DRED = 0x6E0A0A, PALE = 0x9696A0;
    private static final float[] STEPS = {1.0f, 1.7f, 2.3f, 2.8f, 3.2f, 3.55f, 3.85f, 4.1f};   // passos aceleram
    private static final float[] CUT = {1.0f, 2.2f, 3.4f, 4.4f};                                // cortes da figura
    private static final float[] HEAD_SZ = {10, 16, 26, 40}, HEAD_Y = {0.20f, 0.24f, 0.28f, 0.30f};
    private static final String LIXO = "#%&?/*";

    /** true = sem tremor forte de linhas e sem corrupção de letras. */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String msg = "AVISO";
    private static String sub = "";
    private static int stepIdx, cicloAnt = -1;
    private static boolean olhar;

    private PesadeloAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_pesadelo"), PesadeloAvisoFx::render);
    }

    public static void iniciar(String titulo, String mensagem) {
        msg = (titulo == null || titulo.isEmpty()) ? "AVISO" : titulo;
        sub = (mensagem == null) ? "" : mensagem;
        inicio = System.nanoTime();
        stepIdx = 0; olhar = false; cicloAnt = -1;
        tocar(SoundEvents.WARDEN_HEARTBEAT, 0.6f, 1.0f);
    }

    /** SERVIDOR. escuridao=true aplica Escuridão por 5 s (opcional: muda o jogo, não só a tela). */
    public static void particulasMundo(ServerPlayer p, boolean escuridao) {
        ServerLevel lv = p.serverLevel();
        lv.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 0.2, p.getZ(), 40, 1.5, 0.1, 1.5, 0.01);
        lv.sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 0.5, p.getZ(), 12, 1.0, 0.6, 1.0, 0.02);
        if (escuridao) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t >= DUR) { inicio = -1; return; }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight(), frame = (int) (t * 30);
        float cx = w / 2f, cy = h * 0.5f, k = Math.min(w, h) / 240f;
        float env = ss(0, 0.6f, t) * (1 - ss(DUR - 1.0f, DUR, t));
        float beat = beat(t);
        sons(t);

        preparar(g);
        Buf b = new Buf(g.pose().last().pose());

        // 1) escuridão + vinheta de túnel
        b.rect(0, 0, w, h, col(0x000000, 0.80f * env));
        b.vinheta(w, h, Math.min(w, h) * 0.32f, 0x000000, 0.9f * env);

        // 2) moldura vermelha + linhas dos terços (piscam / falham)
        float fa = (0.55f + 0.35f * beat) * env;
        float th = 1.5f;
        b.rect(0, 0, w, th, col(DRED, fa)); b.rect(0, h - th, w, th, col(DRED, fa));
        b.rect(0, 0, th, h, col(DRED, fa)); b.rect(w - th, 0, th, h, col(DRED, fa));
        for (int i = 1; i <= 2; i++) {
            float yl = h * i / 3f;
            boolean glitch = !reduzido && rnd(i, frame / 3) > 0.92f;
            float jy = glitch ? (rnd(i, frame) - 0.5f) * 10 * k : 0;
            float la = (glitch ? 0.9f : 0.45f + 0.1f * sin(t * 3 + i)) * env;
            b.rect(0, yl + jy, w, 1.3f, col(DRED, la));
            if (glitch) b.rect(0, yl + jy + 4 * k, w * (0.2f + 0.5f * rnd(i, frame + 7)), 1.0f, col(RED, 0.35f * env));
        }

        // 3) a figura pálida (aproxima-se em cortes)
        int st = t >= CUT[3] ? 3 : t >= CUT[2] ? 2 : t >= CUT[1] ? 1 : t >= CUT[0] ? 0 : -1;
        boolean some = t > 1.0f && rnd(7, frame / 3) > 0.93f && !reduzido;       // pisca e some por um instante
        if (st >= 0 && !some) {
            float s = HEAD_SZ[st] * k, hx = cx + 0.03f * w + sin(t * 0.7f) * 0.5f * k, hy = h * HEAD_Y[st];
            float fadeIn = ss(CUT[st], CUT[st] + 0.25f, t) * env;
            b.rect(hx - 0.6f * s, hy + 0.5f * s, 1.2f * s, 1.7f * s, col(0x19191C, 0.65f * fadeIn));      // corpo
            b.rect(hx - 0.5f * s, hy - 0.55f * s, s, 1.1f * s, col(PALE, 0.55f * fadeIn));                // cabeça
            for (int e = -1; e <= 1; e += 2) {
                b.rect(hx + e * 0.22f * s - 0.1f * s, hy - 0.2f * s, 0.2f * s, 0.14f * s, col(0x000000, 0.9f * fadeIn));
                b.rect(hx + e * 0.22f * s - 0.05f * s, hy - 0.17f * s, 0.1f * s, 0.07f * s, col(0xFFFFFF, 0.95f * fadeIn));
            }
        }

        // 4) estática pontual
        if (!reduzido) {
            for (int i = 0; i < 6; i++) {
                if (rnd(i, frame / 2 + 50) <= 0.85f) continue;
                float y = rnd(i, 60 + frame / 2) * h;
                b.rect(0, y, w, (0.5f + 1.5f * rnd(i, 61)) * k * 0.6f, col(0xFFFFFF, 0.06f * env));
            }
        }
        b.flush();

        // 5) mensagem digitada
        Font f = mc.font;
        int len = msg.length();
        float tot = 0;
        float[] cw = new float[len];
        for (int i = 0; i < len; i++) { cw[i] = f.width(msg.substring(i, i + 1)); tot += cw[i] + (i < len - 1 ? 1 : 0); }
        float S = Math.min(k * 1.7f, w * 0.86f / Math.max(1, tot));
        float x0 = cx - tot * S / 2, ty = cy - 4 * S, acc = 0;
        int shown = Math.min(len, Math.max(0, (int) ((t - 0.5f) / 0.07f)));
        float gl = (t > 1.2f && fr(t / 1.7f) < 0.07f && !reduzido) ? 1f : 0f;     // rajada de glitch
        for (int i = 0; i < len; i++) {
            if (i < shown) {
                String ch = msg.substring(i, i + 1);
                if (!reduzido && t > 1.5f && rnd(i, frame / 2 + 3) > 0.985f) ch = String.valueOf(LIXO.charAt((int) (rnd(i, frame) * LIXO.length())));
                float fl = (0.85f + 0.15f * rnd(i, frame + 11)) * env;
                int a8 = (int) (fl * 255);
                g.pose().pushPose();
                g.pose().translate(x0 + acc * S, ty, 0);
                g.pose().scale(S, S, 1);
                g.drawString(f, ch, 1, 1, ((int) (a8 * 0.7f) << 24) | DRED, false);
                if (gl > 0) {
                    g.drawString(f, ch, -3, 0, ((int) (a8 * 0.5f) << 24) | 0xFF0000, false);
                    g.drawString(f, ch, 3, 0, ((int) (a8 * 0.4f) << 24) | 0x960000, false);
                }
                g.drawString(f, ch, 0, 0, (a8 << 24) | RED, false);
                g.pose().popPose();
            }
            acc += cw[i] + 1;
        }
        if (shown < len || fr(t * 1.5f) < 0.6f) {                      // cursor
            float cxp = x0 + accTo(cw, shown) * S;
            Buf c = new Buf(g.pose().last().pose());
            c.rect(cxp + 1, ty, 5 * S * 0.5f, 8 * S, col(RED, 0.8f * env));
            c.flush();
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
                g.drawString(f, sub, 0, 0, (a8s << 24) | 0xFF2020, true);
                g.pose().popPose();
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    private static float accTo(float[] cw, int n) {
        float a = 0;
        for (int i = 0; i < n; i++) a += cw[i] + 1;
        return a;
    }

    /** batida dupla do coração; o ciclo encurta com o tempo (acelera). */
    private static float beat(float t) {
        if (t < 0.4f) return 0;
        float per = lerp(1.1f, 0.55f, cl((t - 0.4f) / 3.6f)), p = fr((t - 0.4f) / per) * per;
        return (float) (Math.exp(-p * 10) + (p > 0.25f ? 0.6 * Math.exp(-(p - 0.25f) * 10) : 0));
    }

    private static void sons(float t) {
        while (stepIdx < STEPS.length && t >= STEPS[stepIdx]) { tocar(SoundEvents.GRASS_STEP, 0.6f, 0.5f + 0.06f * stepIdx); stepIdx++; }
        if (!olhar && t >= 4.4f) { olhar = true; tocar(SoundEvents.ENDERMAN_STARE, 0.5f, 0.6f); }
        // coração: mesma cadência do visual
        if (t >= 0.4f) {
            float per = lerp(1.1f, 0.55f, cl((t - 0.4f) / 3.6f));
            int ciclo = (int) Math.floor((t - 0.4f) / per);
            if (ciclo != cicloAnt && t < DUR - 0.8f) tocar(SoundEvents.WARDEN_HEARTBEAT, 0.6f + 0.2f * cl(t / 4f), 1.0f);
            cicloAnt = ciclo;
        }
    }
}
