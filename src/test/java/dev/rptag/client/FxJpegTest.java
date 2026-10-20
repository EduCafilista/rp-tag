package dev.rptag.client;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class FxJpegTest {

    private static byte[] quadro() throws IOException {
        try (InputStream in = FxJpegTest.class.getResourceAsStream("/fx/quadro_c.jpg")) {
            return in.readAllBytes();
        }
    }

    @Test
    void leJpegInteiro() throws Exception {
        assertEquals(1280, FxJpeg.ler(new ByteArrayInputStream(quadro())).getWidth());
    }

    @Test
    void jpegCortadoEhErroNaoMeioQuadro() throws Exception {
        byte[] cortado = Arrays.copyOf(quadro(), 2000);
        assertThrows(IOException.class, () -> FxJpeg.ler(new ByteArrayInputStream(cortado)));
    }

    @Test
    void lixoEhErro() {
        assertThrows(IOException.class, () -> FxJpeg.ler(new ByteArrayInputStream(new byte[]{1, 2, 3})));
    }

    @Test
    void naoUsaArquivoTemporarioNoDisco(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        // pasta de cache do ImageIO que NAO existe: quem usar cache em disco falha
        java.io.File sumiu = dir.resolve("cache").toFile();
        assertTrue(sumiu.mkdir());
        javax.imageio.ImageIO.setCacheDirectory(sumiu);
        assertTrue(sumiu.delete());
        try {
            assertEquals(1280, FxJpeg.ler(new ByteArrayInputStream(quadro())).getWidth());
        } finally {
            javax.imageio.ImageIO.setCacheDirectory(null);
        }
    }
}
