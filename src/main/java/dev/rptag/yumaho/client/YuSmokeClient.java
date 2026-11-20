package dev.rptag.yumaho.client;

import dev.rptag.yumaho.SmokeCloudEntity;
import dev.rptag.yumaho.YuMaho;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Visão de verdade tapada dentro do Smokescreen: névoa curta (3 blocos), cor cinza e véu na tela.
 * Quem lançou enxerga melhor (35%) que os outros.
 */
public final class YuSmokeClient {
    private static float factor, target;
    private static final float G = 0.60f;

    @EventBusSubscriber(modid = YuMaho.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            target = 0f;
            if (mc.player != null && mc.level != null) {
                Vec3 eye = mc.player.getEyePosition();
                for (SmokeCloudEntity c : mc.level.getEntitiesOfClass(SmokeCloudEntity.class, mc.player.getBoundingBox().inflate(14))) {
                    double d = eye.distanceTo(c.position().add(0, c.radius() * 0.25, 0));
                    float r = c.radius();
                    if (d > r) continue;
                    float depth = 1f - (float) (d / r);
                    float k = Mth.clamp(0.55f + 0.9f * depth, 0f, 1f) * c.fade();
                    if (c.ownerId() == mc.player.getId()) k *= 0.35f;
                    target = Math.max(target, k);
                }
            }
            factor += (target - factor) * (target > factor ? 0.25f : 0.08f);
            if (factor < 0.003f) factor = 0f;
        }

        @SubscribeEvent
        public static void fog(ViewportEvent.RenderFog e) {
            if (factor <= 0.01f) return;
            e.setNearPlaneDistance(Mth.lerp(factor, e.getNearPlaneDistance(), 0f));
            e.setFarPlaneDistance(Mth.lerp(factor, e.getFarPlaneDistance(), 3.0f));
            e.setFogShape(FogShape.SPHERE);
            e.setCanceled(true);
        }

        @SubscribeEvent
        public static void color(ViewportEvent.ComputeFogColor e) {
            if (factor <= 0.01f) return;
            e.setRed(Mth.lerp(factor, e.getRed(), G));
            e.setGreen(Mth.lerp(factor, e.getGreen(), G));
            e.setBlue(Mth.lerp(factor, e.getBlue(), G));
        }
    }

    @EventBusSubscriber(modid = YuMaho.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent e) {
            e.registerAboveAll(YuMaho.rl("smoke_veil"), (g, dt) -> {
                if (factor <= 0.01f) return;
                int a = (int) (Mth.clamp(factor * 0.55f, 0f, 0.75f) * 255);
                g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0x9A9A9A);
            });
        }
    }

    private YuSmokeClient() {}
}
