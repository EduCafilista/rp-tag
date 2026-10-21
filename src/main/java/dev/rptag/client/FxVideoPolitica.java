package dev.rptag.client;

/**
 * (4.5.0) Quando um aviso pode usar o VIDEO: so com fxVideo ligado e fora do
 * modo reduzido (o video nao tem variante sem clarao/particulas; o reduzido
 * fica no FX procedural, que ja respeita essa opcao). Java puro (testavel).
 */
public final class FxVideoPolitica {
    private FxVideoPolitica() {}

    public static boolean permitir(boolean habilitado, boolean reduzido) {
        return habilitado && !reduzido;
    }
}
