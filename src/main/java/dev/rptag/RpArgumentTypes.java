package dev.rptag;

import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * (4.5.1) REGISTRO dos argumentos de comando do mod.
 *
 * <p>Existe separado do {@link ListaAlvosArgument} de propósito: o
 * {@code DeferredHolder} do NeoForge resolve o registry no momento em que é criado,
 * então manter isso num {@code <clinit>} da classe do argumento faria qualquer uso
 * dela (inclusive em teste unitário, sem jogo) explodir com
 * {@code ExceptionInInitializerError}. Aqui o registro só acontece quando o mod
 * inicializa, e a classe pura continua testável.
 *
 * <p><b>Por que isso conserta o "Invalid player data":</b> o servidor manda a árvore
 * de comandos pro cliente e, para cada argumento, chama
 * {@code ArgumentTypeInfos.byClass(type)}. Tipos customizados só são conhecidos
 * depois de {@code ArgumentTypeInfos.registerByClass(...)} — que é justamente o que
 * este registry faz (mesmo padrão do {@code ModIdArgument} do NeoForge).
 */
public final class RpArgumentTypes {

    private RpArgumentTypes() {
    }

    /** Registry {@code minecraft:command_argument_type} do mod. */
    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES =
            DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, RPTagMod.MODID);

    /**
     * {@code /rp admin vida <jogador>} — aceita nome, {@code @a/@p/@s} e lista com vírgula.
     * {@code contextFree} = não depende do {@code CommandBuildContext}, então o cliente
     * consegue reconstruir o tipo do outro lado da rede sem nada extra.
     */
    public static final DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<ListaAlvosArgument>> LISTA_ALVOS =
            ARGUMENT_TYPES.register("lista_alvos",
                    () -> ArgumentTypeInfos.registerByClass(ListaAlvosArgument.class,
                            SingletonArgumentInfo.<ListaAlvosArgument>contextFree(ListaAlvosArgument::alvos)));
}
