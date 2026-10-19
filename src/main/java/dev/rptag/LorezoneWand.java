package dev.rptag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * (4.0.0) VARINHA DE LOREZONE — ferramenta de admin.
 *
 * <p>Clique DIREITO com a varinha na mão: mostra (ou esconde) a BARREIRA da
 * lorezone mais próxima, com a faixa vertical em amarelo. O pacote vai SÓ
 * para quem clicou, e só se ele for admin (permissão 2) — players comuns não
 * veem nada nem conseguem usar.
 *
 * <p>Shift + clique direito = esconde.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class LorezoneWand {

    private LorezoneWand() {
    }

    /** evita o duplo disparo (RightClickBlock + RightClickItem no mesmo clique). */
    private static final java.util.Map<java.util.UUID, Long> ULTIMO = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (usar(event.getEntity() instanceof ServerPlayer sp ? sp : null, event.getItemStack())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (usar(event.getEntity() instanceof ServerPlayer sp ? sp : null, event.getItemStack())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    /** @return true se a varinha foi usada (consome o clique). */
    private static boolean usar(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null) {
            return false;
        }
        if (!stack.is(RPTagMod.LOREZONE_WAND.get())) {
            return false;
        }
        long agora = System.currentTimeMillis();
        Long ultimo = ULTIMO.get(player.getUUID());
        if (ultimo != null && agora - ultimo < 250L) {
            return false; // mesmo clique, ja tratado
        }
        ULTIMO.put(player.getUUID(), agora);

        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal(
                    "\u00a7cA Varinha de Lorezone so funciona para administradores."));
            return false;
        }

        LoreZones.LoreZonesData data = LoreZones.LoreZonesData.get(player.server);
        LoreZones.Zone perto = null;
        double melhor = Double.MAX_VALUE;
        for (LoreZones.Zone z : data.all()) {
            if (z.dimension() != player.level().dimension()) {
                continue;
            }
            double d = player.position().distanceTo(z.center()) - z.radius();
            if (d < melhor && d <= 256) {
                melhor = d;
                perto = z;
            }
        }
        if (perto == null) {
            player.sendSystemMessage(Component.literal(
                    "\u00a77[Voce nao esta perto de nenhuma lorezone (ate 256 blocos).]"));
            return true;
        }

        boolean esconder = player.isShiftKeyDown();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                new ZoneShowPayload(!esconder, perto.id(),
                        perto.center().x, perto.center().y, perto.center().z,
                        perto.radius(), perto.minY(), perto.maxY(), 0));
        player.sendSystemMessage(Component.literal(
                (esconder ? "\u00a77Barreira escondida: \u00a7f" : "\u00a7aBarreira visivel: \u00a7f")
                        + perto.id()
                        + "\u00a77 · raio " + Math.round(perto.radius())
                        + " · Y " + Math.round(perto.minY()) + " a " + Math.round(perto.maxY())
                        + (esconder ? "" : " \u00a78(clique com shift para esconder)")));
        return true;
    }
}
