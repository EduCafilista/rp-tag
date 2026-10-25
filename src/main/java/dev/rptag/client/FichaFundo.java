package dev.rptag.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.util.Optional;

/**
 * (4.8.2) O FUNDO DO TABLET — a mesma receita do BALÃO: <b>blur + estrelas</b>.
 *
 * <p>A tela chama {@code renderBlurredBackground} (o borrão do mundo vanilla) e
 * aqui por cima entra só um véu azul-noite, estrelinhas cintilando, um cometa
 * de vez em quando e a vinheta. Nada de nebulosa roxa encharcada — o dono pediu
 * menos roxo e algo mais "galáxia simples".
 *
 * <p>O buraco negro e o disco do chão continuam em TEXTURA (4.8.1), agora com o
 * filtro LINEAR garantido: as texturas são carregadas numa {@link DynamicTexture}
 * e registradas por cima — o carregamento normal do jogo pode reaplicar NEAREST
 * depois, e era isso que deixava o disco e o anel quadriculados na tela.
 */
final class FichaFundo {

    private static final ResourceLocation BURACO = tex("ficha_buraco");
    private static final ResourceLocation ANEL = tex("ficha_anel");

    private static final int BUR_W = 640, BUR_H = 640;
    /** raio da esfera preta dentro do sprite (px da textura). */
    private static final float BUR_RAIO_TEX = 118f;
    private static final int ANE_W = 512, ANE_H = 256;

    private static boolean pronto;

    private FichaFundo() {
    }

    private static ResourceLocation tex(String nome) {
        return ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/" + nome + ".png");
    }

    /**
     * Carrega as texturas numa {@link DynamicTexture} com filtro LINEAR.
     * Registrar com o MESMO id substitui a textura normal do jogo.
     */
    static void preparar() {
        if (pronto) {
            return;
        }
        pronto = true;
        carregar(BURACO);
        carregar(ANEL);
    }

