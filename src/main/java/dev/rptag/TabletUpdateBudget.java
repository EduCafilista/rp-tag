package dev.rptag;

/** Permite uma sequência curta de salvar/confirmar sem abrir espaço para flood. */
final class TabletUpdateBudget {
    private int start = Integer.MIN_VALUE;
    private int used;

    boolean accept(int tick) {
        if (start == Integer.MIN_VALUE || tick < start || tick - start >= 20) {
            start = tick;
            used = 0;
        }
        if (used >= 20) return false;
        used++;
        return true;
    }
}
