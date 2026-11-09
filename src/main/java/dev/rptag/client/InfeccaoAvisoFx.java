package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * (5.24.3) ESTILO 38 — <b>INFECÇÃO</b> ({@code /rp admin avisar <jogadores> infeccao <texto>}).
 *
 * <p>Pedido do dono: <i>"https://n.uguu.se/FXhMykwO.java avisar (INFECÇAO) no avisar nao no avisar2"</i>.
 * Este é o porte do protótipo <b>InfectionWarning</b> (JavaFX, 800×600): o infectado acha que está
 * ficando mais forte — a barra de <b>FORÇA sobe</b> — mas a <b>SANIDADE desaba</b>.
 *
 * <h2>A cena (10 s, o protótipo tem 14 — o resto é proporcional)</h2>
 * <ol>
 *   <li>a tela afunda num vinho escuro pulsando na batida, com <b>células e esporos</b> flutuando;</li>
 *   <li>uma <b>rede de veias ramificadas</b> cresce das quatro bordas para o centro (18 raízes,
 *       ramos de 3 níveis, cada seguimento com sua hora de nascer);</li>
 *   <li>no meio do caminho abre o <b>olho do infectado</b> — amêndoa com vasos, íris amarelo-verde
 *       e a pupila que <b>encolhe até virar um ponto</b> enquanto a loucura sobe;</li>
 *   <li>o HUD mostra as duas barras (FORÇA subindo, SANIDADE caindo) e um <b>apagão</b> leva tudo;</li>
 *   <li>volta o <b>"AVISO" contaminado</b>: letras tremendo com sangue escorrendo e o <b>"O" virando
 *       outro olho</b>, e as frases entram digitadas (a do ADM é a do meio, grande).</li>
 * </ol>
 *
 * <p>Como todos os avisos da casa: nada de arquivo, tudo triângulo e texto, erro desliga o efeito
 * na sessão e o texto só é desenhado depois do {@code flush()} (a lição do 5.16.4).
 */
public final class InfeccaoAvisoFx {

    public static final int ESTILO = 38;
    private static final float DUR = 10.0F;
    private static final String GLITCH = "\u2593\u2592\u2591#@%&?";

    private static boolean ativo, falhou;
    private static long inicio;
    private static String fala = "";
    private static String[] linhas = new String[0];
    private static String[] flavor1 = new String[0];
    private static String[] flavor3 = new String[0];

    /** segmentos de veia: x0, y0, x1, y1, hora de nascer (s), espessura (grade 800×600). */
    private static final java.util.List<float[]> VEIAS = new java.util.ArrayList<>();
    private static boolean veiasProntas;

    // câmera (grade do protótipo → tela), com o tremor
    private static float ES = 1F, OX, OY;

