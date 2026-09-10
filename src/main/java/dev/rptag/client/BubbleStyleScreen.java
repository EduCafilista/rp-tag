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
 * TELA DE PERSONALIZACAO DO BALAO (3.6.0 — bonita, grande e a prova de bugs):
 *
 * <ul>
 *   <li>SIMULACAO DE CHAT AO VIVO com textos GRANDES (escala 1.5x): a linha
 *       "&lt;Voce&gt; sua fala assim" e o balao completo reagem a TUDO na hora.</li>
 *   <li>PALETAS DE 12 CORES por alvo (Dentro / Borda) — 1 clique pinta. Sem
 *       arrastar nada (o arrastar estava falhando em varios clientes).</li>
 *   <li>Cor da LETRA: Auto (contraste perfeito) + 8 cores de chat.</li>
 *   <li>Adesivos desenhados como IMAGEM CRUA de spritesheet POTENCIA-DE-2
 *       (64x64) — compativel com qualquer configuracao grafica.</li>
 *   <li>Layout 224px compacto que cabe em GUI scale 3 e telas pequenas.</li>
 * </ul>
 */
public class BubbleStyleScreen extends Screen {

    private static final int PANEL_W = 268;
    private static final int PANEL_H = 224;

    // teclado de adesivos (8 colunas x 6 linhas)
    private static final int KB_COLS = 8;
    private static final int KB_ROWS = 6;
    private static final int KB_CELL = 28;

    /** Spritesheet dos adesivos — 64x64 (POTENCIA DE 2, compativel com tudo). */
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

    /** Paleta do DENTRO (12 cores, arco-iris completo). */
    private static final int[] SW_IN = {0xF8F8F8, 0xF078B0, 0xE04848, 0xF09030,
            0xF0D048, 0x8CE048, 0x58C858, 0x50C8C0, 0x5878F0, 0xA858E0, 0x82828E, 0x1E1E24};
    /** Paleta da BORDA (12 cores que combinam com qualquer fundo). */
    private static final int[] SW_BD = {0x14161C, 0x2A2E3A, 0x5A5F6E, 0x5C3A1E,
            0x8A6A2E, 0xC8A028, 0x7A2430, 0x24305C, 0x2E4A8A, 0x4A2A6A, 0x245032, 0xE8E8F0};
    /** Cores da LETRA (paleta classica de chat). */
    private static final int[] TEXT_SW = {0xFFFFFF, 0x1E1E24, 0xFF5555, 0xFFAA00,
            0xFFFF55, 0x55FF55, 0x5555FF, 0xFF55FF};

    private BubbleStyle style = BubbleStyle.DEFAULT;
    private boolean bubbleMode = false;
    private boolean modeChanged = false;

    // adesivo solto na mao / teclado
    private String holdingSticker = null;
    private boolean showKeyboard = false;

