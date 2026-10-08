package dev.rptag.client;

import dev.rptag.RPTagMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;

import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * (3.50.0) API DE MUSICA CUSTOM — o cliente toca qualquer arquivo
 * {@code .ogg} que o jogador colocar em {@code config/rptag/musicas/},
 * sem precisar de resource pack.
 *
 * <p>Como funciona: a Lore Zone manda o payload {@code LorezoneMusicPayload}
 * (nome do arquivo); aqui decodificamos o OGG Vorbis com o
 * {@link JOrbisAudioStream} do proprio jogo e enviamos o PCM para um source
 * OpenAL em LOOP ate chegar o STOP (ao sair da zona). O volume acompanha a
 * categoria MUSICA do jogador. A decodificacao roda numa thread auxiliar
 * (musica longa nao trava o jogo); os comandos OpenAL rodam no client thread
 * (mesmo contexto de audio do Minecraft).
 *
 * <p>Comando util pra pegar musica de video (ex.: YouTube):
 * {@code yt-dlp -x --audio-format vorbis "URL"} — renomeie o .ogg resultante
 * para um nome simples (letras/numeros/_) e jogue na pasta.
 */
public final class MusicaCustom {

    private static int fonte = -1;
    private static int buffer = -1;
    private static String atual = "";

    private MusicaCustom() {
    }

