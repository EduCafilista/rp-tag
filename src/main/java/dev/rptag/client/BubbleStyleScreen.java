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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

import dev.rptag.BubbleStyle;
import dev.rptag.OpenBubbleStylePayload;
import dev.rptag.RPTagMod;
import dev.rptag.SetBubbleModePayload;
import dev.rptag.Stickers;
import dev.rptag.UpdateBubbleStylePayload;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * TELA DE PERSONALIZACAO DO BALAO (3.13.0 — VISUAL CARTOON DE PLACAS):
 *
 * <p>O menu inteiro e desenhado por PLACAS de quadrinhos (moldura de tinta
 * grossa + recheio + brilho no topo + etiqueta dourada). O TEMA TODO fica
 * nas constantes TINTA/FUNDO/OURO... — altere elas e o menu acompanha.
 *
 * <p>A SIMULACAO agora desenha a PILULA REAL do mundo (mesma textura e as 6
 * molduras de verdade) — o jogador VE como o balao vai ficar, sem ler nada.
 *
 * <p>FLUXO GUIADO do adesivo (acabou o bug da 3.12 que engolia cliques e
 * enchia a fala de emojis): com um adesivo na mao, a tela ACENDE duas zonas
 * — o balao (clique = colar o adesivo livre) e a linha da fala (clique =
 * empilhar na mensagem). Qualquer clique FORA dessas zonas SOLTA o adesivo
 * e deixa os botoes funcionarem normalmente.
 */
public class BubbleStyleScreen extends Screen {

    // =====================================================================
    //  TEMA CARTOON — mude aqui e o menu INTEIRO acompanha
    // =====================================================================
    private static final int TINTA = 0xFF171B26;       // contorno grosso de HQ
    private static final int FUNDO = 0xFF2B3245;       // recheio do painel
    private static final int FUNDO_CLARO = 0xFF39415A; // filete de luz
    private static final int PLACA_BG = 0xFF232A3B;    // fundo das placas
    private static final int OURO = 0xFFFFD98A;        // titulos e rotulos
    private static final int OURO_VIVO = 0xFFE8A83C;   // barra lateral / destaques
    private static final int TEXTO_CLARO = 0xFFE8ECFF; // valores
    private static final int CINZA = 0xFF9AA4BC;       // texto apagado
    private static final int VERDE_OK = 0xFF9AE8B5;    // selecao / ok
    private static final int VERMELHO_TIRA = 0xFFE87A7A; // remover
    private static final int ZONA_VERDE = 0xFFB5F0C8;  // zona "colar no balao"
    private static final int ZONA_OURO = 0xFFFFE08A;   // zona "por na fala"

    // ---- A PILULA REAL DO MUNDO (o preview mostra o balao DE VERDADE) ----
    private static final ResourceLocation BUBBLE_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble.png");
    private static final ResourceLocation TAIL_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble_tail.png");
    private static final ResourceLocation BUBBLE_STICKERS =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/font/stickers.png");
    private static final RenderType BUBBLE_TYPE = RenderType.text(BUBBLE_TEX);
    private static final RenderType TAIL_TYPE = RenderType.text(TAIL_TEX);
    // regiao da pilula dentro do bubble.png (64x32; tampa = 16px)
    private static final float UV_V1 = 2.0F / 32.0F;
    private static final float UV_V2 = 28.0F / 32.0F;
    private static final float UV_CAP = 16.0F / 64.0F;

    private static final int PANEL_W = 268;
    private static final int PANEL_H = 260;

    // linhas verticais (a partir de py) — UMA fonte de verdade
    private static final int ROW_HINT = 22;
    private static final int ROW_CHAT = 32;
    private static final int ROW_BUBBLE = 46;  // pilula real: 46..72 + rabicho
    private static final int ROW_DENTRO = 84;
    private static final int ROW_BORDA = 99;
    private static final int ROW_LETRA = 114;
    private static final int ROW_BTN1 = 143;   // molduras: Auto / Minha cor / Sem
    private static final int ROW_BTN2 = 161;   // Classica / Cartum / Dupla
    private static final int ROW_BTN3 = 179;   // Tracejada / Nuvem / Gibi
    private static final int ROW_BTN4 = 198;   // Adesivos + duracao (h18)
    private static final int ROW_EXTRAS = 221; // slots de emojis (hit 216..240)
    private static final int ROW_BTN5 = 244;   // modo / padrao / salvar

    // paletas desenhadas como texto "● "
    private static final int SW_X = 64;

    // teclado de adesivos (celula 24px = arte 16px + respiro)
    private static final int KB_COLS = 8;
    private static final int KB_CELL = 24;

    // slots de EMOJIS EXTRAS na fileira propria
    private static final int MAX_SLOTS = 6; // BubbleStyle.MAX_EXTRAS / 2
    private static final int EX_X = 100;    // inicio dos slots (a partir de px)
    private static final int EX_PITCH = 24; // espacamento entre slots

