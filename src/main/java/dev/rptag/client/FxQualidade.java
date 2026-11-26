package dev.rptag.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * (5.77.0 / 5.78.16) OTIMIZACAO — a QUALIDADE dos efeitos do mod, medida pelo FPS do cliente.
 *
 * <p>O problema: as cascas de aura redesenham o modelo do jogador dezenas de vezes por quadro
 * (e com Custom Player Models — {@code cpm} — cada desenho multiplica centenas de cubos!), alem
 * de aneis, tentaculos, chamas, glifos e poderes no mundo. Num servidor cheio isso derruba o
 * FPS na hora em que o player vira a camera para olhar os efeitos.
 *
 * <ul>
 *   <li><b>DESLIGADO</b> ({@code off}) — zera cascas, auras passivas, tentaculos, chamas e
 *       particulas do mod; poderes de combate desenham so o traco minimo de mira/aviso.</li>
 *   <li><b>MINIMO</b> — super leve (sem casca se tiver CPM, 1 anel/2 direcoes sem CPM, efeitos
 *       curtos de 16 blocos, geometria minima e zero particulas).</li>
 *   <li><b>BAIXO</b> — leve (casca enxuta, geometria reduzida pela metade, 24 blocos).</li>
 *   <li><b>MEDIO</b> — equilibrado (padrao inicial do AUTO: bonito de perto sem exagerar, 34 blocos).</li>
 *   <li><b>ALTO</b> — tudo completo (ate 48 blocos de distancia).</li>
 *   <li><b>AUTO</b> (padrao) — comeca em <b>MEDIO</b> (nunca leva pancada de ALTO ao virar a
 *       camera!), reage rapido quando o FPS cai (a cada 0,5 s) e usa histerese para subir devagar
 *       sem ficar piscando.</li>
 * </ul>
 *
 * <p>Nada disso mexe no servidor nem na logica de dano/alcance: e 100% custo de desenho no PC
 * de cada jogador. Regra de ouro do mod: qualquer erro aqui cai para MEDIO, nunca trava o jogo.
 */
@EventBusSubscriber(modid = dev.rptag.RPTagMod.MODID, value = Dist.CLIENT)
public final class FxQualidade {

    public static final int DESLIGADO = -3;
    public static final int MINIMO = -2;
    public static final int AUTO = -1;
    public static final int BAIXO = 0;
    public static final int MEDIO = 1;
    public static final int ALTO = 2;

    /** o que o jogador escolheu (pode ser AUTO). */
    private static int preferida = AUTO;
    /** o que esta valendo agora (nunca AUTO) — comeca em MEDIO para ser leve por padrao! */
    private static int efetiva = MEDIO;
    private static int amostra = 0;
    private static int ticksBons = 0;
    private static Boolean cpmInstalado = null;
    private static String ultimaToml = "";

    private FxQualidade() {}

    public static int preferida() { return preferida; }
    public static int efetiva() { return efetiva; }

    public static boolean desligado() { return efetiva == DESLIGADO; }
    public static boolean minimoOuMenos() { return efetiva <= MINIMO; }
    public static boolean baixoOuMenos() { return efetiva <= BAIXO; }

    /** Detecta 1x se o Custom Player Models (cpm) esta instalado — modelos CPM sao muito mais pesados. */
    public static boolean temCpm() {
        Boolean b = cpmInstalado;
        if (b != null) return b;
        try {
            b = net.neoforged.fml.ModList.get() != null && net.neoforged.fml.ModList.get().isLoaded("cpm");
        } catch (Throwable t) {
            b = false;
        }
        cpmInstalado = b;
        return b;
    }

    public static String nome(int q) {
        return switch (q) {
            case DESLIGADO -> "desligado";
            case MINIMO -> "minimo";
            case BAIXO -> "baixo";
            case MEDIO -> "medio";
            case ALTO -> "alto";
            default -> "auto";
        };
    }

