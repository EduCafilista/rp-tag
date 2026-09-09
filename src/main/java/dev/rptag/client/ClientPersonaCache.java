package dev.rptag.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.rptag.Persona;

/**
 * Cache do cliente: persona de cada jogador, recebida via pacote.
 * Usada para trocar o nome acima da cabeca pelo nome do personagem.
 */
public final class ClientPersonaCache {

    private record Entry(String name, int age, String desc) {
    }

    private static final Map<UUID, Entry> PERSONAS = new ConcurrentHashMap<>();

    private ClientPersonaCache() {
    }

    public static void set(UUID id, String name, int age, String desc) {
        if (name == null || name.isEmpty()) {
            PERSONAS.remove(id);
        } else {
            PERSONAS.put(id, new Entry(name, age, desc == null ? "" : desc));
        }
    }

    /** @return o nome do personagem ou null se nao tem persona. */
    public static String getName(UUID id) {
        Entry e = PERSONAS.get(id);
        return e != null ? e.name() : null;
    }

    /** @return a descricao do personagem ou "" se nao tem. */
    public static String getDesc(UUID id) {
        Entry e = PERSONAS.get(id);
        return e != null ? e.desc() : "";
    }

    /** @return a idade do personagem ou 0 se nao tem. */
    public static int getAge(UUID id) {
        Entry e = PERSONAS.get(id);
        return e != null ? e.age() : 0;
    }

    public static boolean has(UUID id) {
        return PERSONAS.containsKey(id);
    }

    public static void clear() {
        PERSONAS.clear();
    }
}
