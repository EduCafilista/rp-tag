package dev.rptag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * SISTEMA DE TÍTULOS — 5.7.2.
 *
 * <p>O dono mandou uma print (AETHERIS) em que cada alma aparece com uma
 * ETIQUETA colorida na frente do nome — {@code [Sagrado] Seraphiel},
 * {@code [Warden] Myra}, {@code [Gelo] Rihm}, {@code [Fragmento] Ayla},
 * {@code [Almas] Astrael} — e pediu: "títulos em QUALQUER player, tipo
 * Chronos, players, ou personalizado; faça como você achar melhor".
 *
 * <p>Como ficou:
 * <ul>
 *   <li><b>etiqueta</b> — uma palavra entre colchetes, com COR fixa por
 *       "classe" (paleta em {@link #ETIQUETAS}/{@link #CORES}). Pode ser
 *       qualquer texto, mas as da paleta já vêm coloridas de fábrica.</li>
 *   <li><b>título personalizado</b> — texto livre entre colchetes DEPOIS do
 *       nome, como o {@code Nephisto [O Ferreiro]} da print.</li>
 * </ul>
 *
 * <p>Onde aparece: TAB (lista de jogadores), mensagens do chat/servidor (via
 * {@code PlayerEvent.NameFormat}) e no NAMETAG acima da cabeça (o cliente
 * recebe os títulos por {@link TituloSyncPayload}).
 *
 * <p>Persistência: um {@link SavedData} próprio ({@code rptag_titulos}) — o
 * arquivo do mundo guarda tudo, então reiniciar o servidor não perde nada.
 */
public final class Titulos {

    private Titulos() {
    }

    // ------------------------------------------------------------------ paleta
    /** Etiquetas prontas (as da print + as que o dono citou: Chronos…). */
    public static final String[] ETIQUETAS = {
            "Sagrado", "Warden", "Gelo", "Fragmento", "Almas", "Chronos",
            "Sombra", "Divino", "Morte", "Vazio", "Ferreiro", "Player"
    };

    /** Cor (RGB, sem alfa) de cada etiqueta da paleta, na MESMA ordem. */
    public static final int[] CORES = {
            0xFFD75E, // Sagrado  — dourado
            0x6FB3FF, // Warden   — azul
            0x9BE8FF, // Gelo     — ciano
            0x38E0A6, // Fragmento— verde-menta
            0x2FE0C0, // Almas    — água
            0xC9A6FF, // Chronos  — lilás
            0x9A6BFF, // Sombra   — roxo
            0xFFE9A8, // Divino   — creme
            0xFF7B7B, // Morte    — vermelho claro
            0x8C8C9E, // Vazio    — cinza-azulado
            0xE0A860, // Ferreiro — bronze
            0xBFBFBF  // Player   — branco sujo
    };

    /** Cor usada quando a etiqueta não está na paleta. */
    public static final int COR_PADRAO = 0xD8D8D8;

    /** Teto de caracteres de cada campo (nada de título gigante quebrando a tela). */
    public static final int MAX_ETIQUETA = 16;
    public static final int MAX_PERSONALIZADO = 24;

    /** @return a cor de fábrica da etiqueta, ou {@link #COR_PADRAO}. */
    public static int corDe(String etiqueta) {
        if (etiqueta == null) {
            return COR_PADRAO;
        }
        for (int i = 0; i < ETIQUETAS.length; i++) {
            if (ETIQUETAS[i].equalsIgnoreCase(etiqueta)) {
                return CORES[i];
            }
        }
        return COR_PADRAO;
    }

    /** Lista "Sagrado, Warden, Gelo…" pra mensagem de ajuda do comando. */
    public static String paletaTexto() {
        return String.join(", ", ETIQUETAS);
    }

    // ------------------------------------------------------------------ dados
    /** O título de um jogador. Campos vazios = sem etiqueta / sem título extra. */
    public record Titulo(String etiqueta, int cor, String personalizado) {

        public static final Titulo VAZIO = new Titulo("", COR_PADRAO, "");

        public Titulo {
            etiqueta = limpar(etiqueta, MAX_ETIQUETA);
            personalizado = limpar(personalizado, MAX_PERSONALIZADO);
            if (cor == 0) {
                cor = corDe(etiqueta);
            }
        }

        public boolean vazio() {
            return etiqueta.isEmpty() && personalizado.isEmpty();
        }

        public net.minecraft.nbt.CompoundTag save() {
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
            t.putString("Etiqueta", etiqueta);
            t.putInt("Cor", cor);
            t.putString("Titulo", personalizado);
            return t;
        }

        public static Titulo load(net.minecraft.nbt.CompoundTag t) {
            return new Titulo(t.getString("Etiqueta"), t.getInt("Cor"), t.getString("Titulo"));
        }
    }

    /** Tira §, controla tamanho e caracteres de controle (ninguém escreve cor na mão). */
    private static String limpar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        String sem = texto.replaceAll("(?i)\u00a7[0-9a-fk-orx]", "").replace("\u00a7", "").trim();
        StringBuilder sb = new StringBuilder();
        sem.codePoints().filter(c -> !Character.isISOControl(c)).forEach(c -> {
            if (sb.length() + Character.charCount(c) <= max) {
                sb.appendCodePoint(c);
            }
        });
        return sb.toString();
    }

    /** Estado salvo no mundo: um título por jogador. */
    public static final class Dados extends SavedData {

        public static final String NOME = "rptag_titulos";

        private final Map<UUID, Titulo> titulos = new LinkedHashMap<>();

        public static Dados get(MinecraftServer server) {
            // (mesmo padrão do RPWorldData: factory precisa do DataFixTypes)
            return server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(Dados::new, Dados::load, null), NOME);
        }

        public static Dados load(net.minecraft.nbt.CompoundTag tag,
                net.minecraft.core.HolderLookup.Provider registries) {
            Dados d = new Dados();
            net.minecraft.nbt.ListTag lista = tag.getList("Titulos", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < lista.size(); i++) {
                net.minecraft.nbt.CompoundTag e = lista.getCompound(i);
                try {
                    Titulo t = Titulo.load(e.getCompound("T"));
                    if (!t.vazio()) {
                        d.titulos.put(e.getUUID("Id"), t);
                    }
                } catch (Throwable ignored) {
                    // entrada corrompida: ignora, o resto do save continua válido
                }
            }
            return d;
        }

        @Override
        public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag,
                net.minecraft.core.HolderLookup.Provider registries) {
            net.minecraft.nbt.ListTag lista = new net.minecraft.nbt.ListTag();
            titulos.forEach((id, t) -> {
                net.minecraft.nbt.CompoundTag e = new net.minecraft.nbt.CompoundTag();
                e.putUUID("Id", id);
                e.put("T", t.save());
                lista.add(e);
            });
            tag.put("Titulos", lista);
            return tag;
        }

        public Titulo de(UUID id) {
            return titulos.getOrDefault(id, Titulo.VAZIO);
        }

        public void definir(UUID id, Titulo t) {
            if (t == null || t.vazio()) {
                titulos.remove(id);
            } else {
                titulos.put(id, t);
            }
            setDirty();
        }

        public Map<UUID, Titulo> todos() {
            return java.util.Collections.unmodifiableMap(titulos);
        }

        public List<Map.Entry<UUID, Titulo>> lista() {
            return new ArrayList<>(titulos.entrySet());
        }
    }

    // ------------------------------------------------------------- utilidades
    public static Titulo de(MinecraftServer server, UUID id) {
        try {
            return Dados.get(server).de(id);
        } catch (Throwable t) {
            return Titulo.VAZIO;
        }
    }

    /** Define (ou limpa, se vazio) o título e avisa os clientes. */
    public static void definir(MinecraftServer server, UUID id, Titulo t) {
        Dados.get(server).definir(id, t);
        avisar(server, id);
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            p.refreshTabListName();
            p.refreshDisplayName();
        }
    }

    /** Manda o título ATUAL desse jogador pra todo mundo (o TAB já vem do servidor). */
    public static void avisar(MinecraftServer server, UUID id) {
        try {
            Titulo t = Dados.get(server).de(id);
            TituloSyncPayload pacote = new TituloSyncPayload(id, t.etiqueta(), t.cor(), t.personalizado());
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(p, pacote);
            }
        } catch (Throwable erro) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui avisar os titulos", erro);
        }
    }

    /** Manda TODOS os títulos guardados pro jogador que acabou de entrar. */
    public static void enviarTodos(ServerPlayer para) {
        try {
            MinecraftServer server = para.server;
            for (Map.Entry<UUID, Titulo> e : Dados.get(server).lista()) {
                Titulo t = e.getValue();
                PacketDistributor.sendToPlayer(para,
                        new TituloSyncPayload(e.getKey(), t.etiqueta(), t.cor(), t.personalizado()));
            }
        } catch (Throwable erro) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui enviar os titulos no login", erro);
        }
    }

    /**
     * Envolve um nome: {@code [Sagrado] Nome [O Ferreiro]}.
     * Devolve o próprio componente (mesma instância) se o jogador não tem título.
     */
    public static Component aplicar(MinecraftServer server, UUID id, Component nome) {
        try {
            Titulo t = Dados.get(server).de(id);
            if (t.vazio()) {
                return nome;
            }
            MutableComponent out = Component.empty();
            if (!t.etiqueta().isEmpty()) {
                out.append(Component.literal("[" + t.etiqueta() + "] ")
                        .withStyle(s -> s.withColor(TextColor.fromRgb(t.cor()))));
            }
            out.append(nome);
            if (!t.personalizado().isEmpty()) {
                // (como na print: o título pessoal vem DEPOIS do nome, em branco)
                out.append(Component.literal(" [" + t.personalizado() + "]")
                        .withStyle(ChatFormatting.WHITE));
            }
            return out;
        } catch (Throwable erro) {
            return nome;
        }
    }
}
