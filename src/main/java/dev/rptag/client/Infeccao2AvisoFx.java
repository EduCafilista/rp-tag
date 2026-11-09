package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * (5.24.3) ESTILO 39 — <b>INFECÇÃO (variante)</b> —
 * {@code /rp admin avisar <jogadores> infeccao2 <texto>}.
 *
 * <p>O dono mandou DOIS protótipos de infecção e disse que <i>"os dois valem"</i>. O
 * {@link InfeccaoAvisoFx} (estilo 38) é o cinematográfico; <b>este</b> é o porte do
 * {@code InfeccaoAviso} — o <b>estilo Minecraft</b>, 640×360, com cara de HUD do jogo:
 * quadro preto com moldura que troca de cor, a <b>silhueta do infectado</b> com as manchas
 * vermelhas acendendo pelo corpo, as barras em <b>bloquinhos</b> de FORÇA e SANIDADE, os
 * <b>dez corações</b> de pixel art sumindo, os olhos surgindo nas beiradas e os sussurros.
 *
 * <h2>Os quatro estágios (10 s; o protótipo tem 20)</h2>
 * <ol>
 *   <li>0,0–1,5 s — <b>"ALGO ENTROU EM VOCÊ"</b>: uma coceira sob a pele;</li>
 *   <li>1,5–3,5 s — <b>"FÚRIA... FORÇA?"</b>: a FORÇA sobe a 100 e a SANIDADE começa a cair;</li>
 *   <li>3,5–6,0 s — <b>"O CORPO CEDE"</b>: a força desaba, o corpo racha, as manchas acendem;</li>
 *   <li>6,0–9,0 s — <b>"SANIDADE: ???"</b>: sussurros, olhos, as barras zerando;</li>
 *   <li>9,0–10 s — preto, e a frase final na caixa branca: <b>"VOCÊ NUNCA ESTEVE SÃO"</b>.</li>
 * </ol>
 *
 * <p>A frase que o ADM escreveu ocupa o lugar da "dor" do protótipo (é o texto que aparece
 * correndo e se decompondo — o resto é a arte). Tudo em triângulo e texto, erro desliga o
 * efeito na sessão, texto só depois do {@code flush()}.
 */
public final class Infeccao2AvisoFx {

    public static final int ESTILO = 39;
    private static final float DUR = 10.0F;
    private static final String LIXO = "\u2593\u2592\u2591#@%&?";

    private static final String[] SUSSURROS = {
        "<???> te vejo", "<???> n\u00e3o olhe pra tr\u00e1s", "<???> ele est\u00e1 dentro",
        "<???> voc\u00ea \u00e9 o pr\u00f3ximo", "<???> pare de resistir", "<???> sua m\u00e3o n\u00e3o \u00e9 sua",
        "<???> eu sou voc\u00ea", "<???> abra os olhos"
    };
    private static final int[][] MANCHAS = {{0, -95}, {0, -65}, {0, -25}, {-42, -35}, {42, -35}, {-14, 55}, {14, 55}, {0, -80}};
    private static final String[] CORACAO = {"0110110", "1111111", "1111111", "0111110", "0011100", "0001000"};
    private static final float[] ANGU = new float[22];

    private static boolean ativo, falhou;
    private static long inicio;
    private static String fala = "";
    private static String[] linhas = new String[0];
    private static boolean pronto;

    // grade do protótipo (640 × 360) → tela
    private static float ES = 1F, OX, OY;

