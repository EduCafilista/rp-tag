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
import org.joml.Matrix4f;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

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
    // ATLAS HD dos adesivos (3.28.0): arte NOTO (Android) 128px por emoji —
    // o menu desenha QUADS direto desse atlas (qualidade Android, zero pixels)
    private static final ResourceLocation HI_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/stickers_hi.png");
    private static final RenderType HI_TYPE = RenderType.text(HI_TEX);
    // (3.50.0) PREVIA = O MUNDO: a pílula, as molduras e o rabicho sao os
    // MESMOS quads texturizados do BubbleRenderer — fim da divergencia
    // (fills 2D do menu nao batiam com o in-game e o balao "nao aparecia").
    private static final ResourceLocation BUBBLE_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/bubble.png");
    private static final RenderType BUBBLE_TYPE = RenderType.text(BUBBLE_TEX);
    private static final ResourceLocation RING_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/ring.png");
    private static final RenderType RING_TYPE = RenderType.text(RING_TEX);
    private static final ResourceLocation CARTUM_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/cartum.png");
    private static final RenderType CARTUM_TYPE = RenderType.text(CARTUM_TEX);
    private static final ResourceLocation DASHED_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/dashed.png");
    private static final RenderType DASHED_TYPE = RenderType.text(DASHED_TEX);
    private static final ResourceLocation BRILHO_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/brilho.png");
    private static final RenderType BRILHO_TYPE = RenderType.text(BRILHO_TEX);
    private static final ResourceLocation QUAD_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/quadrada.png");
    private static final RenderType QUAD_TYPE = RenderType.text(QUAD_TEX);
    private static final float U_CAP_B = 16.0F / 64.0F; // tampa da pílula (16 de 64)
    private static final float U_CAP_Q = 8.0F / 64.0F;  // tampa da Quadrada (8 de 64)
    /** CEU da previa (gradiente + grama) — textura de verdade: BLIT estica,
     * enquanto fills por linha sumiam no cliente de quem usa shader. */
    private static final ResourceLocation SKY_TEX =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "textures/gui/preview_sky.png");
    /** margem de cada moldura (IGUAL ao BubbleRenderer.RING_MARGIN). */
    private static final float[] MARGEM = {2.0F, 4.0F, 2.5F, 3.0F, 4.0F, 2.5F};
    private static final int VERMELHO_TIRA = 0xFFE87A7A; // remover
    private static final int ZONA_VERDE = 0xFFB5F0C8;  // zona "colar no balao"
    private static final int ZONA_OURO = 0xFFFFE08A;   // zona "por na fala"

    private static final int PANEL_W = 268;
    private static final int PANEL_H = 306;

    // linhas verticais (a partir de py) — UMA fonte de verdade
    // (3.50.0) RESPIRO: as linhas ficavam coladas ("Letra" em cima de
    // "Moldura") e as placas comiam a primeira fileira — cada secao agora
    // tem gap proprio e o TITULO das placas ocupa linha DENTRO da placa.
    private static final int ROW_HINT = 22;
    private static final int ROW_CHAT = 33;
    private static final int CARD_Y = 42;   // card de previa: 42..98 (ceu+grama)
    private static final int CARD_H = 56;
    private static final int ROW_DENTRO = 115; // placa Cores 102..164
    private static final int ROW_BORDA = 131;
    private static final int ROW_LETRA = 147;
    private static final int ROW_BTN1 = 179;   // molduras: Classica / Cartum / Quadrada
    private static final int ROW_BTN2 = 197;   // Costura / Brilho / Sem borda
    private static final int ROW_BTN3 = 215;   // Costura / Brilho
    private static final int ROW_EXTRAS = 243; // slots de emojis (hit 240..260)
    private static final int ROW_BTN4 = 264;   // Adesivos + duracao (h18)
    private static final int ROW_BTN5 = 286;   // modo / padrao / salvar

    // paletas desenhadas como texto "● "
    private static final int SW_X = 64;

    // teclado de adesivos (celula 24px = arte 16px + respiro)
    private static final int KB_COLS = 8;
    private static final int KB_CELL = 24;

    // slots de EMOJIS EXTRAS na fileira propria
    private static final int MAX_SLOTS = 3; // coluna da DIREITA (3 + adesivo na esquerda)
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
    private int lastBorda = 0x14161C; // ultima cor de borda clicada

    private int px, py;
    private int kbX, kbY;
    private int swSlotW; // largura de um "● " na paleta (calculado no init)

    // geometria da pilula da simulacao (pra posicionar o adesivo e as zonas)
    private int simX0, simY, simW, simH;
    /** (3.43.0) geometria da COLUNA do emblema — calculada 1x no render, reusada nos cliques/hover. */
    private float emblemColX, emblemBadgeY;
    private boolean emblemOnRight;
    /** (3.43.0) tamanho/espacamento do emblema na previa — mesmos valores do mundo. */
    private static final float BADGE_PX = 14.0F;
    private static final float BADGE_GAP_PX = 3.0F;
    private static final float TEXT_GAP_PX = 6.0F;
    private boolean hoverSticker = false;

    public BubbleStyleScreen(OpenBubbleStylePayload style) {
        super(Component.literal("Balão de Fala"));
        this.style = new BubbleStyle(style.colorRGB(), style.borderRGB(), style.borderStyle(),
                style.textColorRGB(), style.prefix(), style.mid(), style.suffix(), style.corner(),
                style.extras(), style.stickerX(), style.stickerY(), style.background(),
                style.durationSec());
        this.bubbleMode = style.bubbleMode(); // o botao ☾ reflete o estado REAL
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
    /**
     * Desenha um glifo pela FONTE DE BITMAP (drawInBatch) — a mesma familia do
     * TEXTO, que sempre renderizou neste projeto, e a mesma fonte que pinta os
     * emojis nos baloes no MUNDO (comprovado nos prints).
     */
    private void drawGlyph(GuiGraphics g, String glyph, float x, float y, float scale) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale(scale, scale, 1.0F);
        Font font = mc.font;
        font.drawInBatch(Stickers.seq(glyph), 0.0F, 0.0F, 0xFFFFFFFF, false,
                pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
        buffers.endBatch();
    }

    private void drawGlyphMenu(GuiGraphics g, int idx, float x, float y, float scale) {
        if (idx < 0 || idx >= Stickers.COUNT) {
            idx = 0;
        }
        // (3.28.0) ARTE HD: quad direto do atlas NOTO 128px (Android de verdade)
        float u0 = (idx % 8) / 8.0F;
        float v0 = (idx / 8) / 16.0F; // atlas 1024x2048 POT (10 fileiras usadas)
        Matrix4f m = g.pose().last().pose();
        VertexConsumer buf = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(HI_TYPE);
        float x2 = x + 16.0F * scale;
        float y2 = y + 16.0F * scale;
        buf.addVertex(m, x, y, 0.15F).setColor(0xFFFFFFFF).setUv(u0, v0).setUv1(0, 10)
                .setLight(0xF000F0).setNormal(0.0F, 1.0F, 0.0F);
        buf.addVertex(m, x, y2, 0.15F).setColor(0xFFFFFFFF).setUv(u0, v0 + 0.0625F).setUv1(0, 10)
                .setLight(0xF000F0).setNormal(0.0F, 1.0F, 0.0F);
        buf.addVertex(m, x2, y2, 0.15F).setColor(0xFFFFFFFF).setUv(u0 + 0.125F, v0 + 0.0625F).setUv1(0, 10)
                .setLight(0xF000F0).setNormal(0.0F, 1.0F, 0.0F);
        buf.addVertex(m, x2, y, 0.15F).setColor(0xFFFFFFFF).setUv(u0 + 0.125F, v0).setUv1(0, 10)
                .setLight(0xF000F0).setNormal(0.0F, 1.0F, 0.0F);
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    // =====================================================================
    //  (3.50.0) HELPERS DE QUAD — os MESMOS caminhos do BubbleRenderer:
    //  pílula/moldura/rabicho/costura por textura (RenderType.text). O que
    //  aparece aqui e PIXEL A PIXEL o que aparece sobre a cabeca no mundo.
    // =====================================================================

    private static VertexConsumer buf(RenderType tipo) {
        return Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(tipo);
    }

    private static void endBatch() {
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
    }

    /** QUAD texturizado generico (mesma matematica do quad() do mundo). */
    private static void quadGui(GuiGraphics g, RenderType tipo, float z,
            float x1, float y1, float x2, float y2,
            float u1, float v1, float u2, float v2, int argb) {
        Matrix4f m = g.pose().last().pose();
        VertexConsumer b = buf(tipo);
        b.addVertex(m, x1, y1, z).setColor(argb).setUv(u1, v1).setUv1(0, 10).setLight(0xF000F0)
                .setNormal(0.0F, 1.0F, 0.0F);
        b.addVertex(m, x1, y2, z).setColor(argb).setUv(u1, v2).setUv1(0, 10).setLight(0xF000F0)
                .setNormal(0.0F, 1.0F, 0.0F);
        b.addVertex(m, x2, y2, z).setColor(argb).setUv(u2, v2).setUv1(0, 10).setLight(0xF000F0)
                .setNormal(0.0F, 1.0F, 0.0F);
        b.addVertex(m, x2, y1, z).setColor(argb).setUv(u2, v1).setUv1(0, 10).setLight(0xF000F0)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /** Pílula texturizada em 3 fatias (tampa + meio + tampa) — igual ao mundo. */
    private static void pillGui(GuiGraphics g, RenderType tipo, float uCap, float z,
            float x, float y, float w, float h, int argb) {
        if ((argb >>> 24) == 0 || w < 2) {
            return;
        }
        float capPx = uCap * 64.0F;
        float cap = Math.min(capPx, w / 2.0F);
        quadGui(g, tipo, z, x, y, x + cap, y + h, 0.0F, 0.0F, uCap, 1.0F, argb);
        quadGui(g, tipo, z, x + cap, y, x + w - cap, y + h, uCap, 0.0F, 1.0F - uCap, 1.0F, argb);
        quadGui(g, tipo, z, x + w - cap, y, x + w, y + h, 1.0F - uCap, 0.0F, 1.0F, 1.0F, argb);
    }

    /** ANEL/moldura texturizada (ring.png, cartum.png...) crescendo margin p/ fora. */
    private static void ringGuiTex(GuiGraphics g, RenderType tipo, float uCap,
            float x, float y, float w, float h, int argb, float margin) {
        if ((argb >>> 24) == 0 || w < 4) {
            return;
        }
        float capPx = uCap * 64.0F;
        float cap = Math.min(capPx, w / 2.0F);
        float y1 = y - margin, y2 = y + h + margin;
        quadGui(g, tipo, 0.04F, x - margin, y1, x + cap, y2, 0.0F, 0.0F, uCap, 1.0F, argb);
        quadGui(g, tipo, 0.04F, x + cap, y1, x + w - cap, y2, uCap, 0.0F, 1.0F - uCap, 1.0F, argb);
        quadGui(g, tipo, 0.04F, x + w - cap, y1, x + w + margin, y2, 1.0F - uCap, 0.0F, 1.0F, 1.0F, argb);
    }

    /** COSTURA no menu (portada do mundo): pontilhado em periodos de 8px 1:1. */
    private static void costuraGui(GuiGraphics g, float x, float y, float w, float h, int argb, float margin) {
        if ((argb >>> 24) == 0 || w < 40) {
            return;
        }
        final float cap = 16.0F;
        quadGui(g, DASHED_TYPE, 0.04F, x - margin, y - margin, x + cap, y + h + margin,
                0.0F, 0.0F, 32.0F / 192.0F, 1.0F, argb);
        float avail = w - 2.0F * cap;
        int n = (int) (avail / 8.0F);
        for (int i = 0; i < n; i++) {
            float u0 = (32.0F + (i % 4) * 8.0F) / 192.0F;
            quadGui(g, DASHED_TYPE, 0.04F, x + cap + i * 8.0F, y - margin,
                    x + cap + (i + 1) * 8.0F, y + h + margin, u0, 0.0F, u0 + 8.0F / 192.0F, 1.0F, argb);
        }
        float resto = avail - n * 8.0F;
        if (resto > 0.3F) {
            float u0 = (32.0F + (n % 4) * 8.0F) / 192.0F;
            quadGui(g, DASHED_TYPE, 0.04F, x + cap + n * 8.0F, y - margin,
                    x + cap + n * 8.0F + resto, y + h + margin, u0, 0.0F, u0 + resto / 192.0F, 1.0F, argb);
        }
        quadGui(g, DASHED_TYPE, 0.04F, x + w - cap, y - margin, x + w + margin, y + h + margin,
                160.0F / 192.0F, 0.0F, 1.0F, 1.0F, argb);
    }

    /** Clareia um RGB em direcao ao branco (k 0..1) — halo do Brilho. */
    private static int lightenGui(int rgb, float k) {
        int r = (rgb >> 16) & 0xFF;
        int g2 = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (int) (r + (255 - r) * k);
        g2 = (int) (g2 + (255 - g2) * k);
        b = (int) (b + (255 - b) * k);
        return (r << 16) | (g2 << 8) | b;
    }

    /** Componente "● " na cor pedida (o caminho PROVADO de mostrar cor). */
    private static MutableComponent dot(int rgb) {
        return Component.literal("● ").withStyle(s -> s.withColor(TextColor.fromRgb(rgb & 0xFFFFFF)));
    }

    /** Insere um espaco entre cada emoji (legivel, nada de muro de adesivos). */
    private static String spaced(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) { // (3.50.0) 1 char = 1 emoji
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(s.charAt(i));
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
        g.fill(x + 1, y + 1, x + 3, y + h - 1, OURO_VIVO);      // acento lateral dourado
        if (titulo != null && !titulo.isEmpty()) {
            // (3.50.0) titulo DENTRO da placa, reservando a primeira linha —
            // antes a etiqueta flutuava FORA e comia a primeira fileira de opcoes
            g.drawString(this.font, titulo, x + 10, y + 2, OURO, true);
        }
    }

    // =====================================================================
    //  Emojis EXTRAS: varios emojis acompanhando a fala (ate 6)
    // =====================================================================

    private int extrasCount() {
        return style.extras().length(); // (3.50.0) 1 char = 1 emoji (glifo PUA e BMP)
    }

    /** @return os emojis extras (cada glifo PUA e UM char UTF-16). */
    private String[] extrasList() {
        String e = style.extras();
        String[] out = new String[e.length()];
        for (int i = 0; i < e.length(); i++) {
            out[i] = String.valueOf(e.charAt(i));
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
        if (slot >= 0 && slot < e.length()) { // (3.50.0) 1 char por emoji
            style = withExtras(e.substring(0, slot) + e.substring(slot + 1));
        }
    }

    /** @return o slot da fileira de extras sob o mouse (-1 = nenhum). */
    private int extrasSlotAt(double mx, double my) {
        int y0 = py + ROW_EXTRAS - 7; // (3.52.0) cobre o mini-X do canto
        if (my < y0 || my >= y0 + 26) {
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

    /**
     * (3.43.0) o adesivo do canto agora e sempre o PRIMEIRO item da coluna do
     * emblema (mesma coluna fixa dos emotes, dentro da pilula) — usa os
     * campos calculados no ultimo renderSim, sem posicao livre.
     */
    private float[] stickerCenter() {
        return new float[]{emblemColX + BADGE_PX / 2.0F, emblemBadgeY + BADGE_PX / 2.0F};
    }

    /** Caixinha vermelha [x] no canto do adesivo livre da PREVIA (clique = tira). */
    private int[] removeBoxPos() {
        if (style.corner().isEmpty()) {
            return null;
        }
        float[] c = stickerCenter();
        // (3.52.0) caixinha de 14px (era 12) — mais facil de clicar
        int bx = Math.min(simX0 + simW - 15, Math.round(c[0]) + 16);
        int by = Math.round(c[1]) - 7;
        return new int[]{bx, by};
    }

    private boolean removeBoxAt(double mx, double my) {
        int[] b = removeBoxPos();
        return b != null && inBox(mx, my, b[0], b[1], 14, 14);
    }

    @Override
    protected void init() {
        try {
            initInterno();
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: erro ao montar a tela do balao", t);
        }
    }

    private void initInterno() {
        px = Math.max(2, (this.width - PANEL_W) / 2);
        py = Math.max(2, (this.height - PANEL_H) / 2);
        kbX = px + (PANEL_W - KB_COLS * KB_CELL) / 2;
        kbY = py + 34;
        swSlotW = this.font.width("● ");
        if (style.borderRGB() >= 0) {
            lastBorda = style.borderRGB();
        }

        if (showKeyboard) {
            addRenderableWidget(Button.builder(
                    Component.literal(kbPage == 0 ? "✨ Fofos ▶" : "◀ Clássicos"),
                    b -> {
                        kbPage = kbPage == 0 ? 1 : 0;
                        rebuildWidgets();
                    }).bounds(px + PANEL_W - 96, py + 14, 88, 16).build());
            // GRADE = BOTÕES VANILLA (o caminho mais à prova de cliente que existe)
            int start = Stickers.pageStart(kbPage);
            int count = Stickers.pageCount(kbPage);
            for (int k = 0; k < count; k++) {
                final int idx = start + k;
                int cx = kbX + (k % KB_COLS) * KB_CELL;
                int cy = kbY + (k / KB_COLS) * KB_CELL;
                addRenderableWidget(Button.builder(Component.literal(""), b -> {
                    holdingSticker = String.valueOf(idx);
                    showKeyboard = false;
                    rebuildWidgets();
                }).bounds(cx, cy, KB_CELL, KB_CELL).build());
            }
            addRenderableWidget(Button.builder(Component.literal("✔ Pronto — clique no balão ou na fala!"),
                    b -> {
                        showKeyboard = false;
                        rebuildWidgets();
                    }).bounds(px + (PANEL_W - 220) / 2, py + PANEL_H - 26, 220, 20).build());
            return;
        }

        // (3.51.1) ORDEM ARRUMADA: as 5 molduras em 2 fileiras de 3 e o
        // botao de borda no ULTIMO espaco (nao sobra buraco na grade):
        //   [Classica] [Cartum]  [Quadrada]
        //   [Costura]  [Brilho]  [Sem borda]
        addRenderableWidget(Button.builder(Component.literal("▤ Clássica"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_CLASSIC);
                }).bounds(px + 8, py + ROW_BTN1, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("▤ Cartum"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_CARTOON);
                }).bounds(px + 93, py + ROW_BTN1, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("\u25a3 Quadrada"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_DOUBLE); // id 2 = QUADRADA (3.50.0)
                }).bounds(px + 178, py + ROW_BTN1, 81, 16).build());

        addRenderableWidget(Button.builder(Component.literal("∴ Costura"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_DASHED);
                }).bounds(px + 8, py + ROW_BTN2, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("◕ Brilho"), b -> {
                    style = withBorderStyle(BubbleStyle.BORDER_CLOUD);
                }).bounds(px + 93, py + ROW_BTN2, 81, 16).build());
        // (3.51.0) Auto e "Minha cor" SAIRAM (obsoletos: a cor da borda se
        // escolhe na paleta "Borda:" ali em cima). Ficou UM botao claro.
        addRenderableWidget(Button.builder(bordaToggleLabel(), b -> {
                    style = withBorder(style.borderRGB() == BubbleStyle.NO_BORDER
                            ? lastBorda : BubbleStyle.NO_BORDER);
                    b.setMessage(bordaToggleLabel());
                }).bounds(px + 178, py + ROW_BTN2, 81, 16).build());
        addRenderableWidget(Button.builder(Component.literal("✦ Adesivos (80)"), b -> {
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

    /** (3.51.0) rotulo do botao Sem borda / Com borda. */
    private Component bordaToggleLabel() {
        return style.borderRGB() == BubbleStyle.NO_BORDER
                ? Component.literal("\u25a3 Com borda")
                : Component.literal("\u25a2 Sem borda");
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
        // vinheta suave (degrade por faixas) — profundidade sem textura
        for (int i = 0; i < 5; i++) {
            int a = 0x28 - i * 8;
            g.fill(0, i * 4, this.width, i * 4 + 4, a << 24);
            g.fill(0, this.height - i * 4 - 4, this.width, this.height - i * 4, a << 24);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        // (3.50.1) A PROVA DE CRASH (mesma filosofia do mundo): qualquer erro
        // no desenho da tela NAO fecha o jogo — cai pro modo simples (so os
        // BOTOES continuam) e registra o motivo no log.
        try {
            drawPanel(g);
            drawTitle(g);

            if (!showKeyboard) {
                renderSim(g, mouseX, mouseY);
                renderPalettes(g);
                renderExtras(g, mouseX, mouseY);
                renderHint(g, mouseX, mouseY);
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: erro ao desenhar a tela do balao (modo simples ativado)", t);
            g.drawCenteredString(this.font, "Previa indisponivel - os botoes seguem funcionando",
                    this.width / 2, py + 52, 0xFFFF7070);
        }
        super.render(g, mouseX, mouseY, partialTick);

        try {

        // EMOJIS/CORES por cima dos widgets (drawInBatch = familia do TEXTO que
        // sempre funcionou; "█" colorido = unifont colorido das paletas ●)
        if (holdingSticker != null) {
            drawGlyphMenu(g, Integer.parseInt(holdingSticker), mouseX - 8, mouseY - 18, 1.0F);
            outline(g, mouseX - 9, mouseY - 19, 18, 18, 0xFFFFFFFF);
        }
        if (showKeyboard) {
            renderKeyboard(g, mouseX, mouseY);
        }
        if (!showKeyboard) {
            int st = Mth.clamp(style.borderStyle(), 0, BubbleStyle.BORDER_STYLE_COUNT - 1);
            if (st == 5) {
                st = 2; // (3.50.0) Gibi/Nuvem migraram pra Quadrada
            }
            if (style.borderRGB() == BubbleStyle.NO_BORDER) {
                outline(g, px + 7 + 170, py + ROW_BTN2 - 1, 83, 18, VERDE_OK);
            }
            if (st < 3) {
                outline(g, px + 7 + st * 85, py + ROW_BTN1 - 1, 83, 18, VERDE_OK);
            } else {
                outline(g, px + 7 + (st - 3) * 85, py + ROW_BTN2 - 1, 83, 18, VERDE_OK);
            }
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: erro no overlay da tela do balao", t);
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
    private void renderHint(GuiGraphics g, int mouseX, int mouseY) {
        String hint;
        int cor = CINZA;
        // PASSOU O MOUSE NUM BOTAO DE MOLDURA? explica na hora (sem enigma)
        String mh = molduraHover(mouseX, mouseY);
        if (mh != null) {
            g.drawString(this.font, mh, px + 10, py + ROW_HINT, VERDE_OK, true);
            return;
        }
        if (holdingSticker != null) {
            hint = "Esquerda do balao = adesivo \u00b7 Direita = emoji \u00b7 ESC solta";
            cor = ZONA_OURO;
        } else if (extrasCount() > 0) {
            hint = "Clique no X vermelho pra tirar um emoji";
            cor = OURO;
        } else {
            hint = "✦ Prévia REAL: é EXATAMENTE assim que fica no mundo";
        }
        g.drawString(this.font, hint, px + 10, py + ROW_HINT, cor, true);
    }

    /** @return dica da moldura sob o mouse (ou null) — alinhada aos BOTOES. */
    private String molduraHover(int mouseX, int mouseY) {
        boolean l1 = mouseY >= py + ROW_BTN1 - 2 && mouseY <= py + ROW_BTN1 + 18;
        boolean l2 = mouseY >= py + ROW_BTN2 - 2 && mouseY <= py + ROW_BTN2 + 18;
        if (l1) {
            if (mouseX >= px + 8 && mouseX <= px + 89) {
                return "\u00a7fClassica\u00a77: contorno com relevo e brilho";
            }
            if (mouseX >= px + 93 && mouseX <= px + 174) {
                return "\u00a7fCartum\u00a77: tinta grossa de quadrinho";
            }
            if (mouseX >= px + 178 && mouseX <= px + 259) {
                return "\u00a7fQuadrada\u00a77: balao meio quadrado, cantinhos suaves";
            }
        }
        if (l2) {
            if (mouseX >= px + 8 && mouseX <= px + 89) {
                return "\u00a7fCostura\u00a77: pontilhado fino seguindo o balao";
            }
            if (mouseX >= px + 93 && mouseX <= px + 174) {
                return "\u00a7fBrilho\u00a77: halo suave ao redor do balao";
            }
            if (mouseX >= px + 178 && mouseX <= px + 259) {
                return style.borderRGB() == BubbleStyle.NO_BORDER
                        ? "\u00a7fCom borda\u00a77: traz de volta a ultima cor de borda clicada"
                        : "\u00a7fSem borda\u00a77: balao limpo, sem contorno";
            }
        }
        return null;
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /**
     * (3.50.0) SIMULACAO = O MUNDO: ceu+grama por BLIT (textura de verdade,
     * estica sem depender de fills) e o balao pelos MESMOS quads texturizados
     * do BubbleRenderer (pílula, molduras, rabicho). O menu agora mostra
     * PIXEL A PIXEL como o balao fica sobre a cabeca — fim da "previa bugada".
     */
    private void renderSim(GuiGraphics g, int mouseX, int mouseY) {
        int colorRGB = style.colorRGB() & 0xFFFFFF;
        int fill = BubbleBackgrounds.fillColorFor(colorRGB);
        int border = BubbleBackgrounds.borderColorFor(colorRGB, style.borderRGB());
        int textColor = effectiveTextColor();
        int bsC = Mth.clamp(Math.max(0, style.borderStyle()), 0, BubbleStyle.BORDER_STYLE_COUNT - 1);
        if (bsC == 5) {
            bsC = 2; // (3.50.0) antigo Gibi/Nuvem migraram pro balao QUADRADO
        }

        // --- linha do chat (igual ao chat real: nome na cor do balao) ---
        // (3.55.0) a previa usa a ULTIMA FALA REAL do jogador: so escrever
        // no chat (com a tela fechada) e abrir o /balao pra ver a sua frase
        String fala = falaPrev();
        int chatY = py + ROW_CHAT;
        g.drawString(this.font, "<Voc\u00ea> ", px + 12, chatY, 0xFF000000 | colorRGB, true);
        g.drawString(this.font, fala, px + 12 + this.font.width("<Voc\u00ea> "),
                chatY, textColor, true);
        String ex = style.extras();
        if (!ex.isEmpty()) {
            g.drawString(this.font, Stickers.styled(spaced(ex)),
                    px + 12 + this.font.width("<Voc\u00ea> " + fala + " "), chatY, textColor, true);
        }

        // --- CARD DE PREVIA: CEU + GRAMA (blit estica a textura inteira) ---
        int cardX = px + 8, cardY = py + CARD_Y, cardW = PANEL_W - 16, cardH = CARD_H;
        g.blit(SKY_TEX, cardX, cardY, 0, 0, cardW, cardH, cardW, cardH);
        outline(g, cardX, cardY, cardW, cardH, 0xFF2A3248);
        g.fill(cardX, cardY, cardX + cardW, cardY + 1, OURO_VIVO);
        String tag = "\u2726 Prev\u00eda no mundo";
        int tagW = this.font.width(tag) + 8;
        g.fill(cardX + 4, cardY + cardH - 12, cardX + 4 + tagW, cardY + cardH - 2, 0xCC0D1220);
        g.fill(cardX + 4, cardY + cardH - 12, cardX + 6, cardY + cardH - 2, OURO_VIVO);
        g.drawString(this.font, tag, cardX + 8, cardY + cardH - 10, OURO, true);
        // RECORTE: o balao vive DENTRO do card — nada invade as outras linhas
        g.enableScissor(cardX + 1, cardY + 1, cardX + cardW - 1, cardY + cardH - 1);

        // --- medidas da pilula (as MESMAS contas do mundo) ---
        boolean hasCorner = !style.corner().isEmpty();
        boolean temAdesivo = hasCorner || !ex.isEmpty(); // 1 char = 1 emoji (3.50.0)
        // (3.51.0) DUAS COLUNAS como no mundo: adesivo na ESQUERDA, emojis
        // na DIREITA — um de cada lado, com figuras diferentes.
        float leftW = hasCorner ? BADGE_PX + TEXT_GAP_PX : 0;
        float rightW = ex.length() > 0
                ? ex.length() * BADGE_PX + (ex.length() - 1) * BADGE_GAP_PX + TEXT_GAP_PX : 0;
        // (3.56.0) a previa QUEBRA a fala em linhas (igual ao mundo): mais
        // palavras = mais balao PRA CIMA — o texto nunca mais vaza da pilula
        java.util.List<String> linhasPrev = wrapPrev(falaPrev(), 110);
        float K = 2.0F;
        if (linhasPrev.size() > 2) {
            K = 1.5F;
            linhasPrev = wrapPrev(falaPrev(), 150);
        }
        if (linhasPrev.size() > 3) {
            K = 1.2F;
            linhasPrev = wrapPrev(falaPrev(), 185);
        }
        if (linhasPrev.size() > 4) {
            K = 0.95F;
            linhasPrev = wrapPrev(falaPrev(), 220);
        }
        int textWPrev = 0;
        for (String l : linhasPrev) {
            textWPrev = Math.max(textWPrev, this.font.width(l));
        }
        simW = Math.max(170, Math.min(246,
                Math.round(textWPrev * K) + 28 + Math.round(leftW + rightW)));
        simH = Math.round(linhasPrev.size() * this.font.lineHeight * K) + 8;
        simX0 = px + (PANEL_W - simW) / 2;
        simY = cardY + 5;
        float margem = MARGEM[Math.max(0, Math.min(bsC, MARGEM.length - 1))];
        float cx = simX0 + simW / 2.0F;

        // --- SOMBRA (como no mundo) ---
        boolean quadradaPrev = (bsC == 2);
        if (quadradaPrev) {
            pillGui(g, QUAD_TYPE, U_CAP_Q, 0.02F, simX0 + 1, simY + 2, simW, simH, 0x33000000);
        } else {
            pillGui(g, BUBBLE_TYPE, U_CAP_B, 0.02F, simX0 + 1, simY + 2, simW, simH, 0x33000000);
        }

        // --- MOLDURA REAL (texturas DO MUNDO; mesma margem de cada estilo) ---
        if (border != 0) {
            switch (bsC) {
                case 1 -> // CARTUM: tinta grossa
                    ringGuiTex(g, CARTUM_TYPE, U_CAP_B, simX0, simY, simW, simH, border, margem);
                case 2 -> // QUADRADA: contorno de cantinhos arredondados
                    pillGui(g, QUAD_TYPE, U_CAP_Q, 0.04F, simX0 - margem, simY - margem,
                            simW + 2.0F * margem, simH + 2.0F * margem, border);
                case 3 -> // COSTURA: tracinhos em periodos de 8px (igual ao mundo)
                    costuraGui(g, simX0, simY, simW, simH, border, margem);
                case 4 -> { // BRILHO: halo CLARO + anel fino
                    int halo = 0xCC000000 | lightenGui(colorRGB, 0.55F);
                    ringGuiTex(g, BRILHO_TYPE, U_CAP_B, simX0, simY, simW, simH, halo, margem);
                    ringGuiTex(g, RING_TYPE, U_CAP_B, simX0, simY, simW, simH, border, 2.0F);
                }
                default -> // CLASSICA: contorno com relevo
                    ringGuiTex(g, RING_TYPE, U_CAP_B, simX0, simY, simW, simH, border, margem);
            }
        }

        // --- RECHEIO (a pilula cobre o encaixe do rabicho, igual ao mundo) ---
        if (quadradaPrev) {
            pillGui(g, QUAD_TYPE, U_CAP_Q, 0.05F, simX0, simY, simW, simH, fill);
        } else {
            pillGui(g, BUBBLE_TYPE, U_CAP_B, 0.05F, simX0, simY, simW, simH, fill);
        }
        // (3.56.0) RABICHO PIXEL = os MESMOS degraus do mundo (nem quadrado,
        // nem triangulo): contorno = degraus expandidos, recheio = degraus
        int icx = Math.round(cx) + 1;
        int ty0 = simY + simH - 1;
        if (border != 0) {
            g.fill(icx - 8, ty0 - 1, icx + 8, ty0 + 5, border);
            g.fill(icx - 4, ty0 + 3, icx + 5, ty0 + 10, border);
        }
        // (3.56.1) texel da pilula e 248 (nao 255): fora da Quadrada, o
        // rabicho usa a cor escalada pra ficar EXATAMENTE igual ao balao
        int fillTex = quadradaPrev ? fill : escalaTexel(fill);
        g.fill(icx - 7, ty0, icx + 7, ty0 + 4, fillTex);
        g.fill(icx - 3, ty0 + 4, icx + 4, ty0 + 9, fillTex);
        endBatch();

        // --- fala em MULTI-LINHAS dentro da pilula (zoom K, centrada) ---
        int tx = simX0 + 14 + (int) leftW;
        int blockH = Math.round(linhasPrev.size() * this.font.lineHeight * K);
        int tyText = simY + (simH - blockH) / 2;
        int cumPrev = 0;
        for (String linha : linhasPrev) {
            int lw = this.font.width(linha);
            g.pose().pushPose();
            g.pose().translate(tx + (textWPrev - lw) * K / 2.0F, tyText + cumPrev, 0.0F);
            g.pose().scale(K, K, 1.0F);
            g.drawString(this.font, linha, 0, 0, textColor, true);
            g.pose().popPose();
            cumPrev += Math.round(this.font.lineHeight * K);
        }

        g.disableScissor();

        // --- zonas guiadas quando tem adesivo na mao ---
        if (holdingSticker != null) {
            outline(g, px + 6, py + ROW_CHAT - 3, PANEL_W - 12, 14, ZONA_OURO);
            outline(g, simX0 - 6, simY - 6, simW + 12, simH + 18, ZONA_VERDE);
        }

        // --- EMBLEMA = DUAS COLUNAS DENTRO DA PILULA (igual ao mundo) ---
        // ESQUERDA = adesivo (com o X pra tirar) · DIREITA = emojis da fala
        emblemColX = simX0 + 8;
        emblemBadgeY = simY + (simH - BADGE_PX) / 2.0F;
        if (temAdesivo) {
            int i = 0;
            if (hasCorner) {
                int idx = (style.corner().codePointAt(0)) - 0xE000;
                if (idx < 0 || idx >= Stickers.COUNT) {
                    idx = 0;
                }
                drawGlyphMenu(g, idx, Math.round(emblemColX), Math.round(emblemBadgeY),
                        BADGE_PX / 16.0F);
                int[] rb = removeBoxPos();
                if (rb != null) { // [X] SEMPRE visivel: impossivel nao achar como tirar
                    // (3.52.0) X MAIOR (14px) e centralizado — o "X sumiu" acabou
                    g.fill(rb[0], rb[1], rb[0] + 14, rb[1] + 14, 0xFFB03030);
                    g.fill(rb[0] + 1, rb[1] + 1, rb[0] + 13, rb[1] + 13, 0xFFD85858);
                    g.drawString(this.font, "X", rb[0] + 4, rb[1] + 3, 0xFFFFFFFF, false);
                }
                float bx = emblemColX + BADGE_PX / 2.0F;
                float by = emblemBadgeY + BADGE_PX / 2.0F;
                if (holdingSticker == null && Math.abs(mouseX - bx) <= 8 && Math.abs(mouseY - by) <= 8) {
                    hoverSticker = true;
                    g.drawString(this.font, "+", Math.round(bx) - 2, Math.round(by) - 9, 0xFFFFFFFF, false);
                }
                i++;
            }
            if (!ex.isEmpty()) { // coluna da DIREITA: os emojis da fala
                float rColX = simX0 + simW - 8
                        - (ex.length() * BADGE_PX + (ex.length() - 1) * BADGE_GAP_PX);
                for (int k = 0; k < ex.length(); k++) { // 1 char = 1 emoji (fix 3.50.0)
                    int eIdx = (ex.codePointAt(k)) - 0xE000;
                    if (eIdx < 0 || eIdx >= Stickers.COUNT) {
                        eIdx = 0;
                    }
                    drawGlyphMenu(g, eIdx, Math.round(rColX + k * (BADGE_PX + BADGE_GAP_PX)),
                            Math.round(emblemBadgeY), BADGE_PX / 16.0F);
                }
            }
        } else {
            hoverSticker = false;
        }
        if (holdingSticker != null) {
            int hi = Integer.parseInt(holdingSticker);
            int gx = Mth.clamp(mouseX, simX0, simX0 + simW);
            int gy = Mth.clamp(mouseY, simY, simY + simH);
            g.setColor(1.0F, 1.0F, 1.0F, 0.6F);
            drawGlyphMenu(g, hi, gx - 8, gy - 8, 1.0F);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            outline(g, gx - 9, gy - 9, 18, 18, ZONA_VERDE);
        }
    }

    private void renderPalettes(GuiGraphics g) {
        plate(g, px + 4, py + 102, PANEL_W - 8, 62, "\u25cf Cores");
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
            g.drawString(this.font, "\u25bc", px + SW_X + 12, ly - 9, VERDE_OK, false);
        }
        MutableComponent row = Component.empty();
        for (int c : TEXT_SW) {
            row.append(dot(c));
        }
        g.drawString(this.font, row, px + SW_X + swSlotW * 4, ly, 0xFFFFFFFF, true);
        if (!auto && style.textColorRGB() >= 0) {
            int i = indexOf(TEXT_SW, style.textColorRGB());
            if (i >= 0) {
                g.drawString(this.font, "\u25bc", px + SW_X + swSlotW * 4 + i * swSlotW + 3,
                        ly - 9, 0xFFFFFFFF, false);
            }
        }
        plate(g, px + 4, py + 168, PANEL_W - 8, 50, "\u25cf Moldura"); // (3.51.1) 2 fileiras
        // ICONE da moldura ativa (vede de relance qual esta escolhida)
        int stIc = Mth.clamp(Math.max(0, style.borderStyle()), 0, 5);
        if (stIc == 5) {
            stIc = 2; // (3.50.0) Gibi/Nuvem migraram pra Quadrada
        }
        String ic = switch (stIc) {
            case 1 -> "\u2726";
            case 2 -> "\u25a3";
            case 3 -> "\u2234";
            case 4 -> "\u25d5";
            default -> "\u25c9";
        };
        g.fill(px + PANEL_W - 28, py + 171, px + PANEL_W - 10, py + 187, 0xFF0D1220);
        g.drawString(this.font, ic, px + PANEL_W - 23, py + 174, VERDE_OK, false);
    }

    /**
     * Fileira dos EMOJIS EXTRAS: os varios emojis que acompanham a fala.
     * Slot cheio = a arte (16px); slot vazio = "+" (abre o teclado). Hover
     * marca; clique tira (cheio) ou pega/abre (vazio); tooltip com o nome.
     */
    private void renderExtras(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, "Emojis (clique no X vermelho pra tirar):", px + 8,
                py + ROW_EXTRAS + 1, OURO, true);
        String[] list = extrasList();
        int slot = extrasSlotAt(mouseX, mouseY);
        int y0 = py + ROW_EXTRAS - 3;
        for (int s = 0; s < MAX_SLOTS; s++) {
            int sx = px + EX_X + s * EX_PITCH;
            g.fill(sx, y0, sx + 20, y0 + 20, TINTA);
            g.fill(sx + 1, y0 + 1, sx + 19, y0 + 19, 0xFF1E2432);
            if (s < list.length) {
                drawGlyphMenu(g, (list[s].charAt(0) & 0xFFFF) - 0xE000, sx + 2, y0 + 2, 1.0F);
                // (3.55.0) X vermelho GRANDE (10px) e SEMPRE visivel no canto
                // do slot cheio — sem tooltip: clicou no slot = tirou
                g.fill(sx + 10, y0 - 2, sx + 20, y0 + 8, 0xFFB03030);
                g.fill(sx + 11, y0 - 1, sx + 19, y0 + 7, 0xFFD85858);
                g.drawString(this.font, "X", sx + 13, y0, 0xFFFFFFFF, false);
                if (s == slot) {
                    outline(g, sx - 1, y0 - 1, 22, 22, VERMELHO_TIRA);
                    // (3.55.0) o nome vai na LINHA DO TITULO (a tooltip
                    // "tirar Morango" vivia presa na frente de tudo)
                    String nome = "\u2716 tirar " + nameOf(list[s]);
                    g.drawString(this.font, nome, px + PANEL_W - 8 - this.font.width(nome),
                            py + ROW_EXTRAS + 1, VERMELHO_TIRA, true);
                }
            } else {
                g.drawString(this.font, "+", sx + 7, y0 + 6, 0xFF7A8498, false);
                if (s == slot) {
                    outline(g, sx - 1, y0 - 1, 22, 22, VERDE_OK);
                }
            }
        }
    }

    /**
     * (3.55.0) a fala da previa = a ULTIMA frase que o jogador escreveu no
     * chat (sem comando). Se ainda nao escreveu nada, usa o exemplo.
     */
    private static String falaPrev() {
        String f = ClientRPStates.ultimaFala;
        return (f == null || f.isBlank()) ? "sua fala assim" : f;
    }

    /** (3.56.1) multiplica o RGB por 248/255 (texel do recheio da pilula). */
    private static int escalaTexel(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, (int) (((argb >>> 16) & 0xFF) * 248.0F / 255.0F + 0.5F));
        int g = Math.min(255, (int) (((argb >>> 8) & 0xFF) * 248.0F / 255.0F + 0.5F));
        int b = Math.min(255, (int) ((argb & 0xFF) * 248.0F / 255.0F + 0.5F));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * (3.56.0) quebra gulosa por palavras (palavra gigante e fatiada) — a
     * previa usa as MESMAS regras de largura do mundo.
     */
    private java.util.List<String> wrapPrev(String texto, float maxW) {
        java.util.List<String> linhas = new java.util.ArrayList<>();
        for (String bruta : texto.split(" ")) {
            String w = bruta;
            while (!w.isEmpty() && this.font.width(w) > maxW) {
                int cut = w.length() - 1;
                while (cut > 1 && this.font.width(w.substring(0, cut)) > maxW) {
                    cut--;
                }
                linhas.add(w.substring(0, cut));
                w = w.substring(cut);
            }
            if (w.isEmpty()) {
                continue;
            }
            if (linhas.isEmpty()
                    || this.font.width(linhas.get(linhas.size() - 1) + " " + w) > maxW) {
                linhas.add(w);
            } else {
                linhas.set(linhas.size() - 1, linhas.get(linhas.size() - 1) + " " + w);
            }
        }
        if (linhas.isEmpty()) {
            linhas.add(texto);
        }
        return linhas;
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
        // CARTAO DE TESTE: "█" colorido (unifont, o mesmo das paletas ●) + coracao
        // desenhado pela fonte de emoji. Tudo que aparecer aqui, os emojis abaixo
        // usam o MESMO motor.
        int ty = py + 30;
        g.drawString(this.font, dot(0xFF6060), px + 8, ty, 0xFFFFFFFF, true);
        g.drawString(this.font, dot(0x60FF60), px + 8 + swSlotW, ty, 0xFFFFFFFF, true);
        g.drawString(this.font, dot(0x6060FF), px + 8 + swSlotW * 2, ty, 0xFFFFFFFF, true);
        drawGlyphMenu(g, 0, px + 8 + swSlotW * 3, ty - 2, 0.75F);
        g.drawString(this.font, "← teste de render (cor + emoji)", px + 8 + swSlotW * 4, ty + 1,
                CINZA, false);

        int start = Stickers.pageStart(kbPage);
        int count = Stickers.pageCount(kbPage);
        for (int k = 0; k < count; k++) {
            int i = start + k;
            int cx = kbX + (k % KB_COLS) * KB_CELL;
            int cy = kbY + (k / KB_COLS) * KB_CELL;
            // CHIP da cor do emoji: "██" colorido (TEXTO — sempre renderizou aqui:
            // as paletas ● sempre apareceram). Torna o seletor usavel DE QUALQUER jeito.
            int chip = CHIP[i % CHIP.length];
            g.drawString(this.font, dot(chip), cx + 3, cy + KB_CELL - 11, 0xFFFFFFFF, true);
            // ARTE do emoji pela fonte de bitmap (a MESMA que desenha no mundo!)
            drawGlyphMenu(g, i, cx + 4, cy + 2, 1.0F);
            String num = String.valueOf(i + 1);
            g.drawString(this.font, num, cx + KB_CELL - 4 - this.font.width(num),
                    cy + 1, 0xFFFFFFFF, true);
        }
        int rows = (count + KB_COLS - 1) / KB_COLS;
        int nameY = kbY + rows * KB_CELL + 6;
        int over = stickerAt(mouseX, mouseY);
        if (over >= 0) {
            int k = over - start;
            int cx = kbX + (k % KB_COLS) * KB_CELL;
            int cy = kbY + (k / KB_COLS) * KB_CELL;
            outline(g, cx - 1, cy - 1, KB_CELL + 2, KB_CELL + 2, 0xFFFFFFFF);
            g.drawString(this.font, "▶ " + Stickers.NAMES[over] + "  (clique pra pegar!)",
                    px + 8, nameY + 8, 0xFFFFFFFF, true);
            // PREVIEW GRANDE (3x)
            g.fill(px + PANEL_W - 56, nameY, px + PANEL_W - 8, nameY + 40, 0xFF171B26);
            drawGlyphMenu(g, over, px + PANEL_W - 52, nameY + 2, 3.0F);
            g.renderTooltip(this.font, Component.literal(Stickers.NAMES[over]), mouseX, mouseY);
        } else {
            g.drawString(this.font, "passe o mouse pra ver o nome · clique pra pegar · ESC solta",
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
        try {
            return cliqueInterno(mx, my, button);
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: erro num clique da tela do balao", t);
            holdingSticker = null;
            return super.mouseClicked(mx, my, button);
        }
    }

    private boolean cliqueInterno(double mx, double my, int button) {
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
            // 1) NO balao da simulacao: METADE ESQUERDA = adesivo da coluna
            // esquerda · METADE DIREITA = emoji(s) da coluna direita. Assim
            // dá pra deixar um emoji DE CADA LADO, com figuras diferentes.
            if (inBox(mx, my, simX0 - 4, simY - 4, simW + 8, simH + 14)) {
                holdingSticker = null;
                if (mx >= simX0 + simW / 2.0) { // DIREITA: vira emoji da fala
                    if (extrasCount() >= MAX_SLOTS) {
                        removeExtra(extrasCount() - 1); // substitui o ultimo
                    }
                    addExtra(Stickers.GLYPHS[Integer.parseInt(held)]);
                } else { // ESQUERDA: vira o adesivo fixo
                    setSticker(Stickers.GLYPHS[Integer.parseInt(held)], 25, style.stickerY());
                }
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
            // 3) clicou num BOTAO? solta e deixa o clique funcionar nele
            for (var w : this.children()) {
                if (w.isMouseOver(mx, my)) {
                    holdingSticker = null;
                    break;
                }
            }
            if (holdingSticker == null) {
                return super.mouseClicked(mx, my, button);
            }
            // 4) qualquer outro lugar = SOLTA o adesivo. (3.52.0) FIM do
            // "morango fantasma": antes ele empilhava na fala E ficava
            // grudado no mouse — agora clicar fora do balao descarta.
            // Pra empilhar emoji, use a metade DIREITA do balao (ou os slots).
            holdingSticker = null;
            return true;
        }
        if (!showKeyboard && button == 0 && holdingSticker == null && removeBoxAt(mx, my)) {
            setSticker("", style.stickerX(), style.stickerY()); // tira o adesivo livre
            return true;
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
                        case 'B' -> {
                            lastBorda = SW_BD[idx];
                            style = withBorder(SW_BD[idx]);
                        }
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
