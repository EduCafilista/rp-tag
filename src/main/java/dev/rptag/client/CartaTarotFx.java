package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * MOTOR DAS CARTAS DE TARÔ (5.16.0).
 *
 * <p>Os oito protótipos novos do dono (Eremita, Estrela, Julgamento, Lua, Mago, Morte, Sol e Torre)
 * são todos o MESMO programa JavaFX: um canvas de 800×600, uma {@code Theme} base que desenha o
 * céu, a névoa, os raios, o sigilo de 22 caminhos, a carta que sobe e vira, o título "AVISO" e as
 * três linhas de texto — e uma subclasse por carta que só diz as cores, as frases e o que aparece
 * (bg/mid/front/ring/back/art/emblem).
 *
 * <p>Aqui está o "canvas" deles: uma pilha de transformação (translate/rotate/scale) e as mesmas
 * primitivas do {@code GraphicsContext} (fillRect, fillOval, strokeOval, fillPolygon, strokePolyline,
 * strokeLine, fillArc) convertidas para triângulos do Minecraft. Com isso cada carta virou quase uma
 * cópia linha a linha do protótipo, e não um "inspirado em".
 *
 * <p>Coordenadas: tudo é escrito nas coordenadas do protótipo (canvas 800×600, centro em 400,255) e
 * o motor escala para a tela — igual ao Louco.
 */
public abstract class CartaTarotFx {

    // ================================================================= canvas do protótipo
    static final float PW = 800f, PH = 600f, PCX = 400f, PCY = 255f;
    static final float CICLO = 14f;
    static final float TAU = 6.2831853f;

    /** escala e origem da tela (canvas -> tela). */
    static float K = 1f, OX = 0f, OY = 0f;

    /** pilha de transformação afim: x' = a*x + c*y + e ; y' = b*x + d*y + f. */
    private static final int PILHA = 16;
    private static final float[] MA = new float[PILHA], MB = new float[PILHA], MC = new float[PILHA],
            MD = new float[PILHA], ME = new float[PILHA], MF = new float[PILHA];
    private static int nivel;
    private static float ma = 1, mb = 0, mc = 0, md = 1, me = 0, mf = 0;

    /** alfa global (o {@code setGlobalAlpha} do protótipo). */
    static float alfa = 1f;
    /** o {@code setGlobalBlendMode(ADD)} do protótipo. */
    private static boolean aditivo;

    static Buf B;
    private static GuiGraphics G;

    static final Random R = new Random();

    // ================================================================= estado do aviso
    static CartaTarotFx atual;
    static long inicio = -1;
    static float ultimaT = -1;
    static float pararEm = -1;
    static String msg = "Um grande perigo se aproxima do servidor.";

    // ================================================================= tema
    /** o que cada carta define (o {@code Theme} do protótipo). */
    public abstract static class Tema {
        String nome = "", romano = "";
        int ac = 0xFFFFFF, fogc = 0x999999, glowc = 0xFFFFFF, flashc = 0xFFFFFF, shc = 0x000000;
        int sky0 = 0x000000, sky1 = 0x101010, bk = 0x0A0A0A, pc0 = 0xDDDDDD, pc1 = 0x999999, ink = 0x111111;
        int c1 = 0xAAAAAA, c2 = 0xF0F0F0, l3c = 0xFF8844;
        int tg0 = 0xFFFFFF, tg1 = 0xCCCCCC, tg2 = 0x888888;
        String l1 = "", l3 = "";
        String[] palavras = {"silêncio", "o caminho", "ninguém vem"};

        void reset() {
        }

        void emblem() {
        }

        abstract void back(float t);

        abstract void art(float t);

        abstract void bg(float T, float t);

        abstract void mid(float T, float t);

        abstract void front(float T, float t);

        abstract void ring(float T, float t, float dp);
    }

    // ================================================================= API
    public static void registrar(RegisterGuiLayersEvent e, String id, CartaTarotFx fx) {
        e.registerAboveAll(ResourceLocation.fromNamespaceAndPath("rptag", "aviso_" + id), fx::render);
    }

    static void iniciar(CartaTarotFx fx, String mensagem) {
        atual = fx;
        msg = (mensagem == null || mensagem.isBlank()) ? "Um grande perigo se aproxima do servidor." : mensagem;
        inicio = -1;
        pararEm = CICLO;
        fx.tema().reset();
        fx.limpar();
    }

    static void parar() {
        if (atual != null) {
            atual = null;
            inicio = -1;
        }
    }

    protected abstract Tema tema();

    /** limpa as partículas/estados próprios da carta. */
    protected void limpar() {
    }

    /** nomes dos sussurros que sobem no ar (as palavras do protótipo). */
    protected String[] sussurros() {
        return tema().palavras;
    }

