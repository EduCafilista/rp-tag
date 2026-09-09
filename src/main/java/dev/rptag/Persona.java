package dev.rptag;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Identidade RP de um jogador (o "personagem").
 *
 * <p>Quando definida, o nome do personagem substitui o nick da conta
 * no nametag, no chat e na TAB.
 *
 * @param name nome do personagem (ex.: "Lord Aldric")
 * @param age  idade do personagem (0 = nao definida)
 * @param desc descricao curta do personagem (vazia = nao definida)
 */
public record Persona(String name, int age, String desc) {

    public Persona {
        if (name == null) {
            name = "";
        }
        if (desc == null) {
            desc = "";
        }
    }

    public static final Persona EMPTY = new Persona("", 0, "");

    public boolean isEmpty() {
        return name.isBlank();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putInt("Age", age);
        tag.putString("Desc", desc);
        return tag;
    }

    public static Persona load(CompoundTag tag) {
        return new Persona(
                tag.getString("Name"),
                tag.getInt("Age"),
                tag.getString("Desc"));
    }
}
