package dev.rptag.client;

import org.junit.jupiter.api.Test;

import static dev.rptag.client.VideoEstado.*;
import static org.junit.jupiter.api.Assertions.*;

class VideoFxEstadoTest {

    @Test
    void fimGanhaDeTudo() {
        assertEquals(FIM, VideoEstado.decidir(144, 144, false, true, 9f, 0, true));
    }

    @Test
    void carregaAteQuatro() {
        assertEquals(CARREGANDO, VideoEstado.decidir(0, 144, false, false, 0f, 3, false));
        assertEquals(OK, VideoEstado.decidir(0, 144, true, false, 0f, 4, false));
    }

    @Test
    void semQuadroMeioSegundoFalha() {
        assertEquals(FALHOU, VideoEstado.decidir(30, 144, false, false, 0.51f, 8, true));
        assertEquals(OK, VideoEstado.decidir(30, 144, false, false, 0.49f, 8, true));
    }

    @Test
    void erroDoLoaderFalha() {
        assertEquals(FALHOU, VideoEstado.decidir(10, 144, true, true, 0f, 8, true));
    }

    @Test
    void reabrirDescartaGeracaoAntiga() {
        assertFalse(VideoEstado.aceitaGeracao(1, 2));
        assertTrue(VideoEstado.aceitaGeracao(2, 2));
    }
}
