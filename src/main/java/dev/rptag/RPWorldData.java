package dev.rptag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Dados do mod salvos junto do mundo (data/rptag_states.dat):
 *
 * <ul>
 *   <li>estado (em RP ou nao) de cada jogador</li>
 *   <li>persona (personagem) de cada jogador</li>
 *   <li>preferencia de chat local ligado/desligado</li>
 *   <li>cor pessoal de cada jogador (mensagem no chat + fundo do balao)</li>
 *   <li>config de baloes de fala (global e por jogador)</li>
 * </ul>
 */
public final class RPWorldData extends SavedData {

    public static final String DATA_NAME = "rptag_states";

    /** Raio padrao do chat local (blocos). Grito=100, sussurro=5. */
    public static final int LOCAL_RANGE = 40;
    public static final int SHOUT_RANGE = 100;
    public static final int WHISPER_RANGE = 5;

    /** Cor padrao (branco) quando o jogador nao escolheu nenhuma. */
    public static final int DEFAULT_COLOR = 0xFFFFFF;

    private final Map<UUID, Boolean> states = new HashMap<>();
    private final Map<UUID, Persona> personas = new HashMap<>();
    /** (5.2.0) fichas do tablet Neptune (rascunho + confirmada + revisao). */
    private final Map<UUID, FichaRegistro> fichas = new HashMap<>();
    private final Map<UUID, Integer> colors = new HashMap<>();
    private final Map<UUID, Boolean> bubbleOptOut = new HashMap<>();
    /** jogadores PROIBIDOS de usar balao por um admin (ausente = permitido). */
    private final Map<UUID, Boolean> bubbleAllowed = new HashMap<>();
    private final Map<UUID, Boolean> bubbleMode = new HashMap<>();
    /** (3.65.0) typewriter do balao por jogador (padrao LIGADO). */
    private final Map<UUID, Boolean> typewriter = new HashMap<>();
    /** (3.80.0) MODO de perder vidas (todos JÁ têm vidas canônicas; com
     * o modo ligado, quem morrer perde uma). Persiste no mundo. */
    private boolean canonEvent = false;
    /** (3.82.0) VIDAS CANÔNICAS de cada jogador (quem nunca morreu tem
     * VIDAS_INICIAIS = 5, guardado só quem já perdeu/ganhou diferente). */
    private final Map<UUID, Integer> canonLives = new HashMap<>();
    private final Map<UUID, BubbleStyle> styles = new HashMap<>();
    private boolean chatLocal = true;
    private boolean bubblesOn = true;

    // ==== (4.5.0) REGRAS EM RP (padrao: tudo LIBERADO) ====
    /** PvP permitido em RP (false = bloqueia; vale se qualquer lado estiver em RP). */
    /** Quebrar bloco permitido em RP. */
    /** Colocar bloco (e balde) permitido em RP. */
    /** (4.5.0) segundos parado ate virar AFK sozinho (0 = desligado). */
    private int afkTimeout = 300;
    /** (4.5.0) recado de vida pra quem estava OFFLINE quando o admin mexeu. */
    private final Map<UUID, String> recadosVida = new HashMap<>();

