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
 * TELA DE PERSONALIZACAO DO BALAO (3.0.0):
 *
 * <ul>
 *   <li>O balao de VERDADE aparece na tela como preview — o que voce ve e o que fica.</li>
 *   <li>ADESIVO LIVRE: clique em "✦ Adesivos", escolha um emoji (ele gruda no
 *       mouse) e clique EM CIMA DO BALAO pra soltar ONDE QUISER. Clique de novo
 *       no adesivo no balao pra pegar de volta e reposicionar; clique direito
 *       no balao TIRA o adesivo.</li>
 *   <li>Cores: cartoes DENTRO e BORDA sempre visiveis — o seletor pinta o que
 *       estiver com moldura branca.</li>
 *   <li>Borda em 3 estados claros: Automatica → Personalizada → Sem.</li>
 * </ul>
 *
 * <p>NITIDEZA: fundo OPACO proprio — o desfoque do 1.21 nunca toca no painel.
 */
public class BubbleStyleScreen extends Screen {

    private static final int PANEL_W = 262;
    private static final int PANEL_H = 214;

    private static final int SV_W = 106;
    private static final int SV_H = 40;
    private static final int HUE_H = 10;

    // teclado de adesivos (8 colunas x 6 linhas)
    private static final int KB_COLS = 8;
    private static final int KB_ROWS = 6;
    private static final int KB_CELL = 28;

    /** Alvo do seletor: 0 = dentro, 1 = borda. */
    private int pickerTarget = 0;

    private float hue = 0.5F, sat = 1.0F, val = 1.0F;
    private BubbleStyle style = BubbleStyle.DEFAULT;
    private boolean bubbleMode = false;
    private boolean modeChanged = false;
    private boolean draggingSv = false;
    private boolean draggingHue = false;

    // adesivo solto na mao / teclado
    private String holdingSticker = null;
    private boolean showKeyboard = false;

    // areas
    private int px, py;
    private int svX, svY, hueY;
    private int kbX, kbY;
    private int pvX0, pvY0, pvX1, pvY1;   // caixa do preview (fundo escuro)

    // geometria da pilula do preview (atualizada a cada frame)
    private int pX0, pY0, pW, pH;
    private boolean hoverSticker = false;

