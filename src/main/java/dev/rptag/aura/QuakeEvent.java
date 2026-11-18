package dev.rptag.aura;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Estado global do terremoto. O visual (céu carmesim, lua de sangue, tela balançando) roda no cliente.
 * Integração com o seu /terremoto iniciar:   QuakeEvent.start(server, 1.0f);   e no parar:   QuakeEvent.stop(server);
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class QuakeEvent {
    private static boolean active = false;
    private static float intensity = 1.0f;

    public static boolean isActive() { return active; }

    /** intensity: 0.2 (leve) .. 3.0 (violento); 1.0 é o padrão. */
    public static void start(MinecraftServer server, float intensity) {
        active = true;
        QuakeEvent.intensity = Math.max(0.2f, Math.min(3f, intensity));
        PacketDistributor.sendToAllPlayers(new QuakeStatePayload(true, QuakeEvent.intensity));
    }

    public static void stop(MinecraftServer server) {
        active = false;
        PacketDistributor.sendToAllPlayers(new QuakeStatePayload(false, intensity));
    }

    /** Quem entra no meio do evento já vê o céu vermelho. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (active && e.getEntity() instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new QuakeStatePayload(true, intensity));
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) { active = false; }

    private QuakeEvent() {}
}
