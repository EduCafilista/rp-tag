package dev.rptag;

import dev.rptag.client.TabletClientState;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** Bateria de testes da ficha do tablet (5.2.0). Roda fora do jogo: java -cp ... TesteTablet */
public final class TesteTablet {
    static int ok, falhas;

    public static void main(String[] args) {
        limites();
        legado();
        edicaoEConfirmacao();
        nbt();
        bytebuf();
        orcamento();
        clienteFluxo();
        clienteConflito();
        clienteRetry();
        System.out.println((falhas == 0 ? "PASS" : "FAIL") + "  " + ok + "/" + (ok + falhas) + " asserts");
        if (falhas > 0) System.exit(1);
    }

    static void limites() {
        FichaPersonagem vazia = FichaPersonagem.EMPTY;
        eq("vazio nao completa", false, vazia.validationError(true).isEmpty());
        eq("vazio parcial ok", true, vazia.validationError(false).isEmpty());
        FichaPersonagem cheia = new FichaPersonagem("Ana", "30", "Uberlandia", "curar", "nasceu aqui");
        eq("ficha cheia ok", "", cheia.validationError(true));
        eq("idade letras", false, new FichaPersonagem("Ana", "3a", "", "", "").validationError(false).isEmpty());
        eq("idade 5 digitos", true, new FichaPersonagem("Ana", "99999", "", "", "").validationError(false).isEmpty());
        eq("nome longo", false, new FichaPersonagem("x".repeat(33), "", "", "", "").validationError(false).isEmpty());
        eq("nome no limite", true, new FichaPersonagem("x".repeat(32), "", "", "", "").validationError(false).isEmpty());
        eq("§ recusado", false, new FichaPersonagem("A§bna", "", "", "", "").validationError(false).isEmpty());
        eq("quebra de linha no nome", false, new FichaPersonagem("A\nB", "", "", "", "").validationError(false).isEmpty());
        eq("quebra de linha na historia", true, new FichaPersonagem("Ana", "", "", "", "a\nb").validationError(false).isEmpty());
        eq("historia 3001", false, new FichaPersonagem("Ana", "", "", "", "x".repeat(3001)).validationError(false).isEmpty());
        eq("historia 3000", true, new FichaPersonagem("Ana", "", "", "", "x".repeat(3000)).validationError(false).isEmpty());
    }

    static void legado() {
        FichaRegistro r = FichaRegistro.inicial(new Persona("§bLord §lAldric", 40, "antigo"));
        eq("legado: § fora", "Lord Aldric", r.rascunho().nome());
        eq("legado: idade", "40", r.rascunho().idade());
        eq("legado: nao confirmada", true, r.confirmada() == null);
        FichaRegistro r2 = FichaRegistro.inicial(new Persona("Velho", 4000, ""));
        eq("legado: idade fora da faixa", "", r2.rascunho().idade());
        FichaRegistro r3 = FichaRegistro.inicial(new Persona("x".repeat(60), 20, ""));
        eq("legado: nome cortado em 32", 32, r3.rascunho().nome().length());
    }

    static void edicaoEConfirmacao() {
        FichaRegistro r = FichaRegistro.inicial(Persona.EMPTY);
        eq("inicial pode editar", true, r.podeEditar());
        FichaPersonagem f = new FichaPersonagem("Ana", "30", "Uberlandia", "curar", "historia");
        FichaRegistro r1 = r.atualizar(f, 1, r.revisao(), false);
        eq("revisao +1", 1L, r1.revisao());
        eq("rascunho guardado", "Ana", r1.rascunho().nome());
        eq("ainda sem confirmada", true, r1.confirmada() == null);
        FichaRegistro r2 = r1.atualizar(f, 5, r1.revisao(), true);
        eq("confirmada", "Ana", r2.confirmada().nome());
        eq("bloqueia edicao", false, r2.podeEditar());
        erro("revisao velha", () -> r2.atualizar(f, 5, 0L, false), IllegalStateException.class);
        erro("editar sem liberar", () -> r2.atualizar(f, 5, r2.revisao(), false), IllegalStateException.class);
        FichaRegistro r3 = r2.liberarEdicao();
        eq("liberado edita", true, r3.podeEditar());
        eq("liberou revisao +1", 3L, r3.revisao());
        eq("rascunho mostra confirmada", "Ana", r3.rascunho().nome());
        FichaRegistro r4 = r3.atualizar(new FichaPersonagem("Bia", "31", "x", "y", "z"), 0, r3.revisao(), false);
        eq("edicao apos liberar", "Bia", r4.rascunho().nome());
        eq("confirmada preservada", "Ana", r4.confirmada().nome());
        erro("confirmar incompleta", () -> r1.atualizar(new FichaPersonagem("Ana", "", "", "", ""), 5, 1, true), IllegalArgumentException.class);
        erro("etapa invalida", () -> new FichaRegistro(f, null, false, 9, 0, false), IllegalArgumentException.class);
    }

