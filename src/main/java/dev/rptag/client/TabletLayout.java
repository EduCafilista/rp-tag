package dev.rptag.client;

/**
 * Tela completa, com respiro dentro da composição em vez de uma moldura externa.
 *
 * <p>(5.2.0) A conta nunca pode produzir largura/altura negativa: a escala da GUI
 * e o tamanho da janela mudam bastante entre jogadores (320x240 até 4K). Abaixo de
 * {@link #MIN_CHARACTER_WIDTH} a coluna do personagem some e o formulário usa a
 * tela inteira, mantendo o campo de texto utilizável.
 */
public record TabletLayout(int x, int y, int width, int height, int formX, int formWidth,
        int blackHoleRadius, boolean showCharacter) {
    /** Largura mínima (em px de GUI) para a coluna do personagem caber sem espremer o formulário. */
    public static final int MIN_CHARACTER_WIDTH = 430;

    /** topo da barra de botões (Voltar/Continuar). */
    public int bottomBar() { return y + height - 39; }

    /** topo da área do campo de resposta, já protegido contra telas baixas. */
    public int fieldTop(int titleHeight) { return Math.min(y + 50 + titleHeight, bottomBar() - 36); }

    /** altura útil da área do campo de resposta (deixa 26 px para a linha de status). */
    public int fieldHeight(int titleHeight) { return Math.max(20, bottomBar() - 26 - fieldTop(titleHeight)); }

    public static TabletLayout of(int screenWidth, int screenHeight) {
        int w = Math.max(200, screenWidth);
        int h = Math.max(140, screenHeight);
        int right = Math.max(12, w / 24);
        boolean character = w >= MIN_CHARACTER_WIDTH;
        int formX;
        int formWidth;
        if (character) {
            formX = Math.max(126, Math.min(w * 54 / 100, w - right - 168));
            formWidth = w - right - formX;
            if (formWidth < 168) { // coluna do personagem cede espaço para o formulário
                formX = Math.max(96, w - right - 168);
                formWidth = w - right - formX;
            }
        } else {
            formX = Math.max(8, w / 24);
            formWidth = Math.max(96, w - right - formX);
        }
        int radius = character ? Math.max(9, Math.min(formWidth * 21 / 100, (h - 60) / 7)) : 0;
        return new TabletLayout(0, 0, w, h, formX, formWidth, radius, character);
    }
}
