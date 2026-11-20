package dev.rptag.yumaho;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cofre persistente (mundo principal) com os dois inventários de cada jogador:
 *  - real:  o inventário normal, guardado enquanto o jogador está na Dimensão Branca;
 *  - white: o inventário da Dimensão Branca, guardado enquanto o jogador está fora.
 * Fica no disco do servidor, então sobrevive a morte, desconexão, crash e reinício.
 */
public final class WhiteVault extends SavedData {
    private static final String NAME = "yumaho_white_vault";

    public static final class Entry {
        public ListTag real = new ListTag();
        public ListTag white = new ListTag();
        public boolean inWhite;
        /** morreu na dimensão: o inventário branco já foi guardado e esvaziado. */
        public boolean dead;
        public boolean hasRet;
        public String retDim = "minecraft:overworld";
        public double rx, ry, rz;
        public float ryaw, rpitch;
    }

    private final Map<UUID, Entry> map = new HashMap<>();

    public Entry entry(UUID id) { return map.computeIfAbsent(id, k -> new Entry()); }
    public Entry peek(UUID id) { return map.get(id); }

    public static final SavedData.Factory<WhiteVault> FACTORY = new SavedData.Factory<>(WhiteVault::new, WhiteVault::load, null);

    public static WhiteVault get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag list = new ListTag();
        for (var en : map.entrySet()) {
            Entry e = en.getValue();
            CompoundTag c = new CompoundTag();
            c.putUUID("id", en.getKey());
            c.put("real", e.real);
            c.put("white", e.white);
            c.putBoolean("in", e.inWhite);
            c.putBoolean("dead", e.dead);
            c.putBoolean("hasRet", e.hasRet);
            c.putString("retDim", e.retDim);
            c.putDouble("rx", e.rx); c.putDouble("ry", e.ry); c.putDouble("rz", e.rz);
            c.putFloat("ryaw", e.ryaw); c.putFloat("rpitch", e.rpitch);
            list.add(c);
        }
        tag.put("players", list);
        return tag;
    }

    public static WhiteVault load(CompoundTag tag, HolderLookup.Provider reg) {
        WhiteVault v = new WhiteVault();
        ListTag list = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            Entry e = new Entry();
            e.real = c.getList("real", Tag.TAG_COMPOUND);
            e.white = c.getList("white", Tag.TAG_COMPOUND);
            e.inWhite = c.getBoolean("in");
            e.dead = c.getBoolean("dead");
            e.hasRet = c.getBoolean("hasRet");
            e.retDim = c.getString("retDim");
            e.rx = c.getDouble("rx"); e.ry = c.getDouble("ry"); e.rz = c.getDouble("rz");
            e.ryaw = c.getFloat("ryaw"); e.rpitch = c.getFloat("rpitch");
            v.map.put(c.getUUID("id"), e);
        }
        return v;
    }
}
