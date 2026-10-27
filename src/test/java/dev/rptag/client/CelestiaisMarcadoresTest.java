package dev.rptag.client;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** O fx.json do Void (exportado do Blender) bate com os tempos do FX Java.
 *  (4.9.1) O video do DIVINO saiu — o aviso voltou a ser o procedural antigo. */
class CelestiaisMarcadoresTest {

    private static FxTimeline carregar(String id) throws Exception {
        try (InputStream in = CelestiaisMarcadoresTest.class.getResourceAsStream("/assets/rptag/fx/" + id + "/fx.json")) {
            assertNotNull(in, "fx.json do " + id + " nao esta no classpath");
            return FxTimeline.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static int q(double seg) { return (int) Math.round(seg * 24); }

    @Test
    void voidSeisSegundosComRessoarDoJava() throws Exception {
        FxTimeline tl = carregar("void");
        assertEquals(24, tl.fps());
        assertEquals(144, tl.frames());
        assertFalse(tl.aditivo());
        assertEquals(List.of(0), tl.marcadores("impacto"));
        assertEquals(List.of(q(1.5), q(2.7), q(3.9)), tl.marcadores("ressoar")); // VoidAvisoFx.RES
        assertEquals(List.of(q(6.0 - 0.9)), tl.marcadores("fim"));
    }
}
