package dev.rptag.client;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
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
        try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> it = iis == null ? null : ImageIO.getImageReaders(iis);
            if (it == null || !it.hasNext()) throw new IOException("nao e JPEG");
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
