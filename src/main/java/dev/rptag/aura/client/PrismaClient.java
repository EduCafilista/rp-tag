package dev.rptag.aura.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rptag.aura.PrismaPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * (5.70.0) A TECLA DO PRISMA CELESTIAL — <b>C</b> (mudavel em Controles).
 *
 * <p>So manda o pacote pro servidor; quem decide se pode (ADM + aura ligada + recarga) e o
 * {@link dev.rptag.aura.PrismaCelestial}. As outras teclas do mod continuam: G (iluminacao),
 * V (rugido), R (roda de poderes), X (repetir).
 */
public final class PrismaClient {
    public static final KeyMapping KEY = new KeyMapping(
            "key.rptag.prisma", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.rptag");

    @EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) { e.register(KEY); }
    }

    @EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            while (KEY.consumeClick()) {
                if (Minecraft.getInstance().player != null) PacketDistributor.sendToServer(new PrismaPayload());
            }
        }
    }

    private PrismaClient() {}
}