    // ================================================================= render
    private void render(GuiGraphics g, DeltaTracker dt) {
        if (atual != this) {
            return;
        }
        if (inicio < 0) {
            inicio = System.nanoTime();
        }
        float t = (System.nanoTime() - inicio) / 1e9f;
        if (t < 0) {
            return;
        }
        if (pararEm > 0 && t > pararEm) {
            inicio = -1;
            if (atual == this) {
                atual = null;
            }
            return;
        }
        float T = t % CICLO;
        if (T < ultimaT) {
            tema().reset();
            limpar();
            sussurros.clear();
        }
        ultimaT = T;

        int w = g.guiWidth(), h = g.guiHeight();
        K = Math.min(w / PW, h / PH);
        OX = w / 2f;
        OY = h / 2f;
        nivel = 0;
        ma = 1; mb = 0; mc = 0; md = 1; me = 0; mf = 0;
        alfa = 1f;
        aditivo = false;

        G = g;
        B = AvisoRitual.comecar(g);
        normal();

        Tema th = tema();
        try {
            desenhar(th, T, t, w, h);
        } catch (RuntimeException ex) {
            // nunca deixar um aviso quebrar a tela inteira
            try {
                B.flush();
            } catch (RuntimeException ignored) {
            }
        }
        B.flush();
        normal();
        RenderSystem.enableDepthTest();
    }

    private void desenhar(Tema th, float T, float t, int w, int h) {
        float nevoa = ss(0f, 1.5f, T) * (1 + ss(12f, 14f, T) * 0.6f);
        float rev = ss(3.6f, 4.8f, T);

        // (5.16.1) o alfa do céu agora é garantido dentro do AvisoRitual.degradeVertical.
        AvisoRitual.degradeVertical(B, 0, 0, w, h, th.sky0, th.sky1, 18);
        mancha(PCX, PCY, 420, th.glowc, 0.16f * nevoa);   // (5.16.1) halo em 6 anéis dava 6 faixas

        th.bg(T, t);
        nevoaCamada(false, T, nevoa);
        th.mid(T, t);
        raios(t, rev);
        sigilo(th, T, t);
        orbitar(th, T, t, false);
        carta(th, T, t);
        orbitar(th, T, t, true);
        th.front(T, t);
        nevoaCamada(true, T, nevoa * 0.7f);

        sussurrar(T, t);
        for (Sussurro s : sussurros) {
            if (s.txt == null || s.txt.isEmpty()) {
                continue;
            }
            float a = sin((float) Math.PI * s.vida / s.max) * 0.32f;
            if (a > 0.01f) {
                textoCentro(s.txt, s.x, s.y, fs(14f), col(th.ac, a));
            }
            s.y -= 0.15f;
            s.vida += 1;
        }
        sussurros.removeIf(s -> s.vida > s.max);

        titulo(th, T, t);
        digitado(th.l1, 6.0f, 540, T, fs(15f), th.c1);
        digitado(msg, 7.4f, 566, T, fs(18f), th.c2);
        digitado(th.l3, 9.0f, 590, T, fs(13f), th.l3c);

        // ---- clarões
        float f4 = T > 4.8f ? Math.max(0, 1 - (T - 4.8f) / 0.8f) : 0;
        float f41 = T > 4.1f ? Math.max(0, 1 - (T - 4.1f) / 0.5f) : 0;
        if (f4 > 0.01f) {
            B.rect(0, 0, w, h, col(th.flashc, 0.26f * f4));
        }
        if (f41 > 0.01f) {
            B.rect(0, 0, w, h, col(th.flashc, 0.30f * f41));
        }
        B.flush();
        normal();

        // ---- vinheta: transparente no miolo, escurecendo até a borda (com o pulso).
        // (5.16.1) Leque radial com o alfa interpolado por vértice: anéis concêntricos deixavam
        // RISCO RETO no alto da tela (cada anel virava um arco achatado) e faixas de borda
        // deixavam o centro chapado. O leque é liso e segue o RadialGradient do protótipo.
        float batida = (float) Math.pow(Math.max(0, sin(t * 2.4f)), 2) * ss(5f, 6f, T);
        float alvo = 0.80f + 0.10f * batida;
        vinheta(w, h, alvo);

        // ---- grão
        for (int i = 0; i < 140; i++) {
            float gx = rn(i, 1) * w, gy = rn(i, 2) * h;
            B.rect(gx, gy, 1, 1, col(0xFFFFFF, 0.05f));
        }

        // ---- escurecer no começo e no fim
        float fade = 0;
        if (T < 0.8f) {
            fade = 1 - T / 0.8f;
        }
        if (T > 12.4f) {
            fade = (T - 12.4f) / 1.6f;
        }
        if (fade > 0.01f) {
            B.rect(0, 0, w, h, col(0x000000, fade));
        }
    }

    // ================================================================= névoa
    private static final int NPB = 40, NPF = 14;
    private static final float[][] PB = new float[NPB][6], PF = new float[NPF][6];
    private static boolean nevIniciada;

