package dev.rptag.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import dev.rptag.BubbleStyle;
import dev.rptag.OpenBubbleStylePayload;
import dev.rptag.RPTagMod;
import dev.rptag.SetBubbleModePayload;
import dev.rptag.Stickers;
import dev.rptag.UpdateBubbleStylePayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * TELA DE PERSONALIZACAO DO BALAO (3.7.0 — desenhada SOMENTE com primitivas
 * que funcionam em qualquer cliente: fill + texto do jogo + tooltip):
 *
 * <ul>
 *   <li>SEM texturas e SEM fontes especiais no menu (eram elas que sumiam em
 *       certas GPUs). Adesivos = bolinha de cor + numero + NOME no tooltip.</li>
 *   <li>SIMULACAO DE CHAT AO VIVO com fonte GRANDE (1.5x) reagindo a tudo.</li>
 *   <li>Paletas de 12 cores (Dentro/Borda) + Letra Auto/8 cores — 1 clique.</li>
 *   <li>Layout 3.7: sem sobreposicoes (verificado por teste automatico).</li>
 * </ul>
 */
public class BubbleStyleScreen extends Screen {

    private static final int PANEL_W = 268;
    private static final int PANEL_H = 224;

    // linhas verticais (a partir de py) — UMA fonte de verdade, testada
    private static final int ROW_SIM_Y0 = 30;
    private static final int ROW_SIM_Y1 = 88;
    private static final int ROW_HINT = 90;
    private static final int ROW_DENTRO = 103;
    private static final int ROW_BORDA = 117;
    private static final int ROW_LETRA = 131;
    private static final int ROW_BTN1 = 145;
    private static final int ROW_BTN2 = 163;
    private static final int ROW_BTN3 = 181;
    private static final int ROW_BTN4 = 200;

    // teclado de adesivos
    private static final int KB_COLS = 8;
    private static final int KB_ROWS = 6;
    private static final int KB_CELL = 28;
    private static final int KB_X = 22;   // (268-224)/2
    private static final int KB_Y = 30;

    // paletas
    private static final int SW_X = 76;
    private static final int SW_STRIDE = 15;
    private static final int SW_W = 14;
    private static final int SW_H = 10;

    /** Cores de identidade dos 48 adesivos (bolinha no lugar do emoji). */
    private static final int[] CHIP = {0xE04848, 0xA048C8, 0x4878E0, 0x48B048,
            0xE0C048, 0x784848, 0xF0D048, 0xF0F0B0, 0x48A858, 0xE07030, 0xF0E048, 0xC8C8E0,
            0xF0C030, 0xE05888, 0x68B868, 0x58A8E0, 0x8878B8, 0x8878B8, 0xB0B0B8, 0x886848,
            0xE080C8, 0xF0E8D0, 0xE8C838, 0x68D8E8, 0x88B8E8, 0xE8A868, 0xE04838, 0xE04858,
            0xE8C838, 0xB8B8C8, 0x58B858, 0x68C868, 0x8898E8, 0xD85848, 0x8898D8, 0xE8D878,
            0x484848, 0xE8A8B8, 0xE8A838, 0xC88848, 0xD8B088, 0xB08858, 0xF0D048, 0x68A8D8,
            0xF0F0F0, 0xF0F0F0, 0xF0F0F0, 0x8898B8};

    /** Paleta do DENTRO (12 cores). */
    private static final int[] SW_IN = {0xF8F8F8, 0xF078B0, 0xE04848, 0xF09030,
            0xF0D048, 0x8CE048, 0x58C858, 0x50C8C0, 0x5878F0, 0xA858E0, 0x82828E, 0x1E1E24};
    /** Paleta da BORDA (12 cores). */
    private static final int[] SW_BD = {0x14161C, 0x2A2E3A, 0x5A5F6E, 0x5C3A1E,
            0x8A6A2E, 0xC8A028, 0x7A2430, 0x24305C, 0x2E4A8A, 0x4A2A6A, 0x245032, 0xE8E8F0};
    /** Cores da LETRA. */
    private static final int[] TEXT_SW = {0xFFFFFF, 0x1E1E24, 0xFF5555, 0xFFAA00,
            0xFFFF55, 0x55FF55, 0x5555FF, 0xFF55FF};

