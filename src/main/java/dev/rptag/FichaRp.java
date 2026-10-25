package dev.rptag;

import net.minecraft.nbt.CompoundTag;

/**
 * (4.8.0) FICHA RP — o personagem completo do jogador, criado no TABLET.
 *
 * <p>O que já existia ({@link Persona}) continua sendo o "resumo" que aparece no
 * nametag/chat/TAB: nome, idade e descrição. A ficha guarda TUDO o que o jogador
 * escreveu no tablet, e mantém a persona sincronizada (nome/idade/descrição saem
 * daqui). Assim o tablet não duplica o sistema — ele alimenta o que já existe.
 *
 * <p>Os eixos de personalidade são 0..100: 0 = o lado da esquerda do print,
 * 100 = o lado da direita (ex.: Prudente 0 ... Corajoso 100).
 */
public final class FichaRp {

    /** capítulos do tablet (a última é o resumo). */
    public static final int CAP_NOME = 0, CAP_IDADE = 1, CAP_ORIGEM = 2, CAP_OFICIO = 3,
            CAP_HABILIDADES = 4, CAP_HISTORIA = 5, CAP_PERSONALIDADE = 6, CAP_RESUMO = 7;
    public static final int CAPS = 8;

    /** rótulos dos eixos (esquerda, direita). */
    public static final String[][] EIXOS = {
            {"Prudente", "Corajoso"},
            {"Pragmático", "Empático"},
            {"Espontâneo", "Disciplinado"},
            {"Tradicional", "Curioso"},
            {"Reservado", "Sociável"},
            {"Contente", "Ambicioso"},
    };

    public String nome = "";
    public String idade = "";
    /** "de onde você veio" (a pergunta que substitui raça). */
    public String origem = "";
    /** o que fazia / faz. */
    public String oficio = "";
    public String habilidades = "";
    public String historia = "";
    public final int[] persona = {50, 50, 50, 50, 50, 50};
    public boolean concluida;
    public int pagina;

    public boolean vazia() {
        return nome == null || nome.isBlank();
    }

    /** "Elfo do norte" ou "—" quando não preencheu. */
    public String ou(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    // ------------------------------------------------------------------ NBT
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Nome", nz(nome));
        t.putString("Idade", nz(idade));
        t.putString("Origem", nz(origem));
        t.putString("Oficio", nz(oficio));
        t.putString("Habilidades", nz(habilidades));
        t.putString("Historia", nz(historia));
        t.putIntArray("Persona", persona.clone());
        t.putBoolean("Concluida", concluida);
        t.putInt("Pagina", pagina);
        return t;
    }

    public static FichaRp load(CompoundTag t) {
        FichaRp f = new FichaRp();
        f.nome = t.getString("Nome");
        f.idade = t.getString("Idade");
        f.origem = t.getString("Origem");
        f.oficio = t.getString("Oficio");
        f.habilidades = t.getString("Habilidades");
        f.historia = t.getString("Historia");
        int[] p = t.getIntArray("Persona");
        for (int i = 0; i < 6; i++) {
            f.persona[i] = (p.length == 6) ? Math.max(0, Math.min(100, p[i])) : 50;
        }
        f.concluida = t.getBoolean("Concluida");
        f.pagina = Math.max(0, Math.min(CAP_RESUMO, t.getInt("Pagina")));
        return f;
    }

    public FichaRp copia() {
        return load(save());
    }

    /**
     * (4.8.2) "Corajoso 75 · Empático 25" — só os TRÊS eixos mais marcados.
     *
     * <p>Antes listava os seis, e no resumo do tablet a linha virava um texto
     * gigante que saía do painel. Três cabem em duas linhas com folga.
     */
    public String personaResumo() {
        int[] idx = {0, 1, 2, 3, 4, 5};
        for (int i = 0; i < 6; i++) {
            for (int j = i + 1; j < 6; j++) {
                if (Math.abs(persona[idx[j]] - 50) > Math.abs(persona[idx[i]] - 50)) {
                    int tmp = idx[i];
                    idx[i] = idx[j];
                    idx[j] = tmp;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < 3; k++) {
            int i = idx[k];
            int v = persona[i];
            if (Math.abs(v - 50) < 8) {
                break;
            }
            String lado = v > 50 ? EIXOS[i][1] : EIXOS[i][0];
            int forca = v > 50 ? v : 100 - v;
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(lado).append(' ').append(forca);
        }
        return sb.length() == 0 ? "equilibrado" : sb.toString();
    }

    /** idade em número (0 se o jogador escreveu algo que não é número). */
    public int idadeNumero() {
        try {
            return Integer.parseInt(idade.trim().replace(" anos", "").replace(" ano", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    /** descrição curta que vai para o nametag (origem + ofício). */
    public String descricaoCurta() {
        StringBuilder sb = new StringBuilder();
        if (oficio != null && !oficio.isBlank()) {
            sb.append(oficio.trim());
        }
        if (origem != null && !origem.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(origem.trim());
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
