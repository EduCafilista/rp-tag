package dev.rptag.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FxCorTest {

    @Test
    void montaAbgr() {
        assertEquals(0x80332211, FxCor.abgr(0x112233, 0x80));
        assertEquals(0x00332211, FxCor.abgr(0xFF112233, 0));
        assertEquals(0xFFFFFFFF, FxCor.abgr(0xFFFFFF, 255));
    }

    @Test
    void montaDoRasterBgrECinza() {
        // 2 pixels: BGR (3 bytes cada) do JPEG de cor + 1 byte de cinza do JPEG de alfa
        byte[] bgr = {0x33, 0x22, 0x11, (byte) 0xFF, 0x00, (byte) 0x80};
        byte[] cinza = {(byte) 0x80, 0x00};
        int[] saida = new int[2];
        FxCor.abgrDeRaster(bgr, cinza, saida);
        assertEquals(0x80332211, saida[0]);
        assertEquals(0x00FF0080, saida[1]);
    }
}
