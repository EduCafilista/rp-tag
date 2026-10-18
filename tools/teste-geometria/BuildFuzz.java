package dev.rptag.client;
import java.util.*;
public class BuildFuzz {
    public static void main(String[] a) {
        Random rnd = new Random(7);
        long tris[] = {0};
        int err = 0;
        long t0 = System.nanoTime();
        int N = 6000;
        for (int it = 0; it < N; it++) {
            BubbleGeometry.Look k = new BubbleGeometry.Look();
            k.style = rnd.nextInt(5);
            k.fill = (rnd.nextInt(256) << 24) | rnd.nextInt(0xFFFFFF);
            k.border = rnd.nextFloat() < 0.2 ? 0 : (0xFF000000 | rnd.nextInt(0xFFFFFF));
            k.baseRgb = rnd.nextInt(0xFFFFFF);
            k.alpha = rnd.nextFloat();
            k.w = 20 + rnd.nextFloat() * 300; k.h = 12 + rnd.nextFloat() * 140;
            k.x0 = -k.w / 2; k.y0 = -k.h - 4; k.tailCx = (rnd.nextFloat() - 0.5F) * k.w;
            try {
                BubbleGeometry.build((z,x1,y1,c1,x2,y2,c2,x3,y3,c3) -> {
                    tris[0]++;
                    if (Float.isNaN(x1+y1+x2+y2+x3+y3) || Float.isInfinite(x1+y1+x2+y2+x3+y3)) throw new IllegalStateException("NaN");
                }, k);
            } catch (Throwable t) { err++; if (err < 4) t.printStackTrace(); }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.println("builds=" + N + " erros=" + err + " tris/balao=" + (tris[0] / N) + " tempo/balao=" + String.format("%.3f", ms / N) + "ms");
    }
}
