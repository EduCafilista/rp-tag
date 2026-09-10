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
 * TELA DE PERSONALIZACAO DO BALAO (2.8.0 — estilo classico + total):
 *
 * <ul>
 *   <li>Seletor de cor de ARRASTAR com Alvo: cor de DENTRO ou cor da BORDA</li>
 *   <li>TECLADO DE ADESIVOS: 48 adesivos coloridos — CLICA e ARRASTA ate
 *       Antes / Meio / Depois / CANTO (estilo reacao)</li>
 *   <li>Barra de DURACAO (2-20s) e Modo balao · preview ao vivo</li>
 * </ul>
 *
 * <p>NITIDEZA: fundo OPACO proprio — o desfoque do 1.21 nunca toca no painel.
 */
public class BubbleStyleScreen extends Screen {

    private static final int PANEL_W = 262;
    private static final int PANEL_H = 200;

    private static final int SV_W = 106;
    private static final int SV_H = 62;
    private static final int HUE_H = 10;

    // teclado de adesivos
    private static final int KB_COLS = 8;
    private static final int KB_ROWS = 6;
    private static final int KB_CELL = 24;

    /** Alvo do seletor de cor: false = dentro, true = borda. */
    private boolean pickingBorder = false;

    private float hue = 0.5F, sat = 1.0F, val = 1.0F;
    private BubbleStyle style = BubbleStyle.DEFAULT;
    private boolean bubbleMode = false;
    private boolean modeChanged = false;
    private boolean draggingSv = false;
    private boolean draggingHue = false;

    // teclado
    private boolean showKeyboard = false;
    private String draggingSticker = null;
    private String selectedSticker = null;

    // areas
    private int px, py;
    private int svX, svY, hueY;
    private int kbX, kbY;
    private final int[] slotX = new int[4];
    private int slotY, slotW;

    public BubbleStyleScreen(OpenBubbleStylePayload style) {
        super(Component.literal("Balão de Fala"));
        this.style = new BubbleStyle(style.colorRGB(), style.borderRGB(), style.prefix(),
                style.mid(), style.suffix(), style.corner(), style.background(), style.durationSec());
        loadPickerFrom(style.colorRGB());
    }

    private void loadPickerFrom(int rgb) {
        float[] hsb = rgbToHsv(rgb);
        this.hue = hsb[0];
        this.sat = hsb[1];
        this.val = hsb[2];
    }

    @Override
    protected void init() {
        px = (this.width - PANEL_W) / 2;
        py = (this.height - PANEL_H) / 2;
        svX = px + 12;
        svY = py + 58;
        hueY = svY + SV_H + 6;
        kbX = px + (PANEL_W - KB_COLS * KB_CELL) / 2;
        kbY = py + 46;
        slotW = 54;
        slotY = py + 24;
        for (int i = 0; i < 4; i++) {
            slotX[i] = px + (PANEL_W - 4 * slotW - 3 * 6) / 2 + i * (slotW + 6);
        }

        if (showKeyboard) {
            initKeyboard();
        } else {
            initMain();
        }
    }

    // =====================================================================
    //  UI principal
    // =====================================================================