    static void nbt() {
        FichaPersonagem f = new FichaPersonagem("Ana", "30", "Uberlandia", "curar\ntocar", "historia");
        FichaRegistro r = new FichaRegistro(f, f, true, 4, 77, true);
        CompoundTag tag = r.save();
        FichaRegistro volta = FichaRegistro.load(tag);
        eq("nbt rascunho", "Ana", volta.rascunho().nome());
        eq("nbt confirmada", "historia", volta.confirmada().historia());
        eq("nbt etapa", 4, volta.etapa());
        eq("nbt revisao", 77L, volta.revisao());
        eq("nbt entregue", true, volta.tabletEntregue());
        eq("nbt editavel", true, volta.edicaoLiberada());
        FichaRegistro semConf = FichaRegistro.load(FichaRegistro.inicial(Persona.EMPTY).save());
        eq("nbt sem confirmada", true, semConf.confirmada() == null);
    }

    static void bytebuf() {
        FichaPersonagem f = new FichaPersonagem("Ana", "30", "Uberlandia", "curar", "historia ção");
        FichaRegistro r = new FichaRegistro(f, f, false, 2, 1234567890123L, true);
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        r.write(buf);
        FichaRegistro volta = FichaRegistro.read(buf);
        eq("buf nome", "Ana", volta.rascunho().nome());
        eq("buf historia acentos", "historia ção", volta.confirmada().historia());
        eq("buf revisao", 1234567890123L, volta.revisao());
        eq("buf sobrou bytes", 0, buf.readableBytes());
    }

    static void orcamento() {
        TabletUpdateBudget b = new TabletUpdateBudget();
        int aceitos = 0;
        for (int i = 0; i < 20; i++) if (b.accept(100)) aceitos++;
        eq("20 aceitos", 20, aceitos);
        eq("21o recusado", false, b.accept(100));
        eq("recusado no fim da janela", false, b.accept(119));
        eq("janela nova libera", true, b.accept(120));
        for (int i = 0; i < 19; i++) b.accept(120);
        eq("fecha de novo em 20", false, b.accept(120));
        eq("tick voltou (relogio menor) reseta", true, b.accept(0));
    }

    static void clienteFluxo() {
        long[] relogio = {1000};
        java.util.List<TabletUpdatePayload> enviados = new java.util.ArrayList<>();
        TabletClientState st = new TabletClientState(sync(FichaRegistro.inicial(Persona.EMPTY), false), enviados::add, () -> relogio[0]);
        eq("nao esta sujo no inicio", false, st.dirty());
        st.edit(new FichaPersonagem("Ana", "30", "x", "y", "z"), 0);
        eq("sujo depois de digitar", true, st.dirty());
        st.tick();
        eq("nao envia antes de 750ms", 0, enviados.size());
        relogio[0] += 800;
        st.tick();
        eq("envia apos pausa", 1, enviados.size());
        TabletUpdatePayload env = enviados.get(0);
        eq("envio com revisao 0", 0L, env.revisao());
        eq("envio sem confirmar", false, env.confirmar());
        eq("pendente bloqueia outro envio", true, st.pending());
        st.tick();
        eq("nao envia duas vezes", 1, enviados.size());
        FichaRegistro salva = FichaRegistro.inicial(Persona.EMPTY).atualizar(env.respostas(), env.etapa(), 0, false);
        st.accept(sync(salva, false, env.requestId(), ""));
        eq("ack limpa pendente", false, st.pending());
        eq("ack limpa sujo", false, st.dirty());
        eq("sem erro", "", st.error());
        // confirmar
        st.flush(true);
        eq("confirmar envia", 2, enviados.size());
        eq("confirmar marcado", true, enviados.get(1).confirmar());
        FichaRegistro conf = salva.atualizar(env.respostas(), 5, salva.revisao(), true);
        st.accept(sync(conf, true, enviados.get(1).requestId(), ""));
        eq("depois de confirmar fica somente leitura", true, st.readOnly());
        eq("sem pendencia", false, st.pending());
    }

