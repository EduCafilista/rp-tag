package dev.rptag.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import dev.rptag.UpdateBubbleStylePayload;

/**
 * Tela de personalizacao do balao ({@code /balao}):
 *
 * <ul>
 *   <li>Preview ao vivo do balao no topo</li>
 *   <li>Sliders V/A/Z + 12 cores rapidas</li>
 *   <li>Emojis decorativos antes/depois (botoes rapidos ou digite os seus)</li>
 *   <li>7 fundos: translucido, escuro, claro, gradiente, papel, noite, madeira</li>
 * </ul>
 */
public final class BubbleStyleScreen extends Screen {

    private static final int[] PRESET_COLORS = {
            0xFFFFFF, 0xF5E6C8, 0xFFD700, 0xFF8C00, 0xFF5555, 0xFF69B4,
            0xC77DFF, 0x7B68EE, 0x55FFFF, 0x55FF7F, 0x00CED1, 0x8B5A2B
    };

    private static final String[] QUICK_EMOJIS = {"✦", "★", "♥", "⚔", "☾", "✿", "♪", "⚡"};
    private static final String[] BACKGROUND_NAMES = {
            "Translucido", "Escuro solido", "Claro solido", "Gradiente", "Papel", "Noite", "Madeira"
    };

    private int colorRGB;
    private final String initialPrefix;
    private final String initialSuffix;
    private int background;

    private EditBox prefixBox;
    private EditBox suffixBox;

    private BubbleStyleScreen(int colorRGB, String prefix, String suffix, int background) {
        super(Component.literal("Personalizar balao de fala"));
        this.colorRGB = colorRGB & 0xFFFFFF;
        this.initialPrefix = prefix == null ? "" : prefix;
        this.initialSuffix = suffix == null ? "" : suffix;
        this.background = background;
    }

