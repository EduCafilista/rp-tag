package dev.rptag;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.commands.synchronization.ArgumentTypeInfos;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * (4.5.0) Argumento do {@code /rp admin vida}: aceita um NOME, um seletor
 * ({@code @a}, {@code @p}, {@code @s}) ou uma LISTA com vírgula ({@code Ana,Beto}).
 *
 * <p>Por que não {@code StringArgumentType.word()}: o {@code word} do brigadier só
 * aceita {@code [A-Za-z0-9_.+-]} — <b>vírgula não entra</b>, e o comando morria com
 * "Expected whitespace to end one argument". Aqui a leitura é manual: come até o
 * primeiro espaço e aceita qualquer caractere no meio (nome de jogador não tem espaço).
 *
 * <h2>⚠ (4.5.1) ARGUMENTO CUSTOM PRECISA DE "INFO" REGISTRADA</h2>
 * <p>Na 4.5.0 este argumento existia sem {@code ArgumentTypeInfo}. O servidor
 * funcionava sozinho, mas <b>ninguém conseguia entrar no mundo</b>: ao colocar o
 * jogador, o {@code ClientboundCommandsPacket} tenta serializar a árvore de comandos
 * e chama {@code ArgumentTypeInfos.byClass(...)}, que só conhece tipos registrados —
 * estourava {@code IllegalArgumentException: Unrecognized argument type
 * dev.rptag.ListaAlvosArgument} e o cliente caía com <b>"Invalid player data"</b>.
 *
 * <p>O registro certo (mesmo padrão que o próprio NeoForge usa no
 * {@code ModIdArgument}) é um {@link DeferredRegister} no registry
 * {@code Registries.COMMAND_ARGUMENT_TYPE}, cujo valor é
 * {@code ArgumentTypeInfos.registerByClass(classe, SingletonArgumentInfo.contextFree(...))}.
 * O registry é sincronizado entre servidor e cliente, então o cliente consegue
 * reconstruir o tipo e desenhar o autocomplete.
 */
public final class ListaAlvosArgument implements ArgumentType<String> {

    private static final SimpleCommandExceptionType VAZIO =
            new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal(
                    "Diga pelo menos um nome (ou @a)."));

    private ListaAlvosArgument() {
    }

    public static ListaAlvosArgument alvos() {
        return new ListaAlvosArgument();
    }

    public static String get(com.mojang.brigadier.context.CommandContext<?> ctx, String nome) {
        return ctx.getArgument(nome, String.class);
    }

    /**
     * (4.5.1) O tipo está utilizável AGORA?
     *
     * <p>Rede de segurança: se o registro falhar (outro mod mexeu, ordem de
     * carregamento, versão trocada), {@code byClass} lança. Nesse caso o comando
     * desiste do argumento custom e usa o {@code word()} do vanilla — o admin perde
     * a lista com vírgula, mas <b>o servidor continua aceitando jogadores</b>.
     * Antes da 4.5.1 esse erro derrubava todo mundo no login.
     */
    public static boolean disponivel() {
        try {
            ArgumentTypeInfos.byClass(alvos());
            return true;
        } catch (RuntimeException e) {
            // NAO usar RPTagMod.LOGGER aqui: tocar na classe RPTagMod inicializa os
            // DeferredRegister dela, que dependem dos registries do jogo. Fora do
            // jogo (teste unitario) isso viraria ExceptionInInitializerError — e a
            // rede de seguranca tem que ser mais segura que o problema que ela cobre.
            try {
                com.mojang.logging.LogUtils.getLogger().error("RP Tag: ListaAlvosArgument fora do registry — "
                        + "usando o argumento padrao (sem lista por virgula): " + e);
            } catch (Throwable ignored) {
                // sem logger disponivel: seguir em silencio e usar o argumento do vanilla
            }
            return false;
        }
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int inicio = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ') {
            reader.skip();
        }
        String lido = reader.getString().substring(inicio, reader.getCursor());
        if (lido.isBlank()) throw VAZIO.create();
        return lido;
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("Ana", "Ana,Beto,Caio", "@a");
    }

    /** (4.5.0) quebra em nomes limpos — exposto pra teste. */
    public static List<String> nomes(String cru) {
        if (cru == null) return List.of();
        return Arrays.stream(cru.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
