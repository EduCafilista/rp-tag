package dev.rptag.client;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Os fx.json do Inferno e do Gelo (exportados do Blender) batem com os tempos dos FX Java. */
class ElementaisMarcadoresTest {

    private static FxTimeline carregar(String id) throws Exception {
        try (InputStream in = ElementaisMarcadoresTest.class.getResourceAsStream("/assets/rptag/fx/" + id + "/fx.json")) {
            assertNotNull(in, "fx.json do " + id + " nao esta no classpath");
            return FxTimeline.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static int q(double seg) { return (int) Math.round(seg * 24); }

    @Test
    void infernoSeisSegundosComCrepitarDoJava() throws Exception {
        FxTimeline tl = carregar("inferno");
        assertEquals(24, tl.fps());
        assertEquals(144, tl.frames());
        assertFalse(tl.aditivo());
        assertEquals(List.of(0), tl.marcadores("impacto"));
        List<Integer> crepitar = new ArrayList<>();
        for (int i = 0; 0.5 + 0.45 * i < 6.0 - 0.8; i++) crepitar.add(q(0.5 + 0.45 * i)); // InfernoAvisoFx.sons
        assertEquals(crepitar, tl.marcadores("crepitar"));
    }

    @Test
    void geloSeisSegundosComSinosDoJava() throws Exception {
        FxTimeline tl = carregar("gelo");
        assertEquals(24, tl.fps());
        assertEquals(144, tl.frames());
        assertFalse(tl.aditivo());
        assertEquals(List.of(q(0.12)), tl.marcadores("impacto"));
        assertEquals(List.of(q(0.5), q(1.2), q(2.0), q(3.0)), tl.marcadores("sino")); // GeloAvisoFx.CH
    }
}
