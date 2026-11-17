package dev.rptag.aura.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rptag.aura.GravState;
import dev.rptag.aura.GravityActionPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class GravityClient {
    /**
     * (5.73.0) H abre a roda de poderes; J liga/desliga a aura (os poderes continuam).
     * A tecla G NAO e usada aqui: ela ja e o poder dos portoes da iluminacao.
     */
    public static final KeyMapping WHEEL_KEY = new KeyMapping(
            "key.rptag.gravity_wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.rptag");
    public static final KeyMapping AURA_KEY = new KeyMapping(
            "key.rptag.gravity_aura", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.rptag");

    @EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) { e.register(WHEEL_KEY); e.register(AURA_KEY); }
    }

    @EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            while (WHEEL_KEY.consumeClick()) {
                // (5.73.0) sem aviso: o dono nao quer frase de poder aparecendo na tela
                if (GravState.isGravity(mc.player)) mc.setScreen(new GravityWheelScreen());
            }
            while (AURA_KEY.consumeClick()) PacketDistributor.sendToServer(new GravityActionPayload(0, 0));
        }
    }

    private GravityClient() {}
}
