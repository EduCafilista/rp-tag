package dev.rptag.king.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.rptag.king.IllusionEntity;
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

/** Cópia ilusória do Rei: o mesmo modelo e skin do dono, translúcida e amarelada, com brilho. */
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
        float age = e.tickCount + pt;
        model.young = false;
        model.setupAnim(ap, age * 0.4f, 0.55f, age, 0f, 0f);
        float bodyYaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
        float flick = 0.50f + 0.18f * Mth.sin(age * 0.35f);
        float fade = Mth.clamp((e.tickCount) / 6f, 0f, 1f);
        int a = (int) (255 * flick * fade);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180f - bodyYaw));
        ps.scale(-1f, -1f, 1f);
        ps.translate(0f, -1.501f, 0f);
        model.renderToBuffer(ps, buf.getBuffer(RenderType.entityTranslucent(skin.texture())), 0xF000F0, OverlayTexture.NO_OVERLAY,
                (a << 24) | 0xFFD84A);
        ps.popPose();
    }
}