    private static void carregar(ResourceLocation rl) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Optional<Resource> res = mc.getResourceManager().getResource(rl);
            if (res.isEmpty()) {
                RPTagMod.LOGGER.warn("RP Tag: textura do tablet nao encontrada ({})", rl);
                return;
            }
            NativeImage img;
            try (InputStream in = res.get().open()) {
                img = NativeImage.read(in);
            }
            DynamicTexture tex = new DynamicTexture(img);
            tex.upload();
            tex.setFilter(true, false);   // LINEAR: some o serrilhado/quadriculado
            mc.getTextureManager().register(rl, tex);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: falha ao preparar {} ({})", rl, t.toString());
        }
    }

    // ---------------------------------------------------------------- céu
    /**
     * Desenha o céu do tablet. {@code comBlur} = a tela já embaçou o mundo
     * (igual ao balão); sem blur cai num degradê sólido.
     */
    static void ceu(GuiGraphics g, int w, int h, float t, boolean comBlur) {
        if (comBlur) {
            g.fill(0, 0, w, h, 0x42070C18);              // véu azul-noite (pouco roxo)
        } else {
            g.fillGradient(0, 0, w, h, 0xFF05070F, 0xFF0B1626);
        }
        // brilho frio descendo do topo — dá profundidade sem puxar pro roxo
        g.fillGradient(0, 0, w, (int) (h * 0.62f), 0x243C82C8, 0x00000000);
        // uma faixa de "via láctea" bem discreta, na diagonal
        for (int i = 0; i < 3; i++) {
            float p = (i + 0.5f) / 3f;
            int y = (int) (h * 0.80f - p * h * 0.52f);
            g.fillGradient(-10, y - 14, w + 10, y + 14, 0x00000000, 0x00000000);
            g.fill(-10, y, w + 10, y + 1, 0x0FD8E8FF);
            g.fill(-10, y + 4, w + 10, y + 5, 0x08D8E8FF);
        }

        estrelas(g, w, h, t);
        vinheta(g, w, h);
    }

    private static void estrelas(GuiGraphics g, int w, int h, float t) {
        long tt = (long) (t * 20f);

        // poeira: pontinhos fixos, cintilando devagar
        for (int i = 0; i < 84; i++) {
            int sx = (i * 211 + 97) % Math.max(1, w);
            int sy = (i * 113 + 53) % Math.max(1, h);
            int al = 0x20 + ((i * 31 + (int) (tt % 3L)) % 0x2C);
            int cor = (i % 7 == 0) ? 0xFFE8A83C : 0xBFD4FF;
            g.fill(sx, sy, sx + 1, sy + 1, al << 24 | cor);
        }

        // estrelas vivas: 18 maiores, com pulso e cruz de brilho no pico
        for (int i = 0; i < 18; i++) {
            int sx = (i * 419 + 211) % Math.max(1, w);
            int sy = (i * 307 + 151) % Math.max(1, h);
            int pul = (int) ((tt / 20L + i) % 3L);
            g.fill(sx, sy, sx + 2, sy + 2, (0x60 + pul * 0x2A) << 24 | 0xFFE9C8);
            if (pul == 2) {
                g.fill(sx - 3, sy, sx + 5, sy + 1, 0x40FFE9C8);
                g.fill(sx, sy - 3, sx + 1, sy + 5, 0x40FFE9C8);
            }
        }

        // cometa: risca o céu a cada ~9 s
        long ciclo = tt % 180L;
        if (ciclo < 22L) {
            float p = ciclo / 22f;
            int hx = (int) (w * (1.05f - 1.25f * p));
            int hy = (int) (h * (0.08f + 0.32f * p));
            for (int k = 0; k < 6; k++) {
                g.fill(hx + k * 4, hy - k * 2, hx + k * 4 + 3, hy - k * 2 + 1,
                        (0x90 - k * 0x18) << 24 | 0xCFE8FF);
            }
            g.fill(hx - 1, hy - 1, hx + 2, hy + 2, 0xE0FFFFFF);
        }
    }

    private static void vinheta(GuiGraphics g, int w, int h) {
        for (int i = 0; i < 5; i++) {
            int a = 0x24 - i * 7;
            g.fill(0, i * 4, w, i * 4 + 4, a << 24);
            g.fill(0, h - i * 4 - 4, w, h - i * 4, a << 24);
        }
        for (int i = 0; i < 3; i++) {
            g.fill(i * 4, 0, i * 4 + 4, h, (0x18 - i * 6) << 24);
            g.fill(w - i * 4 - 4, 0, w - i * 4, h, (0x18 - i * 6) << 24);
        }
    }

    // -------------------------------------------------------- personagem
    /** o buraco negro de Netuno, atrás do personagem. */
    static void buracoNegro(GuiGraphics g, float cx, float cy, float raioEsfera, float t) {
        float pulso = 1f + 0.012f * Mth.sin(t * 1.25f);
        int lado = Math.round(raioEsfera * (BUR_W / BUR_RAIO_TEX) * pulso);
        blit(g, BURACO, Math.round(cx - lado / 2f), Math.round(cy - lado / 2f), lado, lado, BUR_W, BUR_H, 1f);
    }

    /** o disco mágico no chão (elipse 2:1), com pulso de brilho. */
    static void discoMagico(GuiGraphics g, float cx, float cy, float raio, float t) {
        float pulso = 1f + 0.02f * Mth.sin(t * 1.6f);
        int larg = Math.round(raio * 2f * pulso);
        int alt = Math.round(larg * ((float) ANE_H / ANE_W));
        float alfa = 0.70f + 0.30f * (0.5f + 0.5f * Mth.sin(t * 1.9f));
        blit(g, ANEL, Math.round(cx - larg / 2f), Math.round(cy - alt / 2f), larg, alt, ANE_W, ANE_H, alfa);
    }

    // ---------------------------------------------------------- pedaços
    private static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
            int texW, int texH, float alfa) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.setColor(1f, 1f, 1f, alfa);
        g.blit(tex, x, y, w, h, 0f, 0f, texW, texH, texW, texH);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** cor ARGB a partir de RGB + alfa 0..1. */
    static int corComAlfa(int rgb, float a) {
        int alfa = (int) (Mth.clamp(a, 0f, 1f) * 255) << 24;
        return alfa | (rgb & 0xFFFFFF);
    }
}
