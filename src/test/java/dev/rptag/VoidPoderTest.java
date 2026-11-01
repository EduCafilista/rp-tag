package dev.rptag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rptag.client.VoidFx;
import org.junit.jupiter.api.Test;

class VoidPoderTest {
    @Test void avisoDoChatTemOFormatoPedido() {
        assertEquals("---- VAZIO ---- apagou Fulano da existência", VoidPoder.textoChat("Fulano", false));
        assertTrue(VoidPoder.textoChat("Fulano", true).contains("Fulano"));
    }

    @Test void escurecerSobeAtePretoEVoltaDepoisDeMeioSegundo() {
        assertEquals(0F, VoidFx.progresso(1000, 1000, 3000));
        assertEquals(.5F, VoidFx.progresso(2000, 1000, 3000), 1e-4);
        assertEquals(1F, VoidFx.progresso(3000, 1000, 3000), 1e-4);
        assertEquals(.5F, VoidFx.progresso(3750, 1000, 3000), 1e-4);
        assertEquals(0F, VoidFx.progresso(5000, 1000, 3000));
        assertEquals(0F, VoidFx.progresso(500, 1000, 3000));
    }
}