    /** Cores de identidade dos 64 adesivos (uso futuro/tooltips). */
    private static final int[] CHIP = {0xE04848, 0xA048C8, 0x4878E0, 0x48B048,
            0xE0C048, 0x784848, 0xF0D048, 0xF0F0B0, 0x48A858, 0xE07030, 0xF0E048, 0xC8C8E0,
            0xF0C030, 0xE05888, 0x68B868, 0x58A8E0, 0x8878B8, 0x8878B8, 0xB0B0B8, 0x886848,
            0xE080C8, 0xF0E8D0, 0xE8C838, 0x68D8E8, 0x88B8E8, 0xE8A868, 0xE04838, 0xE04858,
            0xE8C838, 0xB8B8C8, 0x58B858, 0x68C868, 0x8898E8, 0xD85848, 0x8898D8, 0xE8D878,
            0x484848, 0xE8A8B8, 0xE8A838, 0xC88848, 0xD8B088, 0xB08858, 0xF0D048, 0x68A8D8,
            0xF0F0F0, 0xF0F0F0, 0xF0F0F0, 0x8898B8,
            0xF26D9C, 0x6DBE5C, 0x9B7BD8, 0x4A3B8A, 0xE84D4D, 0x4D8FE8, 0x8A5A32, 0x9B6BE0,
            0x7A4AC8, 0xF0629E, 0xE8883C, 0x2A3140, 0xE04848, 0xF2F2F6, 0x4CAF50, 0xFFE9A8};

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
    private int kbPage = 0;

    private int px, py;
    private int kbX, kbY;
    private int swSlotW; // largura de um "● " na paleta (calculado no init)

    // geometria da pilula da simulacao (pra posicionar o adesivo e as zonas)
    private int simX0, simY, simW, simH;
    private boolean hoverSticker = false;

    public BubbleStyleScreen(OpenBubbleStylePayload style) {
        super(Component.literal("Balão de Fala"));
        this.style = new BubbleStyle(style.colorRGB(), style.borderRGB(), style.borderStyle(),
                style.textColorRGB(), style.prefix(), style.mid(), style.suffix(), style.corner(),
                style.extras(), style.stickerX(), style.stickerY(), style.background(),
                style.durationSec());
    }

    private int effectiveTextColor() {
        if (style.textColorRGB() == BubbleStyle.TEXT_AUTO) {
            return BubbleBackgrounds.textColorFor(style.colorRGB() & 0xFFFFFF);
        }
        return 0xFF000000 | style.textColorRGB();
    }

    /**
     * Desenha a ARTE do adesivo no menu via BLIT da spritesheet
     * (16x16, o MESMO caminho dos icones do inventario — funciona em
     * qualquer cliente; drawInBatch na Screen nao pinta em todos).
     */
    private void drawSticker(GuiGraphics g, int idx, int x, int y) {
        drawStickerScaled(g, idx, x, y, 16);
    }

    /** Adesivo em qualquer tamanho (regiao 16x16 da sheet esticada — blit de inventario). */
    private void drawStickerScaled(GuiGraphics g, int idx, int x, int y, int size) {
        if (idx < 0 || idx >= Stickers.COUNT) {
            idx = 0;
        }
        int u = (idx % 8) * 16;
        int v = (idx / 8) * 16;
        g.blit(BUBBLE_STICKERS, x, y, size, size, u, v, 16, 16, 128, 128);
    }

    /** FATIA da pilula real (3 fatias: tampa + meio + tampa — pontas NUNCA deformam). */
    private void drawPill(GuiGraphics g, float x, float y, float w, float h, int argb) {
        if ((argb >>> 24) == 0 || w < 2) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4f m = g.pose().last().pose();
        VertexConsumer buf = buffers.getBuffer(BUBBLE_TYPE);
        float cap = Math.min(16.0F, w / 2.0F);
        quadGui(m, buf, x, y, x + cap, y + h, 0.0F, UV_V1, UV_CAP, UV_V2, argb);
        quadGui(m, buf, x + cap, y, x + w - cap, y + h, UV_CAP, UV_V1, 1.0F - UV_CAP, UV_V2, argb);
        quadGui(m, buf, x + w - cap, y, x + w, y + h, 1.0F - UV_CAP, UV_V1, 1.0F, UV_V2, argb);
        buffers.endBatch();
    }

    /** O RABICHO real (textura curvada) no menu. */
    private void drawTail(GuiGraphics g, float cx, float yTop, float w, float h, int argb) {
        if ((argb >>> 24) == 0) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4f m = g.pose().last().pose();
        quadGui(m, buffers.getBuffer(TAIL_TYPE), cx - w / 2.0F, yTop, cx + w / 2.0F, yTop + h,
                0.0F, 0.0F, 1.0F, 1.0F, argb);
        buffers.endBatch();
    }