    private BubbleStyle style = BubbleStyle.DEFAULT;
    private boolean bubbleMode = false;
    private boolean modeChanged = false;

    private String holdingSticker = null; // indice em texto ("23") ou null
    private boolean showKeyboard = false;

    private int px, py;
    private int kbX, kbY;
    private int pvX0, pvY0, pvX1, pvY1;

    private int pX0, pY0, pW, pH;
    private boolean hoverSticker = false;

    public BubbleStyleScreen(OpenBubbleStylePayload style) {
        super(Component.literal("Balão de Fala"));
        this.style = new BubbleStyle(style.colorRGB(), style.borderRGB(), style.borderStyle(),
                style.textColorRGB(), style.prefix(), style.mid(), style.suffix(), style.corner(),
                style.stickerX(), style.stickerY(), style.background(), style.durationSec());
    }

    private int effectiveTextColor() {
        if (style.textColorRGB() == BubbleStyle.TEXT_AUTO) {
            return BubbleBackgrounds.textColorFor(style.colorRGB() & 0xFFFFFF);
        }
        return 0xFF000000 | style.textColorRGB();
    }

    /** Texto com escala (fonte GRANDE e legivel). */
    private void bigText(GuiGraphics g, String s, float x, float y, int color, float scale,
            boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(this.font, s, 0, 0, color, shadow);
        g.pose().popPose();
    }

