package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * AVISO CIBERPSICOSE — estética glitch de Cyberpunk. Dura o tempo que você quiser e FICA NAS BORDAS DA TELA.
 *   0.0 s  religa a tela com falhas; barras de erro e estática
 *   0.7 s  "ALERTA: CIBERPSICOSE" estoura em texto corrompido (separação ciano/magenta, caracteres trocando)
 *   1.2 s  medidor de HUMANIDADE despenca de 100% até 23%; log de erros ("0x7F3A: SINCRONIA PERDIDA...") cai pela esquerda
 *   3.2 s  COLAPSO: o vidro da tela ESTILHAÇA (rachaduras + cacos deslocados), clarão, tremor, fatias de imagem rasgadas
 *   3.2-5.6 s  o personagem "fica mal": visão dupla (molduras ciano/vermelha trêmulas), vinheta vermelha no ritmo de um coração
 *          arrítmico, "SINAIS VITAIS CRÍTICOS", biomonitor enlouquecendo
 *   5.8 s  "REINICIANDO..." e a tela estabiliza no MODO PERSISTENTE: blocos corrompidos e rasgos nas bordas, banner amarelo
 *          com a sua mensagem, medidor de humanidade e biomonitor no canto, rachaduras discretas — e SURTOS aleatórios
 *          (a cada ~3 s) em que a tela inteira falha por um instante
 *
 * <p>(4.9.3) "CIBERPSICOSE" — estilo 14 do /rp admin avisar, vindo do protótipo do
 * dono (catbox 57mzzd.java). Só o encanamento mudou (pacote do mod, AvisoFxUtil
 * daqui e as partículas do servidor, que moram no RPCommands).
 *
 * USO: modBus.addListener(CiberpsicoseAvisoFx::registrar);
 *      CiberpsicoseAvisoFx.iniciar("CIBERPSICOSE DETECTADA");            // dura 30 s (DUR_PADRAO)
 *      CiberpsicoseAvisoFx.iniciar("CIBERPSICOSE DETECTADA", 0);        // fica até chamar parar()
 *      CiberpsicoseAvisoFx.parar();                                     // sai com uma falha final
 *      CiberpsicoseAvisoFx.particulasMundo(p, false);                   // true = também aplica Náusea (afeta o jogo!)
 */
public final class CiberpsicoseAvisoFx {
    private static final float DUR_PADRAO = 30f, T_CRASH = 3.2f, T_REBOOT = 5.8f, T_PERS = 6.6f;
    private static final int YEL = 0xFCEE0A, CY = 0x00F0FF, MG = 0xFF2A6D, RED = 0xFF1744, BLACK = 0x000000, WHITE = 0xFFFFFF, DARK = 0x05020A;
    private static final String JUNK = "#@%&01/\\<>?!$";
    private static final String[] LOG = {"ERRO 0x%s: SINCRONIA PERDIDA", "BIOMONITOR: ARRITMIA SEVERA", "NEUROTRANSMISSORES: DESCONTROLE", "RIPPERDOC NAO ENCONTRADO",
            "PROTOCOLO DE CONTENCAO INDISPONIVEL", "IMPLANTE 0x%s: SOBRECARGA", "HUMANIDADE ABAIXO DO LIMITE", "REFLEXOS: FORA DE CONTROLE", "MEMORIA CORROMPIDA EM 0x%s", "SANIDADE: INSTAVEL"};

    /** true = sem clarões, menos piscadas/blocos e tremor reduzido (para quem é sensível a imagens piscando). */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String msg = "CIBERPSICOSE DETECTADA";
    private static float dur = DUR_PADRAO, pararEm = -1;
    private static int evIdx, spikeTocado = -1;
    /** (4.9.5) o áudio do dono toca UMA vez por aviso (o vidro quebrando continua). */
    private static boolean somTocou;
    private static final float[] EV_T = {0.25f, 0.6f, 1.0f, 1.5f, 2.1f, 2.7f, T_CRASH, 3.6f, 4.0f, 4.35f, 4.65f, 4.95f, 5.2f, 5.45f, T_REBOOT, T_PERS};
    private static final int[] EV_K = {0, 0, 1, 2, 2, 2, 3, 2, 2, 2, 2, 2, 2, 2, 4, 5};

