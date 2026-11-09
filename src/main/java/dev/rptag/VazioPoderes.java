package dev.rptag;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * (5.29.0) <b>OS PODERES DO VAZIO</b> — o vazio deixou de ser só paisagem.
 *
 * <p>Pedido do dono: <i>"os poderes do vazio pode usar igual o do bicho desse mod, o vazio vai
 * ficar mais legal se ele tiver poderes igual a esse, e a aura em volta copia a do mod"</i>.
 * A referência (Ronova: Ruler of Death) tem: <b>raio carmesim</b> que termina em explosão,
 * <b>orbes</b>, a <b>onda da morte</b> e os <b>olhos/penas</b> que atiram em tudo na frente.
 *
 * <p>Só funciona <b>no vazio</b> e só para quem tem o efeito {@code rptag:poder_vazio}
 * (o ADM liga com {@code /vazio poder <jogador> ligar}). É de propósito: no resto do mundo
 * isso seria arma de destruição em massa no RP.
 *
 * <p>Regra de ouro do mod: erro num poder avisa o jogador, nunca derruba o servidor.
 */
public final class VazioPoderes {

    private VazioPoderes() {
    }

    /** os quatro poderes + o atalho. */
    public enum Poder {
        RAIO("raio", 1200L), ORBE("orbe", 600L), ONDA("onda", 6000L),
        OLHOS("olhos", 4000L), PENAS("penas", 3500L), BANIR("banir", 12000L);

        public final String id;
        public final long recarga;

        Poder(String id, long recarga) {
            this.id = id;
            this.recarga = recarga;
        }

        public static Poder porId(String s) {
            for (Poder p : values()) {
                if (p.id.equalsIgnoreCase(s)) {
                    return p;
                }
            }
            return null;
        }
    }

    private static final Map<String, Long> PRONTO = new ConcurrentHashMap<>();

    /** (5.31.0) a ORDEM DO LEQUE (teclas 1..6) — igual ao {@code PoderLeque} do cliente. */
    public static Poder poderPorIndice(int i) {
        Poder[] ordem = {Poder.RAIO, Poder.ORBE, Poder.ONDA, Poder.OLHOS, Poder.PENAS, Poder.BANIR};
        return i >= 0 && i < ordem.length ? ordem[i] : null;
    }