    private static void iniciarNevoa() {
        for (int k = 0; k < NPB; k++) {
            PB[k][0] = rn(k, 1) * PW;
            PB[k][1] = 100 + rn(k, 3) * 450;
            PB[k][2] = 90 + rn(k, 7) * 110;
            PB[k][3] = 0.2f + rn(k, 9) * 0.5f;
            PB[k][4] = rn(k, 11) * TAU;
            PB[k][5] = 0.07f + rn(k, 13) * 0.08f;
        }
        for (int k = 0; k < NPF; k++) {
            PF[k][0] = rn(k + 40, 1) * PW;
            PF[k][1] = 380 + rn(k + 43, 3) * 170;
            PF[k][2] = 100 + rn(k + 47, 7) * 70;
            PF[k][3] = 0.3f + rn(k + 49, 9) * 0.5f;
            PF[k][4] = rn(k + 51, 11) * TAU;
            PF[k][5] = 0.05f + rn(k + 53, 13) * 0.03f;
        }
        nevIniciada = true;
    }

    private static void nevoaCamada(boolean frente, float T, float amt) {
        if (!nevIniciada) {
            iniciarNevoa();
        }
        if (amt <= 0.01f) {
            return;
        }
        // (5.16.1) a névoa do protótipo é a COR DA CARTA (fogc), não branco — e ela é
        // desenhada como mancha radial (AvisoRitual.halo), que é o equivalente do RadialGradient.
        Tema th = atual == null ? null : atual.tema();
        int cor = th == null ? 0x999999 : th.fogc;
        float[][] lista = frente ? PF : PB;
        int n = frente ? NPF : NPB;
        float k2 = frente ? 0.7f : 1f;
        for (int i = 0; i < n; i++) {
            float[] p = lista[i];
            float x = (float) ((p[0] + T * p[3] * 14) % (PW + 500)) - 250;
            float y = p[1] + sin(T * 0.3f + p[4]) * 22;
            mancha(x, y, p[2], cor, p[5] * amt * k2 * 1.8f);
        }
    }

    // ================================================================= raios de fundo
    private static void raios(float t, float amt) {
        if (amt <= 0.01f) {
            return;
        }
        Tema th = atual == null ? null : atual.tema();
        int cor = th == null ? 0xFFFFFF : th.ac;
        aditivo();
        save();
        translate(PCX, PCY);
        rotate(t * 0.05f);
        for (int k = 0; k < 16; k++) {
            rotate(TAU / 16f);
            float[] xs = {0, -14, 14}, ys = {0, -430, -430};
            poligono(xs, ys, 3, col(cor, 0.045f * amt));
        }
        restore();
        normal();
    }

    // ================================================================= sigilo de 22 caminhos
    private static void sigilo(Tema th, float T, float t) {
        float dp = ss(0.3f, 2.3f, T);
        if (dp <= 0.01f) {
            return;
        }
        float rot = t * 0.12f;
        save();
        translate(PCX, PCY);
        // as 22 linhas da "estrela" (cada casa ligada à nona seguinte)
        for (int k = 0; k < 22; k++) {
            float a1 = k / 22f * TAU + rot, a2 = ((k + 9) % 22) / 22f * TAU + rot;
            linha(cos(a1) * 200, sin(a1) * 200, cos(a2) * 200, sin(a2) * 200, 1f, col(th.ac, 0.16f * dp));
        }
        // anéis que se fecham
        save();
        rotate(-1.5708f);
        float[][] aneis = {{200, 2, 0.7f}, {176, 1, 0.5f}, {150, 2, 0.6f}, {132, 1, 0.3f}};
        for (float[] an : aneis) {
            anelParcial(an[0], an[1], col(th.ac, an[2] * dp), dp);
        }
        restore();
        // os 22 glifos
        for (int k = 0; k < 22; k++) {
            save();
            rotate(k / 22f * TAU + rot);
            translate(0, -188);
            glifo(k % 5, 1.3f, col(th.ac, 0.85f * dp));
            restore();
        }
        // as Sequências 9 … 0 girando ao contrário
        for (int k = 0; k < 10; k++) {
            save();
            rotate(k / 10f * TAU - rot * 1.6f);
            translate(0, -163);
            textoCentro(String.valueOf(9 - k), 0, 0, fs(15f), col(th.ac, 0.9f * dp));
            restore();
        }
        th.ring(T, t, dp);
        restore();
    }

    private static void anelParcial(float r, float w, int cor, float dp) {
        int segs = 48;
        float ate = TAU * cl(dp);
        for (int i = 0; i < segs; i++) {
            float a0 = ate * i / segs, a1 = ate * (i + 1) / segs;
            linha(cos(a0) * r, sin(a0) * r, cos(a1) * r, sin(a1) * r, w, cor);
        }
    }

