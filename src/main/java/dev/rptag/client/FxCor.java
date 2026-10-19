package dev.rptag.client;

/** (4.2.0) Junta a cor (JPEG _c) e o alfa (JPEG _a, cinza) no formato do NativeImage (ABGR). */
public final class FxCor {
    private FxCor() {}

    /** rgb = 0xRRGGBB (bits altos ignorados), alfa 0..255 -> 0xAABBGGRR. */
    public static int abgr(int rgb, int alfa) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return ((alfa & 0xFF) << 24) | (b << 16) | (g << 8) | r;
    }
}
