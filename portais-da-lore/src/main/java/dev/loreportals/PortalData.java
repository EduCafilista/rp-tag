package dev.loreportals;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MEMORIA DO MUNDO — todos os portais conhecidos, seus vinculos e as
 * viagens. Vive no SavedData do overworld (arquivo data/loreportals_data)
 * — sobrevive a reinicio e funciona entre dimensoes.
 */
public final class PortalData extends SavedData {

    /** Um portal conhecido. */
    public record Portal(String dim, int x, int y, int z, String tipo, int facing, String linkedTo,
            int viagens, String ultimoViajante) {

        /** Construtor curto (sem contadores) — usado em ativacao/vinculo. */
        public Portal(String dim, int x, int y, int z, String tipo, int facing, String linkedTo) {
            this(dim, x, y, z, tipo, facing, linkedTo, 0, null);
        }

        /** Copia com outro vinculo, preservando os contadores. */
        public Portal comLinked(String novoLinked) {
            return new Portal(dim, x, y, z, tipo, facing, novoLinked, viagens, ultimoViajante);
        }

        /** Copia com +1 viagem. */
        public Portal comViagem(String viajante) {
            return new Portal(dim, x, y, z, tipo, facing, linkedTo, viagens + 1, viajante);
        }

        public boolean isMolde() {
            return PortalBlockEntity.TIPO_MOLDE.equals(tipo);
        }

        public String key() {
            return key(dim, x, y, z);
        }

        public static String key(String dim, int x, int y, int z) {
            return dim + "|" + x + "|" + y + "|" + z;
        }
    }

    public static final String NOME = "loreportals_data";

    private final Map<String, Portal> portais = new LinkedHashMap<>();
    /** Viagens por jogador (UUID -> quantas vezes atravessou). */
    private final Map<String, Integer> viagensPorJogador = new HashMap<>();

    // ------------------------------------------------------------------
    // ACESSO
    // ------------------------------------------------------------------
    public static PortalData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                        PortalData::new, PortalData::load), NOME);
    }

    public Portal get(String key) {
        return portais.get(key);
    }

    /** Portais ordenados por criacao (estavel — os numeros do /portal listar nao pulam). */
    public Collection<Portal> todos() {
        return portais.values();
    }

    public int tamanho() {
        return portais.size();
    }

    public int viagensDe(String uuid) {
        return viagensPorJogador.getOrDefault(uuid, 0);
    }

    // ------------------------------------------------------------------
    // MUTACOES (todas marcam dirty)
    // ------------------------------------------------------------------
    public void registrar(ServerLevel level, BlockPos pos, String tipo, int facing) {
        String key = Portal.key(level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        Portal antigo = portais.get(key);
        portais.put(key, new Portal(level.dimension().location().toString(),
                pos.getX(), pos.getY(), pos.getZ(), tipo, facing,
                antigo != null ? antigo.linkedTo() : null,
                antigo != null ? antigo.viagens() : 0,
                antigo != null ? antigo.ultimoViajante() : null));
        setDirty();
    }

    public void remover(String key) {
        Portal p = portais.remove(key);
        if (p != null && p.linkedTo() != null) {
            Portal parceiro = portais.get(p.linkedTo());
            if (parceiro != null && key.equals(parceiro.linkedTo())) {
                portais.put(parceiro.key(), parceiro.comLinked(null));
            }
        }
        setDirty();
    }

    /** @return true se o vinculo foi criado (dois portais distintos e livres). */
    public boolean vincular(String keyA, String keyB) {
        if (keyA == null || keyB == null || keyA.equals(keyB)) {
            return false;
        }
        Portal a = portais.get(keyA);
        Portal b = portais.get(keyB);
        if (a == null || b == null) {
            return false;
        }
        portais.put(keyA, a.comLinked(keyB));
        portais.put(keyB, b.comLinked(keyA));
        setDirty();
        return true;
    }

    public boolean desvincular(String keyA) {
        Portal a = portais.get(keyA);
        if (a == null || a.linkedTo() == null) {
            return false;
        }
        Portal b = portais.get(a.linkedTo());
        portais.put(keyA, a.comLinked(null));
        if (b != null) {
            portais.put(b.key(), b.comLinked(null));
        }
        setDirty();
        return true;
    }

    /** Soma +1 viagem no portal e no jogador (chamar ao teleportar). */
    public void contarViagem(String key, String viajanteUuid) {
        Portal p = portais.get(key);
        if (p != null) {
            portais.put(key, p.comViagem(viajanteUuid));
        }
        viagensPorJogador.merge(viajanteUuid, 1, Integer::sum);
        setDirty();
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------
    @Override
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag lista = new ListTag();
        for (Portal p : portais.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("key", p.key());
            t.putString("dim", p.dim());
            t.putInt("x", p.x());
            t.putInt("y", p.y());
            t.putInt("z", p.z());
            t.putString("tipo", p.tipo());
            t.putInt("facing", p.facing());
            if (p.linkedTo() != null) {
                t.putString("linked", p.linkedTo());
            }
            t.putInt("viagens", p.viagens());
            if (p.ultimoViajante() != null) {
                t.putString("viajante", p.ultimoViajante());
            }
            lista.add(t);
        }
        tag.put("portais", lista);

        ListTag vj = new ListTag();
        for (Map.Entry<String, Integer> e : viagensPorJogador.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("jogador", e.getKey());
            t.putInt("n", e.getValue());
            vj.add(t);
        }
        tag.put("viagens_jogadores", vj);
        return tag;
    }

    public static PortalData load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        PortalData data = new PortalData();
        ListTag lista = tag.getList("portais", Tag.TAG_COMPOUND);
        for (int i = 0; i < lista.size(); i++) {
            CompoundTag t = lista.getCompound(i);
            String key = t.getString("key");
            if (key.isEmpty()) {
                continue;
            }
            data.portais.put(key, new Portal(t.getString("dim"), t.getInt("x"), t.getInt("y"),
                    t.getInt("z"), t.getString("tipo"), t.getInt("facing"),
                    t.contains("linked") ? t.getString("linked") : null,
                    t.getInt("viagens"),
                    t.contains("viajante") ? t.getString("viajante") : null));
        }
        ListTag vj = tag.getList("viagens_jogadores", Tag.TAG_COMPOUND);
        for (int i = 0; i < vj.size(); i++) {
            CompoundTag t = vj.getCompound(i);
            data.viagensPorJogador.put(t.getString("jogador"), t.getInt("n"));
        }
        return data;
    }
}
