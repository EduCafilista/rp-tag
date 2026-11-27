package dev.rptag.vampire.client;

import dev.rptag.vampire.VampireLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Barra de sangue: 10 gotas no lugar da barra de fome (cada gota = 2 pontos).
 * Otimizada (5.78.18): retângulos agrupados por linha (sem textura e sem 810 draws soltos).
 */
public final class BloodHud {
    // 0 = vazio, 1 = contorno, 2 = miolo, 3 = brilho, 4 = base escura
    private static final byte[][] DROP_MAP = new byte[9][9];
    private static final int OUT = 0xFF2A0006, FULL = 0xFFC8101E, SHINE = 0xFFFF8A94, EMPTY = 0xFF140306, DARK = 0xFF7A0A14;

    static {
        String[] raw = {
                "....#....",
                "....#....",
                "...###...",
                "...###...",
                "..#####..",
                ".#######.",
                ".#######.",
                ".#######.",
                "..#####.."};
        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                if (raw[y].charAt(x) != '#') continue;
                boolean edge = x == 0 || x == 8 || y == 0 || y == 8
                        || raw[y].charAt(x - 1) != '#' || raw[y].charAt(x + 1) != '#'
                        || raw[y - 1].charAt(x) != '#' || raw[y + 1].charAt(x) != '#';
                if (edge) DROP_MAP[y][x] = 1;
                else if (x == 3 && (y == 5 || y == 6)) DROP_MAP[y][x] = 3;
                else if (y >= 7) DROP_MAP[y][x] = 4;
                else DROP_MAP[y][x] = 2;
            }
        }
    }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !VampireLogic.isVampire(p)) return;
        if (mc.gameMode == null || !mc.gameMode.canHurtPlayer()) return;             // criativo/espectador: sem barra
        if (p.getVehicle() instanceof LivingEntity) return;                           // montado: o jogo mostra a vida da montaria

        int food = p.getFoodData().getFoodLevel();
        int left = g.guiWidth() / 2 + 91, top = g.guiHeight() - 39;
        boolean low = food <= 6;
        for (int i = 0; i < 10; i++) {
            int x = left - i * 8 - 9, y = top;
            if (low && (p.tickCount + i * 3) % 24 < 3) y += 1;                       // treme de leve quando está com pouco
            int pts = food - i * 2;                                                   // 2 = cheia, 1 = metade
            drop(g, x, y, pts >= 2 ? 2 : Math.max(pts, 0));
        }
    }

    private static int pixelColor(int x, byte kind, int fill) {
        if (kind == 1) return OUT;
        boolean on = fill >= 2 || (fill == 1 && x <= 4);
        if (!on) return EMPTY;
        if (kind == 3) return SHINE;
        if (kind == 4) return DARK;
        return FULL;
    }

    private static void drop(GuiGraphics g, int x0, int y0, int fill) {
        for (int y = 0; y < 9; y++) {
            int runStart = -1;
            int runCol = 0;
            for (int x = 0; x < 9; x++) {
                byte kind = DROP_MAP[y][x];
                if (kind == 0) {
                    if (runStart >= 0) {
                        g.fill(x0 + runStart, y0 + y, x0 + x, y0 + y + 1, runCol);
                        runStart = -1;
                    }
                    continue;
                }
                int col = pixelColor(x, kind, fill);
                if (runStart < 0) {
                    runStart = x;
                    runCol = col;
                } else if (col != runCol) {
                    g.fill(x0 + runStart, y0 + y, x0 + x, y0 + y + 1, runCol);
                    runStart = x;
                    runCol = col;
                }
            }
            if (runStart >= 0) {
                g.fill(x0 + runStart, y0 + y, x0 + 9, y0 + y + 1, runCol);
            }
        }
    }

    private BloodHud() {}
}
