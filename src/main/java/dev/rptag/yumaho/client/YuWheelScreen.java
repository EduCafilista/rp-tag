package dev.rptag.yumaho.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.rptag.yumaho.YuAbilities;
import dev.rptag.yumaho.YuActionPayload;
import dev.rptag.yumaho.YuAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * Engrenagem de poder (mesmo estilo da roda da Gravidade). Anel de dentro: escolhe a % de poder
 * (0, 10, 25, 50, 75, 100). Anel de fora: modo do Blackwhip. Roda do mouse: ±5%. Centro: engrenagem girando com a % atual.
 * Não pausa o jogo. O servidor valida tudo.
 */
public final class YuWheelScreen extends Screen {
    private static final int[] LEVELS = {0, 10, 25, 50, 75, 100};
    private static final String[] MODE_NAME = {"Chicote", "Puxar", "Prender", "Gancho"};
    private static final String[] MODE_DESC = {
            "Golpe de chicote: dano e empurrão",
            "Agarra e traz o alvo até você",
            "Prende o alvo no lugar por alguns segundos",
            "Prende no alvo/bloco e puxa VOCÊ até lá"};
    private static final float RI0 = 44f, RI1 = 88f, RO0 = 94f, RO1 = 132f;
    private int hoverIn = -1, hoverOut = -1;
    /**
     * (5.78.6) BOTOES DAS HABILIDADES na propria roda — o dono: <i>"tem que ter essa roda de
     * habilidades, se nao fica muitos comandos e teclas, em modpack vai ficar ruim"</i>.
     * Cada botao manda a acao da habilidade; as teclas continuam valendo, mas viram opcionais.
     */
    private static final String[][] BOTOES = {
            {"Fumaça", "Z", "smoke"},
            {"Fa Jin", "F", "fajin_full"},
            {"Blackwhip", "B", "whip"},
            {"Acelerar", "N", "gear"},
            {"Desacelerar", "M", "gear_slow"},
            {"Agarrar", "K", "grab"},
    };
    private static final int BTN_W = 150, BTN_H = 20, BTN_GAP = 26;
    private int hoverBtn = -1;

    public YuWheelScreen() { super(Component.translatable("screen.yumaho.power")); }

    @Override
    public boolean isPauseScreen() { return false; }

    private int power() {
        var p = Minecraft.getInstance().player;
        return p == null ? 0 : p.getData(YuAttachments.POWER.get());
    }

    private int mode() {
        var p = Minecraft.getInstance().player;
        return p == null ? 1 : p.getData(YuAttachments.WHIP_MODE.get());
    }

    private void setPower(int v) {
        v = Mth.clamp(v, 0, 100);
        PacketDistributor.sendToServer(new YuActionPayload(YuAbilities.A_SET_POWER, v));
        var p = Minecraft.getInstance().player;
        if (p != null) p.setData(YuAttachments.POWER.get(), v);        // resposta imediata; o servidor confirma pela sincronização
    }

    private void setMode(int m) {
        PacketDistributor.sendToServer(new YuActionPayload(YuAbilities.A_WHIP_MODE, m));
        var p = Minecraft.getInstance().player;
        if (p != null) p.setData(YuAttachments.WHIP_MODE.get(), m);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x88100C00);
        int cx = width / 2, cy = height / 2;
        double dx = mx - cx, dy = my - cy, dist = Math.sqrt(dx * dx + dy * dy);
        double ang = ((Math.toDegrees(Math.atan2(dy, dx)) + 90) % 360 + 360) % 360;      // 0 = topo, horário
        hoverIn = dist >= RI0 && dist <= RI1 + 6 ? (int) (((ang + 30) % 360) / 60) % 6 : -1;
        hoverOut = dist >= RO0 && dist <= RO1 + 6 ? (int) (((ang + 45) % 360) / 90) % 4 : -1;
        int pw = power(), md = mode();
        float tms = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + pt;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (int i = 0; i < 6; i++) {
            boolean act = LEVELS[i] == pw, hov = i == hoverIn;
            float k = LEVELS[i] / 100f;
            int r = (int) Mth.lerp(k, 255, 255), gg = (int) Mth.lerp(k, 236, 200), b = (int) Mth.lerp(k, 170, 40);
            int alpha = hov ? 0xE0 : act ? 0xB8 : 0x66;
            sector(g, cx, cy, RI0, hov ? RI1 + 6 : RI1, i * 60 - 30, i * 60 + 30, (alpha << 24) | (r << 16) | (gg << 8) | b);
        }
        int[] modeCol = {0xFF8855, 0x66CCFF, 0xBB88FF, 0x77EE88};
        for (int i = 0; i < 4; i++) {
            boolean act = i == md, hov = i == hoverOut;
            sector(g, cx, cy, RO0, hov ? RO1 + 6 : RO1, i * 90 - 45, i * 90 + 45, ((hov ? 0xE0 : act ? 0xB8 : 0x55) << 24) | modeCol[i]);
        }
        botoes(g, cx, cy, mx, my);
        gear(g, cx, cy, 34f, 28f, tms * (0.6f + pw / 40f), pw);
        RenderSystem.disableBlend();

