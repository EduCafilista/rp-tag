package dev.rptag;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** Respostas privadas da ficha; a descrição pública de Persona permanece separada. */
public record FichaPersonagem(String nome, String idade, String origem, String habilidades, String historia) {
    public static final FichaPersonagem EMPTY = new FichaPersonagem("", "", "", "", "");
    public static final int[] LIMITES = {32, 5, 240, 600, 3000};

    public FichaPersonagem {
        java.util.Objects.requireNonNull(nome);
        java.util.Objects.requireNonNull(idade);
        java.util.Objects.requireNonNull(origem);
        java.util.Objects.requireNonNull(habilidades);
        java.util.Objects.requireNonNull(historia);
    }

    public String campo(int index) {
        return switch (index) {
            case 0 -> nome; case 1 -> idade; case 2 -> origem; case 3 -> habilidades; case 4 -> historia;
            default -> throw new IllegalArgumentException("Etapa inválida.");
        };
    }

    public FichaPersonagem comCampo(int index, String value) {
        return new FichaPersonagem(index == 0 ? value : nome, index == 1 ? value : idade,
                index == 2 ? value : origem, index == 3 ? value : habilidades, index == 4 ? value : historia);
    }

    public String validationError(boolean complete) {
        for (int i = 0; i < 5; i++) {
            String value = campo(i);
            if (value.length() > LIMITES[i]) return "O campo excede o limite de caracteres.";
            if (complete && value.isBlank()) return "Preencha todos os campos antes de confirmar.";
            final boolean multiline = i >= 2;
            if (value.codePoints().anyMatch(c -> c == '§' || Character.isISOControl(c) && !(multiline && c == '\n')))
                return "O texto contém caracteres não permitidos.";
        }
        if (!idade.isEmpty() && !idade.matches("[0-9]{1,5}")) return "Use somente números na idade.";
        if (complete && (Integer.parseInt(idade) < 1 || Integer.parseInt(idade) > 999))
            return "A idade deve estar entre 1 e 999 anos.";
        return "";
    }

    public FichaPersonagem normalizada() {
        return new FichaPersonagem(nome.strip(), Integer.toString(Integer.parseInt(idade)), origem.strip(), habilidades.strip(), historia.strip());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (int i = 0; i < 5; i++) tag.putString("Campo" + i, campo(i));
        return tag;
    }

    public static FichaPersonagem load(CompoundTag tag) {
        FichaPersonagem ficha = new FichaPersonagem(tag.getString("Campo0"), tag.getString("Campo1"),
                tag.getString("Campo2"), tag.getString("Campo3"), tag.getString("Campo4"));
        String error = ficha.validationError(false);
        if (!error.isEmpty()) throw new IllegalArgumentException(error);
        return ficha;
    }

    public void write(FriendlyByteBuf buf) {
        for (int i = 0; i < 5; i++) buf.writeUtf(campo(i), LIMITES[i]);
    }

    public static FichaPersonagem read(FriendlyByteBuf buf) {
        return new FichaPersonagem(buf.readUtf(32), buf.readUtf(5), buf.readUtf(240), buf.readUtf(600), buf.readUtf(3000));
    }
}
