package dev.rptag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** (4.4.0) a conta do /rp admin vida — sem servidor, puro Java. */
class VidaCanonicaTest {

    @Test
    void tirarDesceSemPassarDeZero() {
        assertEquals(4, VidaCanonica.tirar(5, 1));
        assertEquals(2, VidaCanonica.tirar(5, 3));
        assertEquals(0, VidaCanonica.tirar(2, 5));   // pediu 5, tinha 2 -> 0
        assertEquals(0, VidaCanonica.tirar(0, 1));   // já sem vidas: continua 0
        assertEquals(4, VidaCanonica.tirar(5, 0));   // "0" conta como 1
        assertEquals(4, VidaCanonica.tirar(5, -3));  // negativo tambem conta como 1
    }

    @Test
    void darSobeRespeitandoOCinco() {
        assertEquals(4, VidaCanonica.dar(3, 1));
        assertEquals(5, VidaCanonica.dar(3, 2));
        assertEquals(5, VidaCanonica.dar(5, 1));     // cheio: nao passa
        assertEquals(1, VidaCanonica.dar(0, 1));     // sem vidas: volta com uma
        assertEquals(5, VidaCanonica.dar(4, 0));     // "0" conta como 1
    }

    @Test
    void definirPrendeEntreZeroECinco() {
        assertEquals(0, VidaCanonica.definir(-2));
        assertEquals(3, VidaCanonica.definir(3));
        assertEquals(5, VidaCanonica.definir(9));
    }

    @Test
    void status() {
        assertEquals("0/5", VidaCanonica.mostrar(0));
        assertEquals("5/5", VidaCanonica.mostrar(VidaCanonica.TETO));
    }
}
