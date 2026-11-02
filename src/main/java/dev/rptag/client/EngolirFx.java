package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;
import static dev.rptag.client.FxDraw.*;

import dev.rptag.RPTagMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * (5.12.0) A ANIMAÇÃO DE ENGOLIR — estilo <b>23</b>.
 *
 * <p>O dono mandou o protótipo <b>{@code uKqjYudD.java} (SwallowedByDarkness)</b>
 * e disse que a animação antiga estava "muito feia". Então este arquivo é o
 * porte fiel dele para a lente do jogo:
 *
 * <ol>
 *   <li>o chão aparece (linha de terra) e o bonequinho está de pé;</li>
 *   <li>uma <b>poça negra</b> se abre debaixo dos pés dele, com <b>3 anéis
 *       vermelhos</b> subindo;</li>
 *   <li>o texto diz <i>"algo está embaixo de você…"</i> e depois <i>"algo está
 *       te puxando…"</i>;</li>
 *   <li>a câmera <b>treme</b> (o tremor do protótipo, 5–8 px);</li>
 *   <li><b>olhos</b> abrem na poça e em volta (elipse branca, pupila vermelha,
 *       alguns com três pupilas, brilho aditivo);</li>
 *   <li>o corpo é <b>esticado</b> (encolhe em X, estica em Y) e desce, com
 *       clip no chão — enquanto a tela escurece por cima;</li>
 *   <li><b>9 braços pretos com 7 dedos</b> saem da poça e agarram o corpo;</li>
 *   <li>o texto corrompido: <b>FOI ENGOLIDO / PELA ESCURIDÃO</b> em vermelho,
 *       com glitch de caracteres;</li>
 *   <li>vinheta vermelha pulsando na batida + grão + fade.</li>
 * </ol>
 *
 * <p><b>REGRA DO DONO (pedido 49):</b> nada de tela preta cheia. A "escuridão"
 * que cobre a tela é um <b>polígono orgânico</b> (65 pontos com seno), com
 * <b>borda vermelha</b> e alfa máximo de 0,80 — o mundo continua aparecendo nas
 * beiradas e o texto fica sempre legível por cima.
 */
public final class EngolirFx {

    private EngolirFx() {
    }

    // ---------------------------------------------------------------- desenho
    /** tempo total (s) — 11 s (o protótipo era 13, comprimido 15%). */
    private static final float DUR = 11.0F;
    private static final float DW = 960F, DH = 640F;
    /** chão do protótipo: GROUND = 440 num canvas de 600. */
    private static final float CHAO = 470F;
    private static final float CX = DW / 2F;

    private static final int POCO = 0x000000, ANEL = 0x8A0A14;
    private static final int TEXTO = 0xD01414, BRASA = 0xFF1010;
    private static final int BRACO = 0x010001, BRACO_BORDA = 0x82000A;

    private static final int NOLHOS = 16;
    private static final float[][] OLHO = new float[NOLHOS][6];
    /** 9 braços × 7 dedos (o protótipo: 9 braços, 7 dedos cada). */
    private static final int NBRACOS = 9, NDEDOS = 7;
    private static final float[][] BR = new float[NBRACOS][5];
    private static final java.util.Random SORTE = new java.util.Random();