    @Override
    protected void init() {
        px = Math.max(2, (this.width - PANEL_W) / 2);
        py = Math.max(2, (this.height - PANEL_H) / 2);
        pvX0 = px + 8;
        pvY0 = py + ROW_SIM_Y0;
        pvX1 = px + PANEL_W - 8;
        pvY1 = py + ROW_SIM_Y1;
        kbX = px + KB_X;
        kbY = py + KB_Y;

        if (showKeyboard) {
            addRenderableWidget(Button.builder(Component.literal("✔ Pronto — clique no balão!"), b -> {
                        showKeyboard = false;
                        rebuildWidgets();
                    }).bounds(px + (PANEL_W - 180) / 2, py + ROW_BTN4, 180, 20).build());
            return;
        }

        addRenderableWidget(Button.builder(Component.literal("▣ Auto"), b -> {
                    style = withBorder(BubbleStyle.AUTO_BORDER);
                }).bounds(px + 8, py + ROW_BTN1, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▣ Minha cor"), b -> {
                    style = withBorder(SW_BD[0]);
                }).bounds(px + 93, py + ROW_BTN1, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▢ Sem borda"), b -> {
                    style = withBorder(BubbleStyle.NO_BORDER);
                }).bounds(px + 178, py + ROW_BTN1, 81, 16).build());

        addRenderableWidget(Button.builder(Component.literal("▤ Clássica"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_CLASSIC);
                }).bounds(px + 8, py + ROW_BTN2, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▤ Cartum"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_CARTOON);
                }).bounds(px + 93, py + ROW_BTN2, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▤ Dupla"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_DOUBLE);
                }).bounds(px + 178, py + ROW_BTN2, 81, 16).build());

        addRenderableWidget(Button.builder(Component.literal("✦ Adesivos (48)"), b -> {
                    showKeyboard = true;
                    rebuildWidgets();
                }).bounds(px + 8, py + ROW_BTN3, 124, 16).build());
        addRenderableWidget(new AbstractSliderButton(px + 136, py + ROW_BTN3, 123, 16,
                durationLabel(), (style.durationSec() - BubbleStyle.MIN_DURATION)
                        / (float) (BubbleStyle.MAX_DURATION - BubbleStyle.MIN_DURATION)) {
            @Override
            protected void updateMessage() {
                setMessage(durationLabel());
            }

            @Override
            protected void applyValue() {
                int secs = BubbleStyle.MIN_DURATION
                        + (int) Math.round(this.value * (BubbleStyle.MAX_DURATION - BubbleStyle.MIN_DURATION));
                style = withDuration(secs);
            }
        });

        addRenderableWidget(Button.builder(modeLabel(), b -> {
                    this.bubbleMode = !this.bubbleMode;
                    this.modeChanged = true;
                    b.setMessage(modeLabel());
                }).bounds(px + 8, py + ROW_BTN4, 84, 17).build());
        addRenderableWidget(Button.builder(Component.literal("↺ Padrão"), b -> {
                    style = BubbleStyle.DEFAULT;
                    rebuildWidgets();
                }).bounds(px + 96, py + ROW_BTN4, 78, 17).build());
        addRenderableWidget(Button.builder(Component.literal("✔ Salvar"), b -> saveAndClose())
                .bounds(px + 178, py + ROW_BTN4, 81, 17).build());
    }

    // =====================================================================
    //  Estilo: mutadores
    // =====================================================================

    private void setSticker(String glyph, int x, int y) {
        style = new BubbleStyle(style.colorRGB(), style.borderRGB(), style.borderStyle(),
                style.textColorRGB(), style.prefix(), style.mid(), style.suffix(), glyph,
                Mth.clamp(x, 5, 95), Mth.clamp(y, 5, 95), style.background(), style.durationSec());
    }

    private BubbleStyle withColor(int rgb) {
        return new BubbleStyle(rgb, style.borderRGB(), style.borderStyle(), style.textColorRGB(),
                style.prefix(), style.mid(), style.suffix(), style.corner(), style.stickerX(),
                style.stickerY(), style.background(), style.durationSec());
    }

    private BubbleStyle withBorder(int border) {
        return new BubbleStyle(style.colorRGB(), border, style.borderStyle(), style.textColorRGB(),
                style.prefix(), style.mid(), style.suffix(), style.corner(), style.stickerX(),
                style.stickerY(), style.background(), style.durationSec());
    }

    private BubbleStyle withBorderStyle(int borderStyle) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), borderStyle, style.textColorRGB(),
                style.prefix(), style.mid(), style.suffix(), style.corner(), style.stickerX(),
                style.stickerY(), style.background(), style.durationSec());
    }

    private BubbleStyle withText(int text) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.borderStyle(), text,
                style.prefix(), style.mid(), style.suffix(), style.corner(), style.stickerX(),
                style.stickerY(), style.background(), style.durationSec());
    }

    private BubbleStyle withDuration(int secs) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.borderStyle(),
                style.textColorRGB(), style.prefix(), style.mid(), style.suffix(), style.corner(),
                style.stickerX(), style.stickerY(), style.background(), secs);
    }

    private Component durationLabel() {
        return Component.literal("⏱ " + style.durationSec() + "s");
    }

    private Component modeLabel() {
        return this.bubbleMode
                ? Component.literal("☾ Balão: LIGADO").withStyle(ChatFormatting.GREEN)
                : Component.literal("☾ Balão: off");
    }

    // =====================================================================
    //  Desenho
    // =====================================================================

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF0B0E15);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        renderPanel(g);
        if (showKeyboard) {
            renderKeyboard(g, mouseX, mouseY);
        } else {
            renderChatSim(g, mouseX, mouseY);
            renderPalettes(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (!showKeyboard) {
            int mode = style.borderRGB() == BubbleStyle.AUTO_BORDER ? 0
                    : (style.borderRGB() >= 0 ? 1 : 2);
            outline(g, px + 7, py + ROW_BTN1 - 1, 83, 18, 0xFF9AE8B5);
            outline(g, px + 7 + style.borderStyle() * 85, py + ROW_BTN2 - 1, 83, 18, 0xFF9AE8B5);

            String hint;
            if (holdingSticker != null) {
                hint = "Clique NO balão pra colar · direito/ESC = cancelar";
            } else if (!style.corner().isEmpty()) {
                hint = "Clique no adesivo pra mover · direito no balão TIRA";
            } else {
                hint = "Clique nas cores · ✦ Adesivos pra escolher o emoji";
            }
            g.drawCenteredString(this.font, hint, px + PANEL_W / 2, py + ROW_HINT,
                    holdingSticker != null ? 0xFFB5F0C8 : 0xFFC8D0E8);
            if (holdingSticker != null) {
                drawChipCursor(g, Integer.parseInt(holdingSticker), mouseX, mouseY);
            }
        }
        String ver = "v" + RPTagMod.VERSION;
        g.drawString(this.font, ver, px + PANEL_W - 6 - this.font.width(ver),
                py + 5, 0xFF9AA4BC, false);
    }

    private void renderPanel(GuiGraphics g) {
        g.fill(px + 4, py + 4, px + PANEL_W + 4, py + PANEL_H + 4, 0xB2000000);
        g.fill(px, py, px + PANEL_W, py + PANEL_H, 0xFF262C3B);
        g.fill(px, py, px + PANEL_W, py + 18, 0xFF1C2130);
        g.fill(px, py + 18, px + PANEL_W, py + 20, 0xFF3E4658);
        bigText(g, "✦ Balão de Fala", px + 8, py + 4, 0xFFFFD98A, 1.0F, true);
        g.fill(px, py, px + 3, py + PANEL_H, 0xFFE8A83C);
    }

    /** Bolinha de cor do adesivo i (substitui o emoji que nao aparecia). */
    private void drawChip(GuiGraphics g, int i, int x, int y) {
        g.fill(x, y + 1, x + 16, y + 10, 0xFF000000 | CHIP[i]);
        g.fill(x + 1, y + 1, x + 15, y + 2, 0xFFFFFFFF);
        g.fill(x, y, x + 16, y + 12, 0);
        g.fill(x, y, x + 16, y + 11, 0);
        // numero do adesivo (1..48) embaixo da bolinha
        String num = String.valueOf(i + 1);
        bigText(g, num, x + 8 - this.font.width(num) * 0.4F, y + 12, 0xFFC8D0E8, 0.8F, true);
    }

    private void drawChipCursor(GuiGraphics g, int i, int mx, int my) {
        g.fill(mx - 8, my - 12, mx + 8, my - 1, 0xFF000000 | CHIP[i]);
        outline(g, mx - 8, my - 12, 16, 11, 0xFFFFFFFF);
    }

    /** SIMULACAO DE CHAT AO VIVO. */
    private void renderChatSim(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(pvX0, pvY0, pvX1, pvY1, 0xE4141820);
        outline(g, pvX0, pvY0, pvX1 - pvX0, pvY1 - pvY0, 0xFF060810);

        int colorRGB = style.colorRGB() & 0xFFFFFF;
        int fill = BubbleBackgrounds.fillColorFor(colorRGB);
        int border = BubbleBackgrounds.borderColorFor(colorRGB, style.borderRGB());
        int textColor = effectiveTextColor();

        float chatY = pvY0 + 4;
        bigText(g, "<Você>", pvX0 + 6, chatY, 0xFF000000 | colorRGB, 1.0F, true);
        int nameW = this.font.width("<Você> ");
        bigText(g, "sua fala assim", pvX0 + 6 + nameW, chatY, textColor, 1.0F, true);

        var text = Stickers.styled("sua fala assim");
        int textW = this.font.width(text);
        pW = Math.max(44, textW + 16);
        pH = 16;
        pX0 = (pvX0 + pvX1) / 2 - pW / 2;
        pY0 = pvY0 + 26;

        int bs = style.borderStyle();
        if (border != 0) {
            if (bs == 1) {
                fillPill(g, pX0 - 4, pY0 - 4, pW + 8, pH + 8, border);
            } else if (bs == 2) {
                fillPill(g, pX0 - 1, pY0 - 1, pW + 2, pH + 2, border);
                fillPill(g, pX0 - 4, pY0 - 4, pW + 8, pH + 8, border);
            } else {
                fillPill(g, pX0 - 2, pY0 - 2, pW + 4, pH + 4, border);
            }
        }
        fillPill(g, pX0, pY0, pW, pH, fill);
        g.drawString(this.font, "▼", pvX0 + (pvX1 - pvX0) / 2 - 5, pY0 + pH + (bs == 1 ? 3 : 1), fill, false);
        g.drawString(this.font, text, pX0 + 8, pY0 + 3, textColor, true);

        hoverSticker = false;
        int sx = pX0 + Math.round(style.stickerX() / 100.0F * pW);
        int sy = pY0 + Math.round(style.stickerY() / 100.0F * pH);
        if (!style.corner().isEmpty()) {
            int idx = (style.corner().charAt(0) & 0xFFFF) - 0xE000;
            if (idx < 0 || idx >= Stickers.COUNT) {
                idx = 0;
            }
            g.fill(sx - 8, sy - 8, sx + 8, sy + 3, 0xFF000000 | CHIP[idx]);
            outline(g, sx - 8, sy - 8, 16, 11, 0xFF060810);
            if (holdingSticker == null && Math.abs(mouseX - sx) <= 10 && Math.abs(mouseY - sy) <= 10) {
                hoverSticker = true;
                reticle(g, sx, sy, 0xFFFFFFFF);
            }
        }
        if (holdingSticker != null) {
            int hi = Integer.parseInt(holdingSticker);
            int gx = Mth.clamp(mouseX, pX0, pX0 + pW);
            int gy = Mth.clamp(mouseY, pY0, pY0 + pH);
            g.setColor(1.0F, 1.0F, 1.0F, 0.6F);
            g.fill(gx - 8, gy - 8, gx + 8, gy + 3, 0xFF000000 | CHIP[hi]);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            outline(g, gx - 8, gy - 8, 16, 11, 0xFFFFFFFF);
            reticle(g, gx, gy, 0xFFB5F0C8);
        }

        // rodape: quadradinhos das cores atuais + hex GRANDE
        int fy = pvY1 - 11;
        g.fill(pvX0 + 6, fy, pvX0 + 13, fy + 7, fill);
        outline(g, pvX0 + 6, fy, 7, 7, 0xFF060810);
        if (border != 0) {
            g.fill(pvX0 + 17, fy, pvX0 + 24, fy + 7, border);
            outline(g, pvX0 + 17, fy, 7, 7, 0xFF060810);
        }
        g.fill(pvX0 + 28, fy, pvX0 + 35, fy + 7, textColor);
        outline(g, pvX0 + 28, fy, 7, 7, 0xFF060810);
        bigText(g, String.format("#%06X", colorRGB & 0xFFFFFF), pvX0 + 41, fy - 1,
                0xFFF0F3FF, 1.0F, true);
        if (!style.corner().isEmpty()) {
            String pos = style.stickerX() + "," + style.stickerY();
            g.drawString(this.font, pos, pvX1 - 6 - this.font.width(pos), pvY0 + 4, 0xFFB5F0C8, false);
        }
    }

    private void reticle(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 12, cy, cx + 12, cy + 1, color);
        g.fill(cx, cy - 10, cx + 1, cy + 10, color);
    }

    private void fillPill(GuiGraphics g, int x, int y, int w, int h, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        int r = h / 2;
        for (int dy = 0; dy < h; dy++) {
            double vert = dy - (h - 1) / 2.0;
            double vv = Math.min(r * (double) r, vert * vert);
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * (double) r - vv)));
            g.fill(x + inset, y + dy, x + w - inset, y + dy + 1, argb);
        }
    }

    private void renderSwatchRow(GuiGraphics g, int y, String label, int[] sw, int selected) {
        bigText(g, label, px + 8, y, 0xFFFFD98A, 1.0F, true);
        for (int i = 0; i < sw.length; i++) {
            int x = px + SW_X + i * SW_STRIDE;
            g.fill(x, y, x + SW_W, y + SW_H, 0xFF000000 | sw[i]);
            outline(g, x, y, SW_W, SW_H, selected == sw[i] ? 0xFFFFFFFF : 0xFF060810);
        }
    }

    private void renderPalettes(GuiGraphics g) {
        renderSwatchRow(g, py + ROW_DENTRO, "✦ Dentro:", SW_IN, style.colorRGB());
        renderSwatchRow(g, py + ROW_BORDA, "▣ Borda:", SW_BD,
                style.borderRGB() >= 0 ? style.borderRGB() : -1);
        String bdNote = style.borderRGB() == BubbleStyle.NO_BORDER ? "(sem)"
                : (style.borderRGB() == BubbleStyle.AUTO_BORDER ? "(auto)" : "");
        if (!bdNote.isEmpty()) {
            g.drawString(this.font, bdNote, px + PANEL_W - 34, py + ROW_BORDA + 1, 0xFF9AA4BC, false);
        }
        int ly = py + ROW_LETRA;
        bigText(g, "✎ Letra:", px + 8, ly, 0xFFFFD98A, 1.0F, true);
        boolean auto = style.textColorRGB() == BubbleStyle.TEXT_AUTO;
        g.fill(px + SW_X, ly, px + SW_X + 30, ly + SW_H, auto ? 0xFF2E4A38 : 0xFF1C2130);
        outline(g, px + SW_X, ly, 30, SW_H, auto ? 0xFF9AE8B5 : 0xFF39415A);
        g.drawString(this.font, "Auto", px + SW_X + 4, ly + 1, auto ? 0xFFB5F0C8 : 0xFF9AA3B5, false);
        for (int i = 0; i < TEXT_SW.length; i++) {
            int x = px + SW_X + 34 + i * SW_STRIDE;
            g.fill(x, ly, x + SW_W, ly + SW_H, 0xFF000000 | TEXT_SW[i]);
            outline(g, x, ly, SW_W, SW_H, style.textColorRGB() == TEXT_SW[i] ? 0xFFFFFFFF : 0xFF060810);
        }
    }

    /** Teclado: bolinha de cor + numero; NOME completo no tooltip. */
    private void renderKeyboard(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < Stickers.COUNT; i++) {
            int cx = kbX + (i % KB_COLS) * KB_CELL;
            int cy = kbY + (i / KB_COLS) * KB_CELL;
            g.fill(cx, cy, cx + KB_CELL, cy + KB_CELL, 0xFF242B3D);
            g.fill(cx, cy, cx + 1, cy + KB_CELL, 0xFF39415A);
            g.fill(cx, cy, cx + KB_CELL, cy + 1, 0xFF39415A);
            g.fill(cx, cy + KB_CELL - 1, cx + KB_CELL, cy + KB_CELL, 0xFF171B26);
            g.fill(cx + KB_CELL - 1, cy, cx + KB_CELL, cy + KB_CELL, 0xFF171B26);
            drawChip(g, i, cx + 6, cy + 4);
        }
        int over = stickerAt(mouseX, mouseY);
        if (over >= 0) {
            int cx = kbX + (over % KB_COLS) * KB_CELL;
            int cy = kbY + (over / KB_COLS) * KB_CELL;
            outline(g, cx, cy, KB_CELL, KB_CELL, 0xFFFFFFFF);
            g.renderTooltip(this.font, Component.literal(
                    "#" + (over + 1) + " — " + Stickers.NAMES[over]), mouseX, mouseY);
        }
    }

    private int stickerAt(double mx, double my) {
        int cx = (int) ((mx - kbX) / KB_CELL);
        int cy = (int) ((my - kbY) / KB_CELL);
        if (cx < 0 || cx >= KB_COLS || cy < 0 || cy >= KB_ROWS) {
            return -1;
        }
        return cy * KB_COLS + cx;
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    // =====================================================================
    //  Interacao
    // =====================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && showKeyboard) {
            int idx = stickerAt(mx, my);
            if (idx >= 0) {
                holdingSticker = String.valueOf(idx); // gruda no mouse!
                showKeyboard = false;
                rebuildWidgets();
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        if (!showKeyboard && button == 0 && holdingSticker != null) {
            String idx = holdingSticker;
            holdingSticker = null;
            if (inBox(mx, my, pvX0, pvY0, pvX1 - pvX0, pvY1 - pvY0)) {
                int x = Mth.clamp((int) mx, pX0, pX0 + pW);
                int y = Mth.clamp((int) my, pY0, pY0 + pH);
                setSticker(Stickers.GLYPHS[Integer.parseInt(idx)],
                        Math.round((x - pX0) * 100.0F / pW), Math.round((y - pY0) * 100.0F / pH));
            }
            return true;
        }
        if (!showKeyboard && button == 0 && hoverSticker) {
            holdingSticker = String.valueOf((style.corner().charAt(0) & 0xFFFF) - 0xE000);
            setSticker("", style.stickerX(), style.stickerY());
            return true;
        }
        if (!showKeyboard && button == 1 && holdingSticker != null) {
            holdingSticker = null;
            return true;
        }
        if (!showKeyboard && button == 1 && inBox(mx, my, pvX0, pvY0, pvX1 - pvX0, pvY1 - pvY0)) {
            if (!style.corner().isEmpty()) {
                setSticker("", style.stickerX(), style.stickerY());
            }
            return true;
        }
        if (!showKeyboard && button == 0) {
            String hit = paletteHit(mx, my);
            if (!hit.isEmpty()) {
                if (hit.equals("A")) {
                    style = withText(BubbleStyle.TEXT_AUTO);
                } else {
                    int idx = Integer.parseInt(hit.substring(1));
                    switch (hit.charAt(0)) {
                        case 'D' -> style = withColor(SW_IN[idx]);
                        case 'B' -> style = withBorder(SW_BD[idx]);
                        default -> style = withText(TEXT_SW[idx]);
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** @return "A", "D#"/"B#"/"L#" ou "". */
    private String paletteHit(double mx, double my) {
        int idx = (int) ((mx - (px + SW_X)) / SW_STRIDE);
        boolean inRange = idx >= 0;
        if (my >= py + ROW_DENTRO && my < py + ROW_DENTRO + SW_H && inRange && idx < SW_IN.length) {
            return "D" + idx;
        }
        if (my >= py + ROW_BORDA && my < py + ROW_BORDA + SW_H && inRange && idx < SW_BD.length) {
            return "B" + idx;
        }
        int ly = py + ROW_LETRA;
        if (my >= ly && my < ly + SW_H) {
            if (mx >= px + SW_X && mx < px + SW_X + 30) {
                return "A";
            }
            int li = (int) ((mx - (px + SW_X + 34)) / SW_STRIDE);
            if (li >= 0 && li < TEXT_SW.length) {
                return "L" + li;
            }
        }
        return "";
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            if (holdingSticker != null) {
                holdingSticker = null;
                return true;
            }
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // =====================================================================
    //  Salvar
    // =====================================================================

    private void saveAndClose() {
        PacketDistributor.sendToServer(new UpdateBubbleStylePayload(
                style.colorRGB(), style.borderRGB(), style.borderStyle(), style.textColorRGB(),
                style.prefix(), style.mid(), style.suffix(), style.corner(), style.stickerX(),
                style.stickerY(), style.background(), style.durationSec()));
        if (modeChanged) {
            PacketDistributor.sendToServer(new SetBubbleModePayload(bubbleMode));
        }
        onClose();
    }

    /** Abre a tela (chamado pelo handler do pacote S2C). */
    public static void open(OpenBubbleStylePayload payload) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new BubbleStyleScreen(payload));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