    private static void quadGui(Matrix4f m, VertexConsumer buf,
            float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int argb) {
        buf.addVertex(m, x1, y1, 0.0F).setColor(argb).setUv(u1, v1).setUv1(0, 10)
                .setLight(0xF000F0).setNormal(0.0F, 1.0F, 0.0F);
        buf.addVertex(m, x1, y2, 0.0F).setColor(argb).setUv(u1, v2).setUv1(0, 10)
                .setLight(0xF000F0).setNormal(0.0F, 1.0F, 0.0F);
        buf.addVertex(m, x2, y2, 0.0F).setColor(argb).setUv(u2, v2).setUv1(0, 10)
                .setLight(0xF000F0).setNormal(0.0F, 1.0F, 0.0F);
        buf.addVertex(m, x2, y1, 0.0F).setColor(argb).setUv(u2, v1).setUv1(0, 10)
                .setLight(0xF000F0).setNormal(0.0F, 1.0F, 0.0F);
    }

    /** TRACEJADA no menu: tracinhos em volta + descadinha (igual ao mundo). */
    private void dashedGui(GuiGraphics g, float x, float y, float w, float h, int argb) {
        float dash = 5.0F, gap = 5.0F, t = 3.0F;
        float yTop = y - 3, yBot = y + h + 3, xL = x - 3, xR = x + w + 3;
        for (float dx = xL; dx < xR - 1; dx += dash + gap) {
            float ww = Math.min(dash, xR - dx);
            drawPill(g, dx, yTop, ww, t, argb);
            drawPill(g, dx, yBot - t, ww, t, argb);
        }
        for (float dy = yTop + 8; dy < yBot - 8; dy += dash + gap) {
            float hh = Math.min(dash, yBot - 8 - dy);
            if (hh < 2) {
                break;
            }
            drawPill(g, xL, dy, t, hh, argb);
            drawPill(g, xR - t, dy, t, hh, argb);
        }
        float cx = x + w / 2.0F;
        drawPill(g, cx - 2.0F, yBot - 1, t, 7, argb);
        drawPill(g, cx + 2.0F, yBot + 5, t, 7, argb);
    }

    /** NUVEM no menu: base inflada + gomos fofos em toda a volta (igual ao mundo). */
    private void cloudGui(GuiGraphics g, float x, float y, float w, float h, int argb) {
        drawPill(g, x - 3, y - 3, w + 6, h + 6, argb);
        drawTail(g, x + w / 2.0F, y + h - 3, 15, 14, argb);
        float d = 11.0F, step = 7.0F;
        for (float bx = x - 5; bx < x + w + 1; bx += step) {
            drawPill(g, bx, y - 6, d, d, argb);
            drawPill(g, bx, y + h + 6 - d, d, d, argb);
        }
        for (float by = y - 1; by < y + h - 1; by += step) {
            drawPill(g, x - 6, by, d, d, argb);
            drawPill(g, x + w + 6 - d, by, d, d, argb);
        }
    }

    /** Componente "● " na cor pedida (o caminho PROVADO de mostrar cor). */
    private static MutableComponent dot(int rgb) {
        return Component.literal("● ").withStyle(s -> s.withColor(TextColor.fromRgb(rgb & 0xFFFFFF)));
    }

