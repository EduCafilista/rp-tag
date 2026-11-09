package dev.rptag;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (5.26.0) A CIBERPSICOSE QUE ATACA — pedido do dono:
 *
 * <blockquote>"oque mais podemos fazer no efeito de ciberpsicose? ele atacar as outras pessoas sem
 * ele quere? ou atacar qualquer ser vivo perto dele?"</blockquote>
 *
 * <p>As duas coisas, na verdade: enquanto o efeito {@code rptag:ciberpsicose} está no jogador, ele
 * <b>perde o controle das próprias mãos</b>. De tempos em tempos (2 a 4 s, sem ritmo fixo — a
 * psicose não é pontual) o corpo dele <b>escolhe sozinho</b> um alvo e bate:
 *
 * <ul>
 *   <li><b>gente primeiro</b>: se tem jogador por perto, é nele que a mão vai (é a cena que
 *       interessa no RP — a pessoa surta e acerta o amigo);</li>
 *   <li>senão, <b>qualquer ser vivo</b>: mob, bicho, aldeão, o que estiver ao alcance;</li>
 *   <li>alcance de 4,5 blocos (um pouco mais que o braço, porque ele "avança" sem perceber).</li>
 * </ul>
 *
 * <p>O golpe é um golpe de verdade: usa o caminho do jogo ({@link Player#attack(Entity)}), então
 * vale o item na mão, encantamento, dano, knockback, som e partículas — <b>e conta como ataque do
 * jogador</b> (mob revida, jogador morre se a vida acabar). Não é dano inventado por cima.
 *
 * <p>Quem não quer essa violência pode simplesmente não dar o efeito, ou dar o efeito pelo
 * {@code /effect give} — aí ele dura o que o comando mandar e o comportamento é o mesmo.
 */
public final class CiberpsicoseSistema {

    /** até onde a mão dele alcança quando ele "não está no controle". */
    private static final double ALCANCE = 5.5D;   // (5.27.0) era 4,5: agora pega quem está perto

    /** quando (ms) o próximo golpe involuntário de cada jogador. */
    private static final Map<UUID, Long> PROXIMO = new ConcurrentHashMap<>();
    /** quantos golpes ele já deu nesse surto (só pra variar as falas). */
    private static final Map<UUID, Integer> GOLPES = new ConcurrentHashMap<>();

    /** (5.28.0) quem estava com o efeito no segundo anterior (pra avisar quando acabar). */
    private static final java.util.Set<UUID> TINHA = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final String[] FALAS = {
            "sua m\u00e3o se move sozinha",
            "voc\u00ea n\u00e3o mandou o bra\u00e7o fazer isso",
            "alguma coisa dentro de voc\u00ea decidiu",
            "os dedos fecham antes de voc\u00ea pensar",
            "o corpo dele(a) responde a outra voz",
    };

    private CiberpsicoseSistema() {
    }

    /** chamado de 1 em 1 segundo pelo {@link ServerEvents} — só trabalha pra quem tem o efeito. */
    public static void tick(ServerPlayer p) {
        if (!p.hasEffect(RPEffects.CIBERPSICOSE)) {
            PROXIMO.remove(p.getUUID());
            GOLPES.remove(p.getUUID());
            // (5.28.0) O EFEITO ACABOU — manda o cliente FECHAR A TELA. O dono reclamou que
            // "o efeito de ciberpsicose nao saiu mais da minha tela": quando o efeito expirava
            // sozinho o cliente podia não perceber (troca de dimensão, morte, reconexão) e a
            // tela ficava presa. Agora o servidor AVISA que acabou.
            if (TINHA.remove(p.getUUID())) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                        new ScreenTextPayload("", 1, 14));
            }
            return;
        }
        TINHA.add(p.getUUID());
        // (5.27.0) O DONO ESTAVA EM CREATIVE E NADA ACONTECIA ("ativei o cyberpsicose e nao bati
        // nos mobs a minha volta"). Antes o sistema pulava jogador em creative — e ADM testa em
        // creative. Agora o único caso que não tem golpe é espectador/morto (não tem corpo).
        if (p.isSpectator() || p.isDeadOrDying()) {
            return;
        }
        long agora = System.currentTimeMillis();
        Long quando = PROXIMO.get(p.getUUID());
        if (quando != null && agora < quando) {
            return;
        }
        // (5.28.0) O DONO: "o efeito ciberpsicose pode bater mais rapido". Era 1,2 a 2,6 s;
        // agora é de 0,45 a 0,9 s — a mão dele dispara em quase todo bicho que encosta.
        PROXIMO.put(p.getUUID(), agora + 450L + (long) (Math.random() * 450.0D));

        LivingEntity alvo = acharAlvo(p);
        if (alvo == null) {
            return;   // ninguém por perto: a fissura vem depois
        }
        try {
            golpear(p, alvo);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: golpe da ciberpsicose falhou", t);
        }
    }

    /**
     * quem vai levar: jogador mais perto primeiro (é a cena que importa no RP); se não tiver
     * ninguém, o ser vivo mais próximo de qualquer tipo.
     */
    public static LivingEntity acharAlvo(ServerPlayer p) {
        AABB area = p.getBoundingBox().inflate(ALCANCE);
        LivingEntity melhorJogador = null;
        LivingEntity melhorBicho = null;
        double distJogador = Double.MAX_VALUE;
        double distBicho = Double.MAX_VALUE;
        for (Entity e : p.level().getEntities(p, area)) {
            if (!(e instanceof LivingEntity vivo) || vivo == p || vivo.isDeadOrDying()) {
                continue;
            }
            if (vivo instanceof Player outro && outro.isSpectator()) {
                continue;
            }
            double d = vivo.distanceToSqr(p);
            if (d > ALCANCE * ALCANCE) {
                continue;
            }
            if (vivo instanceof Player) {
                if (d < distJogador) {
                    distJogador = d;
                    melhorJogador = vivo;
                }
            } else if (vivo instanceof Mob || vivo instanceof net.minecraft.world.entity.animal.Animal
                    || vivo instanceof net.minecraft.world.entity.npc.AbstractVillager) {
                if (d < distBicho) {
                    distBicho = d;
                    melhorBicho = vivo;
                }
            }
        }
        return melhorJogador != null ? melhorJogador : melhorBicho;
    }

    /** o golpe: ele vira o corpo, o braço desce — e o dano é o do jogo, de verdade. */
    private static void golpear(ServerPlayer p, LivingEntity alvo) {
        // 1) o corpo se vira sozinho
        Vec3 meio = alvo.position().add(0.0D, alvo.getBbHeight() * 0.5D, 0.0D);
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, meio);

        // 2) o braço desce (animação de verdade pra quem está olhando)
        p.swing(InteractionHand.MAIN_HAND, true);

        // 3) o golpe — o caminho de um ataque NORMAL do jogador (item na mão, encantamento,
        //    knockback, crítico, morte creditada a ele). Quem está no controle é o corpo: o
        //    jogador não estava clicando, então a barra de ataque dele está cheia e o golpe sai
        //    com força total.
        p.attack(alvo);

        // 4) o rastro: som do impacto + sangue de verdade (se o jogo achou que machucou)
        var lv = p.serverLevel();
        lv.playSound(null, alvo.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                SoundSource.PLAYERS, 0.8F, 0.7F + (float) Math.random() * 0.4F);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                meio.x, meio.y, meio.z, 4, 0.2D, 0.2D, 0.2D, 0.05D);
        lv.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                meio.x, meio.y, meio.z, 6, 0.25D, 0.25D, 0.25D, 0.1D);

        // 5) e ele fica sabendo que não foi ele quem fez
        int n = GOLPES.merge(p.getUUID(), 1, Integer::sum);
        String fala = FALAS[(int) (Math.random() * FALAS.length)];
        Component quem = alvo instanceof Player outro
                ? Component.literal(outro.getName().getString()).withStyle(ChatFormatting.WHITE)
                : Component.literal(alvo.getName().getString()).withStyle(ChatFormatting.GRAY);
        p.displayClientMessage(
                Component.literal("\u26a1 ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal("Voc\u00ea acertou ").withStyle(ChatFormatting.AQUA))
                        .append(quem)
                        .append(Component.literal(" \u2014 " + fala + ".").withStyle(ChatFormatting.AQUA)),
                true);
        if (n == 1 || n % 5 == 0) {
            p.sendSystemMessage(Component.literal(
                            "A ciberpsicose est\u00e1 usando as suas m\u00e3os (" + n + " golpe(s) que voc\u00ea n\u00e3o deu).")
                    .withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        }
    }

    /** quantos golpes involuntários ele já deu com o efeito ativo (usado na prova/log). */
    public static int golpes(UUID id) {
        return GOLPES.getOrDefault(id, 0);
    }

    /** só pra conferência: limpa o estado (querysto do comando parar). */
    public static void esquecer(UUID id) {
        PROXIMO.remove(id);
        GOLPES.remove(id);
    }
}
