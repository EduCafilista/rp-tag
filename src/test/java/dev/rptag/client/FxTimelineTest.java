package dev.rptag.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FxTimelineTest {

    private static String json(int fps, int frames, int w, String blend, int marcador) {
        return "{\"id\":\"morte\",\"fps\":" + fps + ",\"frames\":" + frames + ",\"width\":" + w
                + ",\"height\":720,\"blend\":\"" + blend + "\",\"markers\":["
                + "{\"name\":\"batida\",\"frame\":14},{\"name\":\"batida\",\"frame\":" + marcador + "}]}";
    }

    @Test
    void parseValido() {
        FxTimeline tl = FxTimeline.parse(json(24, 144, 1280, "normal", 36));
        assertEquals(24, tl.fps());
        assertEquals(144, tl.frames());
        assertEquals(1280, tl.width());
        assertEquals(720, tl.height());
        assertEquals(6.0f, tl.duracao(), 1e-6);
        assertFalse(tl.aditivo());
        assertEquals(List.of(14, 36), tl.marcadores("batida"));
        assertTrue(tl.marcadores("x").isEmpty());
        assertTrue(FxTimeline.parse(json(24, 144, 1280, "aditivo", 36)).aditivo());
    }

    @Test
    void parseRejeita() {
        assertThrows(IllegalArgumentException.class, () -> FxTimeline.parse(json(0, 144, 1280, "normal", 36)));
        assertThrows(IllegalArgumentException.class, () -> FxTimeline.parse(json(24, 0, 1280, "normal", 0)));
        assertThrows(IllegalArgumentException.class, () -> FxTimeline.parse(json(24, 144, 1920, "normal", 36)));
        assertThrows(IllegalArgumentException.class, () -> FxTimeline.parse(json(24, 144, 1280, "xor", 36)));
        assertThrows(IllegalArgumentException.class, () -> FxTimeline.parse(json(24, 144, 1280, "normal", 144)));
        assertThrows(IllegalArgumentException.class, () -> FxTimeline.parse("nao e json"));
    }

    @Test
    void quadroPeloRelogioPula() {
        FxTimeline tl = FxTimeline.parse(json(24, 144, 1280, "normal", 36));
        assertEquals(-1, tl.quadroEm(-0.01f));
        assertEquals(0, tl.quadroEm(0f));
        assertEquals(24, tl.quadroEm(1.0f));
        assertEquals(35, tl.quadroEm(1.49f));
        assertEquals(144, tl.quadroEm(6.0f));
        assertEquals(144, tl.quadroEm(60f));
    }
}
