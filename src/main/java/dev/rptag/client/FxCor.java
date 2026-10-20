package dev.rptag.client;

/** (4.2.0) Junta a cor (JPEG _c) e o alfa (JPEG _a, cinza) no formato do NativeImage (ABGR). */
public final class FxCor {
    private FxCor() {}

    /** rgb = 0xRRGGBB (bits altos ignorados), alfa 0..255 -> 0xAABBGGRR. */
    public static int abgr(int rgb, int alfa) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return ((alfa & 0xFF) << 24) | (b << 16) | (g << 8) | r;
    }

    /**
     * (4.2.0) Versao RAPIDA direto do raster (sem getRGB, que custava ~35 ms/quadro):
     * bgr = bytes do JPEG de cor (TYPE_3BYTE_BGR), cinza = bytes do JPEG de alfa
     * (TYPE_BYTE_GRAY); saida[i] = ABGR do pixel i.
     */
    public static void abgrDeRaster(byte[] bgr, byte[] cinza, int[] saida) {
        for (int i = 0, j = 0; i < saida.length; i++, j += 3) {
            saida[i] = ((cinza[i] & 0xFF) << 24) | ((bgr[j] & 0xFF) << 16) | ((bgr[j + 1] & 0xFF) << 8) | (bgr[j + 2] & 0xFF);
        }
    }
}
