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
 * TELA DE PERSONALIZACAO DO BALAO (2.9.0 — simples a ponto de nao ter como errar):
 *
 * <ul>
 *   <li>4 DESTINOS sempre visiveis (Antes / Meio / Depois / Canto): clique no
 *       destino e depois no adesivo — ou ARRASTE o adesivo ate o destino.
 *       CLIQUE DIREITO no destino TIRA o adesivo.</li>
 *   <li>Cores SEM armadilha: cartoes DENTRO e BORDA sempre visiveis — o
 *       seletor edita o que estiver selecionado (verde).</li>
 *   <li>Borda em 3 estados claros: Automatica → Personalizada → Sem.</li>
 *   <li>Barra de duracao (2-20s) e Modo balao. Preview ao vivo.</li>
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
    private static final int KB_CELL = 18;

    /** Alvo do seletor: 0 = dentro, 1 = borda. */
    private int pickerTarget = 0;

    private float hue = 0.5F, sat = 1.0F, val = 1.0F;
    private BubbleStyle style = BubbleStyle.DEFAULT;
    private boolean bubbleMode = false;
    private boolean modeChanged = false;
    private boolean draggingSv = false;
    private boolean draggingHue = false;

    // adesivos
    private boolean showKeyboard = false;
    private int activeZone = 0; // 0=antes 1=meio 2=depois 3=canto
    private String draggingSticker = null;

    // areas
    private int px, py;
    private int svX, svY, hueY;
    private int kbX, kbY;
    private final int[] zoneX = new int[4];
    private int zoneY, zoneW;
    private int swatch1X, swatch2X, swatchY;

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
        svY = py + 84;
        hueY = svY + SV_H + 4;
        swatchY = hueY + HUE_H + 6;
        swatch1X = svX;
        swatch2X = svX + 54;
        kbX = px + (PANEL_W - KB_COLS * KB_CELL) / 2;
        kbY = py + 66;
        zoneW = 58;
        zoneY = py + 46;
        for (int i = 0; i < 4; i++) {
            zoneX[i] = px + (PANEL_W - 4 * zoneW - 3 * 6) / 2 + i * (zoneW + 6);
        }

        // ---- os 4 DESTINOS (sempre visiveis, nas duas paginas) ----
        for (int i = 0; i < 4; i++) {
            final int which = i;
            addRenderableWidget(Button.builder(zoneLabel(which), b -> {
                    }).bounds(zoneX[i], zoneY, zoneW, 16).build());
        }

        if (showKeyboard) {
            addRenderableWidget(Button.builder(Component.literal("✖ Tirar do destino selecionado"), b -> {
                        setSlot(activeZone, "");
                    }).bounds(px + 12, py + PANEL_H - 24, 150, 18).build());
            addRenderableWidget(Button.builder(Component.literal("✔ Voltar"), b -> {
                        showKeyboard = false;
                        rebuildWidgets();
                    }).bounds(px + PANEL_W - 100, py + PANEL_H - 24, 88, 18).build());
        } else {
            int bx = px + 128;
            int bw = PANEL_W - 128 - 12;

            addRenderableWidget(Button.builder(borderLabel(), b -> {
                        cycleBorder();
                        b.setMessage(borderLabel());
                    }).bounds(bx, svY, bw, 16).build());

            addRenderableWidget(Button.builder(Component.literal("✦ Adesivos (48)"), b -> {
                        showKeyboard = true;
                        rebuildWidgets();
                    }).bounds(bx, svY + 18, bw, 16).build());

            // BARRA DE DURACAO: arrasta e escolhe 2-20 segundos
            addRenderableWidget(new AbstractSliderButton(bx, svY + 36, bw, 16,
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
                    }).bounds(bx, svY + 54, bw, 16).build());

            addRenderableWidget(Button.builder(Component.literal("✔ Salvar"), b -> saveAndClose())
                    .bounds(px + 12, py + PANEL_H - 24, 115, 18).build());
            addRenderableWidget(Button.builder(Component.literal("✖ Cancelar"), b -> onClose())
                    .bounds(px + PANEL_W - 127, py + PANEL_H - 24, 115, 18).build());
        }
    }

    // =====================================================================
    //  Adesivos: destinos
    // =====================================================================

    private String slotValue(int which) {
        return switch (which) {
            case 0 -> style.prefix();
            case 1 -> style.mid();
            case 2 -> style.suffix();
            default -> style.corner();
        };
    }

    private void setSlot(int which, String glyph) {
        style = switch (which) {
            case 0 -> withPrefix(glyph);
            case 1 -> withMid(glyph);
            case 2 -> withSuffix(glyph);
            default -> withCorner(glyph);
        };
        rebuildWidgets(); // atualiza os rotulos dos destinos
    }

    private Component zoneLabel(int which) {
        String name = switch (which) {
            case 0 -> "Antes";
            case 1 -> "Meio";
            case 2 -> "Depois";
            default -> "Canto";
        };
        String cur = slotValue(which);
        Component c = Component.literal(name + " ")
                .append(cur.isEmpty() ? Component.literal("—") : Stickers.label(cur));
        if (which == activeZone) {
            final Component plain = c;
            c = Component.empty().append(plain).withStyle(ChatFormatting.GREEN);
        }
        return c;
    }

    // =====================================================================
    //  Borda em 3 estados (sem caminho sem saida)
    // =====================================================================

    private void cycleBorder() {
        if (style.borderRGB() == BubbleStyle.AUTO_BORDER) {
            style = withBorder(style.colorRGB()); // vira PERSONALIZADA com a cor atual
            pickerTarget = 1;
            loadPickerFrom(style.borderRGB());
        } else if (style.borderRGB() >= 0) {
            style = withBorder(BubbleStyle.NO_BORDER); // vira SEM
        } else {
            style = withBorder(BubbleStyle.AUTO_BORDER); // volta pra AUTOMATICA
            pickerTarget = 0;
            loadPickerFrom(style.colorRGB());
        }
    }

    private Component borderLabel() {
        if (style.borderRGB() == BubbleStyle.NO_BORDER) {
            return Component.literal("▣ Borda: SEM (clique p/ ligar)");
        }
        if (style.borderRGB() >= 0) {
            return Component.literal("▣ Borda: PERSONALIZADA (clique = auto)");
        }
        return Component.literal("▣ Borda: Automática (clique = sua cor)");
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
            renderPreview(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (showKeyboard) {
            g.drawCenteredString(this.font, "Clique no adesivo = vai pro destino selecionado",
                    this.width / 2, py + 8, 0xFFFFFFFF);
            g.drawCenteredString(this.font, "(ou ARRASTE ate o destino · clique direito tira)",
                    this.width / 2, py + 20, 0xFF9AA3B5);
            String zn = switch (activeZone) {
                case 0 -> "Antes";
                case 1 -> "Meio";
                case 2 -> "Depois";
                default -> "Canto";
            };
            g.drawCenteredString(this.font, "Destino selecionado: " + zn,
                    this.width / 2, py + 34, 0xFF9AE8B5);
        } else {
            g.drawCenteredString(this.font, "✦ Balão de Fala", this.width / 2, py + 8, 0xFFFFFFFF);
            g.drawCenteredString(this.font, "Clique no destino e escolha o adesivo (direito tira)",
                    this.width / 2, py + 22, 0xFF9AA3B5);
            g.drawCenteredString(this.font, "✎ pinte DENTRO e BORDA arrastando aqui embaixo",
                    svX + SV_W / 2, svY - 12, 0xFF9AA3B5);
        }
        // adesivo sendo arrastado: segue o mouse
        if (draggingSticker != null) {
            g.drawString(this.font, Stickers.label(draggingSticker), mouseX - 4, mouseY - 12, 0xFFFFFFFF, false);
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

    /** Quadrado de saturacao/brilho + faixa de matiz + cartoes DENTRO/BORDA. */
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

        // cartoes DENTRO / BORDA (o selecionado fica com moldura branca)
        drawTargetCard(g, swatch1X, swatchY, "Dentro", BubbleBackgrounds.fillColorFor(style.colorRGB()), pickerTarget == 0);
        int bc = BubbleBackgrounds.borderColorFor(style.colorRGB(), style.borderRGB());
        drawTargetCard(g, swatch2X, swatchY, "Borda", bc != 0 ? bc : 0xFF171A22, pickerTarget == 1);

        // hex da cor atual
        g.drawString(this.font, String.format("#%06X", currentRgb()), svX + 104, swatchY + 4, 0xFF8E96A8, false);
    }

    private void drawTargetCard(GuiGraphics g, int x, int y, String label, int color, boolean selected) {
        g.fill(x, y, x + 50, y + 14, 0xFF171A22);
        g.fill(x + 2, y + 2, x + 14, y + 12, color);
        outline(g, x + 2, y + 2, 12, 10, 0xFF000000);
        g.drawString(this.font, label, x + 18, y + 4, selected ? 0xFF9AE8B5 : 0xFF9AA3B5, false);
        outline(g, x, y, 50, 14, selected ? 0xFFFFFFFF : 0xFF12141B);
    }

    /** Preview do balao ao vivo (estilo quadrinhos, com canto). */
    private void renderPreview(GuiGraphics g) {
        int colorRGB = style.colorRGB() & 0xFFFFFF;
        var text = Stickers.styled(
                (style.prefix().isEmpty() ? "" : style.prefix() + " ")
                        + "sua fala assim"
                        + (style.suffix().isEmpty() ? "" : " " + style.suffix()));
        int cx = this.width / 2;
        int y = py + 18;

        g.fill(px + 12, py + 14, px + PANEL_W - 12, py + 42, 0xFF171A22);
        outline(g, px + 12, py + 14, PANEL_W - 24, 28, 0xFF12141B);

        int fill = BubbleBackgrounds.fillColorFor(colorRGB);
        int border = BubbleBackgrounds.borderColorFor(colorRGB, style.borderRGB());
        int pillW = Math.max(34, this.font.width(text) + 14);
        int sFull = Math.max(2, (pillW - 2) / 4);
        int bw = 4 * sFull + 2;
        int x0 = cx - bw / 2;

        g.fill(x0 + 2, y + 2, x0 + bw + 2, y + 10, 0x56000000);
        if (border != 0) {
            g.fill(x0 - 2, y - 1, x0 + bw + 2, y + 9, border);
        }
        g.fill(x0, y - 1, x0 + bw, y + 9, fill);

        int textColor = BubbleBackgrounds.textColorFor(colorRGB);
        g.drawString(this.font, text, cx - this.font.width(text) / 2, y, textColor, true);
        g.drawString(this.font, "▼", cx - 3, y + 8, fill, true);
        if (!style.corner().isEmpty()) {
            g.drawString(this.font, Stickers.label(style.corner()), x0 + bw - 4, y - 11, 0xFFFFFFFF, true);
        }
    }

    /** Teclado: grade 8x6 de adesivos; clique aplica no destino selecionado. */
    private void renderKeyboard(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < Stickers.COUNT; i++) {
            int cx = kbX + (i % KB_COLS) * KB_CELL;
            int cy = kbY + (i / KB_COLS) * KB_CELL;
            g.fill(cx, cy, cx + KB_CELL, cy + KB_CELL, 0xFF171A22);
            g.fill(cx, cy, cx + 1, cy + KB_CELL, 0xFF12141B);
            g.fill(cx + KB_CELL - 1, cy, cx + KB_CELL, cy + KB_CELL, 0xFF12141B);
            g.fill(cx, cy + KB_CELL - 1, cx + KB_CELL, cy + KB_CELL, 0xFF12141B);
            g.drawString(this.font, Stickers.label(Stickers.GLYPHS[i]), cx + 5, cy + 5, 0xFFFFFFFF, false);
        }
        int over = stickerAt(mouseX, mouseY);
        if (over >= 0) {
            int cx = kbX + (over % KB_COLS) * KB_CELL;
            int cy = kbY + (over / KB_COLS) * KB_CELL;
            outline(g, cx, cy, KB_CELL, KB_CELL, 0xFFFFFFFF);
            g.renderTooltip(this.font, Component.literal(Stickers.NAMES[over]), mouseX, mouseY);
        }
        // destinos selecionado em destaque no teclado
        int zx = zoneX[activeZone];
        outline(g, zx - 1, zoneY - 1, zoneW + 2, 18, 0xFF9AE8B5);
    }

    private int stickerAt(double mx, double my) {
        int cx = (int) ((mx - kbX) / KB_CELL);
        int cy = (int) ((my - kbY) / KB_CELL);
        if (cx < 0 || cx >= KB_COLS || cy < 0 || cy >= KB_ROWS) {
            return -1;
        }
        return cy * KB_COLS + cx;
    }

    private int zoneAt(double mx, double my) {
        for (int i = 0; i < 4; i++) {
            if (inBox(mx, my, zoneX[i], zoneY, zoneW, 16)) {
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
        // clique direito num destino TIRA o adesivo
        if (button == 1) {
            int z = zoneAt(mx, my);
            if (z >= 0) {
                setSlot(z, "");
                return true;
            }
        }
        if (button == 0) {
            // cartoes Dentro/Borda (pagina principal)
            if (!showKeyboard && inBox(mx, my, swatch1X, swatchY, 50, 14)) {
                pickerTarget = 0;
                loadPickerFrom(style.colorRGB());
                return true;
            }
            if (!showKeyboard && inBox(mx, my, swatch2X, swatchY, 50, 14)) {
                pickerTarget = 1;
                if (style.borderRGB() < 0) { // sem borda ou auto -> vira personalizada
                    style = withBorder(style.colorRGB());
                }
                loadPickerFrom(style.borderRGB());
                rebuildWidgets();
                return true;
            }
            // seletor
            if (!showKeyboard && inBox(mx, my, svX, svY, SV_W, SV_H)) {
                draggingSv = true;
                updateSv(mx, my);
                return true;
            }
            if (!showKeyboard && inBox(mx, my, svX, hueY, SV_W, HUE_H)) {
                draggingHue = true;
                updateHue(mx);
                return true;
            }
            // grade de adesivos: clique aplica no destino selecionado
            if (showKeyboard) {
                int idx = stickerAt(mx, my);
                if (idx >= 0) {
                    draggingSticker = Stickers.GLYPHS[idx];
                    return true;
                }
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
            // soltou sobre um destino? aplica nele; senao, aplica no destino selecionado
            int z = zoneAt(mx, my);
            setSlot(z >= 0 ? z : activeZone, draggingSticker);
            activeZone = z >= 0 ? z : activeZone;
            draggingSticker = null;
            return true;
        }
        draggingSv = false;
        draggingHue = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // setas trocam o destino selecionado (teclado tambem!)
        if (keyCode == 262 || keyCode == 263) { // direita/esquerda
            activeZone = (activeZone + (keyCode == 262 ? 1 : 3)) % 4;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    /** Aplica a cor do seletor no cartao selecionado (Dentro ou Borda). */
    private void applyPicker() {
        int rgb = currentRgb();
        if (pickerTarget == 1) {
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

    private Component durationLabel() {
        return Component.literal("⏱ Duração: " + style.durationSec() + "s");
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
