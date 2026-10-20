package dev.rptag.client;

/**
 * (4.2.0) Estado do player de video a cada frame + a regra que decide.
 * Java puro (testavel sem Minecraft); quem usa e o VideoFx.
 */
public enum VideoEstado {
    CARREGANDO, OK, FALHOU, FIM;

    /** Quadros prontos antes de comecar a mostrar. */
    public static final int MIN_PRONTOS = 4;
    /** Tempo maximo sem o quadro necessario (ou carregando) antes do fallback. */
    public static final float MAX_SEM_QUADRO = 0.5f;

    /**
     * @param segSemQuadro segundos esperando o quadro necessario (antes de
     *                     comecar: segundos desde o abrir)
     */
    public static VideoEstado decidir(int quadro, int frames, boolean temQuadro, boolean erroLoader,
                                      float segSemQuadro, int prontos, boolean comecou) {
        if (quadro >= frames) return FIM;
        if (erroLoader) return FALHOU;
        if (!comecou && prontos < MIN_PRONTOS) return segSemQuadro > MAX_SEM_QUADRO ? FALHOU : CARREGANDO;
        if (!temQuadro && segSemQuadro > MAX_SEM_QUADRO) return FALHOU;
        return OK;
    }

    /** Quadro de um loader antigo (aviso ja trocado) e descartado. */
    public static boolean aceitaGeracao(int quadroGeracao, int atual) {
        return quadroGeracao == atual;
    }
}
