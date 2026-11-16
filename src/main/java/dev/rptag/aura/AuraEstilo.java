package dev.rptag.aura;

/**
 * (5.60.0) A PALETA DA AURA. Uma so: ILUMINACAO (dourado do Rei dos Herois).
 *
 * <p>{@code r,g,b} = cor principal; {@code r2,g2,b2} = brilho interno (o miolo claro).
 */
public enum AuraEstilo {
    ILUMINACAO(1.00f, 0.80f, 0.15f, 1.00f, 0.96f, 0.70f);

    public final float r, g, b, r2, g2, b2;

    AuraEstilo(float r, float g, float b, float r2, float g2, float b2) {
        this.r = r; this.g = g; this.b = b;
        this.r2 = r2; this.g2 = g2; this.b2 = b2;
    }

    /** Valor guardado no jogador: 0 = desligada, 1 = ligada. */
    public int index() {
        return ordinal() + 1;
    }

    public static final int LIGADA = 1;
}
