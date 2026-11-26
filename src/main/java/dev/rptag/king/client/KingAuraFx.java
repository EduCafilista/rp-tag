package dev.rptag.king.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.king.King;
import dev.rptag.king.KingAbilities;
import dev.rptag.king.KingEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Aura passiva do Rei no mundo: anéis de 15/8/4 blocos no chão, letras orbitando, Sinal Amarelo; e a Marca de Carcosa nos alvos. Sem partículas. */
@EventBusSubscriber(modid = King.MODID, value = Dist.CLIENT)
public final class KingAuraFx {

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (dev.rptag.client.FxQualidade.desligado()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 cam = e.getCamera().getPosition();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        float t = mc.player.tickCount + pt;
        Matrix4f m = e.getPoseStack().last().pose();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bs.getBuffer(RenderType.lightning());
        boolean any = false;
        Entity camEnt = mc.getCameraEntity();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        boolean min = dev.rptag.client.FxQualidade.minimoOuMenos();
        int glifosCorpo = dev.rptag.client.FxQualidade.escala(7, 2);

        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.isSpectator() || !KingAbilities.isKing(p)) continue;
            if (dev.rptag.client.FxQualidade.longe(cam, p.getX(), p.getY(), p.getZ(), 64.0)) continue;
            any = true;
            Vec3 c = new Vec3(Mth.lerp(pt, p.xo, p.getX()), Mth.lerp(pt, p.yo, p.getY()), Mth.lerp(pt, p.zo, p.getZ()));
            // anéis de alcance (15 / 8 / 4): bem sutis, o do meio e o de perto mais fortes
            if (!min) {
                KingGfx.ringGround(vc, m, cam, mc.level, c, 15, 0.09f, 96, t * 0.004f, 24, 0.35f, KingGfx.AMBER, 0.20f);
                KingGfx.ringGround(vc, m, cam, mc.level, c, 8, 0.12f, 64, -t * 0.008f, 16, 0.5f, KingGfx.GOLD, 0.28f);
            }
            KingGfx.ringGround(vc, m, cam, mc.level, c, 4, 0.16f, 48, t * 0.015f, 0, 1f, KingGfx.GOLD, 0.40f);
            if (!dev.rptag.client.FxQualidade.baixoOuMenos()) {
                KingGfx.ringGround(vc, m, cam, mc.level, c, 3.5, 0.08f, 40, -t * 0.02f, 10, 0.55f, KingGfx.AMBER, 0.45f);
                for (int i = 0; i < 4; i++) {
                    float a = t * 0.015f + i * 1.5708f;
                    Vec3 g = new Vec3(c.x + Math.cos(a) * 3.75, c.y + 0.06, c.z + Math.sin(a) * 3.75);
                    KingGfx.glyph(vc, m, cam, i * 2, g, KingGfx.flat(a + 1.5708f), 0.7f, 0f, 0.06f, KingGfx.GOLD, 0.6f);
                }
            }
            if (p == camEnt && firstPerson) continue;                                   // não tampar a própria visão
            for (int i = 0; i < glifosCorpo; i++) {                                     // letras amarelas orbitando o corpo
                float a = t * (0.025f + i * 0.004f) * (i % 2 == 0 ? 1 : -1) + i * 0.9f;
                double r = 1.1 + 0.12 * i;
                Vec3 o = c.add(Math.cos(a) * r, 0.4 + (i * 0.27 + t * 0.006 * (i + 1)) % 2.2, Math.sin(a) * r);
                float fl = 0.55f + 0.35f * Mth.sin(t * 0.2f + i * 2f);
                KingGfx.glyph(vc, m, cam, i, o, KingGfx.facing(cam, o), 0.32f, 0f, 0.03f, KingGfx.GOLD, fl);
            }
            Vec3 so = c.add(0, p.getBbHeight() + 1.15, 0);
            KingGfx.sign(vc, m, cam, so, KingGfx.facing(cam, so), 0.42f, t * 0.01f, 0.035f, 0.75f);
        }

        // Marca de Carcosa sobre os alvos
        double maxMarca = dev.rptag.client.FxQualidade.distanciaPoder(48.0);
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof LivingEntity le) || le.isSpectator() || !le.hasEffect(KingEffects.MARK)) continue;
            if (en == camEnt && firstPerson) continue;
            if (en.distanceToSqr(cam.x, cam.y, cam.z) > maxMarca * maxMarca) continue;
            any = true;
            Vec3 o = new Vec3(Mth.lerp(pt, en.xo, en.getX()), Mth.lerp(pt, en.yo, en.getY()) + en.getBbHeight() + 0.75, Mth.lerp(pt, en.zo, en.getZ()));
            Vec3[] b = KingGfx.facing(cam, o);
            float pulse = 0.8f + 0.2f * Mth.sin(t * 0.2f + en.getId());
            KingGfx.sign(vc, m, cam, o, b, 0.28f, t * 0.03f + en.getId(), 0.03f, 0.9f * pulse);
        }
        if (any) bs.endBatch(RenderType.lightning());
    }

    private KingAuraFx() {}
}
