package dev.rptag.aura;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Personagem Gravidade (/aura gravidade). Roda de poderes (tecla G), aura liga/desliga (tecla H).
 *  0 Pés na parede | 1 Gravidade pesada | 2 Gravidade leve | 3 Queda meteoro
 *  4 Campo esmagar | 5 Campo flutuar | 6 Campo atrair | 7 Campo repelir   (o campo acompanha o jogador)
 * Passivo: qualquer queda >= 4 blocos vira um impacto no chão, mais forte quanto maior a altura.
 * Aura ligada = escudo de gravidade: +7 de armadura. Desligar a aura NÃO desliga os poderes.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID)
public final class GravityPowers {
    public static final double FIELD_RADIUS = 12.0;
    public static final int WALL_TICKS = 200;       // 10 s
    public static final int SELF_TICKS = 400;       // 20 s
    public static final int FIELD_TICKS = 600;      // 30 s
    public static final int METEOR_COOLDOWN = 160;  // 8 s
    private static final double SHIELD_ARMOR = 7.0;
    /** false = o impacto de queda não machuca jogadores (seguro para RP). */
    private static final boolean HURTS_PLAYERS = false;

    private static final ResourceLocation ID_SHIELD = id("grav_shield"), ID_HEAVY = id("grav_heavy"),
            ID_LIGHT = id("grav_light"), ID_CRUSH = id("grav_crush");
    private static final List<Holder<Attribute>> SELF_ATTRS = List.of(Attributes.GRAVITY, Attributes.KNOCKBACK_RESISTANCE,
            Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.JUMP_STRENGTH, Attributes.SAFE_FALL_DISTANCE);

    private static ResourceLocation id(String s) { return ResourceLocation.fromNamespaceAndPath(dev.rptag.RPTagMod.MODID, s); }

    private static final class PS {
        int wall, self, selfT, appliedSelf, field, fieldT, meteor, meteorT;
        long meteorCd;
        final Set<UUID> crushed = new HashSet<>();
    }

    private static final Map<UUID, PS> STATES = new HashMap<>();

    // ------------------------------------------------------------------ comandos da roda
    public static void handle(ServerPlayer p, int action, int arg) {
        if (!GravState.isGravity(p)) { bar(p, "Ative o personagem: /aura gravidade"); return; }
        int bits = GravState.get(p);
        if (action == 0) {
            p.setData(AuraAnexos.GRAV.get(), bits ^ GravState.AURA);
            updateShield(p);
            bar(p, GravState.aura(bits) ? "Aura desligada (os poderes continuam)" : "Aura de gravidade ligada: escudo +7");
            return;
        }
        PS st = STATES.computeIfAbsent(p.getUUID(), k -> new PS());
        switch (arg) {
            case 0 -> {
                if (st.wall > 0) { st.wall = 0; bar(p, "Pés na parede: desligado"); }
                else { st.wall = WALL_TICKS; bar(p, "Pés na parede (10s): ande em paredes e tetos. Agache ou pule para soltar"); }
            }
            case 1, 2 -> {
                if (st.self == arg) { st.self = 0; bar(p, "Gravidade normal"); }
                else { st.self = arg; st.selfT = SELF_TICKS; bar(p, arg == 1 ? "Gravidade pesada (20s)" : "Gravidade leve (20s): pule alto e plane"); }
            }
            case 3 -> {
                long now = p.serverLevel().getGameTime();
                if (st.meteor != 0) return;
                if (now < st.meteorCd) { bar(p, "Meteoro recarregando... " + (st.meteorCd - now) / 20 + "s"); return; }
                st.meteor = 1; st.meteorT = 0; st.meteorCd = now + METEOR_COOLDOWN;
                Vec3 mv = p.getDeltaMovement();
                p.setDeltaMovement(mv.x, 1.4, mv.z);
                p.hurtMarked = true;
                bar(p, "Queda meteoro!");
            }
            case 4, 5, 6, 7 -> {
                int mode = arg - 3;
                if (st.field == mode) { st.field = 0; bar(p, "Campo gravitacional: desligado"); }
                else {
                    st.field = mode; st.fieldT = FIELD_TICKS;
                    bar(p, "Campo: " + new String[]{"Esmagar", "Flutuar", "Atrair", "Repelir"}[mode - 1] + " (30s, " + (int) FIELD_RADIUS + " blocos, acompanha você)");
                }
            }
            default -> { }
        }
        sync(p, st);
    }

    // -------------------------------------------------------------------------- tick
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (!GravState.isGravity(p)) {
            if (p.tickCount % 40 == 0) cleanup(p);
            return;
        }
        PS st = STATES.computeIfAbsent(p.getUUID(), k -> new PS());
        if (p.tickCount % 20 == 0) updateShield(p);