    public static void open(int colorRGB, String prefix, String suffix, int background) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new BubbleStyleScreen(colorRGB, prefix, suffix, background));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = Math.max(40, this.height / 6);

        // ===== sliders V/A/Z =====
        int sliderW = 90;
        int gap = 8;
        int x0 = cx - (sliderW * 3 + gap * 2) / 2;
        this.addRenderableWidget(new ColorSlider(x0, top + 60, sliderW, 16, 0,
                ((this.colorRGB >> 16) & 0xFF) / 255.0));
        this.addRenderableWidget(new ColorSlider(x0 + sliderW + gap, top + 60, sliderW, 16, 1,
                ((this.colorRGB >> 8) & 0xFF) / 255.0));
        this.addRenderableWidget(new ColorSlider(x0 + (sliderW + gap) * 2, top + 60, sliderW, 16, 2,
                (this.colorRGB & 0xFF) / 255.0));

        // ===== cores rapidas =====
        int swatchSize = 16;
        int swatches = PRESET_COLORS.length;
        int sx0 = cx - (swatches * (swatchSize + 4) - 4) / 2;
        for (int i = 0; i < swatches; i++) {
            final int rgb = PRESET_COLORS[i];
            this.addRenderableWidget(new SwatchButton(sx0 + i * (swatchSize + 4), top + 84, swatchSize, swatchSize, rgb));
        }

        // ===== emojis / decoracao =====
        int boxW = 110;
        this.prefixBox = new EditBox(this.font, cx - boxW - 6, top + 112, boxW, 16, Component.literal("Antes"));
        this.prefixBox.setMaxLength(12);
        this.prefixBox.setValue(this.initialPrefix);
        this.prefixBox.setHint(Component.literal("emoji antes..."));
        this.suffixBox = new EditBox(this.font, cx + 6, top + 112, boxW, 16, Component.literal("Depois"));
        this.suffixBox.setMaxLength(12);
        this.suffixBox.setValue(this.initialSuffix);
        this.suffixBox.setHint(Component.literal("emoji depois..."));
        this.addRenderableWidget(this.prefixBox);
        this.addRenderableWidget(this.suffixBox);

        int qx0 = cx - (QUICK_EMOJIS.length * 22 - 6) / 2;
        for (int i = 0; i < QUICK_EMOJIS.length; i++) {
            String emoji = QUICK_EMOJIS[i];
            this.addRenderableWidget(Button.builder(Component.literal(emoji), (b) -> {
                        EditBox target = this.prefixBox.isFocused() ? this.prefixBox : this.suffixBox;
                        if (target.getValue().length() + emoji.length() <= 12) {
                            target.setValue(target.getValue() + emoji);
                        }
                    })
                    .bounds(qx0 + i * 22, top + 132, 20, 16)
                    .tooltip(Tooltip.create(Component.literal("adiciona na caixa em foco")))
                    .build());
        }

        // ===== fundo =====
        Button bgButton = Button.builder(
                        Component.literal("Fundo: ").withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(BACKGROUND_NAMES[this.background])),
                        (b) -> {
                            this.background = (this.background + 1) % BACKGROUND_NAMES.length;
                            b.setMessage(Component.literal("Fundo: ").withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(BACKGROUND_NAMES[this.background])));
                        })
                .bounds(cx - 100, top + 156, 200, 18)
                .tooltip(Tooltip.create(Component.literal("clique para trocar")))
                .build();
        this.addRenderableWidget(bgButton);

        // ===== salvar / cancelar =====
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, (b) -> this.save())
                .bounds(cx - 100, top + 180, 98, 18).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, (b) -> this.onClose())
                .bounds(cx + 2, top + 180, 98, 18).build());
    }

    private void setChannel(int channel, int value) {
        int shift = 16 - channel * 8;
        this.colorRGB = (this.colorRGB & ~(0xFF << shift)) | (value << shift);
    }

    private void save() {
        PacketDistributor.sendToServer(new UpdateBubbleStylePayload(this.colorRGB,
                this.prefixBox.getValue(), this.suffixBox.getValue(), this.background));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, Math.max(14, this.height / 6 - 16), 0xFFFFFF);

        // ===== preview do balao =====
        String before = this.prefixBox == null ? this.initialPrefix : this.prefixBox.getValue();
        String after = this.suffixBox == null ? this.initialSuffix : this.suffixBox.getValue();
        String sample = (before + " Falo por balao! " + after).trim();
        drawBubblePreview(g, sample, this.colorRGB, this.background, this.width / 2, Math.max(40, this.height / 6) + 30);

        if (this.prefixBox != null) {
            g.drawString(this.font, "Antes", this.prefixBox.getX(), this.prefixBox.getY() - 9, 0xAAAAAA, false);
            g.drawString(this.font, "Depois", this.suffixBox.getX(), this.suffixBox.getY() - 9, 0xAAAAAA, false);
        }
    }

    /** Desenha um balao 2D em coordenadas de tela (mesma arte do mundo). */
    static void drawBubblePreview(GuiGraphics g, String text, int colorRGB, int background, int cx, int cy) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int textW = font.width(text);
        int pillW = textW + 8;
        int pillH = 14;
        int x = cx - pillW / 2;
        int y = cy - pillH - 6;

        var pose = g.pose();
        pose.pushPose();
        var matrix = pose.last().pose();
        var buffer = g.bufferSource();
        var vc = buffer.getBuffer(BubbleBackgrounds.typeFor(background));

        BubbleBackgrounds.pill(vc, matrix, x, y, pillW, pillH, background, colorRGB);
        BubbleBackgrounds.tail(vc, matrix, x + pillW / 2 - 3, y + pillH - 1,
                x + pillW / 2 + 3, y + pillH - 1, x + pillW / 2, y + pillH + 5, background, colorRGB);
        buffer.endBatch();

        int textColor = BubbleBackgrounds.textColorFor(background, colorRGB);
        g.drawString(font, text, x + 4, y + 3, textColor, false);
        pose.popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Quadrado de cor rapida. */
    private final class SwatchButton extends AbstractButton {
        private final int rgb;

        private SwatchButton(int x, int y, int w, int h, int rgb) {
            super(x, y, w, h, Component.literal(String.format("#%06X", rgb)));
            this.rgb = rgb;
            setTooltip(Tooltip.create(Component.literal(String.format("#%06X", rgb))));
        }

        @Override
        public void onPress() {
            colorRGB = this.rgb;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(getX(), getY(), getX() + width, getY() + height, 0xFF000000 | this.rgb);
            boolean selected = this.rgb == BubbleStyleScreen.this.colorRGB;
            g.renderOutline(getX(), getY(), width, height, selected ? 0xFFFFFFFF : 0xFF3A3A44);
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    /** Slider 0-255 de um canal de cor. */
    private final class ColorSlider extends AbstractSliderButton {
        private final int channel;

        private ColorSlider(int x, int y, int w, int h, int channel, double initialValue) {
            super(x, y, w, h, Component.empty(), initialValue);
            this.channel = channel;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int v = (BubbleStyleScreen.this.colorRGB >> (16 - this.channel * 8)) & 0xFF;
            String name = this.channel == 0 ? "Vermelho" : this.channel == 1 ? "Verde" : "Azul";
            setMessage(Component.literal(name + ": " + v).withStyle(ChatFormatting.GRAY));
        }

        @Override
        protected void applyValue() {
            int v = Mth.clamp((int) Math.round(this.value * 255.0), 0, 255);
            setChannel(this.channel, v);
        }
    }
}
