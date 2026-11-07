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
    /** (5.7.1) teto de quadros guardados quando o video é em LOOP (~3,7 MB cada). */
    static final int CACHE_LOOP = 16;

    private final String id;
    private final FxTimeline tl;
    private final int geracao;
    private final IntSupplier geracaoAtual;
    /** (5.6.1) true = quando chega no ultimo quadro volta pro 0 (video de fundo do tablet). */
    private final boolean loop;
    private final Map<Integer, NativeImage> prontos = new ConcurrentHashMap<>();
    private final Thread thread;
    private volatile int alvo = 0;
    private int ultimoPego = -1;
    private volatile boolean parar, erro;

    VideoFrameLoader(String id, FxTimeline tl, int geracao, IntSupplier geracaoAtual) {
        this(id, tl, geracao, geracaoAtual, false);
    }

    VideoFrameLoader(String id, FxTimeline tl, int geracao, IntSupplier geracaoAtual, boolean loop) {
        this.loop = loop;
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
        if (!loop) {
            for (Integer v : prontos.keySet()) {
                if (v < k) {
                    NativeImage velho = prontos.remove(v);
                    if (velho != null) velho.close();
                }
            }
        } else if (prontos.size() > CACHE_LOOP) {
            // (5.7.1) no LOOP nao dá pra descartar "tudo o que é menor": na volta
            // os quadros novos (0,1,2…) chegam enquanto o alvo ainda é 119. Então
            // limita pelo TAMANHO, tirando os mais DISTANTES (em distância
            // circular). Antes o cache crescia sem parar e em 2-3 voltas o jogo
            // engasgava/caía (o dono viu a animação "congelar").
            int total = tl.frames();
            java.util.List<Integer> chaves = new java.util.ArrayList<>(prontos.keySet());
            chaves.sort((a, b) -> Integer.compare(dist(a, q, total), dist(b, q, total)));
            for (int i = chaves.size() - 1; i >= 0 && prontos.size() > CACHE_LOOP / 2; i--) {
                NativeImage velho = prontos.remove(chaves.get(i));
                if (velho != null) velho.close();
            }
        }
        if (k < 0) return null;
        ultimoPego = k;
        return prontos.remove(k);
    }

    /** Indice do ultimo quadro entregue pelo pegar(). */
    int ultimoPego() { return ultimoPego; }

    /** distância circular entre dois índices de quadro (pro limite do cache no loop). */
    private static int dist(int a, int b, int total) {
        int d = Math.abs(a - b);
        return Math.min(d, Math.max(1, total) - d);
    }

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
            while (!parar) {
                if (!VideoEstado.aceitaGeracao(geracao, geracaoAtual.getAsInt())) break;
                if (prox >= tl.frames()) {
                    if (!loop) break;
                    prox = 0;                       // LOOP: volta pro primeiro quadro
                }
                int a = alvo;
                if (prox < a) prox = a + 1;                   // atrasou: pula (1 a frente: o decode leva ~1 quadro)
                if (prox - a >= BUFFER) { Thread.sleep(2); continue; }
                NativeImage img = decodificar(prox);
                if (parar || !VideoEstado.aceitaGeracao(geracao, geracaoAtual.getAsInt())) { img.close(); break; }
                NativeImage antigo = prontos.put(prox, img);
                if (antigo != null) antigo.close();   // (5.7.1) na volta o índice repete
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
        // (5.17.0) O QUADRO MANDA NO TAMANHO. Antes exigia 1280x720; agora aceita
        // o que vier (os quadros do jar sao 896x504 pra pesar menos) — a textura
        // e criada no tamanho do arquivo e quem estica pra tela cheia e a GPU,
        // de graça. Continua exigindo cor e alfa do MESMO tamanho (caminho rapido).
        int w = cor.getWidth(), h = cor.getHeight();
        if (alfa.getWidth() != w || alfa.getHeight() != h)
            throw new IOException("quadro " + q + ": cor " + w + "x" + h
                    + " e alfa " + alfa.getWidth() + "x" + alfa.getHeight() + " nao batem");
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
