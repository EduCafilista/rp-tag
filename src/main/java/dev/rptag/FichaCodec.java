package dev.rptag;

import net.minecraft.network.FriendlyByteBuf;

/** (4.8.0) como a ficha viaja pela rede (textos limitados, persona em 6 bytes). */
public final class FichaCodec {

    public static final int NOME = 32, IDADE = 24, ORIGEM = 48, OFICIO = 48,
            HABILIDADES = 160, HISTORIA = 400;

    private FichaCodec() {
    }

    public static void escrever(FriendlyByteBuf buf, FichaRp f) {
        buf.writeUtf(corta(f.nome, NOME), NOME * 4);
        buf.writeUtf(corta(f.idade, IDADE), IDADE * 4);
        buf.writeUtf(corta(f.origem, ORIGEM), ORIGEM * 4);
        buf.writeUtf(corta(f.oficio, OFICIO), OFICIO * 4);
        buf.writeUtf(corta(f.habilidades, HABILIDADES), HABILIDADES * 4);
        buf.writeUtf(corta(f.historia, HISTORIA), HISTORIA * 4);
        for (int i = 0; i < 6; i++) {
            buf.writeByte(Math.max(0, Math.min(100, f.persona[i])));
        }
        buf.writeBoolean(f.concluida);
        buf.writeByte(Math.max(0, Math.min(FichaRp.CAP_RESUMO, f.pagina)));
    }

    public static FichaRp ler(FriendlyByteBuf buf) {
        FichaRp f = new FichaRp();
        f.nome = buf.readUtf(NOME * 4);
        f.idade = buf.readUtf(IDADE * 4);
        f.origem = buf.readUtf(ORIGEM * 4);
        f.oficio = buf.readUtf(OFICIO * 4);
        f.habilidades = buf.readUtf(HABILIDADES * 4);
        f.historia = buf.readUtf(HISTORIA * 4);
        for (int i = 0; i < 6; i++) {
            f.persona[i] = buf.readByte() & 0xFF;
        }
        f.concluida = buf.readBoolean();
        f.pagina = buf.readByte() & 0xFF;
        return f;
    }

    private static String corta(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.length() > max ? s.substring(0, max) : s;
        // o writeUtf do 1.21 nao aceita carácter solto de par surrogado
        if (!t.isEmpty() && Character.isHighSurrogate(t.charAt(t.length() - 1))) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }
}
