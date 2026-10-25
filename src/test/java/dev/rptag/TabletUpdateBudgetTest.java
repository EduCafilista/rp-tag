package dev.rptag;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletUpdateBudgetTest {
    @Test void aceitaSalvamentoEConfirmacaoNoMesmoTickMasLimitaFlood() {
        TabletUpdateBudget budget = new TabletUpdateBudget();
        assertTrue(budget.accept(100));
        assertTrue(budget.accept(100));
        for (int i=2;i<20;i++) assertTrue(budget.accept(100));
        assertFalse(budget.accept(100));
        assertFalse(budget.accept(119));
        assertTrue(budget.accept(120));
    }
}