        g.drawCenteredString(font, Component.literal(pw + "%"), cx, cy - 4, 0xFFFFFF);
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60 - 90);
            g.drawCenteredString(font, Component.literal(LEVELS[i] + "%"), (int) (cx + Math.cos(a) * 66), (int) (cy + Math.sin(a) * 66) - 4,
                    LEVELS[i] == pw ? 0xFFFF66 : 0xFFFFFF);
        }
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(i * 90 - 90);
            g.drawCenteredString(font, Component.literal(MODE_NAME[i]), (int) (cx + Math.cos(a) * 113), (int) (cy + Math.sin(a) * 113) - 4,
                    i == md ? 0x66FF88 : 0xFFFFFF);
        }
        if (hoverOut >= 0) {
            g.drawCenteredString(font, Component.literal("Blackwhip: " + MODE_NAME[hoverOut]), cx, cy + 150, 0xFFFFFF);
            g.drawCenteredString(font, Component.literal(MODE_DESC[hoverOut]), cx, cy + 162, 0xFFE9A0);
        } else if (hoverIn >= 0) {
            g.drawCenteredString(font, Component.literal("Poder " + LEVELS[hoverIn] + "%"), cx, cy + 150, 0xFFFFFF);
            g.drawCenteredString(font, Component.literal("Define a intensidade de todas as habilidades"), cx, cy + 162, 0xFFE9A0);
        } else {
            g.drawCenteredString(font, Component.literal("Esquerda: habilidades · Dentro: poder · Fora: modo do chicote · Roda do mouse: ±5% · U ou ESC fecha"), cx, cy + 150, 0xAAAAAA);
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

    /** Engrenagem de 12 dentes que gira; cor vai de pálida (0%) a amarelo vivo (100%). */
    private static void gear(GuiGraphics g, int cx, int cy, float rOut, float rIn, float spin, int pct) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        float k = pct / 100f;
        int r = 255, gg = (int) Mth.lerp(k, 240, 205), b = (int) Mth.lerp(k, 200, 40);
        bb.addVertex(m, cx, cy, 0).setColor(255, 255, 235, 235);
        int teeth = 12;
        float step = (float) (Math.PI * 2 / teeth);
        for (int t = 0; t <= teeth; t++) {
            float a = spin * 0.02f + t * step;
            float[] ang = {a, a + step * 0.18f, a + step * 0.42f, a + step * 0.60f};
            float[] rad = {rIn, rOut, rOut, rIn};
            for (int j = 0; j < 4; j++)
                bb.addVertex(m, cx + (float) Math.cos(ang[j]) * rad[j], cy + (float) Math.sin(ang[j]) * rad[j], 0).setColor(r, gg, b, 235);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    /** Coluna de botoes das habilidades (esquerda da roda). */
    private void botoes(GuiGraphics g, int cx, int cy, int mx, int my) {
        int x = cx - 250, y0 = cy - (BOTOES.length * BTN_GAP) / 2;
        hoverBtn = -1;
        g.drawString(font, "HABILIDADES", x + 4, y0 - 14, 0xFFE566, true);
        for (int i = 0; i < BOTOES.length; i++) {
            int y = y0 + i * BTN_GAP;
            boolean hov = mx >= x && mx <= x + BTN_W && my >= y && my <= y + BTN_H;
            if (hov) hoverBtn = i;
            int fundo = hov ? 0xD8F0C040 : 0x8A201A08;
            g.fill(x, y, x + BTN_W, y + BTN_H, fundo);
            g.fill(x, y, x + 2, y + BTN_H, 0xFFFFD84A);
            g.drawString(font, BOTOES[i][0], x + 8, y + 6, hov ? 0xFF202A : 0xFFF3E0, true);
            g.drawString(font, "[" + BOTOES[i][1] + "]", x + BTN_W - 26, y + 6, 0xFFC060, true);
        }
    }

    /** Manda a acao da habilidade escolhida na roda. */
    private void lancar(int i) {
        int acao = switch (BOTOES[i][2]) {
            case "fajin_full" -> YuAbilities.A_FAJIN_FULL;
            case "whip" -> YuAbilities.A_WHIP;
            case "gear" -> YuAbilities.A_GEAR;
            case "gear_slow" -> YuAbilities.A_GEAR_SLOW;
            case "grab" -> YuAbilities.A_GRAB;
            default -> YuAbilities.A_SMOKE;
        };
        PacketDistributor.sendToServer(new YuActionPayload(acao, 0));
        onClose();                                  // fecha para a habilidade aparecer na hora
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (hoverBtn >= 0) { lancar(hoverBtn); return true; }
        if (hoverIn >= 0) { setPower(LEVELS[hoverIn]); return true; }
        if (hoverOut >= 0) { setMode(hoverOut); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        setPower(power() + (sy > 0 ? 5 : -5));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (YuClient.SCREEN.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
