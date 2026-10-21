package dev.rptag;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

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
