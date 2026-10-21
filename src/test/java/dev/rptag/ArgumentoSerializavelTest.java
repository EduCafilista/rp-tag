package dev.rptag;

import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * (4.5.1) REGRESSÃO DO "Invalid player data".
 *
 * <p>O servidor, ao colocar um jogador no mundo, serializa a árvore de comandos e
 * chama {@code ArgumentTypeInfos.unpack(type)} para CADA argumento. Se o tipo é
 * custom e não tem {@code ArgumentTypeInfo} registrada, isso lança
 * {@code IllegalArgumentException} e o cliente cai com "Invalid player data".
 *
 * <p>Este teste reproduz exatamente aquele caminho: um argumento custom "cru" falha,
 * e o mesmo argumento com {@code registerByClass} passa e devolve um Template que
 * consegue instanciar o tipo (é o que o cliente faz do outro lado da rede).
 */
class ArgumentoSerializavelTest {

    /** Um argumento qualquer que ninguém registrou — o caso da 4.5.0. */
    private static final class SemRegistro implements com.mojang.brigadier.arguments.ArgumentType<String> {
        @Override
        public String parse(com.mojang.brigadier.StringReader reader) {
            return "";
        }
    }

    @Test
    void argumentoNaoRegistradoQuebraAoSerializar() {
        // é este IllegalArgumentException que virava "Invalid player data" no login
        assertThrows(IllegalArgumentException.class,
                () -> ArgumentTypeInfos.unpack(new SemRegistro()));
    }

    @Test
    void listaDeAlvosComInfoRegistradaSerializaEInstancia() {
        ArgumentTypeInfo<ListaAlvosArgument, ?> info =
                ArgumentTypeInfos.registerByClass(ListaAlvosArgument.class,
                        SingletonArgumentInfo.<ListaAlvosArgument>contextFree(ListaAlvosArgument::alvos));

        assertNotNull(info, "o registry precisa devolver a info do tipo");

        // caminho do ClientboundCommandsPacket: unpack(type) -> Template -> id no registry
        ArgumentTypeInfo.Template<ListaAlvosArgument> tpl = assertDoesNotThrow(
                () -> ArgumentTypeInfos.unpack(ListaAlvosArgument.alvos()));

        // caminho do CLIENTE: o Template reconstroi o argumento
        assertNotNull(tpl.instantiate(null), "contextFree nao pode precisar de CommandBuildContext");
        assertNotNull(tpl.type(), "o Template precisa saber qual info o gerou");
    }

    @Test
    void fallbackNaoDependeDoRegistro() {
        // disponivel() nunca pode lançar — no pior caso devolve false e o comando
        // cai para o word() do vanilla (o jogador continua entrando no mundo)
        assertDoesNotThrow(ListaAlvosArgument::disponivel);
    }
}
