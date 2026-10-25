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

    @Test void pacotesDeAtualizacaoERespostaFazemRoundTripCompleto() {
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
        FichaPersonagem answers=new FichaPersonagem("João","24","São Tomé","Cartografia","Uma história\nOutra linha");
        try {
            TabletUpdatePayload update=new TabletUpdatePayload(4,17,answers,5,true);
            TabletUpdatePayload.STREAM_CODEC.encode(buf,update);
            assertEquals(update,TabletUpdatePayload.STREAM_CODEC.decode(buf));
            FichaRegistro sheet=FichaRegistro.inicial(Persona.EMPTY).atualizar(answers,4,0,false);
            TabletSyncPayload sync=new TabletSyncPayload(java.util.UUID.randomUUID(),17,sheet,false,false,true,"Aguarde");
            TabletSyncPayload.STREAM_CODEC.encode(buf,sync);
            assertEquals(sync,TabletSyncPayload.STREAM_CODEC.decode(buf));
            assertEquals(0,buf.readableBytes());
        } finally { buf.release(); }
    }
}
