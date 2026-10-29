package dev.rptag.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletLayoutTest {
    @Test void ocupaATelaCompletaEControlesCabemNasEscalasDoJogo() {
        for (int[] size : new int[][]{{640,360},{512,267},{426,240},{320,180}}) {
            TabletLayout layout = TabletLayout.of(size[0],size[1]);
            assertEquals(0,layout.x());
            assertEquals(0,layout.y());
            assertTrue(layout.formWidth() >= 150);
            assertEquals(size[0],layout.width());
            assertEquals(size[1],layout.height());
            assertTrue(layout.blackHoleRadius()*2 < layout.height()/2);
        }
    }
}
