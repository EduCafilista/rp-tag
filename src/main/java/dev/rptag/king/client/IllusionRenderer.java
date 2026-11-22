package dev.rptag.king.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rptag.king.IllusionEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * Cópia ilusória do Rei: copia o modelo completo do jogador (com integração nativa ao Custom Player Models — {@code cpm})
 * ou faz fallback para {@link PlayerModel} vanilla quando o jogador não usa modelo CPM ou o mod não está instalado.
 */
public class IllusionRenderer extends EntityRenderer<IllusionEntity> {
    private final PlayerModel<AbstractClientPlayer> wide, slim;

    public IllusionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        wide = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    @Override
    public ResourceLocation getTextureLocation(IllusionEntity e) {
        Entity o = e.level().getEntity(e.ownerId());
        return o instanceof AbstractClientPlayer ap ? ap.getSkin().texture() : DefaultPlayerSkin.getDefaultTexture();
    }

    @Override
    public void render(IllusionEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        Entity o = e.level().getEntity(e.ownerId());
        if (!(o instanceof AbstractClientPlayer ap)) return;

        PlayerSkin skin = ap.getSkin();
        PlayerModel<AbstractClientPlayer> model = skin.model() == PlayerSkin.Model.SLIM ? slim : wide;
        applyModelProperties(model, ap);

        float age = e.tickCount + pt;
        double dx = e.getX() - e.xo;
        double dz = e.getZ() - e.zo;
        float horizSpeed = (float) Math.sqrt(dx * dx + dz * dz);
        float limbSwingAmount = Mth.clamp(horizSpeed * 4.0f, 0.18f, 1.0f);
        float limbSwing = age * 0.45f;

        float bodyYaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float flick = 0.72f + 0.16f * Mth.sin(age * 0.35f);
        float fade = Mth.clamp(e.tickCount / 6f, 0f, 1f);
        int a = Mth.clamp((int) (255 * flick * fade), 0, 255);

        ps.pushPose();
        float entityScale = ap.getScale();
        ps.scale(entityScale, entityScale, entityScale);
        ps.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        ps.scale(-1f, -1f, 1f);
        ps.scale(0.9375f, 0.9375f, 0.9375f);
        ps.translate(0f, -1.501f, 0f);

        boolean renderedCpm = KingCpmCompat.renderIllusion(
                e, ap, model, skin.texture(),
                limbSwing, limbSwingAmount, age, 0f, 0f, bodyYaw, pt,
                ps, buf, 0xF000F0, a);

        if (!renderedCpm) {
            model.prepareMobModel(ap, limbSwing, limbSwingAmount, pt);
            model.setupAnim(ap, limbSwing, limbSwingAmount, age, 0f, 0f);
            model.renderToBuffer(
                    ps,
                    buf.getBuffer(RenderType.entityTranslucent(skin.texture())),
                    0xF000F0,
                    OverlayTexture.NO_OVERLAY,
                    (a << 24) | 0xFFD84A);
        }
        ps.popPose();
    }

    private static void applyModelProperties(PlayerModel<AbstractClientPlayer> model, AbstractClientPlayer ap) {
        model.setAllVisible(true);
        model.hat.visible = ap.isModelPartShown(PlayerModelPart.HAT);
        model.jacket.visible = ap.isModelPartShown(PlayerModelPart.JACKET);
        model.leftPants.visible = ap.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = ap.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        model.leftSleeve.visible = ap.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = ap.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        model.young = false;
        model.crouching = false;
        model.riding = false;
        model.attackTime = 0f;
        model.swimAmount = 0f;
        model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        model.rightArmPose = HumanoidModel.ArmPose.EMPTY;
    }
}
