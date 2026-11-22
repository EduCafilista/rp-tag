package dev.rptag.king.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tom.cpm.client.CustomPlayerModelsClient;
import com.tom.cpm.client.ModelTexture;
import com.tom.cpm.client.PlayerRenderManager;
import com.tom.cpm.shared.animation.AnimationEngine;
import com.tom.cpm.shared.animation.AnimationState;
import com.tom.cpm.shared.config.Player;
import com.tom.cpm.shared.definition.ModelDefinition;
import com.tom.cpm.shared.model.RenderManager;
import com.tom.cpm.shared.model.RootModelType;
import com.tom.cpm.shared.model.TextureSheetType;
import dev.rptag.king.IllusionEntity;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

/**
 * Integração opcional com o mod Custom Player Models ({@code cpm}) para as cópias do Rei de Amarelo.
 *
 * <p>Isola todas as referências a {@code com.tom.cpm.*} na subclasse interna {@link Impl}, carregada
 * apenas quando {@code ModList.get().isLoaded("cpm")} for verdadeiro. Assim o mod roda sem CPM
 * instalado (cliente e servidor dedicado) e, quando o CPM está presente, renderiza o modelo 3D
 * customizado completo do jogador (geometria extra, texturas dinâmicas, partes vanilla ocultas ou
 * redimensionadas, animações da cópia e capa customizada).
 */
public final class KingCpmCompat {
    public static final String CPM_MODID = "cpm";
    private static Boolean loaded;
    private static boolean failed;

    private KingCpmCompat() {}

    public static boolean isLoaded() {
        Boolean b = loaded;
        if (b == null) {
            b = ModList.get().isLoaded(CPM_MODID);
            loaded = b;
        }
        return b && !failed;
    }

    /**
     * Tenta desenhar a cópia usando o modelo CPM do jogador dono {@code ap}.
     *
     * @return {@code true} se o jogador tem um {@code ModelDefinition} CPM ativo e foi desenhado;
     *         {@code false} para cair no fallback vanilla ({@link PlayerModel}).
     */
    public static boolean renderIllusion(
            IllusionEntity illusion,
            AbstractClientPlayer ap,
            PlayerModel<AbstractClientPlayer> model,
            ResourceLocation defaultSkin,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            float bodyYaw,
            float partialTick,
            PoseStack ps,
            MultiBufferSource buf,
            int light,
            int alpha) {
        if (!isLoaded()) return false;
        try {
            return Impl.render(illusion, ap, model, defaultSkin, limbSwing, limbSwingAmount,
                    ageInTicks, netHeadYaw, headPitch, bodyYaw, partialTick, ps, buf, light, alpha);
        } catch (Throwable t) {
            failed = true;
            return false;
        }
    }

