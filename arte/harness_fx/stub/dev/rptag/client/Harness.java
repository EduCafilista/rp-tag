package dev.rptag.client;

import java.io.*;
import java.lang.reflect.*;
import net.minecraft.client.gui.GuiGraphics;

/** Roda o RealAvisoFx de verdade num framebuffer e despeja PPM + o texto. */
public class Harness {
    public static void main(String[] a) throws Exception {
        int W = Integer.parseInt(a[0]), H = Integer.parseInt(a[1]);
        String titulo = a[2], msg = a[3];
        float[] tempos = new float[a.length - 4];
        for (int i = 4; i < a.length; i++) tempos[i - 4] = Float.parseFloat(a[i]);

        GuiGraphics.W = W; GuiGraphics.H = H;
        Field fini = RealAvisoFx.class.getDeclaredField("inicio");
        fini.setAccessible(true);
        Method render = RealAvisoFx.class.getDeclaredMethod("render", GuiGraphics.class, net.minecraft.client.DeltaTracker.class);
        render.setAccessible(true);

        for (float t : tempos) {
            AvisoFxUtil.novoFrame(W, H);
            for (int i = 0; i < AvisoFxUtil.FB.length; i++) AvisoFxUtil.FB[i] = 0f;
            GuiGraphics.TEXTOS.clear();
            RealAvisoFx.iniciar(titulo, msg);
            fini.setLong(null, System.nanoTime() - (long) (t * 1e9));
            render.invoke(null, new GuiGraphics(), new net.minecraft.client.DeltaTracker());

            String base = String.format("/tmp/fx/frame_%.1f", t);
            try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(base + ".bin")))) {
                o.writeInt(W); o.writeInt(H);
                for (float v : AvisoFxUtil.FB) o.writeFloat(Math.min(1f, Math.max(0f, v)));
                o.writeInt(GuiGraphics.TEXTOS.size());
                for (GuiGraphics.Texto tx : GuiGraphics.TEXTOS) {
                    byte[] b = tx.s.getBytes("UTF-8");
                    o.writeInt(b.length); o.write(b);
                    o.writeFloat(tx.x); o.writeFloat(tx.y); o.writeFloat(tx.esc); o.writeInt(tx.cor); o.writeBoolean(tx.sombra);
                }
            }
            System.out.println("frame " + t + " -> " + base + ".bin  textos=" + GuiGraphics.TEXTOS.size());
        }
    }
}
