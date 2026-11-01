package dev.rptag.client;

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
 * (5.6.1) VÍDEO ATRÁS DO PLAYER NO TABLET — em LOOP enquanto a ficha está aberta.
 *
 * <p>O dono mandou um mp4 e pediu: "esse video deve ficar atras do player quando
 * ele abrir o tablet". O mod não toca mp4: o vídeo é convertido em pares de
 * JPEG por quadro (o mesmo formato dos avisos em vídeo) por
 * {@code arte/mp4_para_fx.py}:
 *
 * <pre>
 *   assets/rptag/fx/tablet_fundo/fx.json          {"fps":24,"frames":N,"width":1280,...}
 *   assets/rptag/fx/tablet_fundo/frames/NNNN_c.jpg  (cor 1280x720)
 *   assets/rptag/fx/tablet_fundo/frames/NNNN_a.jpg  (alpha 1280x720)
 * </pre>
 *
 * <p>Comportamento (pedido do dono: <b>loop</b>): o vídeo roda e, quando chega
 * no fim, volta do começo — sem parar. O player 3D é desenhado POR CIMA dele.
 *
 * <p>Regras de ouro do mod: se o asset não existir, nada aparece (o tablet
 * continua igual) e qualquer erro desliga o vídeo nesta sessão — o jogo nunca
 * cai por causa disso. Respeita {@code fxVideo=false} do cliente (PC fraco).
 */
public final class TabletVideoFundo {

