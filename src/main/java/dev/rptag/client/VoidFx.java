package dev.rptag.client;

import dev.rptag.RPTagMod;
import net.minecraft.client.gui.GuiGraphics;

/**
 * (5.5.0) Cliente do PODER DO VOID: a tela escurece até o preto total (com
 * bordas roxas pulsando) na hora em que o adm usa o poder sobre este jogador.
 */
public final class VoidFx {
    private static long ini, fim;
    private static volatile boolean desligado;

    private VoidFx() {}

    public static void receber(int ticks) {
        long agora = System.currentTimeMillis();
        ini = agora;
        fim = agora + Math.max(1, ticks) * 50L;
    }

    public static void parar() { fim = 0; }

    /** 0..1 do escurecer; depois do fim, clareia em 1,5 s (a vítima já foi movida). */
    public static float progresso(long agora, long ini, long fim) {
        if (fim <= ini || agora < ini) return 0F;
        if (agora < fim) return (agora - ini) / (float) (fim - ini);
        long extra = agora - fim;
        if (extra >= 1500) return 0F;
        return 1F - extra / 1500F;
    }

    public static void desenhar(GuiGraphics g) {
        if (desligado || fim == 0) return;
        try {
            long agora = System.currentTimeMillis();
            float p = progresso(agora, ini, fim);
            if (p <= 0F) { if (agora > fim + 1500) fim = 0; return; }
            int w = g.guiWidth(), h = g.guiHeight();
            g.fill(0, 0, w, h, ((int) (Math.min(1F, p * p * 1.05F) * 255) << 24));
            float pulso = 0.5F + 0.5F * (float) Math.sin(agora * 0.012);
            int a = (int) (p * (60 + 70 * pulso));
            g.fillGradient(0, 0, w, h / 3, (a << 24) | 0x3A0A5C, 0x00000000);
            g.fillGradient(0, h - h / 3, w, h, 0x00000000, (a << 24) | 0x3A0A5C);
        } catch (Throwable e) {
            desligado = true;
            RPTagMod.LOGGER.error("RP Tag: efeito do void desligado ate reiniciar o jogo", e);
        }
    }
}
