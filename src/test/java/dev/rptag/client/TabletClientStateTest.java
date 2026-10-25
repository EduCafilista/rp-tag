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

    @Test void confirmacaoAceitaExibeVersaoNormalizadaEBloqueiaEscrita() {
        FichaRegistro initial = FichaRegistro.inicial(Persona.EMPTY);
        TabletClientState state = new TabletClientState(snapshot(initial,-1,""),sent::add,clock::get);
        state.edit(new FichaPersonagem("  João  ","0024","Porto","Mapas","História"),5);
        state.flush(true);
        TabletUpdatePayload request=sent.getFirst();
        FichaRegistro confirmed=initial.atualizar(request.respostas(),5,0,true);
        state.accept(snapshot(confirmed,request.requestId(),""));
        assertTrue(state.readOnly());
        assertFalse(state.pending());
        assertFalse(state.confirming());
        assertFalse(state.dirty());
        assertEquals("João",state.respostas().nome());
        state.edit(FichaPersonagem.EMPTY,0);
        state.flush(false);
        assertEquals("João",state.respostas().nome());
        assertEquals(1,sent.size());
    }

    @Test void retornoRapidoEntreEtapasNaoPerdeAUltimaPergunta() {
        FichaRegistro initial=FichaRegistro.inicial(Persona.EMPTY);
        TabletClientState state=new TabletClientState(snapshot(initial,-1,""),sent::add,clock::get);
        state.edit(FichaPersonagem.EMPTY.comCampo(0,"Ari"),0);
        state.flush(false);
        state.edit(state.respostas(),4);
        state.flush(false);
        state.edit(state.respostas(),2);
        state.flush(false);
        TabletUpdatePayload first=sent.getFirst();
        state.accept(snapshot(initial.atualizar(first.respostas(),0,0,false),first.requestId(),""));
        assertEquals(2,state.etapa());
        assertEquals(2,sent.get(1).etapa());
        assertEquals("Ari",state.respostas().nome());
    }

    @Test void respostaDeOutraFichaOuDuplicadaNaoTrocaOsDados() {
        FichaRegistro initial=FichaRegistro.inicial(Persona.EMPTY);
        TabletClientState state=new TabletClientState(snapshot(initial,-1,""),sent::add,clock::get);
        state.edit(FichaPersonagem.EMPTY.comCampo(0,"Meu nome"),0);
        state.flush(false);
        TabletUpdatePayload request=sent.getFirst();
        FichaRegistro accepted=initial.atualizar(request.respostas(),0,0,false);
        state.accept(new TabletSyncPayload(UUID.randomUUID(),request.requestId(),accepted,false,false,false,""));
        assertTrue(state.pending());
        state.accept(snapshot(accepted,request.requestId(),""));
        assertFalse(state.pending());
        state.accept(snapshot(accepted,request.requestId(),""));
        assertEquals("Meu nome",state.respostas().nome());
        assertEquals(1,sent.size());
    }

    @Test void fecharSemEditarNaoIgnoraDebounceNaProximaAbertura() {
        FichaRegistro initial=FichaRegistro.inicial(Persona.EMPTY);
        TabletClientState state=new TabletClientState(snapshot(initial,-1,""),sent::add,clock::get);
        state.flush(false);
        state.edit(FichaPersonagem.EMPTY.comCampo(0,"Ari"),0);
        clock.set(100); state.tick(); assertTrue(sent.isEmpty());
        clock.set(749); state.tick(); assertTrue(sent.isEmpty());
        clock.set(750); state.tick(); assertEquals(1,sent.size());
    }
}
