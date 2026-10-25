package dev.rptag.client;

import dev.rptag.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletClientStateTest {
    private final UUID id = UUID.randomUUID();
    private final AtomicLong clock = new AtomicLong();
    private final List<TabletUpdatePayload> sent = new ArrayList<>();
    private TabletSyncPayload snapshot(FichaRegistro r, int request, String error) { return new TabletSyncPayload(id, request, r, !r.podeEditar(), false, false, error); }

    @Test void ackAntigoPreservaTextoNovoEFecharDrenaUltimaEdicao() {
        FichaRegistro initial = FichaRegistro.inicial(Persona.EMPTY);
        TabletClientState state = new TabletClientState(snapshot(initial, -1, ""), sent::add, clock::get);
        FichaPersonagem a = FichaPersonagem.EMPTY.comCampo(0, "A");
        FichaPersonagem b = a.comCampo(0, "B");
        state.edit(a, 0);
        state.flush(false);
        state.edit(b, 1);
        state.flush(false);
        assertEquals(1, sent.size());
        FichaRegistro accepted = initial.atualizar(a, 0, 0, false);
        state.accept(snapshot(accepted, sent.getFirst().requestId(), ""));
        assertEquals("B", state.respostas().nome());
        assertEquals(2, sent.size());
        assertEquals(1, sent.get(1).revisao());
        assertEquals(b, sent.get(1).respostas());
    }

    @Test void debounceEspera750msEConfirmarNaoDuplica() {
        FichaRegistro initial = FichaRegistro.inicial(Persona.EMPTY);
        TabletClientState state = new TabletClientState(snapshot(initial, -1, ""), sent::add, clock::get);
        state.edit(new FichaPersonagem("Ari", "24", "Porto", "Mapas", "História"), 5);
        clock.set(749); state.tick(); assertTrue(sent.isEmpty());
        clock.set(750); state.tick(); assertEquals(1, sent.size());
        state.flush(true); state.flush(true); assertEquals(1, sent.size());
        state.accept(snapshot(initial.atualizar(sent.getFirst().respostas(), 5, 0, false), sent.getFirst().requestId(), ""));
        assertEquals(2, sent.size());
        assertTrue(sent.get(1).confirmar());
    }

    @Test void rejeicaoPreservaLocalENaoReenviaSemAcaoExplicita() {
        FichaRegistro initial = FichaRegistro.inicial(Persona.EMPTY);
        TabletClientState state = new TabletClientState(snapshot(initial, -1, ""), sent::add, clock::get);
        state.edit(FichaPersonagem.EMPTY.comCampo(0, "Local"), 0);
        state.flush(false);
        state.accept(snapshot(initial, sent.getFirst().requestId(), "Conflito"));
        clock.set(5000); state.tick();
        assertEquals("Local", state.respostas().nome());
        assertEquals("Conflito", state.error());
        assertEquals(1, sent.size());
        state.reloadServer();
        assertEquals("", state.respostas().nome());
    }

    @Test void limiteTemporarioReenviaSemDescartarTextoLocal() {
        FichaRegistro initial = FichaRegistro.inicial(Persona.EMPTY);
        TabletClientState state = new TabletClientState(snapshot(initial, -1, ""), sent::add, clock::get);
        state.edit(FichaPersonagem.EMPTY.comCampo(0, "Texto novo"), 0);
        state.flush(false);
        state.accept(new TabletSyncPayload(id, sent.getFirst().requestId(), initial, false, false, true, "Aguarde"));
        assertFalse(state.blocked());
        assertEquals("Texto novo", state.respostas().nome());
        clock.set(249); state.tick(); assertEquals(1, sent.size());
        clock.set(250); state.tick(); assertEquals(2, sent.size());
        assertEquals("Texto novo", sent.get(1).respostas().nome());
    }
}
