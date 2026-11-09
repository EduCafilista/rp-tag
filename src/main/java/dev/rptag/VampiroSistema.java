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
 *   <li><b>usando</b> (clique direito): dá o efeito <b>PROTEÇÃO DO ECLIPSE</b>
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

    /** a última morte que o mod reconheceu como "foi o sol" — só pra conferência no servidor. */
    public static volatile String ULTIMA_MORTE_PELO_SOL = "nenhuma";

    private VampiroSistema() {
    }

    /**
     * (5.25.2) <b>Esta morte foi o SOL?</b> — pedido do dono: <i>"quando tiver morto para o sol tem
     * que ser um aviso, que morreu pegando fogo por causa do sol, algo assim melhore"</i>.
     *
     * <p>Só conta quando as DUAS coisas batem:
     * <ol>
     *   <li>a causa da morte é <b>fogo</b> (queimadura, estar em chamas) — queda, mob, faca não são;</li>
     *   <li>o fogo foi <b>o NOSSO</b>: o jogador estava na marca do sol (a que a gente põe a cada
     *       segundo em {@link #tick}) há menos de 10 s — que é exatamente quanto dura o fogo do sol.
     * </ol>
     *
     * <p>Assim quem morreu na lava, no fogo do amigo ou de espada não ganha o aviso do sol por
     * engano, e quem correu pra sombra morrendo do fogo que o sol acendeu ganha.
     */
    public static boolean morreuPeloSol(ServerPlayer p, net.minecraft.world.damagesource.DamageSource fonte) {
        if (fonte == null || !fonte.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            return false;   // não morreu de fogo: não é o sol
        }
        Long quando = ACESO_PELO_SOL.get(p.getUUID());
        if (quando == null) {
            return false;   // o sol nunca acendeu fogo neste jogador
        }
        return System.currentTimeMillis() - quando < 10_000L;
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
    /** o amuleto na mão (principal ou secundária) ou o efeito PROTEÇÃO DO ECLIPSE. */
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
        // (5.25.2) o "é dia?" agora vem do RELÓGIO do mundo, igual à visão noturna: o isDay() do
        // jogo só atualiza quando o céu é recalculado, então logo depois de mudar a hora ele ainda
        // responde a hora antiga (foi o que a prova pegou). Sol = 23 000 -> 13 000.
        return !noite(p) && !p.isInWaterOrRain() && lv.canSeeSky(p.blockPosition());
    }

    /**
     * Roda a cada segundo (o {@link ServerEvents} chama). Barato: sai na primeira linha pra quem
     * não é vampiro — o servidor inteiro custa praticamente nada.
     */
    public static void tick(ServerPlayer p) {
        if (!ehVampiro(p)) {
            ACESO_PELO_SOL.remove(p.getUUID());
            if (AVISADO.remove(p.getUUID()) != null) {
                try {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                            new VampiroNoitePayload(false));
                } catch (Throwable ignored) {
                }
            }
            return;
        }
        visaoNoturna(p);
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

    /**
     * (5.25.1) VISÃO NOTURNA DO VAMPIRO — pedido do dono: <i>"a noite o vampiro vai ter night
     * vision, para ele não ter só debuff"</i>.
     *
     * <p>À noite o vampiro enxerga no escuro (é o mínimo: o sol já é um castigo). De dia não damos
     * nada. Não removemos o efeito de ninguém: a visão que damos dura 16 s e é renovada a cada
     * segundo — quando amanhece ela simplesmente expira sozinha, sem apagar a poção que o jogador
     * tenha tomado.
     */
    /**
     * É noite? A conta é feita no RELÓGIO do mundo (13 000 = anoitecer, 23 000 = amanhecer), do
     * mesmo jeito que o céu escurece — assim não dependemos do {@code isDay()} do jogo (que só
     * liga/desliga ao meio-dia e à meia-noite e é usado para a queimadura do sol).
     */
    public static boolean noite(ServerPlayer p) {
        long t = p.serverLevel().getDayTime() % 24000L;
        return t >= 13000L && t < 23000L;
    }

    /**
     * (5.26.0) <b>Escuro o bastante pra visão de vampiro</b> — do anoitecer (12 000) ao raiar
     * (23 600). É mais largo que a janela do sol de propósito: o dono reclamou que a visão noturna
     * "está falhando", e um dos motivos era justamente o começo da noite (12 000–13 000, quando o
     * céu já está escuro) ficar de fora.
     */
    public static boolean escuro(ServerPlayer p) {
        long t = p.serverLevel().getDayTime() % 24000L;
        return t >= 12000L && t < 23600L;
    }

    /**
     * (5.26.0) VISÃO NOTURNA — agora em duas camadas, porque só o efeito do jogo estava falhando
     * pra quem usa shader:
     * <ol>
     *   <li>o <b>efeito</b> NIGHT_VISION (ícone + a cura/brilho normal do Minecraft), dado por
     *       <b>2 minutos</b> e renovado quando falta menos de 1 minuto — antes eram 16 s renovados
     *       a cada segundo, e qualquer tick perdido (lag, respawn, mundo carregando) deixava o
     *       vampiro no escuro;</li>
     *   <li>o <b>aviso pro cliente</b> ({@link VampiroNoitePayload}) de que ele está na noite dele:
     *       o cliente então acende a "visão de vampiro" do mod (um clarão prateado no escuro), que
     *       não depende de shader nenhum.</li>
     * </ol>
     */
    private static void visaoNoturna(ServerPlayer p) {
        boolean noiteDele = escuro(p);
        avisarCliente(p, noiteDele);
        if (!noiteDele) {
            return;   // de dia o vampiro não ganha nada (e a queimadura continua valendo)
        }
        var atual = p.getEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
        if (atual != null && atual.getDuration() > 1200) {
            return;   // já tem visão noturna de sobra: não gasta pacote à toa
        }
        p.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,
                2400, 0, false, false, true));   // 2 min, renovado quando cai pra menos de 1 min
    }

    /** true = o cliente já sabe que este jogador está no modo "vampiro de noite" (pra não repetir). */
    private static final Map<UUID, Boolean> AVISADO = new java.util.concurrent.ConcurrentHashMap<>();

    private static void avisarCliente(ServerPlayer p, boolean noiteDele) {
        Boolean antes = AVISADO.put(p.getUUID(), noiteDele);
        // manda quando muda — e de vez em quando (a cada ~5 s) pra se o cliente recarregar
        boolean mudou = antes == null || antes != noiteDele;
        boolean relembrar = p.tickCount % 100 < 20;
        if (!mudou && !relembrar) {
            return;
        }
        try {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                    new VampiroNoitePayload(noiteDele));
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: aviso de noite do vampiro falhou", t);
        }
    }

    /** usado pelo comando pra explicar ao ADM onde o jogador está levando o sol. */
    public static String estado(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        sb.append(ehVampiro(p) ? "VAMPIRO (sol queima)" : "normal");
        if (ehVampiro(p) && noite(p)) {
            sb.append(" · vis\u00e3o noturna");
        }
        if (ehVampiro(p)) {
            sb.append(protegido(p) ? " · protegido agora" : noSol(p) ? " · NO SOL agora" : " · na sombra");
        }
        return sb.toString();
    }

    /** usado pelo item: aplica a PROTEÇÃO DO ECLIPSE (2 min). */
    public static void sombra(ServerPlayer p, int segundos) {
        p.addEffect(new MobEffectInstance(RPEffects.SOMBRA, segundos * 20, 0, false, true, true));
    }
}
