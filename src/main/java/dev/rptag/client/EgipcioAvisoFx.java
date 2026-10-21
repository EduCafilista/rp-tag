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
 * AVISO EGÍPCIO — "O OLHO DE HÓRUS" (4.5.0, ~6,5 s) — estilo 12.
 *
 * <p>A tela é um templo à noite: areia soprando, pirâmides na sombra, o disco de Rá
 * subindo devagar atrás de tudo. No centro, o <b>Olho de Hórus</b> é DESENHADO traço
 * por traço (sobrancelha → amêndoa → íris → pupila → lágrima → espiral), com o traço
 * dourado queimando a areia. Quando a pupila fecha, o olho ABRE de vez: clarão de
 * ouro, as colunas de hieróglifos param de rolar e o texto aparece dentro de um
 * CARTUCHO (a moldura oval com o nó embaixo).
 *
 * <ul>
 *   <li><b>fundo</b> — noite de papiro, fibras horizontais, areia no rodapé,
 *       silhueta de 3 pirâmides, disco de Rá com raios girando devagar</li>
 *   <li><b>hieróglifos</b> — 2 colunas rolando nas laterais + 3 pequenos emblemas
 *       (ankh, olho, água) desenhados proceduralmente</li>
 *   <li><b>olho</b> — geometria vetorial pura (segmentos + elipses): sobrancelha,
 *       amêndoa superior/inferior, íris dourada com pupila, o traço vertical
 *       (lágrima) e a espiral do canto (o "6" egípcio)</li>
 *   <li><b>areia</b> — grãos voando na diagonal + poeira dourada que sobe</li>
 *   <li><b>texto</b> — letras douradas com contorno escuro, dentro do cartucho;
 *       a mensagem do comando SEMPRE abaixo do título ({@code cy + 25*k})</li>
 * </ul>
 *
 * <p>Tudo pelo {@link AvisoFxUtil} (um {@code Buf} por camada). A trilha é feita no
 * cliente ({@link #sons}) tocando um "alaúde" pentatônico — a escala que soa
 * oriental sem precisar de arquivo de áudio próprio.
 */
public final class EgipcioAvisoFx {

    private static final float DUR = 6.5f;
    /** quando o olho termina de ser desenhado (o clarão). */
    private static final float ABRE = 2.45f;
    /** quando o texto entra. */
    private static final float TEXTO = 1.55f;

    // ---- paleta egípcia: ouro, areia, lápis-lazúli, turquesa, ocre ----
    private static final int NOITE = 0x160F06, PAPIRO = 0x3A2A11, AREIA = 0xC8A24A,
            AREIA_D = 0x6E5320, OURO = 0xE8C766, OURO_B = 0xFFEBAA, LAPIS = 0x1F4E9C,
            TURQ = 0x2E9E8F, OCRE = 0xA8321E, OSSO = 0xF2E6C8;

    /** true = sem o clarão e com metade dos grãos de areia. */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String msg = "AVISO";
    private static String sub = "";
    private static int harpIdx, sandIdx;
    private static boolean abriu;

    /** linhas dos hieróglifos: 0 vazio · 1 ankh · 2 olho · 3 água · 4 sol · 5 junco. */
    private static final int[][] COLUNAS = new int[2][14];
    static {
        for (int c = 0; c < 2; c++)
            for (int i = 0; i < 14; i++)
                COLUNAS[c][i] = 1 + (int) (rnd(i, 71 + c) * 5);
    }

    /** grãos de areia: x, y, velocidade, tamanho, fase. */
    private static final int NGR = 90;
    private static final float[][] GRAO = new float[NGR][5];
    static {
        for (int i = 0; i < NGR; i++) {
            GRAO[i][0] = rnd(i, 11);
            GRAO[i][1] = rnd(i, 12);
            GRAO[i][2] = 0.25f + 0.9f * rnd(i, 13);
            GRAO[i][3] = 0.4f + 1.5f * rnd(i, 14);
            GRAO[i][4] = rnd(i, 15);
        }
    }

    private EgipcioAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_egipcio"),
                EgipcioAvisoFx::render);
    }

    public static void iniciar(String t, String s) {
        msg = (t == null || t.isEmpty()) ? "AVISO" : t;
        sub = (s == null) ? "" : s;
        inicio = System.nanoTime();
        harpIdx = 0;
        sandIdx = 0;
        abriu = false;
    }

    /** SERVIDOR: o deserto responde — grãos de areia caindo e incenso subindo. */
    public static void particulasMundo(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        BlockParticleOption areiaCai = new BlockParticleOption(ParticleTypes.FALLING_DUST,
                Blocks.SAND.defaultBlockState());
        BlockParticleOption areiaSol = new BlockParticleOption(ParticleTypes.BLOCK,
                Blocks.SAND.defaultBlockState());
        lv.sendParticles(areiaCai, x, y + 3.2, z, 40, 1.2, 0.8, 1.2, 0.02);
        lv.sendParticles(areiaSol, x, y + 0.2, z, 30, 0.9, 0.1, 0.9, 0.06);
        // incenso do templo
        lv.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.4, z, 18, 0.7, 0.2, 0.7, 0.012);
        // brilho do ouro
        lv.sendParticles(ParticleTypes.END_ROD, x, y + 1.6, z, 10, 0.8, 1.0, 0.8, 0.01);
    }

    // ================================================================== render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t >= DUR) { inicio = -1; return; }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h * 0.45f, k = Math.min(w, h) / 240f;
        float env = ss(0, 0.45f, t) * (1 - ss(DUR - 0.85f, DUR, t));
        int frame = (int) (t * 30);
        sons(t);

        // ---- layout: escala FIXA (regra do dono: o título nunca encolhe)
        Font f = mc.font;
        int len = msg.length();
        float sp = 1.5f, tot = 0;
        float[] cw = new float[len], xs = new float[len];
        for (int i = 0; i < len; i++) {
            cw[i] = f.width(msg.substring(i, i + 1));
            tot += cw[i] + (i < len - 1 ? sp : 0);
        }
        float S = k * 1.75f, ty = cy - 4 * S;
        float x0 = cx - tot * S / 2, acc = 0;
        for (int i = 0; i < len; i++) { xs[i] = x0 + acc * S; acc += cw[i] + sp; }

        preparar(g);

        // ============================== CAMADA 1 (normal): o templo
        Buf b = new Buf(g.pose().last().pose());
        noite(b, w, h, k, t, env);
        // (4.5.0) Rá nasce no HORIZONTE, atrás das pirâmides: o disco não
        // pode competir com o olho (na 1ª tentativa ele engolia o emblema)
        ra(b, cx, h * 0.71f, h * 0.16f, k, t, env);
        piramides(b, w, h, k, t, env);
        areiaChao(b, w, h, k, t, env);
        hieroglifos(b, w, h, k, t, env);
        // (4.5.0) o OLHO é o emblema do templo, ACIMA do título: um poço
        // escuro atrás dele dá contraste com o ouro (sem isso o olho sumia)
        b.brilho(cx, cy - 46 * k, 38 * k, 0x0A0703, 0.72f * env);
        olho(b, cx, cy - 46 * k, 22 * k, k, t, env);
        cartucho(b, cx, cy, tot * S, S, k, t, env);
        graos(b, w, h, k, t, env, frame);
        b.flush();

        // ============================== CAMADA 2 (aditivo): ouro
        aditivo();
        b = new Buf(g.pose().last().pose());
        float flash = (t > ABRE && !reduzido) ? Math.max(0f, 1 - (t - ABRE) / 0.45f) : 0f;
        if (flash > 0.01f) b.rect(0, 0, w, h, col(OURO_B, 0.32f * flash * flash));
        brilhoOlho(b, cx, cy - 46 * k, 22 * k, k, t, env);
        b.flush();

        // ============================== TEXTO
        normal();
        texto(g, f, w, k, cx, cy, t, env, xs, cw, S, ty, len);

        // ============================== CAMADA 3: vinheta de papiro
        b = new Buf(g.pose().last().pose());
        b.vinheta(w, h, Math.min(w, h) * 0.14f, NOITE, 0.55f * env);
        b.flush();

        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    // ================================================================== peças
    /** NOITE + fibras de papiro. */
    private static void noite(Buf b, int w, int h, float k, float t, float env) {
        b.rect(0, 0, w, h, col(NOITE, 0.90f * env));
        // fibras horizontais (o papiro)
        for (float y = 0; y < h; y += 3.2f * k) {
            int sd = (int) (y / (3.2f * k));
            float a = (0.05f + 0.05f * rnd(sd, 21)) * env;
            b.rect(0, y, w, Math.max(0.6f, 0.7f * k), col(PAPIRO, a));
        }
        // manchas de envelhecimento
        for (int i = 0; i < 16; i++) {
            float x = rnd(i, 31) * w, y = rnd(i, 32) * h;
            b.circ(x, y, (8 + 22 * rnd(i, 33)) * k, col(PAPIRO, 0.10f * env));
        }
    }

    /** disco de Rá subindo atrás do olho + raios que giram devagar. */
    private static void ra(Buf b, float cx, float cy, float r, float k, float t, float env) {
        float sobe = eo(cl((t - 0.25f) / 1.8f));
        float rr = r * (0.55f + 0.45f * sobe);
        float yy = cy + (1 - sobe) * 26 * k;
        float a = (0.12f + 0.07f * sobe) * env;
        // halo
        b.brilho(cx, yy, rr * 1.70f, OURO, a * 0.55f);
        // disco
        b.circ(cx, yy, rr * 0.80f, col(mix(OURO, OCRE, 0.35f), a * 1.3f));
        b.circ(cx, yy, rr * 0.66f, col(OURO_B, a * 0.45f));
        // raios (triângulos curtos, girando)
        int n = reduzido ? 12 : 24;
        float rot = t * 0.12f;
        for (int i = 0; i < n; i++) {
            float an = i * TAU / n + rot;
            float r0 = rr * 0.86f, r1 = rr * (1.05f + 0.16f * rnd(i, 41));
            float wd = 3.2f * k;
            b.tri(cx + cos(an) * r0, yy + sin(an) * r0, col(OURO, a * 0.55f),
                    cx + cos(an + 0.05f) * r1, yy + sin(an + 0.05f) * r1, col(OURO, 0f),
                    cx + cos(an - 0.05f) * r1, yy + sin(an - 0.05f) * r1, col(OURO, 0f));
            b.seg(cx + cos(an) * rr * 0.9f, yy + sin(an) * rr * 0.9f,
                    cx + cos(an) * rr * 0.9f + cos(an) * wd, yy + sin(an) * rr * 0.9f + sin(an) * wd,
                    1.0f * k, col(OURO, a), col(OURO, 0f));
        }
    }

    /** silhueta de 3 pirâmides no horizonte (2 grandes + 1 pequena). */
    private static void piramides(Buf b, int w, int h, float k, float t, float env) {
        float base = h * 0.80f;
        float a = 0.92f * env;
        pira(b, w * 0.22f, base, w * 0.30f, h * 0.34f, k, a);
        pira(b, w * 0.70f, base, w * 0.26f, h * 0.27f, k, a * 0.9f);
        pira(b, w * 0.46f, base - 2 * k, w * 0.16f, h * 0.17f, k, a * 0.75f);
        // brilho dourado na aresta esquerda (a lua bate)
        for (int i = 0; i < 3; i++) {
            float bxo = i == 0 ? 0.22f : i == 1 ? 0.70f : 0.46f;
            float bw = i == 0 ? 0.30f : i == 1 ? 0.26f : 0.16f;
            float bh = i == 0 ? 0.34f : i == 1 ? 0.27f : 0.17f;
            b.seg(w * bxo - w * bw / 2, base, w * bxo, base - h * bh, 1.4f * k,
                    col(OURO, 0.28f * env), col(OURO, 0.06f * env));
        }
    }

    private static void pira(Buf b, float cxp, float base, float larg, float alt, float k, float al) {
        float ax = cxp - larg / 2, bx = cxp + larg / 2, ay = base;
        float topx = cxp, topy = base - alt;
        // corpo
        b.tri(ax, ay, col(0x120C05, al), bx, ay, col(0x120C05, al), topx, topy, col(0x2A1E0C, al));
        // face iluminada (esquerda)
        b.tri(ax, ay, col(0x0E0903, al * 0.9f), topx, topy, col(0x241A0A, al), cxp, ay, col(0x1A1308, al));
        // linha do horizonte
        b.rect(0, base, 10000, 1.2f * k, col(0x000000, 0.35f * al));
    }

    /** areia acumulada no rodapé, com dunas e grãos parados. */
    private static void areiaChao(Buf b, int w, int h, float k, float t, float env) {
        float a = 0.85f * env;
        for (float x = 0; x < w; x += 7) {
            float xb = Math.min(w, x + 7);
            float ya = h * 0.86f - 7 * k * sin(x * 0.012f + 1.2f) - 3 * k * sin(x * 0.045f);
            float yb = h * 0.86f - 7 * k * sin(xb * 0.012f + 1.2f) - 3 * k * sin(xb * 0.045f);
            b.quad(x, ya, col(AREIA_D, a), xb, yb, col(AREIA_D, a),
                    xb, h, col(0x2A1E0C, a), x, h, col(0x2A1E0C, a));
            b.seg(x, ya, xb, yb, 1.1f * k, col(OURO, 0.22f * env), col(OURO, 0.22f * env));
        }
    }

    /** duas colunas de hieróglifos rolando (as laterais escurecem a leitura do centro). */
    private static void hieroglifos(Buf b, int w, int h, float k, float t, float env) {
        float a = 0.55f * env;
        float cell = 26 * k;
        for (int c = 0; c < 2; c++) {
            float colX = c == 0 ? w * 0.085f : w * 0.915f;
            float scroll = (t * 26 * k) % cell;
            int base = (int) (t * 26 * k / cell);
            for (int i = -1; i < (int) (h / cell) + 2; i++) {
                int tipo = COLUNAS[c][Math.floorMod(base + i, 14)];
                float yy = i * cell - scroll;
                glifo(b, colX, yy, cell * 0.34f, tipo, col(OURO, a * (0.55f + 0.45f * rnd(i + base, 51 + c))), k);
            }
            // moldura da coluna
            b.rect(colX - cell * 0.48f, 0, 1.4f * k, h, col(OURO, 0.18f * env));
            b.rect(colX + cell * 0.48f, 0, 1.4f * k, h, col(OURO, 0.18f * env));
        }
    }

    /** desenha 1 hieróglifo (5 tipos desenhados com linhas/círculos). */
    private static void glifo(Buf b, float x, float y, float s, int tipo, int c, float k) {
        switch (tipo) {
            case 1 -> { // ANKH
                b.circ(x, y - s * 0.55f, s * 0.34f, c);
                b.circ(x, y - s * 0.55f, s * 0.20f, col(0x000000, 0.55f));
                b.seg(x - s * 0.5f, y - s * 0.05f, x + s * 0.5f, y - s * 0.05f, s * 0.20f, c, c);
                b.seg(x, y - s * 0.05f, x, y + s * 0.75f, s * 0.20f, c, c);
            }
            case 2 -> { // OLHO
                b.seg(x - s * 0.6f, y, x + s * 0.6f, y, s * 0.12f, c, c);
                b.elipse(x, y, 1, 0, s * 0.55f, s * 0.30f, c);
                b.circ(x, y, s * 0.17f, col(0x000000, 0.6f));
                b.seg(x - s * 0.1f, y + s * 0.25f, x - s * 0.35f, y + s * 0.7f, s * 0.10f, c, c);
            }
            case 3 -> { // ÁGUA (zig-zag)
                for (int i = 0; i < 3; i++) {
                    float yy = y - s * 0.5f + i * s * 0.45f;
                    b.seg(x - s * 0.6f, yy, x - s * 0.2f, yy + s * 0.16f, s * 0.11f, c, c);
                    b.seg(x - s * 0.2f, yy + s * 0.16f, x + s * 0.2f, yy - s * 0.16f, s * 0.11f, c, c);
                    b.seg(x + s * 0.2f, yy - s * 0.16f, x + s * 0.6f, yy, s * 0.11f, c, c);
                }
            }
            case 4 -> { // SOL (disco com ponto)
                b.circ(x, y, s * 0.5f, c);
                b.circ(x, y, s * 0.22f, col(0x000000, 0.55f));
                for (int i = 0; i < 8; i++) {
                    float an = i * TAU / 8f;
                    b.seg(x + cos(an) * s * 0.55f, y + sin(an) * s * 0.55f,
                            x + cos(an) * s * 0.78f, y + sin(an) * s * 0.78f, s * 0.09f, c, c);
                }
            }
            default -> { // JUNCO / pena
                b.seg(x, y + s * 0.6f, x + s * 0.12f, y - s * 0.5f, s * 0.9f * k + s * 0.08f, c, c);
                b.elipse(x + s * 0.18f, y - s * 0.45f, 0, 1, s * 0.13f, s * 0.3f, c);
            }
        }
    }

    /** O OLHO DE HÓRUS — desenhado traço por traço, depois pulsa. */
    private static void olho(Buf b, float cx, float cy, float R, float k, float t, float env) {
        // 0..1 de "quanto já foi desenhado"
        float p = cl((t - 1.15f) / (ABRE - 1.15f));
        if (p <= 0.001f) return;
        float a = env * cl(p * 4f);
        int cor = col(OURO, a);
        int corF = col(OURO_B, a * 0.95f);

        // ---- 1) sobrancelha (0.00 - 0.22)
        float p1 = cl(p / 0.22f);
        if (p1 > 0) {
            int n = 9;
            float xA = cx - R * 1.05f, xB = cx + R * 0.95f;
            float xm = xA + (xB - xA) * p1;
            for (int i = 0; i < n; i++) {
                float u1 = i / (float) n, u2 = (i + 1) / (float) n;
                if (u2 > p1) break;
                float xx1 = xA + (xB - xA) * u1, xx2 = xA + (xB - xA) * u2;
                float yy1 = cy - R * 0.92f - R * 0.34f * sin(u1 * 2.1f) * (1 - u1 * 0.35f);
                float yy2 = cy - R * 0.92f - R * 0.34f * sin(u2 * 2.1f) * (1 - u2 * 0.35f);
                b.seg(xx1, yy1, xx2, yy2, R * 0.075f, cor, cor);
            }
        }

        // ---- 2) amêndoa (0.22 - 0.50): duas curvas
        float p2 = cl((p - 0.22f) / 0.28f);
        if (p2 > 0) {
            int n = 22;
            for (int i = 0; i < n; i++) {
                float u1 = i / (float) n, u2 = (i + 1) / (float) n;
                if (u2 > p2) break;
                // borda de cima
                float ax1 = cx - R + 2 * R * u1, ax2 = cx - R + 2 * R * u2;
                float ay1 = cy - R * 0.52f * sin(u1 * 3.14159f), ay2 = cy - R * 0.52f * sin(u2 * 3.14159f);
                b.seg(ax1, ay1, ax2, ay2, R * 0.085f, cor, cor);
                // borda de baixo
                float by1 = cy + R * 0.46f * sin(u1 * 3.14159f), by2 = cy + R * 0.46f * sin(u2 * 3.14159f);
                b.seg(ax1, by1, ax2, by2, R * 0.085f, cor, cor);
            }
        }

        // ---- 3) íris + pupila (0.50 - 0.72)
        float p3 = cl((p - 0.50f) / 0.22f);
        if (p3 > 0) {
            float ri = R * 0.34f * eo(p3);
            b.circ(cx, cy, ri, corF);
            b.circ(cx, cy, ri * 0.58f, col(0x140E02, a));
            b.circ(cx - ri * 0.18f, cy - ri * 0.20f, ri * 0.16f, col(OURO_B, a * 0.9f));
        }

        // ---- 4) lágrima (0.72 - 0.86): o traço vertical sob o olho
        float p4 = cl((p - 0.72f) / 0.14f);
        if (p4 > 0) {
            float y2 = cy + R * 0.46f + R * 0.85f * p4;
            b.seg(cx - R * 0.16f, cy + R * 0.42f, cx - R * 0.16f, y2, R * 0.10f, cor, cor);
        }

        // ---- 5) espiral do canto (0.86 - 1.00)
        float p5 = cl((p - 0.86f) / 0.14f);
        if (p5 > 0) {
            int n = 16;
            float xa = cx + R * 0.28f, ya = cy + R * 0.46f;
            for (int i = 0; i < n; i++) {
                float u1 = i / (float) n, u2 = (i + 1) / (float) n;
                if (u2 > p5) break;
                float an1 = u1 * 5.2f, an2 = u2 * 5.2f;
                float r1 = R * 0.16f + u1 * R * 0.42f, r2 = R * 0.16f + u2 * R * 0.42f;
                b.seg(xa + cos(an1 + 1.2f) * r1, ya + sin(an1 + 1.2f) * r1,
                        xa + cos(an2 + 1.2f) * r2, ya + sin(an2 + 1.2f) * r2,
                        R * 0.085f, cor, cor);
            }
        }

        // ---- quando pronto: brilho interno que pulsa com o "abrir/fechar"
        if (p >= 1f) {
            float pulse = 0.5f + 0.5f * sin(t * 2.6f);
            b.circ(cx, cy, R * 0.30f, col(OURO, 0.18f * env * pulse));
        }
    }

    /** brilho do olho (camada aditiva): aureola + faisca na pupila. */
    private static void brilhoOlho(Buf b, float cx, float cy, float R, float k, float t, float env) {
        float p = cl((t - 1.15f) / (ABRE - 1.15f));
        if (p <= 0.05f) return;
        float pulso = 0.55f + 0.45f * sin(t * 2.6f);
        b.brilho(cx, cy, R * 1.15f, OURO, 0.10f * env * p * pulso);
        b.brilho(cx, cy, R * 0.30f, OURO_B, 0.09f * env * p);
        if (p >= 1f) {
            // faísca que atravessa a pupila de tempos em tempos
            float fa = fr(t * 0.7f);
            if (fa < 0.22f) {
                float u = fa / 0.22f;
                b.seg(cx - R * 0.5f + R * u, cy - R * 0.16f, cx - R * 0.22f + R * u, cy + R * 0.16f,
                        1.6f * k, col(OURO_B, 0f), col(OURO_B, 0.85f * env * (1 - u)));
            }
        }
    }

    /** CARTUCHO: a moldura oval egípcia em volta do título (com o nó embaixo). */
    private static void cartucho(Buf b, float cx, float cy, float larg, float S, float k,
                                 float t, float env) {
        float al = ss(TEXTO - 0.25f, TEXTO + 0.55f, t) * env;
        if (al <= 0.01f) return;
        float hw = larg * 0.5f + 26 * k;
        float hh = 5.2f * S + 10 * k;
        int c = col(OURO, 0.55f * al);
        int c2 = col(OURO, 0.22f * al);
        // barra de cima e de baixo (retas)
        b.seg(cx - hw + 8 * k, cy - hh, cx + hw - 8 * k, cy - hh, 1.8f * k, c, c);
        b.seg(cx - hw + 8 * k, cy + hh, cx + hw - 8 * k, cy + hh, 1.8f * k, c, c);
        // laterais (curvas com 5 pontos)
        for (int lado = -1; lado <= 1; lado += 2) {
            float px = cx + lado * (hw - 8 * k), py = cy - hh;
            int n = 6;
            for (int i = 1; i <= n; i++) {
                float u = i / (float) n;
                float nx = cx + lado * (hw - 8 * k) * (1 + 0.10f * sin(u * 3.14159f));
                float ny = cy - hh + 2 * hh * u;
                b.seg(px, py, nx, ny, 1.8f * k, c2, c);
                px = nx; py = ny;
            }
        }
        // NÓ do cartucho (o laço embaixo)
        float ny = cy + hh + 7 * k;
        b.seg(cx - hw * 0.42f, cy + hh, cx - hw * 0.30f, ny, 1.6f * k, c, c);
        b.seg(cx + hw * 0.42f, cy + hh, cx + hw * 0.30f, ny, 1.6f * k, c, c);
        b.rect(cx - hw * 0.34f, ny, hw * 0.68f, 2.0f * k, c);
        // detalhe de lápis-lazúli nos cantos (a faixa azul dos cartuchos reais)
        b.rect(cx - hw + 8 * k, cy - hh - 2.6f * k, hw * 2 - 16 * k, 1.3f * k, col(LAPIS, 0.30f * al));
    }

    /** grãos de areia voando na diagonal (o vento do deserto). */
    private static void graos(Buf b, int w, int h, float k, float t, float env, int frame) {
        int n = reduzido ? 35 : NGR;
        for (int i = 0; i < n; i++) {
            float vx = GRAO[i][2] * 46 * k, vy = GRAO[i][2] * 13 * k;
            float px = fr(GRAO[i][0] + t * vx / Math.max(1, w)) * w + sin(t * 1.6f + GRAO[i][4] * 9) * 5 * k;
            float py = fr(GRAO[i][1] + t * vy / Math.max(1, h)) * h;
            float a = (0.16f + 0.30f * GRAO[i][4]) * env;
            b.circ(px, py, GRAO[i][3] * k * 0.5f, col(OURO, a));
            if (i % 7 == 0) b.seg(px, py, px - vx * 0.05f, py - vy * 0.05f, 0.8f * k,
                    col(OURO, a * 0.7f), col(OURO, 0f));
        }
        // poeira dourada subindo (incenso/faróis)
        for (int i = 0; i < 22; i++) {
            float px = rnd(i, 61) * w;
            float py = h - fr(rnd(i, 62) + t * 0.06f * (0.5f + rnd(i, 63))) * h;
            b.circ(px + sin(t * 0.9f + i) * 6 * k, py, (0.8f + 1.4f * rnd(i, 64)) * k,
                    col(OURO_B, 0.28f * env * rnd(i, 65)));
        }
    }

    // ------------------------------------------------------------------ texto
    private static void texto(GuiGraphics g, Font f, int w, float k, float cx, float cy,
                              float t, float env, float[] xs, float[] cw, float S, float ty,
                              int len) {
        for (int i = 0; i < len; i++) {
            String ch = msg.substring(i, i + 1);
            float la = cl((t - TEXTO - 0.10f * i) / 0.26f), e = eo(la);
            int a8 = (int) (la * env * 255);
            if (a8 < 8) continue;
            float sc = S * (1 + 0.55f * (1 - e));
            float pulse = 1 + 0.015f * sin(t * 2.6f);
            g.pose().pushPose();
            g.pose().translate(xs[i] + cw[i] * S / 2 - cw[i] * sc / 2, ty - 4 * (sc - S), 0);
            g.pose().scale(sc * pulse, sc * pulse, 1);
            int escuro = (a8 << 24) | 0x2A1A05;
            g.drawString(f, ch, -1, 0, escuro, false);
            g.drawString(f, ch, 1, 0, escuro, false);
            g.drawString(f, ch, 0, -1, escuro, false);
            g.drawString(f, ch, 0, 1, escuro, false);
            // corpo dourado + topo mais claro (o metal pega a luz)
            g.drawString(f, ch, 0, 0, (a8 << 24) | 0xE8C766, false);
            g.drawString(f, ch, 0, -1, ((int) (a8 * 0.35f) << 24) | 0xFFEBAA, false);
            g.pose().popPose();
        }

        // MENSAGEM do comando — SEMPRE abaixo do título (regra 3.96.4)
        int sa = (int) (ss(TEXTO + 0.35f, TEXTO + 0.95f, t) * env * 255);
        if (!sub.isEmpty() && sa >= 8) {
            float S2 = Math.min(k * 0.72f, w * 0.85f / Math.max(1, f.width(sub)));
            g.pose().pushPose();
            g.pose().translate(cx - f.width(sub) * S2 / 2, cy + 25 * k, 0);
            g.pose().scale(S2, S2, 1);
            g.drawString(f, sub, 1, 1, ((int) (sa * 0.9f) << 24) | 0x2A1A05, false);
            g.drawString(f, sub, 0, 0, (sa << 24) | OSSO, true);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ som
    /** "alaúde" pentatônico: a escala que soa egípcia sem arquivo de áudio. */
    private static final float[] ESCALA = {0.75f, 0.9f, 1.0f, 1.2f, 1.5f, 1.8f};
    /** quando cada nota do arpejo entra. */
    private static final float[] NOTAS = {0.30f, 0.62f, 0.94f, 1.26f, 1.58f, 1.90f, 2.30f};

    private static void sons(float t) {
        // areia soprando durante toda a cena
        while (sandIdx < 9 && t >= 0.15f + sandIdx * 0.68f) {
            tocar(SoundEvents.SAND_BREAK, 0.30f + 0.07f * (sandIdx % 3), 0.42f);
            sandIdx++;
        }
        // arpejo do alaúde (as notas sobem antes do olho abrir)
        while (harpIdx < NOTAS.length && t >= NOTAS[harpIdx]) {
            float p = ESCALA[harpIdx % ESCALA.length];
            tocar(SoundEvents.NOTE_BLOCK_HARP.value(), p, 0.55f);
            if (harpIdx % 2 == 1) tocar(SoundEvents.NOTE_BLOCK_FLUTE.value(), p * 2f, 0.22f);
            harpIdx++;
        }
        // O OLHO ABRE: clarão + coro grave
        if (!abriu && t >= ABRE) {
            abriu = true;
            tocar(SoundEvents.ENDER_EYE_LAUNCH, 0.55f, 1.20f);
            tocar(SoundEvents.AMETHYST_BLOCK_CHIME, 0.75f, 1.00f);
            tocar(SoundEvents.BEACON_ACTIVATE, 0.45f, 1.30f);
        }
        // depois do clarão: sino do templo + alaúde final
        if (t >= 3.4f && t < 3.45f) tocar(SoundEvents.NOTE_BLOCK_BELL.value(), 0.55f, 0.55f);
        if (t >= 4.6f && t < 4.65f) tocar(SoundEvents.NOTE_BLOCK_BASS.value(), 0.42f, 0.60f);
        if (t >= 5.6f && t < 5.65f) {
            tocar(SoundEvents.NOTE_BLOCK_HARP.value(), 1.5f, 0.35f);
            tocar(SoundEvents.NOTE_BLOCK_FLUTE.value(), 2.0f, 0.20f);
        }
    }
}
