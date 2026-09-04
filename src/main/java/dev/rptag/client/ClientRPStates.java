package dev.rptag.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cache do cliente: estado (RP) de cada jogador conhecido, recebido via pacote.
 *
 * <p>{@link #hasData()} indica se o servidor ja enviou alguma sincronizacao —
 * sem isso, nao desenhamos badge nenhum (evita mostrar (OFF RP) em servidor
 * sem o mod instalado).
 */
public final class ClientRPStates {

    private static final Map<UUID, Boolean> STATES = new ConcurrentHashMap<>();
    private static final AtomicBoolean HAS_DATA = new AtomicBoolean(false);

    private ClientRPStates() {
    }

    public static void set(UUID id, boolean inRp) {
        STATES.put(id, inRp);
        HAS_DATA.set(true);
    }

    public static boolean isInRp(UUID id) {
        return STATES.getOrDefault(id, false);
    }

    /** @return true se o servidor mandou pelo menos uma sincronizacao nesta sessao. */
    public static boolean hasData() {
        return HAS_DATA.get();
    }

    public static void clear() {
        STATES.clear();
        HAS_DATA.set(false);
    }
}
