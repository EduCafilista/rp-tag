package dev.rptag.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;

/** O helper do inventário restaura a pose da entidade depois de renderizar. */
public final class TabletPlayerPreview {
    private TabletPlayerPreview() {}

    public static void render(GuiGraphics graphics, UUID target, int x, int y, int width, int height, int etapa, boolean motion) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.level == null ? null : mc.level.getPlayerByUUID(target);
        if (player == null) return;
        int scale = Math.max(12, Math.min(width * 37 / 100, height * 40 / 100));
        float glance = motion ? (etapa - 2) * .025f : 0;
        InventoryScreen.renderEntityInInventoryFollowsAngle(graphics, x, y, x + width, y + height,
                scale, .0625f, -.12f + glance, -.02f, player);
    }
}