    /**
     * (5.31.0) quem pode soltar os poderes.
     *
     * <p>O dono: <i>"não quero esse comando de ativar poderes ou desativar"</i>. Então o dom não
     * é mais dado por comando: <b>no vazio e no reino do Juízo</b> qualquer um com a tecla usa; e
     * o <b>ADM</b> (nível de permissão 2, o mesmo do {@code /juizo}) pode usar em qualquer lugar,
     * para ensaiar a cena.
     */
    public static boolean temPoder(ServerPlayer p) {
        if (p == null) {
            return false;
        }
        try {
            if (VazioReino.ehVazio(p.level()) || JuizoReino.eh(p.level())) {
                return true;
            }
            return p.hasPermissions(2);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Solta o poder.
     *
     * @return 1 = funcionou, 0 = não podia (sem efeito/fora do vazio/em recarga)
     */
    public static int usar(ServerPlayer p, Poder poder) {
        if (!temPoder(p)) {
            avisar(p, "\u00a78Os poderes do vazio só respondem a quem tem o dom \u2014 e só lá dentro.");
            return 0;
        }
        long agora = System.currentTimeMillis();
        String chave = p.getUUID() + ":" + poder.id;
        Long pronto = PRONTO.get(chave);
        if (pronto != null && agora < pronto) {
            avisar(p, "\u00a78Ainda recuperando... (" + ((pronto - agora) / 1000L + 1) + "s)");
            return 0;
        }
        PRONTO.put(chave, agora + poder.recarga);
        try {
            switch (poder) {
                case RAIO -> raio(p);
                case ORBE -> orbe(p);
                case ONDA -> onda(p);
                case OLHOS -> olhos(p);
                case PENAS -> penas(p);
                case BANIR -> banir(p);
            }
            return 1;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: poder do vazio " + poder.id + " falhou", t);
            avisar(p, "\u00a7cO poder falhou.");
            return 0;
        }
    }

    // ------------------------------------------------------------------ RAIO
    /** o raio carmesim: um risco de 60 blocos que termina numa explosão. */
    private static void raio(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 olho = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        Vec3 fim = olho.add(dir.scale(60.0D));
        HitResult hit = ProjectileUtil.getEntityHitResult(p, olho, fim,
                p.getBoundingBox().inflate(60.0D), e -> e instanceof LivingEntity && e != p, 0.4F);
        if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity alvo) {
            fim = ehr.getLocation();
            alvo.hurt(lv.damageSources().magic(), 14.0F);
            alvo.setRemainingFireTicks(80);
            Vec3 longe = alvo.position().subtract(p.position()).normalize().scale(1.2D);
            alvo.push(longe.x, 0.35D, longe.z);
        }
        lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, fim.x, fim.y, fim.z, 60, 1.4D, 1.0D, 1.4D, 0.06D);
        lv.sendParticles(ParticleTypes.EXPLOSION, fim.x, fim.y, fim.z, 3, 0.6D, 0.6D, 0.6D, 0.0D);
        lv.playSound(null, p.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.2F, 0.6F);
        lv.playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                SoundSource.PLAYERS, 0.8F, 0.7F);
        mandar(lv, p, 0, olho, fim);
    }

    // ------------------------------------------------------------------ ORBE
    /** os orbes: um projétil que estoura no primeiro ser vivo que encostar. */
    private static void orbe(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 olho = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        Vec3 fim = olho.add(dir.scale(28.0D));
        HitResult hit = ProjectileUtil.getEntityHitResult(p, olho, fim,
                p.getBoundingBox().inflate(28.0D), e -> e instanceof LivingEntity && e != p, 0.5F);
        if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity alvo) {
            fim = ehr.getLocation();
            alvo.hurt(lv.damageSources().magic(), 9.0F);
        }
        lv.sendParticles(ParticleTypes.DRAGON_BREATH, fim.x, fim.y, fim.z, 40, 0.8D, 0.8D, 0.8D, 0.05D);
        lv.playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_HURT,
                SoundSource.PLAYERS, 0.6F, 1.4F);
        mandar(lv, p, 1, olho, fim);
    }

    // ------------------------------------------------------------------ ONDA
    /** a onda: tudo na frente dela congela e apanha. */
    private static void onda(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 olho = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        AABB area = p.getBoundingBox().inflate(18.0D);
        int atingidos = 0;
        for (LivingEntity alvo : lv.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != p && e.isAlive())) {
            Vec3 ate = alvo.position().subtract(p.position());
            if (ate.lengthSqr() > 18.0D * 18.0D) {
                continue;
            }
            if (ate.normalize().dot(dir) < 0.45D) {
                continue;   // só o que está na frente
            }
            alvo.hurt(lv.damageSources().magic(), 7.0F);
            alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6, false, false));
            alvo.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, false));
            alvo.push(ate.x * 0.25D, 0.4D, ate.z * 0.25D);
            atingidos++;
        }
        Vec3 fim = olho.add(dir.scale(16.0D));
        lv.sendParticles(ParticleTypes.SOUL, fim.x, fim.y, fim.z, 90, 7.0D, 1.6D, 7.0D, 0.02D);
        lv.playSound(null, p.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.9F, 0.6F);
        mandar(lv, p, 2, olho, fim);
        avisar(p, "\u00a74A onda varreu " + atingidos + " ser(es).");
    }

    // ------------------------------------------------------------------ OLHOS
    /** os olhos/penas: um risco carmesim para cada alvo na frente. */
    private static void olhos(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 olho = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        int n = 0;
        for (LivingEntity alvo : lv.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(20.0D), e -> e != p && e.isAlive())) {
            Vec3 ate = alvo.position().add(0, alvo.getBbHeight() * 0.5D, 0).subtract(olho);
            if (ate.normalize().dot(dir) < 0.5D) {
                continue;
            }
            alvo.hurt(lv.damageSources().magic(), 5.0F);
            mandar(lv, p, 3, olho, alvo.position().add(0, alvo.getBbHeight() * 0.5D, 0));
            n++;
            if (n >= 8) {
                break;
            }
        }
        lv.playSound(null, p.blockPosition(), SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.0F, 1.2F);
        if (n == 0) {
            mandar(lv, p, 3, olho, olho.add(dir.scale(14.0D)));
        }
    }

    // ------------------------------------------------------------------ PENAS
    /** a chuva de penas: uma cortina carmesim cai onde ele olha. */
    private static void penas(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 olho = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        Vec3 alvo = olho.add(dir.scale(18.0D));
        Vec3 fim = olho.add(dir.scale(26.0D));
        int atingidos = 0;
        for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class,
                new AABB(alvo.x - 8, alvo.y - 5, alvo.z - 8, alvo.x + 8, alvo.y + 5, alvo.z + 8),
                e -> e != p && e.isAlive())) {
            e.hurt(lv.damageSources().magic(), 8.0F);
            e.setRemainingFireTicks(60);
            atingidos++;
        }
        lv.sendParticles(ParticleTypes.CRIMSON_SPORE, alvo.x, alvo.y + 4, alvo.z, 160, 5.0D, 0.5D, 5.0D, 0.02D);
        lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, alvo.x, alvo.y, alvo.z, 40, 3.0D, 0.2D, 3.0D, 0.05D);
        lv.playSound(null, alvo.x, alvo.y, alvo.z, SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.0F, 0.7F);
        mandar(lv, p, 5, olho, fim);
        avisar(p, "\u00a74\u00a7lCHUVA DE PENAS \u00a77\u2014 " + atingidos + " atingido(s).");
    }

    // ------------------------------------------------------------------ BANIR
    /**
     * <b>O OLHO VERMELHO QUE BANE</b> (5.31.0) — a assinatura do Ronova.
     *
     * <p>O dono: <i>"quero a animação que faz do olho vermelho para banir a pessoa para outro
     * mundo do mod"</i>. Abre um olho carmesim gigante na frente de quem conjura e o alvo olhado
     * <b>é arrancado do mundo</b> e jogado na dimensão do outro mod (o mesmo reino que o Juízo
     * usa como labirinto). Se não houver alvo, o próprio conjurador é banido (é assim que o ADM
     * testa a cena).
     */
    private static void banir(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 olho = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        Vec3 olhoPos = olho.add(dir.scale(4.0D));
        Vec3 fim = olho.add(dir.scale(40.0D));
        LivingEntity alvo = null;
        HitResult hit = ProjectileUtil.getEntityHitResult(p, olho, fim,
                p.getBoundingBox().inflate(40.0D), e -> e instanceof LivingEntity && e != p, 0.6F);
        if (hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
            alvo = le;
        }
        // o OLHO: o cliente desenha a abertura + a íris + o clarão
        mandar(lv, p, 6, olhoPos, alvo != null ? alvo.position() : fim);
        lv.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.4F, 0.5F);
        lv.playSound(null, p.blockPosition(), SoundEvents.END_PORTAL_SPAWN,
                SoundSource.PLAYERS, 0.8F, 0.6F);
        lv.sendParticles(ParticleTypes.SQUID_INK, olhoPos.x, olhoPos.y, olhoPos.z, 80, 1.6D, 1.6D, 1.6D, 0.02D);
        if (alvo instanceof ServerPlayer vitima) {
            if (JuizoReino.entrar(vitima)) {
                avisar(vitima, "\u00a74\u00a7lO OLHO SE ABRIU \u2014 voc\u00ea foi banido.", 8, 2);
            } else {
                avisar(p, "\u00a7cO outro mod não está instalado: ninguém foi banido.");
            }
            avisar(p, "\u00a74Banido: \u00a7f" + vitima.getName().getString());
        } else if (alvo != null) {
            // mob: não atravessa dimensão, é apagado pelo olho
            alvo.hurt(lv.damageSources().magic(), 30.0F);
            alvo.discard();
            avisar(p, "\u00a74O olho apagou o ser que estava na frente.");
        } else if (JuizoReino.entrar(p)) {
            avisar(p, "\u00a74\u00a7lO OLHO SE ABRIU \u2014 você foi banido.", 8, 2);
        } else {
            avisar(p, "\u00a7cSem alvo na frente (e sem o outro mod para banir você).");
        }
    }

    // ------------------------------------------------------------------ ajuda
    private static void mandar(ServerLevel lv, ServerPlayer p, int tipo, Vec3 de, Vec3 ate) {
        VazioPoderPayload pacote = new VazioPoderPayload(tipo,
                de.x, de.y, de.z, ate.x, ate.y, ate.z);
        for (ServerPlayer outro : lv.players()) {
            if (outro.distanceToSqr(p) > 96.0D * 96.0D) {
                continue;
            }
            try {
                PacketDistributor.sendToPlayer(outro, pacote);
            } catch (Throwable ignorado) {
                // jogador de teste (sem rede)
            }
        }
    }

    private static void avisar(ServerPlayer p, String texto) {
        avisar(p, texto, 4, 1);
    }

    /** aviso na barra de ação (fica {@code segundos} na tela) + a linha no chat. */
    private static void avisar(ServerPlayer p, String texto, int segundos, int estilo) {
        try {
            dev.rptag.JuizoFinal.avisoPublico(p, texto, segundos, estilo);
        } catch (Throwable ignorado) {
            try {
                p.displayClientMessage(Component.literal(texto), true);
            } catch (Throwable semTela) {
                // jogador de teste
            }
        }
    }



    /** o ajudante: solta a onda quando ele leva um golpe? (não — os poderes são na mão do ADM) */
    public static void esquecer(UUID id) {
        PRONTO.keySet().removeIf(k -> k.startsWith(id.toString()));
    }

    /** atalho usado pelo comando: entity que o jogador está olhando (para o /vazio poder). */
    public static Entity olhando(ServerPlayer p, double alcance) {
        Vec3 olho = p.getEyePosition();
        Vec3 fim = olho.add(p.getLookAngle().normalize().scale(alcance));
        HitResult hit = ProjectileUtil.getEntityHitResult(p, olho, fim,
                p.getBoundingBox().inflate(alcance), e -> e != p, 0.5F);
        return hit instanceof EntityHitResult ehr ? ehr.getEntity() : null;
    }
}