    private void initMain() {
        int bx = px + 128;
        int bw = PANEL_W - 128 - 12;

        addRenderableWidget(Button.builder(targetLabel(), b -> {
                    pickingBorder = !pickingBorder;
                    loadPickerFrom(pickingBorder
                            ? (style.borderRGB() >= 0 ? style.borderRGB() : style.colorRGB())
                            : style.colorRGB());
                    b.setMessage(targetLabel());
                }).bounds(bx, svY, bw, 18).build());

        addRenderableWidget(Button.builder(Component.literal("✖ Sem borda"), b -> {
                    style = withBorder(BubbleStyle.NO_BORDER);
                }).bounds(bx, svY + 20, bw, 18).build());

        addRenderableWidget(Button.builder(adesivosLabel(), b -> {
                    showKeyboard = true;
                    rebuildWidgets();
                }).bounds(bx, svY + 40, bw, 18).build());

        addRenderableWidget(Button.builder(modeLabel(), b -> {
                    this.bubbleMode = !this.bubbleMode;
                    this.modeChanged = true;
                    b.setMessage(modeLabel());
                }).bounds(bx, svY + 60, bw, 18).build());

        // botoes rapidos: ciclam o adesivo de cada posicao
        int ew = (bw - 12) / 4;
        for (int i = 0; i < 4; i++) {
            final int which = i;
            addRenderableWidget(Button.builder(slotGlyph(which), b -> {
                        cycleSlot(which);
                        b.setMessage(slotGlyph(which));
                    }).bounds(bx + i * (ew + 4), svY + 82, ew, 16).build());
        }

        // BARRA DE DURACAO: arrasta e escolhe 2-20 segundos
        addRenderableWidget(new AbstractSliderButton(svX, py + 156, SV_W, 16,
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

        addRenderableWidget(Button.builder(Component.literal("✔ Salvar"), b -> saveAndClose())
                .bounds(px + 12, py + PANEL_H - 24, 115, 18).build());
        addRenderableWidget(Button.builder(Component.literal("✖ Cancelar"), b -> onClose())
                .bounds(px + PANEL_W - 127, py + PANEL_H - 24, 115, 18).build());
    }

    // =====================================================================
    //  Teclado de adesivos (ARRASTAR para Antes/Meio/Depois/Canto)
    // =====================================================================

    private void initKeyboard() {
        for (int i = 0; i < 4; i++) {
            final int which = i;
            addRenderableWidget(Button.builder(slotLabel(which), b -> {
                        if (selectedSticker != null) {
                            setSlot(which, selectedSticker);
                            selectedSticker = null;
                        } else {
                            cycleSlot(which);
                        }
                        b.setMessage(slotLabel(which));
                    }).bounds(slotX[i], slotY, slotW, 16).build());
        }
        addRenderableWidget(Button.builder(Component.literal("✔ Voltar"), b -> {
                    showKeyboard = false;
                    rebuildWidgets();
                }).bounds(px + 12, py + PANEL_H - 24, 115, 18).build());
        addRenderableWidget(Button.builder(Component.literal("✖ Limpar alvo"), b -> {
                    if (selectedSticker != null) {
                        setSlot(findSlotOf(selectedSticker), "");
                        selectedSticker = null;
                        rebuildWidgets();
                    }
                }).bounds(px + PANEL_W - 127, py + PANEL_H - 24, 115, 18).build());
    }

    private int findSlotOf(String glyph) {
        if (glyph.equals(style.prefix())) {
            return 0;
        }
        if (glyph.equals(style.mid())) {
            return 1;
        }
        if (glyph.equals(style.suffix())) {
            return 2;
        }
        return 3;
    }

    private void setSlot(int which, String glyph) {
        style = switch (which) {
            case 0 -> withPrefix(glyph);
            case 1 -> withMid(glyph);
            case 2 -> withSuffix(glyph);
            default -> withCorner(glyph);
        };
    }

    private void cycleSlot(int which) {
        setSlot(which, nextSticker(switch (which) {
            case 0 -> style.prefix();
            case 1 -> style.mid();
            case 2 -> style.suffix();
            default -> style.corner();
        }));
    }

    private String slotValue(int which) {
        return switch (which) {
            case 0 -> style.prefix();
            case 1 -> style.mid();
            case 2 -> style.suffix();
            default -> style.corner();
        };
    }

    private Component slotLabel(int which) {
        String name = switch (which) {
            case 0 -> "Antes";
            case 1 -> "Meio";
            case 2 -> "Depois";
            default -> "Canto";
        };
        String cur = slotValue(which);
        return Component.literal(name + " ")
                .append(cur.isEmpty() ? Component.literal("—") : Stickers.label(cur));
    }

    private Component slotGlyph(int which) {
        return Stickers.label(slotValue(which));
    }

    // =====================================================================
    //  Desenho — TUDO opaco e pixel-perfect (zero blur)
    // =====================================================================

    /** Fundo OPACO do menu — sobrescreve o desfoque do 1.21. Idempotente. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF0F1218);
        for (int gx = 0; gx < this.width; gx += 16) {
            g.fill(gx, 0, gx + 1, this.height, 0xFF141824);
        }
        for (int gy = 0; gy < this.height; gy += 16) {
            g.fill(0, gy, this.width, gy + 1, 0xFF141824);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        renderPanel(g);
        if (showKeyboard) {
            renderKeyboard(g, mouseX, mouseY);
        } else {
            renderPicker(g);
            renderSwatches(g);
            renderPreview(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (showKeyboard) {
            String[] heads = {"Antes", "Meio", "Depois", "Canto"};
            for (int i = 0; i < 4; i++) {
                g.drawCenteredString(this.font, heads[i], slotX[i] + slotW / 2, slotY - 10, 0xFF9AA3B5);
            }
            g.drawCenteredString(this.font, "Escolha e ARRASTE o adesivo", this.width / 2, py + 8, 0xFFFFFFFF);
            if (selectedSticker != null) {
                g.drawCenteredString(this.font, "Agora clique no destino (Antes/Meio/Depois/Canto)!",
                        this.width / 2, py + PANEL_H - 36, 0xFF9AE8B5);
            }
        } else {
            g.drawCenteredString(this.font, "✦ Balão de Fala", this.width / 2, py + 8, 0xFFFFFFFF);
        }
        // adesivo sendo arrastado: segue o mouse (como um sticker de verdade)
        if (draggingSticker != null) {
            g.drawString(this.font, Stickers.label(draggingSticker), mouseX - 4, mouseY - 10, 0xFFFFFFFF, false);
        }
    }

    private void renderPanel(GuiGraphics g) {
        g.fill(px + 4, py + 4, px + PANEL_W + 4, py + PANEL_H + 4, 0xAA000000);
        g.fill(px, py, px + PANEL_W, py + PANEL_H, 0xFF232733);
        g.fill(px, py, px + PANEL_W, py + 2, 0xFF3E4454);
        g.fill(px, py, px + 2, py + PANEL_H, 0xFF3E4454);
        g.fill(px, py + PANEL_H - 2, px + PANEL_W, py + PANEL_H, 0xFF12141B);
        g.fill(px + PANEL_W - 2, py, px + PANEL_W, py + PANEL_H, 0xFF12141B);
        if (!showKeyboard) {
            g.fill(svX - 1, svY - 1, svX + SV_W + 1, svY + SV_H + 1, 0xFF12141B);
            g.fill(svX - 1, hueY - 1, svX + SV_W + 1, hueY + HUE_H + 1, 0xFF12141B);
        }
    }

    /** Quadrado de saturacao/brilho + faixa de matiz + cursor + hex. */
    private void renderPicker(GuiGraphics g) {
        int cols = 26, rows = 15;
        float cw = SV_W / (float) cols, ch = SV_H / (float) rows;
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                int rgb = Mth.hsvToRgb(hue, (cx + 0.5F) / cols, 1.0F - (cy + 0.5F) / rows);
                g.fill(svX + (int) (cx * cw), svY + (int) (cy * ch),
                        svX + (int) ((cx + 1) * cw) + 1, svY + (int) ((cy + 1) * ch) + 1,
                        0xFF000000 | rgb);
            }
        }
        int curX = svX + (int) (sat * SV_W) - 3;
        int curY = svY + (int) ((1 - val) * SV_H) - 3;
        outline(g, curX, curY, 7, 7, 0xFF000000);
        outline(g, curX + 1, curY + 1, 5, 5, 0xFFFFFFFF);

        int hcols = 26;
        float hw = SV_W / (float) hcols;
        for (int i = 0; i < hcols; i++) {
            int rgb = Mth.hsvToRgb((i + 0.5F) / hcols, 1.0F, 1.0F);
            g.fill(svX + (int) (i * hw), hueY, svX + (int) ((i + 1) * hw) + 1, hueY + HUE_H, 0xFF000000 | rgb);
        }
        int hx = svX + (int) (hue * SV_W) - 2;
        outline(g, hx, hueY - 1, 5, HUE_H + 2, 0xFF000000);
        outline(g, hx + 1, hueY, 3, HUE_H, 0xFFFFFFFF);

        int rgb = currentRgb();
        g.drawString(this.font, String.format("#%06X", rgb), svX, hueY + HUE_H + 9, 0xFFE8E8F2, true);
        g.fill(svX + 66, hueY + HUE_H + 7, svX + 84, hueY + HUE_H + 20, 0xFF000000 | rgb);
        outline(g, svX + 66, hueY + HUE_H + 7, 18, 13, 0xFF000000);
        outline(g, svX + 67, hueY + HUE_H + 8, 16, 11, 0xFF3E4454);
    }

