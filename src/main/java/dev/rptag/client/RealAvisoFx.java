package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import dev.rptag.RPTagMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.joml.Matrix4f;

/**
 * AVISO REAL — "A CORTE DO FARAÓ" (4.7.1) — estilo 3, ~6,5 s.
 *
 * <p>O estilo 3 era só um véu azul-noite com dois filetes (o mais simples do
 * mod). Agora ele é um <b>salão de trono egípcio</b>, na mesma linguagem visual
 * do aviso egípcio: azul-noite, ouro vivo e pedra.
 *
 * <ul>
 *   <li><b>friso</b> — duas faixas de hieróglifos (ankh, água, sol, junco) no
 *       topo e no rodapé, entre filetes de ouro</li>
 *   <li><b>disco solar alado</b> — o disco de Rá com as duas asas em leque e as
 *       <i>uraeus</i> (as serpentes coroando o disco), pulsando atrás do texto</li>
 *   <li><b>obelisco</b> — o monolito ao centro, com a ponta piramidal dourada que
 *       acende quando o aviso entra; hieróglifos gravados no fuste</li>
 *   <li><b>colunas</b> — dois pilares de papiro nos lados, com capitel e base</li>
 *   <li><b>areia</b> — grãos subindo devagar na diagonal</li>
 *   <li><b>texto</b> — letras de OSSO com contorno de LÁPIS-LAZÚLI (a pedra) e
 *       filete de ouro por baixo, no espaço deixado pela placa central</li>
 * </ul>
 *
 * <p>Tudo em {@link AvisoFxUtil} (um {@code Buf} por camada) e registrado por
 * {@code registerAboveAll}, como manda a regra do projeto.
 */
public final class RealAvisoFx {

    private static final float DUR = 6.5f;
    /** quando o obelisco (e o disco) terminam de subir. */
    private static final float SOBE = 1.55f;

    // ---- paleta: azul-noite, ouro, lápis-lazúli, osso, ocre ----
    private static final int NOITE = 0x080D1E, AZUL = 0x16224C, AZUL_B = 0x28386E,
            OURO = 0xE8C766, OURO_B = 0xFFEBAA, OURO_D = 0xB8862A,
            LAPIS = 0x1F4E9C, LAPIS_B = 0x4A78D8, OSSO = 0xF2E6C8, OCRE = 0xA8321E,
            PEDRA = 0x131C3A, PEDRA_B = 0x23325E;

    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String msg = "AVISO";
    private static String sub = "";

    /** hieróglifos do friso: 0 ankh · 1 olho · 2 água · 3 sol · 4 junco. */
    private static final int[] FRISO = new int[30];

    static {
        for (int i = 0; i < FRISO.length; i++) FRISO[i] = (int) (rnd(i, 41) * 5);
    }

