package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

/**
 * ACABAMENTO DE TELA DO DEUS DO VAZIO — 5.7.7.
 *
 * <h2>O que essa classe NÃO faz mais (e por quê)</h2>
 * Até a 5.7.2 ela desenhava a aura inteira NA TELA: 70 nuvens de fumaça,
 * rachaduras em perspectiva, tentáculos e estilhaços. No F5 isso virava um
 * <b>borrão preto flutuando no céu</b> — o dono mandou print duas vezes
 * ("ainda está esquisito na minha F5") e agora foi taxativo: a aura tem que ser
 * de ESCURIDÃO + RAIOS <b>saindo do player</b>, no mundo.
 *
 * <p>Então toda a fumaça/rachadura de tela SAIU. A aura de verdade é partícula
 * do servidor ({@code NeptuneEventos.auraVazio}: manto de escuridão + raios
 * negros e vermelhos). Aqui sobra só o acabamento que partícula não consegue
 * fazer e que NÃO parece mancha: uma vinheta escura nas bordas (bem leve) e a
 * granulação de filme. Nada no meio da tela, nada de figura flutuante.
 *
 * <p>Regra de ouro do mod: qualquer erro DESLIGA a camada, nunca o jogo.
 */
public final class VazioAuraDarkFx {

    private VazioAuraDarkFx() {
    }

    /** vinheta (0 = canto, 1 = centro) — quanto escurece no pico. */
    private static final float VINHETA = 0.34f;

    private static boolean ligado;
    private static long ultimo = 0L;
    private static float t;
    private static final Random SORTE = new Random();

    public static void ligar() {
        ligado = true;
        ultimo = System.nanoTime();
    }

    public static void desligar() {
        ligado = false;
    }

    public static boolean ligado() {
        return ligado;
    }

    public static void desenhar(GuiGraphics g) {
        if (!ligado) {
            return;
        }
        try {
            long agora = System.nanoTime();
            float dt = Math.min(0.1f, (agora - ultimo) / 1e9f);
            ultimo = agora;
            t += dt;

            int w = g.guiWidth();
            int h = g.guiHeight();
            float esc = Math.min(w, h);

            preparar(g);
            Buf b = new Buf(g.pose().last().pose());

            // (1) VINHETA: as bordas escurecem, o meio fica LIMPO — é o "tem
            //     alguma coisa em volta de mim" sem sujar a visão nem virar
            //     mancha flutuando no céu.
            float pulso = 0.86f + 0.14f * sin(t * 1.6f);
            b.vinheta(w, h, esc * 0.27f, 0x050308, VINHETA * pulso);
            b.vinheta(w, h, esc * 0.10f, 0x0B0410, 0.16f * pulso);

            // (2) GRANULAÇÃO de filme velho, só nas BORDAS (nunca no centro)
            for (int i = 0; i < 90; i++) {
                float x = SORTE.nextFloat() * w;
                float y = SORTE.nextFloat() * h;
                float dx = (x - w / 2f) / (w / 2f);
                float dy = (y - h / 2f) / (h / 2f);
                float fora = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy) - 0.45f);
                if (fora <= 0.02f) {
                    continue;
                }
                int a = (int) (40 * fora * pulso);
                if (a > 3) {
                    b.rect(x, y, 1.4f, 1.4f, col(0x140A1E, a / 255f));
                }
            }

            // (3) piscada RARA e fraca (o vazio "respira") — 1 em ~150 quadros
            if (SORTE.nextFloat() < 0.007f) {
                b.rect(0, 0, w, h, col(0x050308, 0.07f));
            }

            b.flush();
            normal();
        } catch (Throwable erro) {
            ligado = false;   // desliga a camada, o jogo continua
        }
    }
}
