package dev.rptag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** (4.5.0) o clamp do tempo de AFK — puro Java. */
class AfkTimeoutTest {

    @Test
    void zeroDesliga() {
        assertEquals(0, AfkSistema.normalizarTimeout(0));
        assertEquals(0, AfkSistema.normalizarTimeout(-30));
    }

    @Test
    void respeitaOFaixaDoComando() {
        assertEquals(120, AfkSistema.normalizarTimeout(120));
        assertEquals(86400, AfkSistema.normalizarTimeout(999_999));
    }

    @Test
    void padraoECincoMinutos() {
        assertEquals(300, AfkSistema.TIMEOUT_PADRAO_S);
        assertEquals(AfkSistema.TIMEOUT_PADRAO_S, AfkSistema.normalizarTimeout(300));
    }
}