    private RealAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_real"),
                RealAvisoFx::render);
    }

    public static void iniciar(String t, String s) {
        msg = (t == null || t.isEmpty()) ? "AVISO" : t;
        sub = (s == null) ? "" : s;
        inicio = System.nanoTime();
        RPTagMod.LOGGER.info("RP Tag: A CORTE DO FARAO iniciada (FX proprio)");
    }

    public static void parar() {
        inicio = -1;
    }

    /** SERVIDOR: areia dourada e cinzas de incenso subindo no trono. */
    public static void particulasMundo(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(ParticleTypes.END_ROD, x, y + 2.0, z, 14, 1.0, 0.6, 1.0, 0.01);
        lv.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 0.4, z, 16, 0.8, 0.2, 0.8, 0.010);
    }

    // ================================================================== render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (inicio < 0) return;
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t >= DUR) {
            inicio = -1;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float cx = w / 2f, cy = h * 0.45f, k = Math.min(w, h) / 240f;
        float env = ss(0, 0.5f, t) * (1 - ss(DUR - 0.9f, DUR, t));
        int frame = (int) (t * 30);

        // ---- layout (escala FIXA: o título nunca encolhe) ----
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
        for (int i = 0; i < len; i++) {
            xs[i] = x0 + acc * S; acc += cw[i] + sp;
        }

        preparar(g);

        // ============================== CAMADA 1 (normal): o salão
        Buf b = new Buf(g.pose().last().pose());
        fundo(b, w, h, cy, k, t, env);
        discoSolar(b, cx, cy, k, t, env);
        colunas(b, w, h, k, t, env);
        friso(b, w, h, k, t, env);
        areia(b, w, h, k, t, env, frame);
        placa(b, cx, cy, tot * S, S, k, t, env);
        b.flush();

        // ============================== CAMADA 2 (aditivo): o ouro vivo
        aditivo();
        b = new Buf(g.pose().last().pose());
        // clarão de entrada (o obelisco acende)
        float flash = Math.max(0f, 1 - Math.abs(t - SOBE) / 0.35f);
        if (flash > 0.01f) b.rect(0, 0, w, h, col(OURO_B, 0.20f * flash * flash * env));
        b.brilho(cx, cy - 62 * k, 96 * k, OURO_B,
                (0.09f + 0.04f * sin(t * 1.8f)) * env);
        b.flush();

        // ============================== CAMADA 3: o texto
        normal();
        texto(g, f, w, k, cx, cy, t, env, xs, cw, S, ty, len);

        // vinheta curta (a moldura do salão)
        b = new Buf(g.pose().last().pose());
        b.vinheta(w, h, Math.min(w, h) * 0.16f, NOITE, 0.50f * env);
        b.flush();

        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    // ================================================================== peças
    /** AZUL-NOITE com faixas de pedra e um halo central mais claro. */
    private static void fundo(Buf b, int w, int h, float cy, float k, float t, float env) {
        b.rect(0, 0, w, h, col(NOITE, 0.94f * env));
        // faixa de céu (mais clara) no meio: 22 tiras, sem degrau visível
        int n = 22;
        for (int i = 0; i < n; i++) {
            float u = i / (float) (n - 1);
            float a = 0.34f * env * (1 - Math.abs(u * 2 - 1) * 0.72f);
            float y = cy - h * 0.34f + h * 0.68f * u;
            b.rect(0, y, w, h * 0.68f / n + 1, col(AZUL, a));
        }
        // brilho central: 18 retângulos concêntricos acumulando alfa (degradê)
        for (int i = 18; i >= 1; i--) {
            float u = i / 18f;
            float a = 0.07f * env * (1.05f - u);
            float rx = w * 0.42f * u, ry = h * 0.40f * u;
            b.rect(w / 2f - rx, cy - ry, rx * 2, ry * 2, col(AZUL_B, a));
        }
    }

    /**
     * DISCO SOLAR ALADO no alto (o behedeti das tumbas): disco de ouro, asas em
     * leque abertas e as duas uraeus na base. Fica ACIMA da placa.
     */
    private static void discoSolar(Buf b, float cx, float cy, float k, float t, float env) {
        float sobe = eo(cl((t - 0.15f) / 1.25f));
        float R = 30 * k * (0.86f + 0.14f * sobe);
        float yy = cy - 62 * k - (1 - sobe) * 12 * k;
        float a = env * ss(0.05f, 0.9f, t);
        b.brilho(cx, yy, R * 3.0f, OURO, 0.14f * a);
        for (int lado = -1; lado <= 1; lado += 2) {
            for (int i = 0; i < 10; i++) {
                float u = i / 9f;
                float comp = R * (5.4f - 2.0f * u);
                float y0 = yy + R * (0.30f + 0.50f * u);
                float x0 = cx + lado * R * 0.72f;
                float queda = R * (0.10f + 0.28f * u);
                float x1 = x0 + lado * comp * 0.92f;
                float y1 = y0 + queda;
                b.seg(x0, y0, x1, y1, (2.6f - 1.0f * u) * k,
                        col(i % 2 == 0 ? OURO : OURO_D, 0.70f * a),
                        col(i % 2 == 0 ? OURO_B : OURO, 0.55f * a));
                float mx = (x0 + x1) / 2, my = (y0 + y1) / 2;
                b.seg(mx, my, mx + lado * comp * 0.10f, my + queda * 0.55f,
                        1.1f * k, col(OURO_B, 0.30f * a), col(OURO, 0.12f * a));
            }
            for (int i = 0; i < 4; i++) {
                float u = i / 3f;
                float x0 = cx + lado * R * 0.70f;
                float y0 = yy + R * (0.20f + 0.34f * u);
                b.seg(x0, y0, x0 + lado * R * 0.55f, y0 + R * (0.06f + 0.16f * u),
                        1.6f * k, col(OURO, 0.40f * a), col(OURO_D, 0.16f * a));
            }
        }
        b.circ(cx, yy, R, col(OURO, 0.90f * a));
        b.circ(cx, yy, R * 0.90f, col(OURO_B, 0.55f * a));
        b.circ(cx, yy, R * 0.62f, col(0xFFF7DE, 0.70f * a));
        b.anel(cx, yy, R * 1.06f, 40, 1.4f * k, 0, OURO_D, 0.45f * a);
        for (int lado = -1; lado <= 1; lado += 2) {
            float px = cx + lado * R * 0.30f, py = yy + R * 0.92f;
            for (int i = 1; i <= 8; i++) {
                float u = i / 8f;
                float nx = px + lado * R * 0.30f * sin(u * 3.14159f) + lado * R * 0.06f * u;
                float ny = py - R * 0.62f * u;
                b.seg(px, py, nx, ny, (2.2f - 0.8f * u) * k,
                        col(OURO_B, 0.85f * a), col(OURO, 0.60f * a));
                px = nx; py = ny;
            }
            b.circ(px, py, 2.6f * k, col(OCRE, 0.90f * a));
        }
    }

    /** COLUNAS de papiro esbeltas nas laterais, com capitel e base. */
    private static void colunas(Buf b, int w, int h, float k, float t, float env) {
        float a = env * ss(0.10f, 0.9f, t);
        for (int lado = -1; lado <= 1; lado += 2) {
            float x = lado < 0 ? w * 0.070f : w * 0.930f;
            float bw = 20 * k, topo = h * 0.20f, base = h * 0.84f;
            b.rect(x - bw / 2, topo, bw, base - topo, col(PEDRA, 0.80f * a));
            b.rect(x - bw / 2, topo, 2.0f * k, base - topo, col(OURO_D, 0.30f * a));
            b.rect(x + bw / 2 - 2.0f * k, topo, 2.0f * k, base - topo, col(0x05080F, 0.55f * a));
            b.quad(x - bw * 0.62f, topo, col(PEDRA_B, 0.85f * a),
                    x + bw * 0.62f, topo, col(PEDRA_B, 0.85f * a),
                    x + bw * 1.05f, topo - 13 * k, col(PEDRA_B, 0.85f * a),
                    x - bw * 1.05f, topo - 13 * k, col(PEDRA_B, 0.85f * a));
            b.seg(x - bw * 1.05f, topo - 13 * k, x + bw * 1.05f, topo - 13 * k,
                    1.6f * k, col(OURO, 0.55f * a), col(OURO, 0.55f * a));
            for (int i = -2; i <= 2; i++) {
                float px = x + i * bw * 0.34f;
                b.seg(px, topo - 13 * k, px + i * 2.6f * k, topo - 22 * k,
                        1.3f * k, col(OURO, 0.32f * a), col(OURO_D, 0f));
            }
            b.rect(x - bw * 0.72f, base, bw * 1.44f, 6 * k, col(PEDRA_B, 0.85f * a));
            b.seg(x - bw * 0.72f, base, x + bw * 0.72f, base, 1.2f * k,
                    col(OURO_D, 0.40f * a), col(OURO_D, 0.40f * a));
            for (int r = 0; r < 3; r++) {
                float yy = topo + (base - topo) * (0.16f + 0.30f * r);
                b.rect(x - bw / 2, yy, bw, 1.2f * k, col(OURO_D, 0.16f * a));
            }
        }
    }

    /** FRISOS de hieróglifos no topo e no rodapé (como uma tumba). */
    private static void friso(Buf b, int w, int h, float k, float t, float env) {
        float a = env * ss(0.0f, 0.6f, t);
        for (int faixa = 0; faixa < 2; faixa++) {
            float y = faixa == 0 ? 9 * k : h - 27 * k;
            b.rect(0, y, w, 18 * k, col(0x0C1330, 0.92f * a));
            b.rect(0, y, w, 1.4f * k, col(OURO, 0.75f * a));
            b.rect(0, y + 18 * k, w, 1.4f * k, col(OURO, 0.75f * a));
            int n = 24, passo = (int) (w / (float) n);
            float meio = y + 9 * k;
            for (int i = 0; i < n; i++) {
                float gx = passo * (i + 0.5f);
                float vis = ss(0.05f + 0.02f * i, 0.5f + 0.02f * i, t);
                glifo(b, gx, meio, 6 * k, FRISO[i % FRISO.length],
                        col(OURO_D, 0.85f * a * vis));
            }
        }
    }

    /** Um hieróglifo desenhado com segmentos (s = metade do tamanho). */
    private static void glifo(Buf b, float x, float y, float s, int tipo, int c) {
        switch (tipo) {
            case 0 -> { // ankh
                for (int i = 0; i < 8; i++) {
                    float a1 = i * 0.7854f, a2 = (i + 1) * 0.7854f;
                    b.seg(x + cos(a1) * s * 0.45f, y - s * 0.55f + sin(a1) * s * 0.45f,
                            x + cos(a2) * s * 0.45f, y - s * 0.55f + sin(a2) * s * 0.45f, 1.1f * s * 0.22f, c, c);
                }
                b.seg(x - s * 0.55f, y - s * 0.10f, x + s * 0.55f, y - s * 0.10f, 1.1f * s * 0.2f, c, c);
                b.seg(x, y - s * 0.10f, x, y + s * 0.75f, 1.1f * s * 0.2f, c, c);
            }
            case 1 -> { // olho
                b.seg(x - s * 0.6f, y, x + s * 0.6f, y, 1.1f * s * 0.18f, c, c);
                b.seg(x - s * 0.42f, y - s * 0.22f, x + s * 0.42f, y - s * 0.22f, 1.1f * s * 0.14f, c, c);
                b.seg(x - s * 0.42f, y + s * 0.22f, x + s * 0.42f, y + s * 0.22f, 1.1f * s * 0.14f, c, c);
            }
            case 2 -> { // água
                for (int k = 0; k < 3; k++) {
                    float yy = y - s * 0.4f + k * s * 0.4f;
                    b.seg(x - s * 0.6f, yy, x - s * 0.2f, yy, 1.1f * s * 0.16f, c, c);
                    b.seg(x - s * 0.2f, yy, x + s * 0.2f, yy, 1.1f * s * 0.16f, c, c);
                    b.seg(x + s * 0.2f, yy, x + s * 0.6f, yy, 1.1f * s * 0.16f, c, c);
                }
            }
            case 3 -> { // sol
                for (int i = 0; i < 8; i++) {
                    float a1 = i * 0.7854f;
                    b.seg(x + cos(a1) * s * 0.28f, y + sin(a1) * s * 0.28f,
                            x + cos(a1) * s * 0.58f, y + sin(a1) * s * 0.58f, 1.1f * s * 0.16f, c, c);
                }
            }
            default -> { // junco
                b.seg(x, y + s * 0.6f, x + s * 0.08f, y - s * 0.55f, 1.1f * s * 0.18f, c, c);
                b.seg(x + s * 0.08f, y - s * 0.55f, x + s * 0.30f, y - s * 0.10f, 1.1f * s * 0.12f, c, c);
            }
        }
    }

    /** AREIA subindo na diagonal. */
    private static void areia(Buf b, int w, int h, float k, float t, float env, int frame) {
        int n = reduzido ? 24 : 56;
        for (int i = 0; i < n; i++) {
            float vel = 0.10f + 0.22f * rnd(i, 51);
            float x = (rnd(i, 52) * w + t * vel * w * 0.30f) % w;
            float y = (rnd(i, 53) * h - t * vel * h * 0.55f + h) % h;
            float r = (0.5f + 1.1f * rnd(i, 54)) * k;
            int a = (int) (255 * (0.12f + 0.28f * rnd(i, 55)) * env);
            b.circ(x, y, r, (a << 24) | OURO_B);
        }
    }

    /** PLACA central: moldura dourada onde o título e a mensagem ficam. */
    private static void placa(Buf b, float cx, float cy, float larg, float S, float k,
                              float t, float env) {
        float a = env * ss(0.35f, 1.15f, t);
        if (a <= 0.01f) return;
        float hw = larg * 0.5f + 30 * k, hh = 5.6f * S + 12 * k;
        // fundo escuro (legibilidade)
        b.rect(cx - hw, cy - hh, hw * 2, hh * 2, col(0x060A18, 0.72f * a));
        // filete externo + interno
        int o1 = col(OURO, 0.80f * a), o2 = col(OURO_D, 0.45f * a);
        b.seg(cx - hw, cy - hh, cx + hw, cy - hh, 1.8f * k, o1, o1);
        b.seg(cx - hw, cy + hh, cx + hw, cy + hh, 1.8f * k, o1, o1);
        b.seg(cx - hw, cy - hh, cx - hw, cy + hh, 1.8f * k, o1, o1);
        b.seg(cx + hw, cy - hh, cx + hw, cy + hh, 1.8f * k, o1, o1);
        b.seg(cx - hw + 4 * k, cy - hh + 4 * k, cx + hw - 4 * k, cy - hh + 4 * k, 0.8f * k, o2, o2);
        b.seg(cx - hw + 4 * k, cy + hh - 4 * k, cx + hw - 4 * k, cy + hh - 4 * k, 0.8f * k, o2, o2);
        // cantos em losango
        for (int lado = -1; lado <= 1; lado += 2) {
            float px = cx + lado * hw, py = cy;
            b.circ(px, py, 3.4f * k, col(OURO_B, 0.85f * a));
        }
        // barra de lápis-lazúli no topo (o mesmo detalhe do cartucho egípcio)
        b.rect(cx - hw + 6 * k, cy - hh - 3.2f * k, hw * 2 - 12 * k, 1.6f * k, col(LAPIS_B, 0.55f * a));
    }

    // ================================================================== texto
    private static void texto(GuiGraphics g, Font f, int w, float k, float cx, float cy,
                              float t, float env, float[] xs, float[] cw, float S, float ty,
                              int len) {
        for (int i = 0; i < len; i++) {
            String ch = msg.substring(i, i + 1);
            float la = cl((t - 0.45f - 0.055f * i) / 0.26f), e = eo(la);
            int a8 = (int) (la * env * 255);
            if (a8 < 8) continue;
            float sc = S * (1 + 0.45f * (1 - e));
            g.pose().pushPose();
            g.pose().translate(xs[i] + cw[i] * S / 2 - cw[i] * sc / 2, ty - 4 * (sc - S), 0);
            g.pose().scale(sc, sc, 1);
            int lazuli = (a8 << 24) | 0x0A1330;
            g.drawString(f, ch, -1, 0, lazuli, false);
            g.drawString(f, ch, 1, 0, lazuli, false);
            g.drawString(f, ch, 0, -1, lazuli, false);
            g.drawString(f, ch, 0, 1, lazuli, false);
            g.drawString(f, ch, 0, 0, (a8 << 24) | OSSO, false);
            g.drawString(f, ch, 0, -1, ((int) (a8 * 0.42f) << 24) | 0xFFF6DC, false);
            g.pose().popPose();
        }
        // filete de ouro sob o título
        int fa = (int) (ss(0.5f, 1.1f, t) * env * 255);
        if (fa > 8) {
            float lw = Math.min(w * 0.62f, len * S * 8);
            float y = ty + 16 * k;
            Buf b = new Buf(g.pose().last().pose());
            b.seg(cx - lw / 2, y, cx + lw / 2, y, 1.2f * k,
                    col(OURO, 0f), (fa << 24) | OURO);
            b.seg(cx - lw / 2, y, cx + lw / 2, y, 0.8f * k,
                    (fa << 24) | OURO_D, col(OURO_B, fa / 255f));
            b.flush();
        }
        // MENSAGEM do comando — SEMPRE abaixo do título (regra 3.96.4)
        int sa = (int) (ss(1.05f, 1.65f, t) * env * 255);
        if (!sub.isEmpty() && sa >= 8) {
            float S2 = Math.min(k * 0.78f, w * 0.85f / Math.max(1, f.width(sub)));
            g.pose().pushPose();
            g.pose().translate(cx - f.width(sub) * S2 / 2, cy + 27 * k, 0);
            g.pose().scale(S2, S2, 1);
            g.drawString(f, sub, 1, 1, ((int) (sa * 0.9f) << 24) | 0x0A1330, false);
            g.drawString(f, sub, 0, 0, (sa << 24) | OSSO, true);
            g.pose().popPose();
        }
    }
}
