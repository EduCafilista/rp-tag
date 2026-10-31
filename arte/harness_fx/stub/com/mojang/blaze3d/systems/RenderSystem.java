package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.platform.GlStateManager;
import java.util.function.Supplier;

/** STUB: sem GL. Só anota o modo de blend pro rasterizador. */
public final class RenderSystem {
    public static boolean aditivo = false;
    public static void defaultBlendFunc() { aditivo = false; }
    public static void blendFunc(GlStateManager.SourceFactor s, GlStateManager.DestFactor d) { aditivo = (d == GlStateManager.DestFactor.ONE); }
    public static void disableDepthTest() { }
    public static void enableDepthTest() { }
    public static void disableCull() { }
    public static void enableBlend() { }
    public static void setShader(Supplier<?> s) { }
}
