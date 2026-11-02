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

    /** (5.9.0) cor padrão do TÍTULO PESSOAL (o "[O Ferreiro]" depois do nome). */
    public static final int COR_PESSOAL_PADRAO = 0xFFFFFF;

    /**
     * (5.9.0) CORES COM NOME — o que dá pra escrever no {@code /titulo cor}.
     * Aceita português e inglês. Hex livre: {@code FFD700} ou {@code "#FFD700"}.
     */
    public static final java.util.Map<String, Integer> CORES_NOMEADAS = new java.util.LinkedHashMap<>();

    static {
        CORES_NOMEADAS.put("branco", 0xFFFFFF);
        CORES_NOMEADAS.put("white", 0xFFFFFF);
        CORES_NOMEADAS.put("preto", 0x000000);
        CORES_NOMEADAS.put("black", 0x000000);
        CORES_NOMEADAS.put("cinza", 0xAAAAAA);
        CORES_NOMEADAS.put("gray", 0xAAAAAA);
        CORES_NOMEADAS.put("cinza-escuro", 0x555555);
        CORES_NOMEADAS.put("dark_gray", 0x555555);
        CORES_NOMEADAS.put("vermelho", 0xFF5555);
        CORES_NOMEADAS.put("red", 0xFF5555);
        CORES_NOMEADAS.put("vermelho-escuro", 0xAA0000);
        CORES_NOMEADAS.put("dark_red", 0xAA0000);
        CORES_NOMEADAS.put("ouro", 0xFFAA00);
        CORES_NOMEADAS.put("dourado", 0xFFAA00);
        CORES_NOMEADAS.put("gold", 0xFFAA00);
        CORES_NOMEADAS.put("amarelo", 0xFFFF55);
        CORES_NOMEADAS.put("yellow", 0xFFFF55);
        CORES_NOMEADAS.put("verde", 0x55FF55);
        CORES_NOMEADAS.put("green", 0x55FF55);
        CORES_NOMEADAS.put("verde-escuro", 0x00AA00);
        CORES_NOMEADAS.put("dark_green", 0x00AA00);
        CORES_NOMEADAS.put("menta", 0x38E0A6);
        CORES_NOMEADAS.put("mint", 0x38E0A6);
        CORES_NOMEADAS.put("ciano", 0x55FFFF);
        CORES_NOMEADAS.put("aqua", 0x55FFFF);
        CORES_NOMEADAS.put("ciano-escuro", 0x00AAAA);
        CORES_NOMEADAS.put("dark_aqua", 0x00AAAA);
        CORES_NOMEADAS.put("azul", 0x5555FF);
        CORES_NOMEADAS.put("blue", 0x5555FF);
        CORES_NOMEADAS.put("azul-escuro", 0x0000AA);
        CORES_NOMEADAS.put("dark_blue", 0x0000AA);
        CORES_NOMEADAS.put("roxo", 0xFF55FF);
        CORES_NOMEADAS.put("lilas", 0xFF55FF);
        CORES_NOMEADAS.put("light_purple", 0xFF55FF);
        CORES_NOMEADAS.put("roxo-escuro", 0xAA00AA);
        CORES_NOMEADAS.put("dark_purple", 0xAA00AA);
        CORES_NOMEADAS.put("laranja", 0xFF8C00);
        CORES_NOMEADAS.put("orange", 0xFF8C00);
        CORES_NOMEADAS.put("rosa", 0xFF9ACD);
        CORES_NOMEADAS.put("pink", 0xFF9ACD);
    }

    /** Nomes de cor pro TAB do comando. */
    public static String[] nomesDeCor() {
        return CORES_NOMEADAS.keySet().toArray(new String[0]);
    }

    /**
     * (5.9.0) Lê uma cor escrita pelo admin.
     *
     * @return o RGB; {@code null} se não reconheceu (aí o comando reclama em vez
     *         de pintar de preto sem avisar).
     */
    public static Integer corDeTexto(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim().toLowerCase(java.util.Locale.ROOT);
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.matches("[0-9a-f]{6}")) {
            try {
                return Integer.parseInt(t, 16) & 0xFFFFFF;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (t.matches("[0-9a-f]{3}")) { // #FFF = #FFFFFF
            try {
                int r = Integer.parseInt(t.substring(0, 1), 16);
                int g = Integer.parseInt(t.substring(1, 2), 16);
                int b = Integer.parseInt(t.substring(2, 3), 16);
                return (r * 17 << 16) | (g * 17 << 8) | (b * 17);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return CORES_NOMEADAS.get(t);
    }

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
    /**
     * O título de um jogador. Campos vazios = sem etiqueta / sem título extra.
     *
     * @param etiqueta      o {@code [Chronos]} da frente
     * @param cor           cor da ETIQUETA (5.9.0: qualquer uma, via {@code /titulo cor})
     * @param personalizado o {@code [O Ferreiro]} depois do nome
     * @param corPessoal    (5.9.0) cor do título pessoal
     */
    public record Titulo(String etiqueta, int cor, String personalizado, int corPessoal) {

        public static final Titulo VAZIO = new Titulo("", -1, "", -1);

        /** Atalho das versões antigas (cor pessoal = branco). */
        public Titulo(String etiqueta, int cor, String personalizado) {
            this(etiqueta, cor, personalizado, -1);
        }

        public Titulo {
            etiqueta = limpar(etiqueta, MAX_ETIQUETA);
            personalizado = limpar(personalizado, MAX_PERSONALIZADO);
            // -1 = "cor de fábrica da etiqueta" (aceita preto 0x000000 de verdade)
            if (cor < 0) {
                cor = corDe(etiqueta);
            }
            if (corPessoal < 0) {
                corPessoal = COR_PESSOAL_PADRAO;
            }
        }

        /** @return a cor da etiqueta é a de fábrica (ninguém escolheu na mão)? */
        public boolean corDeFabrica() {
            return cor == corDe(etiqueta);
        }

        public boolean vazio() {
            return etiqueta.isEmpty() && personalizado.isEmpty();
        }

        public net.minecraft.nbt.CompoundTag save() {
            net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
            t.putString("Etiqueta", etiqueta);
            t.putInt("Cor", cor);
            t.putString("Titulo", personalizado);
            t.putInt("CorPessoal", corPessoal); // (5.9.0)
            return t;
        }

        public static Titulo load(net.minecraft.nbt.CompoundTag t) {
            // saves antigos (5.7.x) não têm "CorPessoal": cai no branco
            return new Titulo(t.getString("Etiqueta"),
                    t.contains("Cor") ? t.getInt("Cor") : -1,
                    t.getString("Titulo"),
                    t.contains("CorPessoal") ? t.getInt("CorPessoal") : -1);
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
            TituloSyncPayload pacote = new TituloSyncPayload(id, t.etiqueta(), t.cor(), t.personalizado(), t.corPessoal());
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
                        new TituloSyncPayload(e.getKey(), t.etiqueta(), t.cor(), t.personalizado(), t.corPessoal()));
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
                // (como na print: o título pessoal vem DEPOIS do nome)
                // (5.9.0) a cor dele agora também é escolhida: /titulo cor pessoal
                out.append(Component.literal(" [" + t.personalizado() + "]")
                        .withStyle(s -> s.withColor(TextColor.fromRgb(t.corPessoal()))));
            }
            return out;
        } catch (Throwable erro) {
            return nome;
        }
    }
}