    public BubbleStyleScreen(OpenBubbleStylePayload style) {
        super(Component.literal("Balão de Fala"));
        this.style = new BubbleStyle(style.colorRGB(), style.borderRGB(), style.prefix(),
                style.mid(), style.suffix(), style.corner(), style.stickerX(), style.stickerY(),
                style.background(), style.durationSec());
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
        svY = py + 114;
        hueY = svY + SV_H + 2;
        kbX = px + (PANEL_W - KB_COLS * KB_CELL) / 2;
        kbY = py + 18;
        pvX0 = px + 10;
        pvY0 = py + 16;
        pvX1 = px + PANEL_W - 10;
        pvY1 = py + 84;

        if (showKeyboard) {
            addRenderableWidget(Button.builder(Component.literal("✔ Pronto — arraste no balão!"), b -> {
                        showKeyboard = false;
                        rebuildWidgets();
                    }).bounds(px + (PANEL_W - 170) / 2, py + PANEL_H - 28, 170, 20).build());
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
                    .bounds(px + 12, py + PANEL_H - 26, 115, 20).build());
            addRenderableWidget(Button.builder(Component.literal("✖ Cancelar"), b -> onClose())
                    .bounds(px + PANEL_W - 127, py + PANEL_H - 26, 115, 20).build());
        }
    }

    // =====================================================================
    //  Estilo: mutadores
    // =====================================================================

    private void setSticker(String glyph, int x, int y) {
        style = new BubbleStyle(style.colorRGB(), style.borderRGB(), style.prefix(), style.mid(),
                style.suffix(), glyph, x, y, style.background(), style.durationSec());
    }

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
            g.drawCenteredString(this.font, "✦ Escolha o adesivo", this.width / 2, py + 5, 0xFFFFFFFF);
        } else {
            renderPicker(g);
            renderPreview(g, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (!showKeyboard) {
            String hint;
            if (holdingSticker != null) {
                hint = "Solte em cima do balão: clique onde quiser!";
            } else if (!style.corner().isEmpty()) {
                hint = "Clique no adesivo p/ reposicionar · clique direito TIRA";
            } else {
                hint = "✦ Adesivos → escolha → clique no balão!";
            }
            g.drawCenteredString(this.font, hint, this.width / 2, py + 87, holdingSticker != null
                    ? 0xFF9AE8B5 : 0xFF9AA3B5);
            if (holdingSticker != null) {
                g.drawString(this.font, Stickers.label(holdingSticker), mouseX - 4, mouseY - 12, 0xFFFFFFFF, false);
            }
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

    /**
     * PREVIEW: o balao de verdade (pílula arredondada + contorno + rabicho +
     * adesivo na posicao salva). A geometria da pilula fica gravada pra
     * converter a posicao do clique em % (x, y).
     */
    private void renderPreview(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(pvX0, pvY0, pvX1, pvY1, 0xFF171A22);
        outline(g, pvX0, pvY0, pvX1 - pvX0, pvY1 - pvY0, 0xFF12141B);

        int colorRGB = style.colorRGB() & 0xFFFFFF;
        int fill = BubbleBackgrounds.fillColorFor(colorRGB);
        int border = BubbleBackgrounds.borderColorFor(colorRGB, style.borderRGB());
        int textColor = BubbleBackgrounds.textColorFor(colorRGB);

        var text = Stickers.styled(
                (style.prefix().isEmpty() ? "" : style.prefix() + " ")
                        + "sua fala assim"
                        + (style.suffix().isEmpty() ? "" : " " + style.suffix()));
        int textW = this.font.width(text);
        pW = Math.max(32, textW + 14);
        pH = 16; // 1 linha
        pX0 = (pvX0 + pvX1) / 2 - pW / 2;
        pY0 = (pvY0 + pvY1) / 2 - pH / 2 - 3; // deixa espaco pro rabicho

        // contorno + recheio + rabicho (pontas arredondadas por linhas)
        if (border != 0) {
            fillPill(g, pX0 - 2, pY0 - 2, pW + 4, pH + 4, border);
            g.drawString(this.font, "▼", pvX0 + (pvX1 - pvX0) / 2 - 4, pY0 + pH - 1, border, false);
        }
        fillPill(g, pX0, pY0, pW, pH, fill);
        g.drawString(this.font, "▼", pvX0 + (pvX1 - pvX0) / 2 - 5, pY0 + pH, fill, false);
        g.drawString(this.font, text, pX0 + 7, pY0 + 3, textColor, true);

        // adesivo na posicao salva (brilho quando o mouse esta em cima)
        hoverSticker = false;
        if (!style.corner().isEmpty()) {
            int sx = pX0 + Math.round(style.stickerX() / 100.0F * pW);
            int sy = pY0 + Math.round(style.stickerY() / 100.0F * pH);
            int gx = sx - this.font.width(Stickers.label(style.corner())) / 2;
            g.drawString(this.font, Stickers.label(style.corner()), gx, sy - 4, 0xFFFFFFFF, true);
            if (Math.abs(mouseX - sx) <= 8 && Math.abs(mouseY - sy) <= 7 && holdingSticker == null) {
                hoverSticker = true;
                outline(g, gx - 2, sy - 6,
                        this.font.width(Stickers.label(style.corner())) + 4, 13, 0xFF9AE8B5);
            }
        }
        // preview do adesivo na mao (onde vai cair)
        if (holdingSticker != null && inBox(mouseX, mouseY, pX0 - 4, pY0 - 4, pW + 8, pH + 8)) {
            int sx = Mth.clamp(mouseX, pX0, pX0 + pW);
            int sy = Mth.clamp(mouseY, pY0, pY0 + pH);
            g.drawString(this.font, Stickers.label(holdingSticker),
                    sx - this.font.width(Stickers.label(holdingSticker)) / 2, sy - 4, 0xFFFFFFFF, true);
        }
        if (holdingSticker != null) {
            outline(g, pvX0, pvY0, pvX1 - pvX0, pvY1 - pvY0, 0xFF9AE8B5);
        }
    }

    /** Pílula arredondada por linhas (pontas de semicirculo, como no jogo). */
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

    /** Quadrado de saturacao/brilho + faixa de matiz + cartoes DENTRO/BORDA. */
    private void renderPicker(GuiGraphics g) {
        int cols = 26, rows = 10;
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
        drawTargetCard(g, svX, py + 96, "Dentro", BubbleBackgrounds.fillColorFor(style.colorRGB()), pickerTarget == 0);
        int bc = BubbleBackgrounds.borderColorFor(style.colorRGB(), style.borderRGB());
        drawTargetCard(g, svX + 54, py + 96, "Borda", bc != 0 ? bc : 0xFF171A22, pickerTarget == 1);

        // hex da cor atual
        g.drawString(this.font, String.format("#%06X", currentRgb()), svX + 112, py + 100, 0xFF8E96A8, false);
    }

    private void drawTargetCard(GuiGraphics g, int x, int y, String label, int color, boolean selected) {
        g.fill(x, y, x + 50, y + 14, 0xFF171A22);
        g.fill(x + 2, y + 2, x + 14, y + 12, color);
        outline(g, x + 2, y + 2, 12, 10, 0xFF000000);
        g.drawString(this.font, label, x + 18, y + 4, selected ? 0xFF9AE8B5 : 0xFF9AA3B5, false);
        outline(g, x, y, 50, 14, selected ? 0xFFFFFFFF : 0xFF12141B);
    }

    /** Teclado: grade 8x6 de adesivos; clique gruda o adesivo no mouse. */
    private void renderKeyboard(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < Stickers.COUNT; i++) {
            int cx = kbX + (i % KB_COLS) * KB_CELL;
            int cy = kbY + (i / KB_COLS) * KB_CELL;
            g.fill(cx, cy, cx + KB_CELL, cy + KB_CELL, 0xFF171A22);
            g.fill(cx, cy, cx + 1, cy + KB_CELL, 0xFF12141B);
            g.fill(cx + KB_CELL - 1, cy, cx + KB_CELL, cy + KB_CELL, 0xFF12141B);
            g.fill(cx, cy + KB_CELL - 1, cx + KB_CELL, cy + KB_CELL, 0xFF12141B);
            String glyph = Stickers.GLYPHS[i];
            g.drawString(this.font, Stickers.label(glyph),
                    cx + (KB_CELL - this.font.width(Stickers.label(glyph))) / 2,
                    cy + (KB_CELL - 8) / 2, 0xFFFFFFFF, false);
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
                holdingSticker = Stickers.GLYPHS[idx]; // gruda no mouse!
                showKeyboard = false;
                rebuildWidgets();
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        if (!showKeyboard && button == 0 && holdingSticker != null) {
            // SOLTOU: a posicao do clique vira a posicao do adesivo (%)
            int x = Mth.clamp((int) mx, pX0, pX0 + pW);
            int y = Mth.clamp((int) my, pY0, pY0 + pH);
            int percX = Math.round((x - pX0) * 100.0F / pW);
            int percY = Math.round((y - pY0) * 100.0F / pH);
            setSticker(holdingSticker, percX, percY);
            holdingSticker = null;
            return true;
        }
        if (!showKeyboard && button == 0 && hoverSticker) {
            // PEGOU DE VOLTA pra reposicionar
            holdingSticker = style.corner();
            setSticker("", style.stickerX(), style.stickerY());
            return true;
        }
        if (!showKeyboard && button == 1 && inBox(mx, my, pvX0, pvY0, pvX1 - pvX0, pvY1 - pvY0)) {
            // clique direito no balao TIRA o adesivo
            if (!style.corner().isEmpty()) {
                setSticker("", style.stickerX(), style.stickerY());
            }
            return true;
        }
        if (!showKeyboard && button == 0) {
            if (inBox(mx, my, svX, py + 96, 50, 14)) {
                pickerTarget = 0;
                loadPickerFrom(style.colorRGB());
                return true;
            }
            if (inBox(mx, my, svX + 54, py + 96, 50, 14)) {
                pickerTarget = 1;
                if (style.borderRGB() < 0) { // sem borda ou auto -> vira personalizada
                    style = withBorder(style.colorRGB());
                }
                loadPickerFrom(style.borderRGB());
                rebuildWidgets();
                return true;
            }
            if (inBox(mx, my, svX, svY, SV_W, SV_H)) {
                draggingSv = true;
                updateSv(mx, my);
                return true;
            }
            if (inBox(mx, my, svX, hueY, SV_W, HUE_H)) {
                draggingHue = true;
                updateHue(mx);
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
                style.corner(), style.stickerX(), style.stickerY(), style.background(), style.durationSec()));
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
        return new BubbleStyle(rgb, style.borderRGB(), style.prefix(), style.mid(), style.suffix(),
                style.corner(), style.stickerX(), style.stickerY(), style.background(), style.durationSec());
    }

    private BubbleStyle withBorder(int border) {
        return new BubbleStyle(style.colorRGB(), border, style.prefix(), style.mid(), style.suffix(),
                style.corner(), style.stickerX(), style.stickerY(), style.background(), style.durationSec());
    }

    private BubbleStyle withDuration(int secs) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.prefix(), style.mid(), style.suffix(),
                style.corner(), style.stickerX(), style.stickerY(), style.background(), secs);
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
