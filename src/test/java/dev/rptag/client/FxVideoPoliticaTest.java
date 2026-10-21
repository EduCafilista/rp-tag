package dev.rptag.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FxVideoPoliticaTest {

    @Test
    void videoSoComConfigLigadaEModoNormal() {
        assertTrue(FxVideoPolitica.permitir(true, false));
        assertFalse(FxVideoPolitica.permitir(true, true));   // modo reduzido mantem o procedural
        assertFalse(FxVideoPolitica.permitir(false, false));
        assertFalse(FxVideoPolitica.permitir(false, true));
    }
}
