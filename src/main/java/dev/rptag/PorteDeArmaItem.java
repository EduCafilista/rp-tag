package dev.rptag;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * (5.49.0) <b>PORTE DE ARMA — o contrato.</b>
 *
 * <p>Pedido do dono: <i>"faça um item chamado 'Porte de arma', apenas um contrato, qualquer coisa
 * do tipo"</i>.
 *
 * <p>Então é <b>só o documento</b>: uma licença de papel, sem mecânica nenhuma. Não atira, não
 * equipa, não gasta, não vira arma. Ele existe para o RP — é o papel que o personagem mostra
 * quando alguém pergunta se ele pode andar armado.
 *
 * <p>As decisões de propósito:
 * <ul>
 *   <li><b>empilha 1</b>: é um documento assinado, não um pacote de folhas;</li>
 *   <li><b>não tem uso</b> (clique direito não faz nada): não existe regra nenhuma aqui para
 *       quebrar depois — quem manda no RP é o servidor/ADM;</li>
 *   <li><b>o tooltip lê o nome do item</b>: renomear na bigorna (com o nome do portador) já muda
 *       o que está escrito embaixo — dá para assinar o contrato sem inventar mecânica nova.</li>
 * </ul>
 *
 * <p>Regra de ouro do mod: item de RP não pode derrubar o jogo, e este não mexe em nada além do
 * próprio texto.
 */
public class PorteDeArmaItem extends Item {

    public PorteDeArmaItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack pilha, TooltipContext ctx, List<Component> linhas,
            TooltipFlag flag) {
        linhas.add(Component.literal("Autoriza o porte de arma em nome do portador.")
                .withStyle(ChatFormatting.GRAY));
        linhas.add(Component.literal("Documento de roleplay — não é uma arma.")
                .withStyle(ChatFormatting.DARK_GRAY));

        // (5.49.0) se o documento foi RENOMEADO na bigorna, o começo do nome vira o "portador".
        String nome = pilha.getHoverName().getString();
        String padrao = Component.translatable("item.rptag.porte_de_arma").getString();
        if (!nome.equalsIgnoreCase(padrao) && !nome.isBlank()) {
            linhas.add(Component.literal("Portador: " + nome)
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
