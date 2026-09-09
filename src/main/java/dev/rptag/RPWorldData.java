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
    private final Map<UUID, Integer> colors = new HashMap<>();
    private final Map<UUID, Boolean> bubbleOptOut = new HashMap<>();
    private final Map<UUID, Boolean> bubbleMode = new HashMap<>();
    private final Map<UUID, BubbleStyle> styles = new HashMap<>();
    private boolean chatLocal = true;
    private boolean bubblesOn = true;

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

    // ==== estilo do balao ====

    public BubbleStyle getStyle(UUID id) {
        BubbleStyle style = styles.get(id);
        int color = colors.getOrDefault(id, DEFAULT_COLOR);
        if (style == null) {
            return new BubbleStyle(color, "", "", 0);
        }
        if (style.colorRGB() != color) {
            return new BubbleStyle(color, style.prefix(), style.suffix(), style.background());
        }
        return style;
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

        tag.putBoolean("ChatLocal", chatLocal);
        tag.putBoolean("BubblesOn", bubblesOn);
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

        ListTag colors = tag.getList("Colors", Tag.TAG_COMPOUND);
        for (int i = 0; i < colors.size(); i++) {
            CompoundTag e = colors.getCompound(i);
            data.colors.put(e.getUUID("Id"), e.getInt("Rgb"));
        }

        ListTag optOuts = tag.getList("BubbleOptOut", Tag.TAG_COMPOUND);
        for (int i = 0; i < optOuts.size(); i++) {
            data.bubbleOptOut.put(optOuts.getCompound(i).getUUID("Id"), true);
        }

        data.chatLocal = tag.getBoolean("ChatLocal");
        data.bubblesOn = !tag.contains("BubblesOn") || tag.getBoolean("BubblesOn");
        return data;
    }
}
