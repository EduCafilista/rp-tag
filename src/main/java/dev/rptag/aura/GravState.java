package dev.rptag.aura;

import net.minecraft.world.entity.player.Player;

/**
 * Estado do personagem Gravidade num int sincronizado (AuraAnexos.GRAV):
 * bit0 = aura/escudo ligados | bits1-3 = campo (0 off, 1 esmagar, 2 flutuar, 3 atrair, 4 repelir)
 * bits4-5 = gravidade própria (0 normal, 1 pesada, 2 leve) | bit6 = pés na parede ativo
 */
public final class GravState {
    public static final int AURA = 1, WALL = 64;

    public static int get(Player p) { return p.getData(AuraAnexos.GRAV.get()); }
    public static boolean aura(int s) { return (s & AURA) != 0; }
    public static int field(int s) { return (s >> 1) & 7; }
    public static int self(int s) { return (s >> 4) & 3; }
    public static boolean wall(int s) { return (s & WALL) != 0; }

    public static int make(boolean aura, int field, int self, boolean wall) {
        return (aura ? AURA : 0) | (field << 1) | (self << 4) | (wall ? WALL : 0);
    }

    /** O jogador está com o personagem Gravidade (/aura gravidade). */
    public static boolean isGravity(Player p) {
        return p.getData(AuraAnexos.AURA.get()) == AuraEstilo.GRAVIDADE.index();
    }

    private GravState() {}
}
