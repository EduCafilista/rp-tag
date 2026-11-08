package dev.rptag;

import java.util.Map;
import java.util.UUID;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * (5.25.0) O VAMPIRO — <b>o sol queima</b>.
 *
 * <p>Pedido do dono: <i>"quero criar um atributo, que se eu atribuir a algum player ele toma dano
 * ao sol, como se fosse um vampiro, e ter algum item que se ele usar ou estiver na mao, para ele
 * evitar tomar dano do sol"</i>.
 *
 * <h2>Como o sol machuca</h2>
 * <p>Igual ao zumbi do Minecraft: se o jogador é vampiro (atributo {@code rptag:vampiro} acima de
 * 0,5), é <b>dia</b>, ele está com o <b>céu à vista</b> (sem teto, sem folha em cima) e <b>não
 * está na água nem na chuva</b>, ele <b>pega fogo</b> e o fogo come a vida dele. Nada é inventado
 * por cima: quem dá o dano é o próprio jogo (o mesmo dano de estar em chamas), então armadura,
 * resistência a fogo, leite e tudo o mais continuam valendo como no vanilla.
 *
 * <h2>Como se proteger (o item)</h2>
 * <p>O <b>Amuleto do Eclipse</b> ({@code rptag:amuleto_eclipse}) protege de dois jeitos:
 * <ul>
 *   <li><b>na mão</b> (principal <i>ou</i> secundária): proteção passiva, sem apertar nada;</li>
 *   <li><b>usando</b> (clique direito): dá o efeito <b>Sombra do Eclipse</b>
 *       ({@code rptag:sombra}, 2 min) — aí pode guardar o amuleto que a sombra continua.</li>
 * </ul>
 * Quando a proteção termina em cima do sol, o fogo volta na hora.
 *
 * <p>O fogo só é apagado pela proteção quando foi <b>o sol</b> que acendeu (ficamos de olho em
 * quem foi aceso por nós): quem caiu na lava continua queimando, como deve ser.
 */
public final class VampiroSistema {

    /** 0 = normal · 1 = vampiro (o meio é o limite; ver {@link #ehVampiro}). */
    private static final double LIMIAR = 0.5D;

    /** quando (ms) o SOL acendeu cada jogador — pra não apagar fogo que não foi nosso. */
    private static final Map<UUID, Long> ACESO_PELO_SOL = new java.util.concurrent.ConcurrentHashMap<>();

    private VampiroSistema() {
    }

    // ------------------------------------------------------------------ atributo
    public static double nivel(ServerPlayer p) {
        var inst = p.getAttribute(RPAttributes.VAMPIRO);
        return inst == null ? 0.0D : inst.getValue();
    }

    public static boolean ehVampiro(ServerPlayer p) {
        return nivel(p) > LIMIAR;
    }

    /** liga/desliga o atributo e devolve o estado novo (true = virou vampiro). */
    public static boolean definir(ServerPlayer p, boolean ligado) {
        var inst = p.getAttribute(RPAttributes.VAMPIRO);
        if (inst == null) {
            return false;
        }
        inst.setBaseValue(ligado ? 1.0D : 0.0D);
        if (!ligado) {
            ACESO_PELO_SOL.remove(p.getUUID());
            p.clearFire();
        }
        return ligado;
    }

    // ------------------------------------------------------------------ proteção
    /** o amuleto na mão (principal ou secundária) ou o efeito Sombra do Eclipse. */
    public static boolean protegido(ServerPlayer p) {
        if (p.hasEffect(RPEffects.SOMBRA)) {
            return true;
        }
        return p.getMainHandItem().is(RPTagMod.AMULETO_ECLIPSE.get())
                || p.getOffhandItem().is(RPTagMod.AMULETO_ECLIPSE.get());
    }

    // ------------------------------------------------------------------ o sol
    /** true = o sol está batendo nele AGORA (dia, céu à vista, fora da água e da chuva). */
    public static boolean noSol(ServerPlayer p) {
        var lv = p.serverLevel();
        return lv.isDay() && !p.isInWaterOrRain() && lv.canSeeSky(p.blockPosition());
    }

    /**
     * Roda a cada segundo (o {@link ServerEvents} chama). Barato: sai na primeira linha pra quem
     * não é vampiro — o servidor inteiro custa praticamente nada.
     */
    public static void tick(ServerPlayer p) {
        if (!ehVampiro(p)) {
            ACESO_PELO_SOL.remove(p.getUUID());
            return;
        }
        boolean protegido = protegido(p);
        if (protegido) {
            Long quando = ACESO_PELO_SOL.remove(p.getUUID());
            // apaga o fogo do SOL (o da lava continua: a marca só existe pra quem nós acendemos)
            if (quando != null && System.currentTimeMillis() - quando < 4000L) {
                p.clearFire();
            }
            return;
        }
        if (!noSol(p)) {
            ACESO_PELO_SOL.remove(p.getUUID());
            return;
        }
        // ---- o sol está queimando ----
        boolean jaQueimava = p.isOnFire();
        ACESO_PELO_SOL.put(p.getUUID(), System.currentTimeMillis());
        if (p.getRemainingFireTicks() < 200) {
            p.setRemainingFireTicks(200);   // o fogo se renova enquanto ele estiver no sol
        }
        if (!jaQueimava) {
            // o primeiro segundo: avisa o jogador e faz o barulho de pele queimando
            p.displayClientMessage(Component.literal("\u00a76\u00a7lO SOL QUEIMA A SUA PELE!")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.GENERIC_BURN,
                    SoundSource.PLAYERS, 0.9F, 1.25F);
        }
        var lv = p.serverLevel();
        double x = p.getX(), y = p.getY() + 1.0D, z = p.getZ();
        lv.sendParticles(ParticleTypes.FLAME, x, y, z, 6, 0.35D, 0.55D, 0.35D, 0.01D);
        lv.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.4D, z, 4, 0.3D, 0.4D, 0.3D, 0.01D);
        if (p.tickCount % 40 == 0) {
            lv.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0.2D, 0.2D, 0.2D, 0.0D);
        }
    }

    /** usado pelo comando pra explicar ao ADM onde o jogador está levando o sol. */
    public static String estado(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        sb.append(ehVampiro(p) ? "VAMPIRO (sol queima)" : "normal");
        if (ehVampiro(p)) {
            sb.append(protegido(p) ? " · protegido agora" : noSol(p) ? " · NO SOL agora" : " · na sombra");
        }
        return sb.toString();
    }

    /** usado pelo item: aplica a Sombra do Eclipse (2 min). */
    public static void sombra(ServerPlayer p, int segundos) {
        p.addEffect(new MobEffectInstance(RPEffects.SOMBRA, segundos * 20, 0, false, true, true));
    }
}
