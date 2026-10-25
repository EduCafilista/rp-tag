package dev.rptag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * (4.9.0) REGRAS EM RP — AGORA AUTOMÁTICAS.
 *
 * <p>Antes eram três interruptores que o admin ligava com {@code /rp admin regra}
 * (e que ninguém achava: o comando era mais um passo). O comando SAIU — quem está
 * em RP já está protegido, sem configurar nada. Vale para TODOS os mundos:
 * <ul>
 *   <li><b>PvP</b> — cancela o golpe corpo a corpo ANTES do knockback/animação
 *       ({@link AttackEntityEvent}) <b>e</b> o dano indireto ({@link LivingIncomingDamageEvent}:
 *       flecha, tridente, poção, TNT — o que nomeia um jogador como responsável).
 *       Vale se QUALQUER um dos dois está em RP (a vítima em RP também é protegida);</li>
 *   <li><b>quebrar</b> — cancela {@link BlockEvent.BreakEvent} de quem está em RP;</li>
 *   <li><b>colocar</b> — cancela {@link BlockEvent.EntityPlaceEvent} e também o
 *       BALDE (água/lava/balde de mob), que não passa por EntityPlaceEvent.</li>
 * </ul>
 *
 * <p>Quem tem permissão de operador (nível 2) não é afetado — o admin precisa poder
 * montar cenário. O aviso é discreto, na barra de ação.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class RegrasRp {

    private RegrasRp() {
    }

    // ================================================================ PvP
    /** Golpe corpo a corpo: barra cedo (sem knockback, sem animação de dano). */
    @SubscribeEvent
    public static void aoAtacar(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer atacante) || !(e.getTarget() instanceof Player vitima)) return;
        RPWorldData wd = RPWorldData.get(atacante.server);
        if (!(wd.isInRp(atacante.getUUID()) || wd.isInRp(vitima.getUUID()))) return;
        if (atacante.hasPermissions(2)) return;          // staff passa
        e.setCanceled(true);
        avisar(atacante, "PvP bloqueado: um dos dois está em RP");
    }

    /** Dano indireto (flecha, tridente, poção, TNT): sem isto o PvP cairia com um arco. */
    @SubscribeEvent
    public static void aoTomarDano(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof Player vitima)) return;
        Entity responsavel = e.getSource().getEntity();
        if (!(responsavel instanceof Player atacante) || atacante == vitima) return;
        RPWorldData wd = RPWorldData.get(atacante.getServer());
        if (!(wd.isInRp(atacante.getUUID()) || wd.isInRp(vitima.getUUID()))) return;
        if (atacante.hasPermissions(2)) return;
        e.setCanceled(true);
        if (atacante instanceof ServerPlayer sp) avisar(sp, "PvP bloqueado: um dos dois está em RP");
    }

    // ================================================================ quebrar
    @SubscribeEvent
    public static void aoQuebrar(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p)) return;
        RPWorldData wd = RPWorldData.get(p.server);
        if (!wd.isInRp(p.getUUID()) || p.hasPermissions(2)) return;
        e.setCanceled(true);
        avisar(p, "Quebrar bloco está bloqueado em RP");
    }

    // ================================================================ colocar
    @SubscribeEvent
    public static void aoColocar(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        RPWorldData wd = RPWorldData.get(p.server);
        if (!wd.isInRp(p.getUUID()) || p.hasPermissions(2)) return;
        e.setCanceled(true);
        avisar(p, "Colocar bloco está bloqueado em RP");
    }

    /** Balde na mão clicando em bloco: não deixa usar o item (a interação normal continua). */
    @SubscribeEvent
    public static void aoClicarBloco(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        RPWorldData wd = RPWorldData.get(p.server);
        if (!wd.isInRp(p.getUUID()) || p.hasPermissions(2)) return;
        if (ehBaldeDeFluido(e.getItemStack())) e.setUseItem(TriState.FALSE);
    }

    /** Balde na mão olhando pro nada (ou pra cima): cancela com feedback. */
    @SubscribeEvent
    public static void aoUsarItem(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        RPWorldData wd = RPWorldData.get(p.server);
        if (!wd.isInRp(p.getUUID()) || p.hasPermissions(2)) return;
        if (!ehBaldeDeFluido(e.getItemStack())) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        avisar(p, "Colocar bloco está bloqueado em RP");
    }

    /** @return true se a pilha coloca FLUIDO no mundo (baldes de água, lava, leite? leite é bebida). */
    private static boolean ehBaldeDeFluido(ItemStack pilha) {
        if (pilha == null || pilha.isEmpty()) return false;
        // (1.21.1) BucketItem.content é private: quem responde é o FluidUtil.
        // O instanceof evita o custo de consultar fluido em toda pilha da hotbar.
        if (!(pilha.getItem() instanceof BucketItem)) return false;
        return FluidUtil.getFluidContained(pilha).map(fs -> !fs.isEmpty()).orElse(false);
    }

    private static void avisar(ServerPlayer p, String texto) {
        p.displayClientMessage(Component.literal(texto).withStyle(ChatFormatting.RED), true);
    }
}
