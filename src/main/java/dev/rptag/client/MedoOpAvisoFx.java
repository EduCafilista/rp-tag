package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import dev.rptag.RPTagMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * AVISO MEDO OP — estilo <b>22</b> (5.8.1).
 *
 * <p>Porte do protótipo <b>AvisoMedo</b> do dono ({@code oymNvtcE.java}), inspirado
 * no "medo" de Ordem Paranormal: o medo é o que <b>racha a membrana</b> entre a
 * realidade e o outro lado.
 *
 * <h2>O que aparece</h2>
 * <ul>
 *   <li>uma <b>parede de pedra</b> escura com pichações (*"NÃO OLHE", "ELE ESTÁ
 *       AQUI", "SAIA"…*) e mãos ensanguentadas;</li>
 *   <li>uma <b>lanterna</b> que abre um círculo de visão — o resto é escuridão
 *       quase total;</li>
 *   <li><b>olhos</b> espalhados no escuro que <b>somem quando a luz chega perto</b>;</li>
 *   <li>uma <b>silhueta alta</b> que aparece mais perto a cada <b>apagão</b>;</li>
 *   <li>as <b>rachaduras da membrana</b> crescendo junto com o medo (medidor
 *       "MEMBRANA" no canto), pulsando na batida do coração;</li>
 *   <li>sussurros nas beiradas e, no fim, <b>"ELE ESTÁ ATRÁS DE VOCÊ"</b>.</li>
 * </ul>
 *
 * <p>Não existe mouse no aviso do jogo: a lanterna passeia sozinha (lissajous
 * lento) e a "câmera" treme nos apagões. Regra de ouro: erro aqui desliga o
 * aviso, nunca o jogo.
 */
public final class MedoOpAvisoFx {

    private MedoOpAvisoFx() {
    }

    // ------------------------------------------------------------- constantes
    private static final float W = 960F, H = 640F;
    private static final float CX = W / 2F, CY = 285F;
    private static final float DUR = 16.0F, TEXT_T = 3.0F, CPS = 20F;
    /** os apagões: cada um traz a silhueta mais perto. */
    private static final float[] ADV = { 2.4F, 4.0F, 5.4F, 6.7F, 7.9F, 9.0F };

    private static final int PALE = 0xE2E6EE, COLD = 0x96A0B2;
    private static final int SANGRIA = 0x701C18;

    private static final String[] LINES = {
            "MEDO",
            "A membrana só se rompe quando você sente medo.",
            "Não olhe para trás."
    };
    private static final String[] PHRASES = {
            "não olhe", "está atrás de você", "você ouviu isso?", "ele sabe que você viu",
            "continue andando", "não pare", "está mais perto", "não vire"
    };
    private static final String[] PICHACOES = {
            "NÃO OLHE", "ELE ESTÁ AQUI", "SAIA", "NÃO DURMA", "ELE OUVE", "CORRA", "NÃO ESTÁ SOZINHO"
    };
    private static final float[][] PICHA_POS = {
            { 70, 150, -8, 46 }, { 560, 120, 5, 40 }, { 140, 330, 4, 54 }, { 640, 300, -6, 44 },
            { 330, 210, 3, 36 }, { 780, 200, 9, 34 }, { 420, 380, -4, 38 }
    };

    private static final Random SORTE = new Random();

    // ------------------------------------------------------------------ estado
    private static boolean ativo, falhou;
    private static long inicio;
    private static String linha1 = LINES[0];

    private static float medo;              // 0..1 (o "medidor da membrana")
    private static int k;                   // quantos apagões já passaram
    private static float blackout;          // segundos restantes de tela preta
    private static boolean finalVisto;
    private static float tremor;

    /** olhos no escuro */
    private static final int NE = 8;
    private static final float[] ex = new float[NE], ey = new float[NE];
    private static final float[] ea = new float[NE], ew = new float[NE], esep = new float[NE];

    /** rachaduras da membrana (pré-computadas) */
    private static final List<float[]> RACHAS = new ArrayList<>();
    private static float rachaMax;
    private static boolean rachasFeitas;

    /** sussurros vivos: {x, y, idade, vida, índice da frase} */
    private static final List<float[]> SUSSURROS = new ArrayList<>();
    private static float proximoSussurro;

    public static void registrar(RegisterGuiLayersEvent e) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_medo_op"),
                MedoOpAvisoFx::render);
    }

    public static void iniciar(String texto) {
        ativo = true;
        falhou = false;
        inicio = System.currentTimeMillis();
        linha1 = (texto == null || texto.isBlank()) ? LINES[0] : texto;
        medo = 0F;
        k = 0;
        blackout = 0F;
        finalVisto = false;
        tremor = 0F;
        proximoSussurro = 2.6F;
        SUSSURROS.clear();
        for (int i = 0; i < NE; i++) {
            reposiciona(i);
            ea[i] = 0F;
        }
        garantirRachas();
    }

    public static void parar() {
        ativo = false;
    }

    // ---------------------------------------------------------------- preparo
    private static void reposiciona(int i) {
        ex[i] = 60F + SORTE.nextFloat() * (W - 120F);
        ey[i] = 90F + SORTE.nextFloat() * 340F;
        ew[i] = 2F + SORTE.nextFloat() * 3F;
        esep[i] = 9F + SORTE.nextFloat() * 9F;
    }

    /** rachaduras: 9 troncos saindo do centro, com galhos (feito uma vez). */
    private static void garantirRachas() {
        if (rachasFeitas) {
            return;
        }
        rachasFeitas = true;
        RACHAS.clear();
        rachaMax = 0F;
        Random r = new Random(5);
        for (int i = 0; i < 9; i++) {
            float ang = i * TAU / 9F + (r.nextFloat() - 0.5F) * 0.4F;
            crescer(r, CX, 230F, ang, 300F + r.nextFloat() * 150F, 0, 0F);
        }
    }

    private static void crescer(Random r, float x, float y, float ang, float len, int prof, float d) {
        float w0 = Math.max(0.8F, 3.6F - prof * 0.9F);
        float passo = 11F, cx = x, cy = y;
        for (float l = 0; l < len; l += passo) {
            if (RACHAS.size() > 2600) {
                return;
            }
            ang += (r.nextFloat() - 0.5F) * 0.8F;
            float nx = cx + cos(ang) * passo, ny = cy + sin(ang) * passo;
            RACHAS.add(new float[] { cx, cy, nx, ny, Math.max(0.6F, w0 * (1 - 0.6F * l / len)), d + l });
            cx = nx;
            cy = ny;
            if (prof < 4 && r.nextFloat() < 0.09F) {
                crescer(r, cx, cy, ang + (r.nextBoolean() ? 1 : -1) * (0.6F + r.nextFloat() * 0.8F),
                        len * 0.55F * (0.6F + r.nextFloat() * 0.5F), prof + 1, d + l + passo);
            }
            if (cx < -40 || cx > W + 40 || cy < -40 || cy > H + 40) {
                break;
            }
        }
        rachaMax = Math.max(rachaMax, d + len);
    }

    /** a luz encolhe conforme o medo cresce (210 -> 115 px). */
    private static float raioLuz() {
        return 210F - 95F * medo;
    }

    private static void sussurrar(float t) {
        boolean esquerda = SORTE.nextBoolean();
        float x = esquerda ? 30F + SORTE.nextFloat() * 160F : 720F + SORTE.nextFloat() * 170F;
        float y = 130F + SORTE.nextFloat() * 300F;
        SUSSURROS.add(new float[] { x, y, 0F, 3.2F + SORTE.nextFloat(),
                SORTE.nextInt(PHRASES.length) });
    }

    // ------------------------------------------------------------------ render
    private static void render(GuiGraphics g, DeltaTracker dt) {
        if (!ativo || falhou) {
            return;
        }
        try {
            float t = (System.currentTimeMillis() - inicio) / 1000.0F;
            float dtS = Math.min(0.05F, Math.max(0.0F, t - ultimoT));
            ultimoT = t;
            if (t > DUR) {
                ativo = false;
                return;
            }
            atualiza(t, dtS);

            int pw = g.guiWidth(), ph = g.guiHeight();
            float sc = Math.min(pw / W, ph / H);
            float oxs = (pw - W * sc) / 2F, oys = (ph - H * sc) / 2F;

            float aparecer = ease(cl(t / 1.2F));
            float sair = 1F - ease(cl((t - (DUR - 1.0F)) / 1.0F));

            g.pose().pushPose();
            g.pose().translate(oxs, oys, 0F);
            g.pose().scale(sc, sc, 1F);
            if (tremor > 0.01F) {
                g.pose().translate((SORTE.nextFloat() - 0.5F) * 8F * tremor,
                        (SORTE.nextFloat() - 0.5F) * 8F * tremor, 0F);
            }

            // (1) O MUNDO: parede + mãos ensanguentadas
            preparar(g);
            Buf b = new Buf(g.pose().last().pose());
            desenhaParede(b);
            desenhaMaos(b, 1F);
            b.flush();

            // (2) A ESCURIDÃO (lanterna) — leque radial, centro transparente
            float lx = CX + 250F * sin(t * 0.37F);
            float ly = CY + 60F + 120F * cos(t * 0.29F);
            float fl = (Math.sin(t * 13F) > 0.93F) ? 0.6F : 1.0F;   // a lanterna pisca
            float R = raioLuz() * fl;
            preparar(g);
            Buf bd = new Buf(g.pose().last().pose());
            desenhaEscuridao(bd, lx, ly, R, Math.max(0.35F, aparecer) * sair);
            bd.flush();

            // (3) as rachaduras da membrana (aditivo, brilham por cima do escuro)
            aditivo();
            Buf ba = new Buf(g.pose().last().pose());
            desenhaRachas(ba, t);
            ba.flush();
            normal();

            // (4) A SILHUETA vem POR CIMA do escuro e das rachaduras: preta, com
            // contorno frio, ela precisa ser lida mesmo quando a lanterna está
            // do outro lado da parede.
            preparar(g);
            Buf bf = new Buf(g.pose().last().pose());
            desenhaFigura(bf, t);
            bf.flush();
            normal();

            // (5) os olhos (aditivo): os dela e os que estão soltos no escuro
            aditivo();
            Buf bo = new Buf(g.pose().last().pose());
            if (blackout <= 0F) {
                desenhaOlhosDaFigura(bo, t);
                desenhaOlhosSoltos(bo, t, lx, ly, R);
            }
            bo.flush();
            normal();

            // (6) textos
            desenhaSussurros(g, t);
            desenhaInterface(g, t, sair);
            desenhaTextoFinal(g, t);

            // (7) vinheta que pulsa com o coração
            preparar(g);
            Buf bv = new Buf(g.pose().last().pose());
            bv.vinheta((int) W, (int) H, Math.min(W, H) * 0.30F, 0x000000,
                    (0.42F + 0.22F * medo) * sair);
            if (blackout > 0F) {
                float a = blackout > 0.08F ? 1F : blackout / 0.08F;
                bv.rect(0, 0, W, H, col(0x000000, a * sair));
            }
            if (t < 1.2F) {
                bv.rect(0, 0, W, H, col(0x000000, 1F - aparecer));
            }
            bv.flush();
            normal();
            g.pose().popPose();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        } catch (Throwable erro) {
            falhou = true;
            ativo = false;
            RPTagMod.LOGGER.error("RP Tag: aviso MEDO OP desligado nesta sessao", erro);
        }
    }

    private static float ultimoT;

    /** relógio, apagões, medo, batida, olhos e sussurros. */
    private static void atualiza(float t, float dt) {
        tremor = Math.max(0F, tremor - dt * 3F);
        if (blackout > 0F) {
            blackout -= dt;
        }
        // ---- apagões: cada um traz a silhueta mais perto
        if (k < ADV.length && t >= ADV[k]) {
            k++;
            blackout = 0.5F;
            tremor = 1.3F;
            sussurrar(t);
        }
        if (k >= ADV.length && !finalVisto && t > ADV[ADV.length - 1] + 0.55F) {
            finalVisto = true;
            tremor = 1.6F;
        }
        // ---- o medo sobe em degraus
        float alvo = 0.10F * ease(cl(t / 4F)) + 0.82F * (k / (float) ADV.length)
                + (k >= ADV.length ? 0.08F : 0F);
        medo += (cl(alvo) - medo) * Math.min(1F, dt * 2F);

        // ---- olhos: somem quando a luz chega perto
        float lx = CX + 250F * sin(t * 0.37F);
        float ly = CY + 60F + 120F * cos(t * 0.29F);
        float lr = raioLuz() * 0.8F;
        for (int i = 0; i < NE; i++) {
            float d = (float) Math.hypot(lx - ex[i], ly - ey[i]);
            if (d < lr || t < 1.5F) {
                ea[i] -= dt * 6F;
                if (ea[i] <= 0F) {
                    ea[i] = 0F;
                    if (d < lr) {
                        reposiciona(i);
                        ea[i] = -(1.5F + SORTE.nextFloat() * 3F);
                    }
                }
            } else if (ea[i] < 0F) {
                ea[i] += dt;
            } else {
                ea[i] = Math.min(0.85F, ea[i] + dt * 0.7F);
            }
        }
        // ---- sussurros
        if (t > proximoSussurro && SUSSURROS.size() < 4) {
            sussurrar(t);
            proximoSussurro = t + 1.8F + SORTE.nextFloat() * 1.8F;
        }
        for (int i = SUSSURROS.size() - 1; i >= 0; i--) {
            float[] s = SUSSURROS.get(i);
            s[2] += dt;
            if (s[2] > s[3]) {
                SUSSURROS.remove(i);
            }
        }
    }

    // ------------------------------------------------------------------ mundo
    /** parede de pedra: tijolos escuros, pichações e mãos ensanguentadas. */
    private static void desenhaParede(Buf b) {
        b.rect(0, 0, W, H, col(0x101218, 1F));
        // tijolos: 8 linhas x 12 colunas (deslocando 1/2 tijolo nas ímpares)
        for (int lin = 0; lin < 10; lin++) {
            float y = lin * 64F - 10F;
            boolean impar = (lin % 2) == 1;
            for (int col = -1; col < 13; col++) {
                float x = col * 82F + (impar ? 41F : 0F);
                float brilho = 0.055F + 0.05F * hash(lin * 37 + col * 11);
                b.rect(x + 1.5F, y + 1.5F, 79F, 61F, col(mix(0x23262E, 0x3A3E48, brilho * 8F), 1F));
                // junta escura
                b.rect(x, y, 82F, 2F, col(0x08090C, 0.9F));
                b.rect(x, y, 2F, 64F, col(0x08090C, 0.9F));
            }
        }
        // manchas de umidade
        for (int i = 0; i < 18; i++) {
            float mx = hash(i * 13) * W, my = hash(i * 29) * H;
            b.elipse(mx, my, 1F, 0F, 30F + 90F * hash(i * 7), 20F + 60F * hash(i * 3),
                    col(0x05060A, 0.35F));
        }
    }

    /** as pichações e as mãos (texto e elipses — vão por cima da parede). */
    private static void desenhaPichacoes(GuiGraphics g, float alfa) {
        Font f = Minecraft.getInstance().font;
        for (int i = 0; i < PICHACOES.length; i++) {
            float[] p = PICHA_POS[i];
            float tamanho = p[3] / 18F;              // a fonte do MC é 9px de altura
            g.pose().pushPose();
            g.pose().translate(p[0], p[1], 0F);
            g.pose().mulPose(Axis.ZP.rotationDegrees(p[2]));
            g.pose().scale(tamanho, tamanho, 1F);
            int cor = (i % 3 == 0)
                    ? col(0xCDCDC4, 0.55F * alfa)
                    : col(0x781A18, 0.75F * alfa);
            g.drawString(f, PICHACOES[i], 0, 0, cor, false);
            g.pose().popPose();
        }
    }

    private static void desenhaMaos(Buf b, float alfa) {
        float[][] maos = { { 250, 260, -12, 1.6F }, { 700, 390, 14, 1.4F },
                { 90, 410, 6, 1.2F }, { 830, 120, -8, 1.0F } };
        for (float[] m : maos) {
            float x = m[0], y = m[1], rot = m[2] * DEG, s = m[3];
            int c = col(SANGRIA, 0.62F * alfa);
            b.elipse(x, y, 1F, 0F, 14F * s, 17F * s, c);
            for (int d = -2; d <= 2; d++) {
                float ax = x + d * 7F * s;
                float ay = y - 22F * s;
                b.elipse(ax + rot * 10F, ay, 1F, 0F, 4F * s, 12F * s, c);
            }
            // dedão
            b.elipse(x + 16F * s, y + 2F * s, 1F, 0F, 5F * s, 11F * s,
                    col(SANGRIA, 0.55F * alfa));
        }
    }

    // ---- geometria da silhueta (o corpo e os olhos usam as MESMAS contas)
    private static float fracK() { return k / (float) ADV.length; }
    private static float figX() {
        return CX + 210F * (1F - fracK()) + (k > 0 ? 8F * sin(k * 2.3F) : 0F);
    }
    private static float figPe() { return 330F + 240F * fracK(); }
    /** altura total: 150 px quando longe, 430 px quando colado na tela. */
    private static float figAltura() { return 150F + 280F * fracK(); }
    /** largura (ombros = 34 * isso). */
    private static float figLargura() { return 0.45F + 0.62F * fracK(); }
    private static float figCabecaY() { return figPe() - figAltura() + figAltura() * 0.075F; }

    /** o corpo da silhueta em si (mesmo desenho pro contorno e pro preenchimento). */
    private static void corpo(Buf b, float x, float pe, float s, float altura, int c) {
        float topo = pe - altura;
        float hCab = 0.15F * altura;
        float cabY = topo + hCab * 0.5F;
        float ombroY = topo + hCab * 1.15F;
        float quadrilY = topo + altura * 0.62F;
        // cabeça (oval alto) + pescoço
        b.elipse(x, cabY, 1F, 0F, 20F * s, hCab * 0.52F, c);
        b.rect(x - 8F * s, cabY + hCab * 0.42F, 16F * s, hCab * 0.55F, c);
        // tronco: ombros largos afinando na cintura
        b.quad(x - 34F * s, ombroY, c, x + 34F * s, ombroY, c,
                x + 22F * s, quadrilY, c, x - 22F * s, quadrilY, c);
        // braços compridos (quase até o chão)
        float maoY = quadrilY + altura * 0.26F;
        b.quad(x - 34F * s, ombroY, c, x - 41F * s, ombroY + 6F * s, c,
                x - 46F * s, maoY, c, x - 38F * s, maoY, c);
        b.quad(x + 34F * s, ombroY, c, x + 41F * s, ombroY + 6F * s, c,
                x + 46F * s, maoY, c, x + 38F * s, maoY, c);
        // pernas
        b.rect(x - 19F * s, quadrilY, 15F * s, pe - quadrilY, c);
        b.rect(x + 4F * s, quadrilY, 15F * s, pe - quadrilY, c);
    }

    /** a silhueta alta (quanto mais apagões, mais perto/maior). */
    private static void desenhaFigura(Buf b, float t) {
        float s = figLargura();
        float x = figX();
        float pe = figPe();
        float altura = figAltura();
        // contorno frio: sem ele o preto some no escuro e a figura "não existe"
        corpo(b, x, pe, s * 1.10F, altura * 1.015F, col(0x3A4760, 0.45F));
        corpo(b, x, pe, s, altura, col(0x000000, 0.97F));
    }

    // -------------------------------------------------------------- escuridão
    /**
     * A lanterna: leque radial com o centro transparente e a borda preta. Cada
     * fatia é um triângulo com cor por vértice, então o degradê é de verdade —
     * anel preenchido por cima de anel preenchido ia somando alfa e fechava o
     * buraco da luz.
     */
    private static void desenhaEscuridao(Buf b, float lx, float ly, float R, float alfa) {
        float longe = (float) Math.hypot(Math.max(lx, W - lx), Math.max(ly, H - ly)) * 1.35F;
        int fatias = 64;
        float[] raios = { 0F, R * 0.45F, R * 0.85F, R * 1.15F, longe };
        float[] alfas = { 0F, 0.10F, 0.45F, 0.82F, 0.965F };
        int corCentro = col(0x000000, 0F);
        for (int anel = 0; anel < raios.length - 1; anel++) {
            int ca = col(0x000000, alfas[anel] * alfa);
            int cb = col(0x000000, alfas[anel + 1] * alfa);
            for (int i = 0; i < fatias; i++) {
                float a0 = i * TAU / fatias, a1 = (i + 1) * TAU / fatias;
                float x0 = lx + cos(a0) * raios[anel], y0 = ly + sin(a0) * raios[anel];
                float x1 = lx + cos(a1) * raios[anel], y1 = ly + sin(a1) * raios[anel];
                float x2 = lx + cos(a1) * raios[anel + 1], y2 = ly + sin(a1) * raios[anel + 1];
                float x3 = lx + cos(a0) * raios[anel + 1], y3 = ly + sin(a0) * raios[anel + 1];
                b.quad(x0, y0, ca, x1, y1, ca, x2, y2, cb, x3, y3, cb);
            }
        }
        // halo quente da lanterna (escala de cinza-azul, bem de leve)
        FxDraw.discoGrad(b, lx, ly, R * 0.62F, 0x9FB4D8, 0x0A0C12, 0.10F * alfa, 0F, 7);
    }

    // ------------------------------------------------------------------ brilhos
    private static void desenhaOlhosDaFigura(Buf b, float t) {
        float a = ease(cl((t - 1.5F) / 1.2F));
        if (a <= 0.02F) {
            return;
        }
        float s = figLargura();
        float x = figX();
        float cabY = figCabecaY();
        for (int lado = -1; lado <= 1; lado += 2) {
            float ex0 = x + lado * 8F * s;
            b.brilho(ex0, cabY, 6F + 8F * s, 0xEAF0FA, 0.22F * a);
            b.brilho(ex0, cabY, 2F + 2.5F * s, 0xFFFFFF, 0.95F * a);
        }
    }

    private static void desenhaOlhosSoltos(Buf b, float t, float lx, float ly, float R) {
        for (int i = 0; i < NE; i++) {
            if (ea[i] <= 0.01F) {
                continue;
            }
            float pisca = (fr(t * 0.3F + i * 0.37F) < 0.04F) ? 0.1F : 1F;
            float a = 0.75F * ea[i] * pisca;
            for (int lado = -1; lado <= 1; lado += 2) {
                float x = ex[i] + lado * esep[i] / 2F;
                b.brilho(x, ey[i], 5F + ew[i] * 2F, 0xC8D8F4, 0.28F * a);
                // a fenda (mais alta que larga): parece um olho, não uma bolha
                b.elipse(x, ey[i], 1F, 0F, 1.5F, 3.4F + ew[i], col(0xFFFFFF, 0.9F * a));
            }
        }
    }

    private static void desenhaRachas(Buf b, float t) {
        float revela = medo * rachaMax * 1.05F;
        if (revela <= 4F) {
            return;
        }
        float pulso = 0.65F + 0.35F * sin(t * 2.4F) + 0.30F * (float) Math.exp(
                -Math.pow(fr(t * 1.2F) / 0.07F, 2));
        for (float[] s : RACHAS) {
            if (s[5] > revela) {
                continue;
            }
            float borda = cl((revela - s[5]) / 30F);
            // halo frio (bem discreto)
            float wh = Math.max(1.5F, s[4] * 3F);
            b.seg(s[0], s[1], s[2], s[3], wh, col(COLD, 0.10F * borda * pulso), col(COLD, 0.10F * borda * pulso));
            // núcleo claro
            float wc = Math.max(0.8F, s[4] * 0.8F);
            b.seg(s[0], s[1], s[2], s[3], wc, col(0xBFD0F0, 0.55F * borda * cl(pulso)), col(0xBFD0F0, 0.55F * borda * cl(pulso)));
        }
    }

    // ------------------------------------------------------------------- texto
    private static void desenhaSussurros(GuiGraphics g, float t) {
        Font f = Minecraft.getInstance().font;
        for (float[] s : SUSSURROS) {
            float kk = s[2] / s[3];
            float a = (float) Math.pow(Math.sin(Math.PI * cl(kk)), 1.5) * 0.8F;
            if (a <= 0.03F) {
                continue;
            }
            String txt = PHRASES[(int) s[4]];
            float x = s[0] + sin(t * 17F + s[0]) * 1.5F;
            float y = s[1] + cos(t * 13F + s[1]) * 1.2F;
            g.pose().pushPose();
            g.pose().translate(x, y, 0F);
            g.pose().scale(1.35F, 1.35F, 1F);
            g.drawString(f, "§o" + txt, 0, 0, col(COLD, a), true);
            g.pose().popPose();
        }
    }

    private static void desenhaInterface(GuiGraphics g, float t, float sair) {
        desenhaPichacoes(g, sair);
        // ---- título A V I S O com os riscos
        float fi = ease(cl((t - 0.8F) / 0.9F)) * sair;
        if (fi > 0.02F) {
            textoEspacado(g, "AVISO", CX, 44F, 1.9F, 14F, col(PALE, 0.9F * fi));
            preparar(g);
            Buf b2 = new Buf(g.pose().last().pose());
            b2.seg(CX - 220F, 43F, CX - 90F, 43F, 1.5F, col(PALE, 0.6F * fi), col(PALE, 0.6F * fi));
            b2.seg(CX + 90F, 43F, CX + 220F, 43F, 1.5F, col(PALE, 0.6F * fi), col(PALE, 0.6F * fi));
            // ---- medidor da membrana
            b2.rect(740F, 84F, 180F, 8F, col(PALE, 0.15F * fi));
            b2.rect(740F, 84F, 180F * cl(medo), 8F, col(PALE, 0.9F * fi));
            b2.flush();
            normal();
            texto(g, "MEMBRANA", 740F + 45F, 72F, 0.85F, col(COLD, fi));
            texto(g, ((int) (cl(medo) * 100F)) + "%", 740F + 20F, 104F, 1.0F, col(PALE, fi));
        }
        // ---- escurecimento do rodapé (o texto lê em cima de tudo)
        preparar(g);
        Buf bb = new Buf(g.pose().last().pose());
        float pa = ease(cl((t - 1.8F) / 1.0F)) * sair;
        for (int i = 0; i < 18; i++) {
            float u = i / 17F;
            bb.rect(0, H - 200F + i * (200F / 18F), W, 200F / 18F + 1F,
                    col(0x000000, pa * (0.92F * u * u + 0.05F)));
        }
        bb.flush();
        normal();

        // ---- MEDO + as duas linhas, digitadas
        if (t >= TEXT_T) {
            int n = (int) ((t - TEXT_T) * CPS);
            int restante = n;
            // título MEDO grande, letra a letra com tremor
            float escala = 3.6F;
            int letras = Math.min(restante, linha1.length());
            restante -= linha1.length();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < letras; i++) {
                sb.append(linha1.charAt(i));
            }
            if (sb.length() > 0) {
                texto(g, sb.toString(), CX, H - 150F, escala, col(PALE, sair));
            }
            String[] resto = { LINES[1], LINES[2] };
            float[] ys = { H - 84F, H - 56F };
            for (int i = 0; i < 2 && restante > 0; i++) {
                int m = Math.min(restante, resto[i].length());
                restante -= resto[i].length();
                texto(g, resto[i].substring(0, Math.max(0, m)), CX, ys[i], 1.2F,
                        col(0xC8CEE0, sair));
            }
        }
    }

    private static void desenhaTextoFinal(GuiGraphics g, float t) {
        if (k < ADV.length) {
            return;
        }
        float inicioFinal = ADV[ADV.length - 1] + 0.9F;
        if (t < inicioFinal) {
            return;
        }
        float a = ease(cl((t - inicioFinal) / 0.5F));
        String s = "ELE ESTÁ ATRÁS DE VOCÊ";
        float off = 2F + 3F * Math.abs(sin(t * 11F));
        texto(g, s, CX - off, 205F, 2.4F, col(0x788CFF, 0.55F * a));
        texto(g, s, CX + off, 205F, 2.4F, col(0xFF7878, 0.55F * a));
        texto(g, s, CX, 205F, 2.4F, col(PALE, a));
    }

    /** suavização 0..1 (smoothstep) — o "ease" do protótipo. */
    private static float ease(float x) {
        return ss(0F, 1F, x);
    }

    /** ruído barato e determinístico em 0..1 (o "hash" do protótipo). */
    private static float hash(int i) {
        return rnd(i, 7);
    }

    /** texto centralizado em (cx, cy) com escala. */
    private static void texto(GuiGraphics g, String s, float cx, float cy, float escala, int cor) {
        Font f = Minecraft.getInstance().font;
        float largura = f.width(s) * escala;
        g.pose().pushPose();
        g.pose().translate(cx - largura / 2F, cy, 0F);
        g.pose().scale(escala, escala, 1F);
        g.drawString(f, s, 0, 0, cor, false);
        g.pose().popPose();
    }

    /** texto com espaço entre as letras (o "A V I S O" do protótipo). */
    private static void textoEspacado(GuiGraphics g, String s, float cx, float cy, float escala,
            float espaco, int cor) {
        Font f = Minecraft.getInstance().font;
        float total = 0F;
        for (int i = 0; i < s.length(); i++) {
            total += f.width(String.valueOf(s.charAt(i))) + (i < s.length() - 1 ? espaco : 0F);
        }
        float x = cx - total * escala / 2F;
        g.pose().pushPose();
        g.pose().translate(x, cy, 0F);
        g.pose().scale(escala, escala, 1F);
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            g.drawString(f, c, 0, 0, cor, false);
            g.pose().translate(f.width(c) + espaco, 0F, 0F);
        }
        g.pose().popPose();
    }
}