    // areas
    private int px, py;
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
    }

    /** Cor da letra efetiva (auto = contraste do fundo). */
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
        pvY0 = py + 30;
        pvX1 = px + PANEL_W - 8;
        pvY1 = py + 92;
        kbX = px + (PANEL_W - KB_COLS * KB_CELL) / 2;
        kbY = py + 16;

        if (showKeyboard) {
            addRenderableWidget(Button.builder(Component.literal("✔ Pronto — clique no balão!"), b -> {
                        showKeyboard = false;
                        rebuildWidgets();
                    }).bounds(px + (PANEL_W - 180) / 2, py + PANEL_H - 26, 180, 20).build());
            return;
        }

        // --- linha 1: COR da borda ---
        addRenderableWidget(Button.builder(Component.literal("▣ Auto"), b -> {
                    style = withBorder(BubbleStyle.AUTO_BORDER);
                }).bounds(px + 8, py + 132, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▣ Minha cor"), b -> {
                    style = withBorder(SW_BD[0]);
                }).bounds(px + 93, py + 132, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▢ Sem borda"), b -> {
                    style = withBorder(BubbleStyle.NO_BORDER);
                }).bounds(px + 178, py + 132, 81, 16).build());

        // --- linha 2: ESTILO da moldura ---
        addRenderableWidget(Button.builder(Component.literal("▤ Clássica"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_CLASSIC);
                }).bounds(px + 8, py + 150, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▤ Cartum"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_CARTOON);
                }).bounds(px + 93, py + 150, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▤ Dupla"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_DOUBLE);
                }).bounds(px + 178, py + 150, 81, 16).build());

        // --- linha 3: adesivos + duracao ---
        addRenderableWidget(Button.builder(Component.literal("✦ Adesivos (48)"), b -> {
                    showKeyboard = true;
                    rebuildWidgets();
                }).bounds(px + 8, py + 168, 124, 16).build());
        addRenderableWidget(new AbstractSliderButton(px + 136, py + 168, 123, 16,
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

        // --- linha 4: modo, padrao, SALVAR (direita!) ---
        addRenderableWidget(Button.builder(modeLabel(), b -> {
                    this.bubbleMode = !this.bubbleMode;
                    this.modeChanged = true;
                    b.setMessage(modeLabel());
                }).bounds(px + 8, py + 188, 84, 17).build());
        addRenderableWidget(Button.builder(Component.literal("↺ Padrão"), b -> {
                    style = BubbleStyle.DEFAULT;
                    rebuildWidgets();
                }).bounds(px + 96, py + 188, 78, 17).build());
        addRenderableWidget(Button.builder(Component.literal("✔ Salvar"), b -> saveAndClose())
                .bounds(px + 178, py + 188, 81, 17).build());
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
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        renderPanel(g);
        if (showKeyboard) {
            renderKeyboard(g, mouseX, mouseY);
            bigText(g, "✦ Escolha o adesivo", px + 8, py + 3, 0xFFFFFFFF, 1.0F, true);
        } else {
            renderChatSim(g, mouseX, mouseY);
            renderPalettes(g, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (!showKeyboard) {
            // moldura verde no botao ativo (cor da borda + molde)
            int mode = style.borderRGB() == BubbleStyle.AUTO_BORDER ? 0
                    : (style.borderRGB() >= 0 ? 1 : 2);
            outline(g, px + 7, py + 131, 83, 18, 0xFF9AE8B5);
            outline(g, px + 7 + style.borderStyle() * 85, py + 149, 83, 18, 0xFF9AE8B5);

            String hint;
            if (holdingSticker != null) {
                hint = "Clique NO balão pra colar · direito/ESC = cancelar";
            } else if (!style.corner().isEmpty()) {
                hint = "Clique no adesivo pra mover · direito no balão TIRA";
            } else {
                hint = "✦ Adesivos → escolha → clique no balão!";
            }
            g.drawCenteredString(this.font, hint, px + PANEL_W / 2, pvY1 + 3,
                    holdingSticker != null ? 0xFFB5F0C8 : 0xFFB8C0D8);
            if (holdingSticker != null) {
                blitSticker(g, holdingSticker, mouseX - 8, mouseY - 16);
            }
        }
        // selo de versao (canto superior direito, FORA do conteudo)
        String ver = "v" + RPTagMod.VERSION;
        g.drawString(this.font, ver, px + PANEL_W - 6 - this.font.width(ver),
                py + 5, 0xFF8E96A8, false);
    }

    /** Painel com cabecalho bonito (titulo grande + faixa de acento). */
    private void renderPanel(GuiGraphics g) {
        g.fill(px + 4, py + 4, px + PANEL_W + 4, py + PANEL_H + 4, 0xAA000000);
        g.fill(px, py, px + PANEL_W, py + PANEL_H, 0xFF262C3B);
        // cabecalho
        g.fill(px, py, px + PANEL_W, py + 18, 0xFF1C2130);
        g.fill(px, py + 18, px + PANEL_W, py + 20, 0xFF3E4658);
        bigText(g, "✦ Balão de Fala", px + 8, py + 4, 0xFFFFD98A, 1.0F, true);
        // cantos arredondados de acento
        g.fill(px, py, px + 3, py + PANEL_H, 0xFFE8A83C);
        g.fill(px, py + PANEL_H - 3, px + PANEL_W, py + PANEL_H, 0xFF171B26);
    }

    /** Desenha o adesivo pela IMAGEM CRUA (blit) — 16x16. */
    private void blitSticker(GuiGraphics g, String glyph, int x, int y) {
        int idx = glyph.isEmpty() ? 0 : (glyph.charAt(0) & 0xFFFF) - 0xE000;
        if (idx < 0 || idx >= Stickers.COUNT) {
            idx = 0;
        }
        g.blit(SHEET, x, y, 16, 16, (idx % KB_COLS) * 8.0F, (idx / KB_COLS) * 8.0F, 8, 8, 64, 64);
    }

    /**
     * SIMULACAO DE CHAT AO VIVO (textos grandes, legiveis): linha do chat + o
     * balao completo + resumo hex no rodape.
     */
    private void renderChatSim(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(pvX0, pvY0, pvX1, pvY1, 0xE4141820);
        outline(g, pvX0, pvY0, pvX1 - pvX0, pvY1 - pvY0, 0xFF060810);

        int colorRGB = style.colorRGB() & 0xFFFFFF;
        int fill = BubbleBackgrounds.fillColorFor(colorRGB);
        int border = BubbleBackgrounds.borderColorFor(colorRGB, style.borderRGB());
        int textColor = effectiveTextColor();

        // --- linha do chat: <Voce> sua fala assim (GRANDE) ---
        float chatY = pvY0 + 4;
        bigText(g, "<Você>", pvX0 + 6, chatY, 0xFF000000 | colorRGB, 1.0F, true);
        int nameW = this.font.width("<Você> ");
        bigText(g, "sua fala assim", pvX0 + 6 + nameW, chatY, textColor, 1.0F, true);

        // --- o balao completo ---
        var text = Stickers.styled("sua fala assim");
        int textW = this.font.width(text);
        pW = Math.max(44, textW + 16);
        pH = 16;
        pX0 = (pvX0 + pvX1) / 2 - pW / 2;
        pY0 = pvY0 + 26;

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

        // adesivo (imagem crua) + mira
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

        // rodape: cores atuais em quadradinhos + hex
        int fy = pvY1 - 12;
        g.fill(pvX0 + 6, fy, pvX0 + 12, fy + 6, fill);
        outline(g, pvX0 + 6, fy, 6, 6, 0xFF060810);
        if (border != 0) {
            g.fill(pvX0 + 16, fy, pvX0 + 22, fy + 6, border);
            outline(g, pvX0 + 16, fy, 6, 6, 0xFF060810);
        }
        g.fill(pvX0 + 26, fy, pvX0 + 32, fy + 6, textColor);
        outline(g, pvX0 + 26, fy, 6, 6, 0xFF060810);
        bigText(g, String.format("✎ #%06X", colorRGB & 0xFFFFFF), pvX0 + 38, fy - 2,
                0xFFF0F3FF, 1.0F, true);
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

    // ---- paletas de 1 clique (x0 FIXO = facil de clicar e testar) ----
    private static final int SW_X = 76;   // inicio das cores, a partir de px
    private static final int SW_STRIDE = 15; // passo entre cores
    private static final int SW_W = 14;   // largura da cor

    /** Linha de paleta: rotulo grande + 12 cores de 1 clique. */
    private void renderSwatchRow(GuiGraphics g, int y, String label, int[] sw, int selected) {
        bigText(g, label, px + 8, y, 0xFFF0F3FF, 1.0F, true);
        for (int i = 0; i < sw.length; i++) {
            int x = px + SW_X + i * SW_STRIDE;
            g.fill(x, y, x + SW_W, y + 10, 0xFF000000 | sw[i]);
            boolean sel = selected == sw[i];
            outline(g, x, y, SW_W, 10, sel ? 0xFFFFFFFF : 0xFF060810);
        }
    }

    /** As 3 linhas de paleta: Dentro, Borda e Letra (Auto + 8). */
    private void renderPalettes(GuiGraphics g, int mouseX, int mouseY) {
        renderSwatchRow(g, py + 97, "✦ Dentro:", SW_IN, style.colorRGB());
        renderSwatchRow(g, py + 111, "▣ Borda:", SW_BD,
                style.borderRGB() >= 0 ? style.borderRGB() : -1);
        if (style.borderRGB() == BubbleStyle.NO_BORDER) {
            g.drawString(this.font, "(sem)", px + PANEL_W - 30, py + 112, 0xFF8E96A8, false);
        } else if (style.borderRGB() == BubbleStyle.AUTO_BORDER) {
            g.drawString(this.font, "(auto)", px + PANEL_W - 30, py + 112, 0xFF8E96A8, false);
        }
        // LETRA: rotulo + botao Auto + 8 cores de chat
        int ly = py + 125;
        bigText(g, "✎ Letra:", px + 8, ly, 0xFFF0F3FF, 1.0F, true);
        boolean auto = style.textColorRGB() == BubbleStyle.TEXT_AUTO;
        g.fill(px + SW_X, ly, px + SW_X + 30, ly + 10, auto ? 0xFF2E4A38 : 0xFF1C2130);
        outline(g, px + SW_X, ly, 30, 10, auto ? 0xFF9AE8B5 : 0xFF39415A);
        bigText(g, "Auto", px + SW_X + 4, ly, auto ? 0xFFB5F0C8 : 0xFF9AA3B5, 1.0F, false);
        for (int i = 0; i < TEXT_SW.length; i++) {
            int x = px + SW_X + 34 + i * SW_STRIDE;
            g.fill(x, ly, x + SW_W, ly + 10, 0xFF000000 | TEXT_SW[i]);
            outline(g, x, ly, SW_W, 10, style.textColorRGB() == TEXT_SW[i] ? 0xFFFFFFFF : 0xFF060810);
        }
    }

    /** Clique nas paletas: @return "D#"/"B#"/"L#" (indice) ou "A" (auto) ou "". */
    private String paletteHit(double mx, double my) {
        int idx = (int) ((mx - (px + SW_X)) / SW_STRIDE);
        if (my >= py + 97 && my < py + 107 && idx >= 0 && idx < SW_IN.length) {
            return "D" + idx;
        }
        if (my >= py + 111 && my < py + 121 && idx >= 0 && idx < SW_BD.length) {
            return "B" + idx;
        }
        int ly = py + 125;
        if (my >= ly && my < ly + 10) {
            if (mx >= px + SW_X && mx < px + SW_X + 30) {
                return "A"; // botao Auto
            }
            int li = (int) ((mx - (px + SW_X + 34)) / SW_STRIDE);
            if (li >= 0 && li < TEXT_SW.length) {
                return "L" + li;
            }
        }
        return "";
    }

    /** Teclado: grade 8x6 com as IMAGENS dos adesivos (blit 20x20) + chips. */
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
            return true; // fora do balao = cancela
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
            // paletas de 1 clique (Dentro / Borda / Letra+Auto)
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
