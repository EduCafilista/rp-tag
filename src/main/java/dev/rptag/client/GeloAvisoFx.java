package dev.rptag.client;

import dev.rptag.RPTagMod;

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
 * AVISO GELO — "Geada Eterna" (~6 s)
 *  - a tela congela das bordas: geada em dendritos (ramificação de 60°) + vinheta branco-azulada
 *  - trinca de vidro no centro (raios + anéis poligonais) no momento do impacto
 *  - cristais facetados (metade clara, metade escura) sobem do chão e pendem do teto
 *  - FLOCO DE NEVE geométrico de 6 braços girando atrás do texto
 *  - nevasca diagonal de losangos + brilhos em estrela
 *  - o texto congela letra a letra, com reflexo de brilho varrendo e pequenos pingentes
 *
 * USO: modBus.addListener(GeloAvisoFx::registrar);  GeloAvisoFx.iniciar("AVISO", "teste");  GeloAvisoFx.particulasMundo(p, false);
 *      (true = também congela o jogador com a mecânica do neve em pó: lento + dano; afeta o jogo!)
 */
public final class GeloAvisoFx {
    private static final float DUR = 6.0f;
    private static final int ICE = 0xD2F0FF, LIGHT = 0xBEEBFF, MID = 0x5AA0DC, DEEP = 0x0A2850, GLOW = 0x50A0FF, WHITE = 0xFFFFFF;
    private static final float[] CH = {0.5f, 1.2f, 2.0f, 3.0f};
    /** (4.5.0) contorno escuro do texto no modo video (azul-noite). */
    private static final int CONTORNO_VIDEO = 0x0A1C38;
    private static final float[] CP = {1.5f, 1.9f, 2.0f, 1.7f};

    /** true = sem clarão, menos neve e dendritos. */
    public static boolean reduzido = false;

    private static long inicio = -1;
    private static String msg = "AVISO";
    private static String sub = "";
    private static int chIdx;
    private static boolean impacto, fim;
    /** (4.5.0) true = fundo vem do video do Blender (VideoFx). */
    private static boolean video;

