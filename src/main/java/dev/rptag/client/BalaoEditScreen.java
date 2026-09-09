package dev.rptag.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import dev.rptag.BalaoEditMenu;

/**
 * Tela do EDITOR do balao (client-side): e um bau de 6 fileiras com a
 * textura NATIVA do bau do jogo — nada de desenho custom, zero risco.
 */
public class BalaoEditScreen extends AbstractContainerScreen<BalaoEditMenu> {

    private static final ResourceLocation CONTAINER_BG =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    private static final int ROWS = 6;

    public BalaoEditScreen(BalaoEditMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageHeight = 114 + ROWS * 18;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        // parte de cima (as 6 fileiras) + parte de baixo (barra do inventario)
        graphics.blit(CONTAINER_BG, x, y, 0, 0, this.imageWidth, ROWS * 18 + 17, 256, 256);
        graphics.blit(CONTAINER_BG, x, y + ROWS * 18 + 17, 0, 126, this.imageWidth, 96, 256, 256);
    }
}
