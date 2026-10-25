package dev.rptag;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FichaRegistroTest {
    private static final FichaPersonagem ARI = new FichaPersonagem("Ari Valen", "24", "Porto das Brumas", "Cartografia", "Cresceu entre mapas e barcos.");

    @Test void validaLimitesSemExigirFichaCompletaNoRascunho() {
        assertEquals("", FichaPersonagem.EMPTY.validationError(false));
        assertFalse(FichaPersonagem.EMPTY.validationError(true).isEmpty());
        assertEquals("", ARI.validationError(true));
        assertFalse(new FichaPersonagem("x".repeat(33), "24", "Porto", "Mapas", "História").validationError(false).isEmpty());
        assertFalse(new FichaPersonagem("Ari", "1000", "Porto", "Mapas", "História").validationError(true).isEmpty());
        assertFalse(new FichaPersonagem("Ari", "0", "Porto", "Mapas", "História").validationError(true).isEmpty());
        assertFalse(new FichaPersonagem("Ari\u0000", "24", "Porto", "Mapas", "História").validationError(false).isEmpty());
        assertFalse(new FichaPersonagem("§cAri", "24", "Porto", "Mapas", "História").validationError(false).isEmpty());
        assertEquals("", new FichaPersonagem("João", "999", "Évora", "Navegação", "Primeira linha\nSegunda linha").validationError(true));
    }

    @Test void confirmarBloqueiaERevisaoAntigaNaoSobrescreve() {
        FichaRegistro initial = FichaRegistro.inicial(Persona.EMPTY);
        FichaRegistro saved = initial.atualizar(ARI, 5, 0, true);
        assertFalse(saved.podeEditar());
        assertEquals(ARI, saved.confirmada());
        assertEquals(1, saved.revisao());
        assertThrows(IllegalStateException.class, () -> saved.atualizar(ARI, 1, 0, false));
        assertThrows(IllegalStateException.class, () -> saved.atualizar(ARI, 1, 1, false));
    }

    @Test void liberarPreservaFichaAnteriorEnquantoEdita() {
        FichaRegistro locked = FichaRegistro.inicial(Persona.EMPTY).atualizar(ARI, 5, 0, true);
        FichaRegistro unlocked = locked.liberarEdicao();
        FichaPersonagem edited = new FichaPersonagem("Novo nome", "25", "Outro porto", "Mapas", "Outra história");
        FichaRegistro draft = unlocked.atualizar(edited, 4, unlocked.revisao(), false);
        assertTrue(draft.podeEditar());
        assertEquals(ARI, draft.confirmada());
        assertEquals(edited, draft.rascunho());
        assertThrows(IllegalStateException.class, () -> unlocked.atualizar(edited, 1, locked.revisao(), false));
    }

    @Test void nbtPreservaRascunhoConfirmacaoEntregaERevisao() {
        FichaRegistro registro = FichaRegistro.inicial(new Persona("Nome antigo", 150, "Descrição pública"))
                .atualizar(ARI, 5, 0, true).liberarEdicao().comTabletEntregue();
        assertEquals(registro, FichaRegistro.load(registro.save()));
        assertEquals("Nome antigo", FichaRegistro.inicial(new Persona("Nome antigo", 150, "Descrição pública")).rascunho().nome());
    }

    @Test void mundoAntigoPreservaPersonaEEstado() {
        java.util.UUID id = java.util.UUID.randomUUID();
        RPWorldData old = new RPWorldData();
        old.setPersona(id, new Persona("João", 30, "Descrição"));
        old.setInRp(id, true);
        CompoundTag tag = old.save(new CompoundTag(), null);
        tag.remove("TabletSheets");
        RPWorldData loaded = RPWorldData.load(tag, null);
        assertEquals(old.getPersona(id), loaded.getPersona(id));
        assertTrue(loaded.isInRp(id));
        assertEquals("João", loaded.getFicha(id).rascunho().nome());
        assertNull(loaded.getFicha(id).confirmada());
    }

    @Test void nomeLegadoComFormatacaoNaoImpedeAbrirTablet() {
        Persona legacy = new Persona("§aAri\u0000", 24, "Descrição pública preservada");
        FichaRegistro registro = assertDoesNotThrow(() -> FichaRegistro.inicial(legacy));
        assertEquals("Ari", registro.rascunho().nome());
        assertEquals("§aAri\u0000", legacy.name());
    }

    @Test void entradaNovaInvalidaNaoApagaOutrasSecoes() {
        CompoundTag old = new CompoundTag();
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        list.add(new CompoundTag());
        old.put("TabletSheets", list);
        assertDoesNotThrow(() -> RPWorldData.load(old, null));
    }

    @Test void limitesDeTodasAsPerguntasEIdadeSaoRespeitados() {
        FichaPersonagem maximum = new FichaPersonagem("N".repeat(32), "999", "O".repeat(240), "H".repeat(600), "S".repeat(3000));
        assertEquals("", maximum.validationError(true));
        for (int field : new int[]{0,2,3,4}) {
            assertFalse(maximum.comCampo(field, maximum.campo(field)+"x").validationError(false).isEmpty());
        }
        for (String age : new String[]{"", "0", "1000", "-1", "2.5", "abc", "100000"})
            assertFalse(ARI.comCampo(1,age).validationError(true).isEmpty(), age);
    }

    @Test void salvarReabrirCadaPerguntaENormalizarConfirmacaoPreservaConteudo() {
        FichaRegistro registro = FichaRegistro.inicial(Persona.EMPTY);
        FichaPersonagem draft = FichaPersonagem.EMPTY;
        String[] answers = {"  João da Névoa  ", "0024", "São Tomé\nPorto antigo", "Navegação, mapas e cura", "Primeiro capítulo.\nSegundo capítulo: memória e ação."};
        for (int step=0;step<5;step++) {
            draft=draft.comCampo(step,answers[step]);
            registro=registro.atualizar(draft,step,registro.revisao(),false);
            registro=FichaRegistro.load(registro.save());
            assertEquals(draft,registro.rascunho());
            assertEquals(step,registro.etapa());
        }
        registro=registro.atualizar(draft,5,registro.revisao(),true);
        assertEquals("João da Névoa",registro.confirmada().nome());
        assertEquals("24",registro.confirmada().idade());
        assertEquals(answers[4],registro.confirmada().historia());
        assertFalse(registro.podeEditar());
    }
}
