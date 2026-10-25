package dev.rptag;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TabletPayloadTest {
    @Test void respostasFazemRoundTripSemPerderAcentosOuQuebras() {
        FichaPersonagem ficha = new FichaPersonagem("João", "150", "Évora", "Navegação", "Uma linha\nOutra linha");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try { ficha.write(buf); assertEquals(ficha, FichaPersonagem.read(buf)); }
        finally { buf.release(); }
    }

    @Test void textoExcessivoERecusadoNaDecodificacao() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeUtf("x".repeat(33));
            assertThrows(io.netty.handler.codec.DecoderException.class, () -> FichaPersonagem.read(buf));
        } finally { buf.release(); }
    }
}
