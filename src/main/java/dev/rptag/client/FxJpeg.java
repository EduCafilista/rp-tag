package dev.rptag.client;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * (4.2.0) Leitura dos quadros JPEG dos avisos em video. Java puro (testavel).
 * JPEG cortado/corrompido o ImageIO "le pela metade" so com um AVISO (sem
 * excecao) — aqui qualquer aviso do decodificador vira IOException, pra o
 * player cair no FX procedural em vez de mostrar meio quadro cinza.
 */
public final class FxJpeg {
    private FxJpeg() {}

    public static BufferedImage ler(InputStream in) throws IOException {
        // cache em MEMORIA: o createImageInputStream padrao grava um arquivo temporario
        // no disco a cada leitura (288 por aviso) — lento e sujando o %TEMP%
        try (ImageInputStream iis = new MemoryCacheImageInputStream(in)) {
            Iterator<ImageReader> it = ImageIO.getImageReaders(iis);
            if (!it.hasNext()) throw new IOException("nao e JPEG");
            ImageReader r = it.next();
            String[] aviso = new String[1];
            r.addIIOReadWarningListener((src, msg) -> aviso[0] = msg);
            try {
                r.setInput(iis, true, true);
                BufferedImage img = r.read(0);
                if (aviso[0] != null) throw new IOException("JPEG corrompido: " + aviso[0]);
                return img;
            } finally {
                r.dispose();
            }
        }
    }
}