    /** Insere um espaco entre cada emoji (legivel, nada de muro de adesivos). */
    private static String spaced(String s) {
        if (s.length() <= 2) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i += 2) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(s, i, Math.min(i + 2, s.length()));
        }
        return sb.toString();
    }

    /** Fileira de paleta: "Rótulo  ● ● ● ..." + "▼" no selecionado. */
    private void drawSwatchRow(GuiGraphics g, int y, String label, int[] sw, int selected) {
        g.drawString(this.font, label, px + 12, y + 1, OURO, true);
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

    // =====================================================================
    //  Placas de quadrinhos (o esqueleto visual — facil de alterar)
    // =====================================================================

    /** PLACA cartoon: sombra dura + moldura de tinta + recheio + brilho + etiqueta. */
    private void plate(GuiGraphics g, int x, int y, int w, int h, String titulo) {
        g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x66000000); // sombra dura de HQ
        g.fill(x, y, x + w, y + h, TINTA);                      // tinta grossa
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PLACA_BG);   // recheio
        g.fill(x + 2, y + 2, x + w - 1, y + 3, 0x58FFFFFF);     // brilho no topo
        if (titulo != null && !titulo.isEmpty()) {
            int tw = this.font.width(titulo);
            g.fill(x + 6, y - 3, x + 6 + tw + 8, y + 7, TINTA);     // etiqueta de tinta
            g.fill(x + 7, y - 2, x + 6 + tw + 7, y + 6, PLACA_BG);  // recheio da etiqueta
            g.drawString(this.font, titulo, x + 10, y, OURO, true);
        }
    }

    // =====================================================================
    //  Emojis EXTRAS: varios emojis acompanhando a fala (ate 6)
    // =====================================================================

    private int extrasCount() {
        return style.extras().length() / 2;
    }

    /** @return os emojis extras separados (cada um com 2 chars UTF-16). */
    private String[] extrasList() {
        String e = style.extras();
        int n = e.length() / 2;
        String[] out = new String[n];
        for (int i = 0; i < n; i++) {
            out[i] = e.substring(i * 2, i * 2 + 2);
        }
        return out;
    }

    private BubbleStyle withExtras(String newExtras) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.borderStyle(),
                style.textColorRGB(), style.prefix(), style.mid(), style.suffix(), style.corner(),
                newExtras, style.stickerX(), style.stickerY(), style.background(),
                style.durationSec());
    }

    private void addExtra(String glyph) {
        if (extrasCount() < MAX_SLOTS) {
            style = withExtras(style.extras() + glyph);
        }
    }

    private void removeExtra(int slot) {
        String e = style.extras();
        if (slot >= 0 && slot * 2 + 2 <= e.length()) {
            style = withExtras(e.substring(0, slot * 2) + e.substring(slot * 2 + 2));
        }
    }

    /** @return o slot da fileira de extras sob o mouse (-1 = nenhum). */
    private int extrasSlotAt(double mx, double my) {
        int y0 = py + ROW_EXTRAS - 5;
        if (my < y0 || my >= y0 + 24) {
            return -1;
        }
        int slot = (int) Math.floor((mx - (px + EX_X)) / (double) EX_PITCH);
        double frac = (mx - (px + EX_X)) - slot * (double) EX_PITCH;
        if (slot < 0 || slot >= MAX_SLOTS || frac >= EX_PITCH - 4) {
            return -1;
        }
        return slot;
    }

    /** Zona da FALA: a linha do chat (clique com adesivo na mao = empilha). */
    private boolean chatZone(double mx, double my) {
        return inBox(mx, my, px + 6, py + ROW_CHAT - 3, PANEL_W - 12, 14);
    }

    @Override
    protected void init() {
        px = Math.max(2, (this.width - PANEL_W) / 2);
        py = Math.max(2, (this.height - PANEL_H) / 2);
        kbX = px + (PANEL_W - KB_COLS * KB_CELL) / 2;
        kbY = py + 34;
        swSlotW = this.font.width("● ");

        if (showKeyboard) {
            addRenderableWidget(Button.builder(
                    Component.literal(kbPage == 0 ? "✨ Fofos ▶" : "◀ Clássicos"),
                    b -> {
                        kbPage = kbPage == 0 ? 1 : 0;
                        rebuildWidgets();
                    }).bounds(px + PANEL_W - 96, py + 14, 88, 16).build());
            addRenderableWidget(Button.builder(Component.literal("✔ Pronto — clique no balão ou na fala!"),
                    b -> {
                        showKeyboard = false;
                        rebuildWidgets();
                    }).bounds(px + (PANEL_W - 220) / 2, py + PANEL_H - 26, 220, 20).build());
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

        addRenderableWidget(Button.builder(Component.literal("▤ Tracejada"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_DASHED);
                }).bounds(px + 8, py + ROW_BTN3, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▤ Nuvem"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_CLOUD);
                }).bounds(px + 93, py + ROW_BTN3, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▤ Gibi"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_COMIC);
                }).bounds(px + 178, py + ROW_BTN3, 81, 16).build());

        addRenderableWidget(Button.builder(Component.literal("✦ Adesivos (64)"), b -> {
                    showKeyboard = true;
                    rebuildWidgets();
                }).bounds(px + 8, py + ROW_BTN4, 124, 18).build());
        addRenderableWidget(new AbstractSliderButton(px + 136, py + ROW_BTN4, 123, 18,
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

        int row5 = py + ROW_BTN5;
        addRenderableWidget(Button.builder(modeLabel(), b -> {
                    this.bubbleMode = !this.bubbleMode;
                    this.modeChanged = true;
                    b.setMessage(modeLabel());
                }).bounds(px + 8, row5, 84, 16).build());
        addRenderableWidget(Button.builder(Component.literal("↺ Padrão"), b -> {
                    style = BubbleStyle.DEFAULT;
                    rebuildWidgets();
                }).bounds(px + 96, row5, 78, 16).build());
        addRenderableWidget(Button.builder(Component.literal("✔ Salvar"), b -> saveAndClose())
                .bounds(px + 178, row5, 81, 16).build());
    }

    // =====================================================================
    //  Estilo: mutadores
    // =====================================================================

    private void setSticker(String glyph, int x, int y) {
        style = new BubbleStyle(style.colorRGB(), style.borderRGB(), style.borderStyle(),
                style.textColorRGB(), style.prefix(), style.mid(), style.suffix(), glyph,
                style.extras(), Mth.clamp(x, 5, 95), Mth.clamp(y, 5, 95), style.background(),
                style.durationSec());
    }

    private BubbleStyle withColor(int rgb) {
        return new BubbleStyle(rgb, style.borderRGB(), style.borderStyle(), style.textColorRGB(),
                style.prefix(), style.mid(), style.suffix(), style.corner(), style.extras(),
                style.stickerX(), style.stickerY(), style.background(), style.durationSec());
    }

    private BubbleStyle withBorder(int border) {
        return new BubbleStyle(style.colorRGB(), border, style.borderStyle(), style.textColorRGB(),
                style.prefix(), style.mid(), style.suffix(), style.corner(), style.extras(),
                style.stickerX(), style.stickerY(), style.background(), style.durationSec());
    }

    private BubbleStyle withBorderStyle(int borderStyle) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), borderStyle, style.textColorRGB(),
                style.prefix(), style.mid(), style.suffix(), style.corner(), style.extras(),
                style.stickerX(), style.stickerY(), style.background(), style.durationSec());
    }

    private BubbleStyle withText(int text) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.borderStyle(), text,
                style.prefix(), style.mid(), style.suffix(), style.corner(), style.extras(),
                style.stickerX(), style.stickerY(), style.background(), style.durationSec());
    }

    private BubbleStyle withDuration(int secs) {
        return new BubbleStyle(style.colorRGB(), style.borderRGB(), style.borderStyle(),
                style.textColorRGB(), style.prefix(), style.mid(), style.suffix(), style.corner(),
                style.extras(), style.stickerX(), style.stickerY(), style.background(), secs);
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
        drawPanel(g);
        drawTitle(g);

        // adesivo na mao segue o mouse (a arte de verdade)
        if (holdingSticker != null) {
            drawSticker(g, Integer.parseInt(holdingSticker), mouseX - 8, mouseY - 18);
            outline(g, mouseX - 9, mouseY - 19, 18, 18, 0xFFFFFFFF);
        }

        if (showKeyboard) {
            renderKeyboard(g, mouseX, mouseY);
        } else {
            renderSim(g, mouseX, mouseY);
            renderPalettes(g);
            renderExtras(g, mouseX, mouseY);
            renderHint(g);
        }
        super.render(g, mouseX, mouseY, partialTick);

        if (!showKeyboard) {
            int st = Mth.clamp(style.borderStyle(), 0, BubbleStyle.BORDER_STYLE_COUNT - 1);
            outline(g, px + 7, py + ROW_BTN1 - 1, 83, 18, VERDE_OK);
            if (st < 3) {
                outline(g, px + 7 + st * 85, py + ROW_BTN2 - 1, 83, 18, VERDE_OK);
            } else {
                outline(g, px + 7 + (st - 3) * 85, py + ROW_BTN3 - 1, 83, 18, VERDE_OK);
            }
        }
    }

    /** Painel cartoon: sombra dura, moldura de tinta, filete de luz e barra dourada. */
    private void drawPanel(GuiGraphics g) {
        g.fill(px + 4, py + 4, px + PANEL_W + 4, py + PANEL_H + 4, 0x55000000); // sombra dura
        g.fill(px, py, px + PANEL_W, py + PANEL_H, TINTA);                      // tinta grossa
        g.fill(px + 2, py + 2, px + PANEL_W - 2, py + PANEL_H - 2, FUNDO);      // recheio
        g.fill(px + 3, py + 3, px + PANEL_W - 3, py + 4, 0x44FFFFFF);           // luz no topo
        g.fill(px, py, px + 3, py + PANEL_H, OURO_VIVO);                        // barra dourada
    }

    /** Faixa de titulo com sublinhado dourado e versao. */
    private void drawTitle(GuiGraphics g) {
        g.fill(px + 3, py + 3, px + PANEL_W - 3, py + 17, 0xFF1F2534);
        g.fill(px + 3, py + 15, px + PANEL_W - 3, py + 17, OURO_VIVO);
        g.drawString(this.font, "✦ Balão de Fala", px + 10, py + 6, OURO, true);
        String ver = "v" + RPTagMod.VERSION;
        g.drawString(this.font, ver, px + PANEL_W - 8 - this.font.width(ver), py + 6, CINZA, false);
    }

    /** Dica contextual (muda quando tem adesivo na mao). */
    private void renderHint(GuiGraphics g) {
        String hint;
        int cor = CINZA;
        if (holdingSticker != null) {
            hint = "▶ Clique NO BALÃO pra colar · na FALE (dourado) pra empilhar · ESC solta";
            cor = ZONA_OURO;
        } else if (extrasCount() > 0) {
            hint = "✨ " + extrasCount() + " emoji(s) na fala · clique em ✖ pra tirar";
            cor = OURO;
        } else {
            hint = "✦ Prévia REAL: é assim que seu balão aparece no jogo";
        }
        g.drawString(this.font, hint, px + 10, py + ROW_HINT, cor, true);
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** SIMULACAO com a PILULA REAL do mundo: mesma textura, rabicho e MOLDURAS de verdade. */
    private void renderSim(GuiGraphics g, int mouseX, int mouseY) {
        int colorRGB = style.colorRGB() & 0xFFFFFF;
        int fill = BubbleBackgrounds.fillColorFor(colorRGB);
        int border = BubbleBackgrounds.borderColorFor(colorRGB, style.borderRGB());
        int textColor = effectiveTextColor();
        int bsC = Mth.clamp(Math.max(0, style.borderStyle()), 0, BubbleStyle.BORDER_STYLE_COUNT - 1);

        // --- linha do chat (igual ao chat real: nome na cor do balao) ---
        int chatY = py + ROW_CHAT;
        g.drawString(this.font, "<Você> ", px + 12, chatY, 0xFF000000 | colorRGB, true);
        g.drawString(this.font, "sua fala assim", px + 12 + this.font.width("<Você> "),
                chatY, textColor, true);
        String ex = style.extras();
        if (!ex.isEmpty()) {
            g.drawString(this.font, Stickers.styled(spaced(ex)),
                    px + 12 + this.font.width("<Você> sua fala assim "), chatY, textColor, true);
        }

        // --- medidas da pilula (O BALAO DE VERDADE, tingido) ---
        int speechW = this.font.width("sua fala assim")
                + (ex.isEmpty() ? 0 : this.font.width(" ") + this.font.width(Stickers.styled(spaced(ex))));
        simW = Math.max(32, speechW + 16);
        simH = 26;
        simX0 = px + (PANEL_W - simW) / 2;
        simY = py + ROW_BUBBLE;
        int ring = border != 0 ? border : fill;
        float cx = simX0 + simW / 2.0F;

        // --- SOMBRA (como no mundo) ---
        drawPill(g, simX0 + 2, simY + 2, simW, simH, 0x40000000);
        drawTail(g, cx, simY + simH - 1, 12, 12, 0x40000000);

        // --- MOLDURA REAL (as 6, iguais as do mundo) ---
        if (border != 0) {
            switch (bsC) {
                case 1 -> { // CARTUM
                    drawPill(g, simX0 - 4, simY - 4, simW + 8, simH + 8, border);
                    drawTail(g, cx, simY + simH - 5, 18, 17, border);
                }
                case 2 -> { // DUPLA
                    drawPill(g, simX0 - 1, simY - 1, simW + 2, simH + 2, border);
                    drawPill(g, simX0 - 3, simY - 3, simW + 6, simH + 6, border);
                    drawTail(g, cx, simY + simH - 4, 16, 15, border);
                }
                case 3 -> dashedGui(g, simX0, simY, simW, simH, border);
                case 4 -> cloudGui(g, simX0, simY, simW, simH, border);
                case 5 -> { // GIBI
                    drawPill(g, simX0 - 4, simY - 4, simW + 8, simH + 8, border);
                    drawTail(g, cx, simY + simH - 4, 17, 16, border);
                }
                default -> { // CLASSICA
                    drawPill(g, simX0 - 2, simY - 2, simW + 4, simH + 4, border);
                    drawTail(g, cx, simY + simH - 3, 15, 14, border);
                }
            }
        }

        // --- RECHEIO + rabicho ---
        drawTail(g, cx, simY + simH - 2, 13, 12, fill);
        drawPill(g, simX0, simY, simW, simH, fill);
        if (bsC == 5) { // gibi: brilho gelatinoso de HQ
            drawPill(g, simX0 + 8, simY + 3, Math.max(12, simW * 0.45F), 3, 0x99FFFFFF);
        }

        // --- fala + extras DENTRO da pilula (o ring por tras garante o recorte) ---
        int tx = simX0 + (simW - speechW) / 2;
        g.drawString(this.font, "sua fala assim", tx, simY + 9, textColor, true);
        if (!ex.isEmpty()) {
            g.drawString(this.font, Stickers.styled(spaced(ex)),
                    tx + this.font.width("sua fala assim") + this.font.width(" "),
                    simY + 9, textColor, true);
        }

        // --- zonas guiadas quando tem adesivo na mao ---
        if (holdingSticker != null) {
            outline(g, px + 6, py + ROW_CHAT - 3, PANEL_W - 12, 14, ZONA_OURO);
            outline(g, simX0 - 6, simY - 6, simW + 12, simH + 18, ZONA_VERDE);
        }

        // --- adesivo livre: na posicao (%) — com hover ---
        hoverSticker = false;
        if (!style.corner().isEmpty()) {
            int idx = (style.corner().charAt(0) & 0xFFFF) - 0xE000;
            if (idx < 0 || idx >= Stickers.COUNT) {
                idx = 0;
            }
            int sx = simX0 + Math.round(style.stickerX() / 100.0F * simW);
            int sy = simY + Math.round(style.stickerY() / 100.0F * simH);
            drawSticker(g, idx, sx - 8, sy - 8);
            if (holdingSticker == null && Math.abs(mouseX - sx) <= 8 && Math.abs(mouseY - sy) <= 8) {
                hoverSticker = true;
                g.drawString(this.font, "+", sx - 2, sy - 9, 0xFFFFFFFF, false);
            }
        }
        if (holdingSticker != null) {
            int hi = Integer.parseInt(holdingSticker);
            int gx = Mth.clamp(mouseX, simX0, simX0 + simW);
            int gy = Mth.clamp(mouseY, simY, simY + simH);
            g.setColor(1.0F, 1.0F, 1.0F, 0.6F);
            drawSticker(g, hi, gx - 8, gy - 8);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            outline(g, gx - 9, gy - 9, 18, 18, ZONA_VERDE);
        }
    }

    private void renderPalettes(GuiGraphics g) {
        plate(g, px + 4, py + 78, PANEL_W - 8, 49, "🎨 Cores");
        drawSwatchRow(g, py + ROW_DENTRO, "Dentro:", SW_IN, style.colorRGB());
        drawSwatchRow(g, py + ROW_BORDA, "Borda:", SW_BD,
                style.borderRGB() >= 0 ? style.borderRGB() : -1);
        String note = style.borderRGB() == BubbleStyle.NO_BORDER ? "sem"
                : (style.borderRGB() == BubbleStyle.AUTO_BORDER ? "auto" : "");
        if (!note.isEmpty()) {
            g.drawString(this.font, "(" + note + ")", px + SW_X + 12 * swSlotW + 2,
                    py + ROW_BORDA + 1, CINZA, false);
        }
        // LETRA: "Auto" + 8 cores
        int ly = py + ROW_LETRA;
        g.drawString(this.font, "Letra:", px + 12, ly + 1, OURO, true);
        boolean auto = style.textColorRGB() == BubbleStyle.TEXT_AUTO;
        g.drawString(this.font, "[Auto]", px + SW_X, ly + (auto ? -1 : 1),
                auto ? VERDE_OK : CINZA, true);
        if (auto) {
            g.drawString(this.font, "▼", px + SW_X + 12, ly - 9, VERDE_OK, false);
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
        plate(g, px + 4, py + 134, PANEL_W - 8, 64, "🖼 Moldura");
    }

    /**
     * Fileira dos EMOJIS EXTRAS: os varios emojis que acompanham a fala.
     * Slot cheio = a arte (16px); slot vazio = "+" (abre o teclado). Hover
     * marca; clique tira (cheio) ou pega/abre (vazio); tooltip com o nome.
     */
    private void renderExtras(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, "✨ Emojis (" + extrasCount() + "/" + MAX_SLOTS + "):",
                px + 8, py + ROW_EXTRAS - 1, OURO, true);
        String[] list = extrasList();
        int slot = extrasSlotAt(mouseX, mouseY);
        int y0 = py + ROW_EXTRAS - 3;
        for (int s = 0; s < MAX_SLOTS; s++) {
            int sx = px + EX_X + s * EX_PITCH;
            g.fill(sx, y0, sx + 20, y0 + 20, TINTA);
            g.fill(sx + 1, y0 + 1, sx + 19, y0 + 19, 0xFF1E2432);
            if (s < list.length) {
                drawSticker(g, (list[s].charAt(0) & 0xFFFF) - 0xE000, sx + 2, y0 + 2);
                if (s == slot) {
                    outline(g, sx - 1, y0 - 1, 22, 22, VERMELHO_TIRA);
                    g.renderTooltip(this.font, Component.literal("✖ tirar " + nameOf(list[s])),
                            mouseX, mouseY);
                }
            } else {
                g.drawString(this.font, "+", sx + 7, y0 + 6, 0xFF7A8498, false);
                if (s == slot) {
                    outline(g, sx - 1, y0 - 1, 22, 22, VERDE_OK);
                    g.renderTooltip(this.font,
                            Component.literal("adicionar emoji"), mouseX, mouseY);
                }
            }
        }
    }

    /** @return nome do adesivo (pelo glifo) para tooltips. */
    private static String nameOf(String glyph) {
        int idx = (glyph.charAt(0) & 0xFFFF) - 0xE000;
        if (idx < 0 || idx >= Stickers.NAMES.length) {
            return "emoji";
        }
        return Stickers.NAMES[idx];
    }

    /**
     * Teclado: 2 PAGINAS — Classicos (48) e Fofos (16) — com a ARTE de cada
     * adesivo (16x16) + numero de fallback; NOME no rodape da grade e no
     * tooltip. O titulo do painel fica sozinho no topo (sem sobreposicao).
     */
    private void renderKeyboard(GuiGraphics g, int mouseX, int mouseY) {
        String titulo = kbPage == 0 ? "Página 1/2 — Clássicos" : "Página 2/2 — Fofos ✨";
        g.drawString(this.font, titulo, px + 8, py + 18, OURO, true);
        int start = Stickers.pageStart(kbPage);
        int count = Stickers.pageCount(kbPage);
        for (int k = 0; k < count; k++) {
            int i = start + k;
            int cx = kbX + (k % KB_COLS) * KB_CELL;
            int cy = kbY + (k / KB_COLS) * KB_CELL;
            g.fill(cx, cy, cx + KB_CELL, cy + KB_CELL, 0xFF242B3D);
            g.fill(cx, cy, cx + 1, cy + KB_CELL, FUNDO_CLARO);
            g.fill(cx, cy, cx + KB_CELL, cy + 1, FUNDO_CLARO);
            g.fill(cx, cy + KB_CELL - 1, cx + KB_CELL, cy + KB_CELL, TINTA);
            g.fill(cx + KB_CELL - 1, cy, cx + KB_CELL, cy + KB_CELL, TINTA);
            drawSticker(g, i, cx + 4, cy + 4);
            String num = String.valueOf(i + 1);
            g.drawString(this.font, num, cx + KB_CELL - 4 - this.font.width(num),
                    cy + KB_CELL - 9, 0xFF8E96A8, false);
        }
        int rows = (count + KB_COLS - 1) / KB_COLS;
        int nameY = kbY + rows * KB_CELL + 5;
        int over = stickerAt(mouseX, mouseY);
        if (over >= 0) {
            int k = over - start;
            int cx = kbX + (k % KB_COLS) * KB_CELL;
            int cy = kbY + (k / KB_COLS) * KB_CELL;
            outline(g, cx, cy, KB_CELL, KB_CELL, 0xFFFFFFFF);
            g.drawString(this.font, "▶ " + Stickers.NAMES[over] + "  (vai pra fala ou pro balão)",
                    px + 8, nameY + 4, 0xFFFFFFFF, true);
            g.fill(px + PANEL_W - 46, nameY - 2, px + PANEL_W - 10, nameY + 34, TINTA);
            drawStickerScaled(g, over, px + PANEL_W - 42, nameY + 2, 32);
            g.renderTooltip(this.font, Component.literal(Stickers.NAMES[over]), mouseX, mouseY);
        } else {
            g.drawString(this.font, "passe o mouse para ver o nome · clique para pegar",
                    px + 8, nameY, CINZA, false);
        }
    }

    private int stickerAt(double mx, double my) {
        int count = Stickers.pageCount(kbPage);
        int cx = (int) ((mx - kbX) / KB_CELL);
        int cy = (int) ((my - kbY) / KB_CELL);
        if (cx < 0 || cx >= KB_COLS || cy < 0 || my < kbY) {
            return -1;
        }
        int k = cy * KB_COLS + cx;
        if (k >= count) {
            return -1;
        }
        return Stickers.pageStart(kbPage) + k;
    }

    // =====================================================================
    //  Interacao — FLUXO GUIADO: cliques nunca mais engolidos
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
            String held = holdingSticker; // lido ANTES de soltar
            // 1) NO balao da simulacao = adesivo LIVRE (posicao % salva)
            if (inBox(mx, my, simX0 - 4, simY - 4, simW + 8, simH + 14)) {
                holdingSticker = null;
                int x = Mth.clamp((int) mx, simX0, simX0 + simW);
                int y = Mth.clamp((int) my, simY, simY + simH);
                setSticker(Stickers.GLYPHS[Integer.parseInt(held)],
                        Math.round((x - simX0) * 100.0F / simW),
                        Math.round((y - simY) * 100.0F / simH));
                return true;
            }
            // 2) slots de emojis: vazio = adiciona; cheio = tira (e solta)
            int slot = extrasSlotAt(mx, my);
            if (slot >= 0) {
                if (slot < extrasCount()) {
                    removeExtra(slot);
                } else {
                    addExtra(Stickers.GLYPHS[Integer.parseInt(held)]);
                }
                holdingSticker = null;
                return true;
            }
            // 3) na ZONA DA FALA (dourada) = empilha na mensagem (mantem na mao)
            if (chatZone(mx, my)) {
                if (extrasCount() < MAX_SLOTS) {
                    addExtra(Stickers.GLYPHS[Integer.parseInt(held)]);
                }
                return true;
            }
            // 4) qualquer outro lugar = SOLTA e deixa o clique seguir (botoes ok!)
            holdingSticker = null;
            return super.mouseClicked(mx, my, button);
        }
        if (!showKeyboard && button == 0 && holdingSticker == null && hoverSticker) {
            holdingSticker = String.valueOf((style.corner().charAt(0) & 0xFFFF) - 0xE000);
            setSticker("", style.stickerX(), style.stickerY());
            return true;
        }
        if (!showKeyboard && button == 0) {
            int slot = extrasSlotAt(mx, my);
            if (slot >= 0) {
                if (slot < extrasCount()) {
                    removeExtra(slot);
                } else {
                    showKeyboard = true; // slot vazio abre o teclado
                    rebuildWidgets();
                }
                return true;
            }
        }
        if (!showKeyboard && button == 1) {
            int slot = extrasSlotAt(mx, my);
            if (slot >= 0 && slot < extrasCount()) {
                removeExtra(slot);
                return true;
            }
            if (holdingSticker != null) {
                holdingSticker = null;
                return true;
            }
            if (inBox(mx, my, simX0 - 4, simY - 4, simW + 8, simH + 14)) {
                if (!style.corner().isEmpty()) {
                    setSticker("", style.stickerX(), style.stickerY());
                }
                return true;
            }
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
                style.prefix(), style.mid(), style.suffix(), style.corner(), style.extras(),
                style.stickerX(), style.stickerY(), style.background(), style.durationSec()));
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
