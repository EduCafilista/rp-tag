package dev.rptag.aura.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.rptag.aura.AuraAnexos;
import dev.rptag.aura.AuraEstilo;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * (5.60.0) A AURA DE ILUMINACAO — desde a 5.64.0 o desenho e da {@link AuraShellLayer}.
 *
 * <p>Historico: na 5.63.0 (pedido 118) saiu o circulo magico, a coluna, as helices, os aneis, os
 * raios e os 5 portoes — ficou "so a aura em volta do player brilhante", feita com 3 cascas de
 * cilindro. Na 5.64.0 o zip novo trouxe um jeito MELHOR: a {@code AuraShellLayer} redesenha o
 * proprio modelo do jogador dilatado em 10 direcoes (8 no plano + cima/baixo) e mostra so as faces
 * de TRAS — o brilho fica colado na silhueta, de espessura uniforme, sem cilindro flutuando.
 *
 * <p>Esta classe ficou com o que e do MUNDO: a tinta do VAZIO ({@link AuraFx}) e as particulas
 * douradas da iluminacao.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class AuraRendererIluminacao {
    private static final float TAU = (float) (Math.PI * 2.0);

    /** (5.64.0) o VAZIO ganha a animacao de tinta (tentaculos + mancha no chao). */
    @SubscribeEvent
    public static void onRender(RenderPlayerEvent.Post e) {
        Player p = e.getEntity();
        if (p.isSpectator() || p.isInvisible()) return;
        if (AuraFx.ehVazio(p)) {
            AuraFx.renderVazio(e, p, p.tickCount + e.getPartialTick());
        }
    }

    /** Particulas douradas subindo (a iluminacao). O vazio tem as dele na {@link AuraFx}. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        Player p = e.getEntity();
        if (!p.level().isClientSide() || p.isSpectator()) return;
        if (!AuraAnexos.ligada(p)) return;

        AuraEstilo s = AuraEstilo.ILUMINACAO;
        RandomSource rnd = p.getRandom();
        for (int i = 0; i < 4; i++) {
            double ang = rnd.nextDouble() * TAU;
            double rad = 0.35 + rnd.nextDouble() * 0.75;
            double x = p.getX() + Math.cos(ang) * rad;
            double z = p.getZ() + Math.sin(ang) * rad;
            p.level().addParticle(new DustParticleOptions(new Vector3f(s.r, s.g, s.b), 1.2f),
                    x, p.getY() + 0.05, z, 0, 0.04 + rnd.nextDouble() * 0.05, 0);
        }
        if (rnd.nextFloat() < 0.7f) {
            double ang = rnd.nextDouble() * TAU;
            double rad = 0.25 + rnd.nextDouble() * 0.6;
            p.level().addParticle(ParticleTypes.END_ROD,
                    p.getX() + Math.cos(ang) * rad, p.getY() + rnd.nextDouble() * 1.3,
                    p.getZ() + Math.sin(ang) * rad, 0, 0.03 + rnd.nextDouble() * 0.03, 0);
        }
    }

    private AuraRendererIluminacao() {}
}
