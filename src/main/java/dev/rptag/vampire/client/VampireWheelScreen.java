package dev.rptag.vampire.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.rptag.vampire.VampireActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/** Roda do vampiro: 1 Morder, 2 Forma de Morcego, 3 Visão Vampírica. Clique ou teclas 1–3. */
public final class VampireWheelScreen extends Screen {
    private static final float R0 = 44f, R1 = 112f;
    private static final int[][] COL = {{200, 16, 30}, {120, 70, 170}, {200, 150, 40}};
    private int hover = -1;

    public VampireWheelScreen() { super(Component.translatable("screen.vampire.wheel")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // Evita o blur pesado de menu padrão do 1.21 por cima da roda rápida
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x99100003);
        int cx = width / 2, cy = height / 2;
        double dx = mx - cx, dy = my - cy, dist = Math.sqrt(dx * dx + dy * dy);
        double ang = ((Math.toDegrees(Math.atan2(dy, dx)) + 90) % 360 + 360) % 360;
        hover = (dist >= R0 && dist <= R1 + 8) ? (int) (((ang + 60) % 360) / 120) % 3 : -1;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (int i = 0; i < 3; i++) {
            boolean h = i == hover;
            float k = h ? 1f : 0.65f;
            int a = h ? 0xE6 : 0x99;
            sector(g, cx, cy, R0, h ? R1 + 8 : R1, i * 120 - 60, i * 120 + 60,
                    (a << 24) | ((int) (COL[i][0] * k) << 16) | ((int) (COL[i][1] * k) << 8) | (int) (COL[i][2] * k));
            sector(g, cx, cy, R0 - 3, R0, i * 120 - 60, i * 120 + 60, 0xFF000000);
        }
        RenderSystem.disableBlend();

        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(i * 120 - 90);
            int tx = (int) (cx + Math.cos(a) * 78), ty = (int) (cy + Math.sin(a) * 78);
            g.drawCenteredString(font, Component.literal(String.valueOf(i + 1)), tx, ty - 12, 0xFFB0B0);
            g.drawCenteredString(font, Component.translatable("vampire.ability." + i + ".short"), tx, ty - 1, 0xFFFFFF);
        }
        if (hover >= 0) {
            g.drawCenteredString(font, Component.translatable("vampire.ability." + hover), cx, cy + 138, 0xFF5566);
            g.drawCenteredString(font, Component.translatable("vampire.ability." + hover + ".desc"), cx, cy + 150, 0xFFFFFF);
        } else {
            g.drawCenteredString(font, Component.translatable("screen.vampire.hint"), cx, cy + 138, 0xAAAAAA);
        }
    }

    private static void sector(GuiGraphics g, int cx, int cy, float r0, float r1, float a0deg, float a1deg, int argb) {
        float a0 = (float) Math.toRadians(a0deg - 90 + 2), a1 = (float) Math.toRadians(a1deg - 90 - 2);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int a = argb >>> 24 & 255, r = argb >> 16 & 255, gr = argb >> 8 & 255, b = argb & 255;
        for (int i = 0; i <= 20; i++) {
            float ang = a0 + (a1 - a0) * i / 20f, c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            bb.addVertex(m, cx + c * r1, cy + s * r1, 0).setColor(r, gr, b, a);
            bb.addVertex(m, cx + c * r0, cy + s * r0, 0).setColor(r, gr, b, a);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private void cast(int i) {
        PacketDistributor.sendToServer(new VampireActionPayload(i));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && hover >= 0) { cast(hover); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= 49 && key <= 51) { cast(key - 49); return true; }
        if (VampireClient.WHEEL.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
