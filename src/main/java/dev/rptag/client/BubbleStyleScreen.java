package dev.rptag.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;

import dev.rptag.BubbleStyle;
import dev.rptag.OpenBubbleStylePayload;
import dev.rptag.RPTagMod;
import dev.rptag.SetBubbleModePayload;
import dev.rptag.Stickers;
import dev.rptag.UpdateBubbleStylePayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * TELA DE PERSONALIZACAO DO BALAO (3.8.0 — 100% TEXTO + BOTAO):
 *
 * <p>TODO elemento essencial e desenhado com os 2 UNICOS metodos que
 * funcionaram em TODOS os clientes testados: drawString (como a tag "● ʀᴘ")
 * e Buttons vanilla. Nenhuma cor/paleta/adesivo depende de fills ou texturas.
 *
 * <ul>
 *   <li>Paletas = fileiras de "●" coloridos (1 clique pinta; "▼" marca o ato).</li>
 *   <li>SIMULACAO de chat + balao feita com glifos "▣" (borda) e texto real.</li>
 *   <li>Adesivos = "●" na cor de identidade + numero; NOME no tooltip.</li>
 * </ul>
 */
public class BubbleStyleScreen extends Screen {

    private static final int PANEL_W = 268;
    private static final int PANEL_H = 236;

    // linhas verticais (a partir de py) — UMA fonte de verdade
    private static final int ROW_HINT = 22;
    private static final int ROW_CHAT = 34;
    private static final int ROW_BUBBLE = 48;
    private static final int ROW_SUM = 100;
    private static final int ROW_DENTRO = 116;
    private static final int ROW_BORDA = 132;
    private static final int ROW_LETRA = 148;
    private static final int ROW_BTN1 = 162;
    private static final int ROW_BTN2 = 178;
    private static final int ROW_BTN3 = 194;

    // paletas desenhadas como texto "● "
    private static final int SW_X = 64;

    // teclado
    private static final int KB_COLS = 8;
    private static final int KB_ROWS = 6;
    private static final int KB_CELL = 28;

    /** Cores de identidade dos 48 adesivos. */
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

    private String holdingSticker = null;
    private boolean showKeyboard = false;

    private int px, py;
    private int kbX, kbY;
    private int swSlotW; // largura de um "● " na paleta (calculado no init)

    // geometria do balao-texto da simulacao (pra posicionar o adesivo)
    private int simX0, simY, simCharW, simChars, simLineH = 10;
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

    /**
     * Desenha o adesivo com o MESMO metodo do balao no mundo
     * (font.drawInBatch + RenderType.text da fonte de bitmap) — o unico
     * caminho PROVADO de mostrar a arte do emoji neste projeto. Escala 2x.
     */
    private void drawGlyph2x(GuiGraphics g, String glyph, float x, float y) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(2.0F, 2.0F, 1.0F);
        Font font = mc.font;
        font.drawInBatch(Stickers.seq(glyph), 0.0F, 0.0F, 0xFFFFFFFF, false,
                pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
        buffers.endBatch();
    }

    /** Componente "● " na cor pedida (o caminho PROVADO de mostrar cor). */
    private static MutableComponent dot(int rgb) {
        return Component.literal("● ").withStyle(s -> s.withColor(TextColor.fromRgb(rgb & 0xFFFFFF)));
    }

    /** Fileira de paleta: "Rótulo  ● ● ● ..." + "▼" no selecionado. */
    private void drawSwatchRow(GuiGraphics g, int y, String label, int[] sw, int selected) {
        g.drawString(this.font, label, px + 8, y + 1, 0xFFFFD98A, true);
        MutableComponent row = Component.empty();
        for (int c : sw) {
            row.append(dot(c));
        }
        int x0 = px + SW_X;
        g.drawString(this.font, row, x0, y, 0xFFFFFFFF, true);
        if (selected >= 0) {
            int i = indexOf(sw, selected);
            if (i >= 0) {
                g.drawString(this.font, "▼", x0 + i * swSlotW + 3, y - 8, 0xFFFFFFFF, false);
            }
        }
    }

