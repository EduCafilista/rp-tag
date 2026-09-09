package dev.rptag.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import dev.rptag.BubbleStyle;
import dev.rptag.OpenBubbleStylePayload;
import dev.rptag.RPTagMod;
import dev.rptag.SetBubbleModePayload;
import dev.rptag.UpdateBubbleStylePayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * TELA DE PERSONALIZACAO DO BALAO (2.5.0) — bonita e facil:
 *
 * <ul>
 *   <li>SELETOR DE COR de verdade: quadrado de brilho/saturacao + faixa de
 *       matiz — ARRASTA o mouse e escolhe QUALQUER cor</li>
 *   <li>Preview do balao ao vivo no topo</li>
 *   <li>Fundo (7 estilos), barra/borda (Auto + cores), emojis
 *       Antes/Meio/Depois e Modo balao em botoes grandes</li>
 *   <li>Salvar / Cancelar</li>
 * </ul>
 *
 * <p>Todo o desenho usa fills e widgets vanilla — nada que possa crashar.
 */
public class BubbleStyleScreen extends Screen {

    private static final String[] EMOJIS = {"", "✦", "★", "♥", "⚔", "☾", "✿", "♪", "⚡", "✧"};
    private static final String[] BG_NAMES = {"Translúcido", "Escuro", "Claro", "Gradiente", "Papel", "Noite", "Madeira"};
    private static final String[] BAR_NAMES = {"Automática", "Branco", "Ciano", "Verde-menta", "Dourado", "Rosa", "Lilás", "Sem barra"};
    private static final int[] BAR_VALUES = {
            BubbleStyle.AUTO_BORDER, 0xFFFFFF, 0x55FFFF, 0x55FF88, 0xFFD700, 0xFF7BAC, 0xC77DFF, BubbleStyle.NO_BORDER};

    // geometria do painel
    private static final int PANEL_W = 262;
    private static final int PANEL_H = 196;

    // seletor
    private static final int SV_W = 106;
    private static final int SV_H = 62;
    private static final int HUE_H = 10;

    private float hue = 0.5F, sat = 1.0F, val = 1.0F;
    private BubbleStyle style = BubbleStyle.DEFAULT;
    private boolean bubbleMode = false;
    private boolean modeChanged = false;
    private boolean draggingSv = false;
    private boolean draggingHue = false;

    // areas (preenchidas no init)
    private int px, py;       // canto do painel
    private int svX, svY;     // seletor SV
    private int hueY;         // faixa de matiz

    public BubbleStyleScreen(OpenBubbleStylePayload style) {
        super(Component.literal("Balão de Fala"));
        applyPayload(style);
        float[] hsb = rgbToHsv(style.colorRGB());
        this.hue = hsb[0];
        this.sat = hsb[1];
        this.val = hsb[2];
    }

    private void applyPayload(OpenBubbleStylePayload p) {
        this.style = new BubbleStyle(p.colorRGB(), p.borderRGB(), p.prefix(), p.mid(), p.suffix(), p.background());
    }

    @Override
    protected void init() {
        px = (this.width - PANEL_W) / 2;
        py = (this.height - PANEL_H) / 2;
        svX = px + 12;
        svY = py + 52;
        hueY = svY + SV_H + 6;

        // coluna da direita
        int bx = px + 12 + SV_W + 10;
        int bw = PANEL_W - 12 - (12 + SV_W + 10);
        addRenderableWidget(Button.builder(bgLabel(), b -> {
                    style = withBackground((style.background() + 1) % BG_NAMES.length);
                    b.setMessage(bgLabel());
                }).bounds(bx, svY, bw, 18).build());
        addRenderableWidget(Button.builder(barLabel(), b -> {
                    int idx = barIndex() + 1;
                    style = withBorder(BAR_VALUES[idx % BAR_VALUES.length]);
                    b.setMessage(barLabel());
                }).bounds(bx, svY + 20, bw, 18).build());
        int ew = (bw - 8) / 3;
        addRenderableWidget(Button.builder(emojiLabel("Antes", style.prefix()), b -> {
                    style = withPrefix(nextEmoji(style.prefix()));
                    b.setMessage(emojiLabel("Antes", style.prefix()));
                }).bounds(bx, svY + 40, ew, 18).build());
        addRenderableWidget(Button.builder(emojiLabel("Meio", style.mid()), b -> {
                    style = withMid(nextEmoji(style.mid()));
                    b.setMessage(emojiLabel("Meio", style.mid()));
                }).bounds(bx + ew + 4, svY + 40, ew, 18).build());
        addRenderableWidget(Button.builder(emojiLabel("Depois", style.suffix()), b -> {
                    style = withSuffix(nextEmoji(style.suffix()));
                    b.setMessage(emojiLabel("Depois", style.suffix()));
                }).bounds(bx + 2 * (ew + 4), svY + 40, ew, 18).build());
        addRenderableWidget(Button.builder(modeLabel(), b -> {
                    this.bubbleMode = !this.bubbleMode;
                    this.modeChanged = true;
                    b.setMessage(modeLabel());
                }).bounds(bx, svY + 60, bw, 18).build());

        // salvar / cancelar
        addRenderableWidget(Button.builder(Component.literal("✔ Salvar"), b -> saveAndClose())
                .bounds(px + 12, py + PANEL_H - 24, 115, 18).build());
        addRenderableWidget(Button.builder(Component.literal("✖ Cancelar"), b -> onClose())
                .bounds(px + PANEL_W - 127, py + PANEL_H - 24, 115, 18).build());
    }