    private GeloAvisoFx() {}

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_gelo"), GeloAvisoFx::render);
    }

    public static void iniciar(String titulo, String mensagem) {
        msg = (titulo == null || titulo.isEmpty()) ? "AVISO" : titulo;
        sub = (mensagem == null) ? "" : mensagem;
        inicio = System.nanoTime();
        chIdx = 0; impacto = false; fim = false;
        // (4.5.0) tenta o VIDEO renderizado no Blender; reduzido/fxVideo off/sem video -> procedural
        FxComando.garantirConfig();
        video = FxVideoPolitica.permitir(FxConfig.video(), reduzido) && VideoFx.abrir("gelo");
        RPTagMod.LOGGER.info("RP Tag: GEADA ETERNA em {}", video ? "VIDEO" : "procedural");
    }

    /** (4.5.0) Encerra o aviso na hora (um aviso novo substitui este). */
    public static void parar() {
        if (video) VideoFx.fechar();
        video = false;
        inicio = -1;
    }

    /** SERVIDOR: nevasca + estilhaços de gelo. congelar=true usa o congelamento vanilla (afeta o jogo!). */
    public static void particulasMundo(ServerPlayer p, boolean congelar) {
        ServerLevel lv = p.serverLevel();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        lv.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 1.2, z, 80, 1.5, 1.0, 1.5, 0.05);
        lv.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), x, y + 1.0, z, 40, 0.5, 0.6, 0.5, 0.2);
        lv.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 15, 1.0, 1.0, 1.0, 0.02);
        if (congelar) p.setTicksFrozen(p.getTicksRequiredToFreeze() + 40);
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
        float cx = w / 2f, cy = h * 0.45f, k = Math.min(w, h) / 240f;
        float env = ss(0, 0.4f, t) * (1 - ss(DUR - 1.2f, DUR, t));
        float rev = eo(cl((t - 0.1f) / 1.0f));

        Font f = mc.font;
        int len = msg.length();
        float sp = 1.5f, tot = 0;
        float[] cw = new float[len], xs = new float[len];
        for (int i = 0; i < len; i++) { cw[i] = f.width(msg.substring(i, i + 1)); tot += cw[i] + (i < len - 1 ? sp : 0); }
        float S = Math.min(k * 1.9f, w * 0.86f / Math.max(1, tot)), ty = cy - 4 * S, x0 = cx - tot * S / 2, acc = 0;
        for (int i = 0; i < len; i++) { xs[i] = x0 + acc * S; acc += cw[i] + sp; }

        preparar(g);
        if (video) {
            // (4.5.0) fundo = VIDEO; texto, efeitos das letras e sons continuam no codigo
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
        // 1) frio, geada, cristais (normal)
        Buf b = new Buf(g.pose().last().pose());
        if (!video) {
        b.rect(0, 0, w, h, col(MID, 0.12f * env));
        b.vinheta(w, h, Math.min(w, h) * 0.30f, ICE, 0.50f * env);
        }
        b.brilho(cx, cy, 85 * k, DEEP, (video ? 0.5f : 0.30f) * env);  // apoio de leitura (mais forte sem o fundo procedural)
        if (!video) {
        float gg = eo(cl((t - 0.2f) / 1.4f)), gl = Math.min(w, h) * 0.30f, ga = 0.75f * env;
        int[] per = reduzido ? new int[]{1, 3} : new int[]{0, 1, 2, 3};
        for (int e = 0; e < 4; e++) {
            for (int q : per) {
                int sd = e * 4 + q;
                float u = 0.12f + 0.25f * q + 0.05f * (rnd(sd, 71) - 0.5f);
                float gx = e < 2 ? w * u : (e == 2 ? 0 : w), gy = e < 2 ? (e == 0 ? 0 : h) : h * u;
                float ang = (e == 0 ? 90 : e == 1 ? -90 : e == 2 ? 0 : 180) * DEG + (rnd(sd, 72) - 0.5f) * 30 * DEG;
                gelo(b, gx, gy, ang, gl, 3, sd * 31 + 5, gg, ga, k);
            }
        }
        float cg = eo(cl((t - 0.12f) / 0.35f));                         // trinca de vidro
        if (t > 0.12f) {
            float Rt = 110 * k * cg;
            for (int i = 0; i < 9; i++) {
                float a = i * TAU / 9 + 0.2f * rnd(i, 81), x = cx, y = cy;
                float[] rx = new float[4], ry = new float[4];
                for (int j = 0; j < 4; j++) {
                    a += (rnd(i * 7 + j, 82) - 0.5f) * 0.3f;
                    float step = Rt / 4 * (0.8f + 0.4f * rnd(i * 7 + j, 83)), nx = x + cos(a) * step, ny = y + sin(a) * step;
                    b.seg(x, y, nx, ny, (1.6f - 0.25f * j) * k * 0.5f, col(WHITE, 0.7f * env), col(WHITE, 0.55f * env));
                    rx[j] = nx; ry[j] = ny; x = nx; y = ny;
                }
                if (i > 0) {
                    float[] px = prevX, py = prevY;
                    for (int j = 1; j < 4; j++) if (px != null) b.seg(px[j], py[j], rx[j], ry[j], 0.7f * k * 0.5f, col(LIGHT, 0.4f * env), col(LIGHT, 0.4f * env));
                }
                prevX = rx; prevY = ry;
            }
        }
        int NC = reduzido ? 7 : 12;                                      // cristais facetados
        float cg2 = eo(cl((t - 0.3f) / 1.2f));
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < NC; i++) {
                float x = (i + 0.2f + 0.6f * rnd(i + side * 20, 91)) / NC * w, bw = (4 + 5 * rnd(i, 92)) * k;
                float hg = (30 + 60 * rnd(i + side * 20, 93)) * k * cg2 * (side == 0 ? 0.7f : 1f);
                float y0 = side == 0 ? 0 : h, ty2 = side == 0 ? hg : h - hg;
                float a = 0.85f * env;
                b.tri(x - bw, y0, col(LIGHT, a), x, ty2, col(WHITE, a), x, y0, col(LIGHT, a));
                b.tri(x, y0, col(MID, a), x, ty2, col(LIGHT, a), x + bw, y0, col(MID, a));
                b.seg(x - bw, y0, x, ty2, 1.0f, col(WHITE, 0.6f * env), col(WHITE, 0.9f * env));
            }
        }
        } // (4.5.0) fim do fundo procedural (camada 1)
        float ra = 0.8f * env;                                           // pingentes sob as letras
        for (int i = 0; i < len; i++) {
            float age = t - 0.9f - 0.1f * i;
            if (age <= 0) continue;
            float L = Math.min((5 + 8 * rnd(i, 95)) * k, age * 10 * k), dx = xs[i] + cw[i] * S * (0.3f + 0.4f * rnd(i, 96)), y0 = ty + 7.5f * S;
            b.tri(dx - 1.3f * k, y0, col(LIGHT, ra), dx + 1.3f * k, y0, col(MID, ra), dx, y0 + L, col(WHITE, ra));
        }
        b.flush();

        // 2) brilho (aditivo)
        aditivo();
        b = new Buf(g.pose().last().pose());
        if (!video) {
        if (!reduzido && t > 0.12f && t < 0.3f) b.rect(0, 0, w, h, col(LIGHT, (1 - (t - 0.12f) / 0.18f) * 0.4f));
        b.brilho(cx, cy, 80 * k * rev, GLOW, 0.30f * env);               // floco de neve
        float R = 62 * k * rev, rot = t * 10 * DEG;
        for (int a = 0; a < 6; a++) {
            float an = rot + a * TAU / 6, ux = cos(an), uy = sin(an);
            b.seg(cx, cy, cx + ux * R, cy + uy * R, 1.4f * k * 0.6f, col(WHITE, 0.85f * env), col(LIGHT, 0.5f * env));
            for (int br = 0; br < 2; br++) {
                float pos = br == 0 ? 0.45f : 0.7f, bl = (br == 0 ? 0.28f : 0.2f) * R, bx = cx + ux * R * pos, by = cy + uy * R * pos;
                for (int sg = -1; sg <= 1; sg += 2) {
                    float ba = an + sg * 60 * DEG;
                    b.seg(bx, by, bx + cos(ba) * bl, by + sin(ba) * bl, 1.0f * k * 0.6f, col(WHITE, 0.7f * env), col(LIGHT, 0.2f * env));
                }
            }
            b.diamante(cx + ux * R, cy + uy * R, 2.5f * k, 2.5f * k, col(WHITE, 0.9f * env));
        }
        for (int a = 0; a < 6; a++) {                                    // hexágono
            float a0 = -rot * 0.6f + a * TAU / 6, a1 = -rot * 0.6f + (a + 1) * TAU / 6, rr = R * 0.55f;
            b.seg(cx + cos(a0) * rr, cy + sin(a0) * rr, cx + cos(a1) * rr, cy + sin(a1) * rr, 1.0f * k * 0.6f, col(LIGHT, 0.6f * env), col(LIGHT, 0.6f * env));
        }
        b.anel(cx, cy, R * 1.12f, 90, 1.0f * k * 0.6f, 0, GLOW, 0.5f * env);

        int NS = reduzido ? 45 : 90;                                     // nevasca
        for (int i = 0; i < NS; i++) {
            float p = fr(t * (0.18f + 0.25f * rnd(i, 2)) + rnd(i, 3));
            float px = rnd(i, 1) * w * 1.3f - p * w * 0.3f + sin(t * 2 + i) * 8, py = -5 + p * (h + 10);
            float sz = (0.8f + 2.2f * rnd(i, 4)) * k * 0.8f;
            b.diamante(px, py, sz * 0.7f, sz, col(i % 3 == 0 ? LIGHT : WHITE, env * 0.8f));
        }
        for (int i = 0; i < 6; i++) {                                    // brilhos em estrela
            float tw = (float) Math.pow(Math.max(0, sin(t * 2.5f + i * 2.1f)), 8);
            if (tw < 0.05f) continue;
            float px = w * (0.1f + 0.8f * rnd(i, 101)), py = h * (0.1f + 0.8f * rnd(i, 102)), s = (6 + 6 * rnd(i, 103)) * k * tw;
            b.diamante(px, py, s * 0.12f, s, col(WHITE, env * tw)); b.diamante(px, py, s, s * 0.12f, col(WHITE, env * tw));
        }
        } // (4.5.0) fim do fundo procedural (camada 2)
        b.flush();

        // 3) texto: congela letra a letra, reflexo varrendo
        normal();
        float sweep = -0.3f + 1.6f * fr((t - 1.2f) / 2.2f);
        for (int i = 0; i < len; i++) {
            float la = cl((t - 0.4f - 0.08f * i) / 0.3f);
            int a8 = (int) (la * env * 255);
            if (a8 < 8) continue;
            float pos = len <= 1 ? 0.5f : (float) i / (len - 1), mix = cl(1 - Math.abs(pos - sweep) / 0.18f);
            int c = mix(0xE1F5FF, 0xFFFFFF, mix);
            String ch = msg.substring(i, i + 1);
            g.pose().pushPose();
            g.pose().translate(xs[i], ty, 0);
            g.pose().scale(S, S, 1);
            int glow = ((int) (a8 * 0.35f) << 24) | 0x50A0FF;
            if (video) contornoEscuro(g, f, ch, 0, 0, a8, CONTORNO_VIDEO);
            g.drawString(f, ch, -1, 0, glow, false); g.drawString(f, ch, 1, 0, glow, false);
            g.drawString(f, ch, 0, -1, glow, false); g.drawString(f, ch, 0, 1, glow, false);
            g.drawString(f, ch, -1.5f, 0, ((int) (a8 * 0.35f) << 24) | 0x78E6FF, false);
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
                g.drawString(f, sub, 0, 0, (a8s << 24) | 0xD2F0FF, true);
                g.pose().popPose();
            }
        }
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
    }

    // ------------------------------------------------------------------ peças
    private static float[] prevX, prevY;

    /** geada em dendrito: cada galho tem 4 segmentos e bifurca a ±60° nos nós 1 e 3. */
    private static void gelo(Buf b, float x, float y, float ang, float len, int dep, int seed, float g, float a, float k) {
        if (dep <= 0 || g <= 0) return;
        float L = len * Math.min(1, g), px = x, py = y, an = ang;
        for (int i = 0; i < 4; i++) {
            an += (rnd(seed + i, 21) - 0.5f) * 0.12f;
            float nx = px + cos(an) * L / 4, ny = py + sin(an) * L / 4;
            b.seg(px, py, nx, ny, dep * 0.45f * k, col(ICE, a), col(ICE, a * 0.8f));
            if ((i == 1 || i == 3) && g > 0.4f) {
                float cg = cl((g - 0.4f) / 0.6f);
                gelo(b, nx, ny, an + 60 * DEG, len * 0.45f, dep - 1, seed * 7 + i * 13 + dep, cg, a * 0.9f, k);
                gelo(b, nx, ny, an - 60 * DEG, len * 0.45f, dep - 1, seed * 5 + i * 11 + dep, cg, a * 0.9f, k);
            }
            px = nx; py = ny;
        }
    }

    private static void sons(float t) {
        if (!impacto && t >= 0.12f) { impacto = true; tocar(SoundEvents.PLAYER_HURT_FREEZE, 1.0f, 0.8f); tocar(SoundEvents.GLASS_BREAK, 1.8f, 0.5f); }
        while (chIdx < CH.length && t >= CH[chIdx]) { tocar(SoundEvents.AMETHYST_BLOCK_CHIME, CP[chIdx], 0.7f); chIdx++; }
        if (!fim && t >= DUR - 0.9f) { fim = true; tocar(SoundEvents.GLASS_BREAK, 1.3f, 0.4f); }
    }
}
