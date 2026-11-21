package dev.rptag.yumaho.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rptag.yumaho.YuAbilities;
import dev.rptag.yumaho.YuActionPayload;
import dev.rptag.yumaho.YuAttachments;
import dev.rptag.yumaho.YuEntities;
import dev.rptag.yumaho.YuMaho;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Teclas, HUD de poder/energia e renderer da nuvem. O cliente nunca decide nada: só pede e mostra. */
public final class YuClient {
    private static final String CAT = "key.categories.rptag";
    public static final KeyMapping SMOKE = key("smoke", GLFW.GLFW_KEY_Z);
    // (5.78.0) o V ja e o RUGIDO do Macaco no rp-tag -> Fa Jin fica no F
    public static final KeyMapping FAJIN = key("fajin", GLFW.GLFW_KEY_F);
    public static final KeyMapping WHIP = key("whip", GLFW.GLFW_KEY_B);
    public static final KeyMapping GEAR = key("gear", GLFW.GLFW_KEY_N);
    public static final KeyMapping UP = key("power_up", GLFW.GLFW_KEY_PAGE_UP);
    public static final KeyMapping DOWN = key("power_down", GLFW.GLFW_KEY_PAGE_DOWN);
    public static final KeyMapping SCREEN = key("power_screen", GLFW.GLFW_KEY_U);
    // (5.78.5) pacote do Drive: AGARRAR (K), DESACELERAR o alvo (M) e SOLTAR os chicotes (Alt esquerdo)
    public static final KeyMapping GRAB = key("grab", GLFW.GLFW_KEY_K);
    public static final KeyMapping SLOW = key("gear_slow", GLFW.GLFW_KEY_M);
    public static final KeyMapping WHIP_RELEASE = key("whip_release", GLFW.GLFW_KEY_LEFT_ALT);

    private static KeyMapping key(String n, int k) {
        return new KeyMapping("key.yumaho." + n, InputConstants.Type.KEYSYM, k, CAT);
    }

    private static boolean wasFajin;

    private static void send(int action, int arg) { PacketDistributor.sendToServer(new YuActionPayload(action, arg)); }

    @EventBusSubscriber(modid = YuMaho.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(SMOKE); e.register(FAJIN); e.register(WHIP); e.register(GEAR);
            e.register(UP); e.register(DOWN); e.register(SCREEN);
            e.register(GRAB); e.register(SLOW); e.register(WHIP_RELEASE);
        }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerEntityRenderer(YuEntities.SMOKE.get(), NoopRenderer::new);
            e.registerEntityRenderer(YuEntities.WHIP.get(), WhipRenderer::new);
        }

        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent e) {
            e.registerAboveAll(YuMaho.rl("hud"), (g, dt) -> hud(g));
        }
    }

    @EventBusSubscriber(modid = YuMaho.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            if (p == null) { drain(); wasFajin = false; return; }
            // (5.78.5) agarrar NAO exige Yu-Maho: qualquer personagem com aura pega (o servidor decide)
            if (mc.screen == null) {
                while (GRAB.consumeClick()) send(YuAbilities.A_GRAB,
                        net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 1 : 0);
            } else {
                while (GRAB.consumeClick()) {}
            }
            if (!p.getData(YuAttachments.ENABLED.get())) { drain(); wasFajin = false; return; }
            if (mc.screen != null) {                       // menu aberto: solta a carga do Fa Jin se estava segurando
                drain();
                if (wasFajin) { send(YuAbilities.A_FAJIN_RELEASE, 0); wasFajin = false; }
                return;
            }
            while (SMOKE.consumeClick()) send(YuAbilities.A_SMOKE, 0);
            while (WHIP.consumeClick()) send(YuAbilities.A_WHIP, 0);
            while (GEAR.consumeClick()) send(YuAbilities.A_GEAR, 0);
            while (UP.consumeClick()) send(YuAbilities.A_POWER_DELTA, net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 1 : 5);
            while (DOWN.consumeClick()) send(YuAbilities.A_POWER_DELTA, net.minecraft.client.gui.screens.Screen.hasShiftDown() ? -1 : -5);
            while (SCREEN.consumeClick()) mc.setScreen(new YuWheelScreen());
            while (SLOW.consumeClick()) send(YuAbilities.A_GEAR_SLOW, 0);
            while (WHIP_RELEASE.consumeClick()) send(YuAbilities.A_WHIP_RELEASE, 0);
            boolean down = FAJIN.isDown();
            if (down && !wasFajin) send(YuAbilities.A_FAJIN_START, 0);
            if (!down && wasFajin) send(YuAbilities.A_FAJIN_RELEASE, 0);
            wasFajin = down;
        }

        private static void drain() {
            while (SMOKE.consumeClick()) {}
            while (WHIP.consumeClick()) {}
            while (GEAR.consumeClick()) {}
            while (UP.consumeClick()) {}
            while (DOWN.consumeClick()) {}
            while (SLOW.consumeClick()) {}
            while (WHIP_RELEASE.consumeClick()) {}
        }
    }

    // ------------------------------------------------------------------ HUD
    private static void hud(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !p.getData(YuAttachments.ENABLED.get())) return;
        int pct = Mth.clamp(p.getData(YuAttachments.POWER.get()), 0, 100);
        int energy = Mth.clamp(p.getData(YuAttachments.ENERGY.get()), 0, 100);
        int modoChicote = Mth.clamp(p.getData(YuAttachments.WHIP_MODE.get()), 0, 3);
        int w = 90, x = g.guiWidth() - w - 10, y = g.guiHeight() / 2 + 30;
        g.fill(x - 4, y - 14, x + w + 4, y + (energy > 0 ? 26 : 16), 0x88000000);
        g.drawString(mc.font, Component.translatable("hud.yumaho.power", pct), x, y - 10, 0xFFE566, true);
        // (5.78.5) qual modo do chicote esta escolhido na engrenagem
        g.drawString(mc.font, Component.translatable("hud.yumaho.whipmode." + modoChicote), x, y - 19, 0xFFB0FF, true);
        bar(g, x, y, w, 5, pct / 100f, 0xFFFFD84A, 0xFFFFFFFF);
        if (energy > 0) {
            g.drawString(mc.font, Component.translatable("hud.yumaho.energy", energy), x, y + 8, 0xFFFFFF, true);
            bar(g, x, y + 19, w, 3, energy / 100f, 0xFFFFFFFF, 0xFFFFF2A0);
        }
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, float f, int c0, int c1) {
        g.fill(x, y, x + w, y + h, 0xFF2A2A2A);
        int fw = Math.round(w * Mth.clamp(f, 0f, 1f));
        if (fw > 0) g.fillGradient(x, y, x + fw, y + h, c0, c1);
    }

    private YuClient() {}
}
