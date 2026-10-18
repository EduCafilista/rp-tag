package dev.rptag.client;
import java.util.*;
public class AreaTest {
    public static void main(String[] a) {
        Random rnd = new Random(42);
        int bad = 0, n = 0;
        for (int it = 0; it < 4000; it++) {
            BubbleGeometry.Spec s = new BubbleGeometry.Spec();
            s.w = 20 + rnd.nextFloat() * 280;
            s.h = 12 + rnd.nextFloat() * 130;
            s.x0 = -s.w / 2; s.y0 = -s.h - 4;
            s.radius = rnd.nextFloat() < 0.3 ? 4 : Math.min(s.h / 2, 10);
            s.tail = rnd.nextFloat() < 0.9;
            s.tailCx = (rnd.nextFloat() - 0.5F) * s.w;
            BubbleGeometry.Poly p = BubbleGeometry.silhouette(s);
            int[] t = BubbleGeometry.triangulate(p);
            double sum = 0;
            for (int i = 0; i + 2 < t.length; i += 3) {
                double ax = p.x[t[i]], ay = p.y[t[i]], bx = p.x[t[i+1]], by = p.y[t[i+1]], cx = p.x[t[i+2]], cy = p.y[t[i+2]];
                sum += Math.abs((bx-ax)*(cy-ay) - (cx-ax)*(by-ay)) / 2;
            }
            double poly = Math.abs(p.signedArea());
            n++;
            if (Math.abs(sum - poly) > poly * 1e-3 || t.length != (p.n - 2) * 3) {
                bad++;
                if (bad < 6) System.out.println("FALHA w=" + s.w + " h=" + s.h + " r=" + s.radius + " tail=" + s.tail + " cx=" + s.tailCx + " areaTri=" + sum + " areaPoly=" + poly + " tris=" + t.length/3 + " esperado=" + (p.n-2));
            }
        }
        System.out.println("casos=" + n + " falhas=" + bad);
    }
}