    public static RPWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RPWorldData::new, RPWorldData::load, null), DATA_NAME);
    }

    // ==== estado RP ====

    /** @return true se o jogador esta em RP (padrao: false = OFF RP). */
    public boolean isInRp(UUID id) {
        return states.getOrDefault(id, false);
    }

    public void setInRp(UUID id, boolean inRp) {
        states.put(id, inRp);
        setDirty();
    }

    // ==== persona ====

    /** @return a persona do jogador ou {@link Persona#EMPTY}. */
    public Persona getPersona(UUID id) {
        return personas.getOrDefault(id, Persona.EMPTY);
    }

    public void setPersona(UUID id, Persona persona) {
        if (persona.isEmpty()) {
            personas.remove(id);
        } else {
            personas.put(id, persona);
        }
        setDirty();
    }

    // ==== ficha do tablet (5.2.0) ====

    /** ficha do jogador (cria uma inicial a partir da persona, se ainda nao houver). */
    public FichaRegistro getFicha(UUID id) {
        FichaRegistro existente = fichas.get(id);
        if (existente != null && (existente.revisao() != 0 || existente.confirmada() != null)) {
            return existente;
        }
        FichaRegistro inicial = FichaRegistro.inicial(getPersona(id));
        if (existente != null && existente.tabletEntregue()) {
            inicial = inicial.comTabletEntregue();
        }
        fichas.put(id, inicial);
        setDirty();
        return inicial;
    }

    /** ficha sem criar nada (consulta de card/comando). */
    public FichaRegistro getFichaDireto(UUID id) {
        return fichas.get(id);
    }

    public void setFicha(UUID id, FichaRegistro ficha) {
        fichas.put(id, ficha);
        setDirty();
    }

    public boolean hasConfirmedFicha(UUID id) {
        FichaRegistro f = fichas.get(id);
        return f != null && f.confirmada() != null;
    }

    // ==== cor pessoal (chat + balao) ====

    public int getColor(UUID id) {
        return colors.getOrDefault(id, DEFAULT_COLOR);
    }

    public void setColor(UUID id, int rgb) {
        colors.put(id, rgb & 0xFFFFFF);
        setDirty();
    }

    public boolean hasCustomColor(UUID id) {
        return colors.containsKey(id);
    }

    public void clearColor(UUID id) {
        colors.remove(id);
        setDirty();
    }

    // ==== baloes de fala ====

    /** Baloes ligados globalmente (toggle de admin). */
    public boolean isBubblesOn() {
        return bubblesOn;
    }

    public void setBubblesOn(boolean on) {
        bubblesOn = on;
        setDirty();
    }

    /** O jogador desligou os proprios baloes. */
    /** (3.65.0) typewriter do balao (padrao LIGADO). */
    // ==== (3.80.0) modo de perder vidas ====

    /** @return true se o MODO está ligado (quem morrer perde uma vida). */
    public boolean isCanonEvent() {
        return canonEvent;
    }

    /** Liga/desliga o modo (persiste no mundo). */
    public void setCanonEvent(boolean on) {
        this.canonEvent = on;
        setDirty();
    }

    // ==== (3.82.0) estoque de vidas canônicas ====

    /** vidas canônicas do jogador (quem nunca perdeu tem as 5 iniciais). */
    public int getCanonLives(UUID id) {
        return canonLives.getOrDefault(id, 5);
    }

    /** define o estoque de vidas canônicas (persiste no mundo). */
    public void setCanonLives(UUID id, int vidas) {
        canonLives.put(id, Math.max(0, vidas));
        setDirty();
    }

    public boolean isTypewriter(UUID id) {
        return this.typewriter.getOrDefault(id, Boolean.TRUE);
    }

    /** (3.65.0) define o typewriter do jogador. */
    public void setTypewriter(UUID id, boolean on) {
        this.typewriter.put(id, on);
        this.setDirty();
    }

    public boolean isBubbleOptOut(UUID id) {
        return bubbleOptOut.getOrDefault(id, false);
    }

    public void setBubbleOptOut(UUID id, boolean optOut) {
        bubbleOptOut.put(id, optOut);
        setDirty();
    }

    /**
     * MODO BALAO: as falas do jogador viram SOMENTE balao (nao aparecem no
     * chat). Para criancas, ovos e personagens sem voz.
     */
    public boolean isBubbleMode(UUID id) {
        return bubbleMode.getOrDefault(id, false);
    }

    public void setBubbleMode(UUID id, boolean on) {
        bubbleMode.put(id, on);
        setDirty();
    }

    // ==== permissao de balao (ADMIN LIBERA — 3.17.0: so quem o admin liberar usa) ====

    /** @return true se o admin LIBEROU o balao deste jogador (padrao: NINGUEM). */
    public boolean isBubbleAllowed(UUID id) {
        return bubbleAllowed.getOrDefault(id, false);
    }

    public void setBubbleAllowed(UUID id, boolean allowed) {
        if (allowed) {
            bubbleAllowed.put(id, true);
        } else {
            bubbleAllowed.remove(id);
        }
        setDirty();
    }

    public java.util.Set<UUID> bubbleAllowedIds() {
        return bubbleAllowed.keySet();
    }

    // ==== estilo do balao ====

    public BubbleStyle getStyle(UUID id) {
        BubbleStyle style = styles.get(id);
        if (style == null) {
            int color = colors.getOrDefault(id, DEFAULT_COLOR);
            return new BubbleStyle(color, BubbleStyle.DEFAULT_BORDER, BubbleStyle.BORDER_CLASSIC,
                    BubbleStyle.TEXT_AUTO, "", "", "", "", "", BubbleStyle.DEFAULT_STICKER_X,
                    BubbleStyle.DEFAULT_STICKER_Y, 0, BubbleStyle.DEFAULT_DURATION);
        }
        return style; // a cor escolhida na tela manda SEMPRE
    }

    public void setStyle(UUID id, BubbleStyle style) {
        styles.put(id, style);
        colors.put(id, style.colorRGB());
        setDirty();
    }

    // ==== chat local ====

    public boolean isChatLocal() {
        return chatLocal;
    }

    public void setChatLocal(boolean chatLocal) {
        this.chatLocal = chatLocal;
        setDirty();
    }


    /** (4.5.0) UUIDs com vida gravada (pra /rp admin vida sem argumento). */
    public java.util.Set<UUID> canonLivesIds() {
        return java.util.Collections.unmodifiableSet(canonLives.keySet());
    }

    /** (4.5.0) guarda o recado que o jogador (offline) vai ver ao entrar. */
    public void setRecadoVida(UUID id, String texto) {
        if (texto == null || texto.isBlank()) {
            recadosVida.remove(id);
        } else {
            recadosVida.put(id, texto);
        }
        setDirty();
    }

    /** (4.5.0) entrega e apaga (chamado no login). */
    public String consumirRecadoVida(UUID id) {
        String t = recadosVida.remove(id);
        if (t != null) setDirty();
        return t;
    }

    /** (4.5.0) segundos parado ate entrar em AFK; 0 desliga o automatico. */
    public int getAfkTimeout() {
        return afkTimeout;
    }

    public void setAfkTimeout(int segundos) {
        this.afkTimeout = Math.max(0, segundos);
        setDirty();
    }

    // ==== NBT ====

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag states = new ListTag();
        this.states.forEach((id, inRp) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.putBoolean("InRp", inRp);
            states.add(e);
        });
        tag.put("States", states);

        ListTag personas = new ListTag();
        this.personas.forEach((id, persona) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.put("Persona", persona.save());
            personas.add(e);
        });
        tag.put("Personas", personas);

        ListTag sheets = new ListTag();
        this.fichas.forEach((id, ficha) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.put("Sheet", ficha.save());
            sheets.add(e);
        });
        tag.put("TabletSheets", sheets);

        ListTag colors = new ListTag();
        this.colors.forEach((id, rgb) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.putInt("Rgb", rgb);
            colors.add(e);
        });
        tag.put("Colors", colors);

        ListTag optOuts = new ListTag();
        this.bubbleOptOut.forEach((id, opt) -> {
            if (opt) {
                CompoundTag e = new CompoundTag();
                e.putUUID("Id", id);
                optOuts.add(e);
            }
        });
        tag.put("BubbleOptOut", optOuts);

        ListTag banned = new ListTag();
        this.bubbleAllowed.forEach((id, b) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            banned.add(e);
        });
        tag.put("BubbleAllowed", banned);

        ListTag modes = new ListTag();
        this.bubbleMode.forEach((id, on) -> {
            if (on) {
                CompoundTag e = new CompoundTag();
                e.putUUID("Id", id);
                modes.add(e);
            }
        });
        tag.put("BubbleMode", modes);

        ListTag stylesList = new ListTag();
        this.styles.forEach((id, style) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.put("Style", style.save());
            stylesList.add(e);
        });
        tag.put("BubbleStyles", stylesList);

        ListTag typewriterList = new ListTag();
        this.typewriter.forEach((id, on) -> {
            if (on) {
                CompoundTag e = new CompoundTag();
                e.putUUID("Id", id);
                typewriterList.add(e);
            }
        });
        tag.put("Typewriter", typewriterList);

        // (3.80.0) modo de perder vidas
        tag.putBoolean("CanonEvent", canonEvent);

        // (3.82.0) vidas canônicas por jogador
        ListTag vidasList = new ListTag();
        for (Map.Entry<UUID, Integer> en : canonLives.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", en.getKey());
            e.putInt("Lives", en.getValue());
            vidasList.add(e);
        }
        tag.put("CanonLives", vidasList);

        tag.putBoolean("ChatLocal", chatLocal);
        tag.putBoolean("BubblesOn", bubblesOn);
        // (4.9.0) as 3 flags de "regra em RP" sairam junto com o /rp admin regra
        tag.putInt("AfkTimeout", afkTimeout);
        // (4.5.0) recados de vida de quem estava offline
        ListTag recados = new ListTag();
        for (var en : recadosVida.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", en.getKey());
            e.putString("Texto", en.getValue());
            recados.add(e);
        }
        tag.put("RecadosVida", recados);
        return tag;
    }

    public static RPWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        RPWorldData data = new RPWorldData();

        ListTag states = tag.getList("States", Tag.TAG_COMPOUND);
        for (int i = 0; i < states.size(); i++) {
            CompoundTag e = states.getCompound(i);
            data.states.put(e.getUUID("Id"), e.getBoolean("InRp"));
        }

        ListTag personas = tag.getList("Personas", Tag.TAG_COMPOUND);
        for (int i = 0; i < personas.size(); i++) {
            CompoundTag e = personas.getCompound(i);
            data.personas.put(e.getUUID("Id"), Persona.load(e.getCompound("Persona")));
        }

        ListTag sheets = tag.getList("TabletSheets", Tag.TAG_COMPOUND);
        for (int i = 0; i < sheets.size(); i++) {
            CompoundTag e = sheets.getCompound(i);
            // (5.2.0) nunca confiar no save: entrada sem UUID ou malformada e ignorada
            if (!e.hasUUID("Id") || !e.contains("Sheet", Tag.TAG_COMPOUND)) {
                continue;
            }
            try {
                data.fichas.put(e.getUUID("Id"), FichaRegistro.load(e.getCompound("Sheet")));
            } catch (RuntimeException erro) {
                RPTagMod.LOGGER.warn("RP Tag: ficha do tablet descartada (dados invalidos)", erro);
            }
        }

        ListTag colors = tag.getList("Colors", Tag.TAG_COMPOUND);
        for (int i = 0; i < colors.size(); i++) {
            CompoundTag e = colors.getCompound(i);
            data.colors.put(e.getUUID("Id"), e.getInt("Rgb"));
        }

        ListTag optOuts = tag.getList("BubbleOptOut", Tag.TAG_COMPOUND);
        for (int i = 0; i < optOuts.size(); i++) {
            data.bubbleOptOut.put(optOuts.getCompound(i).getUUID("Id"), true);
        }

        ListTag banned = tag.getList("BubbleAllowed", Tag.TAG_COMPOUND);
        for (int i = 0; i < banned.size(); i++) {
            data.bubbleAllowed.put(banned.getCompound(i).getUUID("Id"), true);
        }

        // CORRECAO: estilos e modos de balao agora voltam ao reiniciar o servidor!
        ListTag modes = tag.getList("BubbleMode", Tag.TAG_COMPOUND);
        for (int i = 0; i < modes.size(); i++) {
            data.bubbleMode.put(modes.getCompound(i).getUUID("Id"), true);
        }

        ListTag typewriterList = tag.getList("Typewriter", Tag.TAG_COMPOUND);
        for (int i = 0; i < typewriterList.size(); i++) {
            data.typewriter.put(typewriterList.getCompound(i).getUUID("Id"), true);
        }

        data.canonEvent = tag.getBoolean("CanonEvent");

        ListTag vidasList = tag.getList("CanonLives", Tag.TAG_COMPOUND);
        for (int i = 0; i < vidasList.size(); i++) {
            CompoundTag e = vidasList.getCompound(i);
            data.canonLives.put(e.getUUID("Id"), e.getInt("Lives"));
        }

        ListTag stylesList = tag.getList("BubbleStyles", Tag.TAG_COMPOUND);
        for (int i = 0; i < stylesList.size(); i++) {
            CompoundTag e = stylesList.getCompound(i);
            data.styles.put(e.getUUID("Id"), BubbleStyle.load(e.getCompound("Style")));
        }

        // (correcao) mesmo cuidado que ja existia pro "BubblesOn": se a tag
        // nunca foi salva (mundo/save antigo), tem que manter o padrao real
        // (ligado) em vez de virar "false" so por ausencia da chave.
        data.chatLocal = !tag.contains("ChatLocal") || tag.getBoolean("ChatLocal");
        data.bubblesOn = !tag.contains("BubblesOn") || tag.getBoolean("BubblesOn");

        // (4.5.0) mesmo cuidado: save antigo nao tem as chaves -> padrao LIBERADO
        data.afkTimeout = tag.contains("AfkTimeout") ? tag.getInt("AfkTimeout") : AfkSistema.TIMEOUT_PADRAO_S;
        ListTag recados = tag.getList("RecadosVida", Tag.TAG_COMPOUND);
        for (int i = 0; i < recados.size(); i++) {
            CompoundTag e = recados.getCompound(i);
            data.recadosVida.put(e.getUUID("Id"), e.getString("Texto"));
        }
        return data;
    }
}
