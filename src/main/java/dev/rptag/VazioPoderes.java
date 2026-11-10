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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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
            avisar(p, "\u00a78Os poderes só respondem no vazio, no reino ou para o ADM.");
            return 0;
        }
        long agora = System.currentTimeMillis();
        String chave = p.getUUID() + ":" + poder.id;
        Long pronto = PRONTO.get(chave);
        if (pronto != null && agora < pronto) {
            // (5.32.0) sem texto de recarga: o próprio leque mostra o que está pronto
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
            return 0;
        }
    }

    // ------------------------------------------------------------------ dano
    /**
     * (5.32.0) <b>O DANO QUE NÃO ACONTECIA.</b>
     *
     * <p>O dono: <i>"os ataques a maior parte não está dando dano e não funciona"</i>. A causa:
     * no Minecraft, depois de levar UM golpe o mob entra em 10 ticks de invulnerabilidade
     * ({@code invulnerableTime}); tudo o que a gente aplicava nesse intervalo era <b>ignorado em
     * silêncio</b> — e como os poderes saem em sequência, quase nada passava. Além disso
     * {@code damageSources().magic()} é reduzido por Resistência e por armadura encantada.
     *
     * <p>Aqui: zera a invulnerabilidade, aplica o dano <b>sem escudo</b> ({@code Bypassarmor} não
     * existe no vanilla — usamos {@code playerAttack} do conjurador, que é o que mais se comporta
     * como ataque de verdade) e <b>confere</b> se o alvo realmente perdeu vida; se não perdeu,
     * aplica o dano genérico como rede de segurança.
     *
     * @return true se o alvo levou dano de verdade
     */
    private static boolean ferir(ServerPlayer quem, LivingEntity alvo, float dano) {
        if (alvo == null || !alvo.isAlive()) {
            return false;
        }
        try {
            float antes = alvo.getHealth();
            alvo.invulnerableTime = 0;                       // o conserto principal
            var fonte = quem != null
                    ? quem.damageSources().playerAttack(quem)
                    : alvo.damageSources().generic();
            alvo.hurt(fonte, dano);
            if (alvo.getHealth() >= antes && alvo.isAlive()) {
                alvo.invulnerableTime = 0;
                alvo.hurt(alvo.damageSources().genericKill(), Math.max(1.0F, dano * 0.5F));
            }
            return alvo.getHealth() < antes || !alvo.isAlive();
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: aplicar dano do poder falhou", t);
            return false;
        }
    }

    // ------------------------------------------------------------------ RAIO
    /** o raio carmesim: um risco de 60 blocos que atravessa e explode no fim. */
    private static void raio(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 olho = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        Vec3 fim = olho.add(dir.scale(60.0D));
        int acertos = 0;
        // (5.32.0) antes só o PRIMEIRO alvo levava; agora tudo o que a linha atravessa leva
        for (LivingEntity alvo : lv.getEntitiesOfClass(LivingEntity.class,
                new AABB(olho, fim).inflate(3.0D), e -> e != p && e.isAlive())) {
            Vec3 ate = alvo.position().add(0, alvo.getBbHeight() * 0.5D, 0).subtract(olho);
            double comprimento = ate.dot(dir);
            if (comprimento < 0 || comprimento > 60.0D) {
                continue;
            }
            double distancia = ate.subtract(dir.scale(comprimento)).length();
            if (distancia > 2.2D) {
                continue;   // fora do cano do raio
            }
            if (ferir(p, alvo, 16.0F)) {
                acertos++;
            }
            alvo.setRemainingFireTicks(100);
            Vec3 empurra = ate.normalize().scale(1.4D);
            alvo.push(empurra.x, 0.45D, empurra.z);
        }
        lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, fim.x, fim.y, fim.z, 60, 1.4D, 1.0D, 1.4D, 0.06D);
        lv.sendParticles(ParticleTypes.EXPLOSION, fim.x, fim.y, fim.z, 3, 0.6D, 0.6D, 0.6D, 0.0D);
        lv.playSound(null, p.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1.2F, 0.6F);
        mandar(lv, p, 0, olho, fim);
    }


    // ------------------------------------------------------------------ ORBE
    /** os orbes: estoura no primeiro ser vivo que encostar (e esquenta em área). */
    private static void orbe(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 olho = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        double alcance = 28.0D;
        Vec3 impacto = olho.add(dir.scale(alcance));

        // (5.35.0) <b>A ORBE NÃO DAVA DANO — e o motivo era o caminho.</b>
        //
        // <p>Antes eu perguntava ao jogo "tem alguém na linha?" com um raycast. Só que a
        // entidade tem que estar EXATAMENTE na linha do olho: meio bloco de diferença e a
        // resposta era "ninguém", então a orbe caía a 28 blocos de distância e estourava no
        // ar. Agora ela é <b>varrida de verdade</b>: o trajeto é percorrido de 0,4 em 0,4
        // bloco e a primeira criatura que estiver naquele ponto (com folga de 0,85) é o alvo.
        // Depois disso o estouro pega todo mundo colado no impacto.
        try {
            BlockHitResult parede = lv.clip(new ClipContext(olho, impacto,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (parede.getType() != HitResult.Type.MISS) {
                impacto = parede.getLocation();      // bateu na parede: estoura ali
            }
        } catch (Throwable ignorado) {
            // sem clip: segue a linha inteira
        }

        LivingEntity acertado = null;
        double limite = olho.distanceTo(impacto);
        Vec3 ponto = impacto;
        for (double d = 0.6D; d <= limite; d += 0.4D) {
            Vec3 amostra = olho.add(dir.scale(d));
            LivingEntity achou = null;
            double menor = Double.MAX_VALUE;
            for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class,
                    new AABB(amostra.x - 0.9D, amostra.y - 0.9D, amostra.z - 0.9D,
                            amostra.x + 0.9D, amostra.y + 0.9D, amostra.z + 0.9D),
                    e -> e != p && e.isAlive())) {
                double q = e.position().add(0, e.getBbHeight() * 0.5D, 0).distanceToSqr(amostra);
                if (q < menor) {
                    menor = q;
                    achou = e;
                }
            }
            if (achou != null) {
                acertado = achou;
                ponto = amostra;
                break;
            }
        }
        impacto = ponto;

        // o dano direto em quem foi atingido
        if (acertado != null) {
            ferir(p, acertado, 16.0F);
            acertado.setRemainingFireTicks(100);
            acertado.push(dir.x * 0.8D, 0.35D, dir.z * 0.8D);
        }
        // o estouro em área (pega quem estiver colado no ponto de impacto)
        final LivingEntity jaAtingido = acertado;
        final Vec3 onde = impacto;
        for (LivingEntity perto : lv.getEntitiesOfClass(LivingEntity.class,
                new AABB(onde.x - 4.0D, onde.y - 4.0D, onde.z - 4.0D,
                        onde.x + 4.0D, onde.y + 4.0D, onde.z + 4.0D),
                e -> e != p && e != jaAtingido && e.isAlive())) {
            ferir(p, perto, 9.0F);
            perto.setRemainingFireTicks(60);
            Vec3 fora = perto.position().subtract(onde).normalize().scale(0.6D);
            perto.push(fora.x, 0.35D, fora.z);
        }
        lv.sendParticles(ParticleTypes.DRAGON_BREATH, impacto.x, impacto.y, impacto.z,
                40, 0.8D, 0.8D, 0.8D, 0.05D);
        lv.sendParticles(ParticleTypes.EXPLOSION, impacto.x, impacto.y, impacto.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
        lv.playSound(null, p.blockPosition(), SoundEvents.ENDER_DRAGON_HURT,
                SoundSource.PLAYERS, 0.6F, 1.4F);
        lv.playSound(null, net.minecraft.core.BlockPos.containing(impacto),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.3F);
        mandar(lv, p, 1, olho, impacto);
        RPTagMod.LOGGER.info("RP Tag: orbe de {} -> {} ({} bloco(s))",
                p.getName().getString(),
                acertado == null ? "o chao/vazio" : acertado.getName().getString(),
                (int) olho.distanceTo(impacto));
    }

    // ------------------------------------------------------------------ ONDA
    /**
     * a onda: tudo na frente dela congela e apanha.
     *
     * <p>(5.32.0) o dono: <i>"o onda está mostrando um avisar na minha tela, não quero isso"</i>.
     * O aviso "A onda varreu N seres" saiu — o poder fala pelo efeito, não por texto.
     */
    private static void onda(ServerPlayer p) {
        ServerLevel lv = p.serverLevel();
        Vec3 olho = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        for (LivingEntity alvo : lv.getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(18.0D), e -> e != p && e.isAlive())) {
            Vec3 ate = alvo.position().subtract(p.position());
            if (ate.lengthSqr() > 18.0D * 18.0D) {
                continue;
            }
            if (ate.normalize().dot(dir) < 0.45D) {
                continue;   // só o que está na frente
            }
            ferir(p, alvo, 9.0F);
            alvo.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6, false, false));
            alvo.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, false));
            alvo.push(ate.x * 0.25D, 0.4D, ate.z * 0.25D);
        }
        Vec3 fim = olho.add(dir.scale(16.0D));
        lv.sendParticles(ParticleTypes.SOUL, fim.x, fim.y, fim.z, 90, 7.0D, 1.6D, 7.0D, 0.02D);
        lv.playSound(null, p.blockPosition(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.9F, 0.6F);
        mandar(lv, p, 2, olho, fim);
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
            ferir(p, alvo, 7.0F);
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
            ferir(p, e, 10.0F);
            e.setRemainingFireTicks(60);
            atingidos++;
        }
        lv.sendParticles(ParticleTypes.CRIMSON_SPORE, alvo.x, alvo.y + 4, alvo.z, 160, 5.0D, 0.5D, 5.0D, 0.02D);
        lv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, alvo.x, alvo.y, alvo.z, 40, 3.0D, 0.2D, 3.0D, 0.05D);
        lv.playSound(null, alvo.x, alvo.y, alvo.z, SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.0F, 0.7F);
        mandar(lv, p, 5, olho, fim);
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
        // (5.33.0) o cone de mira decide quem vai; sem alvo, nada acontece (nunca o próprio)
        banirAlvo(p, alvoNaMira(p, 48.0D, 0.35D));
    }

    /**
     * (5.33.0) <b>BANIR UM ALVO CONCRETO</b> — o miolo do poder, separado da mira.
     *
     * <p>Serve para os dois caminhos: o leque/tecla (que passa o que está na mira) e o
     * {@code /vazio soltar poder banir <jogador>} (que passa a pessoa escolhida pelo ADM).
     * O conjurador <b>nunca</b> é banido: se o alvo for ele mesmo, o método devolve
     * {@code false} e não faz nada.
     *
     * @return true se alguém foi mandado para a dimensão do banir
     */
    public static boolean banirAlvo(ServerPlayer p, LivingEntity alvo) {
        ServerLevel lv = p.serverLevel();
        Vec3 olho = p.getEyePosition();
        Vec3 dir = p.getLookAngle().normalize();
        Vec3 olhoPos = olho.add(dir.scale(4.0D));
        if (alvo == p) {
            return false;                       // o conjurador nunca paga a conta
        }
        Vec3 alvoPos = alvo != null
                ? alvo.position().add(0, alvo.getBbHeight() * 0.5, 0)
                : olho.add(dir.scale(30.0D));

        // o OLHO abre na frente de quem conjura, virado pro alvo
        mandar(lv, p, 6, olhoPos, alvoPos);
        lv.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.PLAYERS, 1.4F, 0.5F);
        lv.playSound(null, p.blockPosition(), SoundEvents.END_PORTAL_SPAWN,
                SoundSource.PLAYERS, 0.8F, 0.6F);
        lv.sendParticles(ParticleTypes.SQUID_INK, olhoPos.x, olhoPos.y, olhoPos.z, 80, 1.6D, 1.6D, 1.6D, 0.02D);

        RPTagMod.LOGGER.info("RP Tag: banir de {} em {}", p.getName().getString(),
                alvo == null ? "ninguem (sem alvo na mira)" : alvo.getName().getString());
        if (alvo instanceof ServerPlayer vitima) {
            // vai para a DIMENSÃO DO OUTRO MOD (é "a dimensão do banir" — nada de terceira)
            if (!JuizoReino.banirPara(vitima)) {
                RPTagMod.LOGGER.warn("RP Tag: banir nao achou a dimensao do outro mod");
                lv.sendParticles(ParticleTypes.SMOKE, alvoPos.x, alvoPos.y, alvoPos.z, 40, 1, 1, 1, 0.02);
                return false;
            }
            return true;
        }
        if (alvo != null) {
            alvo.hurt(lv.damageSources().magic(), 40.0F);   // mob não atravessa dimensão: é apagado
            alvo.discard();
            return true;
        }
        return false;                            // (5.33.0) SEM ALVO = SEM BANIMENTO
    }

    /**
     * (5.33.0) acha o ser vivo mais perto da mira, dentro de um cone (aim assist suave).
     *
     * @param cosMinimo 1.0 = mira perfeita; 0.55 ≈ 56° de abertura
     */
    public static LivingEntity alvoNaMira(ServerPlayer p, double alcance, double cosMinimo) {
        try {
            Vec3 olho = p.getEyePosition();
            Vec3 dir = p.getLookAngle().normalize();
            // (5.34.0) PRIMEIRO A MIRA DE VERDADE: quem está na linha do olho, até 48 blocos.
            // O dono: "o banir eu quero conseguir banir quem eu ver". Se a pessoa está na
            // frente, é ela — sem depender de estar exatamente no centro do cone.
            try {
                Vec3 fim = olho.add(dir.scale(alcance));
                net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(olho, fim).inflate(1.2D);
                LivingEntity naLinha = null;
                double menorDist = Double.MAX_VALUE;
                for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, area,
                        e -> e != p && e.isAlive())) {
                    net.minecraft.world.phys.AABB caixa = e.getBoundingBox().inflate(0.35D);
                    java.util.Optional<Vec3> bate = caixa.clip(olho, fim);
                    if (bate.isEmpty()) {
                        continue;
                    }
                    double d = bate.get().distanceToSqr(olho);
                    if (d < menorDist) {
                        menorDist = d;
                        naLinha = e;
                    }
                }
                if (naLinha != null) {
                    return naLinha;
                }
            } catch (Throwable ignorado) {
                // sem mira exata: cai no cone
            }
            LivingEntity melhor = null;
            double melhorDot = cosMinimo;
            for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class,
                    p.getBoundingBox().inflate(alcance), e -> e != p && e.isAlive())) {
                Vec3 ate = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(olho);
                double dist = ate.length();
                if (dist > alcance || dist < 0.4) {
                    continue;
                }
                double dot = ate.normalize().dot(dir);
                if (dot > melhorDot) {
                    melhorDot = dot;
                    melhor = e;
                }
            }
            return melhor;
        } catch (Throwable t) {
            return null;
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



    /**
     * (5.32.0) acende a aura SEM soltar poder — usado pelo {@code /vazio ligar} (modo deus).
     * O cliente escuta pelo pacote de "poder 7" (só visual); aqui só mandamos o recado.
     */
    public static void acenderAura(ServerPlayer p) {
        try {
            if (p.level() instanceof ServerLevel lv) {
                mandar(lv, p, 7, p.getEyePosition(), p.position());
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: acender a aura falhou", t);
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
