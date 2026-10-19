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
}
