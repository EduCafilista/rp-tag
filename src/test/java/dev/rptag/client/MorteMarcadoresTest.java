package dev.rptag.client;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** O fx.json do MORTE (exportado do Blender) tem que bater com MorteTempos. */
class MorteMarcadoresTest {

    private static FxTimeline carregar() throws Exception {
        try (InputStream in = MorteMarcadoresTest.class.getResourceAsStream("/assets/rptag/fx/morte/fx.json")) {
            assertNotNull(in, "fx.json do morte nao esta no classpath");
            return FxTimeline.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static int q(float seg) { return Math.round(seg * 24); }

    @Test
    void videoTem6SegundosA24Fps() throws Exception {
        FxTimeline tl = carregar();
        assertEquals(24, tl.fps());
        assertEquals(144, tl.frames());
        assertFalse(tl.aditivo());
    }

    @Test
    void marcadoresBatemComMorteTempos() throws Exception {
        FxTimeline tl = carregar();
        List<Integer> batidas = new ArrayList<>();
        for (float b : MorteTempos.BEATS) batidas.add(q(b));
        assertEquals(List.of(14, 36, 60, 89), batidas);
        assertEquals(batidas, tl.marcadores("batida"));
        assertEquals(List.of(q(MorteTempos.FLAT)), tl.marcadores("linha_reta"));
        assertEquals(List.of(94), tl.marcadores("linha_reta"));
        assertEquals(List.of(12), tl.marcadores("texto_entra"));
        assertEquals(List.of(106), tl.marcadores("cinzas"));
        assertEquals(List.of(120), tl.marcadores("texto_sai"));
    }
}
