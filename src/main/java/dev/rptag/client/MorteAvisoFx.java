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
 * AVISO MORTE — "Linha Reta" (~6 s)
 *  - monitor cardíaco: a linha vermelha percorre a tela batendo cada vez mais devagar... e vira uma LINHA RETA
 *  - a borda da tela pulsa em vermelho no ritmo de cada batida (como vida baixa) e para junto com o coração
 *  - a visão vai se fechando (túnel) e a tela esfria para cinza
 *  - um CRÂNIO em pixel art (estilo Minecraft) se forma do pó, com os olhos em brasa pulsando; quando o coração
 *    para, os olhos se apagam, e no fim o crânio vira cinzas que sobem
 *  - o texto é vermelho vivo e, na linha reta, perde a cor e vira osso
 *  - cinzas caem o tempo todo; sino fúnebre grave, batidas e o apito contínuo
 *
 * USO: modBus.addListener(MorteAvisoFx::registrar);  MorteAvisoFx.iniciar("AVISO", "VOCE MORREU");  MorteAvisoFx.particulasMundo(p);
 */
public final class MorteAvisoFx {
    // (4.2.0) tempos agora em MorteTempos (fonte unica: FX, cena do Blender e fx.json)
    private static final float DUR = MorteTempos.DUR, FLAT = MorteTempos.FLAT, ECG_END = MorteTempos.ECG_END;
    private static final float[] BEATS = MorteTempos.BEATS;
    private static final int BONE = 0xE1DAC3, BONE_D = 0xA8A08C, RED = 0xD21E28, DRED = 0x5A0008, ASH = 0x9696A0, DARK = 0x0A0000;
    private static final String[] CRANIO = {
        "....########....",
        "..############..",
        ".##############.",
        ".##############.",
        "################",
        "##oooo####oooo##",
        "##oooo####oooo##",
        "##oooo####oooo##",
        "###oo######oo###",
        "#######oo#######",
        "######oooo######",
        "..############..",
        "..#.##.##.##.#..",
        "...#.#.##.#.#..."};

    /** true = sem pulso forte na borda. */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String msg = "AVISO";
    private static String sub = "";
    private static int evIdx, apitoIdx;
    private static boolean sino1, sino2;
    /** (4.2.0) true = o fundo vem do VIDEO do Blender (VideoFx); false = procedural. */
    private static boolean video;

