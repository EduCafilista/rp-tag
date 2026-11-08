package dev.rptag;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * (5.25.0) ATRIBUTOS do mod.
 *
 * <h2>VAMPIRO ({@code rptag:vampiro})</h2>
 *
 * <p>Pedido do dono: <i>"quero criar um atributo, que se eu atribuir a algum player ele toma dano
 * ao sol, como se fosse um vampiro, e ter algum item que se ele usar ou estiver na mao, para ele
 * evitar tomar dano do sol"</i>.
 *
 * <p>É um <b>atributo de verdade</b> do jogo (não uma etiqueta escondida no save): entra no
 * registro oficial, é <b>sincronizado</b> com o cliente e existe na ficha do jogador. Então dá pra
 * mexer nele de três jeitos:
 *
 * <ul>
 *   <li><b>pelo comando do mod</b> — {@code /rp admin vampiro <jogadores> ligar|desligar};</li>
 *   <li><b>pelo comando do Minecraft</b> — {@code /attribute <jogador> rptag:vampiro base set 1}
 *       (ou um modificador, se o ADM quiser tirar/voltar sem zerar);</li>
 *   <li><b>pelo NBT/ datapack</b>, como qualquer atributo.</li>
 * </ul>
 *
 * <p>O valor vai de <b>0 a 1</b>: 0 = pessoa normal, 1 = vampiro. Qualquer coisa acima de 0,5 já
 * conta como vampiro (o sistema lê com {@code > 0.5}), então {@code 0.2} não liga nada pela
 * metade — quem manda é o ligar/desligar.
 *
 * <p>Quem lê esse valor é o {@link VampiroSistema}: sol na pele, fogo, fumaça e o aviso no chat.
 */
public final class RPAttributes {

    private RPAttributes() {
    }

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, RPTagMod.MODID);

    /**
     * O VAMPIRO: 0 = normal · 1 = vampiro. {@code setSyncable(true)} porque o cliente também
     * precisa saber (o HUD mostra o ícone do sol queimando e a tela escurece no sol).
     */
    public static final DeferredHolder<Attribute, Attribute> VAMPIRO =
            ATTRIBUTES.register("vampiro", () -> new RangedAttribute(
                    "attribute.rptag.vampiro", 0.0D, 0.0D, 1.0D).setSyncable(true));
}
