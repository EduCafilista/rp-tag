package dev.rptag.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class TerremotoFxTest {
    @Test void pulsoComecaEAcabaEmZeroEPicaNoMeio() {
        assertEquals(0F, TerremotoFx.intensidadePulso(1000, 1000, 3000), 1e-4);
        assertEquals(1F, TerremotoFx.intensidadePulso(2000, 1000, 3000), 1e-4);
        assertEquals(0F, TerremotoFx.intensidadePulso(3000, 1000, 3000), 1e-4);
    }

    @Test void pulsoForaDoIntervaloEZero() {
        assertEquals(0F, TerremotoFx.intensidadePulso(500, 1000, 3000));
        assertEquals(0F, TerremotoFx.intensidadePulso(9000, 1000, 3000));
        assertEquals(0F, TerremotoFx.intensidadePulso(2000, 3000, 3000));
    }

    @Test void progressoDeLevarVaiDeZeroAUmEFicaNoUm() {
        assertEquals(0F, TerremotoFx.progressoLevar(1000, 1000, 4000));
        assertEquals(.5F, TerremotoFx.progressoLevar(2500, 1000, 4000), 1e-4);
        assertEquals(1F, TerremotoFx.progressoLevar(9999, 1000, 4000));
        assertEquals(0F, TerremotoFx.progressoLevar(500, 1000, 4000));
    }

    @Test void sorteiosRespeitamAsFaixas() {
        RandomSource rng = RandomSource.create(42);
        for (int i = 0; i < 2000; i++) {
            int intervalo = dev.rptag.Terremoto.sorteioIntervalo(rng);
            int duracao = dev.rptag.Terremoto.sorteioDuracao(rng);
            assertTrue(intervalo >= 160 && intervalo <= 400, "intervalo " + intervalo);
            assertTrue(duracao >= 40 && duracao <= 80, "duracao " + duracao);
        }
    }
}
