package dev.rptag.aura;

/**
 * (5.60.0) A PALETA DA AURA. Uma so: ILUMINACAO (dourado do Rei dos Herois).
 *
 * <p>{@code r,g,b} = cor principal; {@code r2,g2,b2} = brilho interno (o miolo claro).
 */
public enum AuraEstilo {
    /** Dourado do Rei dos Herois (a aura do /iluminacao). */
    ILUMINACAO(1.00f, 0.80f, 0.15f, 1.00f, 0.96f, 0.70f),
    /** (5.64.0) A AURA DO VAZIO: tinta preta com brilho arroxeado. */
    VAZIO(0.02f, 0.02f, 0.05f, 0.35f, 0.30f, 0.45f),
    /** (5.67.0) A AURA DE RADIACAO (/radiacao): azul-ciano, chamas espectrais. */
    RADIACAO(0.15f, 0.85f, 1.00f, 0.85f, 1.00f, 1.00f),
    /** (5.69.0) A AURA ARCO-IRIS (/arcoiris): estrela de luz no peito, cada raio numa cor. */
    ARCOIRIS(1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f),
    /** (5.73.0) A AURA DE GRAVIDADE (/gravidade): contorno branco-azulado, bolhas e fissuras. */
    GRAVIDADE(0.75f, 0.90f, 1.00f, 1.00f, 1.00f, 1.00f),
    /**
     * (5.78.6) O REI DE AMARELO — Carcosa (/rei): amarelo do Rei, com o miolo dourado.
     * O desenho e do pacote do Rei (client/KingAuraFx): aneis de 15/8/4 blocos, letras e sinal amarelo.
     */
    REI(0.94f, 0.78f, 0.10f, 1.00f, 0.95f, 0.45f);

    public final float r, g, b, r2, g2, b2;

    AuraEstilo(float r, float g, float b, float r2, float g2, float b2) {
        this.r = r; this.g = g; this.b = b;
        this.r2 = r2; this.g2 = g2; this.b2 = b2;
    }

    /** (5.67.0) estilo desenhado como casca em volta do corpo? */
    public boolean ehCasca() {
        return this == ILUMINACAO || this == VAZIO || this == RADIACAO || this == ARCOIRIS || this == GRAVIDADE
                || this == REI;
    }

    /** Valor guardado no jogador: 0 = desligada, 1 = iluminacao, 2 = vazio, 3 = radiacao, 4 = arcoiris, 5 = gravidade. */
    public int index() {
        return ordinal() + 1;
    }

    public static final int LIGADA = 1;

    /** (5.64.0) volta o estilo pelo valor guardado no jogador (0 = desligada, devolve null). */
    public static AuraEstilo byIndex(int id) {
        for (AuraEstilo e : values()) if (e.index() == id) return e;
        return null;
    }
}
