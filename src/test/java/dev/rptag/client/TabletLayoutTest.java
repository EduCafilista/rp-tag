package dev.rptag.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletLayoutTest {
    @Test void molduraMantemMargensEControlesCabemNasEscalasDoJogo() {
        for (int[] size : new int[][]{{640,360},{512,267},{426,240},{320,180}}) {
            TabletLayout layout = TabletLayout.of(size[0],size[1]);
            assertTrue(layout.x() >= 10);
            assertTrue(layout.y() >= 10);
            assertTrue(layout.formWidth() >= 150);
            assertTrue(layout.x()+layout.width() <= size[0]-10);
            assertTrue(layout.y()+layout.height() <= size[1]-10);
            assertTrue(layout.blackHoleRadius()*2 < layout.height()/2);
        }
    }
}
