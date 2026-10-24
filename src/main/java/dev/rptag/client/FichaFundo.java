package dev.rptag.client;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * (4.8.1) O FUNDO DO TABLET — galáxia no estilo da arte do dono.
 *
 * <p>A 4.8.0 desenhava o buraco negro com retângulos de 1 px na GUI: de perto
 * aquilo virava um monte de "pixels quebrados" atrás do personagem. Agora o
 * fundo, o buraco negro e o disco do chão são <b>texturas com gradiente e
 * anti-aliasing</b>, desenhadas com filtro LINEAR — suave em qualquer escala.
 *
 * <p>As texturas são geradas por {@code arte/gerar_texturas_ficha.py}
 * (reproduzível): nebulosa roxa, buraco negro com disco de acreção laranja e o
 * anel mágico azul do pedestal.
 */
final class FichaFundo {

    private static final ResourceLocation NEBULOSA = tex("ficha_nebulosa");
    private static final ResourceLocation BURACO = tex("ficha_buraco");
    private static final ResourceLocation ANEL = tex("ficha_anel");

    private static final int NEB_W = 1024, NEB_H = 576;
    private static final int BUR_W = 640, BUR_H = 640;
    /** raio da esfera dentro do sprite do buraco (em pixels da textura). */
    private static final float BUR_RAIO_TEX = 118f;
    private static final int ANE_W = 512, ANE_H = 256;

    private static boolean filtroPronto;

    private FichaFundo() {
    }

    private static ResourceLocation tex(String nome) {
        return ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/" + nome + ".png");
    }

    /**
     * Liga o filtro LINEAR nas texturas. O padrão do jogo é NEAREST, e é isso que
     * deixa a borda serrilhada quando a textura é reduzida na tela.
     */
    static void preparar() {
        if (filtroPronto) {
            return;
        }
        filtroPronto = true;
        try {
            TextureManager tm = Minecraft.getInstance().getTextureManager();
            for (ResourceLocation rl : new ResourceLocation[]{NEBULOSA, BURACO, ANEL}) {
                AbstractTexture t = tm.getTexture(rl);
                t.setFilter(true, false);
            }
        } catch (Exception e) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui suavizar as texturas da ficha ({})", e.toString());
        }
    }

    // ------------------------------------------------------------- fundo
    static void fundo(GuiGraphics g, int w, int h, float t) {
        g.fillGradient(0, 0, w, h, 0xFF050309, 0xFF0C0718);

        // camada grande: deriva devagar (parallax)
        int dx = Math.round(Mth.sin(t * 0.05f) * 9f);
        int dy = Math.round(Mth.cos(t * 0.04f) * 6f);
        blit(g, NEBULOSA, -14 + dx, -12 + dy, w + 28, h + 26, NEB_W, NEB_H, 1f);

        // segunda camada, menor e mais fraca, dá profundidade
        int w2 = (int) (w * 0.78f);
        int h2 = (int) (h * 0.95f);
        int x2 = (int) (w * 0.40f + Mth.sin(t * 0.031f + 1f) * 16f);
        blit(g, NEBULOSA, x2, -h / 5, w2, h2, NEB_W, NEB_H, 0.55f);

        estrelas(g, w, h, t);

        // vinheta: fecha as bordas para o olho ir no centro
        g.fillGradient(0, 0, w, (int) (h * 0.16f), 0x88000000, 0x00000000);
        g.fillGradient(0, (int) (h * 0.84f), w, h, 0x00000000, 0x99000000);
    }

    /** o buraco negro de Netuno, atrás do personagem. */
    static void buracoNegro(GuiGraphics g, float cx, float cy, float raioEsfera, float t) {
        float pulso = 1f + 0.012f * Mth.sin(t * 1.25f);
        int lado = Math.round(raioEsfera * (BUR_W / BUR_RAIO_TEX) * pulso);
        blit(g, BURACO, Math.round(cx - lado / 2f), Math.round(cy - lado / 2f), lado, lado, BUR_W, BUR_H, 1f);
    }

    /** o disco mágico no chão, embaixo do personagem. */
    static void discoMagico(GuiGraphics g, float cx, float cy, float raio, float t) {
        float pulso = 1f + 0.025f * Mth.sin(t * 1.6f);
        int larg = Math.round(raio * 2.25f * pulso);
        int alt = Math.round(larg * ((float) ANE_H / ANE_W));
        float alfa = 0.72f + 0.28f * (0.5f + 0.5f * Mth.sin(t * 1.9f));
        blit(g, ANEL, Math.round(cx - larg / 2f), Math.round(cy - alt / 2f), larg, alt, ANE_W, ANE_H, alfa);
    }

    // ------------------------------------------------------------- pedaços
    private static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
            int texW, int texH, float alfa) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.setColor(1f, 1f, 1f, alfa);
        g.blit(tex, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
        g.setColor(1f, 1f, 1f, 1f);
    }

    private static void estrelas(GuiGraphics g, int w, int h, float t) {
        for (int i = 0; i < 46; i++) {
            float x = (rnd(i, 1) * w + t * (2f + rnd(i, 5) * 5f)) % (w + 6) - 3;
            float y = rnd(i, 2) * h;
            float tw = 0.5f + 0.5f * Mth.sin(t * (0.9f + rnd(i, 3)) + i * 1.7f);
            int cor = i % 9 == 0 ? 0xFFD9A0 : (i % 5 == 0 ? 0x9FD8FF : 0xFFFFFF);
            int a = (int) (Math.max(0f, Math.min(1f, tw)) * 190);
            int s = rnd(i, 4) > 0.86f ? 2 : 1;
            g.fill((int) (x - s / 2f), (int) (y - s / 2f), (int) (x + (s + 1) / 2f), (int) (y + (s + 1) / 2f),
                    (a << 24) | (cor & 0xFFFFFF));
        }
    }

    /** cor ARGB a partir de RGB + alfa 0..1. */
    static int corComAlfa(int rgb, float a) {
        int alfa = (int) (Mth.clamp(a, 0f, 1f) * 255) << 24;
        return alfa | (rgb & 0xFFFFFF);
    }

    private static float rnd(int i, int s) {
        float v = (float) (Math.sin(i * 12.9898 + s * 78.233) * 43758.547);
        return v - (float) Math.floor(v);
    }
}
