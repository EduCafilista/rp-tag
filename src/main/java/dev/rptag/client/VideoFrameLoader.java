package dev.rptag.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;

/**
 * (4.2.0) Thread que DECODIFICA os quadros de um aviso em video
 * (frames/NNNN_c.jpg + NNNN_a.jpg -> NativeImage RGBA) ate 8 quadros a frente
 * do quadro que o render pede. Nao toca na GPU (so a thread de render envia).
 * Ficou pra tras (PC lento)? Pula direto pro quadro pedido.
 */
final class VideoFrameLoader implements Runnable {
    static final int BUFFER = 8;

    private final String id;
    private final FxTimeline tl;
    private final int geracao;
    private final IntSupplier geracaoAtual;
    private final Map<Integer, NativeImage> prontos = new ConcurrentHashMap<>();
    private final Thread thread;
    private volatile int alvo = 0;
    private int ultimoPego = -1;
    private volatile boolean parar, erro;

    VideoFrameLoader(String id, FxTimeline tl, int geracao, IntSupplier geracaoAtual) {
        this.id = id;
        this.tl = tl;
        this.geracao = geracao;
        this.geracaoAtual = geracaoAtual;
        this.thread = new Thread(this, "rptag-fx-loader");
        this.thread.setDaemon(true);
    }

    void iniciar() { thread.start(); }
    boolean erro() { return erro; }
    int prontos() { return prontos.size(); }

    /**
     * Entrega o quadro PRONTO mais novo que nao passa de q (a posse passa pra
     * quem chamou) ou null; descarta os mais velhos. Atrasado? Mostra o que tem
     * (nunca fica esperando o quadro exato — era o que travava o PC lento).
     */
    NativeImage pegar(int q) {
        alvo = q;
        int k = VideoEstado.escolher(prontos.keySet(), q);
        for (Integer v : prontos.keySet()) {
            if (v < k) {
                NativeImage velho = prontos.remove(v);
                if (velho != null) velho.close();
            }
        }
        if (k < 0) return null;
        ultimoPego = k;
        return prontos.remove(k);
    }

    /** Indice do ultimo quadro entregue pelo pegar(). */
    int ultimoPego() { return ultimoPego; }

    void parar() {
        parar = true;
        thread.interrupt();
        limpar();
    }

    private void limpar() {
        for (Integer k : prontos.keySet()) {
            NativeImage img = prontos.remove(k);
            if (img != null) img.close();
        }
    }

    @Override
    public void run() {
        int prox = 0;
        try {
            while (!parar && prox < tl.frames()) {
                if (!VideoEstado.aceitaGeracao(geracao, geracaoAtual.getAsInt())) break;
                int a = alvo;
                if (prox < a) prox = a + 1;                   // atrasou: pula (1 a frente: o decode leva ~1 quadro)
                if (prox - a >= BUFFER) { Thread.sleep(2); continue; }
                NativeImage img = decodificar(prox);
                if (parar || !VideoEstado.aceitaGeracao(geracao, geracaoAtual.getAsInt())) { img.close(); break; }
                prontos.put(prox, img);
                prox++;
            }
        } catch (InterruptedException ignored) {
            // parar()
        } catch (Throwable t) {
            if (!parar) {
                erro = true;
                RPTagMod.LOGGER.warn("RP Tag FX: falha decodificando o video '{}' (quadro {}): {}", id, prox, t.toString());
            }
        }
        if (parar) limpar();
    }

    private NativeImage decodificar(int q) throws IOException {
        String base = String.format("fx/%s/frames/%04d", id, q);
        BufferedImage cor = ler(base + "_c.jpg"), alfa = ler(base + "_a.jpg");
        int w = FxTimeline.LARGURA, h = FxTimeline.ALTURA;
        if (cor.getWidth() != w || cor.getHeight() != h || alfa.getWidth() != w || alfa.getHeight() != h)
            throw new IOException("quadro " + q + " nao e 1280x720");
        int[] px = new int[w * h];
        if (cor.getType() == BufferedImage.TYPE_3BYTE_BGR && alfa.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            // caminho RAPIDO (~10 ms/quadro): bytes direto do raster
            FxCor.abgrDeRaster(((DataBufferByte) cor.getRaster().getDataBuffer()).getData(),
                    ((DataBufferByte) alfa.getRaster().getDataBuffer()).getData(), px);
        } else {
            // formato inesperado: caminho lento, mas correto
            int[] rgb = cor.getRGB(0, 0, w, h, null, 0, w);
            int[] a = alfa.getRGB(0, 0, w, h, null, 0, w);
            for (int i = 0; i < px.length; i++) px[i] = FxCor.abgr(rgb[i], (a[i] >> 16) & 0xFF);
        }
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0, i = 0; y < h; y++)
            for (int x = 0; x < w; x++, i++)
                img.setPixelRGBA(x, y, px[i]);
        return img;
    }

    private static BufferedImage ler(String caminho) throws IOException {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("rptag", caminho);
        Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(rl);
        if (r.isEmpty()) throw new IOException("faltando " + rl);
        try (InputStream in = r.get().open()) {
            return FxJpeg.ler(in);
        } catch (IOException e) {
            throw new IOException(rl + ": " + e.getMessage(), e);
        }
    }
}
