package net.minecraft.client.gui;
/** STUB: métricas aproximadas da fonte padrão do Minecraft (6 px por glifo). */
public class Font {
    public int width(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) w += (s.charAt(i) == ' ') ? 4 : 6;
        return w;
    }
}
