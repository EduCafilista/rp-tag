package dev.rptag.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import dev.rptag.BubbleStyle;
import dev.rptag.OpenBubbleStylePayload;
import dev.rptag.RPTagMod;
import dev.rptag.SetBubbleModePayload;
import dev.rptag.Stickers;
import dev.rptag.UpdateBubbleStylePayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * TELA DE PERSONALIZACAO DO BALAO (3.5.0 — SIMULACAO DE CHAT AO VIVO):
 *
 * <ul>
 *   <li>O painel de cima e um CHAT DE MENTIRA que reage a TUDO na hora:
 *       linha do chat (&lt;Voce&gt; fala) + balao completo com recheio, borda,
 *       molde, rabicho, adesivo e a COR DA LETRA escolhida.</li>
 *   <li>Cor da LETRA: Auto (branco/preto conforme o fundo) + 8 cores, com
 *       preview imediato na simulacao.</li>
 *   <li>2 seletores independentes (Dentro/Borda) + paletas de 1 clique +
 *       6 botoes explicitos (Auto/Minha/Sem · Clássica/Cartum/Dupla).</li>
 *   <li>Adesivos desenhados como imagem crua (blit) — sempre visíveis.</li>
 * </ul>
 */
public class BubbleStyleScreen extends Screen {

    private static final int PANEL_W = 262;
    private static final int PANEL_H = 240;

    private static final int SV_W = 118;
    private static final int SV_H = 22;
    private static final int HUE_H = 8;

    // teclado de adesivos (8 colunas x 6 linhas)
    private static final int KB_COLS = 8;
    private static final int KB_ROWS = 6;
    private static final int KB_CELL = 28;

    /** Spritesheet CRUA dos adesivos (8x8 por sprite) — desenhada com blit. */
    private static final ResourceLocation SHEET =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/font/stickers.png");

    /** Cor de identidade de cada adesivo (ponto no canto da celula). */
    private static final int[] CHIP = {0xE04848, 0xA048C8, 0x4878E0, 0x48B048,
            0xE0C048, 0x784848, 0xF0D048, 0xF0F0B0, 0x48A858, 0xE07030, 0xF0E048, 0xC8C8E0,
            0xF0C030, 0xE05888, 0x68B868, 0x58A8E0, 0x8878B8, 0x8878B8, 0xB0B0B8, 0x886848,
            0xE080C8, 0xF0E8D0, 0xE8C838, 0x68D8E8, 0x88B8E8, 0xE8A868, 0xE04838, 0xE04858,
            0xE8C838, 0xB8B8C8, 0x58B858, 0x68C868, 0x8898E8, 0xD85848, 0x8898D8, 0xE8D878,
            0x484848, 0xE8A8B8, 0xE8A838, 0xC88848, 0xD8B088, 0xB08858, 0xF0D048, 0x68A8D8,
            0xF0F0F0, 0xF0F0F0, 0xF0F0F0, 0x8898B8};

    /** Paletas rapidas: 1 clique pinta (sem arrastar nada!). */
    private static final int[] SW_IN = {0xF8F8F8, 0x1E1E24, 0xE04848, 0xF09030,
            0xF0D048, 0x58C858, 0x5878F0, 0xF078B0};
    private static final int[] SW_BD = {0x14161C, 0x5A5F6E, 0x5C3A1E, 0x7A2430,
            0x24305C, 0x4A2A6A, 0x245032, 0xC8A028};
    /** Cores da LETRA (paleta classica de chat) + modo AUTO. */
    private static final int[] TEXT_SW = {0xFFFFFF, 0x1E1E24, 0xFF5555, 0xFFAA00,
            0xFFFF55, 0x55FF55, 0x5555FF, 0xFF55FF};

    private float[] hsvIn = {0.5F, 1.0F, 1.0F}; // seletor do DENTRO
    private float[] hsvBd = {0.5F, 1.0F, 1.0F}; // seletor da BORDA

