package dev.rptag.client;

import static dev.rptag.client.AvisoFxUtil.*;

import java.util.Random;

import net.minecraft.client.gui.GuiGraphics;

/**
 * AURA DO VAZIO (deus) — 5.7.3.
 *
 * <h2>O que mudou (e por quê)</h2>
 * Nas 5.7.1/5.7.2 esta classe desenhava a aura NA TELA: 70 nuvens de fumaça,
 * rachaduras em perspectiva, tentáculos, estilhaços. No F5 (câmera atrás do
 * personagem) isso virava um BORRÃO PRETO flutuando no céu — o dono mandou a
 * print e disse "ainda está esquisito na minha f5". Faz sentido: era pintura de
 * tela, não aura.
 *
 * <p>Agora a aura existe **no mundo** (as partículas são do servidor, em
 * {@code NeptuneEventos.auraVazio}: coluna de escuridão e RAIOS negros e
 * vermelhos saindo do corpo). Esta camada só faz o acabamento que partícula não
 * faz: escurece as BORDAS da tela (vinheta), um pulso de sombra leve e a
 * granulação de filme. Nada de mancha no meio, nada de rachadura na tela.
 *
 * <p>Regra de ouro do mod: qualquer erro DESLIGA a aura, nunca o jogo.
 */
public final class VazioAuraDarkFx {

    private VazioAuraDarkFx() {
    }

    /** vinheta (0 = canto, 1 = centro) — quanto escurece no pico. */
    private static final float VINHETA = 0.42f;

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

            // (1) VINHETA: as bordas escurecem, o meio fica limpo (é o que dá
            //     "tem alguma coisa em volta de mim" sem sujar a visão)
            float pulso = 0.82f + 0.18f * sin(t * 1.7f);
            b.vinheta(w, h, esc * 0.30f, 0x050308, VINHETA * pulso);
            b.vinheta(w, h, esc * 0.12f, 0x0B0410, 0.22f * pulso);

            // (2) um "peso" escuro no rodapé (a escuridão sobe do chão)
            b.quad(0, h, col(0x07040B, 0.30f), w, h, col(0x07040B, 0.30f),
                    w, h - esc * 0.16f, col(0x07040B, 0f), 0, h - esc * 0.16f, col(0x07040B, 0f));
            // e um pouco no topo (simetria — senão parece só um degrau)
            b.quad(0, 0, col(0x07040B, 0.22f), w, 0, col(0x07040B, 0.22f),
                    w, esc * 0.10f, col(0x07040B, 0f), 0, esc * 0.10f, col(0x07040B, 0f));

            // (3) GRANULAÇÃO de filme velho, bem discreta e só nas bordas
            for (int i = 0; i < 130; i++) {
                float x = SORTE.nextFloat() * w;
                float y = SORTE.nextFloat() * h;
                float dx = (x - w / 2f) / (w / 2f);
                float dy = (y - h / 2f) / (h / 2f);
                float fora = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy) - 0.35f);
                if (fora <= 0.02f) {
                    continue;
                }
                int a = (int) (46 * fora * pulso);
                if (a > 3) {
                    b.rect(x, y, 1.4f, 1.4f, col(0x140A1E, a / 255f));
                }
            }

            // (4) o "piscar" raro: por 1 quadro a tela escurece um tico
            if (SORTE.nextFloat() < 0.012f) {
                b.rect(0, 0, w, h, col(0x050308, 0.10f));
            }

            b.flush();
            normal();
        } catch (Throwable erro) {
            ligado = false;   // desliga a camada, o jogo continua
        }
    }
}
