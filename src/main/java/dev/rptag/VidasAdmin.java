package dev.rptag;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * (4.5.0) VIDAS DO ADMIN — agora vale também para quem está OFFLINE.
 *
 * <p>Na 4.4.0 o {@code /rp admin vida} usava {@code EntityArgument.player()}, que só
 * enxerga quem está conectado. Aqui a resolução é por NOME, com dois caminhos:
 * <ol>
 *   <li>jogador online → o próprio {@link ServerPlayer} (recebe FX, som e mensagem na hora);</li>
 *   <li>jogador offline → {@code GameProfile} do cache de usuários do servidor
 *       ({@code usercache.json}), usado só pra descobrir o UUID.</li>
 * </ol>
 *
 * <p>O estoque de vidas já mora no {@link RPWorldData} indexado por UUID, então mexer
 * na vida de quem está offline é o MESMO {@code setCanonLives} — não precisa de
 * arquivo paralelo (o in-rp usa JSON porque não tem SavedData; nós temos). O que
 * falta é só avisar a pessoa quando ela voltar: fica um <b>recado</b> pendente no
 * world data, entregue no login.
 *
 * <p>(4.9.0) O lote ({@code /rp admin vida lote A,B,C tirar 1}) aplica NA HORA:
 * o {@code /rp admin confirmar} e o {@code /rp admin cancelar} saíram, então
 * não existe mais passo intermediário. {@link Alvo} continua resolvendo
 * nome/seletor (online ou pelo cache de perfis).
 */
public final class VidasAdmin {

    private static final SimpleCommandExceptionType NAO_ACHEI =
            new SimpleCommandExceptionType(Component.literal("Jogador nao encontrado (nunca entrou neste servidor?)"));
    /** (4.5.0) erros do /rp admin vida — usados pelos resolvers do comando. */
    public static CommandSyntaxException naoAchei() {
        return NAO_ACHEI.create();
    }

    public static CommandSyntaxException seletorInvalido(String s) {
        return new SimpleCommandExceptionType(Component.literal(
                "Seletor nao suportado aqui: " + s + " (use @a, @p ou @s)")).create();
    }

    public static CommandSyntaxException soJogador() {
        return new SimpleCommandExceptionType(Component.literal(
                "Este seletor precisa de um jogador executando o comando.")).create();
    }

    private VidasAdmin() {
    }

    // ============================================================ alvo

    /**
     * Um alvo de vida: sempre tem UUID e nome; {@code online} é null quando a pessoa
     * está fora do servidor.
     *
     * <p>{@code getUUID()} existe com esse nome de propósito — o código de vida já
     * usava {@code alvo.getUUID()} quando o alvo era {@link ServerPlayer}.
     */
    public record Alvo(UUID id, String nome, ServerPlayer online) {
        public UUID getUUID() {
            return id;
        }

        public boolean isOnline() {
            return online != null;
        }
    }

    /** Resolve por nome: primeiro online, depois o cache de perfis (offline). */
    public static Alvo resolver(CommandSourceStack src, String nome) throws CommandSyntaxException {
        ServerPlayer online = src.getServer().getPlayerList().getPlayerByName(nome);
        if (online != null) {
            return new Alvo(online.getUUID(), online.getName().getString(), online);
        }
        GameProfile perfil = src.getServer().getProfileCache().get(nome).orElse(null);
        if (perfil == null || perfil.getId() == null) {
            throw NAO_ACHEI.create();
        }
        return new Alvo(perfil.getId(), perfil.getName() != null ? perfil.getName() : nome, null);
    }

    /** Nome de um UUID qualquer (online ou no cache); devolve o UUID cru se não achar. */
    public static String nomeDe(net.minecraft.server.MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getName().getString();
        }
        return server.getProfileCache().get(id).map(GameProfile::getName).orElse(id.toString().substring(0, 8));
    }

    // ============================================================ lote

    /**
     * (4.5.0) Divide a lista do lote ("Ana,Beto,Caio") em nomes limpos, na ordem.
     * Vazio/so espaco e virgula dupla sao descartados; nome repetido conta uma vez.
     */
    public static List<String> dividirNomes(String cru) {
        List<String> out = new ArrayList<>();
        if (cru == null) return out;
        for (String pedaco : cru.split(",")) {
            String nome = pedaco.trim();
            if (nome.isEmpty()) continue;
            if (!out.contains(nome)) out.add(nome);
        }
        return out;
    }
}
