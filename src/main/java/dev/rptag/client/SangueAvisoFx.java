package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Vector3f;

/**
 * AVISO DE SANGUE — "HEMORRAGIA" (4.3.0, ~6 s) — SUBSTITUI o "Sangria".
 *
 * <p>Ideia: a TELA é um corpo. Algo se rompe DENTRO dela. Nada de gotinha cartoon:
 * a coisa abre, esguicha e escorre.
 *
 * <ul>
 *   <li><b>0,00–0,30 s</b> — tela quase preta; uma costura fina se abre no centro
 *       (a pele esticando antes de rasgar), com um ronco grave</li>
 *   <li><b>0,30 s — A RUPTURA</b> — a costura RASGA de uma vez: clarão, tremor,
 *       estalo seco. A ferida abre em ~0,35 s com efeito elástico (passa do ponto
 *       e volta), com lábios de carne irregular e estrias de músculo</li>
 *   <li><b>jatos arteriais</b> — a cada BATIDA o sangue esguicha pelas bordas da
 *       ferida em arcos com gravidade, gotas esticadas em voo e respingos</li>
 *   <li><b>tendões</b> — 6 fibras atravessadas na ferida; cada uma ESTALA (retrai
 *       para as bordas) num tempo próprio, com som seco</li>
 *   <li><b>sangue no VIDRO</b> — 8 manchas irregulares (metaball + tentáculos)
 *       grudam na "lente" e escorrem para baixo por gravidade, deixando rastro,
 *       cabeça de gota e brilho úmido</li>
 *   <li><b>veias</b> — capilares crescem das 4 bordas da tela, pulsando</li>
 *   <li><b>poça</b> — sobe do rodapé, ondula e drena no fim</li>
 *   <li><b>texto</b> — "AVISO" em vermelho vivo com contorno escuro, cada letra
 *       sangrando em 3 filetes que aceleram; a mancha "encharca" o topo das letras</li>
 * </ul>
 *
 * <p>O coração ACELERA até 3,4 s e depois AFROUXA — o aviso sangra até parar.
 * A mensagem do comando fica SEMPRE embaixo do título (regra 3.96.4).
 *
 * USO: ClientGuiLayers → {@code SangueAvisoFx.registrar(e)};
 *      ClientEffects (estilo 9) → {@code SangueAvisoFx.iniciar("AVISO", mensagem)};
 *      servidor → {@code RPCommands.particulasSangue(p)}.
 *
 * <p>Todo desenho vai por {@link AvisoFxUtil.Buf} e sai num único BufferUploader.drawWithShader
 * por camada (normal / aditivo) — nada de RenderSystem manual, nada de RenderGuiEvent.Post.
 */
public final class SangueAvisoFx {
    private static final float DUR = 6.0f;
    /** quando a costura rasga. */
    private static final float RUPTURA = 0.30f;
    /** batidas do coração: aceleram (0,63 s → 0,32 s) e afrouxam (a morte chegando). */
    private static final float[] BEATS = {
            0.32f, 0.95f, 1.48f, 1.94f, 2.34f, 2.70f, 3.02f, 3.36f, 3.74f, 4.20f, 4.78f, 5.45f};
    /** quando cada tendão estala. */
    private static final float[] TENDOES = {0.70f, 1.55f, 2.45f};
    /** pingos que se ouvem. */
    private static final float[] DR = {1.15f, 1.75f, 2.45f, 3.10f, 4.05f, 5.05f};

    // ---- cores (sangue arterial escuro -> vivo, carne, crosta) ----
    private static final int VOID = 0x070001, CRUST = 0x240003, DEEP = 0x3D0006,
            BLOOD = 0x8E0510, BLOOD_B = 0xC4121F, FLESH = 0x8E1512, FLESH_D = 0x4A0806,
            GLOSS = 0xE07070;

    /** true = sem clarão, sem tremor e com metade dos respingos. */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String msg = "AVISO";
    private static String sub = "";
    private static int beatIdx, dripIdx, tendaoIdx;
    private static boolean rupturaTocada;

    /** manchas no "vidro": x%, y%, raio(k), quando aparece, semente, gota(0..1). */
    private static final int NMAN = 14;
    private static final float[][] MAN = new float[NMAN][6];
    static {
        for (int i = 0; i < NMAN; i++) {
            MAN[i][0] = 0.05f + 0.90f * rnd(i, 11);
            MAN[i][1] = 0.03f + 0.58f * rnd(i, 12);
            MAN[i][2] = 5f + 15f * rnd(i, 13);            // 5..20 k  -> respingos, nao bolhas
            MAN[i][3] = RUPTURA + 0.02f + 2.4f * rnd(i, 14);
            MAN[i][4] = i * 37 + 5;
            MAN[i][5] = 0.35f + 0.65f * rnd(i, 15);
        }
    }

