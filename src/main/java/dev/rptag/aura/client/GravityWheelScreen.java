package dev.rptag.aura.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.rptag.aura.GravState;
import dev.rptag.aura.GravityActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/** Roda de poderes: 8 setores; clique escolhe o poder; o centro liga/desliga a aura. Não pausa o jogo. */
public final class GravityWheelScreen extends Screen {
    private static final String[] NAMES = {"Parede", "Pesada", "Leve", "Meteoro", "Esmagar", "Flutuar", "Atrair", "Repelir"};
    private static final String[] TITLES = {"Pés na Parede", "Gravidade Pesada", "Gravidade Leve", "Queda Meteoro",
            "Campo: Esmagar", "Campo: Flutuar", "Campo: Atrair", "Campo: Repelir"};
    private static final String[] DESC = {
            "Anda em paredes e tetos por 10s. Agache ou pule para soltar",
            "Pesado e resistente: imune a empurrões, mais defesa (20s)",
            "Pulos altos e planadas longas, sem dano de queda (20s)",
            "Salta alto e despenca: o impacto cresce com a altura",
            "Quem estiver no campo fica pesado, lento e sem pular",
            "Ergue inimigos no ar. Quando acabar, eles caem",
            "Puxa todos no campo para você",
            "Empurra todos no campo para longe"};
    private static final int[] COLORS = {0x66CCFF, 0x7788EE, 0xCCEEFF, 0xFFC864, 0xAA66FF, 0x66FFEE, 0x6699FF, 0xFFFFFF};
    private static final float R_IN = 42f, R_OUT = 125f;
    private int hover = -1;

    public GravityWheelScreen() { super(Component.literal("Gravidade")); }

    @Override
    public boolean isPauseScreen() { return false; }

    private static int bits() {
        var p = Minecraft.getInstance().player;
        return p == null ? 0 : GravState.get(p);
    }

    private static boolean active(int slot, int bits) {
        return switch (slot) {
            case 0 -> GravState.wall(bits);
            case 1 -> GravState.self(bits) == 1;
            case 2 -> GravState.self(bits) == 2;
            case 3 -> false;
            default -> GravState.field(bits) == slot - 3;
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x88000010);
        int cx = width / 2, cy = height / 2;
        double dx = mx - cx, dy = my - cy, dist = Math.sqrt(dx * dx + dy * dy);
        hover = -1;
        if (dist >= R_IN && dist <= R_OUT + 25) {
            double ang = Math.toDegrees(Math.atan2(dy, dx)) + 90 + 22.5;
            ang = ((ang % 360) + 360) % 360;
            hover = ((int) (ang / 45)) % 8;
        }
        int bits = bits();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (int i = 0; i < 8; i++) {
            int alpha = i == hover ? 0xDD : active(i, bits) ? 0xAA : 0x66;
            sector(g, cx, cy, R_IN, i == hover ? R_OUT + 8 : R_OUT, i, (alpha << 24) | COLORS[i]);
        }
        boolean on = GravState.aura(bits);
        disc(g, cx, cy, R_IN - 6, ((dist < R_IN ? 0xDD : 0xAA) << 24) | (on ? 0x33CC66 : 0xCC4444));
        RenderSystem.disableBlend();

        g.drawCenteredString(font, Component.literal("AURA"), cx, cy - 9, 0xFFFFFF);
        g.drawCenteredString(font, Component.literal(on ? "ON" : "OFF"), cx, cy + 2, 0xFFFFFF);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 - 90);
            int lx = (int) (cx + Math.cos(a) * 86), ly = (int) (cy + Math.sin(a) * 86) - 4;
            g.drawCenteredString(font, Component.literal(NAMES[i]), lx, ly, active(i, bits) ? 0x66FF88 : 0xFFFFFF);
        }
        if (hover >= 0) {
            g.drawCenteredString(font, Component.literal(TITLES[hover]), cx, cy + 150, 0xFFFFFF);
            g.drawCenteredString(font, Component.literal(DESC[hover]), cx, cy + 162, 0xBBDDFF);
        } else {
            g.drawCenteredString(font, Component.literal("Clique num poder · centro liga/desliga a aura · G ou ESC fecha"), cx, cy + 150, 0xAAAAAA);
        }
    }

    private static void sector(GuiGraphics g, int cx, int cy, float r0, float r1, int slot, int argb) {
        float a0 = (float) Math.toRadians(slot * 45 - 90 - 22.5 + 1.5), a1 = (float) Math.toRadians(slot * 45 - 90 + 22.5 - 1.5);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int a = argb >>> 24 & 255, r = argb >> 16 & 255, gr = argb >> 8 & 255, b = argb & 255;
        for (int i = 0; i <= 12; i++) {
            float ang = a0 + (a1 - a0) * i / 12f, c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            bb.addVertex(m, cx + c * r1, cy + s * r1, 0).setColor(r, gr, b, a);
            bb.addVertex(m, cx + c * r0, cy + s * r0, 0).setColor(r, gr, b, a);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private static void disc(GuiGraphics g, int cx, int cy, float rad, int argb) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        int a = argb >>> 24 & 255, r = argb >> 16 & 255, gr = argb >> 8 & 255, b = argb & 255;
        bb.addVertex(m, cx, cy, 0).setColor(r, gr, b, a);
        for (int i = 0; i <= 24; i++) {
            float ang = (float) (i * Math.PI * 2 / 24);
            bb.addVertex(m, cx + (float) Math.cos(ang) * rad, cy + (float) Math.sin(ang) * rad, 0).setColor(r, gr, b, a);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (Math.sqrt(dx * dx + dy * dy) < R_IN) {
            PacketDistributor.sendToServer(new GravityActionPayload(0, 0));
            return true;
        }
        if (hover >= 0) {
            PacketDistributor.sendToServer(new GravityActionPayload(1, hover));
            onClose();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (GravityClient.WHEEL_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