    /** pasta em assets/rptag/fx/<ID> — é o que o arte/mp4_para_fx.py gera. */
    public static final String ID = "tablet_fundo";

    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "tablet_fundo_video");

    private TabletVideoFundo() {
    }

    private static DynamicTexture tex;
    private static FxTimeline tl;
    private static VideoFrameLoader loader;
    private static volatile int geracao;
    private static int mostrado = -1;
    private static long inicioLoop;
    private static boolean semAsset;      // não tem fx.json: não tenta de novo
    private static boolean desligado;     // erro: desliga nesta sessão
    private static long ultimoQuadroNovo;  // (5.7.1) trava anti-congelamento

    // ------------------------------------------------------------------ abrir
    /** Abre (ou reabre) o vídeo do começo. Chamado quando o tablet aparece. */
    public static void abrir() {
        if (desligado || semAsset) {
            return;
        }
        try {
            FxComando.garantirConfig();
            if (!FxConfig.video()) {
                desligado = true;      // cliente pediu "sem vídeo"
                return;
            }
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID,
                    "fx/" + ID + "/fx.json");
            Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (r.isEmpty()) {
                semAsset = true;       // ainda não tem vídeo: o tablet fica como era
                return;
            }
            String json;
            try (InputStream in = r.get().open()) {
                json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            tl = FxTimeline.parse(json);
            int g = ++geracao;
            loader = new VideoFrameLoader(ID, tl, g, () -> geracao, true);   // true = EM LOOP
            loader.iniciar();
            mostrado = -1;
            inicioLoop = System.nanoTime();
        } catch (Throwable t) {
            desligado = true;
            RPTagMod.LOGGER.warn("RP Tag: video de fundo do tablet desligado ({})", t.toString());
        }
    }

    /** Para o vídeo (tablet fechado). */
    public static void fechar() {
        geracao++;
        if (loader != null) {
            loader.parar();
        }
        loader = null;
        tl = null;
        mostrado = -1;
    }

    // ---------------------------------------------------------------- desenhar
    /**
     * Desenha o quadro atual preenchendo o retângulo (x,y,w,h) — recorte
     * "cover": cobre a área sem distorcer, cortando o que sobrar.
     */
    public static void desenhar(GuiGraphics g, int x, int y, int w, int h) {
        if (desligado || semAsset || tl == null || loader == null || w <= 4 || h <= 4) {
            return;
        }
        try {
            long agora = System.nanoTime();
            float t = (agora - inicioLoop) / 1e9F;
            int frames = tl.frames();
            int q = (int) (t * tl.fps());
            if (q >= frames) {              // LOOP: acabou? volta do começo sem parar
                // soma o tempo de um clipe (mantém a fase) em vez de recriar o
                // decodificador: o VideoFrameLoader está rodando com loop=true e já
                // tem o quadro 0 pronto — assim a volta não dá engasgo
                long volta = (long) (frames * 1_000_000_000L / Math.max(1, tl.fps()));
                inicioLoop += volta;
                t = (agora - inicioLoop) / 1e9F;
                q = (int) (t * tl.fps());
                if (q >= frames || q < 0) { inicioLoop = agora; q = 0; }
            }
            NativeImage img = loader.pegar(q);
            if (img != null) {
                if (tex == null) {
                    tex = new DynamicTexture(img);
                    Minecraft.getInstance().getTextureManager().register(TEX, tex);
                } else {
                    tex.setPixels(img);   // fecha a imagem anterior
                }
                tex.upload();
                mostrado = loader.ultimoPego();
            }
            if (mostrado < 0) {
                // (5.7.1) ficou tempo demais sem NENHUM quadro? o decodificador
                // morreu (memória/IO): recomeça em vez de ficar em preto
                if (inicioLoop > 0 && (System.nanoTime() - inicioLoop) > 4_000_000_000L) {
                    fechar();
                    abrir();
                }
                return;                     // ainda decodificando o primeiro
            }

            int texW = FxTimeline.LARGURA;
            int texH = FxTimeline.ALTURA;
            float alvoRatio = w / (float) h;
            float texRatio = texW / (float) texH;
            int desenhaW;
            int desenhaH;
            // (5.7.1) "cover": enche o painel (o painel é retrato e o vídeo é
            // 16:9, então corta só as beiradas — o buraco negro fica inteiro no
            // meio). A animação roda do primeiro ao último quadro em loop.
            if (alvoRatio > texRatio) {
                desenhaW = w;
                desenhaH = Math.round(w / texRatio);
            } else {
                desenhaH = h;
                desenhaW = Math.round(h * texRatio);
            }
            int dx = x + (w - desenhaW) / 2;
            int dy = y + (h - desenhaH) / 2;

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // apagado só o suficiente pra o player e o texto continuarem legíveis
            RenderSystem.setShaderColor(0.72F, 0.70F, 0.84F, 0.95F);
            g.enableScissor(x, y, x + w, y + h);
            g.blit(TEX, dx, dy, desenhaW, desenhaH, 0, 0, texW, texH, texW, texH);
            g.disableScissor();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            g.flush();
            RenderSystem.defaultBlendFunc();

            // faixas pretas em degradê pra o vídeo "derreter" no painel
            float s = Math.max(8f, w / 48f);
            AvisoFxUtil.preparar(g);
            AvisoFxUtil.Buf bf = new AvisoFxUtil.Buf(g.pose().last().pose());
            int preto = AvisoFxUtil.col(0x0A0812, 0.85f);
            int zero = AvisoFxUtil.col(0x0A0812, 0f);
            float topo = Math.max(y, dy);
            float base = Math.min(y + h, dy + desenhaH);
            s = Math.max(8f, Math.min(w, h) / 26f);
            bf.quad(x, topo, preto, x + w, topo, preto, x + w, topo + s, zero, x, topo + s, zero);
            bf.quad(x, base, preto, x + w, base, preto, x + w, base - s, zero, x, base - s, zero);
            bf.flush();
            AvisoFxUtil.normal();
        } catch (Throwable t) {
            desligado = true;
            fechar();
            RPTagMod.LOGGER.warn("RP Tag: video de fundo do tablet desligado ({})", t.toString());
        }
    }

    /**
     * (5.6.1) true quando o vídeo está de fato desenhando na tela — nesse caso o
     * tablet NÃO desenha o aro procedural: o vídeo do dono já é um buraco negro
     * com o próprio aro, dois aros ficaria estranho.
     */
    public static boolean ativo() {
        return !desligado && !semAsset && tl != null && loader != null && mostrado >= 0;
    }

    /** true se existe algum vídeo de fundo disponível (pro /rpfx status). */
    public static boolean disponivel() {
        return !desligado && !semAsset && tl != null;
    }
}
