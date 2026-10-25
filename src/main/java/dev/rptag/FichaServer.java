package dev.rptag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * (4.8.0) Regras do TABLET no servidor: abrir, salvar, entregar, resetar.
 *
 * <p>Um detalhe importante: ao concluir, o nome e a idade da ficha viram a
 * {@link Persona} do jogador — então o nome do personagem passa a aparecer no
 * nametag, no chat e na TAB sem nenhum comando extra.
 */
public final class FichaServer {

    private FichaServer() {
    }

    /** abre o tablet (manda a ficha e o cliente abre a tela). */
    public static void abrir(ServerPlayer sp) {
        FichaRp f = RPWorldData.get(sp.server).getFicha(sp.getUUID());
        // (4.8.2) o DONO sempre pode editar — antes, ficha concluída reabria em
        // modo leitura e o botão "Confirmar Personagem" desaparecia.
        FichaOpenPayload.enviar(sp, f, sp.getGameProfile().getName(), true);
    }

    /** abre a ficha de OUTRA pessoa (só leitura). */
    public static void ver(ServerPlayer quem, ServerPlayer alvo) {
        FichaRp f = RPWorldData.get(quem.server).getFicha(alvo.getUUID());
        FichaOpenPayload.enviar(quem, f, alvo.getGameProfile().getName(), false);
    }

    /** grava o que o cliente mandou. */
    public static void salvar(ServerPlayer sp, FichaRp nova, boolean concluir) {
        RPWorldData wd = RPWorldData.get(sp.server);
        FichaRp atual = wd.getFicha(sp.getUUID());

        atual.nome = corta(nova.nome, FichaCodec.NOME);
        atual.idade = corta(nova.idade, FichaCodec.IDADE);
        atual.origem = corta(nova.origem, FichaCodec.ORIGEM);
        atual.oficio = corta(nova.oficio, FichaCodec.OFICIO);
        atual.habilidades = corta(nova.habilidades, FichaCodec.HABILIDADES);
        atual.historia = corta(nova.historia, FichaCodec.HISTORIA);
        for (int i = 0; i < 6; i++) {
            atual.persona[i] = Math.max(0, Math.min(100, nova.persona[i]));
        }
        atual.pagina = Math.max(0, Math.min(FichaRp.CAP_RESUMO, nova.pagina));

        boolean primeiraVez = concluir && !atual.concluida && !atual.vazia();
        if (concluir && !atual.vazia()) {
            atual.concluida = true;
        }
        wd.setFicha(sp.getUUID(), atual);

        // o personagem "veste" a ficha: nome/idade/descricao viram Persona
        if (!atual.vazia()) {
            ServerEvents.setPersona(sp, new Persona(atual.nome, atual.idadeNumero(), atual.descricaoCurta()));
            if (!ServerEvents.isInRp(sp)) {
                ServerEvents.setState(sp, true, false);
            }
        }

        if (primeiraVez) {
            sp.sendSystemMessage(Component.literal("\u2726 Ficha registrada. Bem-vindo à história, ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(atual.nome).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(".").withStyle(ChatFormatting.AQUA)));
            sp.server.getPlayerList().broadcastSystemMessage(
                    Component.literal("\u2726 ").withStyle(ChatFormatting.DARK_PURPLE)
                            .append(Component.literal(atual.nome).withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(" escreveu a própria ficha.").withStyle(ChatFormatting.GRAY)),
                    false);
            sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.8f, 1.25f);
            RPTagMod.LOGGER.info("RP Tag: {} concluiu a ficha ({})", sp.getGameProfile().getName(), atual.nome);
        }
    }

    /** (4.8.0) recebe o que o cliente mandou (o pacote passa por aqui). */
    public static void receberSalvar(FichaSavePayload payload,
            net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                salvar(sp, payload.ficha(), payload.concluir());
            }
        });
    }

    /** dá o tablet na primeira vez que o jogador entra (sem duplicar). */
    public static void garantirTablet(ServerPlayer sp) {
        if (jaTemTablet(sp)) {
            return;
        }
        dar(sp, true);
    }

    public static boolean jaTemTablet(ServerPlayer sp) {
        for (ItemStack st : sp.getInventory().items) {
            if (st.is(RPTagMod.TABLET.get())) {
                return true;
            }
        }
        if (sp.getOffhandItem().is(RPTagMod.TABLET.get())) {
            return true;
        }
        return false;
    }

    /** coloca o tablet no inventário (cai no chão se não couber). */
    public static void dar(ServerPlayer sp, boolean avisar) {
        ItemStack st = new ItemStack(RPTagMod.TABLET.get());
        if (!sp.getInventory().add(st)) {
            sp.drop(st, false);
        }
        sp.containerMenu.broadcastChanges();
        if (avisar) {
            sp.sendSystemMessage(Component.literal("\u25c8 ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("Você recebeu o ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("Tablet da Lore").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(". Clique com o botão direito para criar sua ficha.")
                            .withStyle(ChatFormatting.GRAY)));
        }
    }

    /** apaga a ficha (o jogador refaz do zero). O tablet continua com ele. */
    public static void resetar(ServerPlayer alvo) {
        RPWorldData wd = RPWorldData.get(alvo.server);
        wd.setFicha(alvo.getUUID(), new FichaRp());
        ServerEvents.setPersona(alvo, Persona.EMPTY);
        alvo.sendSystemMessage(Component.literal("\u2726 Sua ficha foi apagada — o tablet continua com você.")
                .withStyle(ChatFormatting.YELLOW));
    }

    /** (4.8.2) /tablet resetar e /ficha resetar: apaga e já abre a tela limpa. */
    public static void resetarEabrir(ServerPlayer alvo) {
        resetar(alvo);
        garantirTablet(alvo);
        abrir(alvo);
    }

    private static String corta(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
