package dev.rptag;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (5.4.0) PACOTES DO EVENTO NEPTUNE — todos S2C (o servidor manda, o cliente
 * só desenha). Ficam aninhados aqui pra não encher o projeto de arquivo de
 * 20 linhas; o registro é em {@link ModNetworking} (canal v9).
 *
 * <ul>
 *   <li>{@code terremoto} — liga/desliga o tremor e diz a intensidade;</li>
 *   <li>{@code saida} — a animação VERDE de quem está saindo do servidor;</li>
 *   <li>{@code susto} — o bicho pulando na tela (face + flash vermelho);</li>
 *   <li>{@code aura} — o jogador é (ou não) o DEUS do vazio;</li>
 *   <li>{@code nebulosa} — a tela de blur/nebula do vazio quando apaga alguém.</li>
 * </ul>
 */
public final class NeptunePackets {

    private NeptunePackets() {
    }

    private static ResourceLocation id(String nome) {
        return ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, nome);
    }

    /** TERREMOTO: ativo/intensidade (0 = desligado) + segundos + pulso travado. */
    public record Terremoto(boolean ativo, float forca, int segundos, boolean travado)
            implements CustomPacketPayload {
        public static final Type<Terremoto> TYPE = new Type<>(id("neptune_terremoto"));
        public static final StreamCodec<ByteBuf, Terremoto> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.BOOL.encode(buf, p.ativo());
                    ByteBufCodecs.FLOAT.encode(buf, p.forca());
                    ByteBufCodecs.VAR_INT.encode(buf, p.segundos());
                    ByteBufCodecs.BOOL.encode(buf, p.travado());
                },
                buf -> new Terremoto(ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** SAÍDA DO SERVIDOR: a pessoa some nas sombras (segundos) + o nome. */
    public record Saida(int segundos, String nome) implements CustomPacketPayload {
        public static final Type<Saida> TYPE = new Type<>(id("neptune_saida"));
        public static final StreamCodec<ByteBuf, Saida> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, p.segundos());
                    ByteBufCodecs.STRING_UTF8.encode(buf, p.nome());
                },
                buf -> new Saida(ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** SUSTO: o bicho na tela (intensidade 0..1 = quão perto ele pulou). */
    public record Susto(float intensidade, long semente) implements CustomPacketPayload {
        public static final Type<Susto> TYPE = new Type<>(id("neptune_susto"));
        public static final StreamCodec<ByteBuf, Susto> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.FLOAT.encode(buf, p.intensidade());
                    ByteBufCodecs.VAR_LONG.encode(buf, p.semente());
                },
                buf -> new Susto(ByteBufCodecs.FLOAT.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** AURA: sou (ou não) o DEUS do vazio; trilha = pode tocar a trilha. */
    /**
     * (5.7.4) AURA DO VAZIO — agora carrega QUEM é o vazio.
     *
     * <p>Antes era só {@code (deus, trilha)} e ia só pro próprio jogador; os
     * outros não sabiam de quem era a aura, então não dava pra desenhar os raios
     * 3D em volta dele. Agora vai pra TODOS: {@code alvo} é o UUID do vazio
     * (vazio/zero = ninguém), {@code euSou} diz se este cliente é o próprio
     * (ele vê a trilha e os raios mais discretos) e {@code trilha} o som.
     */
    public record Aura(java.util.UUID alvo, boolean euSou, boolean trilha) implements CustomPacketPayload {
        public static final Type<Aura> TYPE = new Type<>(id("neptune_aura"));
        public static final StreamCodec<ByteBuf, Aura> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    net.minecraft.core.UUIDUtil.STREAM_CODEC.encode(buf,
                            p.alvo() == null ? new java.util.UUID(0L, 0L) : p.alvo());
                    ByteBufCodecs.BOOL.encode(buf, p.euSou());
                    ByteBufCodecs.BOOL.encode(buf, p.trilha());
                },
                buf -> new Aura(net.minecraft.core.UUIDUtil.STREAM_CODEC.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf)));

        /** atalho: aura desligada. */
        public static Aura desligada() {
            return new Aura(new java.util.UUID(0L, 0L), false, false);
        }

        public boolean ligada() {
            return alvo() != null && !(alvo().getMostSignificantBits() == 0L
                    && alvo().getLeastSignificantBits() == 0L);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** NEBULOSA: o blur de espaço do "vazio" (segundos, 0 = desliga). */
    public record Nebulosa(int segundos, boolean forte) implements CustomPacketPayload {
        public static final Type<Nebulosa> TYPE = new Type<>(id("neptune_nebulosa"));
        public static final StreamCodec<ByteBuf, Nebulosa> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, p.segundos());
                    ByteBufCodecs.BOOL.encode(buf, p.forte());
                },
                buf -> new Nebulosa(ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }


    /**
     * (5.6.0) CENA do terremoto — o drama na tela.
     * <ul>
     *   <li>tipo {@link #CENA_RACHADURA} (0): o chão RACHANDO na tela (teia de
     *       rachaduras crescendo das bordas), "algo se abrindo";</li>
     *   <li>tipo {@link #CENA_QUEDA} (1): o jogador CAIU na fenda — poeira
     *       subindo da base da tela, câmera tombada, vinheta de terra;</li>
     *   <li>tipo {@link #CENA_ESTOURO} (2): o tranco inicial (flash +
     *       onda de choque).</li>
     * </ul>
     */
    public record Cena(int tipo, float forca, int ms, long semente) implements CustomPacketPayload {
        public static final int CENA_RACHADURA = 0;
        public static final int CENA_QUEDA = 1;
        public static final int CENA_ESTOURO = 2;
        /** (5.7.6) o jogador está sendo EXILADO (escuridão engolindo a tela). */
        public static final int CENA_EXILIO = 3;
        /** (5.7.6) o jogador está VOLTANDO do exílio (escuridão se abrindo). */
        public static final int CENA_VOLTA = 4;
        /** (5.38.0) ENTRAR NO VAZIO: o buraco negro (geometria na tela, sem partícula). */
        public static final int CENA_BURACO_NEGRO = 5;
        /** (5.38.0) A ESCURIDÃO ABRINDO depois do teleporte (a íris). */
        public static final int CENA_IRIS = 6;

        public static final Type<Cena> TYPE = new Type<>(id("neptune_cena"));
        public static final StreamCodec<ByteBuf, Cena> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.VAR_INT.encode(buf, p.tipo());
                    ByteBufCodecs.FLOAT.encode(buf, p.forca());
                    ByteBufCodecs.VAR_INT.encode(buf, p.ms());
                    ByteBufCodecs.VAR_LONG.encode(buf, p.semente());
                },
                buf -> new Cena(ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                        ByteBufCodecs.VAR_LONG.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * (5.7.7) SAÍDA NO MUNDO: glifos verdes girando EM VOLTA do jogador.
     *
     * <p>O dono explicou o que queria por "animação": <i>"uns efeitos em volta
     * do player, digitalizando códigos em números verdes por exemplo, como se
     * estivessem sendo computados para sair do servidor"</i>. Então o servidor
     * manda a POSIÇÃO de quem está saindo e o cliente desenha os glifos em 3D
     * ali (todo mundo vê), além do bonequinho de tela que já existia.
     */
    public record Codigos(double x, double y, double z, int ms, String nome)
            implements CustomPacketPayload {
        public static final Type<Codigos> TYPE = new Type<>(id("neptune_codigos"));
        public static final StreamCodec<ByteBuf, Codigos> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.DOUBLE.encode(buf, p.x());
                    ByteBufCodecs.DOUBLE.encode(buf, p.y());
                    ByteBufCodecs.DOUBLE.encode(buf, p.z());
                    ByteBufCodecs.VAR_INT.encode(buf, p.ms());
                    ByteBufCodecs.STRING_UTF8.encode(buf, p.nome());
                },
                buf -> new Codigos(ByteBufCodecs.DOUBLE.decode(buf),
                        ByteBufCodecs.DOUBLE.decode(buf), ByteBufCodecs.DOUBLE.decode(buf),
                        ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Lista só pra o registro ficar legível. */
    public static List<CustomPacketPayload.Type<? extends CustomPacketPayload>> todos() {
        List<CustomPacketPayload.Type<? extends CustomPacketPayload>> l = new ArrayList<>();
        l.add(Terremoto.TYPE);
        l.add(Saida.TYPE);
        l.add(Susto.TYPE);
        l.add(Aura.TYPE);
        l.add(Nebulosa.TYPE);
        l.add(Cena.TYPE);
        l.add(Codigos.TYPE);
        return l;
    }
}
