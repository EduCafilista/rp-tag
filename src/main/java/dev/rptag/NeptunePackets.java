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
    public record Aura(boolean deus, boolean trilha) implements CustomPacketPayload {
        public static final Type<Aura> TYPE = new Type<>(id("neptune_aura"));
        public static final StreamCodec<ByteBuf, Aura> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    ByteBufCodecs.BOOL.encode(buf, p.deus());
                    ByteBufCodecs.BOOL.encode(buf, p.trilha());
                },
                buf -> new Aura(ByteBufCodecs.BOOL.decode(buf),
                        ByteBufCodecs.BOOL.decode(buf)));

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

    /** Lista só pra o registro ficar legível. */
    public static List<CustomPacketPayload.Type<? extends CustomPacketPayload>> todos() {
        List<CustomPacketPayload.Type<? extends CustomPacketPayload>> l = new ArrayList<>();
        l.add(Terremoto.TYPE);
        l.add(Saida.TYPE);
        l.add(Susto.TYPE);
        l.add(Aura.TYPE);
        l.add(Nebulosa.TYPE);
        l.add(Cena.TYPE);
        return l;
    }
}