    private static boolean ativo;
    private static long inicio;
    private static String nome = "ALGUÉM";
    private static float semente;

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "engolir_vazio"),
                EngolirFx::render);
    }

    /** @param quem nome de quem está sendo engolido (já sem códigos &amp;). */
    public static void iniciar(String quem) {
        nome = (quem == null || quem.isBlank()) ? "ALGUÉM" : quem.replaceAll("\u00a7.", "").toUpperCase();
        inicio = System.nanoTime();
        ativo = true;
        semente = SORTE.nextFloat() * 100F;
        // olhos: nascem na poça (6) e depois em volta dela (o resto)
        for (int i = 0; i < NOLHOS; i++) {
            if (i < 6) {
                OLHO[i][0] = CX + (SORTE.nextFloat() - 0.5F) * 150F;
                OLHO[i][1] = CHAO + 12F + SORTE.nextFloat() * 42F;
            } else {
                double a = SORTE.nextDouble() * Math.PI * 2;
                float d = 150F + SORTE.nextFloat() * 300F;
                OLHO[i][0] = CX + cos((float) a) * d;
                OLHO[i][1] = CHAO + 30F + sin((float) a) * d * 0.32F;
            }
            OLHO[i][2] = 5F + SORTE.nextFloat() * 4F;              // raio x
            OLHO[i][3] = i < 6 ? 1.0F : 0.6F;                      // "é da poça"
            OLHO[i][4] = 0.9F + SORTE.nextFloat() * 1.6F;          // atraso
            OLHO[i][5] = SORTE.nextFloat() < 0.30F ? 1F : 0F;      // 3 pupilas?
        }
        // braços: ângulos espalhados embaixo da poça
        for (int i = 0; i < NBRACOS; i++) {
            BR[i][0] = -2.55F + i * (2.4F / (NBRACOS - 1)) + (SORTE.nextFloat() - 0.5F) * 0.08F;
            BR[i][1] = 0.85F + SORTE.nextFloat() * 0.5F;           // atraso
            BR[i][2] = 0.9F + SORTE.nextFloat() * 0.5F;            // comprimento
            BR[i][3] = SORTE.nextFloat() * 6.28F;                  // fase do tremor
            BR[i][4] = 0.55F + SORTE.nextFloat() * 0.45F;          // grossura
        }
    }

    public static void parar() {
        ativo = false;
        inicio = 0L;
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (!ativo || inicio == 0L) {
            return;
        }
        float t = (System.nanoTime() - inicio) / 1e9F;
        if (t >= DUR) {
            ativo = false;
            return;
        }
        try {
            desenhar(g, t);
        } catch (Throwable e) {
            ativo = false;                 // prova de crash: o jogo segue
            RPTagMod.LOGGER.warn("RP Tag: FX de engolir falhou (o jogo segue)", e);
        }
    }

    private static void desenhar(GuiGraphics g, float t) {
        int w = g.guiWidth();
        int h = g.guiHeight();
        float s = Math.min(w / DW, h / DH);
        float fade = 1F - ss(DUR - 1.1F, DUR - 0.1F, t);

        AvisoFxUtil.preparar(g);

        // ---- (0) o fundo escurece DE LEVE (nunca 100%: o dono vetou tela preta)
        g.pose().pushPose();
        g.pose().translate(w / 2F, h / 2F, 0);
        g.pose().scale(s, s, 1);
        g.pose().translate(-DW / 2F, -DH / 2F, 0);

        AvisoFxUtil.Buf fundo = new AvisoFxUtil.Buf(g.pose().last().pose());
        float escuro = 0.30F * ss(0.0F, 1.2F, t) * fade;
        fundo.rect(0, 0, DW, DH, col(0x030204, escuro));
        fundo.flush();

        // ---- tremor de câmera (protótipo: 2,4 s–2,9 s, amp 5–8 px)
        float amp = 0F;
        if (t > 1.9F && t < 4.6F) {
            float k = ss(1.9F, 2.4F, t) * (1F - ss(3.6F, 4.6F, t));
            amp = (5F + 3.5F * sin(t * 23F)) * k * s;
        }
        float ox = sin(t * 41.3F + semente) * amp;
        float oy = cos(t * 37.1F + semente) * amp * 0.8F;

        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        AvisoFxUtil.Buf b = new AvisoFxUtil.Buf(g.pose().last().pose());

        // ---- (1) o CHÃO: faixa de terra e a linha do horizonte
        float chaoK = ss(0.0F, 0.7F, t);
        b.rect(0, CHAO, DW, DH - CHAO, col(0x0B0906, 0.55F * chaoK * fade));
        b.rect(0, CHAO, DW, 2.5F, col(0x1A140C, 0.85F * chaoK * fade));
        // pedrisco na linha do chão
        for (int i = 0; i < 40; i++) {
            float px = rnd(i * 7 + 1, 11) * DW;
            float pr = 1.4F + rnd(i * 3 + 5, 7) * 2.6F;
            disco(b, px, CHAO + 2F + rnd(i, 3) * 8F, pr, 0x241C12, 0.30F * chaoK * fade, 8);
        }

        // ---- (2) A POÇA NEGRA (cresce de 1,2 s a 3,4 s)
        float poça = cl((t - 1.2F) / 2.2F);
        float poolR = 8F + 100F * eo(poça);
        if (poça > 0.01F) {
            // base larga e rasa: elipse "de chão" (perspectiva)
            elipse(b, CX, CHAO + 22F, poolR * 1.9F, poolR * 0.62F, col(POCO, 0.96F * fade), 22);
            elipse(b, CX, CHAO + 22F, poolR * 1.55F, poolR * 0.48F, col(0x050203, 1.0F * fade), 22);
            // borda viva (o vazio cuspindo brasa)
            for (int i = 0; i < 3; i++) {
                float k = ((t * 0.45F + i / 3F) % 1F);
                float rr = poolR * (0.55F + 0.75F * k);
                int a = (int) (0.42F * (1F - k) * 255 * fade);
                if (a > 4) {
                    b.anel(CX, CHAO + 22F, rr * 1.6F, 40, 1.6F + 1.4F * (1F - k),
                            t * 0.6F + i, ANEL, a / 255F);
                }
            }
            // poeira sendo sugada pra dentro da poça
            for (int i = 0; i < 26; i++) {
                float s1 = rnd(i * 13 + 3, 21);
                float s2 = rnd(i * 17 + 9, 5);
                float f = ((t * 0.35F + s1) % 1F);
                double ang = s2 * 6.2831853;
                float rad = poolR * (1.2F + 1.9F * (1F - f));
                float px = CX + cos((float) ang) * rad * 1.5F;
                float py = CHAO + 22F + sin((float) ang) * rad * 0.5F - f * 18F;
                disco(b, px, py, 1.5F + 2.5F * (1F - f), 0x2A2018,
                        0.40F * (1F - f) * poça * fade, 8);
            }
        }

        // ---- (3) OS OLHOS (protótipo: abrem a partir de 2,6 s)
        if (t > 2.2F) {
            for (int i = 0; i < NOLHOS; i++) {
                float abriu = ss(2.2F + OLHO[i][4], 2.9F + OLHO[i][4], t);
                if (abriu <= 0.02F) {
                    continue;
                }
                boolean pisca = sin(t * (2.1F + OLHO[i][4]) + i) > 0.965F;
                float abertura = pisca ? 0.15F : abriu;
                float rx = OLHO[i][2] * abertura;
                float ry = OLHO[i][2] * 0.62F * abertura;
                if (rx < 0.8F) {
                    continue;
                }
                float px = OLHO[i][0] + sin(t * 0.9F + i) * 2.2F;
                float py = OLHO[i][1] + cos(t * 1.1F + i) * 1.4F;
                float alfa = (0.55F + 0.45F * OLHO[i][3]) * fade;
                // esclera
                elipse(b, px, py, rx, ry, col(0xE9E2E2, 0.90F * alfa), 10);
                // pupila vermelha (alguns com três)
                float pr = ry * 0.78F;
                if (OLHO[i][5] > 0.5F) {
                    disco(b, px - rx * 0.45F, py, pr, 0xB00000, 0.95F * alfa, 10);
                    disco(b, px, py, pr * 0.9F, 0x1A0000, 0.95F * alfa, 10);
                    disco(b, px + rx * 0.45F, py, pr, 0xB00000, 0.95F * alfa, 10);
                } else {
                    disco(b, px + sin(t * 1.7F + i) * rx * 0.25F, py, pr, 0xB00000, 0.95F * alfa, 10);
                    disco(b, px + sin(t * 1.7F + i) * rx * 0.25F, py, pr * 0.45F, 0x0A0000, 0.95F * alfa, 8);
                }
                // brilho aditivo vermelho
                AvisoFxUtil.aditivo();
                brilho(b, px, py, rx * 2.1F, BRASA, 0.13F * alfa);
                AvisoFxUtil.normal();
            }
        }

        // ---- (4) O CORPO: estica e afunda (3,0 s → 5,4 s), com clip no chão
        float puxa = cl((t - 3.0F) / 2.2F);
        float escalaY = 1F + 0.70F * puxa;
        float escalaX = 1F - 0.45F * puxa;
        float desce = 190F * puxa;
        float corpoK = ss(0.35F, 1.1F, t) * (1F - ss(6.2F, 7.0F, t));
        if (corpoK > 0.02F) {
            float pe = CHAO + 6F + desce;
            // clip: nada do corpo aparece abaixo da poça
            float limite = CHAO + 26F;
            g.pose().pushPose();
            g.pose().translate(CX, pe, 0);
            g.pose().scale(escalaX, escalaY, 1);
            AvisoFxUtil.Buf c = new AvisoFxUtil.Buf(g.pose().last().pose());
            // "Steve" do protótipo, em unidades de 4 px (16 × 32 no total)
            int pele = col(0xC68E6B, 0.98F * corpoK);
            int camisa = col(0x00A8A8, 0.98F * corpoK);
            int calca = col(0x3C3C9A, 0.98F * corpoK);
            int cabelo = col(0x3A2412, 0.98F * corpoK);
            // cabeça (-34..-18)
            c.rect(-8F, -34F, 16F, 16F, pele);
            c.rect(-8F, -34F, 16F, 5F, cabelo);
            c.rect(-6F, -26F, 3F, 2F, col(0xFFFFFF, corpoK));   // olho
            c.rect(3F, -26F, 3F, 2F, col(0xFFFFFF, corpoK));
            // tronco
            c.rect(-8F, -18F, 16F, 14F, camisa);
            // braços
            c.rect(-12F, -18F, 4F, 12F, camisa);
            c.rect(8F, -18F, 4F, 12F, camisa);
            c.rect(-12F, -6F, 4F, 6F, pele);
            c.rect(8F, -6F, 4F, 6F, pele);
            // pernas
            c.rect(-7F, -4F, 6F, 10F, calca);
            c.rect(1F, -4F, 6F, 10F, calca);
            c.flush();
            g.pose().popPose();
            // (o clip: cobre o que passou da poça com a própria poça)
            if (pe > limite) {
                AvisoFxUtil.Buf cl2 = new AvisoFxUtil.Buf(g.pose().last().pose());
                elipse(cl2, CX, CHAO + 22F, poolR * 1.9F, poolR * 0.62F, col(POCO, 0.98F), 22);
                cl2.flush();
            }
        }

        b.flush();
        AvisoFxUtil.normal();
        g.pose().popPose();

        // ---- (6) A ESCURIDÃO COBRE A TELA (5,4 s → 7,4 s) — ORGÂNICA, COM BORDA
        float cobre = ss(5.4F, 7.4F, t) * 0.80F * fade;
        if (cobre > 0.01F) {
            desenharCobertura(g, t, cobre);
        }

        // ---- (6b) OS BRAÇOS (5,0 s → 8,0 s): 9 braços, 7 dedos cada.
        //     (5.12.0) eles são desenhados DEPOIS da escuridão: no protótipo
        //     as mãos aparecem POR CIMA do breu — antes de mim, a cobertura
        //     engolia os braços e a cena ficava só um borrão.
        float bracoK = ss(4.9F, 5.5F, t) * (1F - ss(7.2F, 8.2F, t)) * fade;
        if (bracoK > 0.02F) {
            desenharBracos(g, t, bracoK);
        }

        // ---- (7) OS TEXTOS
        if (t > 1.5F && t < 3.2F) {
            float a = ss(1.5F, 1.9F, t) * (1F - ss(2.7F, 3.2F, t));
            texto(g, "algo está embaixo de você…", DW / 2F, CHAO - 210F, 1.55F,
                    ((int) (a * 255) << 24) | 0x8C8C8C);
        }
        if (t > 2.9F && t < 4.6F) {
            float a = ss(2.9F, 3.3F, t) * (1F - ss(4.1F, 4.6F, t));
            texto(g, "algo está te puxando…", DW / 2F, CHAO - 210F, 1.55F,
                    ((int) (a * 255) << 24) | 0xA8A8A8);
        }
        if (t > 6.0F) {
            float a = ss(6.0F, 6.7F, t) * fade;
            float treme = sin(t * 26F) * 2.2F;
            glitchTexto(g, nome, DW / 2F + treme, CHAO - 250F, 2.6F, a, 0xB8B8B8);
            float a2 = ss(6.5F, 7.2F, t) * fade;
            glitchTexto(g, "FOI ENGOLIDO", DW / 2F - treme * 0.6F, CHAO - 190F, 3.4F, a2, TEXTO);
            float a3 = ss(7.0F, 7.7F, t) * fade;
            texto(g, "PELA ESCURIDÃO", DW / 2F, CHAO - 138F, 2.5F,
                    ((int) (a3 * 255) << 24) | TEXTO);
        }

        // ---- (8) VINHETA VERMELHA pulsando na batida + grão
        AvisoFxUtil.Buf v = new AvisoFxUtil.Buf(g.pose().last().pose());
        float batida = 0.55F + 0.45F * sin(t * 6.2831853F * 0.85F);
        float vin = (0.20F + 0.22F * batida) * ss(0.0F, 1.4F, t) * fade * (0.55F + 0.45F * cobre / 0.8F);
        v.vinheta((int) DW, (int) DH, 150F, 0x38060A, vin);
        // grão (estável por frame, some no fim)
        int frame = (int) (t * 24F);
        for (int i = 0; i < 90; i++) {
            float px = rnd(i * 5 + 1, frame) * DW;
            float py = rnd(i * 7 + 3, frame + 91) * DH;
            float pr = 0.7F + rnd(i, frame) * 1.5F;
            disco(v, px, py, pr, 0xFFFFFF, 0.028F * fade, 6);
        }
        v.flush();

        // ---- (9) o mundo desfoca de leve no clímax (nunca dá enjoo)
        float blur = 1.2F * ss(5.2F, 6.6F, t) * (1F - ss(8.4F, 9.4F, t));
        if (blur > 0.05F) {
            NeptuneFx.desfocar(blur);
        }

        AvisoFxUtil.normal();
        g.pose().popPose();
    }


    /**
     * Os 9 braços com 7 dedos do protótipo. Desenhados POR CIMA da escuridão:
     * preto quase absoluto (#0C0A10) com a borda vermelha de
     * {@code rgb(130,0,10)}, então eles LEEM contra o breu (o preto puro
     * sumiria na escuridão — foi o que o dono viu na versão antiga).
     */
    private static void desenharBracos(GuiGraphics g, float t, float k) {
        AvisoFxUtil.Buf b = new AvisoFxUtil.Buf(g.pose().last().pose());
        for (int i = 0; i < NBRACOS; i++) {
            float nasce = ss(4.9F + BR[i][1] * 0.55F, 5.5F + BR[i][1] * 0.55F, t);
            if (nasce <= 0.02F) {
                continue;
            }
            float ang = BR[i][0];
            float comp = (120F + 150F * BR[i][2]) * nasce;
            float grosso = (8F + 9F * BR[i][4]) * nasce;
            float trem = sin(t * 9F + BR[i][3]) * 3.4F;
            float bx = CX + cos(ang) * 125F + trem;
            float by = CHAO + 30F;
            float tx = CX + cos(ang) * (30F + 46F * BR[i][2]) + trem * 2F;
            float ty = CHAO - comp * 0.55F + 42F * BR[i][2];
            float x0 = bx, y0 = by;
            for (int elo = 1; elo <= 3; elo++) {
                float u = elo / 3F;
                float x1 = lerp(bx, tx, u) + sin(u * 3.1F + BR[i][3]) * 16F * (1F - u) * nasce;
                float y1 = lerp(by, ty, u) - sin(u * 3.1F + BR[i][3]) * 12F * (1F - u) * nasce;
                float w = grosso * (1.30F - u * 0.45F);
                // borda vermelha (embaixo) + corpo escuro por cima
                b.seg(x0, y0 + w * 0.42F, x1, y1 + w * 0.42F, w * 1.05F,
                        col(0x9A0C18, 0.72F * k), col(0x9A0C18, 0.72F * k));
                b.seg(x0, y0, x1, y1, w, col(0x141018, 0.98F * k), col(0x141018, 0.98F * k));
                x0 = x1;
                y0 = y1;
            }
            // 7 dedos: escuros com a PONTA vermelha (lê no breu)
            float fl = (12F + 12F * BR[i][4]) * nasce;
            for (int d = 0; d < NDEDOS; d++) {
                float fa = -2.6F + d * (1.35F / (NDEDOS - 1));
                float fx = x0 + cos(fa) * fl + sin(t * 13F + d + BR[i][3]) * 1.8F;
                float fy = y0 + sin(fa) * fl * 0.75F - 5F * nasce;
                b.seg(x0, y0, fx, fy, 3.4F + 2.2F * BR[i][4],
                        col(0x141018, 0.98F * k), col(0x141018, 0.98F * k));
                disco(b, fx, fy, 2.2F + 1.6F * BR[i][4], 0xC2141E, 0.55F * k, 8);
            }
        }
        b.flush();
    }

    /**
     * A escuridão que sobe: polígono orgânico de 65 pontos (protótipo), com
     * borda vermelha. <b>NUNCA</b> um retângulo preto: o alfa é limitado a 0,80
     * e as beiradas ficam mais fracas que o miolo.
     */
    private static void desenharCobertura(GuiGraphics g, float t, float forca) {
        AvisoFxUtil.Buf b = new AvisoFxUtil.Buf(g.pose().last().pose());
        int pts = 65;
        float cx = CX;
        float cy = CHAO - 40F;
        float raio = 60F + 1100F * eo(cl((t - 5.4F) / 2.0F));
        float[] xs = new float[pts];
        float[] ys = new float[pts];
        for (int i = 0; i < pts; i++) {
            float a = i * 6.2831853F / pts;
            float ond = 1F + 0.10F * sin(a * 5F + t * 2.2F + semente)
                    + 0.06F * sin(a * 11F - t * 3.1F);
            xs[i] = cx + cos(a) * raio * ond * 1.15F;
            ys[i] = cy + sin(a) * raio * ond;
        }
        int centro = col(0x040307, forca);
        int meio = col(0x060409, forca * 0.86F);
        for (int i = 0; i < pts; i++) {
            int j = (i + 1) % pts;
            b.tri(cx, cy, centro, xs[i], ys[i], meio, xs[j], ys[j], meio);
        }
        // borda vermelha pulsando (protótipo: alpha 0,4)
        float pulso = 0.30F + 0.14F * sin(t * 5.4F);
        for (int i = 0; i < pts; i++) {
            int j = (i + 1) % pts;
            b.seg(xs[i], ys[i], xs[j], ys[j], 3.2F, col(0x8A0A14, pulso * forca / 0.8F),
                    col(0x8A0A14, pulso * forca / 0.8F));
            // faíscas vermelhas na borda
            if (i % 4 == 0) {
                float fa = ((t * 0.9F + i * 0.13F) % 1F);
                disco(b, xs[i], ys[i], 2.4F + 3.4F * (1F - fa) * sin(fa * 3.14159F),
                        0xC01010, 0.55F * sin(fa * 3.14159F) * forca / 0.8F, 8);
            }
        }
        b.flush();
    }

    /** texto com glitch: alguns caracteres trocados pelos símbolos do protótipo. */
    private static void glitchTexto(GuiGraphics g, String s, float cx, float cy, float esc,
            float alfa, int cor) {
        if (alfa <= 0.01F) {
            return;
        }
        String sujo;
        if (Math.random() < 0.72) {
            String lixo = "▓▒░█#@%&?";
            StringBuilder sb = new StringBuilder(s);
            int n = Math.max(1, (int) (s.length() * 0.22F));
            for (int i = 0; i < n; i++) {
                int pos = (int) (Math.random() * sb.length());
                sb.setCharAt(pos, lixo.charAt((int) (Math.random() * lixo.length())));
            }
            sujo = sb.toString();
        } else {
            sujo = s;
        }
        texto(g, sujo, cx, cy, esc, ((int) (alfa * 255) << 24) | cor);
        // sombra vermelha deslocada (o "fantasma" do glitch)
        texto(g, s, cx + 1.6F, cy + 1.2F, esc, ((int) (alfa * 90) << 24) | 0x5A0000);
    }
}