    /** os cinco glifos do protótipo, desenhados centrados em (0,0). */
    static void glifo(int k, float w, int cor) {
        switch (k) {
            case 0 -> {
                linha(-5, -5, 5, 5, w, cor);
                linha(5, -5, -5, 5, w, cor);
            }
            case 1 -> {
                contorno(0, 0, 4, w, cor);
                linha(0, -8, 0, -4, w, cor);
            }
            case 2 -> poligono(new float[]{0, 5, -5}, new float[]{-6, 4, 4}, 3, cor);
            case 3 -> {
                linha(0, -6, 0, 6, w, cor);
                linha(-4, -2, 4, -2, w, cor);
                linha(-3, 3, 3, 3, w, cor);
            }
            default -> {
                polilinha(new float[]{-5, 0, 5}, new float[]{2, -6, 2}, 3, w, cor);
                linha(0, -6, 0, 0, w, cor);
            }
        }
    }

    // ================================================================= as cinco cartas orbitando
    private static void orbitar(Tema th, float T, float t, boolean frente) {
        float a0 = ss(5.6f, 6.4f, T);
        if (a0 <= 0.01f) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            float a = t * 0.5f + i * 1.2566f, sn = sin(a);
            if ((sn >= 0) != frente) {
                continue;
            }
            float d = (sn + 1) / 2, s = 0.7f + 0.3f * d;
            save();
            translate(PCX + cos(a) * 255, PCY + sn * 70 - 10 + sin(t + i) * 6);
            rotate(cos(a) * 0.25f);
            scale(s, s);
            alfa = a0 * (0.5f + 0.5f * d);
            retangulo(-14, -22, 28, 44, col(th.bk, 1f));
            contornoRet(-14, -22, 28, 44, 1.5f, col(th.ac, 1f));
            save();
            scale(1.2f, 1.2f);
            th.emblem();
            restore();
            alfa = 1f;
            restore();
        }
    }

    // ================================================================= a carta
    private static void carta(Tema th, float T, float t) {
        float sobe = ss(1.6f, 3.0f, T);
        if (sobe <= 0.01f) {
            return;
        }
        float fa = ss(3.2f, 4.1f, T), ang = (float) Math.PI * fa;
        float sxv = cos(ang);
        float y = PCY + (1 - sobe) * 140 + sin(t * 1.4f) * 5;
        boolean frente = ang > 1.5708f;

        save();
        translate(PCX, y);
        scale(Math.max(0.02f, Math.abs(sxv)), 1);

        mancha(0, 0, 110, th.ac, (0.35f + 0.3f * fa) * alfa);

        if (!frente) {
            retangulo(-60, -95, 120, 190, col(th.bk, alfa));
            contornoRet(-54, -89, 108, 178, 3f, col(th.ac, alfa));
            contornoRet(-48, -83, 96, 166, 1f, col(th.ac, alfa));
            alfa = sobe;
            th.back(t);
            alfa = 1f;
            for (float[] c : new float[][]{{-40, -75}, {40, -75}, {-40, 75}, {40, 75}}) {
                disco(c[0], c[1], 2.5f, th.ac, alfa);
            }
        } else {
            degradeRect(-60, -95, 120, 190, th.pc0, th.pc1);
            contornoRet(-54, -89, 108, 178, 3f, col(th.ink, alfa));
            contornoRet(-49, -84, 98, 168, 1f, col(th.ink, alfa));
            // a arte, recortada no miolo da carta
            recorte(-44, -52, 88, 106);
            alfa = sobe;
            th.art(t);
            alfa = 1f;
            semRecorte();
            textoCentro(th.romano, 0, -76, fs(17f), col(th.ink, alfa));
            textoCentro(th.nome, 0, 74, fs(th.nome.length() > 10 ? 10f : 11f), col(th.ink, alfa));
        }
        restore();
    }

    // ================================================================= título
    private static void titulo(Tema th, float T, float t) {
        float T0 = 4.8f;
        if (T < T0) {
            return;
        }
        String palavra = "AVISO";
        float espaco = 14 * K;
        float esc = fs(56f);
        var fonte = net.minecraft.client.Minecraft.getInstance().font;
        float total = 0;
        for (int i = 0; i < palavra.length(); i++) {
            total += fonte.width(String.valueOf(palavra.charAt(i))) * esc;
        }
        total += espaco * (palavra.length() - 1);
        float x = OX - total / 2f;
        boolean brilho = rn((int) Math.floor(t * 14), 5) > 0.94f;
        float jx = brilho ? (R.nextFloat() - 0.5f) * 10 * K : 0;

        int meio = mix(th.tg0, th.tg2, 0.45f);
        for (int i = 0; i < palavra.length(); i++) {
            String ch = String.valueOf(palavra.charAt(i));
            float a = ss(T0 + i * 0.18f, T0 + i * 0.18f + 0.45f, T);
            float larg = fonte.width(ch) * esc;
            float yy = sy(500 + (1 - a) * 18);
            if (a > 0.01f) {
                if (brilho) {
                    textoEm(ch, x + 5 * K + jx, yy, esc, col(th.ac, 0.55f * a), false);
                    textoEm(ch, x - 5 * K + jx, yy, esc, col(0xDC283C, 0.5f * a), false);
                }
                textoEm(ch, x + jx, yy, esc, col(mix(th.tg0, th.tg2, (i + 0.5f) / palavra.length()), a), true);
            }
            x += larg + espaco;
        }
        float u = ss(T0 + 0.9f, T0 + 1.7f, T);
        if (u > 0.01f) {
            linha(PCX - total / (2 * K) * u - 10, 512, PCX + total / (2 * K) * u + 10, 512, 1.5f, col(th.ac, 0.8f * u));
            poligono(new float[]{PCX, PCX + 5, PCX, PCX - 5}, new float[]{507, 512, 517, 512}, 4, col(0xFFFFFF, u));
        }
    }

    private static void digitado(String txt, float inicio, float y, float T, float esc, int cor) {
        int n = (int) Math.floor((T - inicio) * 30);
        if (n <= 0 || txt == null || txt.isEmpty()) {
            return;
        }
        String s = txt.substring(0, Math.min(txt.length(), n));
        boolean cursor = n < txt.length() && ((int) Math.floor(T * 6)) % 2 == 1;
        textoCentro(s + (cursor ? "_" : ""), PCX, y, esc, cor);
    }

    // ================================================================= sussurros
    /** uma palavra subindo na tela. */
    static final class Sussurro {
        String txt;
        float x, y, vida, max = 140;
    }

    private static final List<Sussurro> sussurros = new ArrayList<>();
    private static float ultimoSussurro;

    private static void sussurrar(float T, float t) {
        if (T > 5 && T < 11.5f && t - ultimoSussurro > 0.7f) {
            ultimoSussurro = t;
            String[] p = (atual == null || atual.sussurros() == null || atual.sussurros().length == 0)
                    ? new String[]{"silêncio"} : atual.sussurros();
            Sussurro s = new Sussurro();
            s.txt = p[R.nextInt(p.length)];
            s.x = 50 + R.nextFloat() * 700;
            s.y = 110 + R.nextFloat() * 320;
            sussurros.add(s);
            if (sussurros.size() > 26) {
                sussurros.remove(0);
            }
        }
    }

    /** vinheta radial: leque de triângulos do miolo (alfa 0) até a borda (alfa total). */
    static void vinheta(int w, int h, float alvo) {
        // (5.16.1) Em 4 faixas com rampa LINEAR aparecia uma CAIXA clara no meio da tela. Em
        // leque apareciam emendas radiais. Aqui: 10 sub-faixas por borda com rampa QUADRÁTICA
        // (alfa = alvo*(1-u)^2) e alfa interpolado dentro de cada faixa — liso, sem degrau e
        // sem emenda, e os cantos escurecem um pouco mais, como na vinheta do protótipo.
        if (alvo <= 0.004f) {
            return;
        }
        int n = 10;
        float dx = w * 0.34f, dy = h * 0.34f;
        for (int i = 0; i < n; i++) {
            float u0 = i / (float) n, u1 = (i + 1) / (float) n;
            float a0 = alvo * (1 - u0) * (1 - u0), a1 = alvo * (1 - u1) * (1 - u1);
            int c0 = col(0x000000, a0), c1 = col(0x000000, a1);
            float y0 = dy * u0, y1 = dy * u1;
            B.quad(0, y0, c0, w, y0, c0, w, y1, c1, 0, y1, c1);                    // topo
            B.quad(0, h - y0, c0, w, h - y0, c0, w, h - y1, c1, 0, h - y1, c1);    // base
            float x0 = dx * u0, x1 = dx * u1;
            B.quad(x0, 0, c0, x0, h, c0, x1, h, c1, x1, 0, c1);                    // esquerda
            B.quad(w - x0, 0, c0, w - x0, h, c0, w - x1, h, c1, w - x1, 0, c1);    // direita
        }
    }

    // ================================================================= primitivas (canvas -> tela)
    static float sx(float x) {
        return OX + (x - PCX) * K;
    }

    static float sy(float y) {
        return OY + (y - PH / 2f) * K;
    }

    private static float tx(float x, float y) {
        return ma * x + mc * y + me;
    }

    private static float ty(float x, float y) {
        return mb * x + md * y + mf;
    }

    static float fs(float pxProto) {
        return pxProto * K / 9f;
    }

    /** (5.16.1) o texto do GUI troca o shader; sem rearmar, a geometria seguinte sai quebrada. */
    static void rearmar() {
        boolean eraAditivo = aditivo;
        B.flush();
        if (eraAditivo) {
            AvisoFxUtil.normal();
            aditivo = false;
        }
        B = AvisoRitual.comecar(G);
        if (eraAditivo) {
            aditivo();
        }
    }

    static void textoCentro(String s, float x, float y, float esc, int cor) {
        if (s == null || s.isEmpty()) {
            return;
        }
        B.flush();
        normal();
        AvisoRitual.textoCentro(G, s, sx(tx(x, y)), sy(ty(x, y)), esc, cor, true);
        B = AvisoRitual.comecar(G);
    }

    /** texto do canto esquerdo (o {@code AvisoRitual.texto} direto), já rearmando depois. */
    static void textoEm(String s, float x, float y, float esc, int cor, boolean sombra) {
        if (s == null || s.isEmpty()) {
            return;
        }
        B.flush();
        normal();
        AvisoRitual.texto(G, s, x, y, esc, cor, sombra);
        B = AvisoRitual.comecar(G);
    }

    static void save() {
        if (nivel + 1 >= PILHA) {
            return;
        }
        MA[nivel] = ma; MB[nivel] = mb; MC[nivel] = mc; MD[nivel] = md; ME[nivel] = me; MF[nivel] = mf;
        nivel++;
    }

    static void restore() {
        if (nivel <= 0) {
            return;
        }
        nivel--;
        ma = MA[nivel]; mb = MB[nivel]; mc = MC[nivel]; md = MD[nivel]; me = ME[nivel]; mf = MF[nivel];
    }

    static void translate(float dx, float dy) {
        me += ma * dx + mc * dy;
        mf += mb * dx + md * dy;
    }

    static void rotate(float rad) {
        float c = cos(rad), s = sin(rad);
        float na = ma * c + mc * s, nb = mb * c + md * s, nc = -ma * s + mc * c, nd = -mb * s + md * c;
        ma = na; mb = nb; mc = nc; md = nd;
    }

    static void scale(float fx, float fy) {
        ma *= fx;
        mb *= fx;
        mc *= fy;
        md *= fy;
    }

    static void aditivo() {
        if (!aditivo) {
            B.flush();
            AvisoFxUtil.aditivo();
            aditivo = true;
        }
    }

    static void normal() {
        if (aditivo) {
            B.flush();
            AvisoFxUtil.normal();
            aditivo = false;
        }
    }

    // ---- recorte simples (retângulo) para a arte da carta
    private static boolean recortando;
    private static float rcX, rcY, rcW, rcH;

    static void recorte(float x, float y, float w, float h) {
        recortando = true;
        rcX = x;
        rcY = y;
        rcW = w;
        rcH = h;
    }

    static void semRecorte() {
        recortando = false;
    }

    /** aplica o {@code setGlobalAlpha} do protótipo (só a carta usa). */
    private static int ca(int cor) {
        if (alfa >= 0.999f) {
            return cor;
        }
        int a = (cor >>> 24) & 255;
        return ((int) (a * alfa) << 24) | (cor & 0xFFFFFF);
    }

    private static boolean dentro(float x, float y) {
        return !recortando || (x >= rcX - 0.5f && x <= rcX + rcW + 0.5f && y >= rcY - 0.5f && y <= rcY + rcH + 0.5f);
    }

    static void retangulo(float x, float y, float w, float h, int cor) {
        cor = ca(cor);
        quad(tx(rcx(x), rcy(y)), ty(rcx(x), rcy(y)), tx(rcx(x + w), rcy(y)), ty(rcx(x + w), rcy(y)),
                tx(rcx(x + w), rcy(y + h)), ty(rcx(x + w), rcy(y + h)), tx(rcx(x), rcy(y + h)), ty(rcx(x), rcy(y + h)), cor);
    }

    private static void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int cor) {
        B.tri(sx(x1), sy(y1), cor, sx(x2), sy(y2), cor, sx(x3), sy(y3), cor);
        B.tri(sx(x1), sy(y1), cor, sx(x3), sy(y3), cor, sx(x4), sy(y4), cor);
    }

    /** prende o vértice dentro do recorte da carta (a arte não vaza pela moldura). */
    private static float rcx(float x) {
        return recortando ? Math.max(rcX, Math.min(rcX + rcW, x)) : x;
    }

    private static float rcy(float y) {
        return recortando ? Math.max(rcY, Math.min(rcY + rcH, y)) : y;
    }

    static void linha(float x1, float y1, float x2, float y2, float w, int cor) {
        if (!dentro(x1, y1) && !dentro(x2, y2)) {
            float mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
            if (!dentro(mx, my)) {
                return;
            }
        }
        cor = ca(cor);
        B.seg(sx(tx(x1, y1)), sy(ty(x1, y1)), sx(tx(x2, y2)), sy(ty(x2, y2)), Math.max(0.6f, w * K), cor, cor);
    }

    static void polilinha(float[] xs, float[] ys, int n, float w, int cor) {
        for (int i = 0; i + 1 < n; i++) {
            linha(xs[i], ys[i], xs[i + 1], ys[i + 1], w, cor);
        }
    }

    static void poligono(float[] xs, float[] ys, int n, int cor) {
        cor = ca(cor);
        for (int i = 1; i + 1 < n; i++) {
            B.tri(sx(tx(rcx(xs[0]), rcy(ys[0]))), sy(ty(rcx(xs[0]), rcy(ys[0]))), cor,
                    sx(tx(rcx(xs[i]), rcy(ys[i]))), sy(ty(rcx(xs[i]), rcy(ys[i]))), cor,
                    sx(tx(rcx(xs[i + 1]), rcy(ys[i + 1]))), sy(ty(rcx(xs[i + 1]), rcy(ys[i + 1]))), cor);
        }
    }

    static void contornoRet(float x, float y, float w, float h, float esp, int cor) {
        linha(x, y, x + w, y, esp, cor);
        linha(x + w, y, x + w, y + h, esp, cor);
        linha(x + w, y + h, x, y + h, esp, cor);
        linha(x, y + h, x, y, esp, cor);
    }

    static void disco(float cx, float cy, float r, int rgb, float a) {
        elipse(cx, cy, r, r, rgb, a);
    }


    static void elipse(float cx, float cy, float rx, float ry, int rgb, float a) {
        if (!dentro(cx, cy)) {
            return;
        }
        int cor = ca(col(rgb, a));
        // (5.16.1) NÃO escalar rx/ry aqui: o tx()/ty() de cada vértice já aplica a escala
        // da pilha — antes a elipse saía com o dobro do tamanho de encolhimento.
        int n = Math.max(10, Math.min(40, (int) (Math.max(rx, ry) * K * 0.7f)));
        float px = 0, py = 0;
        for (int i = 0; i <= n; i++) {
            float an = i * TAU / n;
            float ex = cx + cos(an) * rx, ey = cy + sin(an) * ry;
            if (i > 0) {
                B.tri(sx(tx(rcx(cx), rcy(cy))), sy(ty(rcx(cx), rcy(cy))), cor,
                        sx(tx(rcx(px), rcy(py))), sy(ty(rcx(px), rcy(py))), cor,
                        sx(tx(rcx(ex), rcy(ey))), sy(ty(rcx(ex), rcy(ey))), cor);
            }
            px = ex;
            py = ey;
        }
    }

    static void contorno(float cx, float cy, float r, float w, int cor) {
        anel(cx, cy, r, w, cor, lados(r));
    }

    /** (5.16.1) lados suficientes para o círculo não virar polígono na tela. */
    static int lados(float r) {
        return (int) Math.max(16, Math.min(64, r * K * 0.35f));
    }

    static void anel(float cx, float cy, float r, float w, int cor, int segs) {
        segs = Math.max(segs, lados(r));
        for (int i = 0; i < segs; i++) {
            float a0 = i * TAU / segs, a1 = (i + 1) * TAU / segs;
            linha(cx + cos(a0) * r, cy + sin(a0) * r, cx + cos(a1) * r, cy + sin(a1) * r, w, cor);
        }
    }

    /** o {@code fillArc(x,y,w,h,start,extent,ROUND)} do protótipo (setor de elipse). */
    static void setor(float x, float y, float w, float h, float inicioGraus, float extentGraus, int rgb, float a) {
        int cor = ca(col(rgb, a));
        float cx = x + w / 2f, cy = y + h / 2f, rx = w / 2f, ry = h / 2f;
        int n = Math.max(6, (int) (Math.abs(extentGraus) / Math.max(3f, 28f / Math.max(1f, Math.max(rx, ry) * K * 0.25f))));
        float a0 = inicioGraus * 0.0174533f;
        for (int i = 0; i < n; i++) {
            float t0 = a0 + (extentGraus * 0.0174533f) * i / n;
            float t1 = a0 + (extentGraus * 0.0174533f) * (i + 1) / n;
            B.tri(sx(tx(cx, cy)), sy(ty(cx, cy)), cor,
                    sx(tx(cx + cos(t0) * rx, cy + sin(t0) * ry)), sy(ty(cx + cos(t0) * rx, cy + sin(t0) * ry)), cor,
                    sx(tx(cx + cos(t1) * rx, cy + sin(t1) * ry)), sy(ty(cx + cos(t1) * rx, cy + sin(t1) * ry)), cor);
        }
    }

    /** degradê vertical de um retângulo (o {@code Theme.lin}). */
    static void degradeRect(float x, float y, float w, float h, int c0, int c1) {
        // (5.16.1) eram 12 faixas chapadas = degraus horizontais na cara da carta. Um
        // quadrilátero só, com as cores nas duas pontas: o jogo interpola e sai liso.
        float x0 = sx(tx(x, y)), y0 = sy(ty(x, y));
        float x1 = sx(tx(x + w, y)), y1 = sy(ty(x + w, y));
        float x2 = sx(tx(x + w, y + h)), y2 = sy(ty(x + w, y + h));
        float x3 = sx(tx(x, y + h)), y3 = sy(ty(x, y + h));
        int a0 = col(c0, alfa), a1 = col(c1, alfa);
        B.quad(x0, y0, a0, x1, y1, a0, x2, y2, a1, x3, y3, a1);
    }

    /**
     * mancha radial suave (o {@code Theme.rg}).
     *
     * <p>(5.16.1) Agora usa a textura de sombra do jogo ({@link AvisoBlob}): degradê radial de
     * verdade, sem degrau e sem catavento — que era o que aparecia quando eu empilhava anéis.
     */
    static void mancha(float cx, float cy, float r, int rgb, float a) {
        if (a <= 0.004f || r <= 0.5f) {
            return;
        }
        float px = sx(tx(cx, cy)), py = sy(ty(cx, cy));
        float rr = r * K * Math.abs(ma);
        B.flush();
        AvisoBlob.desenhar(G, px - rr, py - rr, rr * 2, rr * 2, rgb, a);
        B = new AvisoFxUtil.Buf(G.pose().last().pose());   // não usa comecar(): ele zera o blend
    }

    /** mancha radial ESTICADA (elipse macia) — para colunas de luz e poças de claridade. */
    static void manchaE(float cx, float cy, float rx, float ry, int rgb, float a) {
        if (a <= 0.004f || rx <= 0.5f || ry <= 0.5f) {
            return;
        }
        float px = sx(tx(cx, cy)), py = sy(ty(cx, cy));
        float w = rx * K * Math.abs(ma) * 2, h = ry * K * Math.abs(md) * 2;
        B.flush();
        AvisoBlob.desenhar(G, px - w / 2, py - h / 2, w, h, rgb, a);
        B = new AvisoFxUtil.Buf(G.pose().last().pose());
    }

    /** estrela de 8 pontas do {@code Theme.star8}. */
    static void estrela8(float cx, float cy, float Rr, float r, float rot, boolean preencher, int rgb, float a) {
        int n = 16;
        float[] xs = new float[n], ys = new float[n];
        for (int k = 0; k < n; k++) {
            float an = rot + k * (float) Math.PI / 8f, rr = k % 2 == 1 ? r : Rr;
            xs[k] = cx + sin(an) * rr;
            ys[k] = cy - cos(an) * rr;
        }
        if (preencher) {
            poligono(xs, ys, n, col(rgb, a));
        } else {
            for (int k = 0; k < n; k++) {
                linha(xs[k], ys[k], xs[(k + 1) % n], ys[(k + 1) % n], 1.2f, col(rgb, a));
            }
        }
    }

    /** hexagrama (dois triângulos). */
    static void hexagrama(float rad, float rot, float w, int cor) {
        for (int k = 0; k < 2; k++) {
            float[] xs = new float[3], ys = new float[3];
            for (int j = 0; j < 3; j++) {
                float an = rot - 1.5708f + j * 2.0944f + k * 1.0472f;
                xs[j] = cos(an) * rad;
                ys[j] = sin(an) * rad;
            }
            for (int j = 0; j < 3; j++) {
                linha(xs[j], ys[j], xs[(j + 1) % 3], ys[(j + 1) % 3], w, cor);
            }
        }
    }

    /** símbolo alquímico do protótipo: 0 fogo, 1 água, 2 ar, 3 terra. */
    static void simbolo(int k, float s, float w, int cor) {
        boolean up = k == 0 || k == 2;
        float d = up ? 1 : -1;
        float[] xs = {0, s, -s}, ys = {-s * d, s * 0.7f * d, s * 0.7f * d};
        for (int j = 0; j < 3; j++) {
            linha(xs[j], ys[j], xs[(j + 1) % 3], ys[(j + 1) % 3], w, cor);
        }
        if (k > 1) {
            linha(-s * 0.8f, 0, s * 0.8f, 0, w, cor);
        }
    }


    /** curva quadrática do protótipo, em 12 pedaços. */
    static void curva(float x0, float y0, float cx, float cy, float x1, float y1, float w, int cor) {
        int n = 12;
        float px = x0, py = y0;
        for (int i = 1; i <= n; i++) {
            float u = i / (float) n, v = 1 - u;
            float bx = v * v * x0 + 2 * v * u * cx + u * u * x1;
            float by = v * v * y0 + 2 * v * u * cy + u * u * y1;
            linha(px, py, bx, by, w, cor);
            px = bx;
            py = by;
        }
    }

    /** duas curvas fechando uma "folha" (o moveTo/quadTo/quadTo/fill do protótipo). */
    static void folha(float x0, float y0, float c1x, float c1y, float x1, float y1, float c2x, float c2y, int cor) {
        int n = 12;
        float[] xs = new float[2 * n + 1], ys = new float[2 * n + 1];
        int p = 0;
        for (int i = 0; i <= n; i++) {
            float u = i / (float) n, v = 1 - u;
            xs[p] = v * v * x0 + 2 * v * u * c1x + u * u * x1;
            ys[p] = v * v * y0 + 2 * v * u * c1y + u * u * y1;
            p++;
        }
        for (int i = 1; i <= n; i++) {
            float u = i / (float) n, v = 1 - u;
            xs[p] = v * v * x1 + 2 * v * u * c2x + u * u * x0;
            ys[p] = v * v * y1 + 2 * v * u * c2y + u * u * y0;
            p++;
        }
        poligono(xs, ys, p, cor);
    }

    // ---- conveniências
    static int rgba(int r, int g, int b, float a) {
        return col(((cl(r / 255f) > 0 ? Math.min(255, r) : 0) << 16) | (Math.min(255, Math.max(0, g)) << 8)
                | Math.min(255, Math.max(0, b)), a);
    }

    static float rn(int i, int s) {
        return rnd(i, s);
    }
}