    private SangueAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_sangue"), SangueAvisoFx::render);
    }

    public static void iniciar(String t, String s) {
        msg = (t == null || t.isEmpty()) ? "AVISO" : t;
        sub = (s == null) ? "" : s;
        inicio = System.nanoTime();
        beatIdx = 0;
        dripIdx = 0;
        tendaoIdx = 0;
        rupturaTocada = false;
    }

    /** SERVIDOR: o corpo respinga — blocos de redstone, poeira vermelha e névoa. */
    public static void particulasMundo(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        BlockParticleOption blk = new BlockParticleOption(ParticleTypes.BLOCK,
                Blocks.REDSTONE_BLOCK.defaultBlockState());
        BlockParticleOption fall = new BlockParticleOption(ParticleTypes.FALLING_DUST,
                Blocks.REDSTONE_BLOCK.defaultBlockState());
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.05f), 1.8f);
        lv.sendParticles(blk, x, y + 1.0, z, 70, 0.5, 0.7, 0.5, 0.28);
        lv.sendParticles(dust, x, y + 1.2, z, 45, 0.8, 0.9, 0.8, 0.05);
        for (int i = 0; i < 26; i++)
            lv.sendParticles(fall, x + (Math.random() - 0.5) * 2.6, y + 3.4,
                    z + (Math.random() - 0.5) * 2.6, 1, 0, 0, 0, 0);
    }

    // ================================================================== render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t >= DUR) { inicio = -1; return; }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h * 0.45f, k = Math.min(w, h) / 240f;
        float env = ss(0, 0.30f, t) * (1 - ss(DUR - 0.85f, DUR, t));
        float beat = batida(t);
        int frame = (int) (t * 30);
        sons(t);

        // tremor de tela depois da ruptura (decai em ~0,9 s)
        float treme = reduzido ? 0f : (1 - ss(RUPTURA, RUPTURA + 0.9f, t)) * 2.2f * k;
        float shx = treme * (rnd(frame, 7) - 0.5f), shy = treme * (rnd(frame, 8) - 0.5f);

        // ---- layout do título: escala FIXA (a regra do dono: título nunca encolhe)
        Font f = mc.font;
        int len = msg.length();
        float sp = 1.5f, tot = 0;
        float[] cw = new float[len], xs = new float[len];
        for (int i = 0; i < len; i++) { cw[i] = f.width(msg.substring(i, i + 1)); tot += cw[i] + (i < len - 1 ? sp : 0); }
        float S = k * 1.9f, ty = cy - 4 * S;
        float x0 = cx - tot * S / 2, acc = 0;
        for (int i = 0; i < len; i++) { xs[i] = x0 + acc * S; acc += cw[i] + sp; }

        float abertura = abertura(t);

        preparar(g);

        // ============================== CAMADA 1 (normal): o corpo
        Buf b = new Buf(g.pose().last().pose());
        b.rect(0, 0, w, h, col(VOID, (0.66f + 0.12f * beat) * env));

        carne(b, w, h, k, t, env, beat);                       // borda de carne viva
        veias(b, w, h, k, t, env, beat);                       // capilares das bordas
        if (abertura > 0.02f) {
            ferida(b, cx + shx, cy + shy, h * 0.30f, 34 * k, k, h, abertura, beat, env, t);
        } else {
            costura(b, cx, cy, h * 0.30f, k, t, env, abertura);
        }
        jatos(b, cx + shx, cy + shy, h * 0.30f, 34 * k, k, t, env, abertura);
        manchas(b, w, h, k, t, env, false);                    // sangue no vidro (atrás do texto)
        cortina(b, w, h, k, t, env);                              // sangue escorrendo do teto
        grao(b, w, h, k, t, env, frame);                          // grão de filme (tira o "vetor limpo")
        poco(b, w, h, k, t, env);                              // poça subindo
        b.flush();

        // ============================== CAMADA 2 (aditivo): úmido / clarão
        aditivo();
        b = new Buf(g.pose().last().pose());
        float flash = (!reduzido && t > RUPTURA) ? Math.max(0f, 1 - (t - RUPTURA) / 0.28f) : 0f;
        if (flash > 0.01f) b.rect(0, 0, w, h, col(0xC01018, 0.30f * flash * flash * flash));
        if (abertura > 0.02f) {
            // miolo vivo pulsando dentro da ferida
            float ca = (0.22f + 0.42f * beat) * env;
            b.brilho(cx + shx, cy + shy, (26 + 16 * beat) * k, BLOOD_B, ca);
            b.brilho(cx + shx, cy + shy, 12 * k, 0xFF6A6A, ca * 0.7f);
        }
        brilhosManchas(b, w, h, k, t, env);
        b.flush();

        // ============================== TEXTO
        normal();
        FrameTexto(g, f, w, k, cx, cy, t, env, beat, frame, xs, cw, S, ty);

        // ============================== CAMADA 3 (normal): vidro na frente
        b = new Buf(g.pose().last().pose());
        manchasFrente(b, w, h, k, t, env);
        b.vinheta(w, h, Math.min(w, h) * 0.30f, CRUST, (0.55f + 0.20f * beat) * env);
        b.flush();

        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    // ================================================================== peças
    /** 0 → 1: a ferida abre rápido (0,35 s) com efeito elástico e pulsa. */
    private static float abertura(float t) {
        if (t < RUPTURA) return 0f;
        float u = cl((t - RUPTURA) / 0.35f);
        float el = 1 + 0.22f * sin(u * 3.14159f) * (1 - u);      // passa do ponto e volta
        return eo(u) * el * (1 - ss(DUR - 0.7f, DUR - 0.1f, t));
    }

    /** soma de batidas com eco (0,20 s) — o pulso do coração. */
    private static float batida(float t) {
        float s = 0;
        for (float tb : BEATS) {
            if (t >= tb) s += (float) Math.exp(-(t - tb) * 8.5);
            if (t >= tb + 0.20f) s += 0.55f * (float) Math.exp(-(t - tb - 0.20f) * 8.5);
        }
        return Math.min(1.25f, s);
    }

    /** CARNE: vinheta granulada de tecido vivo nas bordas, respirando. */
    private static void carne(Buf b, int w, int h, float k, float t, float env, float beat) {
        float d = Math.min(w, h) * 0.30f;
        float va = (0.70f + 0.22f * beat) * env;
        b.quad(0, 0, col(CRUST, va), w, 0, col(CRUST, va), w, d, col(CRUST, 0), 0, d, col(CRUST, 0));
        b.quad(0, h, col(CRUST, va), w, h, col(CRUST, va), w, h - d, col(CRUST, 0), 0, h - d, col(CRUST, 0));
        b.quad(0, 0, col(CRUST, va), 0, h, col(CRUST, va), d, h, col(CRUST, 0), d, 0, col(CRUST, 0));
        b.quad(w, 0, col(CRUST, va), w, h, col(CRUST, va), w - d, h, col(CRUST, 0), w - d, 0, col(CRUST, 0));
        // grão de carne (pontinhos irregulares que pulsam com o coração)
        int n = reduzido ? 60 : 150;
        for (int i = 0; i < n; i++) {
            float u = rnd(i, 21), v = rnd(i, 22);
            // empurra para as bordas
            float ex = Math.abs(u - 0.5f) * 2, ey = Math.abs(v - 0.5f) * 2;
            if (Math.max(ex, ey) < 0.55f + 0.12f * sin(t * 1.7f + i)) continue;
            float px = u * w, py = v * h, r = (1.2f + 3.2f * rnd(i, 23)) * k * 0.5f;
            float a = (0.18f + 0.22f * rnd(i, 24)) * (0.75f + 0.35f * beat) * env;
            b.circ(px, py, r, col(i % 5 == 0 ? BLOOD : FLESH_D, a));
        }
    }

    /** VEIAS: capilares ramificados crescendo das 4 bordas, pulsando. */
    private static void veias(Buf b, int w, int h, float k, float t, float env, float beat) {
        float g = eo(cl((t - 0.15f) / 1.6f));
        if (g <= 0.02f) return;
        float a = (0.50f + 0.30f * beat) * env;
        float len = Math.min(w, h) * 0.34f;
        float[] fr4 = reduzido ? new float[]{0.28f, 0.72f} : new float[]{0.10f, 0.33f, 0.58f, 0.84f, 0.95f};
        for (int e = 0; e < 4; e++) {
            for (int q = 0; q < fr4.length; q++) {
                int sd = e * 17 + q * 5 + 3;
                float u = fr4[q] + 0.04f * (rnd(sd, 31) - 0.5f);
                float ja = (rnd(sd, 32) - 0.5f) * 46 * DEG;
                float vx = e < 2 ? w * u : (e == 2 ? 0 : w);
                float vy = e < 2 ? (e == 0 ? 0 : h) : h * u;
                float ang = (e == 0 ? 90 : e == 1 ? -90 : e == 2 ? 0 : 180) * DEG + ja;
                veia(b, vx, vy, ang, len, 3, sd * 29 + 11, g, a, k);
            }
        }
    }

    /** veia ramificada recursiva (4 nós, bifurca em 1 e 3) — grossura pulsa. */
    private static void veia(Buf b, float x, float y, float ang, float len, int dep,
                             int seed, float g, float a, float k) {
        if (dep <= 0 || g <= 0.02f) return;
        float L = len * Math.min(1, g), px = x, py = y, an = ang;
        for (int i = 0; i < 4; i++) {
            an += (rnd(seed + i, 41) - 0.5f) * 0.55f;
            float nx = px + cos(an) * L / 4, ny = py + sin(an) * L / 4;
            float wd = (0.35f + dep * 0.55f) * k;
            b.seg(px, py, nx, ny, wd, col(DEEP, a), col(DEEP, a * 0.82f));
            b.seg(px, py, nx, ny, wd * 0.42f, col(BLOOD, a * 0.55f), col(BLOOD, a * 0.40f));
            if ((i == 1 || i == 3) && g > 0.35f)
                veia(b, nx, ny, an + (rnd(seed + i, 42) > 0.5f ? 0.75f : -0.75f),
                        len * 0.58f, dep - 1, seed * 7 + i * 13 + dep,
                        cl((g - 0.35f) / 0.65f), a * 0.88f, k);
            px = nx; py = ny;
        }
    }

    /** COSTURA: antes de rasgar, a pele esticada — uma linha fina e trêmula. */
    private static void costura(Buf b, float cx, float cy, float raioY, float k, float t, float env, float ab) {
        if (ab > 0.02f) return;
        float a = cl(t / RUPTURA) * env;
        int n = 22;
        for (int i = 0; i < n; i++) {
            float u = i / (float) n, u2 = (i + 1) / (float) n;
            float y1 = cy - raioY + 2 * raioY * u, y2 = cy - raioY + 2 * raioY * u2;
            float x1 = cx + (rnd(i, 51) - 0.5f) * 2.4f * k * a;
            float x2 = cx + (rnd(i + 1, 51) - 0.5f) * 2.4f * k * a;
            b.seg(x1, y1, x2, y2, 1.1f * k, col(CRUST, 0.9f * a), col(CRUST, 0.9f * a));
            if (i % 3 == 0) b.circ(x1, y1, 0.5f * k, col(BLOOD, 0.6f * a));
        }
    }

    /** FERIDA: rasgo vertical irregular — interior preto, lábios de carne, estrias, tendões. */
    private static void ferida(Buf b, float cx, float my, float raioY, float larg, float k,
                               float hTela, float ab, float beat, float env, float t) {
        int n = 34;
        float[] lx = new float[n + 1], rx = new float[n + 1], yy = new float[n + 1], ed = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n;
            float v = (u - 0.5f) * 2f;
            float perfil = (float) Math.pow(Math.max(0, 1 - v * v), 0.55);
            // borda: base lisa + ruído de baixa frequência (rasgo de pele, não zigue-zague)
            float jag = 0.86f + 0.26f * rnd(i / 2, 91) + 0.16f * rnd(i, 95) + 0.10f * sin(u * 7.3f);
            // a ferida "respira": abre e fecha devagar depois da ruptura
            float resp = 1 + 0.055f * sin(t * 2.2f) + 0.09f * beat;
            float wd = larg * perfil * jag * ab * resp;
            yy[i] = my - raioY + 2 * raioY * u;
            lx[i] = cx - wd;
            rx[i] = cx + wd;
            ed[i] = perfil;
        }
        // interior: fundo do corpo — escuro, mas com profundidade (não é um buraco chapado)
        for (int i = 0; i < n; i++) {
            float u1 = i / (float) n, u2 = (i + 1) / (float) n;
            int c1 = mix(DEEP, VOID, ss(0f, 0.55f, u1)), c2 = mix(DEEP, VOID, ss(0f, 0.55f, u2));
            b.quad(lx[i], yy[i], col(c1, 0.99f * env), rx[i], yy[i], col(c1, 0.99f * env),
                    rx[i + 1], yy[i + 1], col(c2, 0.99f * env), lx[i + 1], yy[i + 1], col(c2, 0.99f * env));
        }
        // fibras/tecidos pendurados na parede interna (irregularidade do rasgo)
        for (int i = 2; i < n - 2; i += 2) {
            int sd = i * 17 + 3;
            float hw = (rx[i] - lx[i]) * 0.5f;
            float x1 = cx + (rnd(sd, 111) - 0.5f) * 2f * hw * 0.8f;
            float len = (3f + 7f * rnd(sd, 112)) * k * (1 + 0.1f * beat);
            float ang = (rnd(sd, 113) - 0.5f) * 0.9f;
            b.seg(x1, yy[i], x1 + sin(ang) * len, yy[i] + cos(ang) * len, 0.6f * k,
                    col(BLOOD, 0.30f * env), col(BLOOD, 0.45f * env));
        }
        // lábios: carne -> transparente (fora) + fio úmido na borda do corte
        float lip = 7f * k;
        for (int i = 0; i < n; i++) {
            float s1 = 0.55f + 0.95f * rnd(i / 3, 93), s2 = 0.55f + 0.95f * rnd((i + 1) / 3, 93);
            float a1 = env * (0.22f + 0.42f * ed[i]), a2 = env * (0.22f + 0.42f * ed[i + 1]);
            // sombra que separa a ferida da pele
            float o1 = lip * s1 * 1.15f, o2 = lip * s2 * 1.15f, av = 0.45f * env;
            b.quad(lx[i] - o1, yy[i], col(VOID, 0f), lx[i], yy[i], col(VOID, av),
                    lx[i + 1], yy[i + 1], col(VOID, av), lx[i + 1] - o2, yy[i + 1], col(VOID, 0f));
            b.quad(rx[i] + o1, yy[i], col(VOID, 0f), rx[i], yy[i], col(VOID, av),
                    rx[i + 1], yy[i + 1], col(VOID, av), rx[i + 1] + o2, yy[i + 1], col(VOID, 0f));
            b.quad(lx[i], yy[i], col(FLESH, a1), lx[i] - lip * s1, yy[i], col(FLESH_D, 0f),
                    lx[i + 1] - lip * s2, yy[i + 1], col(FLESH_D, 0f), lx[i + 1], yy[i + 1], col(FLESH, a2));
            b.quad(rx[i], yy[i], col(FLESH, a1), rx[i] + lip * s1, yy[i], col(FLESH_D, 0f),
                    rx[i + 1] + lip * s2, yy[i + 1], col(FLESH_D, 0f), rx[i + 1], yy[i + 1], col(FLESH, a2));
            // fio de sangue vivo exatamente no corte (dá a leitura de "aberto")
            b.seg(lx[i], yy[i], lx[i + 1], yy[i + 1], 1.2f * k, col(BLOOD_B, 0.60f * env), col(BLOOD_B, 0.60f * env));
            b.seg(rx[i], yy[i], rx[i + 1], yy[i + 1], 1.2f * k, col(BLOOD_B, 0.60f * env), col(BLOOD_B, 0.60f * env));
            // estria muscular (fibra curta atravessando o lábio)
            if (i % 3 == 0) {
                b.seg(lx[i] - lip * 0.85f, yy[i], lx[i] - lip * 0.15f, yy[i] + 2.5f * k, 0.6f * k,
                        col(FLESH_D, 0.45f * env), col(FLESH_D, 0.12f * env));
                b.seg(rx[i] + lip * 0.85f, yy[i], rx[i] + lip * 0.15f, yy[i] - 2.5f * k, 0.6f * k,
                        col(FLESH_D, 0.45f * env), col(FLESH_D, 0.12f * env));
            }
            // RACHADURAS saindo da ferida para a pele (a pele continua rasgando)
            if (i % 4 == 1) {
                int sd = i * 31 + 7;
                float rl = (10f + 34f * rnd(sd, 96)) * k * cl(ab) * (0.6f + 0.4f * rnd(sd, 97));
                for (int q = 0; q < 3; q++) {
                    float ang = (rnd(sd + q, 98) - 0.5f) * 1.5f;
                    float sx = (q % 2 == 0 ? lx[i] : rx[i]);
                    float ex = sx + (q % 2 == 0 ? -1 : 1) * cos(ang) * rl * (q + 1) / 3f;
                    float ey = yy[i] + sin(ang) * rl * (q + 1) / 3f;
                    b.seg(sx, yy[i] + (q - 1) * k, ex, ey, 0.55f * k,
                            col(DEEP, 0.75f * env), col(BLOOD, 0.15f * env));
                }
            }
        }
        // TENDÕES: fibras CURVAS atravessando o buraco (não retas — reta virava "escada")
        for (int i = 0; i < TENDOES.length; i++) {
            float y = my + (i - (TENDOES.length - 1) / 2f) * raioY * 0.34f;
            int idx = Math.round((y - (my - raioY)) / (2 * raioY) * n);
            idx = Math.max(0, Math.min(n, idx));
            float hw = (rx[idx] - lx[idx]) / 2f;
            // comprimento próprio + curvatura
            float frac = 0.48f + 0.36f * rnd(i, 101);
            float sag = (2.5f + 3.5f * rnd(i, 102)) * k;
            float tau = t - TENDOES[i];
            float tensao = 1f;
            if (tau > 0) tensao = 1f - eo(cl(tau / 0.30f)) * 0.92f;      // retrai e desaparece
            if (tau < 0.30f) {
                int passos = 5;
                float x0 = cx - hw * frac * tensao, x1 = cx + hw * frac * tensao;
                float pxp = x0, pyp = y, aAnt = 0.85f * env;
                for (int q = 1; q <= passos; q++) {
                    float u = q / (float) passos;
                    float xx = x0 + (x1 - x0) * u;
                    float yv = y + sag * sin(u * 3.14159f) * (0.7f + 0.3f * sin(t * 4.1f + i));
                    float aa = 0.85f * env * (1 - (tau > 0 ? cl(tau / 0.30f) : 0));
                    b.seg(pxp, pyp, xx, yv, (0.75f - 0.22f * u) * k, col(FLESH, aAnt), col(FLESH, aa));
                    pxp = xx; pyp = yv; aAnt = aa;
                }
            }
        }
        // ESCORRIDO: fita contínua de sangue saindo do fundo da ferida (bordas variáveis, sem "pincel")
        float fy = my + raioY * 0.90f;
        float fl = cl((t - 0.55f) / 1.5f);
        if (fl > 0.02f && ab > 0.2f) {
            int seg = 44;
            float prevL = 0, prevR = 0, prevY = 0;
            for (int i = 0; i <= seg; i++) {
                float u = i / (float) seg;
                float y = fy + u * hTela * 0.22f;
                float x = cx + (4.5f * sin(u * 4.1f + t * 2.1f) + 2.5f * sin(u * 9.7f - t * 3.3f)) * k * u;
                float wdt = (4f + 7f * fl) * k * (0.45f + 0.80f * u) * (1f - 0.55f * ss(0.80f, 1f, u))
                        * (0.85f + 0.22f * sin(u * 6.1f - t * 1.7f)) * (1 + 0.05f * beat);
                float L = x - wdt * 0.5f, R = x + wdt * 0.5f;
                if (i > 0) {
                    float aa = env * (1 - 0.30f * u);
                    int cc = mix(DEEP, BLOOD, cl(u * 0.9f));
                    b.quad(prevL, prevY, col(cc, aa), prevR, prevY, col(cc, aa),
                            R, y, col(cc, aa), L, y, col(cc, aa));
                    // fio de luz no meio (o sangue escorrendo, não um brilho branco)
                    if (i % 2 == 0)
                        b.seg((prevL + prevR) * 0.5f, prevY, x, y, 0.8f * k, col(BLOOD_B, aa * 0.32f), col(BLOOD_B, aa * 0.32f));
                }
                prevL = L; prevR = R; prevY = y;
            }
        }
    }

    /** JATOS: a cada batida o sangue esguicha pelas bordas da ferida (com gravidade). */
    private static void jatos(Buf b, float cx, float my, float raioY, float larg, float k,
                              float t, float env, float ab) {
        if (ab <= 0.05f) return;
        for (int bi = 0; bi < BEATS.length; bi++) {
            float tau = t - BEATS[bi];
            if (tau <= 0 || tau > 0.55f) continue;
            float life = 1 - tau / 0.55f;
            int N = reduzido ? 5 : 11;
            for (int j = 0; j < N; j++) {
                int sd = bi * 53 + j * 11 + 7;
                float lado = rnd(sd, 1) < 0.5f ? -1f : 1f;
                float ang = lado * (0.12f + 1.25f * rnd(sd, 2));      // pra fora, quase horizontal
                float y0 = my + (rnd(sd, 3) - 0.5f) * raioY * 1.15f;
                float x0 = cx + lado * larg * (0.55f + 0.5f * rnd(sd, 4));
                float v = (110f + 230f * rnd(sd, 5)) * k;
                float vx = cos(ang) * v, vy = sin(ang) * v * 0.75f - 60f * k;
                float g = 520f * k;
                float px = x0 + vx * tau, py = y0 + vy * tau + 0.5f * g * tau * tau;
                float px2 = x0 + vx * Math.max(0, tau - 0.035f);
                float py2 = y0 + vy * Math.max(0, tau - 0.035f) + 0.5f * g * Math.max(0, tau - 0.035f) * Math.max(0, tau - 0.035f);
                float sz = (2.4f + 4.6f * rnd(sd, 6)) * k * life;
                int c = col(rnd(sd, 7) < 0.18f ? BLOOD_B : BLOOD, 0.95f * env * cl(life * 1.6f));
                b.seg(px2, py2, px, py, sz, col(BLOOD, 0f), c);
                b.circ(px, py, sz * 0.62f, c);
            }
            // jato central (o esguicho que sai do meio da ferida, pra cima)
            if (!reduzido && bi % 2 == 0 && tau < 0.42f) {
                float u = tau / 0.42f;
                float hh = (60 + 130 * (1 - u)) * k;
                b.seg(cx, my - raioY * 0.15f, cx + (rnd(bi, 61) - 0.5f) * 12 * k,
                        my - raioY * 0.15f - hh, 5.5f * k * (1 - u), col(BLOOD, 0.85f * env * (1 - u)), col(BLOOD, 0f));
            }
        }
    }

    /** MANCHAS no vidro (atrás do texto): respingo irregular + escorrido. Sem bolha, sem gloss branco. */
    private static void manchas(Buf b, int w, int h, float k, float t, float env, boolean frente) {
        for (int i = 0; i < NMAN; i++) {
            float t0 = MAN[i][3];
            if (t < t0) continue;
            float age = t - t0;
            float sc = eo(cl(age / 0.12f));
            float px = MAN[i][0] * w, py = MAN[i][1] * h;
            float r = MAN[i][2] * k * sc;
            int seed = (int) MAN[i][4];
            float a = 0.86f * env * sc;
            float py2 = py + Math.min(0.5f * 22f * k * age * age, h * 0.5f);

            // corpo: 5 bolhas irregulares, mas SEM contorno redondo dominante
            int nb = 5;
            for (int j = 0; j < nb; j++) {
                float an = j * TAU / nb + rnd(seed + j, 71) * 1.1f;
                float dd = r * (0.06f + 0.30f * rnd(seed + j, 72));
                float rr = r * (0.30f + 0.26f * rnd(seed + j, 73));
                b.circ(px + cos(an) * dd, py2 + sin(an) * dd * 0.9f, rr, col(BLOOD, a));
            }
            // satélites (o respingo quebrado)
            for (int j = 0; j < 5; j++) {
                float an = rnd(seed + j, 84) * TAU;
                float dd = r * (1.1f + 1.5f * rnd(seed + j, 85));
                float rr = r * (0.06f + 0.10f * rnd(seed + j, 86));
                b.circ(px + cos(an) * dd, py2 + sin(an) * dd * 0.85f, rr, col(BLOOD, a * 0.9f));
            }
            // tentáculos rasgados (não radiais demais: ângulo enviesado p/ baixo)
            int nt = 5;
            for (int j = 0; j < nt; j++) {
                float an = (j * TAU / nt + rnd(seed + j, 74) * 0.7f) * 0.55f + 1.0f;
                float L = r * (0.5f + 1.1f * rnd(seed + j, 75));
                float ex = px + cos(an) * L, ey = py2 + sin(an) * L;
                b.seg(px, py2, ex, ey, r * 0.13f, col(BLOOD, a * 0.9f), col(BLOOD, 0f));
                b.circ(ex, ey, r * 0.08f, col(BLOOD, a * 0.7f));
            }
            // escorridos: fio fino + CABEÇA ACHATADA (elipse alongada, sem brilho)
            int nd = 1 + (int) (MAN[i][5] * 2);
            for (int j = 0; j < nd; j++) {
                float dx = px + (rnd(seed + j, 76) - 0.5f) * r * 1.2f;
                float y0 = py2 + r * 0.5f;
                float L = Math.min(h * 0.55f, 0.5f * 26f * k * age * age * (0.5f + rnd(seed + j, 78)));
                if (L < 3) continue;
                float wd = r * (0.09f + 0.06f * rnd(seed + j, 79));
                b.seg(dx, y0, dx + 1.2f * k * sin(age * 2.1f + j), y0 + L, wd,
                        col(DEEP, a * 0.92f), col(BLOOD, a * 0.92f));
                // cabeça: achatada/alongada, SEM gloss branco (era o que ficava cartoon)
                b.elipse(dx, y0 + L, 0, 1, wd * 0.95f, wd * 1.65f, col(BLOOD, a * 0.95f));
            }
        }
    }

    /** respingos NA FRENTE (cantos): pequenos, achatados — profundidade de "lente suja". */
    private static void manchasFrente(Buf b, int w, int h, float k, float t, float env) {
        for (int i = 0; i < 4; i++) {
            float t0 = 1.0f + i * 0.5f;
            if (t < t0) continue;
            float age = t - t0, sc = eo(cl(age / 0.10f));
            float px = (i == 0 ? 0.05f : i == 1 ? 0.95f : i == 2 ? 0.08f : 0.92f) * w;
            float py = (i == 0 ? 0.09f : i == 1 ? 0.15f : i == 2 ? 0.88f : 0.80f) * h;
            float r = (5 + 6 * rnd(i, 81)) * k * sc;
            float a = 0.70f * env;
            float py2 = py + 0.5f * 26f * k * age * age;
            for (int j = 0; j < 4; j++) {
                float an = j * TAU / 4 + rnd(i * 7 + j, 82);
                b.circ(px + cos(an) * r * 0.28f, py2 + sin(an) * r * 0.28f,
                        r * (0.40f + 0.22f * rnd(i + j, 83)), col(BLOOD, a));
            }
            float L = Math.min(h * 0.35f, 0.5f * 34f * k * age * age);
            if (L > 2) {
                b.seg(px, py2, px, py2 + L, r * 0.16f, col(DEEP, a * 0.8f), col(BLOOD, a * 0.75f));
                b.elipse(px, py2 + L, 0, 1, r * 0.17f, r * 0.30f, col(BLOOD, a * 0.85f));
            }
        }
    }

    /** umidade sutil (aditivo) — vermelho, NUNCA branco: branco é o que fazia parecer cartoon. */
    private static void brilhosManchas(Buf b, int w, int h, float k, float t, float env) {
        for (int i = 0; i < NMAN; i++) {
            float t0 = MAN[i][3];
            if (t < t0) continue;
            float age = t - t0, sc = eo(cl(age / 0.12f));
            float px = MAN[i][0] * w, py = MAN[i][1] * h + Math.min(0.5f * 22f * k * age * age, h * 0.5f);
            float r = MAN[i][2] * k * sc;
            b.brilho(px - r * 0.22f, py - r * 0.26f, r * 0.30f, 0xFF3A3A, 0.10f * env * sc);
        }
    }

    /** CORTINA: sangue grosso escorrendo da borda de cima. */
    private static void cortina(Buf b, int w, int h, float k, float t, float env) {
        float g = eo(cl((t - 0.45f) / 1.1f));
        if (g <= 0.02f) return;
        for (float x = 0; x < w; x += 10) {
            float xb = Math.min(w, x + 10);
            float ha = topo(x, k, t, g), hb = topo(xb, k, t, g);
            b.quad(x, 0, col(CRUST, 0.95f * env), xb, 0, col(CRUST, 0.95f * env),
                    xb, hb, col(DEEP, 0.95f * env), x, ha, col(DEEP, 0.95f * env));
            b.seg(x, ha - 0.5f * k, xb, hb - 0.5f * k, 1.0f, col(GLOSS, 0.22f * env), col(GLOSS, 0.22f * env));
        }
        int nd = reduzido ? 4 : 7;
        for (int i = 0; i < nd; i++) {
            float xx = (i + 0.2f + 0.65f * rnd(i, 31)) / nd * w;
            float tau = t - (0.5f + 0.6f * rnd(i, 32));
            if (tau <= 0) continue;
            float r = (2.6f + 2.2f * rnd(i, 33)) * k, v0 = (7f + 9f * rnd(i, 34)) * k, ga = 12f * k;
            float yh = Math.min(topo(xx, k, t, g) + v0 * tau + 0.5f * ga * tau * tau, h * 0.13f);
            int c = col(BLOOD, 0.95f * env);
            float wh = 0.42f * (0.6f + 0.8f * rnd(i, 35));
            b.quad(xx - wh * r, 0, c, xx + wh * r, 0, c, xx + 0.62f * wh * r * 1.5f, yh, c, xx - 0.62f * wh * r * 1.5f, yh, c);
            b.elipse(xx, yh, 0, 1, r * 0.92f, r * 1.45f, c);            // gota esticada (cai)
            b.circ(xx - 0.25f * r, yh - 0.35f * r, r * 0.18f, col(GLOSS, 0.18f * env));
        }
    }

    private static float topo(float x, float k, float t, float g) {
        return (6f * k + 3f * k * sin(x * 0.037f + t * 1.4f) + 2f * k * sin(x * 0.10f - t * 2.1f)) * g;
    }

    /** GRÃO: sujeira de filme — pontinhos escuros e claros que trocam a cada frame. */
    private static void grao(Buf b, int w, int h, float k, float t, float env, int frame) {
        int n = reduzido ? 90 : 240;
        for (int i = 0; i < n; i++) {
            float x = rnd(i, frame * 7 + 1) * w, y = rnd(i, frame * 11 + 3) * h;
            float r = (0.4f + 1.1f * rnd(i, 4)) * k * 0.5f;
            boolean claro = rnd(i, 5) > 0.86f;
            b.circ(x, y, r, col(claro ? 0xFF3020 : 0x000000, (claro ? 0.05f : 0.10f) * env));
        }
    }

    /** POÇA: sobe do rodapé, ondula e drena. */
    private static void poco(Buf b, int w, int h, float k, float t, float env) {
        float lf = ss(0.5f, 1.6f, t) * (1 - ss(DUR - 1.5f, DUR - 0.2f, t));
        float pa = cl(lf * 4);
        if (pa <= 0.01f) return;
        for (float x = 0; x < w; x += 8) {
            float xb = Math.min(w, x + 8);
            float ya = poolY(x, h, k, t, lf), yb = poolY(xb, h, k, t, lf);
            b.quad(x, ya, col(BLOOD, 0.95f * pa), xb, yb, col(BLOOD, 0.95f * pa),
                    xb, h, col(CRUST, 0.98f * pa), x, h, col(CRUST, 0.98f * pa));
            b.seg(x, ya + k * 0.5f, xb, yb + k * 0.5f, 1.2f, col(0xD0202A, 0.30f * pa), col(0xD0202A, 0.30f * pa));
        }
    }

    private static float poolY(float x, int h, float k, float t, float lf) {
        int bucket = (int) (x / (14f * k));
        float n = (rnd(bucket, 71) - 0.5f) * 5f;                  // ruído fixo por coluna (borda rasgada)
        return h - (0.02f + 0.10f * lf) * h + n * k
                - 3f * k * sin(x * 0.028f + t * 2.3f) - 1.6f * k * sin(x * 0.083f - t * 3.2f);
    }

    // ------------------------------------------------------------------ texto
    /** "AVISO" em sangue: contorno escuro, mancha encharcada no topo, 3 filetes por letra. */
    private static void FrameTexto(GuiGraphics g, Font f, int w, float k, float cx, float cy,
                                   float t, float env, float beat, int frame,
                                   float[] xs, float[] cw, float S, float ty) {
        int len = msg.length();
        for (int i = 0; i < len; i++) {
            String ch = msg.substring(i, i + 1);
            float la = cl((t - 0.18f - 0.055f * i) / 0.20f), e = eo(la);
            int a8 = (int) (la * env * 255);
            if (a8 < 8) continue;
            float shake = (1 - ss(0.18f, 0.85f, t)) * (reduzido ? 0.4f : 1.6f);
            float jx = (rnd(i, frame) - 0.5f) * 2 * shake, jy = (rnd(i, frame + 99) - 0.5f) * 2 * shake;
            float sc = S * (1 + 0.75f * (1 - e));                 // "bate" na tela e assenta
            float pulse = 1 + 0.02f * beat;
            g.pose().pushPose();
            g.pose().translate(xs[i] + cw[i] * S / 2 - cw[i] * sc / 2, ty - 4 * (sc - S), 0);
            g.pose().scale(sc * pulse, sc * pulse, 1);
            int dark = (a8 << 24) | CRUST;
            // contorno grosso (a letra fica "crostada")
            g.drawString(f, ch, (int) jx - 1, (int) jy, dark, false); g.drawString(f, ch, (int) jx + 1, (int) jy, dark, false);
            g.drawString(f, ch, (int) jx, (int) jy - 1, dark, false); g.drawString(f, ch, (int) jx, (int) jy + 1, dark, false);
            g.drawString(f, ch, (int) jx - 1, (int) jy - 1, dark, false); g.drawString(f, ch, (int) jx + 1, (int) jy + 1, dark, false);
            // corpo vermelho vivo + brilho úmido no topo
            g.drawString(f, ch, (int) jx, (int) jy, (a8 << 24) | 0xD81822, false);
            g.drawString(f, ch, (int) jx, (int) jy - 1, ((int) (a8 * 0.18f) << 24) | 0xFF2E2E, false);
            g.pose().popPose();
        }
        // filetes saindo das letras (3 por letra, tempos diferentes, com gravidade)
        Buf b = new Buf(g.pose().last().pose());
        for (int i = 0; i < len; i++) {
            for (int dd = 0; dd < 3; dd++) {
                if (reduzido && dd == 2) continue;
                int sd = i * 13 + dd * 5;
                float tau = t - (0.75f + 0.22f * i + 0.45f * rnd(sd, 62));
                if (tau <= 0) continue;
                float maxL = (18 + 46 * rnd(sd, 63)) * k;
                float L = Math.min(maxL, 7f * k * tau + 4.2f * k * tau * tau);
                float dx = xs[i] + cw[i] * S * (0.18f + 0.64f * rnd(sd, 64));
                float y0 = ty + 6.6f * S;
                float wd = (0.9f - 0.25f * dd) * k;
                int c = col(BLOOD, 0.95f * env);
                b.seg(dx, y0, dx, y0 + L, wd, col(DEEP, 0.95f * env), c);
                b.circ(dx, y0 + L, wd * 1.15f, c);
                b.circ(dx, y0 + L, wd * 1.15f, col(BLOOD, 0.95f * env));
            }
        }
        // MENSAGEM do comando — SEMPRE EMBAIXO do título (regra 3.96.4)
        b.flush();
        int sa = (int) (ss(0.75f, 1.35f, t) * env * 255);
        if (!sub.isEmpty() && sa >= 8) {
            float S2 = Math.min(k * 0.75f, w * 0.85f / Math.max(1, f.width(sub)));
            g.pose().pushPose();
            g.pose().translate(cx - f.width(sub) * S2 / 2, cy + 25 * k, 0);
            g.pose().scale(S2, S2, 1);
            g.drawString(f, sub, 1, 1, ((int) (sa * 0.85f) << 24) | CRUST, false);
            g.drawString(f, sub, 0, 0, (sa << 24) | 0xFF6B6B, true);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ som
    private static void sons(float t) {
        if (!rupturaTocada && t >= RUPTURA) {
            rupturaTocada = true;
            tocar(SoundEvents.SLIME_BLOCK_BREAK, 0.40f, 1.0f);     // o rasgo molhado
            tocar(SoundEvents.GENERIC_SPLASH, 0.55f, 0.85f);      // o esguicho
            tocar(SoundEvents.TRIPWIRE_DETACH, 0.50f, 0.9f);      // o estalo da pele
        }
        // batidas: o coração acelera e afrouxa
        while (beatIdx < BEATS.length && t >= BEATS[beatIdx]) {
            float u = beatIdx / (float) BEATS.length;
            float pitch = lerp(0.78f, 0.46f, u);                  // vai engrossando
            tocar(SoundEvents.WARDEN_HEARTBEAT, pitch, 0.70f + 0.15f * u);
            beatIdx++;
        }
        // tendões estalando
        while (tendaoIdx < TENDOES.length && t >= TENDOES[tendaoIdx]) {
            tocar(SoundEvents.TRIPWIRE_DETACH, 0.75f + 0.1f * tendaoIdx, 0.55f);
            tendaoIdx++;
        }
        // pingos
        while (dripIdx < DR.length && t >= DR[dripIdx]) {
            tocar(SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 0.55f + 0.09f * (dripIdx % 3), 0.9f);
            dripIdx++;
        }
        // no fim, o sangue escorrendo
        if (t >= 5.0f && t < 5.05f) tocar(SoundEvents.HONEY_BLOCK_SLIDE, 0.50f, 0.75f);
    }
}