    private CiberpsicoseAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_ciberpsicose"), CiberpsicoseAvisoFx::render);
    }

    public static void iniciar(String mensagem) { iniciar(mensagem, DUR_PADRAO); }

    /** segundos <= 0: fica até chamar parar(). */
    public static void iniciar(String mensagem, float segundos) {
        msg = (mensagem == null || mensagem.isEmpty()) ? "CIBERPSICOSE DETECTADA" : mensagem.toUpperCase();
        dur = segundos; pararEm = -1; evIdx = 0; spikeTocado = -1; somTocou = false;
        inicio = System.nanoTime();
    }

    public static void parar() { if (inicio >= 0 && pararEm < 0) pararEm = (System.nanoTime() - inicio) / 1e9f; }

    /** SERVIDOR: faíscas elétricas e fumaça; efeitosJogo=true aplica Náusea por 10 s (opcional: muda o jogo, não só a tela). */
    public static void particulasMundo(ServerPlayer p, boolean efeitosJogo) {
        ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 1.2, z, 60, 0.7, 0.9, 0.7, 0.4);
        lv.sendParticles(ParticleTypes.SMOKE, x, y + 1.0, z, 20, 0.5, 0.6, 0.5, 0.02);
        lv.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 12, 0.8, 0.8, 0.8, 0.05);
        if (efeitosJogo) BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.withDefaultNamespace("nausea"))
                .ifPresent(hld -> p.addEffect(new MobEffectInstance(hld, 200, 0, false, false)));
    }

    // ------------------------------------------------------------------ surtos (picos de falha) previsíveis pelo tempo
    private static float surto(float t) {
        float v = 0;
        int n0 = (int) Math.floor((t - 8f) / 3.1f);
        for (int n = Math.max(0, n0 - 1); n <= n0 + 1; n++) {
            float tn = 8f + 3.1f * n + 1.3f * rnd(n, 5), d = t - tn;
            if (d > 0 && d < 0.34f) v = Math.max(v, 1 - d / 0.34f);
        }
        return v;
    }
    private static int surtoIdx(float t) {
        int n0 = (int) Math.floor((t - 8f) / 3.1f);
        for (int n = Math.max(0, n0 - 1); n <= n0 + 1; n++) { float tn = 8f + 3.1f * n + 1.3f * rnd(n, 5); if (t > tn && t < tn + 0.34f) return n; }
        return -1;
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        float fimT = Float.MAX_VALUE;
        if (dur > 0) fimT = dur;
        if (pararEm >= 0) fimT = Math.min(fimT, pararEm + 1.4f);
        if (t >= fimT) { inicio = -1; return; }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight(), frame = (int) (t * (reduzido ? 12 : 30));
        float k = Math.min(w, h) / 240f, cx = w / 2f, cy = h * 0.5f;
        float saida = ss(fimT - 1.2f, fimT - 0.15f, t);
        float intro = ss(0, 0.5f, t) * (1 - ss(5.8f, 6.8f, t));
        float sp = surto(t);
        float base = (0.38f + 0.07f * sin(t * 0.7f)) * ss(0.3f, 1.0f, t);
        float I = cl(Math.max(base, intro) + sp * 0.6f + saida * 0.8f);
        float on = ss(0.0f, 0.3f, t) * (1 - ss(fimT - 0.12f, fimT, t));
        float pos = ss(T_CRASH, T_CRASH + 0.3f, t);                                           // depois do colapso
        float mal = pos * (1 - ss(T_REBOOT, T_REBOOT + 0.8f, t));                              // "o personagem fica mal"
        float amp = (reduzido ? 0.3f : 1f) * (6 * k * (float) Math.exp(-Math.max(0, t - T_CRASH) * 3.5f) * (t > T_CRASH ? 1 : 0) + 2.5f * k * sp + 1.0f * k * I * 0.4f);
        float shx = amp * sin(t * 83) * (rnd(frame, 3) > 0.5f ? 1 : -1), shy = amp * cos(t * 71);
        sons(t);
        int spi = surtoIdx(t);
        if (spi >= 0 && spi != spikeTocado) { spikeTocado = spi; tocar(SoundEvents.GLASS_BREAK, 1.7f, 0.30f); tocar(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f + rnd(spi, 8), 0.4f); }

        Font f = mc.font;
        preparar(g);
        Buf b = new Buf(g.pose().last().pose());

        // ===== 1) fundo e glitch (blend normal)
        float boot = t < 0.8f ? (rnd(frame, 1) > 0.45f ? 1f : 0.25f) * 0.9f : 0f;
        b.rect(0, 0, w, h, col(BLACK, (0.55f * ss(0.0f, 0.2f, t) * (1 - ss(0.8f, 1.6f, t)) + boot * 0.5f + 0.35f * (intro > 0.5f ? ss(1.0f, 1.6f, t) : 0)) * on));
        b.vinheta(w, h, Math.min(w, h) * (0.30f + 0.12f * mal), DARK, (0.45f + 0.40f * I) * on);
        float ritmo = (float) Math.exp(-fr(t * 1.35f + 0.2f * sin(t * 3)) * 5);                // coração arrítmico
        b.vinheta(w, h, Math.min(w, h) * 0.34f, RED, (0.06f + 0.30f * mal * ritmo + 0.10f * sp) * on);

        if (pos > 0.01f || t > 3.2f) rachaduras(b, cx * 0.92f + shx, cy * 1.04f + shy, 0.62f * (float) Math.hypot(w, h), eo(cl((t - T_CRASH) / 0.35f)), (0.22f + 0.65f * (1 - ss(T_CRASH + 0.3f, T_REBOOT, t)) * pos + 0.35f * sp) * on, k, frame);

        float tickF = reduzido ? 6 : 3;
        int NB = (int) ((reduzido ? 8 : 14) + (reduzido ? 18 : 46) * I);                       // blocos corrompidos pelas bordas
        for (int i = 0; i < NB; i++) {
            if (rnd(i, (int) (t * 30 / tickF) + 99) > 0.30f + 0.45f * I) continue;
            int side = (int) (rnd(i, 1) * 4);
            float along = rnd(i, (int) (t * 30 / tickF) * 3 + 7), dep = (2 + 11 * rnd(i, 3) * rnd(i, 3)) * k * (0.5f + I), ln = (4 + 30 * rnd(i, 4)) * k;
            int[] pal = {CY, MG, YEL, BLACK, RED, WHITE};
            int c = pal[(int) (rnd(i, (int) (t * 30 / tickF) + 5) * pal.length) % pal.length];
            float a = (0.18f + 0.45f * rnd(i, 6)) * (c == BLACK ? 1.6f : 1f) * on;
            if (side == 0) b.rect(along * w, 0, ln, dep, col(c, a)); else if (side == 1) b.rect(along * w, h - dep, ln, dep, col(c, a));
            else if (side == 2) b.rect(0, along * h, dep, ln, col(c, a)); else b.rect(w - dep, along * h, dep, ln, col(c, a));
        }
        int NT = 3 + (int) (9 * I);                                                           // rasgos de imagem (tears)
        for (int i = 0; i < NT; i++) {
            if (rnd(i, (int) (t * 30 / tickF) + 40) > 0.55f + 0.3f * I) continue;
            float y = rnd(i, (int) (t * 30 / tickF) + 41) * h, th = (0.6f + 2.4f * rnd(i, 42)) * k, x0 = rnd(i, 43) * w * 0.7f, len = (0.15f + 0.7f * rnd(i, 44)) * w, dx = (rnd(i, 45) - 0.5f) * 30 * k * I;
            b.rect(0, y, w, th, col(WHITE, 0.05f * on));
            b.rect(x0, y, len, th, col(CY, 0.30f * on)); b.rect(x0 + dx, y + th, len, th * 0.8f, col(MG, 0.30f * on));
        }
        if (I > 0.5f || sp > 0.1f) {                                                          // fatias largas deslocadas (surtos e colapso)
            int NF = (int) (3 + 9 * I);
            for (int i = 0; i < NF; i++) {
                float y = rnd(i, (int) (t * 30 / tickF) + 61) * h, hh = (3 + 18 * rnd(i, 62)) * k * 0.6f, dx = (rnd(i, 63) - 0.5f) * 90 * k * I, x0 = rnd(i, 64) * w * 0.5f, len = (0.2f + 0.7f * rnd(i, 65)) * w;
                b.rect(Math.max(0, x0 + dx), y, len, hh, col(i % 2 == 0 ? CY : MG, 0.12f * (0.4f + I) * on));
                b.rect(Math.max(0, x0 - dx), y + hh * 0.3f, len * 0.6f, hh * 0.35f, col(YEL, 0.20f * I * on));
            }
        }
        if (mal > 0.01f) {                                                                    // visão dupla: molduras que tremem
            float d1 = (3 + 5 * ritmo) * k, d2 = (2 + 3 * rnd(frame, 70)) * k;
            moldura(b, w, h, -d1 + shx, d2, 1.6f * k, col(CY, 0.55f * mal * on));
            moldura(b, w, h, d1 - shx, -d2, 1.6f * k, col(RED, 0.55f * mal * on));
        }
        b.rect(0, 0, 1.6f * k, h, col(CY, (0.30f + 0.4f * rnd(frame, 80)) * I * on));        // bordas cromáticas
        b.rect(w - 1.6f * k, 0, 1.6f * k, h, col(MG, (0.30f + 0.4f * rnd(frame, 81)) * I * on));

        // banner persistente (amarelo, inclinado) e painel de humanidade
        float perA = ss(T_REBOOT, T_PERS, t) * on, bw = Math.max(70 * k, f.width(msg) * 0.95f * k * 0.5f + 20 * k), by = 14 * k;
        boolean piscaB = fr(t * 1.4f) < 0.78f || sp > 0;
        if (perA > 0.01f && piscaB) {
            b.quad(cx - bw + shx, by, col(YEL, 0.96f * perA), cx + bw + shx, by, col(YEL, 0.96f * perA), cx + bw - 7 * k + shx, by + 11 * k, col(YEL, 0.96f * perA), cx - bw + 7 * k + shx, by + 11 * k, col(YEL, 0.96f * perA));
            b.rect(cx - bw + 6 * k + shx, by + 12.5f * k, 2 * bw - 12 * k, 1.1f * k, col(YEL, 0.7f * perA));
        }
        float hum = humanidade(t);
        float pa = ss(1.0f, 1.5f, t) * on, ps = lerp(1.35f, 1.0f, ss(5.2f, 6.2f, t)), px0 = w - 8 * k - 78 * k * ps, py0 = 16 * k + (intro > 0.5f ? 0 : 0);
        if (pa > 0.01f) {
            b.quad(px0, py0, col(DARK, 0.80f * pa), px0 + 78 * k * ps, py0, col(DARK, 0.80f * pa), px0 + 78 * k * ps - 6 * k, py0 + 20 * k * ps, col(DARK, 0.80f * pa), px0 + 6 * k, py0 + 20 * k * ps, col(DARK, 0.80f * pa));
            b.rect(px0, py0, 78 * k * ps, 1.2f * k, col(hum < 40 ? RED : YEL, pa));
            int segs = 20, cheios = (int) Math.ceil(hum / 100f * segs);
            for (int i = 0; i < segs; i++) {
                float sx = px0 + 8 * k * ps + i * 3.3f * k * ps, sy = py0 + 11 * k * ps;
                int c = i < cheios ? (hum < 40 ? RED : YEL) : 0x3A3A44;
                b.quad(sx + 1.2f * k, sy, col(c, (i < cheios ? 0.95f : 0.5f) * pa), sx + 3 * k, sy, col(c, (i < cheios ? 0.95f : 0.5f) * pa), sx + 2 * k, sy + 5 * k * ps, col(c, (i < cheios ? 0.95f : 0.5f) * pa), sx, sy + 5 * k * ps, col(c, (i < cheios ? 0.95f : 0.5f) * pa));
            }
        }
        ecg(b, 8 * k, h - 40 * k, 72 * k, 22 * k, t, I, mal, frame, k, on);
        b.flush();

        // ===== 2) luz (aditivo): clarão do colapso, brilho neon
        aditivo();
        b = new Buf(g.pose().last().pose());
        if (!reduzido && t > T_CRASH && t < T_CRASH + 0.16f) b.rect(0, 0, w, h, col(WHITE, (1 - (t - T_CRASH) / 0.16f) * 0.65f));
        if (!reduzido && sp > 0.6f) b.rect(0, 0, w, h, col(CY, (sp - 0.6f) * 0.20f));
        b.brilho(cx, by + 6 * k, 90 * k, YEL, 0.10f * perA);
        b.brilho(0, cy, 50 * k, CY, 0.10f * I * on); b.brilho(w, cy, 50 * k, MG, 0.10f * I * on);
        b.flush();

        // ===== 3) texto
        normal();
        float tA = ss(0.7f, 1.0f, t) * (1 - ss(T_CRASH - 0.05f, T_CRASH + 0.1f, t)) * on;
        if (tA > 0.01f) {
            String ti = "ALERTA: CIBERPSICOSE";
            float S = Math.min(k * 3.4f, w * 0.88f / Math.max(1, f.width(ti)));
            gtxt(g, f, ti, cx - f.width(ti) * S / 2 + shx, cy - 30 * k + shy, S, YEL, tA, 0.5f + I, frame, 1);
            String sb2 = "FALHA CRÍTICA NO IMPLANTE NEURAL";
            float S2 = Math.min(k * 1.6f, w * 0.7f / Math.max(1, f.width(sb2)));
            gtxt(g, f, sb2, cx - f.width(sb2) * S2 / 2, cy - 30 * k + 8 * S + 6 * k, S2, CY, tA * ss(1.1f, 1.5f, t), I, frame, 2);
            if (fr(t * 2.2f) < 0.6f) txtc(g, f, "▲", cx, cy - 58 * k, k * 3f, ((int) (tA * 255) << 24) | RED);
        }
        float la = ss(1.8f, 2.1f, t) * (1 - ss(T_CRASH + 0.1f, T_CRASH + 0.4f, t)) * on;
        if (la > 0.01f) for (int i = 0; i < 9; i++) {
            float ti2 = 1.8f + 0.13f * i;
            if (t < ti2) continue;
            String s = String.format(LOG[i % LOG.length], String.format("%04X", (int) (rnd(i, frame / 8) * 65535)));
            int vis = Math.min(s.length(), (int) ((t - ti2) * 70));
            txt(g, f, s.substring(0, vis), 8 * k, 20 * k + i * 8 * k, k * 0.95f, ((int) (la * 255) << 24) | (i % 3 == 0 ? RED : (i % 3 == 1 ? CY : YEL)));
        }
        if (mal > 0.05f) {
            String s = "SINAIS VITAIS CRÍTICOS";
            if (fr(t * 2.8f) < 0.62f) gtxt(g, f, s, cx - f.width(s) * k * 2.2f / 2, h - 30 * k, k * 2.2f, RED, mal * on, 0.8f, frame, 3);
        }
        float rb = ss(T_REBOOT - 0.05f, T_REBOOT + 0.2f, t) * (1 - ss(T_PERS - 0.2f, T_PERS, t)) * on;
        if (rb > 0.01f) {
            String s = "REINICIANDO...";
            gtxt(g, f, s, cx - f.width(s) * k * 2.4f / 2, cy - 4 * k, k * 2.4f, WHITE, rb, 0.7f, frame, 4);
            Buf pb = new Buf(g.pose().last().pose());
            float u = cl((t - T_REBOOT) / (T_PERS - T_REBOOT)), wbar = 120 * k;
            pb.rect(cx - wbar / 2, cy + 14 * k, wbar, 2.2f * k, col(0x303040, 0.8f * rb));
            pb.rect(cx - wbar / 2, cy + 14 * k, wbar * (u + (rnd(frame, 90) - 0.5f) * 0.05f), 2.2f * k, col(YEL, rb));
            pb.flush();
            normal();
        }
        if (perA > 0.01f && piscaB) {
            float sc = Math.min(k * 1.5f, (2 * bw - 16 * k) / Math.max(1, f.width(msg)));
            txt(g, f, msg, cx - f.width(msg) * sc / 2 + shx, by + 11 * k / 2 - 4 * sc + 0.5f * k, sc, ((int) (perA * 255) << 24) | BLACK);
        }
        if (pa > 0.01f) {
            String hs = String.format("HUMANIDADE %d%%", (int) hum);
            gtxt(g, f, hs, px0 + 8 * k * ps, py0 + 3 * k, k * 0.95f * ps, hum < 40 ? RED : YEL, pa, I * 0.8f, frame, 5);
        }
        txt(g, f, "● REC", 8 * k, h - 12 * k, k * 0.8f, ((int) ((fr(t) < 0.6f ? 0.9f : 0.2f) * on * 255) << 24) | RED);

        // ===== 4) varredura/CRT por cima
        g.flush();
        preparar(g);
        b = new Buf(g.pose().last().pose());
        int stp = Math.max(2, Math.round(1.6f * k));
        for (float y = 0; y < h; y += stp) b.rect(0, y, w, stp * 0.4f, col(BLACK, (0.10f + 0.08f * I) * on));
        float off = fimT < Float.MAX_VALUE ? ss(fimT - 0.12f, fimT, t) : 0;
        if (off > 0.01f) b.rect(0, 0, w, h, col(BLACK, off));
        b.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    /** humanidade: despenca de 100% para 23% e fica oscilando perto disso. */
    private static float humanidade(float t) {
        float v = lerp(100, 23, eo(cl((t - 1.2f) / 1.9f)));
        if (t > 3.1f) v = 23 + 3 * sin(t * 1.3f) - 4 * ss(T_PERS, 30f, t) + (surto(t) > 0 ? -5 * surto(t) : 0);
        if (t < 3.1f) v += (rnd((int) (t * 20), 4) - 0.5f) * 3;
        return Math.max(1, v);
    }

    // ------------------------------------------------------------------ peças
    private static void txt(GuiGraphics g, Font f, String s, float x, float y, float sc, int color) {
        if (s.isEmpty() || ((color >>> 24) & 255) < 6) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(sc, sc, 1);
        g.drawString(f, s, 0, 0, color, false);
        g.pose().popPose();
    }

    private static void txtc(GuiGraphics g, Font f, String s, float cx, float y, float sc, int color) { txt(g, f, s, cx - f.width(s) * sc / 2, y, sc, color); }

    /** texto glitch: caracteres que trocam + separação ciano/magenta. intensidade em [0,1+]. */
    private static void gtxt(GuiGraphics g, Font f, String s, float x, float y, float sc, int rgb, float a, float I, int frame, int seed) {
        StringBuilder sb = new StringBuilder(s);
        for (int i = 0; i < sb.length(); i++)
            if (sb.charAt(i) != ' ' && rnd(seed * 31 + i, frame / 2) < 0.05f * I) sb.setCharAt(i, JUNK.charAt((int) (rnd(i, frame) * JUNK.length())));
        String o = sb.toString();
        float d = (0.6f + 2.6f * Math.min(1.3f, I)) * sc * 0.55f, jy = (rnd(seed, frame / 2) - 0.5f) * 1.6f * I * sc;
        int a8 = (int) (cl(a) * 255);
        txt(g, f, o, x - d, y + jy, sc, ((int) (a8 * 0.65f) << 24) | CY);
        txt(g, f, o, x + d, y - jy, sc, ((int) (a8 * 0.55f) << 24) | MG);
        txt(g, f, o, x, y, sc, (a8 << 24) | rgb);
    }

    private static void moldura(Buf b, int w, int h, float dx, float dy, float th, int c) {
        b.rect(dx, dy, w, th, c); b.rect(dx, h - th + dy, w, th, c); b.rect(dx, dy, th, h, c); b.rect(w - th + dx, dy, th, h, c);
    }

    /** vidro estilhaçado: rachaduras radiais irregulares, anéis, e cacos translúcidos levemente deslocados. */
    private static void rachaduras(Buf b, float cx, float cy, float R, float prog, float a, float k, int frame) {
        if (prog < 0.01f || a < 0.01f) return;
        int NR = 16, NJ = 7;
        float[][] px = new float[NR][NJ + 1], py = new float[NR][NJ + 1];
        for (int i = 0; i < NR; i++) {
            float an = i * TAU / NR + (rnd(i, 31) - 0.5f) * 0.28f, x = cx, y = cy;
            px[i][0] = x; py[i][0] = y;
            for (int j = 1; j <= NJ; j++) {
                an += (rnd(i * 13 + j, 32) - 0.5f) * 0.32f;
                float step = R * prog / NJ * (0.7f + 0.6f * rnd(i * 13 + j, 33));
                x += cos(an) * step; y += sin(an) * step; px[i][j] = x; py[i][j] = y;
            }
        }
        for (int i = 0; i < NR; i++) {                                                          // cacos entre as rachaduras (anéis 2 a 5)
            int n = (i + 1) % NR;
            for (int j = 2; j < 5; j++) {
                float dxs = (rnd(i * 7 + j, 34) - 0.5f) * 3.5f * k, dys = (rnd(i * 7 + j, 35) - 0.5f) * 3.5f * k;
                int c = rnd(i * 7 + j, 36) > 0.5f ? CY : (rnd(i * 7 + j, 37) > 0.5f ? MG : WHITE);
                float al = a * 0.12f * (0.4f + rnd(i * 7 + j, 38));
                b.quad(px[i][j] + dxs, py[i][j] + dys, col(c, al), px[n][j] + dxs, py[n][j] + dys, col(c, al), px[n][j + 1] + dxs, py[n][j + 1] + dys, col(c, al), px[i][j + 1] + dxs, py[i][j + 1] + dys, col(c, al));
            }
        }
        for (int i = 0; i < NR; i++) {
            for (int j = 0; j < NJ; j++) {
                float wd = (2.6f - 1.7f * j / NJ) * k * 0.6f;
                b.seg(px[i][j], py[i][j], px[i][j + 1], py[i][j + 1], wd, col(WHITE, a * 0.85f), col(WHITE, a * 0.75f));
                b.seg(px[i][j] + 1.2f * k, py[i][j], px[i][j + 1] + 1.2f * k, py[i][j + 1], wd * 0.7f, col(CY, a * 0.45f), col(CY, a * 0.35f));
            }
        }
        for (int j = 2; j <= 5; j += 1)
            for (int i = 0; i < NR; i++) { int n = (i + 1) % NR; b.seg(px[i][j], py[i][j], px[n][j], py[n][j], 0.9f * k * 0.6f, col(WHITE, a * 0.45f), col(WHITE, a * 0.45f)); }
    }

    /** biomonitor: batimentos irregulares e ruído; enlouquece no colapso. */
    private static void ecg(Buf b, float x0, float y0, float w, float h, float t, float I, float mal, int frame, float k, float on) {
        float a = ss(1.0f, 1.5f, t) * on;
        if (a < 0.01f) return;
        b.rect(x0 - 2 * k, y0 - 2 * k, w + 4 * k, h + 4 * k, col(DARK, 0.65f * a));
        b.rect(x0 - 2 * k, y0 - 2 * k, w + 4 * k, 1f * k, col(RED, 0.8f * a));
        float lx = x0, ly = y0 + h * 0.6f, speed = 36f;
        for (float px = 2; px <= w; px += 2.5f) {
            float tt = t - (w - px) / (speed * k);
            int n = Math.round(tt / 0.82f);
            float dtt = tt - (n * 0.82f + 0.12f * sin(n * 1.7f)), amp = 1f + 0.5f * sin(n * 3.1f) + 0.6f * mal * rnd(n, 7);
            float v = (float) (Math.exp(-(dtt / 0.022f) * (dtt / 0.022f)) * amp - 0.3 * Math.exp(-((dtt - 0.05f) / 0.03f) * ((dtt - 0.05f) / 0.03f)));
            v += (rnd((int) (px * 3), frame) - 0.5f) * (0.08f + 0.35f * I) + mal * 0.35f * sin(px * 0.5f + t * 40) * rnd(n, 9);
            float yy = y0 + h * 0.6f - v * h * 0.5f;
            yy = Math.max(y0, Math.min(y0 + h, yy));
            if (px > 2) b.seg(lx, ly, x0 + px, yy, 1.2f * k * 0.7f, col(RED, 0.95f * a), col(RED, 0.95f * a));
            lx = x0 + px; ly = yy;
        }
    }

    private static void sons(float t) {
        // ---- (4.9.5) a trilha do dono (4 s): dispara no primeiro quadro e cobre a
        //      entrada da tela até o COLAPSO; os efeitos de vidro continuam tocando.
        if (!somTocou) {
            somTocou = true;
            if (!reduzido) {
                tocar(dev.rptag.RPTagMod.SOM_CIBERPSICOSE.get(), 1.0f, 1.0f);
            }
        }
        while (evIdx < EV_T.length && t >= EV_T[evIdx]) {
            int kd = EV_K[evIdx++];
            switch (kd) {
                case 0 -> tocar(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 0.5f);
                case 1 -> { tocar(SoundEvents.ENDER_DRAGON_GROWL, 0.3f, 0.5f); tocar(SoundEvents.ENDERMAN_SCREAM, 0.4f, 0.3f); }
                case 2 -> tocar(SoundEvents.WARDEN_HEARTBEAT, 0.7f + 0.15f * (evIdx % 3), 1.0f);
                case 3 -> { tocar(SoundEvents.GLASS_BREAK, 0.6f, 1.0f); tocar(SoundEvents.ENDER_DRAGON_GROWL, 0.3f, 0.8f); tocar(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2f, 0.5f); }
                case 4 -> tocar(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.7f);
                case 5 -> tocar(SoundEvents.BEACON_ACTIVATE, 1.6f, 0.5f);
                default -> { }
            }
        }
    }
}