    private static int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == v) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void init() {
        px = Math.max(2, (this.width - PANEL_W) / 2);
        py = Math.max(2, (this.height - PANEL_H) / 2);
        kbX = px + (PANEL_W - KB_COLS * KB_CELL) / 2;
        kbY = py + 24;
        swSlotW = this.font.width("● ");

        if (showKeyboard) {
            addRenderableWidget(Button.builder(Component.literal("✔ Pronto — clique no balão!"), b -> {
                        showKeyboard = false;
                        rebuildWidgets();
                    }).bounds(px + (PANEL_W - 180) / 2, py + ROW_BTN3, 180, 20).build());
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
                }).bounds(px + 8, py + ROW_BTN3, 124, 18).build());
        addRenderableWidget(new AbstractSliderButton(px + 136, py + ROW_BTN3, 123, 18,
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

        int row4 = py + ROW_BTN3 + 18;
        addRenderableWidget(Button.builder(modeLabel(), b -> {
                    this.bubbleMode = !this.bubbleMode;
                    this.modeChanged = true;
                    b.setMessage(modeLabel());
                }).bounds(px + 8, row4, 84, 16).build());
        addRenderableWidget(Button.builder(Component.literal("↺ Padrão"), b -> {
                    style = BubbleStyle.DEFAULT;
                    rebuildWidgets();
                }).bounds(px + 96, row4, 78, 16).build());
        addRenderableWidget(Button.builder(Component.literal("✔ Salvar"), b -> saveAndClose())
                .bounds(px + 178, row4, 81, 16).build());
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
        // moldura do painel (texto-art: barras ▄/▀ se fills nao renderizarem, o
        // titulo e o conteudo ainda guiam) — painel em fill simples:
        g.fill(px + 3, py + 3, px + PANEL_W + 3, py + PANEL_H + 3, 0x99000000);
        g.fill(px, py, px + PANEL_W, py + PANEL_H, 0xFF262C3B);
        g.fill(px, py, px + PANEL_W, py + 16, 0xFF1C2130);
        g.fill(px, py, px + 3, py + PANEL_H, 0xFFE8A83C);
        g.drawString(this.font, "✦ Balão de Fala", px + 8, py + 4, 0xFFFFD98A, true);
        String ver = "v" + RPTagMod.VERSION;
        g.drawString(this.font, ver, px + PANEL_W - 6 - this.font.width(ver), py + 4, 0xFF9AA4BC, false);

        // adesivo na mao segue o mouse (a arte de verdade, 2x)
        if (holdingSticker != null) {
            drawGlyph2x(g, Stickers.GLYPHS[Integer.parseInt(holdingSticker)],
                    mouseX - 8, mouseY - 18);
            outline(g, mouseX - 9, mouseY - 19, 18, 18, 0xFFFFFFFF);
        }

        if (showKeyboard) {
            renderKeyboard(g, mouseX, mouseY);
        } else {
            renderSim(g, mouseX, mouseY);
            renderPalettes(g);
        }
        super.render(g, mouseX, mouseY, partialTick);

        if (!showKeyboard) {
            int mode = style.borderRGB() == BubbleStyle.AUTO_BORDER ? 0
                    : (style.borderRGB() >= 0 ? 1 : 2);
            outline(g, px + 7, py + ROW_BTN1 - 1, 83, 18, 0xFF9AE8B5);
            outline(g, px + 7 + style.borderStyle() * 85, py + ROW_BTN2 - 1, 83, 18, 0xFF9AE8B5);
        }
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** SIMULACAO: linha do chat + balao de glifos "▣" (a prova de cliente). */
    private void renderSim(GuiGraphics g, int mouseX, int mouseY) {
        int colorRGB = style.colorRGB() & 0xFFFFFF;
        int fill = BubbleBackgrounds.fillColorFor(colorRGB);
        int border = BubbleBackgrounds.borderColorFor(colorRGB, style.borderRGB());
        int textColor = effectiveTextColor();

        // --- linha do chat (igual ao chat real: nome na cor do balao) ---
        int chatY = py + ROW_CHAT;
        g.drawString(this.font, "<Você> ", px + 10, chatY, 0xFF000000 | colorRGB, true);
        g.drawString(this.font, "sua fala assim", px + 10 + this.font.width("<Você> "),
                chatY, textColor, true);

        // --- balao de glifos: topo ▣, texto, base ▣, rabicho ▼ ---
        simCharW = this.font.width("▣");
        int innerW = Math.max(this.font.width("sua fala assim") + 6, 8 * simCharW);
        simChars = (innerW + simCharW - 1) / simCharW;
        int bx = px + (PANEL_W - simChars * simCharW) / 2;
        simX0 = bx;
        simY = py + ROW_BUBBLE;

        int ring = border != 0 ? border : fill;
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < simChars; i++) {
            bar.append('▣');
        }
        String barStr = bar.toString();
        g.drawString(this.font, barStr, bx, simY, ring, true);
        g.drawString(this.font, "▣", bx, simY + simLineH, ring, true);
        g.drawString(this.font, "sua fala assim", bx + simCharW + 3, simY + simLineH, textColor, true);
        g.drawString(this.font, "▣", bx + simChars * simCharW - simCharW, simY + simLineH, ring, true);
        g.drawString(this.font, barStr, bx, simY + simLineH * 2, ring, true);
        g.drawString(this.font, "▼", bx + simChars * simCharW / 2 - 3, simY + simLineH * 2 + 8, ring, true);

        // adesivo: bolinha na cor de identidade, na posicao (%) — com hover
        hoverSticker = false;
        if (!style.corner().isEmpty()) {
            int idx = (style.corner().charAt(0) & 0xFFFF) - 0xE000;
            if (idx < 0 || idx >= Stickers.COUNT) {
                idx = 0;
            }
            int sx = bx + Math.round(style.stickerX() / 100.0F * simChars * simCharW);
            int sy = simY + Math.round(style.stickerY() / 100.0F * (simLineH * 2));
            drawGlyph2x(g, Stickers.GLYPHS[idx], sx - 8, sy - 8);
            if (holdingSticker == null && Math.abs(mouseX - sx) <= 8 && Math.abs(mouseY - sy) <= 6) {
                hoverSticker = true;
                g.drawString(this.font, "+", sx - 2, sy - 9, 0xFFFFFFFF, false);
            }
        }
        if (holdingSticker != null) {
            int hi = Integer.parseInt(holdingSticker);
            int gx = Mth.clamp(mouseX, bx, bx + simChars * simCharW);
            int gy = Mth.clamp(mouseY, simY, simY + simLineH * 2);
            g.setColor(1.0F, 1.0F, 1.0F, 0.6F);
            drawGlyph2x(g, Stickers.GLYPHS[hi], gx - 8, gy - 8);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            outline(g, gx - 9, gy - 9, 18, 18, 0xFFB5F0C8);
        }

        // rodape: resumo com bolinhas coloridas
        int fy = py + ROW_SUM;
        int fx = px + 10;
        g.drawString(this.font, dot(fill), fx, fy, 0xFFFFFFFF, true);
        g.drawString(this.font, String.format("#%06X", colorRGB), fx + swSlotW, fy, 0xFFE8ECFF, true);
        if (border != 0) {
            g.drawString(this.font, dot(border), fx + 72, fy, 0xFFFFFFFF, true);
        } else {
            g.drawString(this.font, "◌", fx + 72, fy, 0xFF9AA4BC, false);
        }
        g.drawString(this.font, style.borderRGB() >= 0
                ? String.format("#%06X", style.borderRGB())
                : (style.borderRGB() == BubbleStyle.NO_BORDER ? "sem" : "auto"),
                fx + 72 + swSlotW, fy, 0xFFE8ECFF, true);
        g.drawString(this.font, dot(effectiveTextColor()), fx + 150, fy, 0xFFFFFFFF, true);
        g.drawString(this.font, style.textColorRGB() == BubbleStyle.TEXT_AUTO ? "auto"
                : String.format("#%06X", style.textColorRGB()), fx + 150 + swSlotW, fy, 0xFFE8ECFF, true);
    }

    private void renderPalettes(GuiGraphics g) {
        drawSwatchRow(g, py + ROW_DENTRO, "✦ Dentro:", SW_IN, style.colorRGB());
        drawSwatchRow(g, py + ROW_BORDA, "▣ Borda:", SW_BD,
                style.borderRGB() >= 0 ? style.borderRGB() : -1);
        String note = style.borderRGB() == BubbleStyle.NO_BORDER ? "sem"
                : (style.borderRGB() == BubbleStyle.AUTO_BORDER ? "auto" : "");
        if (!note.isEmpty()) {
            g.drawString(this.font, "(" + note + ")", px + SW_X + 12 * swSlotW + 2,
                    py + ROW_BORDA + 1, 0xFF9AA4BC, false);
        }
        // LETRA: "Auto" + 8 cores
        int ly = py + ROW_LETRA;
        g.drawString(this.font, "✎ Letra:", px + 8, ly + 1, 0xFFFFD98A, true);
        boolean auto = style.textColorRGB() == BubbleStyle.TEXT_AUTO;
        g.drawString(this.font, "[Auto]", px + SW_X, ly + (auto ? -1 : 1),
                auto ? 0xFFB5F0C8 : 0xFF9AA3B5, true);
        if (auto) {
            g.drawString(this.font, "▼", px + SW_X + 12, ly - 9, 0xFFB5F0C8, false);
        }
        MutableComponent row = Component.empty();
        for (int c : TEXT_SW) {
            row.append(dot(c));
        }
        g.drawString(this.font, row, px + SW_X + swSlotW * 4, ly, 0xFFFFFFFF, true);
        if (!auto && style.textColorRGB() >= 0) {
            int i = indexOf(TEXT_SW, style.textColorRGB());
            if (i >= 0) {
                g.drawString(this.font, "▼", px + SW_X + swSlotW * 4 + i * swSlotW + 3,
                        ly - 9, 0xFFFFFFFF, false);
            }
        }
    }

    /** Teclado: a ARTE de cada adesivo (16x16) + numero; NOME no rodape/tooltip. */
    private void renderKeyboard(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, "✦ Escolha o adesivo — o nome aparece ao passar o mouse",
                px + 8, py + 8, 0xFFFFD98A, true);
        for (int i = 0; i < Stickers.COUNT; i++) {
            int cx = kbX + (i % KB_COLS) * KB_CELL;
            int cy = kbY + (i / KB_COLS) * KB_CELL;
            g.fill(cx, cy, cx + KB_CELL, cy + KB_CELL, 0xFF242B3D);
            g.fill(cx, cy, cx + 1, cy + KB_CELL, 0xFF39415A);
            g.fill(cx, cy, cx + KB_CELL, cy + 1, 0xFF39415A);
            g.fill(cx, cy + KB_CELL - 1, cx + KB_CELL, cy + KB_CELL, 0xFF171B26);
            g.fill(cx + KB_CELL - 1, cy, cx + KB_CELL, cy + KB_CELL, 0xFF171B26);
            drawGlyph2x(g, Stickers.GLYPHS[i], cx + 6, cy + 3);
            String num = String.valueOf(i + 1);
            g.drawString(this.font, num, cx + KB_CELL - 4 - this.font.width(num),
                    cy + KB_CELL - 9, 0xFF8E96A8, false);
        }
        int over = stickerAt(mouseX, mouseY);
        if (over >= 0) {
            int cx = kbX + (over % KB_COLS) * KB_CELL;
            int cy = kbY + (over / KB_COLS) * KB_CELL;
            outline(g, cx, cy, KB_CELL, KB_CELL, 0xFFFFFFFF);
            g.drawString(this.font, "▶ " + Stickers.NAMES[over],
                    px + 8, py + PANEL_H - 24, 0xFFFFFFFF, true);
            g.renderTooltip(this.font, Component.literal(Stickers.NAMES[over]), mouseX, mouseY);
        }
    }

    private int stickerAt(double mx, double my) {
        int cx = (int) ((mx - kbX) / KB_CELL);
        int cy = (int) ((my - kbY) / KB_CELL);
        if (cx < 0 || cx >= KB_COLS || cy < 0 || cy >= KB_ROWS || my < kbY) {
            return -1;
        }
        return cy * KB_COLS + cx;
    }

    // =====================================================================
    //  Interacao
    // =====================================================================

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && showKeyboard) {
            int idx = stickerAt(mx, my);
            if (idx >= 0) {
                holdingSticker = String.valueOf(idx);
                showKeyboard = false;
                rebuildWidgets();
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        if (!showKeyboard && button == 0 && holdingSticker != null) {
            String idx = holdingSticker;
            holdingSticker = null;
            int bubbleW = simChars * simCharW;
            if (inBox(mx, my, simX0 - 4, simY - 4, bubbleW + 8, simLineH * 2 + 8)) {
                int x = Mth.clamp((int) mx, simX0, simX0 + bubbleW);
                int y = Mth.clamp((int) my, simY, simY + simLineH * 2);
                setSticker(Stickers.GLYPHS[Integer.parseInt(idx)],
                        Math.round((x - simX0) * 100.0F / bubbleW),
                        Math.round((y - simY) * 100.0F / (simLineH * 2)));
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
        if (!showKeyboard && button == 1 && inBox(mx, my, simX0 - 4, simY - 4,
                simChars * simCharW + 8, simLineH * 2 + 8)) {
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

    /** @return "A", "D#"/"B#"/"L#" ou "" — mesma geometria do desenho. */
    private String paletteHit(double mx, double my) {
        int x0 = px + SW_X;
        int idx = (int) Math.floor((mx - x0) / (double) swSlotW);
        double frac = (mx - x0) - idx * (double) swSlotW;
        boolean inside = frac < swSlotW - 2 && idx >= 0; // o espaco final nao clica
        if (my >= py + ROW_DENTRO && my < py + ROW_DENTRO + 10 && inside && idx < SW_IN.length) {
            return "D" + idx;
        }
        if (my >= py + ROW_BORDA && my < py + ROW_BORDA + 10 && inside && idx < SW_BD.length) {
            return "B" + idx;
        }
        int ly = py + ROW_LETRA;
        if (my >= ly && my < ly + 10) {
            if (mx >= x0 && mx < x0 + this.font.width("[Auto]")) {
                return "A";
            }
            int li = (int) Math.floor((mx - (x0 + swSlotW * 4)) / (double) swSlotW);
            double lfrac = (mx - (x0 + swSlotW * 4)) - li * (double) swSlotW;
            if (li >= 0 && li < TEXT_SW.length && lfrac < swSlotW - 2) {
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
