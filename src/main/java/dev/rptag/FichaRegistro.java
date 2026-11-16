package dev.rptag;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record FichaRegistro(FichaPersonagem rascunho, FichaPersonagem confirmada,
        boolean edicaoLiberada, int etapa, long revisao, boolean tabletEntregue,
        String skinUrl, String skinModel) {
    public FichaRegistro {
        java.util.Objects.requireNonNull(rascunho);
        // (5.61.0) a skin salva e opcional, mas se vier tem que ser confiavel
        skinUrl = skinUrl == null ? "" : skinUrl;
        skinModel = skinModel == null ? "" : skinModel;
        if (!SkinFicha.urlValida(skinUrl) || !SkinFicha.modeloValido(skinModel))
            throw new IllegalArgumentException("Skin inv\u00e1lida.");
        if (etapa < 0 || etapa > 5 || revisao < 0) throw new IllegalArgumentException("Registro de ficha inválido.");
        String error = rascunho.validationError(false);
        if (!error.isEmpty()) throw new IllegalArgumentException(error);
        if (confirmada != null && !confirmada.validationError(true).isEmpty())
            throw new IllegalArgumentException("Ficha confirmada inválida.");
    }

    public static FichaRegistro inicial(Persona persona) {
        // Nomes aceitos pelo cadastro antigo não podem impedir login ou abertura do tablet.
        String text = persona.name().replaceAll("(?i)§[0-9a-fk-orx]", "");
        StringBuilder safe = new StringBuilder();
        text.codePoints().filter(c -> c != '§' && !Character.isISOControl(c)).forEach(c -> {
            if (safe.length() + Character.charCount(c) <= 32) safe.appendCodePoint(c);
        });
        return new FichaRegistro(new FichaPersonagem(safe.toString(), persona.age() > 0 && persona.age() <= 999 ? Integer.toString(persona.age()) : "", "", "", ""),
                null, false, 0, 0, false, "", "");
    }

    public boolean podeEditar() { return confirmada == null || edicaoLiberada; }

    public FichaRegistro atualizar(FichaPersonagem respostas, int step, long expected, boolean confirmar) {
        if (expected != revisao) throw new IllegalStateException("A ficha foi atualizada em outra sessão. Confira os dados do servidor antes de reenviar.");
        if (!podeEditar()) throw new IllegalStateException("Edição bloqueada. Solicite liberação a um administrador.");
        String error = respostas.validationError(confirmar);
        if (!error.isEmpty()) throw new IllegalArgumentException(error);
        FichaPersonagem next = confirmar ? respostas.normalizada() : respostas;
        return new FichaRegistro(next, confirmar ? next : confirmada, !confirmar && edicaoLiberada,
                step, Math.addExact(revisao, 1), tabletEntregue, skinUrl, skinModel);
    }

    public FichaRegistro liberarEdicao() {
        if (podeEditar()) return this;
        return new FichaRegistro(confirmada, confirmada, true, 0, Math.addExact(revisao, 1), tabletEntregue,
                skinUrl, skinModel);
    }

    public FichaRegistro comTabletEntregue() {
        return new FichaRegistro(rascunho, confirmada, edicaoLiberada, etapa, revisao, true, skinUrl, skinModel);
    }

    /**
     * (5.61.0) GUARDA A SKIN. Nao mexe na revisao de proposito: a skin nao e dado do formulario,
     * entao trocar de skin nao invalida o texto que o jogador esta escrevendo.
     */
    public FichaRegistro comSkin(String url, String modelo) {
        String u = url == null ? "" : url;
        String m = modelo == null ? "" : modelo;
        if (!SkinFicha.urlValida(u) || !SkinFicha.modeloValido(m)) return this;
        if (u.equals(skinUrl) && m.equals(skinModel)) return this;
        return new FichaRegistro(rascunho, confirmada, edicaoLiberada, etapa, revisao, tabletEntregue, u, m);
    }

    /** A ficha tem skin salva? */
    public boolean temSkin() {
        return !skinUrl.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Draft", rascunho.save());
        if (confirmada != null) tag.put("Confirmed", confirmada.save());
        tag.putBoolean("Editable", edicaoLiberada);
        tag.putInt("Step", etapa);
        tag.putLong("Revision", revisao);
        tag.putBoolean("Delivered", tabletEntregue);
        tag.putString("SkinUrl", skinUrl);
        tag.putString("SkinModel", skinModel);
        return tag;
    }

    public static FichaRegistro load(CompoundTag tag) {
        return new FichaRegistro(FichaPersonagem.load(tag.getCompound("Draft")),
                tag.contains("Confirmed") ? FichaPersonagem.load(tag.getCompound("Confirmed")) : null,
                tag.getBoolean("Editable"), tag.getInt("Step"), tag.getLong("Revision"), tag.getBoolean("Delivered"),
                tag.getString("SkinUrl"), tag.getString("SkinModel"));
    }

    public void write(FriendlyByteBuf buf) {
        rascunho.write(buf);
        buf.writeBoolean(confirmada != null);
        if (confirmada != null) confirmada.write(buf);
        buf.writeBoolean(edicaoLiberada).writeVarInt(etapa).writeVarLong(revisao).writeBoolean(tabletEntregue);
        buf.writeUtf(skinUrl, SkinFicha.URL_MAX).writeUtf(skinModel, 16);
    }

    public static FichaRegistro read(FriendlyByteBuf buf) {
        FichaPersonagem draft = FichaPersonagem.read(buf);
        FichaPersonagem confirmed = buf.readBoolean() ? FichaPersonagem.read(buf) : null;
        return new FichaRegistro(draft, confirmed, buf.readBoolean(), buf.readVarInt(), buf.readVarLong(), buf.readBoolean(),
                buf.readUtf(SkinFicha.URL_MAX), buf.readUtf(16));
    }
}
