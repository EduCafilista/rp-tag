package dev.rptag.yumaho;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Dimensão Branca e Amarela + troca segura de inventário.
 *
 * Regras (tudo no servidor, na mesma tick, sem item sobrando em dois lugares):
 *  entrar: salva inventário real no cofre -> esvazia -> carrega o inventário branco do cofre.
 *  sair:   salva inventário branco no cofre -> esvazia -> devolve o real.
 *  morrer lá: guarda o branco, esvazia (nada cai no chão) e, ao renascer, devolve o real.
 *  /tp, portal, relog, crash: eventos abaixo corrigem o estado de forma idempotente.
 */
@EventBusSubscriber(modid = YuMaho.MODID)
public final class YuWhite {
    public static final ResourceKey<Level> WHITE = ResourceKey.create(Registries.DIMENSION, YuMaho.rl("white"));

    public static boolean inWhite(ServerPlayer sp) { return sp.level().dimension().equals(WHITE); }

    // ------------------------------------------------------------------ troca de inventário (idempotente)
    /** Passa para o inventário branco. Não mexe em posição. */
    public static void swapIn(ServerPlayer sp) {
        WhiteVault v = WhiteVault.get(sp.server);
        WhiteVault.Entry e = v.entry(sp.getUUID());
        if (e.inWhite) return;
        sp.closeContainer();
        Inventory inv = sp.getInventory();
        e.real = new ListTag();
        inv.save(e.real);
        inv.clearContent();
        inv.load(e.white);
        e.inWhite = true;
        e.dead = false;
        v.setDirty();
        refresh(sp);
    }

    /** Volta ao inventário real. Não mexe em posição. */
    public static void swapOut(ServerPlayer sp) {
        WhiteVault v = WhiteVault.get(sp.server);
        WhiteVault.Entry e = v.peek(sp.getUUID());
        if (e == null || !e.inWhite) return;
        sp.closeContainer();
        Inventory inv = sp.getInventory();
        if (!e.dead) {                       // se morreu, o branco já foi guardado antes de esvaziar
            e.white = new ListTag();
            inv.save(e.white);
        }
        inv.clearContent();
        inv.load(e.real);
        e.real = new ListTag();
        e.inWhite = false;
        e.dead = false;
        v.setDirty();
        refresh(sp);
    }

    private static void refresh(ServerPlayer sp) {
        sp.inventoryMenu.broadcastChanges();
        sp.inventoryMenu.sendAllDataToRemote();
    }

    // ------------------------------------------------------------------ comandos
    public static boolean enter(ServerPlayer sp) {
        MinecraftServer s = sp.server;
        ServerLevel w = s.getLevel(WHITE);
        if (w == null) { sp.sendSystemMessage(Component.translatable("msg.yumaho.white.missing")); return false; }
        if (inWhite(sp)) return false;
        WhiteVault v = WhiteVault.get(s);
        WhiteVault.Entry e = v.entry(sp.getUUID());
        e.retDim = sp.level().dimension().location().toString();
        e.rx = sp.getX(); e.ry = sp.getY(); e.rz = sp.getZ();
        e.ryaw = sp.getYRot(); e.rpitch = sp.getXRot();
        e.hasRet = true;
        v.setDirty();
        swapIn(sp);                                          // 1) cofre + troca  2) só então teleporta
        BlockPos p = spawnPos(w);
        w.setDefaultSpawnPos(p, 0f);                         // renascer aqui também cai no chão
        sp.teleportTo(w, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, sp.getYRot(), 0f);
        return true;
    }

    /**
     * (5.78.1) Ponto SEGURO no branco.
     * O bug: o chunk central ainda não existia quando getHeight() foi chamado, então ele devolvia
     * o min_y (y=0) e o jogador nascia DENTRO da bedrock. Agora o chunk é gerado antes e, se o
     * chão não vier, um piso de quartzo é assentado na hora.
     */
    public static BlockPos spawnPos(ServerLevel w) {
        final int x = 0, z = 0;
        w.getChunk(x >> 4, z >> 4);                                  // 1) gera o chunk (isso é o conserto)
        int min = w.getMinBuildHeight();
        int y = w.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= min + 1) y = min + 64;                              // 2) piso do branco: topo em min_y + 63
        BlockPos p = new BlockPos(x, y, z);
        if (!w.getBlockState(p.below()).isSolid()) {                 // 3) sem chão? assenta um
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    w.setBlockAndUpdate(p.offset(dx, -1, dz), Blocks.QUARTZ_BLOCK.defaultBlockState());
        }
        return p;
    }

    public static boolean exit(ServerPlayer sp) {
        MinecraftServer s = sp.server;
        WhiteVault v = WhiteVault.get(s);
        WhiteVault.Entry e = v.peek(sp.getUUID());
        if (!inWhite(sp) && (e == null || !e.inWhite)) return false;
        swapOut(sp);
        ServerLevel dest = null;
        double x, y, z; float yaw = 0, pitch = 0;
        if (e != null && e.hasRet) {
            dest = s.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(e.retDim)));
            x = e.rx; y = e.ry; z = e.rz; yaw = e.ryaw; pitch = e.rpitch;
        } else { x = y = z = 0; }
        if (dest == null) {
            dest = s.overworld();
            BlockPos sp0 = dest.getSharedSpawnPos();
            x = sp0.getX() + 0.5; y = sp0.getY(); z = sp0.getZ() + 0.5;
        }
        sp.teleportTo(dest, x, y, z, yaw, pitch);
        return true;
    }

    // ------------------------------------------------------------------ eventos de segurança
    /** Mudou de dimensão por qualquer meio (/tp, /execute, mod): acerta o inventário. */
    @SubscribeEvent
    public static void changed(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (e.getTo().equals(WHITE)) swapIn(sp); else swapOut(sp);
    }

    /**
     * (5.78.1) Rede de segurança: quem já ficou preso abaixo da bedrock (posição salva de antes
     * do conserto, /tp errado, portal) é puxado para a superfície do branco no tick seguinte.
     */
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !inWhite(sp)) return;
        ServerLevel w = sp.server.getLevel(WHITE);
        if (w == null || sp.getY() >= w.getMinBuildHeight() + 1) return;
        BlockPos p = spawnPos(w);
        sp.teleportTo(w, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, sp.getYRot(), sp.getXRot());
    }

    /** Login: se o estado do cofre e a dimensão não batem (crash, dimensão removida), corrige. */
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        WhiteVault.Entry en = WhiteVault.get(sp.server).peek(sp.getUUID());
        boolean vaultIn = en != null && en.inWhite;
        if (inWhite(sp) && !vaultIn) swapIn(sp);
        else if (!inWhite(sp) && vaultIn) swapOut(sp);
    }

    /** Morte dentro da dimensão: guarda o inventário branco e esvazia para nada cair no chão. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !inWhite(sp)) return;
        WhiteVault v = WhiteVault.get(sp.server);
        WhiteVault.Entry en = v.peek(sp.getUUID());
        if (en == null || !en.inWhite) return;
        en.white = new ListTag();
        sp.getInventory().save(en.white);
        sp.getInventory().clearContent();
        en.dead = true;
        v.setDirty();
    }

    /** Renasceu: devolve o inventário real. */
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) swapOut(sp);
    }

    private YuWhite() {}
}