    private static final class Impl {
        @SuppressWarnings({"unchecked", "rawtypes"})
        static boolean render(
                IllusionEntity e,
                AbstractClientPlayer ap,
                PlayerModel<AbstractClientPlayer> model,
                ResourceLocation defaultSkin,
                float limbSwing,
                float limbSwingAmount,
                float ageInTicks,
                float netHeadYaw,
                float headPitch,
                float bodyYaw,
                float pt,
                PoseStack ps,
                MultiBufferSource buf,
                int light,
                int alpha) {
            CustomPlayerModelsClient cpm = CustomPlayerModelsClient.INSTANCE;
            if (cpm == null || CustomPlayerModelsClient.mc == null || cpm.manager == null) return false;

            RenderManager<GameProfile, net.minecraft.world.entity.player.Player, Model, MultiBufferSource> mgr = cpm.manager;
            Player<net.minecraft.world.entity.player.Player> profile = mgr.loadPlayerProfile(ap.getGameProfile(), ap, "player");
            if (profile == null) return false;
            ModelDefinition def = profile.getModelDefinition();
            if (def == null) return false;

            PlayerRenderManager prm = CustomPlayerModelsClient.mc.getPlayerRenderManager();
            if (prm == null) return false;

            // Prepara o estado base a partir do jogador dono (equipamentos, camadas de skin, gestos/poses sincronizados)
            // e sobrescreve o movimento/orientação com os dados reais da própria cópia ilusória.
            AnimationState anim = new AnimationState(AnimationEngine.AnimationMode.PLAYER);
            mgr.prepareAnimationState(profile, ap, anim);
            anim.moveAmountX = (float) (e.getX() - e.xo);
            anim.moveAmountY = (float) (e.getY() - e.yo);
            anim.moveAmountZ = (float) (e.getZ() - e.zo);
            anim.yaw = bodyYaw;
            anim.bodyYaw = bodyYaw;
            anim.pitch = headPitch;
            anim.sneaking = false;
            anim.sleeping = false;
            anim.dying = false;
            anim.riding = false;
            anim.elytraFlying = false;
            anim.swimming = false;
            anim.crawling = false;
            anim.tridentSpin = false;
            anim.sprinting = limbSwingAmount > 0.65f;
            anim.firstPersonMod = false;
            anim.inGui = false;
            anim.invisible = false;

            boolean bound = false;
            try {
                // arg = null -> RedirectHolderPlayer (todas as 13 partes de PlayerModel: head, body, braços, pernas, hat, sleeves, pants, jacket, cloak)
                mgr.bindPlayerState(profile, buf, model, null, anim);
                bound = prm.isBound(model);
                if (!bound) return false;

                model.prepareMobModel(ap, limbSwing, limbSwingAmount, pt);
                model.setupAnim(ap, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

                // CRÍTICO: bindSkin inicializa holder.renderTypes (RenderTypeBuilder) e resolve a textura dinâmica do CPM.
                // Sem bindSkin antes de renderToBuffer, RedirectModelRendererVanilla cai no parent vanilla!
                ModelTexture mt = new ModelTexture(defaultSkin, RenderType::entityTranslucent);
                prm.bindSkin(model, mt, TextureSheetType.SKIN);
                ResourceLocation tex = mt.getTexture();
                if (tex == null) tex = defaultSkin;
                RenderType rt = mt.getRenderType();
                if (rt == null) rt = RenderType.entityTranslucent(tex);

                // Mantém as cores reais do modelo CPM (0xFFF6D6: quase branco com leve calor dourado) e a transparência da ilusão,
                // para que o modelo 3D e suas texturas apareçam fielmente sem virar uma mancha amarela chapada.
                int bodyColor = (Mth.clamp(alpha, 0, 255) << 24) | 0xFFF6D6;
                model.renderToBuffer(ps, buf.getBuffer(rt), light, OverlayTexture.NO_OVERLAY, bodyColor);

                // Descarrega os cubos do corpo ANTES de trocar o bindSkin para CAPE
                prm.flushBatch(model, null);

                // Capa customizada do CPM (RootModelType.CAPE), se o modelo possuir raiz de capa
                if (def.hasRoot(RootModelType.CAPE)
                        && ap.isModelPartShown(PlayerModelPart.CAPE)
                        && ap.getItemBySlot(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
                    ResourceLocation baseCape = ap.getSkin().capeTexture();
                    if (baseCape == null) baseCape = CustomPlayerModelsClient.DEFAULT_CAPE;
                    ModelTexture capeTex = new ModelTexture(baseCape, RenderType::entityTranslucent);
                    prm.rebindModel(model);
                    prm.bindSkin(model, capeTex, TextureSheetType.CAPE);
                    if (capeTex.getTexture() != null) {
                        RenderType capeRt = capeTex.getRenderType();
                        if (capeRt == null) capeRt = RenderType.entityTranslucent(capeTex.getTexture());
                        VertexConsumer capeVc = buf.getBuffer(capeRt);
                        CustomPlayerModelsClient.renderCape(ps, capeVc, light, ap, pt, model, def);
                    }
                }
                return true;
            } finally {
                if (bound) {
                    mgr.unbindFlush(model);
                }
            }
        }
    }
}
