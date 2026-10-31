package dev.rptag.client;

import dev.rptag.RPTagMod;
import dev.rptag.TerremotoPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * (5.4.0) TERREMOTO no cliente: a tela treme sem parar (mais forte nos pulsos),
 * nos pulsos o jogo fica DESFOCADO com tom espacial (roxo/azul + estrelas) e, ao ser
 * levado, um verde dissolve a tela. Qualquer erro desliga só este efeito.
 */
@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class TerremotoFx {
    private static boolean ativo;
    private static long pulsoIni, pulsoFim, levarIni, levarFim;
    private static volatile boolean desligado;

    private TerremotoFx() {}

    public static void receber(int acao, int ticks) {
        long agora = System.currentTimeMillis();
        switch (acao) {
            case TerremotoPayload.INICIAR -> {
                ativo = true;
                levarFim = 0;
                var mc = Minecraft.getInstance();
                mc.getSoundManager().play(SimpleSoundInstance.forUI(RPTagMod.SOM_TERREMOTO.get(), 1.0F, 1.0F));
            }
            case TerremotoPayload.RETOMAR -> ativo = true;
            case TerremotoPayload.PULSO -> { pulsoIni = agora; pulsoFim = agora + ticks * 50L; }
            case TerremotoPayload.LEVAR -> { levarIni = agora; levarFim = agora + ticks * 50L; }
            default -> { ativo = false; pulsoFim = 0; levarFim = 0; }
        }
    }

    /** Para tudo (ao sair do mundo). */
    public static void parar() { ativo = false; pulsoFim = 0; levarFim = 0; }

    /** 0..1: sobe e desce suave ao longo do pulso. */
    static float intensidadePulso(long agora, long ini, long fim) {
        if (agora < ini || agora >= fim || fim <= ini) return 0F;
        float t = (agora - ini) / (float) (fim - ini);
        return (float) Math.sin(Math.PI * Math.min(1F, t) ) ;
    }

    /** 0..1: progresso de ser levado. */
    static float progressoLevar(long agora, long ini, long fim) {
        if (fim <= ini || agora < ini) return 0F;
        return Math.min(1F, (agora - ini) / (float) (fim - ini));
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (desligado || !ativo) return;
        try {
            long agora = System.currentTimeMillis();
            float amp = 0.45F + 1.9F * intensidadePulso(agora, pulsoIni, pulsoFim);
            float t = agora * 0.001F;
            event.setRoll(event.getRoll() + amp * (1.6F * (float) Math.sin(t * 21) + 0.7F * (float) Math.sin(t * 37.3)));
            event.setYaw(event.getYaw() + amp * 0.8F * (float) Math.sin(t * 14.1 + 1.3));
            event.setPitch(event.getPitch() + amp * 0.7F * (float) Math.sin(t * 17.9 + 0.4));
        } catch (Throwable e) {
            desligado = true;
            RPTagMod.LOGGER.error("RP Tag: tremor do terremoto desligado ate reiniciar o jogo", e);
        }
    }

    /** Camada de HUD (acima de tudo, funciona com shaders). */
    public static void desenhar(GuiGraphics g, float partialTick) {
        if (desligado || (!ativo && levarFim == 0)) return;
        try {
            long agora = System.currentTimeMillis();
            int w = g.guiWidth(), h = g.guiHeight();
            float pulso = intensidadePulso(agora, pulsoIni, pulsoFim);
            if (pulso > 0.35F) Minecraft.getInstance().gameRenderer.processBlurEffect(partialTick);
            // base: bordas roxas escuras sempre, mais fortes no pulso
            int borda = (int) ((0.18F + 0.5F * pulso) * 255);
            int roxo = 0x1A0838;
            g.fillGradient(0, 0, w, h / 4, (borda << 24) | roxo, 0x00000000);
            g.fillGradient(0, h - h / 4, w, h, 0x00000000, (borda << 24) | roxo);
            if (pulso > 0F) {
                g.fill(0, 0, w, h, ((int) (pulso * 70) << 24) | 0x2A1B6B);
                estrelas(g, w, h, agora, pulso);
            }
            if (levarFim > 0 && agora >= levarIni) verde(g, w, h, agora);
        } catch (Throwable e) {
            desligado = true;
            RPTagMod.LOGGER.error("RP Tag: camada do terremoto desligada ate reiniciar o jogo", e);
        }
    }

    private static void estrelas(GuiGraphics g, int w, int h, long agora, float pulso) {
        int n = 70;
        for (int i = 0; i < n; i++) {
            int x = Math.floorMod(i * 211 + (int) (agora / (9 + i % 5)), w);
            int y = Math.floorMod(i * 97 + (int) (agora / (23 + i % 7)), h);
            int a = (int) ((0.25F + 0.5F * ((i * 31) % 10) / 10F) * pulso * 255);
            int s = 1 + i % 3 / 2;
            g.fill(x, y, x + s, y + s, (a << 24) | 0xC9D4FF);
        }
    }

    private static void verde(GuiGraphics g, int w, int h, long agora) {
        float p = progressoLevar(agora, levarIni, levarFim);
        g.fill(0, 0, w, h, ((int) (p * p * 235) << 24) | 0x0F3D1E);
        int n = 60;
        for (int i = 0; i < n; i++) {
            int x = Math.floorMod(i * 173 + 31, w);
            float subida = Math.floorMod(i * 41, 100) / 100F + p * 1.4F;
            int y = h - (int) (Math.floorMod((int) (subida * h), h + 20));
            int a = (int) (Math.min(1F, p * 2F) * 230);
            g.fill(x, y, x + 2, y + 6, (a << 24) | 0x6BFF9A);
        }
        if (p > 0.85F) g.fill(0, 0, w, h, ((int) ((p - 0.85F) / 0.15F * 255) << 24) | 0xE6FFE9);
    }
}
