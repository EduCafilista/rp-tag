package dev.rptag.vampire.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import dev.rptag.aura.AuraAnexos;
import dev.rptag.vampire.Vampire;
import dev.rptag.vampire.VampireLogic;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.lwjgl.glfw.GLFW;

/** Tecla V: roda do vampiro. Troca a barra de fome por gotas de sangue e desenha o morceguinho. */
public final class VampireClient {
    public static final KeyMapping WHEEL = new KeyMapping("key.vampire.wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.rptag");
    public static final ModelLayerLocation BAT_LAYER = new ModelLayerLocation(Vampire.rl("vampire_bat"), "main");
    private static final ResourceLocation BAT_TEX = Vampire.rl("textures/entity/vampire_bat.png");
    private static BatCuteModel batModel;

    private static BatCuteModel model() {
        if (batModel == null) batModel = new BatCuteModel(Minecraft.getInstance().getEntityModels().bakeLayer(BAT_LAYER));
        return batModel;
    }

    @EventBusSubscriber(modid = Vampire.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) { e.register(WHEEL); }

        @SubscribeEvent
        public static void layerDefs(EntityRenderersEvent.RegisterLayerDefinitions e) {
            e.registerLayerDefinition(BAT_LAYER, BatCuteModel::layer);
        }

        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent e) {
            e.registerAboveAll(Vampire.rl("blood"), (g, dt) -> BloodHud.render(g));
        }
    }

    @EventBusSubscriber(modid = Vampire.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer p = mc.player;
            VampireFxClient.tick();
            if (p == null || mc.screen != null) { while (WHEEL.consumeClick()) {} return; }
            while (WHEEL.consumeClick()) {
                if (VampireLogic.isVampire(p)) {
                    mc.setScreen(new VampireWheelScreen());
                } else if (!Boolean.TRUE.equals(p.getData(AuraAnexos.MACACO.get()))) {
                    p.displayClientMessage(Component.translatable("msg.vampire.not_vampire"), true);
                }
            }
        }

        /** A barra de fome do jogo some para vampiros; a de gotas (BloodHud) ocupa o lugar. */
        @SubscribeEvent
        public static void hideFood(RenderGuiLayerEvent.Pre e) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null && e.getName().equals(VanillaGuiLayers.FOOD_LEVEL) && VampireLogic.isVampire(p)) e.setCanceled(true);
        }

        @SubscribeEvent
        public static void hand(RenderHandEvent e) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p != null && VampireLogic.isBat(p)) e.setCanceled(true);       // morcego não tem braço
        }

        /** Jogador na forma de morcego: troca o modelo inteiro pelo morceguinho (HIGHEST = antes do WallWalkClient). */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void renderPlayer(RenderPlayerEvent.Pre e) {
            Player p = e.getEntity();
            if (!VampireLogic.isBat(p)) return;
            e.setCanceled(true);
            float pt = e.getPartialTick();
            var ps = e.getPoseStack();
            float body = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
            float head = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot) - body;
            float pitch = Mth.lerp(pt, p.xRotO, p.getXRot());
            float age = p.tickCount + pt;
            boolean fast = !p.onGround() || p.getDeltaMovement().horizontalDistanceSqr() > 1.0E-3;

            ps.pushPose();
            ps.translate(0.0, 0.3, 0.0);                       // centro da hitbox pequena
            ps.mulPose(Axis.YP.rotationDegrees(180f - body));
            ps.scale(-0.7f, -0.7f, 0.7f);                      // mesma inversão do renderer de seres vivos
            BatCuteModel m = model();
            m.setup(age, fast, (float) Math.toRadians(head), (float) Math.toRadians(pitch));
            var vc = e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(BAT_TEX));
            m.render(ps, vc, e.getPackedLight(), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
    }

    private VampireClient() {}
}
