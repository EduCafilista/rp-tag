package dev.rptag.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

/**
 * (5.7.2) Cache do cliente com o TÍTULO de cada jogador, recebido por
 * {@code TituloSyncPayload}. Serve pro NAMETAG acima da cabeça — o TAB e o
 * chat o próprio servidor já monta com título.
 *
 * <p>Regra da casa: qualquer erro aqui só NÃO desenha o título; nunca derruba
 * o jogo.
 */
public final class ClientTitulos {

    /** Um título: etiqueta colorida na frente + texto pessoal depois do nome. */
    public record Titulo(String etiqueta, int cor, String personalizado, int corPessoal) {

        public boolean vazio() {
            return (etiqueta == null || etiqueta.isEmpty())
                    && (personalizado == null || personalizado.isEmpty());
        }
    }

    private static final Map<UUID, Titulo> TITULOS = new ConcurrentHashMap<>();

    private ClientTitulos() {
    }

    public static void set(UUID id, String etiqueta, int cor, String personalizado, int corPessoal) {
        Titulo t = new Titulo(etiqueta == null ? "" : etiqueta, cor,
                personalizado == null ? "" : personalizado,
                corPessoal < 0 ? 0xFFFFFF : corPessoal);
        if (t.vazio()) {
            TITULOS.remove(id);
        } else {
            TITULOS.put(id, t);
        }
    }

    public static Titulo get(UUID id) {
        return TITULOS.get(id);
    }

    /** Limpa tudo (troca de servidor/mundo: nada de título do mundo anterior). */
    public static void limpar() {
        TITULOS.clear();
    }

    /** Envolve o nome do nametag: {@code [Sagrado] Nome [O Ferreiro]}. */
    public static Component aplicar(UUID id, Component nome) {
        try {
            Titulo t = TITULOS.get(id);
            if (t == null || t.vazio()) {
                return nome;
            }
            MutableComponent out = Component.empty();
            if (!t.etiqueta().isEmpty()) {
                out.append(Component.literal("[" + t.etiqueta() + "] ")
                        .withStyle(s -> s.withColor(TextColor.fromRgb(t.cor()))));
            }
            out.append(nome);
            if (!t.personalizado().isEmpty()) {
                // (5.9.0) cor do título pessoal escolhida no /titulo cor pessoal
                out.append(Component.literal(" [" + t.personalizado() + "]")
                        .withStyle(s -> s.withColor(TextColor.fromRgb(t.corPessoal()))));
            }
            return out;
        } catch (Throwable erro) {
            return nome;
        }
    }
}
