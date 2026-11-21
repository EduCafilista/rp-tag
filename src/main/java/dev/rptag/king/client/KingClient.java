package dev.rptag.king.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rptag.king.King;
import dev.rptag.king.KingAbilities;
import dev.rptag.king.KingEntities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Tecla Y abre a roda de habilidades do Rei (só quem tem a aura do Rei). */
public final class KingClient {
    public static final KeyMapping WHEEL = new KeyMapping("key.carcosa.wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.rptag");

    @EventBusSubscriber(modid = King.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) { e.register(WHEEL); }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(KingEntities.ILLUSION.get(), IllusionRenderer::new);
        }

        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent e) {
            e.registerAboveAll(King.rl("passive"), (g, dt) -> KingPassiveClient.hud(g, dt.getGameTimeDeltaPartialTick(false)));
        }
    }

    @EventBusSubscriber(modid = King.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null || mc.screen != null) { while (WHEEL.consumeClick()) {} return; }
            while (WHEEL.consumeClick()) {
                if (KingAbilities.isKing(p)) mc.setScreen(new KingWheelScreen());
                else p.displayClientMessage(Component.translatable("msg.king.notking"), true);
            }
        }
    }

    private KingClient() {}
}
