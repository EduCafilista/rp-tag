package dev.rptag.king.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.rptag.king.KingActionPayload;
import dev.rptag.king.KingAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Roda de habilidades do Rei de Amarelo (mesmo estilo da roda da Gravidade): 6 setores + disco central (ultimate).
 * Clique ou teclas 1–7 lançam; setores em recarga ficam apagados com os segundos restantes. Não pausa o jogo.
 */
public final class KingWheelScreen extends Screen {
    private static final float R0 = 54f, R1 = 122f, RC = 44f;
    private int hover = -1;

    public KingWheelScreen() { super(Component.translatable("screen.king.wheel")); }

    @Override public boolean isPauseScreen() { return false; }

    private long cdLeft(int i) {
        var p = Minecraft.getInstance().player;
        if (p == null || Minecraft.getInstance().level == null) return 0;
        List<Integer> cd = p.getData(KingAttachments.COOLDOWNS.get());
        if (cd == null || cd.size() <= i) return 0;
        return Math.max(0L, (long) cd.get(i) - (Minecraft.getInstance().level.getGameTime() & 0xFFFFFFFFL));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x99080500);
        int cx = width / 2, cy = height / 2;
        double dx = mx - cx, dy = my - cy, dist = Math.sqrt(dx * dx + dy * dy);
        double ang = ((Math.toDegrees(Math.atan2(dy, dx)) + 90) % 360 + 360) % 360;
        hover = dist <= RC ? 6 : (dist >= R0 && dist <= R1 + 8) ? (int) (((ang + 30) % 360) / 60) % 6 : -1;
        float t = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + pt;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (int i = 0; i < 6; i++) {
            boolean hov = i == hover, cd = cdLeft(i) > 0;
            float k = hov ? 1f : 0.7f;
            int a = cd ? 0x40 : hov ? 0xE6 : 0x9A;
            int r = (int) (255 * (cd ? 0.35f : k)), gg = (int) (200 * (cd ? 0.3f : k)), b = (int) (30 * (cd ? 0.2f : k));
            sector(g, cx, cy, R0, hov ? R1 + 8 : R1, i * 60 - 30, i * 60 + 30, (a << 24) | (r << 16) | (gg << 8) | b);
            sector(g, cx, cy, R0 - 3, R0, i * 60 - 30, i * 60 + 30, 0xFF000000);                 // aro negro interno
        }
        boolean uh = hover == 6, ucd = cdLeft(6) > 0;
        disc(g, cx, cy, RC + 3, 0xFF000000);                                                      // disco-eclipse negro
        disc(g, cx, cy, RC, ucd ? 0xFF1B1308 : 0xFF050301);
        ring(g, cx, cy, RC, RC + 3, ucd ? 0x66A07A18 : (uh ? 0xFFFFF0A0 : 0xFFFFC62A), t);       // anel luminoso
        RenderSystem.disableBlend();

        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60 - 90);
            int tx = (int) (cx + Math.cos(a) * 88), ty = (int) (cy + Math.sin(a) * 88);
            long left = cdLeft(i);
            g.drawCenteredString(font, Component.literal(String.valueOf(i + 1)), tx, ty - 14, 0xFFE9A0);
            g.drawCenteredString(font, Component.translatable("king.ability." + i + ".short"), tx, ty - 3, left > 0 ? 0x887744 : 0xFFFFFF);
            if (left > 0) g.drawCenteredString(font, Component.literal(String.format("%.1fs", left / 20.0)), tx, ty + 8, 0xFFCC55);
        }
        long ul = cdLeft(6);
        g.drawCenteredString(font, Component.literal("7"), cx, cy - 14, 0xFFE9A0);
        g.drawCenteredString(font, Component.translatable("king.ability.6.short"), cx, cy - 3, ul > 0 ? 0x887744 : 0xFFD84A);
        if (ul > 0) g.drawCenteredString(font, Component.literal(String.format("%.0fs", ul / 20.0)), cx, cy + 8, 0xFFCC55);

        if (hover >= 0) {
            g.drawCenteredString(font, Component.translatable("king.ability." + hover), cx, cy + 148, 0xFFE566);
            g.drawCenteredString(font, Component.translatable("king.ability." + hover + ".desc"), cx, cy + 160, 0xFFFFFF);
        } else {
            g.drawCenteredString(font, Component.translatable("screen.king.hint"), cx, cy + 148, 0xAAAAAA);
        }
    }

    private static void sector(GuiGraphics g, int cx, int cy, float r0, float r1, float a0deg, float a1deg, int argb) {
        float a0 = (float) Math.toRadians(a0deg - 90 + 1.5), a1 = (float) Math.toRadians(a1deg - 90 - 1.5);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int a = argb >>> 24 & 255, r = argb >> 16 & 255, gr = argb >> 8 & 255, b = argb & 255;
        for (int i = 0; i <= 14; i++) {
            float ang = a0 + (a1 - a0) * i / 14f, c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            bb.addVertex(m, cx + c * r1, cy + s * r1, 0).setColor(r, gr, b, a);
            bb.addVertex(m, cx + c * r0, cy + s * r0, 0).setColor(r, gr, b, a);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private static void disc(GuiGraphics g, int cx, int cy, float r, int argb) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        int a = argb >>> 24 & 255, rr = argb >> 16 & 255, gg = argb >> 8 & 255, b = argb & 255;
        bb.addVertex(m, cx, cy, 0).setColor(rr, gg, b, a);
        for (int i = 0; i <= 40; i++) {
            double ang = Math.PI * 2 * i / 40;
            bb.addVertex(m, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r, 0).setColor(rr, gg, b, a);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    /** Anel luminoso com brilho que pulsa. */
    private static void ring(GuiGraphics g, int cx, int cy, float r0, float r1, int argb, float t) {
        Matrix4f m = g.pose().last().pose();
        int a = argb >>> 24 & 255, r = argb >> 16 & 255, gr = argb >> 8 & 255, b = argb & 255;
        float pulse = 0.85f + 0.15f * Mth.sin(t * 0.15f);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 48; i++) {
            double ang = Math.PI * 2 * i / 48;
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            bb.addVertex(m, cx + c * (r1 + 6), cy + s * (r1 + 6), 0).setColor(r, gr, b, 0);
            bb.addVertex(m, cx + c * r0, cy + s * r0, 0).setColor(r, gr, b, (int) (a * pulse));
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
        bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= 48; i++) {
            double ang = Math.PI * 2 * i / 48;
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            bb.addVertex(m, cx + c * r1, cy + s * r1, 0).setColor(r, gr, b, a);
            bb.addVertex(m, cx + c * r0, cy + s * r0, 0).setColor(r, gr, b, a);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    private void cast(int i) {
        PacketDistributor.sendToServer(new KingActionPayload(i));
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && hover >= 0) { cast(hover); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= 49 && key <= 55) { cast(key - 49); return true; }                             // teclas 1..7
        if (KingClient.WHEEL.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