    private Infeccao2AvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "aviso_infeccao2"),
                Infeccao2AvisoFx::render);
    }

    public static void iniciar(String texto) {
        ativo = true;
        falhou = false;
        inicio = System.currentTimeMillis();
        // (5.26.0) A TRILHA da variante "de Minecraft": Warden farejando, slime espremendo,
        // esporos estourando e o baque final — sintetizada em arte/audio_infeccao.py.
        try {
            tocar(dev.rptag.RPTagMod.SOM_INFECCAO2.get(), 1.0F, 1.0F);
        } catch (Throwable t) {
            dev.rptag.RPTagMod.LOGGER.warn("RP Tag: trilha da infeccao 2 falhou", t);
        }
        fala = texto == null ? "" : texto;
        linhas = quebrar(fala.replaceAll("\u00a7.", ""), 40);
        if (!pronto) {
            for (int i = 0; i < ANGU.length; i++) {
                ANGU[i] = rnd(i, 3) * TAU;
            }
            pronto = true;
        }
    }

    public static void parar() {
        ativo = false;
    }

    // ------------------------------------------------------------------ utilidades
    private static float ez(float x) {
        float u = cl(x);
        return u * u * (3F - 2F * u);
    }

    private static float X(float x) {
        return OX + x * ES;
    }

    private static float Y(float y) {
        return OY + y * ES;
    }

    private static String[] quebrar(String s, int max) {
        if (s == null || s.isBlank()) {
            return new String[0];
        }
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String par : s.split("\n")) {
            StringBuilder linha = new StringBuilder();
            for (String palavra : par.split(" ")) {
                if (linha.length() > 0 && linha.length() + palavra.length() + 1 > max) {
                    out.add(linha.toString());
                    linha.setLength(0);
                }
                if (linha.length() > 0) {
                    linha.append(' ');
                }
                linha.append(palavra);
            }
            if (linha.length() > 0) {
                out.add(linha.toString());
            }
        }
        return out.toArray(new String[0]);
    }

    private static String embaralhar(String s, float p, int quadro) {
        if (s == null || s.isEmpty() || p <= 0.01F) {
            return s == null ? "" : s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != ' ' && rnd(i + quadro * 17, 6) < p) {
                sb.append(LIXO.charAt((int) (rnd(i, quadro) * LIXO.length()) % LIXO.length()));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------- desenho
    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
        if (!ativo || falhou) {
            return;
        }
        try {
            float T = (System.currentTimeMillis() - inicio) / 1000F;
            if (T > DUR) {
                ativo = false;
                return;
            }
            int W = g.guiWidth(), H = g.guiHeight();
            ES = Math.min(W / 640F, H / 360F);
            OX = W / 2F - 320F * ES;
            OY = H / 2F - 180F * ES;
            int quadro = (int) Math.floor(T * 12F);
            float t = T;

            // ------------------------------------------------- os estágios
            int p;
            float inf, forca, san, vida, k = 0F;
            String titulo;
            if (T < 1.5F) {
                p = 1;
                inf = lerp(0F, 0.2F, T / 1.5F);
                forca = 40F;
                san = 100F;
                vida = 10F;
                titulo = "ALGO ENTROU EM VOC\u00ca";
            } else if (T < 3.5F) {
                p = 2;
                k = (T - 1.5F) / 2F;
                inf = lerp(0.2F, 0.45F, k);
                forca = lerp(40F, 100F, k);
                san = lerp(100F, 65F, k);
                vida = 10F;
                titulo = "F\u00daRIA... FOR\u00c7A?";
            } else if (T < 6.0F) {
                p = 3;
                k = (T - 3.5F) / 2.5F;
                inf = lerp(0.45F, 0.85F, k);
                forca = lerp(100F, 5F, k);
                san = lerp(65F, 25F, k);
                vida = lerp(10F, 2F, k);
                titulo = "O CORPO CEDE";
            } else if (T < 9.0F) {
                p = 4;
                k = (T - 6.0F) / 3F;
                inf = 1F;
                forca = lerp(5F, 0F, k);
                san = lerp(25F, 0F, k);
                vida = lerp(2F, 0F, k);
                titulo = "SANIDADE: ???";
            } else {
                p = 4;
                k = 1F;
                inf = 1F;
                forca = 0F;
                san = 0F;
                vida = 0F;
                titulo = "";
            }
            float mad = 1F - san / 100F;
            float pulso = 0.5F + 0.5F * sin(t * (2F + mad * 12F));
            float pk = p >= 3 ? (p == 3 ? k : 1F) : p == 2 ? k * 0.3F : 0F;
            float aTela = cl(T / 0.3F) * (T > 9.2F ? cl((DUR - T) / 0.6F) : 1F);

            AvisoFxUtil.preparar(g);
            AvisoFxUtil.Buf b = AvisoRitual.comecar(g);

            // fundo (com o tremor da loucura)
            float trem = mad * 10F;
            b.rect(0, 0, W, H, col(0x0C060A, 1F));
            float dx = (rnd(quadro, 1) - 0.5F) * trem, dy = (rnd(quadro, 2) - 0.5F) * trem;

            // as veias saindo do meio (o túnel)
            for (int vi = 0; vi < ANGU.length; vi++) {
                float ang = ANGU[vi];
                int segs = (int) (20F * inf);
                if (segs < 2) {
                    continue;
                }
                int corVeia = col(mix(0x6E0028, 0xEC0030, pulso), 0.85F * aTela);
                float antX = 0, antY = 0;
                for (int q = 0; q <= segs; q++) {
                    float rr = 400F * (1F - q / 40F * 0.9F);
                    float wv = sin(q * 0.5F + ang * 5F + t * (2F + mad * 6F)) * (14F + mad * 20F);
                    float x = 320F + cos(ang) * rr + cos(ang + 1.57F) * wv;
                    float y = 180F + sin(ang) * rr + sin(ang + 1.57F) * wv;
                    if (q > 0) {
                        b.seg(X(antX) + dx, Y(antY) + dy, X(x) + dx, Y(y) + dy,
                                Math.max(1F, (1.5F + pulso * 3F) * ES), corVeia, corVeia);
                    }
                    antX = x;
                    antY = y;
                }
            }

            // o quadro preto com a moldura que troca de cor
            b.rect(X(30) + dx, Y(25) + dy, 580F * ES, 310F * ES, col(0x000000, 0.70F * aTela));
            int corMolde = (int) (lerp(0.2F, 1F, mad) * 255F) << 16
                    | (int) (lerp(0.9F, 0.05F, mad) * 255F) << 8 | 0x33;
            float molde = Math.max(2F, 4F * ES);
            b.rect(X(30) + dx, Y(25) + dy, 580F * ES, molde, corMolde);
            b.rect(X(30) + dx, Y(335) + dy - molde, 580F * ES, molde, corMolde);
            b.rect(X(30) + dx, Y(25) + dy, molde, 310F * ES, corMolde);
            b.rect(X(610) + dx - molde, Y(25) + dy, molde, 310F * ES, corMolde);

            // ------------------------------------------- a silhueta do infectado
            float cx = 130F, cy = 175F, j = mad * 7F;
            int corpo = col(0xBEA0AF, 0.35F * aTela);
            b.circ(X(cx - 16F) + dx + (rnd(quadro, 3) - 0.5F) * j, Y(cy - 104F) + dy + (rnd(quadro, 4) - 0.5F) * j, 16F * ES, corpo);
            b.rect(X(cx - 22F) + dx, Y(cy - 68F) + dy, 44F * ES, 85F * ES, corpo);
            b.rect(X(cx - 44F) + dx + (rnd(quadro, 5) - 0.5F) * j, Y(cy - 65F) + dy, 18F * ES, 70F * ES, corpo);
            b.rect(X(cx + 26F) + dx + (rnd(quadro, 6) - 0.5F) * j, Y(cy - 65F) + dy, 18F * ES, 70F * ES, corpo);
            b.rect(X(cx - 20F) + dx, Y(cy + 17F) + dy, 16F * ES, 72F * ES, corpo);
            b.rect(X(cx + 4F) + dx, Y(cy + 17F) + dy, 16F * ES, 72F * ES, corpo);
            // as manchas vermelhas acendendo pelo corpo
            int ativas = (int) Math.ceil(pk * 8F);
            for (int i = 0; i < ativas && i < MANCHAS.length; i++) {
                float sx = cx + MANCHAS[i][0], sy = cy + MANCHAS[i][1];
                float rr = 10F + pulso * 14F + mad * 10F;
                AvisoRitual.halo(b, X(sx) + dx, Y(sy) + dy, 0F, rr * ES, 0xFF2828, 0.85F * aTela);
            }

            // ------------------------------------------- as barras e os corações
            barraBlocos(b, 240, 160, forca, 0xFF4500, dx, dy, aTela);
            barraBlocos(b, 240, 210, san, 0x9370DB, dx, dy, aTela);
            for (int i = 0; i < 10; i++) {
                boolean vivo = i < Math.ceil(vida);
                float sh = vivo && vida < 5F ? (rnd(quadro + i, 7) - 0.5F) * 3F : 0F;
                int cor = vivo ? 0xEE2222 : 0x2A1A1A;
                if (p == 4 && rnd(quadro + i, 9) < mad * 0.25F) {
                    cor = rnd(quadro, i) < 0.5F ? 0xAA00FF : 0x000000;
                }
                coracao(b, 240F + i * 26F + sh + dx, 260F + sh + dy, cor, aTela);
            }

            // ------------------------------------------- olhos nas beiradas
            if (mad > 0.35F) {
                int cnt = (int) (mad * 16F);
                for (int i = 0; i < cnt; i++) {
                    float ex = 20F + rnd(i, 1) * 600F, ey = 15F + rnd(i, 2) * 330F;
                    float esc = 0.6F + rnd(i, 3) * 1.1F;
                    float ab = Math.max(0F, sin(t * 1.5F + rnd(i, 4) * TAU));
                    if (rnd(i, quadro) < 0.04F) {
                        ab = 0.04F;
                    }
                    float ry = 9F * esc * Math.min(1F, ab * 2.5F + 0.1F), rx = 15F * esc;
                    float exd = (320F - ex) * 0.02F, eyd = (180F - ey) * 0.02F;
                    b.elipse(X(ex) + dx, Y(ey) + dy, 1F, 0F, rx * ES, ry * ES, col(0xE8E0D0, aTela));
                    float pr = Math.min(ry, 6F * esc);
                    b.circ(X(ex + exd) + dx, Y(ey + eyd) + dy, pr * ES, col(0x660000, aTela));
                }
            }

            // ------------------------------------------- glitch do protótipo
            // (o snapshot do JavaFX virou tarjas deslocadas + um véu aditivo)
            if (mad > 0.05F) {
                int sl = (int) (mad * 8F);
                for (int i = 0; i < sl; i++) {
                    if (rnd(i + quadro, 11) < mad * 0.7F) {
                        float sy = rnd(i, quadro + 1) * (H - 40F), hh = 4F + rnd(i, 5) * 30F;
                        b.rect((rnd(i, 6) - 0.5F) * mad * 80F, sy, W, hh, col(0xFF2050, 0.10F * aTela));
                    }
                }
            }
            // linhas de varredura e vinheta
            for (int yy = 0; yy < H; yy += 4) {
                b.rect(0, yy, W, 1, col(0x000000, 0.18F * aTela));
            }
            b.vinheta(W, H, H * 0.55F, 0x8C0014, (0.35F + mad * 0.55F * pulso) * aTela);
            b.flush();
            if (mad > 0.05F) {
                b = AvisoRitual.comecar(g);
                AvisoFxUtil.aditivo();
                b.rect(mad * 8F, 0, W, H, col(0x802040, 0.08F * mad * aTela));
                b.flush();
                AvisoFxUtil.normal();
            }

            // ------------------------------------------- os textos (sempre depois do flush)
            var fonte = Minecraft.getInstance().font;
            float escT = Math.max(1.2F, 2.0F * ES);
            if (p < 5 && !titulo.isEmpty()) {
                String t1 = embaralhar(titulo, mad * 0.6F, quadro);
                float larg = fonte.width(t1) * escT;
                AvisoRitual.texto(g, t1, X(425) + dx - larg / 2F, Y(75) + dy, escT,
                        col(mix(0xFF5030, 0xFF5050, mad), aTela), true);
            }
            // a frase do ADM no lugar da "dor" (correndo e se decompondo)
            float escF = Math.max(1.0F, 1.25F * ES);
            float yF = Y(112);
            for (int i = 0; i < linhas.length; i++) {
                String l = embaralhar(linhas[i], mad * 0.45F, quadro + i);
                float larg = fonte.width(l) * escF;
                float lx = X(425) + dx - larg / 2F;
                if (p == 4 && rnd(quadro + i, 12) < 0.08F) {
                    // o "flip" do protótipo: a linha aparece espelhada por um quadro
                    AvisoRitual.texto(g, l, lx, yF + i * 18F * ES, -escF, col(0xD3D3D3, aTela), false);
                } else {
                    AvisoRitual.texto(g, l, lx, yF + i * 18F * ES, escF,
                            col(p >= 3 ? 0xFF7B7B : 0xD3D3D3, aTela), true);
                }
            }
            // sussurros do estágio final (o protótipo joga essas frases pelas beiradas)
            if (p == 4) {
                int nl = (int) (k * 8F) + 1;
                for (int i = 0; i < nl; i++) {
                    int s = (int) (t * 1.3F + i * 5F);
                    String w = SUSSURROS[s % SUSSURROS.length];
                    float px = (s * 97F + i * 53F) % 420F, py = 40F + (s * 61F + i * 37F) % 290F;
                    AvisoRitual.texto(g, embaralhar(w, 0.3F, quadro + i), X(px) + dx, Y(py) + dy,
                            Math.max(0.8F, 1.1F * ES),
                            col(0xEBEBEB, (0.25F + 0.35F * Math.abs(sin(t * 3F + i))) * aTela), false);
                }
            }
            AvisoRitual.texto(g, "FOR\u00c7A", X(240), Y(155), 1.0F, col(0xFFFFFF, aTela), true);
            AvisoRitual.texto(g, "SANIDADE", X(240), Y(205), 1.0F, col(0xFFFFFF, aTela), true);
            AvisoRitual.texto(g, "VIDA", X(240), Y(252), 1.0F, col(0xFFFFFF, aTela), true);

            // ------------------------------------------- o fim: "VOCÊ NUNCA ESTEVE SÃO"
            if (T >= 9.0F) {
                b = AvisoRitual.comecar(g);
                b.rect(0, 0, W, H, col(0x000000, aTela));
                for (int i = 0; i < 8; i++) {
                    float ex = rnd(i, 21) * W, ey = rnd(i, 22) * H;
                    b.elipse(ex, ey, 1F, 0F, 16F * ES, 9F * ES, col(0xE8E0D0, aTela));
                    b.circ(ex, ey, 5F * ES, col(0x660000, aTela));
                }
                b.rect(W / 2F - 210F * ES, H / 2F - 26F * ES, 420F * ES, 52F * ES, col(0xFFFFFF, aTela));
                b.flush();
                String fim = embaralhar("VOC\u00ca NUNCA ESTEVE S\u00c3O", 0.04F, quadro);
                float escFim = Math.max(1.4F, 2.1F * ES);
                float larg = fonte.width(fim) * escFim;
                AvisoRitual.texto(g, fim, W / 2F - larg / 2F, H / 2F - 6F * ES, escFim, col(0x000000, aTela), false);
            }
        } catch (Throwable t) {
            falhou = true;
            RPTagMod.LOGGER.warn("RP Tag: aviso INFECCAO 2 desligado nesta sessão", t);
        }
    }

    /** barra de 16 bloquinhos, como no protótipo. */
    private static void barraBlocos(AvisoFxUtil.Buf b, float x, float y, float v, int cor, float dx, float dy, float a) {
        for (int i = 0; i < 16; i++) {
            int c = i < v / 6.25F ? col(cor, a) : col(0x2E2E2E, a);
            b.rect(X(x + i * 17F) + dx, Y(y) + dy, 14F * ES, 14F * ES, c);
        }
    }

    /** coração de pixel art (7×6). */
    private static void coracao(AvisoFxUtil.Buf b, float x, float y, int cor, float a) {
        for (int j = 0; j < CORACAO.length; j++) {
            for (int i = 0; i < 7; i++) {
                if (CORACAO[j].charAt(i) == '1') {
                    b.rect(X(x + i * 3F), Y(y + j * 3F), 3F * ES, 3F * ES, col(cor, a));
                }
            }
        }
    }
}
