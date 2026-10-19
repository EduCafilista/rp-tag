package dev.rptag.client;

/**
 * (4.2.0) TEMPOS do aviso MORTE "Linha Reta" — fonte UNICA: o FX procedural
 * (MorteAvisoFx), a cena do Blender (arte/cenas/morte.py) e o teste dos
 * marcadores do fx.json usam estes numeros. Java puro (sem Minecraft).
 */
public final class MorteTempos {
    public static final float DUR = 6.0f, FLAT = 3.9f, ECG_END = 4.6f;
    public static final float[] BEATS = {0.6f, 1.5f, 2.5f, 3.7f};
    public static final float TEXTO_ENTRA = 0.5f, CINZAS = DUR - 1.6f, TEXTO_SAI = DUR - 1.0f;

    private MorteTempos() {}
}
