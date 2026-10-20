package dev.rptag.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * (4.2.0) PLAYER dos avisos em video (renderizados no Blender). Nao tem
 * camada propria: a classe do aviso (ex.: MorteAvisoFx) chama
 * desenhar(g, t) entre o fundo e o texto. Quadro escolhido pelo RELOGIO
 * (PC lento pula quadros); uma unica textura 1280x720 pra todos os avisos.
 * Qualquer problema -> FALHOU e o aviso segue no FX procedural (regra de
 * ouro 7: render nunca crasha; Throwable desliga o video na sessao).
 */
public final class VideoFx {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath("rptag", "fx_video");

    private static DynamicTexture tex;
    private static FxTimeline tl;
    private static VideoFrameLoader loader;
    private static volatile int geracao;
    private static int mostrado = -1;
    private static boolean comecou, desligadoSessao;
    private static long esperaDesde;

    private VideoFx() {}

    /** Liga o video do aviso; false = sem video (use o FX procedural). */
    public static boolean abrir(String id) {
        try {
            FxComando.garantirConfig();
            fechar();
            if (!FxConfig.video() || desligadoSessao) return false;
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("rptag", "fx/" + id + "/fx.json");
            Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (r.isEmpty()) return false;
            String json;
            try (InputStream in = r.get().open()) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            tl = FxTimeline.parse(json);
            int g = ++geracao;
            loader = new VideoFrameLoader(id, tl, g, () -> geracao);
            loader.iniciar();
            mostrado = -1;
            comecou = false;
            esperaDesde = System.nanoTime();
            return true;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag FX: video '{}' indisponivel: {}", id, t.toString());
            fechar();
            return false;
        }
    }

    /** Desenha o quadro do instante t (segundos desde o inicio do aviso) em tela cheia. */
    public static VideoEstado desenhar(GuiGraphics g, float t) {
        if (tl == null || loader == null) return VideoEstado.FALHOU;
        try {
            int q = Math.max(0, tl.quadroEm(t));
            long agora = System.nanoTime();
            if (!comecou) {
                VideoEstado e = VideoEstado.decidir(q, tl.frames(), false, loader.erro(),
                        (agora - esperaDesde) / 1e9f, loader.prontos(), false);
                if (e != VideoEstado.OK) {
                    if (e != VideoEstado.CARREGANDO) fechar();
                    return e;
                }
                comecou = true;
                esperaDesde = agora;
            }
            if (q >= tl.frames()) {
                fechar();
                return VideoEstado.FIM;
            }
            if (q > mostrado) {
                NativeImage img = loader.pegar(q);
                if (img != null) {
                    if (tex == null) {
                        tex = new DynamicTexture(img);
                        Minecraft.getInstance().getTextureManager().register(TEX, tex);
                    } else {
                        tex.setPixels(img); // fecha a imagem anterior
                    }
                    tex.upload();
                    mostrado = loader.ultimoPego();
                    esperaDesde = agora;
                }
            } else {
                esperaDesde = agora;
            }
            VideoEstado e = VideoEstado.decidir(q, tl.frames(), VideoEstado.emDia(mostrado, q), loader.erro(),
                    (agora - esperaDesde) / 1e9f, loader.prontos(), true);
            if (e == VideoEstado.FALHOU) {
                RPTagMod.LOGGER.warn("RP Tag FX: video sem quadro a tempo (quadro {}), seguindo procedural", q);
                fechar();
                return e;
            }
            if (mostrado >= 0) blit(g);
            return e;
        } catch (Throwable th) {
            RPTagMod.LOGGER.error("RP Tag FX: erro no player de video - video DESLIGADO nesta sessao", th);
            desligadoSessao = true;
            fechar();
            return VideoEstado.FALHOU;
        }
    }

    private static void blit(GuiGraphics g) {
        RenderSystem.enableBlend();
        if (tl.aditivo()) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        int w = FxTimeline.LARGURA, h = FxTimeline.ALTURA;
        int[] r = FxTimeline.cobrir(g.guiWidth(), g.guiHeight()); // 16:9 sem distorcer
        g.blit(TEX, r[0], r[1], r[2], r[3], 0, 0, w, h, w, h);
        g.flush();
        RenderSystem.defaultBlendFunc();
    }

    /** Para o video atual (fim, aviso novo, saida do mundo). */
    public static void fechar() {
        geracao++;
        if (loader != null) loader.parar();
        loader = null;
        tl = null;
        mostrado = -1;
        comecou = false;
    }

    /** Quantos avisos tem video no jar (pro /rpfx status). */
    public static int contarVideos() {
        try {
            return Minecraft.getInstance().getResourceManager()
                    .listResources("fx", p -> p.getPath().endsWith("fx.json")).size();
        } catch (Throwable t) {
            return 0;
        }
    }
}
