package dev.rptag.aura.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rptag.aura.RoarPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class MacacoClient {
    /**
     * Tecla padrão V — e NAO R: no RP Tag a R ja e "Poderes do Vazio" ({@code key.rptag.leque}).
     * O jogador troca em Controles se quiser.
     */
    public static final KeyMapping ROAR_KEY = new KeyMapping(
            "key.rptag.rugido", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.rptag");

    @EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) { e.register(ROAR_KEY); }
    }

    @EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            while (ROAR_KEY.consumeClick()) {
                if (Minecraft.getInstance().player != null) PacketDistributor.sendToServer(new RoarPayload());
            }
        }
    }

    private MacacoClient() {}
}