    static void clienteConflito() {
        long[] relogio = {0};
        java.util.List<TabletUpdatePayload> enviados = new java.util.ArrayList<>();
        TabletClientState st = new TabletClientState(sync(FichaRegistro.inicial(Persona.EMPTY), false), enviados::add, () -> relogio[0]);
        st.edit(new FichaPersonagem("Ana", "30", "x", "y", "z"), 0);
        st.flush(false);
        eq("enviou", 1, enviados.size());
        st.accept(sync(FichaRegistro.inicial(Persona.EMPTY), false, enviados.get(0).requestId(),
                "A ficha foi atualizada em outra sessão. Confira os dados do servidor antes de reenviar."));
        eq("conflito bloqueia", true, st.blocked());
        eq("texto local preservado", "Ana", st.respostas().nome());
        st.edit(new FichaPersonagem("Bia", "30", "x", "y", "z"), 0);
        eq("bloqueado nao edita", "Ana", st.respostas().nome());
        st.reloadServer();
        eq("recarregar desbloqueia", false, st.blocked());
        eq("recarregar troca pelo servidor", "", st.respostas().nome());
    }

    static void clienteRetry() {
        long[] relogio = {0};
        java.util.List<TabletUpdatePayload> enviados = new java.util.ArrayList<>();
        TabletClientState st = new TabletClientState(sync(FichaRegistro.inicial(Persona.EMPTY), false), enviados::add, () -> relogio[0]);
        st.edit(new FichaPersonagem("Ana", "30", "x", "y", "z"), 0);
        st.flush(false);
        int id = enviados.get(0).requestId();
        st.accept(new TabletSyncPayload(ALVO, id, FichaRegistro.inicial(Persona.EMPTY),
                false, false, true, "Aguarde um instante."));
        eq("erro temporario nao bloqueia", false, st.blocked());
        eq("erro na tela", "Aguarde um instante.", st.error());
        eq("texto local mantido", "Ana", st.respostas().nome());
        relogio[0] += 100;
        st.tick();
        eq("nao reenvia antes de 250ms", 1, enviados.size());
        relogio[0] += 200;
        st.tick();
        eq("reenvia depois de 250ms", 2, enviados.size());
        eq("reenvio preserva texto", "Ana", enviados.get(1).respostas().nome());
    }

    // ---- apoio
    static TabletSyncPayload sync(FichaRegistro r, boolean readOnly) { return sync(r, readOnly, -1, ""); }
    static final UUID ALVO = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static TabletSyncPayload sync(FichaRegistro r, boolean readOnly, int id, String erro) {
        return new TabletSyncPayload(ALVO, id, r, readOnly, false, false, erro);
    }

    static void eq(String nome, Object esperado, Object obtido) {
        boolean bate = esperado == null ? obtido == null : esperado.equals(obtido);
        if (bate) ok++; else { falhas++; System.out.println("  FALHOU: " + nome + " (esperado " + esperado + ", veio " + obtido + ")"); }
    }

    static void erro(String nome, Runnable acao, Class<? extends Throwable> tipo) {
        try { acao.run(); falhas++; System.out.println("  FALHOU: " + nome + " (não lançou)"); }
        catch (Throwable t) {
            if (tipo.isInstance(t)) ok++;
            else { falhas++; System.out.println("  FALHOU: " + nome + " (lançou " + t.getClass().getSimpleName() + ")"); }
        }
    }
}