    private BubbleStyle style = BubbleStyle.DEFAULT;
    private boolean bubbleMode = false;
    private boolean modeChanged = false;
    private int dragging = 0; // 0 nada, 1 sv dentro, 2 hue dentro, 3 sv borda, 4 hue borda

    // adesivo solto na mao / teclado
    private String holdingSticker = null;
    private boolean showKeyboard = false;

    // areas
    private int px, py;
    private int svInX, svBdX, svY, hueY;
    private int kbX, kbY;
    private int pvX0, pvY0, pvX1, pvY1;

    // geometria da pilula da simulacao (atualizada a cada frame)
    private int pX0, pY0, pW, pH;
    private boolean hoverSticker = false;

    public BubbleStyleScreen(OpenBubbleStylePayload style) {
        super(Component.literal("Balão de Fala"));
        this.style = new BubbleStyle(style.colorRGB(), style.borderRGB(), style.borderStyle(),
                style.textColorRGB(), style.prefix(), style.mid(), style.suffix(), style.corner(),
                style.stickerX(), style.stickerY(), style.background(), style.durationSec());
        hsvIn = rgbToHsv(style.colorRGB());
        hsvBd = style.borderRGB() >= 0 ? rgbToHsv(style.borderRGB()) : hsvIn.clone();
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
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    private static int hsvToRgb(float[] hsv) {
        return Mth.hsvToRgb(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
    }

    /** Cor da letra efetiva (auto = contraste do fundo). */
    private int effectiveTextColor() {
        if (style.textColorRGB() == BubbleStyle.TEXT_AUTO) {
            return BubbleBackgrounds.textColorFor(style.colorRGB() & 0xFFFFFF);
        }
        return 0xFF000000 | style.textColorRGB();
    }

    @Override
    protected void init() {
        px = (this.width - PANEL_W) / 2;
        py = (this.height - PANEL_H) / 2;
        pvX0 = px + 10;
        pvY0 = py + 16;
        pvX1 = px + PANEL_W - 10;
        pvY1 = py + 76;
        svInX = px + 12;
        svBdX = px + 132;
        svY = py + 102;
        hueY = svY + SV_H + 2;
        kbX = px + (PANEL_W - KB_COLS * KB_CELL) / 2;
        kbY = py + 16;

        if (showKeyboard) {
            addRenderableWidget(Button.builder(Component.literal("✔ Pronto — clique no balão!"), b -> {
                        showKeyboard = false;
                        rebuildWidgets();
                    }).bounds(px + (PANEL_W - 180) / 2, py + PANEL_H - 28, 180, 20).build());
            return;
        }

        // --- linha A: COR da borda (3 botoes explicitos) ---
        addRenderableWidget(Button.builder(Component.literal("▣ Auto"), b -> {
                    style = withBorder(BubbleStyle.AUTO_BORDER);
                }).bounds(px + 12, py + 163, 75, 15).build());
        addRenderableWidget(Button.builder(Component.literal("▣ Minha cor"), b -> {
                    style = withBorder(hsvToRgb(hsvBd));
                }).bounds(px + 93, py + 163, 75, 15).build());
        addRenderableWidget(Button.builder(Component.literal("▢ Sem borda"), b -> {
                    style = withBorder(BubbleStyle.NO_BORDER);
                }).bounds(px + 174, py + 163, 75, 15).build());

        // --- linha B: ESTILO da moldura (3 botoes explicitos) ---
        addRenderableWidget(Button.builder(Component.literal("▤ Clássica"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_CLASSIC);
                }).bounds(px + 12, py + 180, 75, 15).build());
        addRenderableWidget(Button.builder(Component.literal("▤ Cartum"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_CARTOON);
                }).bounds(px + 93, py + 180, 75, 15).build());
        addRenderableWidget(Button.builder(Component.literal("▤ Dupla"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_DOUBLE);
                }).bounds(px + 174, py + 180, 75, 15).build());

