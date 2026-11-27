package dev.rptag.vampire;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * (5.78.18) Regras do vampiro (servidor): mordida, garrafa de sangue, forma de morcego, visão
 * vampírica e restrição de comida comum.
 *
 * <p>Unificado com {@link dev.rptag.VampiroSistema} e {@link dev.rptag.RPAttributes#VAMPIRO}:
 * qualquer jogador que tenha o anexo {@code rptag:vampire} OU o atributo {@code rptag:vampiro > 0.5}
 * é reconhecido como vampiro nos dois sistemas. A queimadura solar continua centralizada em
 * {@link dev.rptag.VampiroSistema} (preservando Amuleto do Eclipse, Sombra, capacete protetor e
 * aviso especial de morte pelo sol).
 */
@EventBusSubscriber(modid = Vampire.MODID)
public final class VampireLogic {
    public static final int BITE_COOLDOWN = 40;           // 2 s
    public static final double BITE_REACH = 3.2;
    public static final float BITE_DAMAGE = 2.0f;         // 1 coração
    public static final int BLOOD_MOB = 5, BLOOD_PLAYER = 7;     // pontos da barra (2 = 1 gota)
    private static final net.minecraft.resources.ResourceLocation BAT_SCALE = Vampire.rl("vampire_bat_scale");
    private static final Map<UUID, Long> BITE_CD = new ConcurrentHashMap<>();
    /** Jogadores que desligaram manualmente a visão vampírica pela roda (respeitado pelo tick noturno). */
    private static final Set<UUID> VISAO_DESLIGADA_MANUAL = ConcurrentHashMap.newKeySet();

    public static boolean isVampire(Player p) {
        if (p == null) return false;
        try {
            if (Boolean.TRUE.equals(p.getData(VampireAttachments.VAMPIRE.get()))) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            var inst = p.getAttribute(dev.rptag.RPAttributes.VAMPIRO);
            if (inst != null && inst.getValue() > 0.5D) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean isBat(Player p) {
        if (p == null) return false;
        try {
            return Boolean.TRUE.equals(p.getData(VampireAttachments.BAT.get()));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isVisionMuted(UUID id) {
        return id != null && VISAO_DESLIGADA_MANUAL.contains(id);
    }

    public static void clearVisionMute(UUID id) {
        if (id != null) VISAO_DESLIGADA_MANUAL.remove(id);
    }

    // ------------------------------------------------------------------ roda
    public static void action(ServerPlayer sp, int a) {
        if (!isVampire(sp) || !sp.isAlive() || sp.isSpectator()) return;
        switch (a) {
            case 0 -> bite(sp);
            case 1 -> toggleBat(sp, true);
            case 2 -> vision(sp);
            default -> { }
        }
    }

    private static boolean hasItem(ServerPlayer sp, net.minecraft.world.item.Item it) {
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++)
            if (sp.getInventory().getItem(i).is(it)) return true;
        return false;
    }

    private static void msg(ServerPlayer sp, String key) { sp.displayClientMessage(Component.translatable(key), true); }

    // ------------------------------------------------------------------ mordida
    private static boolean behind(LivingEntity target, ServerPlayer biter) {
        Vec3 look = new Vec3(target.getLookAngle().x, 0, target.getLookAngle().z);
        Vec3 to = new Vec3(biter.getX() - target.getX(), 0, biter.getZ() - target.getZ());
        if (look.lengthSqr() < 1e-4 || to.lengthSqr() < 1e-4) return true;
        return look.normalize().dot(to.normalize()) < -0.2;           // o vampiro está nas costas
    }

    private static boolean eligible(ServerPlayer sp, LivingEntity t) {
        if (t == sp || !t.isAlive() || t instanceof ArmorStand || t.isSpectator()) return false;
        if (t.getType().is(EntityTypeTags.UNDEAD) || t.getType().is(Tags.EntityTypes.BOSSES)) return false;   // sem sangue / grandes demais
        if (t instanceof Player p) return !p.isCreative() && behind(p, sp);     // jogadores: só pelas costas
        return true;                                                            // qualquer mob normal: de frente mesmo
    }

    private static LivingEntity findTarget(ServerPlayer sp) {
        Vec3 eye = sp.getEyePosition(), look = sp.getLookAngle(), end = eye.add(look.scale(BITE_REACH));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(sp, eye, end, sp.getBoundingBox().expandTowards(look.scale(BITE_REACH)).inflate(0.6),
                e -> e instanceof LivingEntity le && eligible(sp, le), BITE_REACH * BITE_REACH);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) return le;
        return sp.level().getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(BITE_REACH),
                        e -> eligible(sp, e) && e.getBoundingBox().getCenter().subtract(eye).normalize().dot(look) > 0.82
                                && e.distanceTo(sp) <= BITE_REACH)
                .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp))).orElse(null);
    }

    public static void bite(ServerPlayer sp) {
        if (isBat(sp)) { msg(sp, "msg.vampire.bat_no_bite"); return; }
        ServerLevel lv = sp.serverLevel();
        long now = lv.getGameTime();
        long left = BITE_CD.getOrDefault(sp.getUUID(), 0L) - now;
        if (left > 0) return;
        LivingEntity t = findTarget(sp);
        if (t == null) { msg(sp, "msg.vampire.no_target"); return; }
        BITE_CD.put(sp.getUUID(), now + BITE_COOLDOWN);

        boolean human = t instanceof Player;
        t.invulnerableTime = 0;
        t.hurt(sp.damageSources().playerAttack(sp), BITE_DAMAGE);
        if (human) {
            t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            t.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 60, 0));
        }
        sp.getFoodData().eat(human ? BLOOD_PLAYER : BLOOD_MOB, 0.2f);          // bebe um pouco na hora

        int slot = -1;                                                         // e enche uma garrafa vazia, se tiver
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++)
            if (sp.getInventory().getItem(i).is(VampireItems.EMPTY_FLASK.get())) { slot = i; break; }
        if (slot >= 0) {
            sp.getInventory().getItem(slot).shrink(1);
            ItemStack full = new ItemStack(VampireItems.BLOOD_FLASK.get());
            if (!sp.getInventory().add(full)) sp.drop(full, false);
            msg(sp, "msg.vampire.flask_filled");
        }
        lv.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.6f, 0.6f);
        lv.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.7f, 0.5f);
        PacketDistributor.sendToPlayersNear(lv, null, t.getX(), t.getY(), t.getZ(), 48.0, new VampireFxPayload(t.getId(), sp.getId()));
    }

    // ------------------------------------------------------------------ visão vampírica
    private static void vision(ServerPlayer sp) {
        if (sp.hasEffect(MobEffects.NIGHT_VISION) || dev.rptag.VampiroSistema.visaoAtivaNoServidor(sp)) {
            VISAO_DESLIGADA_MANUAL.add(sp.getUUID());
            sp.removeEffect(MobEffects.NIGHT_VISION);
            PacketDistributor.sendToPlayer(sp, new dev.rptag.VampiroNoitePayload(false));
            msg(sp, "msg.vampire.sight_off");
        } else {
            VISAO_DESLIGADA_MANUAL.remove(sp.getUUID());
            sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, true, false, true));
            PacketDistributor.sendToPlayer(sp, new dev.rptag.VampiroNoitePayload(true));
            msg(sp, "msg.vampire.sight_on");
        }
    }

    // ------------------------------------------------------------------ morcego
    /** @param needAmulet true quando vem da roda: precisa ter o Amuleto do Morcego no inventário. */
    public static void toggleBat(ServerPlayer sp, boolean needAmulet) {
        if (!isVampire(sp)) { msg(sp, "msg.vampire.not_vampire"); return; }
        if (isBat(sp)) { exitBat(sp); return; }
        if (needAmulet && !hasItem(sp, VampireItems.BAT_AMULET.get())) {
            msg(sp, "msg.vampire.need_amulet");
            return;
        }
        if (sp.getFoodData().getFoodLevel() <= 2) { msg(sp, "msg.vampire.low_blood"); return; }
        sp.setData(VampireAttachments.BAT.get(), true);
        var scale = sp.getAttribute(Attributes.SCALE);
        if (scale != null && !scale.hasModifier(BAT_SCALE))
            scale.addTransientModifier(new AttributeModifier(BAT_SCALE, -0.7, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        sp.getAbilities().mayfly = true;
        sp.getAbilities().flying = true;
        sp.getAbilities().setFlyingSpeed(0.045f);
        sp.onUpdateAbilities();
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BAT_TAKEOFF, SoundSource.PLAYERS, 1f, 1.1f);
    }

    public static void exitBat(ServerPlayer sp) {
        if (!isBat(sp)) return;
        sp.setData(VampireAttachments.BAT.get(), false);
        var scale = sp.getAttribute(Attributes.SCALE);
        if (scale != null) scale.removeModifier(BAT_SCALE);
        boolean keep = sp.isCreative() || sp.isSpectator();
        if (!keep) { sp.getAbilities().mayfly = false; sp.getAbilities().flying = false; }
        sp.getAbilities().setFlyingSpeed(0.05f);
        sp.onUpdateAbilities();
        sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BAT_LOOP, SoundSource.PLAYERS, 0.8f, 0.8f);
    }

    // ------------------------------------------------------------------ tick: morcego + sincronia atributo <-> anexo
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        // Sincroniza a cada 20 ticks caso alguém mude /attribute <p> rptag:vampiro base set 1|0 na mão
        if (sp.tickCount % 20 == 0) {
            boolean attr = dev.rptag.VampiroSistema.nivel(sp) > 0.5D;
            boolean att = Boolean.TRUE.equals(sp.getData(VampireAttachments.VAMPIRE.get()));
            if (attr != att) {
                sp.setData(VampireAttachments.VAMPIRE.get(), attr);
            }
        }
        if (!isVampire(sp)) {
            if (isBat(sp)) exitBat(sp);       // deixou de ser vampiro
            return;
        }
        if (isBat(sp)) {
            sp.fallDistance = 0;
            sp.causeFoodExhaustion(0.012f);                                            // voar gasta sangue
            if (sp.getFoodData().getFoodLevel() <= 0) { exitBat(sp); msg(sp, "msg.vampire.low_blood"); }
        }
    }

    // ------------------------------------------------------------------ comida
    /** Vampiro não come comida de verdade: só sangue. */
    @SubscribeEvent
    public static void noFood(LivingEntityUseItemEvent.Start e) {
        if (!(e.getEntity() instanceof Player p) || !isVampire(p)) return;
        ItemStack s = e.getItem();
        if (s.has(DataComponents.FOOD) && !s.is(VampireItems.BLOOD_FLASK.get())) {
            e.setCanceled(true);
            if (!p.level().isClientSide) p.displayClientMessage(Component.translatable("msg.vampire.no_food"), true);
        }
    }

    // ------------------------------------------------------------------ limpeza
    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) exitBat(sp);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            exitBat(sp);
            BITE_CD.remove(sp.getUUID());
            VISAO_DESLIGADA_MANUAL.remove(sp.getUUID());
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {          // o scale transitório some sozinho; garante o resto
        if (e.getEntity() instanceof ServerPlayer sp) {
            boolean attr = dev.rptag.VampiroSistema.nivel(sp) > 0.5D;
            boolean att = Boolean.TRUE.equals(sp.getData(VampireAttachments.VAMPIRE.get()));
            if (att && !attr) {
                var inst = sp.getAttribute(dev.rptag.RPAttributes.VAMPIRO);
                if (inst != null) inst.setBaseValue(1.0D);
            } else if (attr && !att) {
                sp.setData(VampireAttachments.VAMPIRE.get(), true);
            }
            if (isBat(sp)) exitBat(sp);
        }
    }

    private VampireLogic() {}
}
