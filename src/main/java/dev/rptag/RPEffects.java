package dev.rptag;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * (3.45.0) Efeitos de status do mod.
 *
 * <p><b>MEDO</b> ({@code rptag:medo}): o efeito do terror RP — sem efeito
 * fisico no jogador (a tremedeira da camera vem do pacote de rede), o
 * status existe para o ICONE aparecer no canto do HUD (igual Veneno/
 * Wither) e pra poder dar via {@code /effect give <jogador> rptag:medo}.
 * O icone fica em {@code assets/rptag/textures/mob_effect/medo.png}
 * (3.53.0: carinha amarela assustada — e o cliente traduz o efeito em
 * tremedeira, entao {@code /effect give} dispara o medo completo).
 */
public final class RPEffects {

    private RPEffects() {
    }

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, RPTagMod.MODID);

    /** O MEDO: roxo-escuro (cor da "aura" nos pixels de particula). */
    public static final DeferredHolder<MobEffect, MobEffect> MEDO =
            EFFECTS.register("medo", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x6A0DAD) {
            }); // (1.21.1) o construtor e PROTECTED — subclasse anonima, igual o vanilla

    /**
     * (5.25.0) A CIBERPSICOSE: o efeito do colapso ciberpsicótico — igual ao MEDO, é um efeito
     * de verdade (ícone no HUD, dá pra usar {@code /effect give <jogador> rptag:ciberpsicose 30})
     * e o CLIENTE transforma ele na tela toda quebrada (glitch, rasgos, biomonitor, "SINAIS
     * VITAIS CRÍTICOS") do {@code CiberpsicoseAvisoFx} — do começo ao fim do efeito.
     *
     * <p>Cor ciano-choque: a mesma do glitch do FX.
     */
    public static final DeferredHolder<MobEffect, MobEffect> CIBERPSICOSE =
            EFFECTS.register("ciberpsicose", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x00F0FF) {
            });

    /**
     * (5.25.0) PROTEÇÃO DO ECLIPSE (id antigo "sombra"): o sol não vê quem está na sombra. É o que o Amuleto do Eclipse
     * entrega no clique direito — e é o que o {@code VampiroSistema} consulta pra não deixar o
     * sol acender o vampiro. BENÉFICO, então entra como ícone azul no HUD.
     */
    public static final DeferredHolder<MobEffect, MobEffect> SOMBRA =
            EFFECTS.register("sombra", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x1A237E) {
            });
}
