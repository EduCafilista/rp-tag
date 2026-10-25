package dev.rptag.client;

import dev.rptag.*;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Uma solicitação por vez; o texto local não depende do ciclo de vida da tela. */
public final class TabletClientState {
    private final UUID target;
    private final Consumer<TabletUpdatePayload> send;
    private final LongSupplier clock;
    private FichaRegistro server;
    private FichaPersonagem local;
    private int step;
    private boolean readOnly, blocked, force, confirm;
    private long generation, savedGeneration, sentGeneration, editedAt, sentAt, retryAt;
    private int nextId;
    private TabletUpdatePayload pending;
    private String error = "";

    public TabletClientState(TabletSyncPayload initial, Consumer<TabletUpdatePayload> send, LongSupplier clock) {
        this.target = initial.target();
        this.send = send;
        this.clock = clock;
        server = initial.registro();
        readOnly = initial.readOnly();
        local = readOnly && server.confirmada() != null ? server.confirmada() : server.rascunho();
        step = server.etapa();
    }

    public UUID target() { return target; }
    public FichaPersonagem respostas() { return local; }
    public int etapa() { return step; }
    public boolean readOnly() { return readOnly; }
    public boolean pending() { return pending != null; }
    public boolean confirming() { return confirm || pending != null && pending.confirmar(); }
    public boolean dirty() { return generation != savedGeneration; }
    public String error() { return error; }
    public boolean blocked() { return blocked; }

    public void edit(FichaPersonagem respostas, int etapa) {
        if (readOnly || confirming() || blocked) return;
        if (etapa < 0 || etapa > 5) throw new IllegalArgumentException("Etapa inválida.");
        if (local.equals(respostas) && step == etapa) return;
        local = respostas;
        step = etapa;
        generation++;
        editedAt = clock.getAsLong();
        error = "";
    }

    public void flush(boolean confirmar) {
        if (readOnly || blocked) return;
        if (!dirty() && !confirm && !confirmar) { force = false; return; }
        String invalid = local.validationError(confirmar);
        if (!invalid.isEmpty()) { error = invalid; return; }
        confirm |= confirmar;
        force = true;
        sendIfReady();
    }

    private void sendIfReady() {
        if (pending != null || blocked || readOnly || clock.getAsLong() < retryAt || !dirty() && !confirm) return;
        String invalid=local.validationError(confirm);
        if(!invalid.isEmpty()) { error=invalid; return; }
        pending = new TabletUpdatePayload(server.revisao(), nextId++, local, step, confirm);
        sentGeneration = generation;
        sentAt = clock.getAsLong();
        force = false;
        send.accept(pending);
    }

    public void tick() {
        if (pending != null && clock.getAsLong() - sentAt > 10_000) error = "Aguardando resposta do servidor. Seu texto continua nesta sessão.";
        if ((force || confirm || dirty() && clock.getAsLong() - editedAt >= 750) && pending == null && !blocked) sendIfReady();
    }

    public void accept(TabletSyncPayload response) {
        if (!target.equals(response.target()) || response.registro().revisao() < server.revisao()) return;
        if (response.requestId() >= 0 && (pending == null || response.requestId() != pending.requestId())) return;
        boolean unsolicited = response.requestId() < 0;
        if (unsolicited && response.registro().revisao() == server.revisao() && (dirty() || pending != null)) return;
        boolean hadLocal = dirty();
        boolean wasConfirmation = pending != null && pending.confirmar();
        server = response.registro();
        readOnly = response.readOnly();
        if (!unsolicited) {
            pending = null;
            if (response.error().isEmpty()) savedGeneration = sentGeneration;
        }
        if (response.retryable()) {
            error = response.error();
            force = true;
            retryAt = clock.getAsLong() + 250;
            return;
        }
        if (!response.error().isEmpty() || unsolicited && hadLocal) {
            error = response.error().isEmpty() ? "A ficha mudou no servidor. Seu texto foi mantido; confira a versão atual antes de continuar." : response.error();
            blocked = true;
            confirm = false;
            return;
        }
        error = "";
        if (!dirty() || readOnly || unsolicited && !hadLocal) {
            local = readOnly && server.confirmada() != null ? server.confirmada() : server.rascunho();
            step = server.etapa();
            if (readOnly) savedGeneration = generation;
        }
        if (wasConfirmation && !unsolicited) confirm = false;
        if (force || confirm) sendIfReady();
    }

    /** Ação explícita do jogador para substituir a cópia local após um conflito. */
    public void reloadServer() {
        if (pending != null) return;
        local = readOnly && server.confirmada() != null ? server.confirmada() : server.rascunho();
        step = server.etapa();
        savedGeneration = generation;
        error = "";
        blocked = force = confirm = false;
    }
}