    private InfeccaoAvisoFx() {
    }

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "aviso_infeccao"),
                InfeccaoAvisoFx::render);
    }

    public static void iniciar(String texto) {
        ativo = true;
        falhou = false;
        inicio = System.currentTimeMillis();
        // (5.26.0) A TRILHA — pedido do dono: "o avisar Infecçao merece um som mais irado, algo
        // que dê para mostrar o que se está passando na animação". 10 s, sintetizada em
        // arte/audio_infeccao.py justamente pra bater com a cena: coração acelerando, veias
        // crescendo, olho abrindo, sussurros, o surto e o apagão nos 8,2 s.
        try {
            tocar(dev.rptag.RPTagMod.SOM_INFECCAO.get(), 1.0F, 1.0F);
        } catch (Throwable t) {
            dev.rptag.RPTagMod.LOGGER.warn("RP Tag: trilha da infeccao falhou", t);
        }
        fala = texto == null ? "" : texto;
        linhas = quebrar(fala.replaceAll("\u00a7.", ""), 44);
        flavor1 = quebrar("Ela promete for\u00e7a, mas consome a mente de quem a recebe.", 56);
        flavor3 = quebrar("Quando a sanidade acaba, s\u00f3 resta a f\u00faria.", 56);
        construirVeias();
    }

    public static void parar() {
        ativo = false;
    }

    // ------------------------------------------------------------------ utilidades
    private static float nf(float x) {
        return fr((float) Math.sin(x * 12.9898F) * 43758.5453F);
    }

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

    /** troca letras por lixo (o "corrupt" do protótipo), de forma determinística por quadro. */
    private static String corromper(String s, float quanto, int quadro) {
        if (s == null || s.isEmpty() || quanto <= 0.01F) {
            return s == null ? "" : s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != ' ' && rnd(i + quadro * 31, 5) < quanto) {
                sb.append(GLITCH.charAt((int) (rnd(i, quadro) * GLITCH.length()) % GLITCH.length()));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // ----------------------------------------------------------- a rede de veias
    private static void crescer(float x, float y, float ang, float len, int prof, float t0, float w, float seed) {
        if (prof < 0 || len < 10F) {
            return;
        }
        int segs = Math.max(2, (int) Math.floor(len / 36F));
        float px = x, py = y, a = ang;
        for (int i = 0; i < segs; i++) {
            a += (nf(seed + i * 7.3F) - 0.5F) * 0.9F;
            float l = len / segs;
            float nx = px + cos(a) * l, ny = py + sin(a) * l;
            float ts = t0 + i * 0.05F;
            VEIAS.add(new float[]{px, py, nx, ny, ts, w * (1F - i / (float) segs * 0.4F)});
            if (i > 0 && nf(seed + i * 3.1F) < 0.55F) {
                crescer(px, py, a + (nf(seed + i) < 0.5F ? -1F : 1F) * (0.5F + nf(seed + i * 2F) * 0.7F),
                        len * 0.55F, prof - 1, ts + 0.02F, w * 0.6F, seed * 1.7F + i);
            }
            px = nx;
            py = ny;
        }
    }

    private static void construirVeias() {
        if (veiasProntas) {
            return;
        }
        VEIAS.clear();
        float Wp = 800F, Hp = 600F;
        for (int k = 0; k < 18; k++) {
            float s = k / 18F * 2F * (Wp + Hp), x, y, a;
            if (s < Wp) {
                x = s;
                y = 0;
                a = 1.57F;
            } else if (s < Wp + Hp) {
                x = Wp;
                y = s - Wp;
                a = 3.14F;
            } else if (s < 2F * Wp + Hp) {
                x = 2F * Wp + Hp - s;
                y = Hp;
                a = -1.57F;
            } else {
                x = 0;
                y = 2F * (Wp + Hp) - s;
                a = 0;
            }
            crescer(x, y, a + (nf(k) - 0.5F) * 0.8F, 230F + nf(k + 3F) * 200F, 3,
                    nf(k + 5F) * 0.12F, 6F + nf(k + 7F) * 3F, k * 3.7F + 1F);
        }
        veiasProntas = true;
    }

    private static void desenharVeias(AvisoFxUtil.Buf b, float vp, float pulse, float a) {
        float escala = 0.714F;   // o protótipo tem 14 s, o aviso tem 10
        for (float[] s : VEIAS) {
            float f = cl((vp - s[4] * escala) / 0.06F);
            if (f <= 0F) {
                continue;
            }
            float ex = s[0] + (s[2] - s[0]) * f, ey = s[1] + (s[3] - s[1]) * f;
            float w = s[5] * (1F + 0.25F * pulse) * ES;
            b.seg(X(s[0]), Y(s[1]), X(ex), Y(ey), Math.max(1F, w * 1.8F), col(0x260208, 0.92F * a), col(0x260208, 0.92F * a));
            b.seg(X(s[0]), Y(s[1]), X(ex), Y(ey), Math.max(1F, w * 0.75F), col(0xB3121E, (0.7F + 0.3F * pulse) * a), col(0xB3121E, (0.7F + 0.3F * pulse) * a));
        }
    }

    // ------------------------------------------------------------------- o olho
    private static void poligonoOlho(float h, float[] xs, float[] ys) {
        int N = 20;
        for (int i = 0; i <= N; i++) {
            float u = i / (float) N;                       // curva de cima
            float px = (1 - u) * (1 - u) * (400 - 210) + 2 * (1 - u) * u * 400 + u * u * (400 + 210);
            float py = (1 - u) * (1 - u) * 300 + 2 * (1 - u) * u * (300 - h * 2F) + u * u * 300;
            xs[i] = X(px);
            ys[i] = Y(py);
        }
        for (int i = 0; i <= N; i++) {
            float u = i / (float) N;                       // curva de baixo
            float px = (1 - u) * (1 - u) * (400 + 210) + 2 * (1 - u) * u * 400 + u * u * (400 - 210);
            float py = (1 - u) * (1 - u) * 300 + 2 * (1 - u) * u * (300 + h * 2F) + u * u * 300;
            xs[N + 1 + i] = X(px);
            ys[N + 1 + i] = Y(py);
        }
    }

    private static void olho(AvisoFxUtil.Buf b, float o, float m, float t, float T, float a) {
        if (o <= 0.02F) {
            return;
        }
        float h = 105F * o;
        float[] xs = new float[42], ys = new float[42];
        poligonoOlho(h, xs, ys);
        // a amêndoa: base rosada + halo de esclera
        AvisoRitual.poligono(b, xs, ys, 42, col(0xD9A9A2, a));
        AvisoRitual.halo(b, X(400), Y(300), 0F, 230F * ES, 0xF6E3DC, 0.55F * a);
        // os vasos da esclera
        for (int i = 0; i < 22; i++) {
            int lado = i % 2 == 1 ? 1 : -1;
            float y0 = 300 + (nf(i) - 0.5F) * 90F, x0 = 400 + lado * (210 - nf(i + 3F) * 40F);
            float jx = m > 0.5F ? (nf(i + 40F + (int) Math.floor(t * 8F)) - 0.5F) * 2F : 0F;
            AvisoRitual.polilinha(b, new float[]{X(x0), X(400 + lado * (120 + nf(i + 5F) * 30F)), X(400 + lado * (60 + nf(i + 9F) * 10F) + jx)},
                    new float[]{Y(y0), Y(y0 + (nf(i + 7F) - 0.5F) * 60F), Y(300 + (nf(i + 11F) - 0.5F) * 50F)}, 3,
                    Math.max(1F, 1.6F * ES), col(0xB3121E, (0.35F + 0.6F * m) * a));
        }
        // íris: o amarelo doente que vai perdendo a razão
        float ix = 400 + m * (nf(7F + (int) Math.floor(t * 6F)) - 0.5F) * 10F + sin(t * 3F) * 4F * m;
        float iy = 300 + (nf(11F + (int) Math.floor(t * 6F)) - 0.5F) * m * 6F;
        AvisoRitual.halo(b, X(ix), Y(iy), 0F, 72F * ES, 0xD9E040, 0.85F * a);
        b.circ(X(ix), Y(iy), 46F * ES, col(0x7A8A14, a));
        b.circ(X(ix), Y(iy), 30F * ES, col(0xD9E040, a));
        float pr = T < 3.5F ? 26F + 22F * ez((T - 3.0F) / 0.45F) : 48F - 44F * ez((T - 3.5F) / 1.0F);
        for (int i = 0; i < 30; i++) {
            float ang = i / 30F * TAU;
            b.seg(X(ix + cos(ang) * pr), Y(iy + sin(ang) * pr), X(ix + cos(ang) * 70F), Y(iy + sin(ang) * 70F),
                    Math.max(1F, 1.0F * ES), col(0x141E00, 0.5F * a), col(0x141E00, 0.5F * a));
        }
        // pupila (encolhe até virar um ponto) + brilho
        b.circ(X(ix), Y(iy), Math.max(2F, pr + (nf(3F + (int) Math.floor(t * 9F)) - 0.5F) * m * 4F) * ES, col(0x000000, a));
        b.elipse(X(ix - 27F), Y(iy - 25F), 1F, 0F, 9F * ES, 5F * ES, col(0xFFFFFF, 0.8F * a));
        // o contorno grosso (a pálpebra)
        for (int i = 0; i < 41; i++) {
            b.seg(xs[i], ys[i], xs[(i + 1) % 42], ys[(i + 1) % 42], Math.max(2F, 9F * ES), col(0x1A0308, a), col(0x1A0308, a));
        }
    }

    // --------------------------------------------------------------------- barra
    private static void barra(AvisoFxUtil.Buf b, float x, float y, float val, int cor, boolean treme, float a, float t) {
        // moldura
        int molde = col(0xE6D2CD, 0.8F * a);
        b.rect(X(x), Y(y), 300F * ES, 16F * ES, molde);
        b.rect(X(x + 2), Y(y + 2), 296F * ES, 12F * ES, col(0x140206, a));
        float v = cl(val / 100F) * (treme ? 0.9F + 0.2F * nf((int) Math.floor(t * 20F)) : 1F);
        b.rect(X(x + 2), Y(y + 2), Math.max(0F, 296F * cl(v)) * ES, 12F * ES, col(cor, a));
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
            float t = T;
            float m = ez((T - 2.6F) / 2.0F);
            float vp = ez((T - 0.6F) / 3.9F);
            boolean pos = T > 4.95F;
            float bpm = 1.2F + 1.4F * ez((T - 0.6F) / 2.8F) + 0.5F * sin(T * 7F) * m;
            float beat = (float) Math.pow(Math.max(0F, sin(T * (float) Math.PI * bpm)), 8);
            float tremor = (ez((T - 0.7F) / 1.7F) * 2F + m * 7F) * (T < 4.6F ? 1F : 0.35F);

            ES = Math.min(W / 800F, H / 600F);
            OX = W / 2F - 400F * ES + (nf(1F + (int) Math.floor(t * 30F)) - 0.5F) * tremor;
            OY = H / 2F - 300F * ES + (nf(7F + (int) Math.floor(t * 30F)) - 0.5F) * tremor;

            float aTela = cl(T / 0.35F) * (T > 9.2F ? cl((DUR - T) / 0.8F) : 1F);

            AvisoFxUtil.preparar(g);
            AvisoFxUtil.Buf b = AvisoRitual.comecar(g);

            // ------------------------------------------------ fundo: vinho vivo
            b.rect(0, 0, W, H, col(0x060102, 1F));
            AvisoRitual.halo(b, X(400), Y(300), 0F, 420F * ES, 0x370A0A, 0.85F * aTela);
            AvisoRitual.halo(b, X(400), Y(300), 0F, 260F * ES, 0x46080C, (0.35F + 0.25F * beat) * aTela);
            // o calor da infecção no começo
            float eu = ez((T - 1.0F) / 1.4F) * (1F - ez((T - 2.6F) / 0.7F));
            if (eu > 0.01F) {
                AvisoRitual.halo(b, X(400), Y(300), 0F, 330F * ES, 0xFF963C, 0.22F * eu * (0.8F + 0.2F * beat) * aTela);
            }
            // células e esporos
            for (int i = 0; i < 40; i++) {
                float x = fr((nf(i) * W + t * (8F + nf(i + 3F) * 22F)) / (W + 100F)) * (W + 100F) - 50F;
                float y = nf(i + 5F) * H + sin(t * 0.5F + i) * 30F;
                float rr = (6F + nf(i + 7F) * 18F) * ES;
                AvisoRitual.halo(b, x, y, rr * 0.55F, rr, 0xC83C32, 0.20F * aTela, 8);
                b.circ(x, y, rr, col(0x78141A, 0.35F * aTela));
                b.circ(x + rr * 0.2F, y - rr * 0.1F, Math.max(1F, rr * 0.3F), col(0x781419, 0.35F * aTela));
            }
            // poeira subindo
            for (int i = 0; i < 50; i++) {
                float x = nf(i + 30F) * W + sin(t + i) * 20F;
                float y = H - fr((t * (20F + nf(i + 33F) * 40F) + nf(i + 31F) * H) / H) * H;
                b.rect(x, y, 2, 2, col(0x0A0002, 0.7F * aTela));
            }
            desenharVeias(b, pos ? 1F : vp, beat, aTela);

            // ------------------------------------------------ glitch e túnel
            if (m > 0.05F) {
                AvisoFxUtil.aditivo();
                int[] cc = {0xFF003C, 0x00FFDC, 0xFFE600};
                int nGl = (int) Math.floor(m * 14F);
                for (int i = 0; i < nGl; i++) {
                    float gy = nf(i * 3F + (int) Math.floor(t * 12F)) * H;
                    b.rect((nf(i) - 0.5F) * 40F, gy, W, 2F + nf(i + 1F) * 16F, col(cc[i % 3], 0.08F + 0.12F * m));
                }
                AvisoFxUtil.normal();
            }
            float tin = T < 4.8F ? m : 0.35F;
            // a visão em túnel: a borda fecha conforme a loucura sobe
            b.vinheta(W, H, H * (0.30F + 0.34F * tin), 0x000000, (0.68F + 0.28F * tin) * aTela);
            // grão
            for (int i = 0; i < 150 + (int) (500F * m); i++) {
                b.rect(rnd(i + (int) Math.floor(t * 30F), 4) * W, rnd(i, 8) * H, 1 + rnd(i, 2) * 1.5F, 1,
                        col(0xFFFFFF, (0.10F + 0.06F * m) * aTela));
            }
            // a batida ("dano"): um véu vermelho por cima de tudo
            float ferido = beat * (0.06F + 0.12F * m);
            if (ferido > 0.01F) {
                b.rect(0, 0, W, H, col(0xB3121E, ferido * aTela));
            }
            b.flush();

            // ------------------------------------------------ O OLHO
            if (T >= 3.0F && T < 4.8F) {
                float o = ez((T - 3.0F) / 0.45F) * (1F - ez((T - 4.45F) / 0.3F));
                b = AvisoRitual.comecar(g);
                olho(b, o, m, t, T, aTela);
                b.flush();
            }

            // ------------------------------------------------ HUD FORÇA × SANIDADE
            float forca = ForcaEm(T), sanid = SanEm(T);
            if (T > 0.6F && T < 4.8F) {
                float aH = ez((T - 0.6F) / 0.4F) * (1F - ez((T - 4.4F) / 0.35F));
                b = AvisoRitual.comecar(g);
                barra(b, 40, 52, forca + (T > 2.7F ? (nf((int) Math.floor(t * 14F)) - 0.5F) * 6F : 0F), 0xE0501E, T > 2.7F, aH, t);
                barra(b, 460, 52, sanid, 0xCFD8E0, sanid < 35F, aH, t);
                b.flush();
                String rotulo = corromper("INFEC\u00c7\u00c3O DETECTADA", m * 0.2F, (int) Math.floor(t * 6F));
                AvisoRitual.textoCentro(g, rotulo, W / 2F, Y(30), 1.4F, col(0xB3121E, aH), false);
                var fonteH = Minecraft.getInstance().font;
                AvisoRitual.texto(g, "FOR\u00c7A", X(40), Y(40), 1.2F, col(0xD9C9C4, aH), false);
                String pctF = Math.round(forca) + "%";
                AvisoRitual.texto(g, pctF, X(340) - fonteH.width(pctF) * 1.2F, Y(40), 1.2F, col(0xD9C9C4, aH), false);
                AvisoRitual.texto(g, "SANIDADE", X(460), Y(40), 1.2F, col(0xD9C9C4, aH), false);
                String pctS = Math.round(sanid) + "%";
                AvisoRitual.texto(g, pctS, X(760) - fonteH.width(pctS) * 1.2F, Y(40), 1.2F, col(0xD9C9C4, aH), false);
            }

            // ------------------------------------------------ apagão e clarão
            if (T >= 4.45F && T < 4.95F) {
                b = AvisoRitual.comecar(g);
                float f = T < 4.6F ? ez((T - 4.45F) / 0.15F) : 1F;
                b.rect(0, 0, W, H, col(0x000000, f));
                b.flush();
            }
            if (T >= 4.45F && T < 4.53F) {
                b = AvisoRitual.comecar(g);
                b.rect(0, 0, W, H, col(0xFFFFFF, 0.8F));
                b.flush();
            }

            // ------------------------------------------------ o AVISO contaminado
            if (pos) {
                float aT = cl((T - 4.95F) / 0.3F) * (T > 9.2F ? cl((DUR - T) / 0.8F) : 1F);
                int quadro = (int) Math.floor(t * 15F);
                String titulo = "AVISO";
                float esc = 6.2F * Math.max(0.55F, ES);
                var fonte = Minecraft.getInstance().font;
                float largLetra = fonte.width("A") * esc;
                float espaco = 0.35F * largLetra;
                float total = 5 * largLetra + 4 * espaco;
                float x0 = W / 2F - total / 2F;
                float yT = Y(300);
                b = AvisoRitual.comecar(g);
                for (int i = 0; i < 5; i++) {
                    float ai = ez((T - 4.95F - i * 0.09F) / 0.2F);
                    if (ai <= 0.01F) {
                        continue;
                    }
                    float lx = x0 + i * (largLetra + espaco) + (nf(quadro + i) - 0.5F) * 4F;
                    // sangue escorrendo das letras
                    for (int k = 0; k < 3; k++) {
                        float dx = (nf(i * 5F + k) - 0.5F) * largLetra * 0.7F;
                        float L = Math.min(70F, Math.max(0F, (T - 5.2F - i * 0.1F - k * 0.14F) * (14F + nf(i + k) * 14F)));
                        if (L > 1F) {
                            b.rect(lx + dx - 2F * ES, yT + 6F, Math.max(1F, 4F * ES), L * ES, col(0xB3121E, ai * aT));
                            b.circ(lx + dx, yT + 6F + L * ES, Math.max(1F, 4.2F * ES), col(0xB3121E, ai * aT));
                        }
                    }
                    // o "O" vira um OLHO
                    if (i == 4) {
                        float px = sin(t * 3F) * 3F, py = cos(t * 2.3F) * 3F;
                        AvisoRitual.halo(b, lx + largLetra / 2F + px, yT + 4F + py, 0F, 26F * Math.max(0.8F, ES), 0xD9E040, ai * aT * 0.9F);
                        b.circ(lx + largLetra / 2F + px, yT + 4F + py, 11F * Math.max(0.8F, ES), col(0x4A5A0C, ai * aT));
                        b.circ(lx + largLetra / 2F + px * 0.4F, yT + 4F + py, Math.max(2F, (9F - 6F * Math.abs(sin(t * 1.5F))) * Math.max(0.8F, ES)), col(0x000000, ai * aT));
                    }
                }
                b.flush();
                // as letras (texto por cima do desenho — o flush já foi feito)
                for (int i = 0; i < 5; i++) {
                    float ai = ez((T - 4.95F - i * 0.09F) / 0.2F);
                    if (ai <= 0.01F) {
                        continue;
                    }
                    String ch = String.valueOf(titulo.charAt(i));
                    if (rnd(quadro + i * 7, 9) > 0.96F) {
                        ch = String.valueOf(GLITCH.charAt((int) (rnd(i, quadro) * GLITCH.length()) % GLITCH.length()));
                    }
                    float lx = x0 + i * (largLetra + espaco) + (nf(quadro + i) - 0.5F) * 5F;
                    float ly = yT + (nf(quadro + i * 3F) - 0.5F) * 5F;
                    AvisoRitual.texto(g, ch, lx + 3F, ly + 3F, esc, col(0x4A0008, ai * aT), false);
                    AvisoRitual.texto(g, ch, lx, ly, esc, col(0xE6DACD, ai * aT), false);
                }
                // as frases, digitadas
                float yF = Y(370);
                for (int i = 0; i < flavor1.length; i++) {
                    digitar(g, flavor1[i], 5.7F, yF + i * 20F, 1.3F, 0xCDBDB4, T, t, aT);
                }
                float yM = yF + flavor1.length * 20F + 10F;
                for (int i = 0; i < linhas.length; i++) {
                    digitar(g, linhas[i], 6.5F, yM + i * 26F, 1.9F, 0xF1E6DC, T, t, aT);
                }
                float yR = yM + linhas.length * 26F + 8F;
                for (int i = 0; i < flavor3.length; i++) {
                    digitar(g, flavor3[i], 7.4F, yR + i * 20F, 1.2F, 0xE0242E, T, t, aT);
                }
            }

            // ------------------------------------------------ fade do fim
            if (T > 9.2F) {
                b = AvisoRitual.comecar(g);
                b.rect(0, 0, W, H, col(0x000000, cl((T - 9.2F) / 0.8F)));
                b.flush();
            }
        } catch (Throwable t) {
            falhou = true;
            RPTagMod.LOGGER.warn("RP Tag: aviso INFECCAO desligado nesta sessão", t);
        }
    }

    /** a frase que o ADM mandou, entrando letra por letra (o "typed" do protótipo). */
    private static void digitar(GuiGraphics g, String txt, float inicio, float y, float esc, int rgb,
                                float T, float t, float a) {
        int k = (int) Math.floor((T - inicio) * 24F);
        if (k <= 0) {
            return;
        }
        String s = corromper(txt.substring(0, Math.min(txt.length(), k)),
                rnd((int) Math.floor(t * 6F), 3) > 0.82F ? 0.16F : 0.02F, (int) Math.floor(t * 10F));
        float larg = Minecraft.getInstance().font.width(txt) * esc;
        float x = g.guiWidth() / 2F - larg / 2F;
        AvisoRitual.texto(g, s, x + 1F, y + 1F, esc, col(0x140004, 0.95F * a), false);
        AvisoRitual.texto(g, s, x, y, esc, col(rgb, a), false);
    }

    // ------------------------------------------------ as curvas das barras
    private static final float[][] PONTOS_FORCA = {{0, 0}, {0.8F, 0}, {3.4F, 100}, {3.8F, 100}, {4.4F, 70}, {4.7F, 76}, {5.2F, 35}, {5.5F, 40}, {6.3F, 8}};
    private static final float[][] PONTOS_SAN = {{0, 100}, {0.8F, 100}, {3.4F, 78}, {3.8F, 70}, {4.6F, 45}, {5.4F, 20}, {6.3F, 0}};

    private static float interpolar(float[][] p, float T) {
        float Tp = T / 0.714F;   // o protótipo tem 14 s
        for (int i = 1; i < p.length; i++) {
            if (Tp <= p[i][0]) {
                return p[i - 1][1] + (p[i][1] - p[i - 1][1]) * ez((Tp - p[i - 1][0]) / (p[i][0] - p[i - 1][0]));
            }
        }
        return p[p.length - 1][1];
    }

    private static float ForcaEm(float T) {
        return interpolar(PONTOS_FORCA, T);
    }

    private static float SanEm(float T) {
        return interpolar(PONTOS_SAN, T);
    }
}
