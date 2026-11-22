package dev.rptag;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

/**
 * (5.78.9) PONTE DE COMPATIBILIDADE COM O MOD <b>Hidden Names</b> ({@code hiddennames}, pitbox46).
 *
 * <p>Pedido do dono: <i>"esse mod, ele mexe seta o nome dos players, mas preciso que mesmo
 * setando o nome ainda apareça o titulo"</i>.
 *
 * <h2>O que o Hidden Names faz por dentro</h2>
 * <ol>
 *   <li>No nametag acima da cabeça, {@code ClientEvents.onRenderNameplate} roda em prioridade
 *       {@code NORMAL} e faz {@code event.setContent(HiddenNames.getFullNameplate(...))} —
 *       <b>apagando</b> o título que o {@code NameplateRenderer} tinha acabado de colocar.</li>
 *   <li>No {@code Player.getDisplayName()}, o {@code PlayerMixin} dele substitui o argumento de
 *       {@code PlayerTeam.formatNameForTeam} por {@code NameData.getDisplayName()} — jogando fora
 *       tudo o que saiu do {@code PlayerEvent.NameFormat} (inclusive o título!).</li>
 *   <li>Na lista do TAB, o {@code PlayerTabOverlayMixin} faz {@code @Overwrite} em
 *       {@code getNameForDisplay(PlayerInfo)} e <b>ignora</b> {@code getTabListDisplayName()}
 *       sempre que o jogador está em {@code NameData.DATA} (que é SEMPRE: ele insere todo mundo no
 *       login!).</li>
 *   <li>No nosso chat RP ({@code RPChat}), nós líamos só {@code persona} ou {@code player.getName()},
 *       sem saber que o admin trocou o nome pelo {@code /hiddennames setName}.</li>
 * </ol>
 *
 * <p>Tudo via reflexão e {@link ModList#isLoaded}: se o {@code hiddennames} não estiver instalado,
 * esta classe não faz nada e não exige o jar dele para compilar nem para rodar.
 */
public final class HiddenNamesCompat {

    public static final String MODID = "hiddennames";

    private static Boolean instalado;
    private static volatile boolean refletido;
    private static Map<?, ?> mapaDados;
    private static Method metodoGetDisplayName;

    private HiddenNamesCompat() {
    }

    /** @return true se o mod {@code hiddennames} está carregado neste jogo/servidor. */
    public static boolean ativo() {
        if (instalado != null) {
            return instalado;
        }
        try {
            ModList l = ModList.get();
            instalado = l != null && l.isLoaded(MODID);
        } catch (Throwable t) {
            instalado = false;
        }
        return instalado;
    }

    private static void prepararReflexao() {
        if (refletido) {
            return;
        }
        refletido = true;
        try {
            Class<?> cls = Class.forName("github.pitbox46.hiddennames.data.NameData");
            Field f = cls.getField("DATA");
            Object obj = f.get(null);
            if (obj instanceof Map<?, ?> m) {
                mapaDados = m;
            }
            metodoGetDisplayName = cls.getMethod("getDisplayName");
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui refletir NameData do HiddenNames", t);
        }
    }

    /**
     * Devolve o {@link Component} que o Hidden Names guardou para {@code id} (via
     * {@code /hiddennames setName}), ou {@code null} se não houver / mod ausente.
     *
     * <p>Quando o jogador entra pela primeira vez sem {@code setName}, o Hidden Names grava o
     * próprio {@code unmodifiedDisplayName} dele (que já traz nossa tag {@code ● ʀᴘ} / título).
     * Se devolvermos isso cru no chat, duplicaria título/tag — então limpamos o que já for nosso
     * em {@link #nomeLimpo(UUID, String)}.
     */
    public static Component componente(UUID id) {
        if (id == null || !ativo()) {
            return null;
        }
        try {
            prepararReflexao();
            if (mapaDados == null || metodoGetDisplayName == null) {
                return null;
            }
            Object nd = mapaDados.get(id);
            if (nd == null) {
                return null;
            }
            Object comp = metodoGetDisplayName.invoke(nd);
            return comp instanceof Component c ? c : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Devolve o nome escolhido no Hidden Names pronto para entrar no meio de
     * {@code [Etiqueta] <nome> [Pessoal] ● ʀᴘ}, preservando a cor/estilo se foi setado por
     * {@code /hiddennames setName}. Se o que está lá é apenas o espelho automático do login
     * (igual ao nick padrão com nossa tag/título), devolve {@code null} para o fluxo normal agir.
     */
    public static Component nomeCustomizado(UUID id, String nickReal) {
        Component c = componente(id);
        if (c == null) {
            return null;
        }
        String s = c.getString();
        if (s == null || s.isBlank()) {
            return null;
        }
        // se o NameData foi criado sozinho no login sem /hiddennames setName, ele contém a nossa
        // tag (● ʀᴘ / ○ ᴏꜰꜰ ʀᴘ) ou é idêntico ao nick sem estilo próprio — não é nome customizado
        if (RPTags.hasTag(c)) {
            return null;
        }
        if (s.equals(nickReal) && c.getStyle().isEmpty() && c.getSiblings().isEmpty()) {
            return null;
        }
        return c.copy();
    }

    /** Verifica se {@code texto} já contém a etiqueta {@code [Etiqueta]} ou o pessoal {@code [Pessoal]}. */
    public static boolean jaTemTitulo(Component c, String etiqueta, String personalizado) {
        if (c == null) {
            return false;
        }
        String s = c.getString();
        if (etiqueta != null && !etiqueta.isEmpty() && s.contains("[" + etiqueta + "]")) {
            return true;
        }
        if (personalizado != null && !personalizado.isEmpty() && s.contains("[" + personalizado + "]")) {
            return true;
        }
        return false;
    }
}
