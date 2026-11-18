package dev.rptag.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * (5.77.0) OTIMIZACAO — a QUALIDADE dos efeitos do mod, medida pelo FPS do cliente.
 *
 * <p>O problema: as cascas de aura redesenham o modelo do jogador dezenas de vezes por quadro
 * (5 aneis x 10 direcoes = 50 desenhos por jogador com aura). Num servidor cheio isso derruba o
 * FPS mesmo em PC bom. Aqui mora a regra que resolve isso sem tirar o efeito de quem esta perto:
 *
 * <ul>
 *   <li><b>ALTO</b> — tudo como sempre foi (ate 48 blocos de distancia).</li>
 *   <li><b>MEDIO</b> — metade das direcoes, no maximo 3 aneis e metade das particulas (36 blocos).</li>
 *   <li><b>BAIXO</b> — 1/3 das direcoes, 2 aneis, 1 particula por tick, 28 blocos.</li>
 *   <li><b>AUTO</b> (padrao) — escolhe sozinho olhando o FPS: &lt;30 = BAIXO, &lt;55 = MEDIO,
 *       senao ALTO. Reavalia a cada 2 segundos, entao cai sozinho no lag e volta sozinho.</li>
 * </ul>
 *
 * <p>Nada disso mexe no servidor, na logica, nem em quem ve o efeito de perto: e so custo de
 * desenho. Regra de ouro do mod: qualquer erro aqui volta para ALTO.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class FxQualidade {

    public static final int AUTO = -1;
    public static final int BAIXO = 0;
    public static final int MEDIO = 1;
    public static final int ALTO = 2;

    /** o que o jogador escolheu (pode ser AUTO). */
    private static int preferida = AUTO;
    /** o que esta valendo agora (nunca AUTO). */
    private static int efetiva = ALTO;
    private static int amostra = 0;

    private FxQualidade() {}

    public static int preferida() { return preferida; }
    public static int efetiva() { return efetiva; }

    public static String nome(int q) {
        return switch (q) {
            case BAIXO -> "baixo";
            case MEDIO -> "medio";
            case ALTO -> "alto";
            default -> "auto";
        };
    }

    /** Le a escolha salva ("auto", "alto", "medio", "baixo"). Valor estranho = AUTO. */
    public static void carregar(String valor) {
        preferida = deNome(valor);
        efetiva = preferida == AUTO ? ALTO : preferida;
        amostra = 0;
    }

    public static int deNome(String valor) {
        if (valor == null) return AUTO;
        return switch (valor.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "baixo", "low" -> BAIXO;
            case "medio", "médio", "medium" -> MEDIO;
            case "alto", "high" -> ALTO;
            default -> AUTO;
        };
    }

    public static void definir(int q) {
        preferida = (q < AUTO || q > ALTO) ? AUTO : q;
        efetiva = preferida == AUTO ? efetiva : preferida;
        amostra = 0;
    }

    /** Chamado a cada 2 s: no AUTO, o FPS decide a qualidade. */
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        try {
            if (++amostra < 40) return;
            amostra = 0;
            if (preferida != AUTO) { efetiva = preferida; return; }
            int fps = Minecraft.getInstance().getFps();
            efetiva = fps < 30 ? BAIXO : (fps < 55 ? MEDIO : ALTO);
        } catch (Throwable erro) {
            efetiva = ALTO;   // nunca deixar o cliente sem efeito por causa de uma medicao
        }
    }

    // ------------------------------------------------------------------ regras

    /** De quantas em quantas direcoes a casca redesenha o modelo (1 = todas). */
    public static int passoDir() {
        return switch (efetiva) {
            case BAIXO -> 3;
            case MEDIO -> 2;
            default -> 1;
        };
    }

    /** Quantos aneis da casca desenhar (de `total`). */
    public static int aneis(int total) {
        return switch (efetiva) {
            case BAIXO -> Math.min(total, 2);
            case MEDIO -> Math.min(total, 3);
            default -> total;
        };
    }

    /** Ate que distancia (blocos) vale a pena desenhar o efeito. */
    public static double distancia() {
        return switch (efetiva) {
            case BAIXO -> 28.0;
            case MEDIO -> 36.0;
            default -> 48.0;
        };
    }

    /** Este jogador/entidade esta longe demais para gastar desenho? */
    public static boolean longe(Entity e) {
        try {
            double d = distancia();
            return Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(e) > d * d;
        } catch (Throwable erro) {
            return false;
        }
    }

    /** Quantas particulas por tick (de `n`). */
    public static int particulas(int n) {
        return switch (efetiva) {
            case BAIXO -> 1;
            case MEDIO -> Math.max(1, n / 2);
            default -> n;
        };
    }

    /** Numero de segmentos/aneis de geometria (de `n`). */
    public static int segmentos(int n) {
        return switch (efetiva) {
            case BAIXO -> Math.max(6, n / 2);
            case MEDIO -> Math.max(8, (n * 3) / 4);
            default -> n;
        };
    }
}