    /** Amostras: a cor de DENTRO e a cor da BORDA atuais. */
    private void renderSwatches(GuiGraphics g) {
        int y = py + 47;
        g.drawString(this.font, "dentro", svX, y + 2, 0xFF9AA3B5, false);
        g.fill(svX + 36, y, svX + 48, y + 10, BubbleBackgrounds.fillColorFor(style.colorRGB()));
        outline(g, svX + 36, y, 12, 10, 0xFF000000);
        g.drawString(this.font, "borda", svX + 56, y + 2, 0xFF9AA3B5, false);
        int bc = BubbleBackgrounds.borderColorFor(style.colorRGB(), style.borderRGB());
        g.fill(svX + 84, y, svX + 96, y + 10, bc != 0 ? bc : 0xFF171A22);
        outline(g, svX + 84, y, 12, 10, bc != 0 ? 0xFF000000 : 0xFF5A5F6E);
    }

    /** Preview do balao ao vivo (estilo classico, com canto). */
    private void renderPreview(GuiGraphics g) {
        int colorRGB = style.colorRGB() & 0xFFFFFF;
        var text = Stickers.styled(
                (style.prefix().isEmpty() ? "" : style.prefix() + " ")
                        + "sua fala assim"
                        + (style.suffix().isEmpty() ? "" : " " + style.suffix()));
        int cx = this.width / 2;
        int y = py + 30;

        g.fill(px + 12, py + 22, px + PANEL_W - 12, py + 52, 0xFF171A22);
        outline(g, px + 12, py + 22, PANEL_W - 24, 30, 0xFF12141B);

        int fill = BubbleBackgrounds.fillColorFor(colorRGB);
        int border = BubbleBackgrounds.borderColorFor(colorRGB, style.borderRGB());
        int pillW = Math.max(34, this.font.width(text) + 14);
        int sFull = Math.max(2, (pillW - 2) / 4);
        int sCap = Math.max(1, sFull - 2);
        int bw = 4 * sFull + 2;
        int x0 = cx - bw / 2;
        int xCap = x0 + 8 - (bw - (4 * sCap + 2)) / 2; // capas centradas (inset ~4px)

        // sombra + contorno + recheio (tampa, meio, tampa)
        g.fill(x0 + 3, y + 3, x0 + bw + 3, y + 13, 0x56000000);
        if (border != 0) {
            g.fill(x0 - 2, y - 1, x0 + bw + 2, y + 9, border);
        }
        g.fill(x0, y - 1, x0 + bw, y + 9, fill);
        if (border != 0) {
            g.fill(xCap - 2, y - 11, xCap + (4 * sCap + 2) + 2, y - 1, border);
            g.fill(xCap - 2, y + 9, xCap + (4 * sCap + 2) + 2, y + 1 + 9, border);
        }
        g.fill(xCap, y - 11, xCap + (4 * sCap + 2), y - 1, fill);
        g.fill(xCap, y + 9, xCap + (4 * sCap + 2), y + 19, fill);

        int textColor = BubbleBackgrounds.textColorFor(colorRGB);
        g.drawString(this.font, text, cx - this.font.width(text) / 2, y, textColor, true);

        // rabicho + adesivo de canto
        g.drawString(this.font, "▼", cx - 3, y + 18, fill, true);
        if (!style.corner().isEmpty()) {
            g.drawString(this.font, Stickers.label(style.corner()), x0 + bw - 5, y - 12, 0xFFFFFFFF, true);
        }
    }