    /** Pasta onde o jogador coloca as musicas (.ogg). */
    public static Path pasta() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config").resolve("rptag").resolve("musicas");
    }

    /**
     * Toca {@code config/rptag/musicas/<nome>.ogg} em LOOP (para o anterior).
     * (3.52.0) se {@code nome} for um LINK http(s) direto de .ogg/.wav, baixa
     * com cache local e toca — os players nao precisam fazer NADA.
     */
    public static void tocar(String nome) {
        parar();
        if (nome.startsWith("http://") || nome.startsWith("https://")) {
            tocarUrl(nome);
            return;
        }
        Path arquivo = pasta().resolve(nome + ".ogg");
        if (!Files.isRegularFile(arquivo)) {
            RPTagMod.LOGGER.warn("RP Tag: musica custom '{}' nao encontrada (coloque o .ogg em {})",
                    nome, pasta());
            return;
        }
        tocarArquivo(arquivo, nome);
    }

    /** Pasta de CACHE dos audios baixados por LINK (3.52.0). */
    public static Path pastaCache() {
        return pasta().resolve("cache");
    }

    /**
     * (3.55.0) toca um arquivo da pasta pelo NOME COM EXTENSAO (ex.:
     * "musica-x.ogg") — usado pelo sync do servidor.
     */
    public static void tocarArquivoNome(String nomeArq) {
        if (nomeArq == null || !nomeArq.matches("[a-zA-Z0-9_-]+\\.(ogg|wav)")) {
            return;
        }
        Path arquivo = pasta().resolve(nomeArq);
        if (Files.isRegularFile(arquivo)) {
            tocarArquivo(arquivo, nomeArq);
        }
    }

    /**
     * (3.52.0) LINK DIRETO de audio (.ogg Vorbis ou .wav): baixa UMA vez pra
     * {@code config/rptag/musicas/cache/} e toca em LOOP. YouTube NAO funciona
     * direto (o video precisa virar .ogg — yt-dlp) — o aviso vai no comando.
     */
    private static void tocarUrl(String url) {
        String chave = hex16(url);
        boolean wav = url.toLowerCase().endsWith(".wav");
        Path arquivo = pastaCache().resolve(chave + (wav ? ".wav" : ".ogg"));
        Thread baixa = new Thread(() -> {
            try {
                if (!Files.isRegularFile(arquivo)) {
                    Files.createDirectories(pastaCache());
                    java.net.http.HttpClient cliente = java.net.http.HttpClient.newBuilder()
                            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
                    java.net.http.HttpRequest pedido = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(url))
                            .timeout(java.time.Duration.ofSeconds(20))
                            .header("User-Agent", "RPTag/3.52.0 (audio de lore zone)")
                            .build();
                    java.net.http.HttpResponse<byte[]> resp = cliente.send(pedido,
                            java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                    if (resp.statusCode() / 100 != 2) {
                        throw new IllegalStateException("HTTP " + resp.statusCode());
                    }
                    byte[] dados = resp.body();
                    if (dados == null || dados.length == 0 || dados.length > 32 * 1024 * 1024) {
                        throw new IllegalStateException("arquivo vazio ou maior que 32 MB");
                    }
                    Files.write(arquivo, dados);
                }
                final Path alvo = arquivo;
                Minecraft.getInstance().execute(() -> tocarArquivo(alvo, url));
            } catch (Exception e) {
                RPTagMod.LOGGER.warn("RP Tag: falha ao baixar o audio da zona ({})", url, e);
                Minecraft.getInstance().execute(() -> avisarNoChat(
                        "\u00a77[\u00a7dRP Tag\u00a77] \u00a7cfalhou ao baixar o audio da zona (link fora do ar?)"));
            }
        }, "RPTag-MusicaUrl");
        baixa.setDaemon(true);
        baixa.start();
    }

    /** Decodifica um arquivo LOCAL (.ogg Vorbis ou .wav) e manda pro OpenAL. */
    private static void tocarArquivo(Path arquivo, String rotulo) {
        Thread deco = new Thread(() -> {
            try {
                java.nio.ByteBuffer pcm;
                javax.sound.sampled.AudioFormat formato;
                if (arquivo.getFileName().toString().toLowerCase().endsWith(".wav")) {
                    // (3.52.0) WAV: o javax.sound do proprio Java decodifica
                    javax.sound.sampled.AudioInputStream ain =
                            javax.sound.sampled.AudioSystem.getAudioInputStream(arquivo.toFile());
                    formato = ain.getFormat();
                    pcm = java.nio.ByteBuffer.wrap(ain.readAllBytes());
                    ain.close();
                } else {
                    try (JOrbisAudioStream ogg = new JOrbisAudioStream(
                            new BufferedInputStream(Files.newInputStream(arquivo)))) {
                        pcm = ogg.readAll();
                        formato = ogg.getFormat();
                    }
                }
                final java.nio.ByteBuffer fpcm = pcm;
                final javax.sound.sampled.AudioFormat fmt = formato;
                Minecraft.getInstance().execute(() -> tocarPcm(fpcm, fmt, rotulo));
            } catch (Exception e) {
                RPTagMod.LOGGER.warn("RP Tag: falha ao decodificar audio '{}' (use .ogg Vorbis"
                        + " — yt-dlp -x --audio-format vorbis)", rotulo, e);
                Minecraft.getInstance().execute(() -> avisarNoChat(
                        "\u00a77[\u00a7dRP Tag\u00a77] \u00a7co audio da zona veio num formato que o jogo"
                                + " nao toca (precisa ser .ogg Vorbis ou .wav)"));
            }
        }, "RPTag-MusicaDec");
        deco.setDaemon(true);
        deco.start();
    }

    /** (3.52.0) mensagem curta no chat local (melhor esforco). */
    private static void avisarNoChat(String msg) {
        try {
            Minecraft.getInstance().gui.getChat().addMessage(
                    net.minecraft.network.chat.Component.literal(msg));
        } catch (Throwable ignored) {
        }
    }

    /** (3.52.0) chave de cache curta e estavel pra uma URL. */
    private static String hex16(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "audio" + Integer.toHexString(s.hashCode());
        }
    }

    /** (client thread) sobe o PCM pro OpenAL e inicia o loop. */
    private static synchronized void tocarPcm(java.nio.ByteBuffer pcm,
            javax.sound.sampled.AudioFormat formato, String nome) {
        try {
            int rate = (int) formato.getSampleRate();
            int alFormat = formato.getChannels() >= 2 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;
            buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, alFormat, pcm, rate);
            fonte = AL10.alGenSources();
            AL10.alSourcei(fonte, AL10.AL_BUFFER, buffer);
            AL10.alSourcei(fonte, AL10.AL_LOOPING, AL10.AL_TRUE);
            float vol = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MUSIC);
            AL10.alSourcef(fonte, AL10.AL_GAIN, 0.9F * vol);
            AL10.alSourcePlay(fonte);
            atual = nome;
            RPTagMod.LOGGER.info("RP Tag: tocando musica custom '{}' em loop (zona)", nome);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: falha ao tocar musica custom '{}'", nome, t);
            parar();
        }
    }

    /** Para a musica custom atual (e libera os recursos OpenAL). */
    public static synchronized void parar() {
        try {
            if (fonte >= 0) {
                AL10.alSourceStop(fonte);
                AL10.alSourcei(fonte, AL10.AL_BUFFER, 0);
                AL10.alDeleteSources(fonte);
            }
            if (buffer >= 0) {
                AL10.alDeleteBuffers(buffer);
            }
        } catch (Throwable ignored) {
            // audio e "melhor esforco": nunca quebra o jogo
        }
        fonte = -1;
        buffer = -1;
        atual = "";
    }

    /** @return nome da musica tocando agora ("" = nenhuma). */
    public static synchronized String tocando() {
        return atual;
    }
}
