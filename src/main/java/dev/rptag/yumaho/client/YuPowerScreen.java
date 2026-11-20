package dev.rptag.yumaho.client;

import dev.rptag.yumaho.YuAbilities;
import dev.rptag.yumaho.YuActionPayload;
import dev.rptag.yumaho.YuAttachments;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tela para escolher a % de poder: barra + atalhos 0/25/50/75/100. O servidor valida o valor. */
public class YuPowerScreen extends Screen {
    public YuPowerScreen() { super(Component.translatable("screen.yumaho.power")); }

    private int current() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getData(YuAttachments.POWER.get()) : 50;
    }

    private void apply(int v) {
        PacketDistributor.sendToServer(new YuActionPayload(YuAbilities.A_SET_POWER, v));
        if (minecraft != null && minecraft.player != null) minecraft.player.setData(YuAttachments.POWER.get(), v);  // resposta imediata; o servidor confirma
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        addRenderableWidget(new AbstractSliderButton(cx - 100, cy - 10, 200, 20,
                Component.literal(current() + "%"), current() / 100.0) {
            @Override protected void updateMessage() { setMessage(Component.literal(Math.round(value * 100) + "%")); }
            @Override protected void applyValue() {}
            @Override public void onRelease(double mx, double my) {
                super.onRelease(mx, my);
                apply((int) Math.round(value * 100));
            }
        });
        int[] presets = {0, 25, 50, 75, 100};
        for (int i = 0; i < presets.length; i++) {
            int v = presets[i];
            addRenderableWidget(Button.builder(Component.literal(v + "%"), b -> { apply(v); rebuildWidgets(); })
                    .bounds(cx - 100 + i * 41, cy + 18, 38, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(cx - 50, cy + 46, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 34, 0xFFE566);
    }
}
