package dev.rptag.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FxConfigTest {

    @Test
    void ausenteCriaPadrao(@TempDir Path dir) throws Exception {
        Path arq = dir.resolve("rptag").resolve("cliente.properties");
        FxConfig.carregar(arq);
        assertTrue(FxConfig.video());
        assertTrue(Files.exists(arq));
        assertTrue(Files.readString(arq).contains("fxVideo=true"));
    }

    @Test
    void lixoViraPadrao(@TempDir Path dir) throws Exception {
        Path arq = dir.resolve("cliente.properties");
        Files.write(arq, new byte[]{0, (byte) 0xFF, 'x', 'x', '='});
        assertDoesNotThrow(() -> FxConfig.carregar(arq));
        assertTrue(FxConfig.video());
    }

    @Test
    void offPersiste(@TempDir Path dir) {
        Path arq = dir.resolve("cliente.properties");
        FxConfig.carregar(arq);
        FxConfig.definirVideo(false);
        FxConfig.carregar(arq);
        assertFalse(FxConfig.video());
        FxConfig.definirVideo(true);
        FxConfig.carregar(arq);
        assertTrue(FxConfig.video());
    }
}