        // --- linha C: adesivos + duracao ---
        addRenderableWidget(Button.builder(Component.literal("✦ Adesivos (48)"), b -> {
                    showKeyboard = true;
                    rebuildWidgets();
                }).bounds(px + 12, py + 197, 112, 15).build());
        addRenderableWidget(new AbstractSliderButton(px + 130, py + 197, 119, 15,
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

        // --- linha D: modo, padrao, SALVAR (direita!) ---
        addRenderableWidget(Button.builder(modeLabel(), b -> {
                    this.bubbleMode = !this.bubbleMode;
                    this.modeChanged = true;
                    b.setMessage(modeLabel());
                }).bounds(px + 12, py + 215, 82, 16).build());
        addRenderableWidget(Button.builder(Component.literal("↺ Padrão"), b -> {
                    style = BubbleStyle.DEFAULT;
                    hsvIn = rgbToHsv(style.colorRGB());
                    hsvBd = hsvIn.clone();
                    rebuildWidgets();
                }).bounds(px + 98, py + 215, 70, 16).build());
        addRenderableWidget(Button.builder(Component.literal("✔ Salvar"), b -> saveAndClose())
                .bounds(px + 174, py + 215, 75, 16).build());
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
            g.drawCenteredString(this.font, "✦ Escolha o adesivo", this.width / 2, py + 6, 0xFFFFFFFF);
        } else {
            renderChatSim(g, mouseX, mouseY);
            renderPickers(g);
            renderLetra(g);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (!showKeyboard) {
            // moldura verde no botao ativo (cor da borda + molde)
            int mode = style.borderRGB() == BubbleStyle.AUTO_BORDER ? 0
                    : (style.borderRGB() >= 0 ? 1 : 2);
            outline(g, px + 12 + mode * 81, py + 162, 77, 17, 0xFF9AE8B5);
            outline(g, px + 12 + style.borderStyle() * 81, py + 179, 77, 17, 0xFF9AE8B5);

            String hint;
            if (holdingSticker != null) {
                hint = "Clique NO balão pra colar · direito/ESC = cancelar";
            } else if (!style.corner().isEmpty()) {
                hint = "Clique no adesivo pra mover · direito no balão TIRA";
            } else {
                hint = "✦ Adesivos → escolha → clique no balão!";
            }
            g.drawCenteredString(this.font, hint, this.width / 2, py + 80,
                    holdingSticker != null ? 0xFFB5F0C8 : 0xFFE8ECFF);
            if (holdingSticker != null) {
                blitSticker(g, holdingSticker, mouseX - 8, mouseY - 16);
            }
        }
        // selo de versao (topo, fora do painel de simulacao)
        String ver = "v" + RPTagMod.VERSION;
        g.drawString(this.font, ver, px + PANEL_W - 6 - this.font.width(ver),
                py + 6, 0xFF8E96A8, false);
    }

    private void renderPanel(GuiGraphics g) {
        g.fill(px + 4, py + 4, px + PANEL_W + 4, py + PANEL_H + 4, 0xAA000000);
        g.fill(px, py, px + PANEL_W, py + PANEL_H, 0xFF2A3040);
        g.fill(px, py, px + PANEL_W, py + 2, 0xFF3E4658);
        g.fill(px, py, px + 2, py + PANEL_H, 0xFF3E4658);
        g.fill(px, py + PANEL_H - 2, px + PANEL_W, py + PANEL_H, 0xFF171B26);
        g.fill(px + PANEL_W - 2, py, px + PANEL_W, py + PANEL_H, 0xFF171B26);
    }

    /** Desenha o adesivo pela IMAGEM CRUA (blit) — 16x16, sempre funciona. */
    private void blitSticker(GuiGraphics g, String glyph, int x, int y) {
        int idx = glyph.isEmpty() ? 0 : (glyph.charAt(0) & 0xFFFF) - 0xE000;
        if (idx < 0 || idx >= Stickers.COUNT) {
            idx = 0;
        }
        g.blit(SHEET, x, y, 16, 16, (idx % KB_COLS) * 8.0F, (idx / KB_COLS) * 8.0F, 8, 8, 64, 48);
    }

    /**
     * SIMULACAO DE CHAT AO VIVO: painel escuro estilo chat do jogo com a linha
     * "&lt;Voce&gt; sua fala assim" (nome na cor do balao, fala na cor da LETRA)
     * e o balao completo (recheio + borda + molde + rabicho + adesivo).
     */
    private void renderChatSim(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(pvX0, pvY0, pvX1, pvY1, 0xBE12161F);
        outline(g, pvX0, pvY0, pvX1 - pvX0, pvY1 - pvY0, 0xFF10131C);

        int colorRGB = style.colorRGB() & 0xFFFFFF;
        int fill = BubbleBackgrounds.fillColorFor(colorRGB);
        int border = BubbleBackgrounds.borderColorFor(colorRGB, style.borderRGB());
        int textColor = effectiveTextColor();

        // --- linha do chat: <Voce> sua fala assim ---
        int chatY = pvY0 + 5;
        String name = "<Você> ";
        int nameW = this.font.width(name);
        g.drawString(this.font, name, pvX0 + 6, chatY, 0xFF000000 | colorRGB, true);
        g.drawString(this.font, "sua fala assim", pvX0 + 6 + nameW, chatY, textColor, true);

        // --- o balao completo ---
        var text = Stickers.styled("sua fala assim");
        int textW = this.font.width(text);
        pW = Math.max(44, textW + 16);
        pH = 16;
        pX0 = (pvX0 + pvX1) / 2 - pW / 2;
        pY0 = pvY0 + 24;

        int bs = style.borderStyle();
        if (border != 0) {
            if (bs == 1) { // Cartum: grossa
                fillPill(g, pX0 - 4, pY0 - 4, pW + 8, pH + 8, border);
            } else if (bs == 2) { // Dupla
                fillPill(g, pX0 - 1, pY0 - 1, pW + 2, pH + 2, border);
                fillPill(g, pX0 - 4, pY0 - 4, pW + 8, pH + 8, border);
            } else { // Classica
                fillPill(g, pX0 - 2, pY0 - 2, pW + 4, pH + 4, border);
            }
        }
        fillPill(g, pX0, pY0, pW, pH, fill);
        g.drawString(this.font, "▼", pvX0 + (pvX1 - pvX0) / 2 - 5, pY0 + pH + (bs == 1 ? 3 : 1), fill, false);
        g.drawString(this.font, text, pX0 + 8, pY0 + 3, textColor, true);

        // adesivo (imagem crua 18x18) + mira
        hoverSticker = false;
        int sx = pX0 + Math.round(style.stickerX() / 100.0F * pW);
        int sy = pY0 + Math.round(style.stickerY() / 100.0F * pH);
        if (!style.corner().isEmpty()) {
            blitSticker(g, style.corner(), sx - 9, sy - 9);
            if (holdingSticker == null && Math.abs(mouseX - sx) <= 10 && Math.abs(mouseY - sy) <= 10) {
                hoverSticker = true;
                reticle(g, sx, sy, 0xFFFFFFFF);
            }
        }
        if (holdingSticker != null) {
            int gx = Mth.clamp(mouseX, pX0, pX0 + pW);
            int gy = Mth.clamp(mouseY, pY0, pY0 + pH);
            g.setColor(1.0F, 1.0F, 1.0F, 0.55F);
            blitSticker(g, holdingSticker, gx - 9, gy - 9);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            reticle(g, gx, gy, 0xFFB5F0C8);
        }

        // rodape da simulacao: resumo de TUDO (dentro · borda · letra)
        String info = String.format("#%06X", colorRGB)
                + " · ▣ " + (border == 0 ? "sem" : (style.borderRGB() >= 0
                        ? String.format("#%06X", style.borderRGB() & 0xFFFFFF) : "auto"))
                + " · ✎ " + (style.textColorRGB() == BubbleStyle.TEXT_AUTO ? "auto"
                        : String.format("#%06X", style.textColorRGB() & 0xFFFFFF));
        g.drawString(this.font, info, pvX0 + 6, pvY1 - 11, 0xFFDDE3F0, true);

        // posicao do adesivo (topo direito)
        if (!style.corner().isEmpty()) {
            String pos = style.stickerX() + "," + style.stickerY();
            g.drawString(this.font, pos, pvX1 - 6 - this.font.width(pos), pvY0 + 4, 0xFFB5F0C8, false);
        }
    }

    /** Mira de posicionamento (duas linhas em cruz). */
    private void reticle(GuiGraphics g, int cx, int cy, int color) {
        g.fill(cx - 12, cy, cx + 12, cy + 1, color);
        g.fill(cx, cy - 10, cx + 1, cy + 10, color);
    }

    /** Pílula arredondada por linhas (pontas de semicirculo). */
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

    /** DOIS seletores independentes + paletas de 1 clique. */
    private void renderPickers(GuiGraphics g) {
        pickerColumn(g, svInX, "✦ Dentro", hsvToRgb(hsvIn));
        String bdLabel;
        int bdColor;
        if (style.borderRGB() == BubbleStyle.NO_BORDER) {
            bdLabel = "▣ Borda —";
            bdColor = 0xFF6A748E;
        } else if (style.borderRGB() == BubbleStyle.AUTO_BORDER) {
            bdLabel = "▣ Borda (auto)";
            bdColor = BubbleBackgrounds.borderColorFor(style.colorRGB(), BubbleStyle.AUTO_BORDER);
        } else {
            bdLabel = "▣ Borda";
            bdColor = hsvToRgb(hsvBd);
        }
        pickerColumn(g, svBdX, bdLabel, bdColor);
    }

    private void pickerColumn(GuiGraphics g, int x, String label, int rgb) {
        g.drawString(this.font, label + String.format(" #%06X", rgb & 0xFFFFFF), x, py + 91,
                0xFFE8ECFF, false);
        // quadrado saturacao/brilho
        float[] hsv = (x == svInX) ? hsvIn : hsvBd;
        int cols = 29, rows = 8;
        float cw = SV_W / (float) cols, ch = SV_H / (float) rows;
        for (int cx = 0; cx < cols; cx++) {
            for (int cy = 0; cy < rows; cy++) {
                int c = Mth.hsvToRgb(hsv[0], (cx + 0.5F) / cols, 1.0F - (cy + 0.5F) / rows);
                g.fill(x + (int) (cx * cw), svY + (int) (cy * ch),
                        x + (int) ((cx + 1) * cw) + 1, svY + (int) ((cy + 1) * ch) + 1,
                        0xFF000000 | c);
            }
        }
        int curX = x + (int) (hsv[1] * SV_W) - 3;
        int curY = svY + (int) ((1 - hsv[2]) * SV_H) - 3;
        outline(g, curX, curY, 7, 7, 0xFF000000);
        outline(g, curX + 1, curY + 1, 5, 5, 0xFFFFFFFF);
        // faixa de matiz
        for (int i = 0; i < cols; i++) {
            int c = Mth.hsvToRgb((i + 0.5F) / cols, 1.0F, 1.0F);
            g.fill(x + (int) (i * cw), hueY, x + (int) ((i + 1) * cw) + 1, hueY + HUE_H, 0xFF000000 | c);
        }
        int hx = x + (int) (hsv[0] * SV_W) - 2;
        outline(g, hx, hueY - 1, 5, HUE_H + 2, 0xFF000000);
        outline(g, hx + 1, hueY, 3, HUE_H, 0xFFFFFFFF);
        // paleta de 1 clique
        int[] sw = (x == svInX) ? SW_IN : SW_BD;
        for (int i = 0; i < sw.length; i++) {
            int sx = x + i * 14;
            g.fill(sx, py + 136, sx + 13, py + 144, 0xFF000000 | sw[i]);
        }
        outline(g, x - 1, py + 135, 8 * 14 + 1, 10, 0xFF10131C);
    }

    /** Cor da LETRA: botao Auto + 8 cores (com a selecionada em destaque). */
    private void renderLetra(GuiGraphics g) {
        int ly = py + 150;
        g.drawString(this.font, "✎ Letra:", px + 12, ly, 0xFFE8ECFF, false);
        int ax = px + 12 + 52;
        boolean auto = style.textColorRGB() == BubbleStyle.TEXT_AUTO;
        g.fill(ax, ly - 2, ax + 24, ly + 9, auto ? 0xFF2E4A38 : 0xFF1C2130);
        outline(g, ax, ly - 2, 24, 11, auto ? 0xFF9AE8B5 : 0xFF39415A);
        g.drawString(this.font, "Auto", ax + 3, ly, auto ? 0xFFB5F0C8 : 0xFF9AA3B5, false);
        for (int i = 0; i < TEXT_SW.length; i++) {
            int x = ax + 28 + i * 14;
            g.fill(x, ly - 2, x + 12, ly + 9, 0xFF000000 | TEXT_SW[i]);
            boolean sel = style.textColorRGB() == TEXT_SW[i];
            outline(g, x, ly - 2, 12, 11, sel ? 0xFFFFFFFF : 0xFF10131C);
        }
    }

    /** Teclado: grade 8x6 com as IMAGENS dos adesivos (blit 20x20). */
    private void renderKeyboard(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < Stickers.COUNT; i++) {
            int cx = kbX + (i % KB_COLS) * KB_CELL;
            int cy = kbY + (i / KB_COLS) * KB_CELL;
            g.fill(cx, cy, cx + KB_CELL, cy + KB_CELL, 0xFF242B3D);
            g.fill(cx, cy, cx + 1, cy + KB_CELL, 0xFF39415A);
            g.fill(cx, cy, cx + KB_CELL, cy + 1, 0xFF39415A);
            g.fill(cx, cy + KB_CELL - 1, cx + KB_CELL, cy + KB_CELL, 0xFF171B26);
            g.fill(cx + KB_CELL - 1, cy, cx + KB_CELL, cy + KB_CELL, 0xFF171B26);
            blitSticker(g, Stickers.GLYPHS[i], cx + 4, cy + 4);
            g.fill(cx + KB_CELL - 6, cy + KB_CELL - 6, cx + KB_CELL - 3, cy + KB_CELL - 3,
                    0xFF000000 | CHIP[i]);
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
            String glyph = holdingSticker;
            holdingSticker = null;
            if (inBox(mx, my, pvX0, pvY0, pvX1 - pvX0, pvY1 - pvY0)) {
                int x = Mth.clamp((int) mx, pX0, pX0 + pW);
                int y = Mth.clamp((int) my, pY0, pY0 + pH);
                setSticker(glyph, Math.round((x - pX0) * 100.0F / pW), Math.round((y - pY0) * 100.0F / pH));
            }
            return true; // fora do balao = cancela (nao come cliques!)
        }
        if (!showKeyboard && button == 0 && hoverSticker) {
            holdingSticker = style.corner(); // pegou de volta
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
            // cor da LETRA (Auto + paleta)
            String t = letraAt(mx, my);
            if (!t.isEmpty()) {
                if (t.equals("A")) {
                    style = withText(BubbleStyle.TEXT_AUTO);
                } else {
                    style = withText(TEXT_SW[Integer.parseInt(t)]);
                }
                return true;
            }
            // paletas de 1 clique (Dentro/Borda)
            String sw = swatchAt(mx, my);
            if (!sw.isEmpty()) {
                if (sw.equals("D")) {
                    int c = SW_IN[(int) ((mx - svInX) / 14)];
                    hsvIn = rgbToHsv(c);
                    style = withColor(c);
                } else {
                    int c = SW_BD[(int) ((mx - svBdX) / 14)];
                    hsvBd = rgbToHsv(c);
                    style = withBorder(c); // paleta da borda = PERSONALIZADA
                }
                return true;
            }
            if (inBox(mx, my, svInX, svY, SV_W, SV_H)) {
                dragging = 1;
                updateSv(hsvIn, svInX, mx, my);
                style = withColor(hsvToRgb(hsvIn));
                return true;
            }
            if (inBox(mx, my, svInX, hueY, SV_W, HUE_H)) {
                dragging = 2;
                updateHue(hsvIn, svInX, mx);
                style = withColor(hsvToRgb(hsvIn));
                return true;
            }
            if (inBox(mx, my, svBdX, svY, SV_W, SV_H)) {
                dragging = 3;
                updateSv(hsvBd, svBdX, mx, my);
                style = withBorder(hsvToRgb(hsvBd)); // mexeu = vira PERSONALIZADA
                return true;
            }
            if (inBox(mx, my, svBdX, hueY, SV_W, HUE_H)) {
                dragging = 4;
                updateHue(hsvBd, svBdX, mx);
                style = withBorder(hsvToRgb(hsvBd));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** @return "A" (auto), "0".."7" (paleta) ou "" se nao clicou na linha da letra. */
    private String letraAt(double mx, double my) {
        int ly = py + 150;
        if (my < ly - 2 || my >= ly + 9) {
            return "";
        }
        int ax = px + 12 + 52;
        if (mx >= ax && mx < ax + 24) {
            return "A";
        }
        for (int i = 0; i < TEXT_SW.length; i++) {
            int x = ax + 28 + i * 14;
            if (mx >= x && mx < x + 12) {
                return String.valueOf(i);
            }
        }
        return "";
    }

    /** @return "D" ou "B" se o clique foi numa paleta. */
    private String swatchAt(double mx, double my) {
        if (my >= py + 136 && my < py + 144) {
            for (int i = 0; i < 8; i++) {
                if (mx >= svInX + i * 14 && mx < svInX + i * 14 + 13) {
                    return "D";
                }
                if (mx >= svBdX + i * 14 && mx < svBdX + i * 14 + 13) {
                    return "B";
                }
            }
        }
        return "";
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (dragging == 1) {
            updateSv(hsvIn, svInX, mx, my);
            style = withColor(hsvToRgb(hsvIn));
            return true;
        }
        if (dragging == 2) {
            updateHue(hsvIn, svInX, mx);
            style = withColor(hsvToRgb(hsvIn));
            return true;
        }
        if (dragging == 3) {
            updateSv(hsvBd, svBdX, mx, my);
            style = withBorder(hsvToRgb(hsvBd));
            return true;
        }
        if (dragging == 4) {
            updateHue(hsvBd, svBdX, mx);
            style = withBorder(hsvToRgb(hsvBd));
            return true;
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = 0;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            if (holdingSticker != null) {
                holdingSticker = null;
                return true;
            }
            saveAndClose(); // ESC SALVA
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateSv(float[] hsv, int colX, double mx, double my) {
        hsv[1] = Mth.clamp((float) (mx - colX) / SV_W, 0.0F, 1.0F);
        hsv[2] = 1.0F - Mth.clamp((float) (my - svY) / SV_H, 0.0F, 1.0F);
    }

    private void updateHue(float[] hsv, int colX, double mx) {
        hsv[0] = Mth.clamp((float) (mx - colX) / SV_W, 0.0F, 1.0F);
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
