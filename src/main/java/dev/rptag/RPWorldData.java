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
 * Estado (em RP ou nao) de cada jogador, salvo junto do mundo (data/rptag_states.dat).
 *
 * <p>Guardado no overworld, entao sobrevive a relog, morte, reinicio do servidor
 * e funciona em multiplayer normal e em servidores com multi-mundo.
 */
public final class RPWorldData extends SavedData {

    public static final String DATA_NAME = "rptag_states";

    private final Map<UUID, Boolean> states = new HashMap<>();

    public static RPWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RPWorldData::new, RPWorldData::load, null), DATA_NAME);
    }

    /** @return true se o jogador esta em RP (padrao: false = OFF RP). */
    public boolean isInRp(UUID id) {
        return states.getOrDefault(id, false);
    }

    public void set(UUID id, boolean inRp) {
        states.put(id, inRp);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        states.forEach((id, inRp) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putBoolean("InRp", inRp);
            list.add(entry);
        });
        tag.put("States", list);
        return tag;
    }

    public static RPWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        RPWorldData data = new RPWorldData();
        ListTag list = tag.getList("States", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.states.put(entry.getUUID("Id"), entry.getBoolean("InRp"));
        }
        return data;
    }
}