    // =====================================================================
    //  Desenho
    // =====================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        renderPanel(g);
        renderPicker(g);
        renderPreview(g);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, "✦ Balão de Fala", this.width / 2, py + 8, 0xFFE8E8F2);
    }

    private void renderPanel(GuiGraphics g) {
        // sombra do painel + moldura + fundo
        g.fill(px + 3, py + 3, px + PANEL_W + 3, py + PANEL_H + 3, 0x66000000);
        g.fill(px, py, px + PANEL_W, py + PANEL_H, 0xFFF2F3F7);
        // moldura (4 faixas finas)
        g.fill(px, py, px + PANEL_W, py + 1, 0xFF8B90A3);
        g.fill(px, py + PANEL_H - 1, px + PANEL_W, py + PANEL_H, 0xFF8B90A3);
        g.fill(px, py, px + 1, py + PANEL_H, 0xFF8B90A3);
        g.fill(px + PANEL_W - 1, py, px + PANEL_W, py + PANEL_H, 0xFF8B90A3);
        // faixa do seletor (fundo destacado atras do quadrado)
        g.fill(svX - 1, svY - 1, svX + SV_W + 1, svY + SV_H + 1, 0xFF3A3F4C);
        g.fill(svX - 1, hueY - 1, svX + SV_W + 1, hueY + HUE_H + 1, 0xFF3A3F4C);
    }

    /** Quadrado de saturacao/brilho + faixa de matiz + cursor. */
    private void renderPicker(GuiGraphics g) {
        int cols = 30, rows = 20;
        float cw = SV_W / (float) cols, ch = SV_H / (float) rows;
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                int rgb = Mth.hsvToRgb(hue, (cx + 0.5F) / cols, 1.0F - (cy + 0.5F) / rows);
                g.fill(svX + (int) (cx * cw), svY + (int) (cy * ch),
                        svX + (int) ((cx + 1) * cw) + 1, svY + (int) ((cy + 1) * ch) + 1,
                        0xFF000000 | rgb);
            }
        }
        // cursor do quadrado
        int curX = svX + (int) (sat * SV_W) - 3;
        int curY = svY + (int) ((1 - val) * SV_H) - 3;
        outline(g, curX, curY, 7, 7, 0xFFFFFFFF);

        // faixa de matiz (arco-iris)
        int hcols = 30;
        float hw = SV_W / (float) hcols;
        for (int i = 0; i < hcols; i++) {
            int rgb = Mth.hsvToRgb((i + 0.5F) / hcols, 1.0F, 1.0F);
            g.fill(svX + (int) (i * hw), hueY, svX + (int) ((i + 1) * hw) + 1, hueY + HUE_H, 0xFF000000 | rgb);
        }
        int hx = svX + (int) (hue * SV_W) - 2;
        outline(g, hx, hueY - 1, 5, HUE_H + 2, 0xFFFFFFFF);

        // hex da cor
        int rgb = currentRgb();
        g.drawString(this.font, String.format("#%06X", rgb), svX, hueY + HUE_H + 8, 0xFF2A2E3A, false);
        g.fill(svX + 62, hueY + HUE_H + 7, svX + 74, hueY + HUE_H + 18, 0xFF000000 | rgb);
        outline(g, svX + 62, hueY + HUE_H + 7, 12, 11, 0xFF3A3F4C);
    }

    /** Preview do balao ao vivo (mesmo visual do mundo). */
    private void renderPreview(GuiGraphics g) {
        int background = style.background();
        int colorRGB = style.colorRGB() & 0xFFFFFF;
        String text = (style.prefix().isEmpty() ? "" : style.prefix() + " ")
                + "sua fala aparece assim"
                + (style.suffix().isEmpty() ? "" : " " + style.suffix());
        int pillW = Math.max(34, this.font.width(text) + 14);
        int sFull = Math.max(2, (pillW - 2) / 4);
        int cx = this.width / 2;
        int y = py + 26;

        int rowFill = background == BubbleBackgrounds.BG_GRADIENTE
                ? BubbleBackgrounds.gradientColorFor(colorRGB, 0.5F)
                : BubbleBackgrounds.bgColorFor(background, colorRGB);
        int bx = cx - (4 * sFull + 2) / 2;

        // sombra + cartao (1 linha, cantos chanfrados) + barra de destaque
        g.fill(bx + 2, y + 2, bx + 4 * (sFull - 2) + 4, y + 12, 0x5A000000);
        if ((rowFill >>> 24) != 0) {
            g.fill(bx + 4, y, bx + 4 * (sFull - 2) + 2, y + 10, rowFill);
        }
        int barColor = BubbleBackgrounds.borderColorFor(background, colorRGB, style.borderRGB());
        int accent = barColor != 0 ? barColor : BubbleBackgrounds.accentColorFor(background, colorRGB);
        if ((rowFill >>> 24) != 0) {
            g.fill(bx + 6, y, bx + 12, y + 10, accent);
        }

        // texto + rabicho
        int textColor = BubbleBackgrounds.textColorFor(background, colorRGB);
        g.drawString(this.font, text, cx - this.font.width(text) / 2, y + 2, textColor, true);
        g.drawString(this.font, "▼", cx - 3, y + 10, 0xFF000000 | (rowFill & 0xFFFFFF), true);
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    // =====================================================================
    //  Interacao do seletor (ARRASTAR o mouse!)
    // =====================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && inBox(mx, my, svX, svY, SV_W, SV_H)) {
            draggingSv = true;
            updateSv(mx, my);
            return true;
        }
        if (button == 0 && inBox(mx, my, svX, hueY, SV_W, HUE_H)) {
            draggingHue = true;
            updateHue(mx);
            return true;
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
    }

    private void updateHue(double mx) {
        hue = Mth.clamp((float) (mx - svX) / SV_W, 0.0F, 1.0F);
    }

    private boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // =====================================================================
    //  Salvar
    // =====================================================================

    private void saveAndClose() {
        PacketDistributor.sendToServer(new UpdateBubbleStylePayload(
                currentRgb(), style.borderRGB(), style.prefix(), style.mid(), style.suffix(), style.background()));
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

    private Component bgLabel() {
        return Component.literal("▤ Fundo: " + BG_NAMES[style.background()]);
    }

    private int barIndex() {
        for (int i = 0; i < BAR_VALUES.length; i++) {
            if (BAR_VALUES[i] == style.borderRGB()) {
                return i;
            }
        }
        return 0;
    }

    private Component barLabel() {
        return Component.literal("▣ Barra: " + BAR_NAMES[barIndex()]);
    }

    private Component emojiLabel(String pos, String current) {
        return Component.literal(pos + ": " + (current.isEmpty() ? "—" : current));
    }

    private Component modeLabel() {
        return this.bubbleMode
                ? Component.literal("☾ Modo balão: LIGADO").withStyle(ChatFormatting.GREEN)
                : Component.literal("☾ Modo balão: desligado");
    }

    private static String nextEmoji(String current) {
        for (int i = 0; i < EMOJIS.length; i++) {
            if (EMOJIS[i].equals(current)) {
                return EMOJIS[(i + 1) % EMOJIS.length];
            }
        }
        return EMOJIS[1];
    }

    private BubbleStyle withBackground(int bg) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.prefix(), style.mid(), style.suffix(), bg);
    }

    private BubbleStyle withBorder(int border) {
        return new BubbleStyle(style.colorRGB(), border, style.prefix(), style.mid(), style.suffix(), style.background());
    }

    private BubbleStyle withPrefix(String prefix) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), prefix, style.mid(), style.suffix(), style.background());
    }

    private BubbleStyle withMid(String mid) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.prefix(), mid, style.suffix(), style.background());
    }

    private BubbleStyle withSuffix(String suffix) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.prefix(), style.mid(), suffix, style.background());
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
