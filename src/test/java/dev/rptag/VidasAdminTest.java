package dev.rptag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** (4.5.0) lista do lote de vidas — puro Java, sem servidor. */
class VidasAdminTest {

    @Test
    void dividePorVirgula() {
        assertEquals(List.of("Ana", "Beto", "Caio"), VidasAdmin.dividirNomes("Ana,Beto,Caio"));
    }

    @Test
    void tiraEspacoEVazio() {
        assertEquals(List.of("Ana", "Beto"), VidasAdmin.dividirNomes("  Ana , ,Beto,  "));
        assertTrue(VidasAdmin.dividirNomes("").isEmpty());
        assertTrue(VidasAdmin.dividirNomes(null).isEmpty());
        assertTrue(VidasAdmin.dividirNomes(",,").isEmpty());
    }

    @Test
    void nomeRepetidoContaUmaVez() {
        assertEquals(List.of("Ana", "Beto"), VidasAdmin.dividirNomes("Ana,Beto,Ana"));
    }

    @Test
    void mantemAOrdemDigitada() {
        assertEquals(List.of("Zoe", "Ana", "Bob"), VidasAdmin.dividirNomes("Zoe,Ana,Bob"));
    }
}
