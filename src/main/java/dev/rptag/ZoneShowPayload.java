package dev.rptag;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (4.0.0) Pacote S2C: mostra a "BARREIRA" de uma lorezone para UM admin.
 *
 * <p>O servidor so manda pra quem pediu (/lorezone mostrar ou com a Varinha
 * de Lorezone) — os players comuns nunca recebem, entao nunca veem nada.
 *
 * @param ligado true = mostrar · false = esconder
 * @param zona   id da zona (so pra exibir o nome na tela)
 * @param cx     centro X · cy centro Y · cz centro Z
 * @param raio   raio da esfera
 * @param minY   inicio da faixa vertical (onde o aviso dispara)
 * @param maxY   fim da faixa vertical
 * @param seg    quantos segundos fica na tela (0 = ate esconder)
 */
public record ZoneShowPayload(boolean ligado, String zona,
        double cx, double cy, double cz, double raio,
        double minY, double maxY, int seg) implements CustomPacketPayload {

    public static final Type<ZoneShowPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "zone_show"));

    /** (4.0.0) codec MANUAL: 9 campos passam do limite do StreamCodec.composite. */
    public static final StreamCodec<io.netty.buffer.ByteBuf, ZoneShowPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBoolean(p.ligado());
                        // ByteBuf puro: UTF-8 na mao (ByteBufCodecs.STRING_UTF8 usa a mesma coisa)
                        byte[] nome = p.zona().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        buf.writeInt(nome.length);
                        buf.writeBytes(nome);
                        buf.writeDouble(p.cx());
                        buf.writeDouble(p.cy());
                        buf.writeDouble(p.cz());
                        buf.writeDouble(p.raio());
                        buf.writeDouble(p.minY());
                        buf.writeDouble(p.maxY());
                        buf.writeInt(p.seg());
                    },
                    buf -> new ZoneShowPayload(
                            buf.readBoolean(),
                            lerTexto(buf),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readDouble(),
                            buf.readInt()));

    private static String lerTexto(io.netty.buffer.ByteBuf buf) {
        int n = buf.readInt();
        byte[] b = new byte[n];
        buf.readBytes(b);
        return new String(b, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