    /** Le a escolha salva ("auto", "alto", "medio", "baixo", "minimo", "desligado"). Valor estranho = AUTO. */
    public static void carregar(String valor) {
        preferida = deNome(valor);
        efetiva = preferida == AUTO ? MEDIO : preferida;
        amostra = 0;
        ticksBons = 0;
    }

    public static int deNome(String valor) {
        if (valor == null) return AUTO;
        return switch (valor.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "desligado", "off", "nenhum", "none", "zero" -> DESLIGADO;
            case "minimo", "mínimo", "muito_baixo", "muitobaixo", "min", "batata" -> MINIMO;
            case "baixo", "low" -> BAIXO;
            case "medio", "médio", "medium" -> MEDIO;
            case "alto", "high" -> ALTO;
            default -> AUTO;
        };
    }

    public static void definir(int q) {
        if (q != DESLIGADO && q != MINIMO && q != AUTO && q != BAIXO && q != MEDIO && q != ALTO) {
            q = AUTO;
        }
        preferida = q;
        efetiva = preferida == AUTO ? MEDIO : preferida;
        amostra = 0;
        ticksBons = 0;
    }

    /**
     * (5.78.16) Resposta rapida ao lag + sincronia com rptag-client.toml:
     * checa o FPS a cada 10 ticks (0,5 s) para derrubar a qualidade NA HORA em que trava ao olhar,
     * mas exige ~2 s de FPS estavel antes de subir um degrau (evita piscar quando vira a camera).
     * Com CPM instalado, o modo AUTO limita o teto em MEDIO (nunca sobe sozinho para ALTO).
     */
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        try {
            FxComando.garantirConfig();
            sincronizarToml();
            // Mantem compatibilidade com a checagem classica (amostra < 40 para subir de nivel),
            // mas reage em 10 ticks (0,5 s) se o FPS despencar!
            amostra++;
            if (preferida != AUTO) {
                efetiva = preferida;
                if (amostra >= 40) amostra = 0;
                return;
            }
            if (amostra % 10 != 0) return;

            int fps = Minecraft.getInstance().getFps();
            if (fps <= 0) return;

            int alvo;
            if (fps < 28) alvo = MINIMO;
            else if (fps < 45) alvo = BAIXO;
            else if (fps < 75 || temCpm()) alvo = MEDIO;
            else alvo = ALTO;

            if (alvo < efetiva) {
                // Queda de FPS: desce IMEDIATAMENTE para segurar o travamento!
                efetiva = alvo;
                ticksBons = 0;
                amostra = 0;
            } else if (alvo > efetiva && amostra < 40) {
                // Espera completar a janela completa para subir (sem piscar)
                ticksBons += 10;
            } else if (alvo > efetiva) {
                ticksBons += 10;
                if (ticksBons >= 40 && fps >= (efetiva == MINIMO ? 36 : (efetiva == BAIXO ? 55 : 85))) {
                    efetiva = efetiva == MINIMO ? BAIXO : (efetiva == BAIXO ? MEDIO : ALTO);
                    ticksBons = 0;
                }
                amostra = 0;
            } else if (amostra >= 40) {
                amostra = 0;
                ticksBons = 0;
            }
        } catch (Throwable erro) {
            efetiva = MEDIO;   // nunca deixar travar no ALTO se algo falhar
        }
    }

    private static void sincronizarToml() {
        try {
            String cfg = dev.rptag.RpConfig.qualidadeEfeitos();
            if (cfg != null && !cfg.isBlank() && !cfg.equals(ultimaToml)) {
                String antes = ultimaToml;
                ultimaToml = cfg;
                // Se o dono do modpack/jogador editou o rptag-client.toml para algo explicito
                // diferente do padrao "auto", ou na primeira leitura quando cliente.properties esta em auto:
                if (!antes.isEmpty() || preferida == AUTO) {
                    int q = deNome(cfg);
                    if (q != preferida) definir(q);
                }
            }
        } catch (Throwable ignorado) {
        }
    }

    // ------------------------------------------------------------------ regras

    /** De quantas em quantas direcoes a casca redesenha o modelo (1 = todas; >10 = nenhuma). */
    public static int passoDir() {
        boolean cpm = temCpm();
        return switch (efetiva) {
            case DESLIGADO -> 99;
            case MINIMO -> cpm ? 99 : 5;
            case BAIXO -> cpm ? 5 : 4;
            case MEDIO -> cpm ? 3 : 2;
            default -> cpm ? 2 : 1;
        };
    }

    /** Quantos aneis da casca desenhar (de `total`). */
    public static int aneis(int total) {
        boolean cpm = temCpm();
        return switch (efetiva) {
            case DESLIGADO -> 0;
            case MINIMO -> cpm ? 0 : Math.min(total, 1);
            case BAIXO -> Math.min(total, cpm ? 1 : 2);
            case MEDIO -> Math.min(total, cpm ? 2 : 3);
            default -> cpm ? Math.min(total, 3) : total;
        };
    }

    /** Ate que distancia (blocos) vale a pena desenhar auras/efeitos passivos. */
    public static double distancia() {
        return switch (efetiva) {
            case DESLIGADO -> 0.0;
            case MINIMO -> 16.0;
            case BAIXO -> 24.0;
            case MEDIO -> 34.0;
            default -> 48.0;
        };
    }

    /** Alcance para habilidades/poderes ativos no mundo (maior que passivos, para ver ataques). */
    public static double distanciaPoder(double base) {
        return switch (efetiva) {
            case DESLIGADO -> Math.min(base, 20.0);
            case MINIMO -> Math.min(base, 28.0);
            case BAIXO -> Math.min(base, 40.0);
            case MEDIO -> Math.min(base, 56.0);
            default -> base;
        };
    }

    /** Este jogador/entidade esta longe demais para gastar desenho? */
    public static boolean longe(Entity e) {
        if (efetiva == DESLIGADO) return true;
        try {
            double d = distancia();
            return Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(e) > d * d;
        } catch (Throwable erro) {
            return false;
        }
    }

    /** Este ponto no mundo esta longe demais da camera para efeitos passivos? */
    public static boolean longe(Vec3 cam, double x, double y, double z, double limiteBase) {
        if (efetiva == DESLIGADO) return true;
        double d = Math.min(limiteBase, distancia() * 1.25);
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        return dx * dx + dy * dy + dz * dz > d * d;
    }

    /** Este ataque/poder no mundo esta longe demais da camera? */
    public static boolean longePoder(Vec3 cam, Vec3 pos, double limiteBase) {
        double d = distanciaPoder(limiteBase);
        return pos.distanceToSqr(cam) > d * d;
    }

    /** Quantas particulas por tick (de `n`). */
    public static int particulas(int n) {
        return switch (efetiva) {
            case DESLIGADO, MINIMO -> 0;
            case BAIXO -> n >= 3 ? 1 : 0;
            case MEDIO -> Math.max(1, n / 2);
            default -> n;
        };
    }

    /** Numero de segmentos/aneis de geometria circular (de `n`). */
    public static int segmentos(int n) {
        return switch (efetiva) {
            case DESLIGADO -> Math.min(n, 6);
            case MINIMO -> Math.max(6, n / 4);
            case BAIXO -> Math.max(8, n / 3);
            case MEDIO -> Math.max(10, (n * 3) / 5);
            default -> n;
        };
    }

    /** Escala de quantidade de elementos secundarios (tentaculos, chamas, penas, estrelas, faixas). */
    public static int escala(int n, int minimo) {
        if (efetiva == DESLIGADO) return 0;
        return switch (efetiva) {
            case MINIMO -> Math.max(minimo, n / 4);
            case BAIXO -> Math.max(minimo, n / 3);
            case MEDIO -> Math.max(minimo, (n * 3) / 5);
            default -> n;
        };
    }
}