    /** Teclado: grade 8x6 de adesivos com hover + tooltip. */
    private void renderKeyboard(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < Stickers.COUNT; i++) {
            int cx = kbX + (i % KB_COLS) * KB_CELL;
            int cy = kbY + (i / KB_COLS) * KB_CELL;
            g.fill(cx, cy, cx + KB_CELL, cy + KB_CELL, 0xFF171A22);
            g.fill(cx, cy, cx + 1, cy + KB_CELL, 0xFF12141B);
            g.fill(cx + KB_CELL - 1, cy, cx + KB_CELL, cy + KB_CELL, 0xFF12141B);
            g.fill(cx, cy + KB_CELL - 1, cx + KB_CELL, cy + KB_CELL, 0xFF12141B);
            g.drawString(this.font, Stickers.label(Stickers.GLYPHS[i]), cx + 8, cy + 8, 0xFFFFFFFF, false);
        }
        if (selectedSticker != null) {
            int idx = glyphIndex(selectedSticker);
            if (idx >= 0) {
                int cx = kbX + (idx % KB_COLS) * KB_CELL;
                int cy = kbY + (idx / KB_COLS) * KB_CELL;
                outline(g, cx - 1, cy - 1, KB_CELL + 2, KB_CELL + 2, 0xFFFFFFFF);
            }
        }
        int over = stickerAt(mouseX, mouseY);
        if (over >= 0) {
            int cx = kbX + (over % KB_COLS) * KB_CELL;
            int cy = kbY + (over / KB_COLS) * KB_CELL;
            outline(g, cx, cy, KB_CELL, KB_CELL, 0xFFFFFFFF);
            g.renderTooltip(this.font, Component.literal(Stickers.NAMES[over]), mouseX, mouseY);
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

    private int glyphIndex(String glyph) {
        for (int i = 0; i < Stickers.COUNT; i++) {
            if (Stickers.GLYPHS[i].equals(glyph)) {
                return i;
            }
        }
        return -1;
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
        if (button == 0 && !showKeyboard && inBox(mx, my, svX, svY, SV_W, SV_H)) {
            draggingSv = true;
            updateSv(mx, my);
            return true;
        }
        if (button == 0 && !showKeyboard && inBox(mx, my, svX, hueY, SV_W, HUE_H)) {
            draggingHue = true;
            updateHue(mx);
            return true;
        }
        if (button == 0 && showKeyboard) {
            int idx = stickerAt(mx, my);
            if (idx >= 0) {
                draggingSticker = Stickers.GLYPHS[idx];
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (draggingSv) {
            updateSv(mx, my);
            return true;
        }
        if (draggingHue) {
            updateHue(mx);
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (draggingSticker != null) {
            // soltou sobre um alvo? aplica; senao fica "selecionado" (clique + destino)
            boolean dropped = false;
            for (int i = 0; i < 4; i++) {
                if (inBox(mx, my, slotX[i], slotY, slotW, 16)) {
                    setSlot(i, draggingSticker);
                    dropped = true;
                    break;
                }
            }
            if (!dropped) {
                selectedSticker = draggingSticker;
            }
            draggingSticker = null;
            rebuildWidgets();
            return true;
        }
        draggingSv = false;
        draggingHue = false;
        return super.mouseReleased(mx, my, button);
    }

    private void updateSv(double mx, double my) {
        sat = Mth.clamp((float) (mx - svX) / SV_W, 0.0F, 1.0F);
        val = 1.0F - Mth.clamp((float) (my - svY) / SV_H, 0.0F, 1.0F);
        applyPicker();
    }

    private void updateHue(double mx) {
        hue = Mth.clamp((float) (mx - svX) / SV_W, 0.0F, 1.0F);
        applyPicker();
    }

    /** Aplica a cor do seletor no alvo (dentro ou borda) em tempo real. */
    private void applyPicker() {
        int rgb = currentRgb();
        if (pickingBorder) {
            style = withBorder(rgb);
        } else {
            style = withColor(rgb);
        }
    }

    private boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // =====================================================================
    //  Salvar
    // =====================================================================

    private void saveAndClose() {
        PacketDistributor.sendToServer(new UpdateBubbleStylePayload(
                style.colorRGB(), style.borderRGB(), style.prefix(), style.mid(), style.suffix(),
                style.corner(), style.background(), style.durationSec()));
        if (modeChanged) {
            PacketDistributor.sendToServer(new SetBubbleModePayload(bubbleMode));
        }
        onClose();
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private int currentRgb() {
        return Mth.hsvToRgb(hue, sat, val) & 0xFFFFFF;
    }

    private Component targetLabel() {
        return pickingBorder
                ? Component.literal("▣ Cor da: BORDA")
                : Component.literal("✿ Cor de: DENTRO");
    }

    private Component durationLabel() {
        return Component.literal("⏱ Duração: " + style.durationSec() + "s");
    }

    private Component adesivosLabel() {
        return Component.empty()
                .append(Stickers.label(Stickers.GLYPHS[0]))
                .append(Component.literal(" Adesivos (" + Stickers.COUNT + ")"));
    }

    private Component modeLabel() {
        return this.bubbleMode
                ? Component.literal("☾ Modo balão: LIGADO").withStyle(ChatFormatting.GREEN)
                : Component.literal("☾ Modo balão: desligado");
    }

    private static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) {
                h = ((g - b) / d) % 6;
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h /= 6;
            if (h < 0) {
                h += 1;
            }
        }
        float s = max == 0 ? 0 : d / max;
        return new float[]{h, s, max};
    }

    private static String nextSticker(String current) {
        for (int i = 0; i < Stickers.COUNT; i++) {
            if (Stickers.GLYPHS[i].equals(current)) {
                return i == Stickers.COUNT - 1 ? "" : Stickers.GLYPHS[i + 1];
            }
        }
        return Stickers.GLYPHS[0];
    }

    private BubbleStyle withColor(int rgb) {
        return new BubbleStyle(rgb, style.borderRGB(), style.prefix(), style.mid(), style.suffix(), style.corner(), style.background(), style.durationSec());
    }

    private BubbleStyle withBorder(int border) {
        return new BubbleStyle(style.colorRGB(), border, style.prefix(), style.mid(), style.suffix(), style.corner(), style.background(), style.durationSec());
    }

    private BubbleStyle withPrefix(String prefix) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), prefix, style.mid(), style.suffix(), style.corner(), style.background(), style.durationSec());
    }

    private BubbleStyle withMid(String mid) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.prefix(), mid, style.suffix(), style.corner(), style.background(), style.durationSec());
    }

    private BubbleStyle withSuffix(String suffix) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.prefix(), style.mid(), suffix, style.corner(), style.background(), style.durationSec());
    }

    private BubbleStyle withCorner(String corner) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.prefix(), style.mid(), style.suffix(), corner, style.background(), style.durationSec());
    }

    private BubbleStyle withDuration(int secs) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.prefix(), style.mid(), style.suffix(), style.corner(), style.background(), secs);
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
