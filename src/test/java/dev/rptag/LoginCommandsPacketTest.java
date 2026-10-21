package dev.rptag;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.RootCommandNode;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.network.protocol.game.ClientboundCommandsPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * (4.5.1) O TESTE DO BUG — "Invalid player data".
 *
 * <p>Este é o caminho EXATO que o servidor percorre em
 * {@code PlayerList.placeNewPlayer → sendPlayerPermissionLevel → Commands.sendCommands}:
 * ele monta um {@link ClientboundCommandsPacket} com a árvore de comandos, e o
 * construtor percorre os nós chamando {@code ArgumentTypeInfos.unpack(type)} em cada
 * argumento.
 *
 * <p>Na 4.5.0 o {@code ListaAlvosArgument} não tinha info registrada: o construtor
 * lançava {@code IllegalArgumentException} e o jogador era expulso com
 * "Invalid player data". Aqui montamos o mesmo pacote com e sem registro.
 */
class LoginCommandsPacketTest {

    /** Árvore mínima igual à do mod: /rp admin vida &lt;jogador&gt; ver. */
    private static RootCommandNode<SharedSuggestionProvider> arvore(
            com.mojang.brigadier.arguments.ArgumentType<String> tipo) {
        CommandDispatcher<SharedSuggestionProvider> d = new CommandDispatcher<>();
        d.register(com.mojang.brigadier.builder.LiteralArgumentBuilder
                .<SharedSuggestionProvider>literal("rp")
                .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<SharedSuggestionProvider>literal("admin")
                        .then(com.mojang.brigadier.builder.LiteralArgumentBuilder.<SharedSuggestionProvider>literal("vida")
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                                        .<SharedSuggestionProvider, String>argument("jogador", tipo)
                                        .then(com.mojang.brigadier.builder.LiteralArgumentBuilder
                                                .<SharedSuggestionProvider>literal("ver"))))));
        return d.getRoot();
    }

    /** Tipo "cru", como o ListaAlvosArgument estava na 4.5.0 (nunca registrado). */
    private static final class TipoSemRegistro implements com.mojang.brigadier.arguments.ArgumentType<String> {
        @Override
        public String parse(com.mojang.brigadier.StringReader reader) {
            return "";
        }
    }

    @Test
    void tipoSemRegistroDerrubaOPacoteDoLogin() {
        // estado da 4.5.0: é ESTE IllegalArgumentException que virava "Invalid player data".
        // (usa um tipo dummy porque o registro do ArgumentTypeInfos é GLOBAL — o teste
        //  positivo abaixo registra o ListaAlvosArgument de verdade e o contamina.)
        RootCommandNode<SharedSuggestionProvider> raiz = arvore(new TipoSemRegistro());
        assertThrows(IllegalArgumentException.class, () -> new ClientboundCommandsPacket(raiz));
    }

    @Test
    void comRegistroOPacoteDoLoginEBuildado() {
        // o que o RpArgumentTypes faz no boot do mod
        ArgumentTypeInfos.registerByClass(ListaAlvosArgument.class,
                SingletonArgumentInfo.<ListaAlvosArgument>contextFree(ListaAlvosArgument::alvos));

        RootCommandNode<SharedSuggestionProvider> raiz = arvore(ListaAlvosArgument.alvos());
        assertDoesNotThrow(() -> new ClientboundCommandsPacket(raiz),
                "com a info registrada o jogador entra no mundo (nada de Invalid player data)");
    }
}
