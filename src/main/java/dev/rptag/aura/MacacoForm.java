package dev.rptag.aura;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Forma Macaco: 5x maior + Regeneração II, Força III, Resistência III (infinitos). */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class MacacoForm {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, "macaco");

    /** Tamanho final (o padrão do Minecraft é 1.0). */
    public static final double SCALE = 5.0;

    public static void apply(ServerPlayer p) {
        p.setData(AuraAnexos.MACACO.get(), true);
        mod(p, Attributes.SCALE, SCALE - 1.0);
        mod(p, Attributes.STEP_HEIGHT, 2.5);                 // sobe blocos andando
        mod(p, Attributes.BLOCK_INTERACTION_RANGE, 8.0);     // alcance proporcional ao tamanho
        mod(p, Attributes.ENTITY_INTERACTION_RANGE, 6.0);
        effects(p);
    }

    public static void remove(ServerPlayer p) {
        p.setData(AuraAnexos.MACACO.get(), false);
        for (Holder<Attribute> a : java.util.List.of(Attributes.SCALE, Attributes.STEP_HEIGHT,
                Attributes.BLOCK_INTERACTION_RANGE, Attributes.ENTITY_INTERACTION_RANGE)) {
            AttributeInstance inst = p.getAttribute(a);
            if (inst != null) inst.removeModifier(ID);
        }
        p.removeEffect(MobEffects.REGENERATION);
        p.removeEffect(MobEffects.DAMAGE_BOOST);
        p.removeEffect(MobEffects.DAMAGE_RESISTANCE);
    }

    private static void mod(ServerPlayer p, Holder<Attribute> attr, double amount) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(ID);
        inst.addTransientModifier(new AttributeModifier(ID, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void effects(ServerPlayer p) {
        give(p, MobEffects.REGENERATION, 1);        // Regeneração II
        give(p, MobEffects.DAMAGE_BOOST, 2);        // Força III
        give(p, MobEffects.DAMAGE_RESISTANCE, 2);   // Resistência III ("defesa 3")
    }

    private static void give(ServerPlayer p, Holder<MobEffect> eff, int amp) {
        MobEffectInstance cur = p.getEffect(eff);
        if (cur == null || cur.getAmplifier() != amp || !cur.isInfiniteDuration()) {
            p.addEffect(new MobEffectInstance(eff, MobEffectInstance.INFINITE_DURATION, amp, false, false, true));
        }
    }

    /** Reaplica sozinho ao relogar (modificadores são temporários) e se o leite remover os efeitos. */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 40 != 0) return;
        if (!p.getData(AuraAnexos.MACACO.get())) return;
        AttributeInstance scale = p.getAttribute(Attributes.SCALE);
        if (scale == null || !scale.hasModifier(ID)) { apply(p); return; }
        effects(p);
    }

    private MacacoForm() {}
}
