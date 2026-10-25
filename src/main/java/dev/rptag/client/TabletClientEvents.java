package dev.rptag.client;

import dev.rptag.RPTagMod;
import dev.rptag.TabletSyncPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = RPTagMod.MODID, value = Dist.CLIENT)
public final class TabletClientEvents {
    private static TabletClientState own;
    private TabletClientEvents() {}

    public static void receive(TabletSyncPayload packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        boolean self = packet.target().equals(mc.player.getUUID());
        // A consulta administrativa não substitui a fila de escrita da própria ficha.
        TabletClientState state;
        if (self && !(packet.readOnly() && packet.open() && packet.registro().podeEditar())) {
            if (own == null) own = new TabletClientState(packet, PacketDistributor::sendToServer, Util::getMillis);
            else own.accept(packet);
            state = own;
        } else state = new TabletClientState(packet, p -> {}, Util::getMillis);
        if (packet.open()) {
            if (mc.screen instanceof TabletScreen screen && screen.state() == state) screen.refresh();
            else mc.setScreen(new TabletScreen(state));
        } else if (mc.screen instanceof TabletScreen screen && screen.state() == state) screen.onServerUpdate();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (own != null && Minecraft.getInstance().getConnection() != null) own.tick();
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { own = null; }
}
