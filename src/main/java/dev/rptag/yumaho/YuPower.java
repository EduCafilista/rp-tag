package dev.rptag.yumaho;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Sistema de poder em %, compartilhado por todas as habilidades. Lê-se no servidor; o cliente só exibe. */
public final class YuPower {
    public static boolean enabled(Player p) { return p.getData(YuAttachments.ENABLED.get()); }
    public static int percent(Player p) { return Mth.clamp(p.getData(YuAttachments.POWER.get()), 0, 100); }
    /** 0..1 */
    public static float frac(Player p) { return percent(p) / 100f; }

    public static void set(ServerPlayer sp, int pct) {
        int v = Mth.clamp(pct, 0, 100);
        if (sp.getData(YuAttachments.POWER.get()) != v) sp.setData(YuAttachments.POWER.get(), v);
    }

    public static float lerp(double min, double max, float f) { return (float) (min + (max - min) * f); }

    private YuPower() {}
}
