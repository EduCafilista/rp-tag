package net.minecraft.client;

import net.minecraft.client.gui.Font;

public class Minecraft {
    private static final Minecraft INST = new Minecraft();
    public final Font font = new Font();
    public static Minecraft getInstance() { return INST; }
    public static Minecraft m_91087_() { return INST; }
}
