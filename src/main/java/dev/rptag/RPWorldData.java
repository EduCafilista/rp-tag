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
 * </ul>
 */
public final class RPWorldData extends SavedData {

    public static final String DATA_NAME = "rptag_states";

    /** Raio padrao do chat local (blocos). Grito=100, sussurro=5. */
    public static final int LOCAL_RANGE = 40;
    public static final int SHOUT_RANGE = 100;
    public static final int WHISPER_RANGE = 5;

    private final Map<UUID, Boolean> states = new HashMap<>();
    private final Map<UUID, Persona> personas = new HashMap<>();
    private boolean chatLocal = true;

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

        tag.putBoolean("ChatLocal", chatLocal);
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

        data.chatLocal = tag.getBoolean("ChatLocal");
        return data;
    }
}
