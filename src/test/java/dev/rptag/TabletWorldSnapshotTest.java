package dev.rptag;

import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** Integração opt-in: lê cópias do save de QA, sem alterar o mundo em uso. */
@EnabledIfEnvironmentVariable(named="RPTAG_QA_SNAPSHOT_DIR",matches=".+")
class TabletWorldSnapshotTest {
    private CompoundTag read(String name) throws Exception {
        return NbtIo.readCompressed(Path.of(System.getenv("RPTAG_QA_SNAPSHOT_DIR"),name),NbtAccounter.unlimitedHeap()).getCompound("data");
    }

    @Test void comandosLegadosNaoAlteramPersonaOuFichaConfirmada() throws Exception {
        CompoundTag before=read("rptag_states-before.dat"), after=read("rptag_states-after.dat");
        assertFalse(before.getList("TabletSheets",Tag.TAG_COMPOUND).isEmpty(),"O cenário precisa conter uma ficha existente");
        assertEquals(before.get("Personas"),after.get("Personas"));
        assertEquals(before.get("TabletSheets"),after.get("TabletSheets"));
    }

    @Test void dadosSalvosPeloServidorFazemRoundTripSemPerderAsFichas() throws Exception {
        CompoundTag saved=read("rptag_states-after.dat");
        RPWorldData loaded=RPWorldData.load(saved,null);
        RPWorldData again=RPWorldData.load(loaded.save(new CompoundTag(),null),null);
        var entries=saved.getList("TabletSheets",Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size();i++) {
            var id=entries.getCompound(i).getUUID("Id");
            FichaRegistro original=FichaRegistro.load(entries.getCompound(i).getCompound("Sheet"));
            assertEquals(original,loaded.getFicha(id));
            assertEquals(original,again.getFicha(id));
            assertEquals(loaded.getPersona(id),again.getPersona(id));
        }
    }
}