    private MorteAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_morte"), MorteAvisoFx::render);
    }

    public static void iniciar(String titulo, String mensagem) {
        msg = (titulo == null || titulo.isEmpty()) ? "AVISO" : titulo;
        sub = (mensagem == null) ? "" : mensagem;
        inicio = System.nanoTime();
        evIdx = 0; apitoIdx = 0; sino1 = sino2 = false;
        // (4.2.0) tenta o VIDEO renderizado no Blender; sem video -> procedural
        FxComando.garantirConfig();
        video = FxVideoPolitica.permitir(FxConfig.video(), reduzido) && VideoFx.abrir("morte");
        RPTagMod.LOGGER.info("RP Tag: LINHA RETA em {}", video ? "VIDEO" : "procedural");
    }

    /** (4.3.0) Encerra o aviso na hora (um aviso novo substitui este). */
    public static void parar() {
        if (video) VideoFx.fechar();
        video = false;
        inicio = -1;
    }

    /** SERVIDOR: almas subindo, cinzas e um anel de fogo-de-alma. */
    public static void particulasMundo(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(ParticleTypes.SOUL, x, y + 0.5, z, 30, 0.6, 0.6, 0.6, 0.05);
        lv.sendParticles(ParticleTypes.ASH, x, y + 1.5, z, 40, 1.5, 1.0, 1.5, 0.0);
        lv.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.3, z, 15, 0.8, 0.3, 0.8, 0.02);
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI * 2 / 20;
            lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x + Math.cos(a) * 2, y + 0.1, z + Math.sin(a) * 2, 0, 0, 0.03, 0, 1.0);
        }
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
        float cx = w / 2f, cy = h * 0.5f, k = Math.min(w, h) / 240f;
        float env = ss(0, 0.5f, t) * (1 - ss(DUR - 1.0f, DUR, t));
        float pulse = pulso(t);

        Font f = mc.font;
        int len = msg.length();
        float sp = 1.5f, tot = 0;
        float[] cw = new float[len], xs = new float[len];
        for (int i = 0; i < len; i++) { cw[i] = f.width(msg.substring(i, i + 1)); tot += cw[i] + (i < len - 1 ? sp : 0); }
        float S = Math.min(k * 1.9f, w * 0.86f / Math.max(1, tot)), ty = cy + 6 * k - 4 * S, x0 = cx - tot * S / 2, acc = 0;
        for (int i = 0; i < len; i++) { xs[i] = x0 + acc * S; acc += cw[i] + sp; }

        preparar(g);
        Buf b = new Buf(g.pose().last().pose());

        // 1) esfria (cinza) — fica no codigo (o video nao enxerga o jogo por tras)
        b.rect(0, 0, w, h, col(0x464650, 0.22f * ss(1f, 3.5f, t) * env));
        if (video) {
            // (4.2.0) fundo = VIDEO (ECG, cranio, tunel, borda, cinzas) entre o cinza e o texto
            b.flush();
            VideoEstado ve = VideoFx.desenhar(g, t);
            if (ve == VideoEstado.CARREGANDO) {
                inicio = System.nanoTime(); // relogio parado ate ter quadros
                com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
                return;
            }
            if (ve == VideoEstado.FALHOU) {
                // segue procedural NESTE MESMO t (nao reinicia o aviso)
                video = false;
                RPTagMod.LOGGER.warn("RP Tag: video do aviso falhou — seguindo procedural");
            }
            preparar(g);
            b = new Buf(g.pose().last().pose());
        }
        sons(t);
        if (!video) {
        float tunel = 0.30f + 0.18f * ss(1.5f, 4f, t) * (1 - ss(DUR - 1.4f, DUR - 0.2f, t));
        b.vinheta(w, h, Math.min(w, h) * tunel, 0x000000, 0.92f * env);
        float pa = (reduzido ? 0.10f : 0.20f) + (reduzido ? 0.20f : 0.45f) * pulse;
        b.vinheta(w, h, Math.min(w, h) * 0.38f, 0xB4000A, pa * env);

        // 2) cinzas caindo
        for (int i = 0; i < 50; i++) {
            float p = fr(t * (0.05f + 0.08f * rnd(i, 2)) + rnd(i, 3));
            float px = rnd(i, 1) * w + sin(t * 0.8f + i) * 12, py = h * (-0.02f + 1.04f * p), sz = (0.6f + 1.4f * rnd(i, 4)) * k * 0.8f;
            b.rect(px, py, sz, sz, col(i % 7 == 0 ? 0x78140F : ASH, 0.6f * env * sin(3.1416f * p)));
        }

        // 3) crânio em pixel art: se forma do pó, e no fim vira cinza subindo
        float px0 = 2.3f * k, sx = cx - 8 * px0, sy = cy - 30 * k - 7 * px0;
        float eyeA = t < FLAT ? 0.55f + 0.45f * pulse : 0.55f * (1 - ss(FLAT, FLAT + 0.8f, t));
        for (int r = 0; r < CRANIO.length; r++) {
            for (int c = 0; c < 16; c++) {
                char ch = CRANIO[r].charAt(c);
                if (ch == '.') continue;
                int idx = r * 16 + c;
                float ta = 0.4f + 0.9f * rnd(idx, 1), td = DUR - 1.6f + 1.0f * rnd(idx, 2);
                float a = ss(ta, ta + 0.25f, t) * env;
                float dy = 0, dx = 0;
                if (t > td) { a *= 1 - ss(td, td + 0.7f, t); dy = -(t - td) * 25 * k * (0.5f + rnd(idx, 3)); dx = sin(t * 3 + idx) * 2 * k * (t - td); }
                if (a < 0.02f) continue;
                int cc;
                if (ch == 'o') {
                    cc = DARK;
                    boolean ember = (r == 6 || r == 7) && ((c >= 4 && c <= 5) || (c >= 10 && c <= 11));
                    if (ember) { b.rect(sx + c * px0 + dx, sy + r * px0 + dy, px0, px0, col(DARK, 0.9f * a)); cc = RED; a *= eyeA; }
                    else a *= 0.9f;
                } else {
                    boolean dr = c + 1 >= 16 || CRANIO[r].charAt(c + 1) == '.' || r + 1 >= CRANIO.length || CRANIO[r + 1].charAt(c) == '.';
                    cc = dr ? BONE_D : BONE;
                    a *= 0.92f;
                }
                b.rect(sx + c * px0 + dx, sy + r * px0 + dy, px0, px0, col(cc, a));
            }
        }

        // 4) monitor cardíaco
        float yb = h * 0.80f, xh = w * cl(t / ECG_END), amp = 24 * k;
        int ecgC = mix(RED, BONE, ss(FLAT, FLAT + 0.6f, t));
        float ya = yb - ecg(0, w, k, amp), prevX = 0;
        for (float xx = 3; xx <= xh; xx += 3) {
            float yy = yb - ecg(xx, w, k, amp);
            float al = (0.25f + 0.75f * cl(1 - (xh - xx) / (0.55f * w))) * env;
            b.seg(prevX, ya, xx, yy, 1.6f * k * 0.6f, col(ecgC, al), col(ecgC, al));
            prevX = xx; ya = yy;
        }
        if (t < ECG_END + 0.3f) {
            b.circ(xh, yb - ecg(xh, w, k, amp), 2 * k, col(0xFFFFFF, 0.95f * env));
            b.brilho(xh, yb - ecg(xh, w, k, amp), 8 * k, ecgC, 0.5f * env);
        }
        b.flush();

        aditivo();                                                       // brilho dos olhos
        b = new Buf(g.pose().last().pose());
        for (int e = 0; e < 2; e++) {
            float ex = sx + (e == 0 ? 5 : 11) * px0, ey = sy + 7 * px0;
            b.brilho(ex, ey, 5 * px0, RED, 0.35f * eyeA * env * ss(1.0f, 1.4f, t) * (1 - ss(DUR - 1.4f, DUR - 0.6f, t)));
        }
        } // (4.2.0) fim do desenho procedural
        b.flush();
        aditivo();
        b = new Buf(g.pose().last().pose());
        b.brilho(cx, ty + 4 * S, Math.max(tot * S * 0.6f, 60 * k), RED, (0.10f + 0.15f * pulse) * env * (1 - ss(FLAT, FLAT + 0.8f, t)));
        b.flush();

        // 5) texto: vermelho vivo -> osso na linha reta
        normal();
        int tc = mix(RED, BONE, ss(FLAT, FLAT + 0.8f, t));
        for (int i = 0; i < len; i++) {
            float la = cl((t - 0.5f - 0.07f * i) / 0.35f);
            int a8 = (int) (la * env * 255);
            if (a8 < 8) continue;
            float sc = S * (1 + 0.03f * pulse);
            String ch = msg.substring(i, i + 1);
            g.pose().pushPose();
            g.pose().translate(xs[i] + cw[i] * S / 2 - cw[i] * sc / 2, ty - 4 * (sc - S), 0);
            g.pose().scale(sc, sc, 1);
            int sh = (a8 << 24) | DRED;
            g.drawString(f, ch, 1, 1, sh, false); g.drawString(f, ch, -1, 0, sh, false);
            g.drawString(f, ch, 1, 0, sh, false); g.drawString(f, ch, 0, -1, sh, false);
            g.drawString(f, ch, 0, 0, (a8 << 24) | tc, false);
            g.pose().popPose();
        }
        // MENSAGEM do comando — SEMPRE EMBAIXO do titulo (padrao 3.96.4)
        if (!sub.isEmpty()) {
            float S2 = Math.min(k * 0.75f, w * 0.85f / Math.max(1, f.width(sub)));
            float sa2 = ss(0.8f, 1.4f, t) * env;
            int a8s = (int) (sa2 * 255);
            if (a8s >= 8) {
                g.pose().pushPose();
                g.pose().translate(cx - f.width(sub) * S2 / 2, cy + 25 * k, 0);
                g.pose().scale(S2, S2, 1);
                g.drawString(f, sub, 0, 0, (a8s << 24) | (t < FLAT ? 0xD21E28 : BONE), true);
                g.pose().popPose();
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ peças
    /** batida + eco 0.22 s depois, para cada coração em BEATS. */
    private static float pulso(float t) {
        float s = 0;
        for (float tb : BEATS) {
            if (t >= tb) s += (float) Math.exp(-(t - tb) * 9);
            if (t >= tb + 0.22f) s += 0.55f * (float) Math.exp(-(t - tb - 0.22f) * 9);
        }
        return Math.min(1.2f, s);
    }

    /** forma de onda do ECG na posição x (pixels); 0 = linha de base. */
    private static float ecg(float x, int w, float k, float amp) {
        float v = 0, u0 = 10 * k;
        for (float tb : BEATS) {
            float u = (x - w * tb / ECG_END) / u0;
            if (Math.abs(u) > 1.3f) continue;
            v += (float) (Math.exp(-sq(u * 6)) - 0.3 * Math.exp(-sq((u - 0.25f) * 8)) + 0.2 * Math.exp(-sq((u + 0.6f) * 6)) + 0.3 * Math.exp(-sq((u - 0.8f) * 5)));
        }
        return v * amp;
    }
    private static double sq(double v) { return v * v; }

    private static void sons(float t) {
        // eventos de batida: tb e tb+0.22
        while (evIdx < BEATS.length * 2) {
            float te = BEATS[evIdx / 2] + (evIdx % 2) * 0.22f;
            if (t < te) break;
            tocar(SoundEvents.WARDEN_HEARTBEAT, evIdx % 2 == 0 ? 0.7f : 0.65f, 1.0f);
            evIdx++;
        }
        if (!sino1 && t >= 0.1f) { sino1 = true; tocar(SoundEvents.BELL_BLOCK, 0.5f, 0.6f); }
        if (!sino2 && t >= 4.2f) { sino2 = true; tocar(SoundEvents.BELL_BLOCK, 0.5f, 0.7f); }
        while (apitoIdx < 3 && t >= FLAT + 0.5f * apitoIdx && t < DUR - 0.6f) {      // apito da linha reta
            tocar(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 0.9f);
            apitoIdx++;
        }
    }
}
