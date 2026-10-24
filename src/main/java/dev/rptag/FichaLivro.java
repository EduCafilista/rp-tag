package dev.rptag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** (4.8.0) a ficha escrita no chat — o mesmo conteúdo do tablet, sem abrir a tela. */
public final class FichaLivro {

    private FichaLivro() {
    }

    public static void mostrar(ServerPlayer sp, String dono, FichaRp f) {
        for (Component c : linhas(dono, f)) {
            sp.sendSystemMessage(c);
        }
    }

    public static List<Component> linhas(String dono, FichaRp f) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal("◈ ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal("FICHA").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                .append(Component.literal(" de ").withStyle(ChatFormatting.DARK_AQUA))
                .append(Component.literal(f.ou(f.nome)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" ◈").withStyle(ChatFormatting.DARK_AQUA)));

        linha(out, "Nome", f.ou(f.nome), ChatFormatting.WHITE);
        linha(out, "Idade", f.ou(f.idade), ChatFormatting.WHITE);
        linha(out, "Veio de", f.ou(f.origem), ChatFormatting.WHITE);
        linha(out, "Ocupação", f.ou(f.oficio), ChatFormatting.WHITE);
        linha(out, "Habilidades", f.ou(f.habilidades), ChatFormatting.WHITE);
        linha(out, "História", f.ou(f.historia), ChatFormatting.WHITE);
        linha(out, "Personalidade", f.personaResumo(), ChatFormatting.LIGHT_PURPLE);

        out.add(Component.literal("◈ conta: ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(dono).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(f.concluida ? " · ficha confirmada" : " · em criação")
                        .withStyle(f.concluida ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                .append(Component.literal(" ◈").withStyle(ChatFormatting.DARK_AQUA)));
        return out;
    }

    private static void linha(List<Component> out, String rotulo, String valor, ChatFormatting cor) {
        out.add(Component.literal("│ ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(rotulo + ": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(valor).withStyle(cor)));
    }
}