        if (st.wall > 0 && --st.wall == 0) bar(p, "Pés na parede acabou");
        if (st.self != 0 && --st.selfT <= 0) { st.self = 0; bar(p, "Gravidade normal"); }
        if (st.field != 0 && --st.fieldT <= 0) { st.field = 0; bar(p, "Campo gravitacional acabou"); }
        if (st.self != st.appliedSelf) applySelf(p, st);

        meteor(p, st);
        field(p, st);
        sync(p, st);
    }

    private static void sync(ServerPlayer p, PS st) {
        int bits = GravState.make(GravState.aura(GravState.get(p)), st.field, st.self, st.wall > 0);
        if (bits != GravState.get(p)) p.setData(AuraAnexos.GRAV.get(), bits);
    }

    private static void updateShield(ServerPlayer p) {
        mod(p, Attributes.ARMOR, ID_SHIELD, GravState.aura(GravState.get(p)) ? SHIELD_ARMOR : 0.0,
                AttributeModifier.Operation.ADD_VALUE);
    }

    private static void applySelf(ServerPlayer p, PS st) {
        for (Holder<Attribute> a : SELF_ATTRS) { mod(p, a, ID_HEAVY, 0, AttributeModifier.Operation.ADD_VALUE); mod(p, a, ID_LIGHT, 0, AttributeModifier.Operation.ADD_VALUE); }
        var add = AttributeModifier.Operation.ADD_VALUE;
        if (st.self == 1) {          // pesada e resistente
            mod(p, Attributes.GRAVITY, ID_HEAVY, 0.08, add);
            mod(p, Attributes.KNOCKBACK_RESISTANCE, ID_HEAVY, 1.0, add);
            mod(p, Attributes.ARMOR, ID_HEAVY, 4.0, add);
            mod(p, Attributes.ARMOR_TOUGHNESS, ID_HEAVY, 4.0, add);
            mod(p, Attributes.JUMP_STRENGTH, ID_HEAVY, -0.12, add);
        } else if (st.self == 2) {   // leve: pulo alto e planada longa, sem dano de queda
            mod(p, Attributes.GRAVITY, ID_LIGHT, -0.05, add);
            mod(p, Attributes.JUMP_STRENGTH, ID_LIGHT, 0.25, add);
            mod(p, Attributes.SAFE_FALL_DISTANCE, ID_LIGHT, 40.0, add);
        }
        st.appliedSelf = st.self;
    }

    private static void cleanup(ServerPlayer p) {
        PS st = STATES.remove(p.getUUID());
        if (st != null) releaseCrush(p.serverLevel(), st);
        mod(p, Attributes.ARMOR, ID_SHIELD, 0, AttributeModifier.Operation.ADD_VALUE);
        for (Holder<Attribute> a : SELF_ATTRS) { mod(p, a, ID_HEAVY, 0, AttributeModifier.Operation.ADD_VALUE); mod(p, a, ID_LIGHT, 0, AttributeModifier.Operation.ADD_VALUE); }
        int bits = GravState.get(p);
        if ((bits & ~GravState.AURA) != 0) p.setData(AuraAnexos.GRAV.get(), bits & GravState.AURA);
    }

    // ------------------------------------------------------------------- meteoro
    private static void meteor(ServerPlayer p, PS st) {
        if (st.meteor == 0) return;
        if (st.meteor == 1) {                         // sobe
            p.fallDistance = 0;
            if (++st.meteorT >= 12) { st.meteor = 2; st.meteorT = 0; }
            return;
        }
        Vec3 mv = p.getDeltaMovement();               // despenca
        p.setDeltaMovement(mv.x * 0.3, -3.0, mv.z * 0.3);
        p.hurtMarked = true;
        p.serverLevel().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 4, 0.3, 0.5, 0.3, 0.02);
        st.meteorT++;
        if ((p.onGround() && st.meteorT > 2) || st.meteorT > 100) st.meteor = 0;
    }

    // --------------------------------------------------------------------- campo
    private static boolean immune(Entity en) {
        return en instanceof Player pl && (pl.isCreative() || GravState.isGravity(pl));
    }

    private static void field(ServerPlayer p, PS st) {
        ServerLevel level = p.serverLevel();
        if (st.field == 0) { if (!st.crushed.isEmpty()) releaseCrush(level, st); return; }
        Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
        Set<UUID> now = new HashSet<>();
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(FIELD_RADIUS),
                en -> en != p && en.isAlive() && !en.isSpectator() && !immune(en))) {
            if (t.position().distanceTo(p.position()) > FIELD_RADIUS) continue;
            switch (st.field) {
                case 1 -> { crush(t); now.add(t.getUUID()); }
                case 2 -> {
                    if (t.getY() - p.getY() > 12) t.removeEffect(MobEffects.LEVITATION);
                    else t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 25, 2, false, false));
                }
                case 3 -> {
                    Vec3 d = c.subtract(t.position().add(0, t.getBbHeight() * 0.5, 0));
                    double l = d.length();
                    if (l < 1.5) continue;
                    Vec3 v = t.getDeltaMovement().add(d.scale(0.13 / l));
                    t.setDeltaMovement(v.length() > 1.2 ? v.scale(1.2 / v.length()) : v);
                    t.hurtMarked = true;
                }
                default -> {
                    Vec3 d = new Vec3(t.getX() - p.getX(), 0, t.getZ() - p.getZ());
                    double l = Math.max(0.5, d.length());
                    t.setDeltaMovement(t.getDeltaMovement().add(d.x / l * 0.16, 0.03, d.z / l * 0.16));
                    t.hurtMarked = true;
                }
            }
        }
        if (st.field == 1) {
            Set<UUID> gone = new HashSet<>(st.crushed);
            gone.removeAll(now);
            for (UUID u : gone) if (level.getEntity(u) instanceof LivingEntity le) uncrush(le);
            st.crushed.clear();
            st.crushed.addAll(now);
        } else if (!st.crushed.isEmpty()) {
            releaseCrush(level, st);
        }
    }

    private static void crush(LivingEntity t) {
        AttributeInstance g = t.getAttribute(Attributes.GRAVITY);
        if (g != null && g.hasModifier(ID_CRUSH)) return;
        mod(t, Attributes.GRAVITY, ID_CRUSH, 0.10, AttributeModifier.Operation.ADD_VALUE);
        mod(t, Attributes.JUMP_STRENGTH, ID_CRUSH, -0.42, AttributeModifier.Operation.ADD_VALUE);
        mod(t, Attributes.MOVEMENT_SPEED, ID_CRUSH, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void uncrush(LivingEntity t) {
        mod(t, Attributes.GRAVITY, ID_CRUSH, 0, AttributeModifier.Operation.ADD_VALUE);
        mod(t, Attributes.JUMP_STRENGTH, ID_CRUSH, 0, AttributeModifier.Operation.ADD_VALUE);
        mod(t, Attributes.MOVEMENT_SPEED, ID_CRUSH, 0, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void releaseCrush(ServerLevel level, PS st) {
        for (UUID u : st.crushed) if (level.getEntity(u) instanceof LivingEntity le) uncrush(le);
        st.crushed.clear();
    }

    // ----------------------------------------------------------- queda = impacto
    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !GravState.isGravity(p)) return;
        float d = e.getDistance();
        PS st = STATES.get(p.getUUID());
        if (st != null && st.meteor != 0) { if (st.meteor == 2) d += 12f; st.meteor = 0; }
        e.setDamageMultiplier(0f);                    // o personagem não sofre dano de queda
        if (d >= 4f) impact(p, d);
    }

    /** Quanto maior a altura da queda, maior o raio e o dano. */
    private static void impact(ServerPlayer p, float d) {
        ServerLevel level = p.serverLevel();
        double radius = Math.min(3.0 + d * 0.45, 25.0);
        float maxDmg = Math.min(d * 1.2f, 40f);
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(radius),
                en -> en != p && en.isAlive() && !en.isSpectator() && !immune(en))) {
            double dx = t.getX() - p.getX(), dz = t.getZ() - p.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > radius) continue;
            double k = 1.0 - dist / radius;
            if (HURTS_PLAYERS || !(t instanceof Player)) {
                t.invulnerableTime = 0;
                t.hurt(level.damageSources().explosion(p, p), (float) (maxDmg * (0.3 + 0.7 * k)));
            }
            double len = Math.max(0.5, dist);
            t.push(dx / len * (0.3 + k), 0.3 + 0.5 * k, dz / len * (0.3 + k));
            t.hurtMarked = true;
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS,
                Math.min(4f, 1f + d * 0.1f), 0.6f);
        PacketDistributor.sendToPlayersNear(level, null, p.getX(), p.getY(), p.getZ(), 96.0,
                new GravityImpactFxPayload(p.getX(), p.getY(), p.getZ(), (float) radius));
    }

    // ------------------------------------------------------------------- escudo FX
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !GravState.isGravity(p) || !GravState.aura(GravState.get(p))) return;
        ServerLevel lv = p.serverLevel();
        lv.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 6, 0.35, 0.5, 0.35, 0.05);
        lv.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    // ------------------------------------------------------------------- sessão
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) p.setData(AuraAnexos.GRAV.get(), GravState.get(p) & GravState.AURA);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        PS st = STATES.remove(e.getEntity().getUUID());
        if (st != null && e.getEntity() instanceof ServerPlayer p) releaseCrush(p.serverLevel(), st);
    }

    // ------------------------------------------------------------------ helpers
    private static void mod(LivingEntity e, Holder<Attribute> a, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst == null) return;
        inst.removeModifier(id);
        if (amount != 0) inst.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    /** (5.73.0) MUDO: o dono mandou tirar os avisos dos poderes da tela. */
    private static void bar(ServerPlayer p, String msg) { }

    private GravityPowers() {}
}
