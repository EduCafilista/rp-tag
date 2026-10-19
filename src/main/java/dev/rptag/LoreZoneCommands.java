package dev.rptag;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /lorezone} — comandos de administrador (permissao 2) para
 * criar regioes que contam a historia do mundo.
 *
 * <ul>
 *   <li>{@code /lorezone criar <id> <raio> <titulo>} — cria na sua posicao</li>
 *   <li>{@code /lorezone texto <id> <texto>} — lore exibida no subtitulo/chat</li>
 *   <li>{@code /lorezone som <id> <som>} — ex.: minecraft:ambient.cave</li>
 *   <li>{@code /lorezone musica <id> <som> [intervalo]} — MUSICA DE FUNDO (toca ao ENTRAR e para no TEMPO MAXIMO)
 *       em loop enquanto o jogador esta na zona (para ao sair)</li>
 *   <li>{@code /lorezone aviso <id> <classico|pesadelo|runico|real|morte|inferno|gelo|divino|void|sangue>
 *       <segundos> <texto>} — (3.85.0) AVISO cinematografico ao ENTRAR na
 *       zona (os mesmos estilos do /rp admin avisar); {@code off} volta ao
 *       titulo classico</li>
 *   <li>{@code /lorezone remover <id>} — apaga a zona</li>
 *   <li>{@code /lorezone listar} — lista as zonas</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class LoreZoneCommands {

    private LoreZoneCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> ZONE_IDS = (ctx, builder) -> {
        var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
        return SharedSuggestionProvider.suggest(data.all().stream().map(LoreZones.Zone::id), builder);
    };

    /**
     * (3.50.0) PRESETS de som — nomes faceis pra nao decorar ResourceLocation.
     * Usados em {@code /lorezone som} e {@code /lorezone musica}.
     */
    // (3.52.0) +17 presets NOVOS (todos verificados no sounds.json 1.21.1) e
    // o preset "nenhum" que LIMPA o som da zona.
    public static final java.util.Map<String, String> PRESETAS = java.util.Map.ofEntries(
            java.util.Map.entry("caverna", "minecraft:ambient.cave"),
            java.util.Map.entry("coracao", "minecraft:entity.warden.heartbeat"),
            java.util.Map.entry("warden", "minecraft:entity.warden.agitated"),
            java.util.Map.entry("deepdark", "minecraft:music.overworld.deep_dark"),
            java.util.Map.entry("disc13", "minecraft:music_disc.13"),
            java.util.Map.entry("disc11", "minecraft:music_disc.11"),
            java.util.Map.entry("disc5", "minecraft:music_disc.5"),
            java.util.Map.entry("mood", "minecraft:ambient.basalt_deltas.mood"),
            java.util.Map.entry("almas", "minecraft:ambient.soul_sand_valley.mood"),
            java.util.Map.entry("pigstep", "minecraft:music_disc.pigstep"),
            java.util.Map.entry("portal", "minecraft:block.portal.ambient"),
            java.util.Map.entry("portalviagem", "minecraft:block.portal.travel"),
            java.util.Map.entry("nether", "minecraft:ambient.nether_wastes.mood"),
            java.util.Map.entry("vento", "minecraft:ambient.soul_sand_valley.loop"),
            java.util.Map.entry("dragao", "minecraft:entity.ender_dragon.growl"),
            java.util.Map.entry("ghast", "minecraft:entity.ghast.scream"),
            java.util.Map.entry("creeper", "minecraft:entity.creeper.primed"),
            java.util.Map.entry("trovao", "minecraft:entity.lightning_bolt.thunder"),
            java.util.Map.entry("chuva", "minecraft:weather.rain"),
            java.util.Map.entry("fogo", "minecraft:block.fire.ambient"),
            java.util.Map.entry("agua", "minecraft:ambient.underwater.loop"),
            java.util.Map.entry("sino", "minecraft:block.bell.resonate"),
            java.util.Map.entry("outros", "minecraft:music_disc.otherside"),
            java.util.Map.entry("relic", "minecraft:music_disc.relic"),
            java.util.Map.entry("maldicao", "minecraft:music_disc.ward"),
            java.util.Map.entry("creator", "minecraft:music_disc.creator"),
            java.util.Map.entry("nenhum", ""));

    /**
     * Sugestao de tab para o campo SOM (3.52.0): presets + as musicas custom
     * do SERVIDOR (config/rptag/musicas/*.ogg vira @nome).
     */
    private static final SuggestionProvider<CommandSourceStack> SONS = (ctx, builder) -> {
        java.util.List<String> sug = new java.util.ArrayList<>(PRESETAS.keySet());
        try {
            java.nio.file.Path pasta = java.nio.file.Path.of("config", "rptag", "musicas");
            if (java.nio.file.Files.isDirectory(pasta)) {
                try (var arqs = java.nio.file.Files.list(pasta)) {
                    arqs.map(a -> a.getFileName().toString())
                            .filter(n -> n.toLowerCase().endsWith(".ogg"))
                            .map(n -> "@" + n.substring(0, n.length() - 4))
                            .forEach(sug::add);
                }
            }
        } catch (Exception ignored) {
        }
        return SharedSuggestionProvider.suggest(sug, builder);
    };

    /**
     * (3.50.0) Resolve o argumento de som: preset → som vanilla;
     * {@code @nome} → musica custom (sanitize: letras/numeros/_/-);
     * qualquer outra coisa passa direto (ResourceLocation completo).
     */
    private static String resolverSom(String som) {
        String preset = PRESETAS.get(som.toLowerCase());
        if (preset != null) {
            return preset;
        }
        if (som.startsWith("@")) {
            return "@" + som.substring(1).replaceAll("[^a-zA-Z0-9_-]", "");
        }
        return som;
    }

    /** (4.0.0) manda a BARREIRA da zona SÓ para o admin que pediu. */
    private static int mostrar(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            String id, int segundos) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "\u00a7cUse dentro do jogo (a barreira aparece na tela do admin)."));
            return 0;
        }
        LoreZones.Zone z = LoreZones.LoreZonesData.get(ctx.getSource().getServer()).get(id);
        if (z == null) {
            ctx.getSource().sendFailure(Component.literal("Zona '" + id + "' nao existe."));
            return 0;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                new ZoneShowPayload(true, z.id(), z.center().x, z.center().y, z.center().z,
                        z.radius(), z.minY(), z.maxY(), segundos));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "\u00a7aBarreira da zona \u00a7f" + id + "\u00a7a aparecendo s\u00f3 pra VOCE:"
                        + " \u00a7bciano = esfera\u00a7a, \u00a7eamarelo = faixa onde o aviso dispara"
                        + "\u00a7a (Y " + Math.round(z.minY()) + " a " + Math.round(z.maxY()) + ")"
                        + (segundos > 0 ? " \u00a77por " + segundos + "s" : " \u00a77(use /lorezone esconder)")
                        + "."), false);
        return Command.SINGLE_SUCCESS;
    }

    /** (3.48.0) define a musica de fundo da zona (loop + stop ao sair). */
    private static int setMusica(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            String id, String som, int intervalo) {
        var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
        LoreZones.Zone zone = data.get(id);
        if (zone == null) {
            ctx.getSource().sendFailure(Component.literal("Zona '" + id + "' nao existe."));
            return 0;
        }
        String resolvido = resolverSom(som);
        // (3.52.0) LINK: .ogg/.wav direto funciona (o cliente baixa e toca em
        // loop). YouTube NAO toca direto dentro do jogo — manda o comando pronto.
        if (resolvido.startsWith("http")) {
            String baixo = resolvido.toLowerCase();
            if (baixo.contains("youtube.com") || baixo.contains("youtu.be")) {
                String slug = "musica-" + Integer.toHexString(resolvido.hashCode());
                ctx.getSource().sendFailure(Component.literal(
                        "\u00a7cYouTube nao toca direto dentro do jogo (o video precisa virar .ogg primeiro)."
                                + " No seu PC rode: \u00a7fyt-dlp -x --audio-format vorbis \"" + resolvido
                                + "\" -o \"config/rptag/musicas/" + slug + ".ogg\""
                                + "\u00a7c e depois: \u00a7f/lorezone musica " + id + " @" + slug));
                return Command.SINGLE_SUCCESS;
            }
            if (!baixo.endsWith(".ogg") && !baixo.endsWith(".wav")) {
                ctx.getSource().sendFailure(Component.literal(
                        "\u00a7cO link precisa apontar pra um arquivo .ogg ou .wav direto"
                                + " (ex.: https://seusite.com/tema.ogg). YouTube vira .ogg com yt-dlp."));
                return Command.SINGLE_SUCCESS;
            }
        }
        LoreZones.Zone nova = new LoreZones.Zone(zone.id(), zone.dimension(), zone.center(),
                zone.radius(), zone.title(), zone.text(), zone.sound(), resolvido, intervalo,
                zone.aviso(), zone.avisoEstilo(), zone.avisoSeg(), zone.minY(), zone.maxY());
        data.put(nova);
        String msg;
        if (resolvido.startsWith("@")) {
            msg = "Musica CUSTOM da zona '" + id + "': " + resolvido + " — o jogador toca o arquivo "
                    + resolvido.substring(1) + ".ogg (pasta config/rptag/musicas do CLIENTE) em LOOP"
                    + " e para ao sair. Presets: /lorezone musicas";
        } else if (resolvido.startsWith("http")) {
            msg = "Musica por LINK da zona '" + id + "': " + resolvido
                    + " — os players baixam o audio automaticamente (cache local) e tocam em LOOP.";
        } else if (intervalo == -2) {
            // (3.99.0) MUSICA UNICA: so na primeira entrada da vida
            msg = "Musica UNICA da zona '" + id + "' definida: " + resolvido
                    + " — toca so na PRIMEIRA entrada de cada jogador (e para em no maximo "
                    + LoreZones.MUSIC_MAX_PADRAO_SEG + "s). Presets: /lorezone musicas";
        } else if (intervalo == -1) {
            // (3.55.0) 1x = toca uma unica vez quando o jogador ENTRA na zona
            // (3.99.0) e para sozinha no TEMPO MAXIMO padrao (nunca pra sempre)
            msg = "Musica da zona '" + id + "' definida: " + resolvido
                    + " — toca ao ENTRAR e para em no maximo " + LoreZones.MUSIC_MAX_PADRAO_SEG
                    + "s (nunca fica pra sempre). Presets: /lorezone musicas";
        } else {
            // (3.99.0) o numero agora e o TEMPO MAXIMO de reproducao
            msg = "Musica da zona '" + id + "' definida: " + resolvido + " — toca ao entrar e PARA em "
                    + intervalo + "s (tempo maximo; nao fica em loop eterno). Presets: /lorezone musicas"
                    + " · custom: /lorezone musica " + id + " @arquivo";
        }
        String dicaVol = "\u00a77 (\u00a7fvolume\u00a77: Opcoes > Musica e Sons > \u00a7fMusica\u00a77)";
        ctx.getSource().sendSuccess(() -> Component.literal(msg + dicaVol), true);
        // (3.52.0) quem ja esta dentro da zona ouve a musica NOVA agora
        // (para a antiga — substitui, nunca empilha)
        LoreZones.atualizarSons(ctx.getSource().getServer(), nova);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * (3.55.0) BAIXA a musica pra pasta do SERVIDOR com mensagens de
     * progresso ("download iniciado" / "download concluido"). Depois e so
     * usar {@code /lorezone musica <id> @<nome>} — o mod ENTREGA o arquivo
     * pros jogadores automaticamente (sync em pedaços).
     */
    private static int baixarMusica(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            String url) {
        String limpo = url.trim();
        String baixo = limpo.toLowerCase();
        var src = ctx.getSource();
        if (baixo.contains("youtube.com") || baixo.contains("youtu.be")) {
            src.sendFailure(Component.literal(
                    "\u00a7cYouTube nao permite baixar direto. No seu PC rode:"
                            + " \u00a7fyt-dlp -x --audio-format vorbis \"" + limpo
                            + "\"\u00a7c e envie o .ogg pra pasta config/rptag/musicas"
                            + " do SERVIDOR (ou use um link .ogg direto)."));
            return 0;
        }
        if (!baixo.startsWith("http")) {
            src.sendFailure(Component.literal(
                    "\u00a7cPrecisa ser um LINK direto de audio (.ogg ou .wav)."));
            return 0;
        }
        String caminho = limpo.split("[?#]", 2)[0];
        String arquivo = caminho.substring(caminho.lastIndexOf('/') + 1);
        if (!arquivo.toLowerCase().endsWith(".ogg") && !arquivo.toLowerCase().endsWith(".wav")) {
            src.sendFailure(Component.literal(
                    "\u00a7cO link precisa apontar pra um arquivo .ogg ou .wav direto"
                            + " (terminando em .ogg/.wav)."));
            return 0;
        }
        String nome = arquivo.substring(0, arquivo.length() - 4).replaceAll("[^a-zA-Z0-9_-]", "");
        if (nome.isEmpty()) {
            nome = "musica-" + Integer.toHexString(limpo.hashCode());
        }
        final String nomeFinal = nome;
        final String ext = arquivo.toLowerCase().endsWith(".wav") ? ".wav" : ".ogg";
        src.sendSuccess(() -> Component.literal(
                "\u00a7d\u2935 Download da musica '\u00a7f" + nomeFinal
                        + "\u00a7d' iniciado...\u00a77 (o arquivo vai pra config/rptag/musicas do SERVIDOR)"), false);
        Thread t = new Thread(() -> {
            try {
                java.net.http.HttpClient cliente = java.net.http.HttpClient.newBuilder()
                        .followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
                java.net.http.HttpRequest pedido = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(limpo))
                        .timeout(java.time.Duration.ofSeconds(60))
                        .header("User-Agent", "RPTag/3.55.0 (baixar musica de lore zone)")
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
                java.nio.file.Path pasta = java.nio.file.Path.of("config", "rptag", "musicas");
                java.nio.file.Files.createDirectories(pasta);
                java.nio.file.Path destino = pasta.resolve(nomeFinal + ext);
                java.nio.file.Files.write(destino, dados);
                dev.rptag.MusicSync.recarregar(nomeFinal + ext, dados);
                ctx.getSource().getServer().execute(() -> {
                    String msgf = "\u00a7a\u2714 Download concluido: \u00a7f" + nomeFinal + ext
                            + "\u00a7a (" + (dados.length / 1024) + " KB)! Todo mundo ouve:"
                            + " \u00a7f/lorezone musica <id> @" + nomeFinal;
                    avisarAdmin(ctx, msgf);
                });
            } catch (Exception e) {
                ctx.getSource().getServer().execute(() -> avisarAdmin(ctx,
                        "\u00a7c\u2716 Download da musica '\u00a7f" + nomeFinal
                                + "\u00a7c' falhou: " + e.getMessage()));
            }
        }, "RPTag-BaixarMusica");
        t.setDaemon(true);
        t.start();
        return Command.SINGLE_SUCCESS;
    }

    /** manda a mensagem do download pro admin (que pode ter saido). */
    private static void avisarAdmin(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            String msgf) {
        try {
            var p = ctx.getSource().getPlayerOrException();
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(msgf));
        } catch (Exception semPlayer) {
            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(msgf), false);
        }
    }

    /** (3.85.0) estilos de aviso por NOME (igual /rp admin avisar). */
    private static final java.util.Map<String, Integer> ESTILOS = java.util.Map.ofEntries(
            java.util.Map.entry("classico", 0), java.util.Map.entry("pesadelo", 1),
            java.util.Map.entry("runico", 2), java.util.Map.entry("real", 3),
            java.util.Map.entry("morte", 4), java.util.Map.entry("inferno", 5),
            java.util.Map.entry("gelo", 6), java.util.Map.entry("divino", 7),
            java.util.Map.entry("void", 8), java.util.Map.entry("sangue", 9),
            java.util.Map.entry("demoniaco", 10), java.util.Map.entry("pesadelo2", 11));

    /** (3.85.0) nome do estilo pra exibir no /lorezone listar. */
    private static String nomeEstilo(int e) {
        return switch (e) {
            case 1 -> "pesadelo";
            case 2 -> "runico";
            case 3 -> "real";
            case 4 -> "morte";
            case 5 -> "inferno";
            case 6 -> "gelo";
            case 7 -> "divino";
            case 8 -> "void";
            case 9 -> "sangue";
            case 10 -> "demoniaco";
            case 11 -> "pesadelo2";
            default -> "classico";
        };
    }

    /**
     * (3.85.0) define o AVISO da zona: quando um jogador ENTRAR, recebe o
     * aviso cinematografico (estilo escolhido) no lugar do titulo classico.
     * Quem ja esta dentro ve o aviso novo NA HORA (teste imediato).
     */
    private static int setAviso(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            String id, String estiloNome, int segundos, String texto) {
        var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
        LoreZones.Zone z = data.get(id);
        if (z == null) {
            ctx.getSource().sendFailure(Component.literal("Zona '" + id + "' nao existe."));
            return 0;
        }
        Integer estilo = ESTILOS.get(estiloNome.toLowerCase());
        if (estilo == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "\u00a7cEstilo '" + estiloNome + "' nao existe! Opcoes (TAB):"
                            + " classico, pesadelo, pesadelo2, runico, real, morte, inferno, gelo, divino, void, sangue, demoniaco"));
            return 0;
        }
        String traduzido = RPCommands.translateAmp(texto);
        if (estilo == 0 && traduzido.indexOf('\u00a7') < 0) {
            traduzido = "\u00a7c" + traduzido; // classico sem cor = VERMELHO (padrao do avisar)
        }
        if (traduzido.length() > 160) {
            traduzido = traduzido.substring(0, 160);
        }
        final String avisoFinal = traduzido;
        final int estiloFinal = estilo;
        final int segFinal = Math.max(1, Math.min(60, segundos));
        LoreZones.Zone nova = new LoreZones.Zone(z.id(), z.dimension(), z.center(), z.radius(),
                z.title(), z.text(), z.sound(), z.music(), z.musicInterval(),
                avisoFinal, estiloFinal, segFinal, z.minY(), z.maxY());
        data.put(nova);
        String dica = estiloFinal == 0
                ? "\u00a77 (aceita codigos & — ex. &lnegrito)"
                : "\u00a77 (a paleta \u00e9 do estilo — c\u00f3digos & s\u00e3o ignorados)";
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Aviso da zona '" + id + "': \u00a7e" + estiloNome.toUpperCase() + "\u00a77 por "
                        + segFinal + "s \u2014 dispara QUANDO ALGU\u00c9M ENTRAR"
                        + " (o t\u00edtulo cl\u00e1ssico d\u00e1 lugar ao aviso; \u00a7f/lorezone aviso " + id
                        + " off\u00a77 volta ao t\u00edtulo). " + dica), true);
        // quem JA ESTA dentro da zona ve o aviso novo NA HORA
        for (ServerPlayer p : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            if (p.level().dimension() == z.dimension()
                    && p.position().distanceTo(z.center()) <= z.radius()) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                        new ScreenTextPayload(avisoFinal, segFinal, estiloFinal));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    /** (3.85.0) remove o aviso da zona (volta ao titulo classico). */
    private static int limparAviso(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx,
            String id) {
        var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
        LoreZones.Zone z = data.get(id);
        if (z == null) {
            ctx.getSource().sendFailure(Component.literal("Zona '" + id + "' nao existe."));
            return 0;
        }
        LoreZones.Zone nova = new LoreZones.Zone(z.id(), z.dimension(), z.center(), z.radius(),
                z.title(), z.text(), z.sound(), z.music(), z.musicInterval(), "", 0, 8,
                z.minY(), z.maxY());
        data.put(nova);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Aviso da zona '" + id + "' removido \u2014 voltou ao t\u00edtulo cl\u00e1ssico \u2726"), true);
        return Command.SINGLE_SUCCESS;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("lorezone")
                .requires(src -> src.hasPermission(2));

        // /lorezone criar <id> <raio> <titulo>
        root.then(Commands.literal("criar")
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("raio", DoubleArgumentType.doubleArg(2, 500))
                                .then(Commands.argument("titulo", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String id = StringArgumentType.getString(ctx, "id");
                                            double raio = DoubleArgumentType.getDouble(ctx, "raio");
                                            String titulo = StringArgumentType.getString(ctx, "titulo");
                                            LoreZones.LoreZonesData.get(player.server).put(new LoreZones.Zone(
                                                    id, player.level().dimension(), player.position(),
                                                    raio, titulo, "", "", "", 45, "", 0, 8,
                                                    player.position().y - raio, player.position().y + raio));
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "Zona '" + id + "' criada aqui! \u00a7eRaio " + raio
                                                            + "\u00a77 = ESFERA de " + raio + " blocos em TODAS as dire\u00e7\u00f5es"
                                                            + " (di\u00e2metro " + Math.round(raio * 2) + ")."
                                                            + " Add texto: /lorezone texto " + id + " ..."),
                                                    true);
                                            return Command.SINGLE_SUCCESS;
                                        })))));

        // /lorezone texto <id> <texto>
        root.then(Commands.literal("texto")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(ZONE_IDS)
                        .then(Commands.argument("texto", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    String texto = StringArgumentType.getString(ctx, "texto");
                                    var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
                                    LoreZones.Zone z = data.get(id);
                                    if (z == null) {
                                        ctx.getSource().sendFailure(Component.literal("Zona '" + id + "' nao existe."));
                                        return 0;
                                    }
                                    data.put(new LoreZones.Zone(z.id(), z.dimension(), z.center(), z.radius(), z.title(), texto, z.sound(), z.music(), z.musicInterval(), z.aviso(), z.avisoEstilo(), z.avisoSeg(), z.minY(), z.maxY()));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Texto da zona '" + id + "' atualizado."), true);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // (4.0.0) /lorezone vertical <id> <minY> <maxY> — faixa vertical da zona
        // (ex.: evento na caverna: so dispara entre Y=-20 e Y=40; quem estiver
        // la no topo do mundo NAO recebe o aviso)
        root.then(Commands.literal("vertical")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(ZONE_IDS)
                        .then(Commands.literal("auto").executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
                            LoreZones.Zone z = data.get(id);
                            if (z == null) {
                                ctx.getSource().sendFailure(Component.literal("Zona '" + id + "' nao existe."));
                                return 0;
                            }
                            data.put(new LoreZones.Zone(z.id(), z.dimension(), z.center(), z.radius(),
                                    z.title(), z.text(), z.sound(), z.music(), z.musicInterval(),
                                    z.aviso(), z.avisoEstilo(), z.avisoSeg(),
                                    z.center().y - z.radius(), z.center().y + z.radius()));
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "\u00a7aZona '" + id + "' voltou a usar a ESFERA inteira (sem faixa vertical)."), true);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.argument("minY", DoubleArgumentType.doubleArg(-512, 512))
                                .then(Commands.argument("maxY", DoubleArgumentType.doubleArg(-512, 512))
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            double minY = DoubleArgumentType.getDouble(ctx, "minY");
                                            double maxY = DoubleArgumentType.getDouble(ctx, "maxY");
                                            var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
                                            LoreZones.Zone z = data.get(id);
                                            if (z == null) {
                                                ctx.getSource().sendFailure(Component.literal("Zona '" + id + "' nao existe."));
                                                return 0;
                                            }
                                            if (maxY <= minY) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "\u00a7cO maxY precisa ser MAIOR que o minY."));
                                                return 0;
                                            }
                                            data.put(new LoreZones.Zone(z.id(), z.dimension(), z.center(), z.radius(),
                                                    z.title(), z.text(), z.sound(), z.music(), z.musicInterval(),
                                                    z.aviso(), z.avisoEstilo(), z.avisoSeg(), minY, maxY));
                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                    "\u00a7aZona '" + id + "' agora so dispara entre Y \u00a7f"
                                                            + Math.round(minY) + "\u00a7a e Y \u00a7f" + Math.round(maxY)
                                                            + "\u00a7a. Use \u00a7f/lorezone mostrar " + id
                                                            + "\u00a7a para ver a barreira."), true);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.argument("minY", DoubleArgumentType.doubleArg(-512, 512))
                        .then(Commands.argument("maxY", DoubleArgumentType.doubleArg(-512, 512))
                                .executes(ctx -> {
                                    ctx.getSource().sendFailure(Component.literal(
                                            "\u00a7cUso: /lorezone vertical <id> <minY> <maxY>"));
                                    return 0;
                                }))));

        // (4.0.0) /lorezone mostrar <id> [segundos] — BARREIRA so pra VOCE
        // (esfera em ciano + faixa vertical em amarelo). Ninguem mais ve.
        root.then(Commands.literal("mostrar")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(ZONE_IDS)
                        .executes(ctx -> mostrar(ctx, StringArgumentType.getString(ctx, "id"), 0))
                        .then(Commands.argument("segundos", IntegerArgumentType.integer(3, 600))
                                .executes(ctx -> mostrar(ctx,
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "segundos"))))));
        root.then(Commands.literal("esconder").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            if (p != null) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                        new ZoneShowPayload(false, "", 0, 0, 0, 0, 0, 0, 0));
            }
            ctx.getSource().sendSuccess(() -> Component.literal("\u00a77Barreira escondida."), false);
            return Command.SINGLE_SUCCESS;
        }));

        // (4.0.0) /lorezone varinha — pega a Varinha de Lorezone (admin)
        root.then(Commands.literal("varinha").executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            var stack = new net.minecraft.world.item.ItemStack(RPTagMod.LOREZONE_WAND.get());
            if (!p.getInventory().add(stack)) {
                p.drop(stack, false);
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "\u00a7aPegou a \u00a7dVarinha de Lorezone\u00a7a! Clique DIREITO perto de uma zona para ver a barreira"
                            + " \u00a77(shift + clique esconde)\u00a7a."), false);
            return Command.SINGLE_SUCCESS;
        }));

        // (3.99.0) /lorezone avisos reset <jogador> — o jogador ve os avisos
        // das zonas DE NOVO (os avisos sao unicos e ficam salvos no mundo)
        root.then(Commands.literal("avisos")
                .then(Commands.literal("reset")
                        .then(Commands.argument("jogador",
                                        net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(ctx -> {
                                    var alvo = net.minecraft.commands.arguments.EntityArgument
                                            .getPlayer(ctx, "jogador");
                                    int n = LoreZones.LoreZonesData.get(ctx.getSource().getServer())
                                            .resetAvisos(alvo.getUUID());
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "\u00a7aAvisos resetados de \u00a7f" + alvo.getGameProfile().getName()
                                                    + "\u00a7a (" + n + " zona(s)) — ele vai ver de novo ao entrar."), true);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // /lorezone som <id> <som> — aceita preset (ex.: caverna) ou minecraft:...
        root.then(Commands.literal("som")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(ZONE_IDS)
                        // (3.50.0) GREEDY: aceita "minecraft:xyz" solto (o string()
                        // do brigadier NAO aceita ":" em palavra solta!)
                        .then(Commands.argument("som", StringArgumentType.greedyString()).suggests(SONS)
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "id");
                                    String som = resolverSom(StringArgumentType.getString(ctx, "som"));
                                    var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
                                    LoreZones.Zone z = data.get(id);
                                    if (z == null) {
                                        ctx.getSource().sendFailure(Component.literal("Zona '" + id + "' nao existe."));
                                        return 0;
                                    }
                                    // (3.52.0) VALIDA: som que nao existe no jogo nao
                                    // salva mais — o admin descobre o erro NA HORA (era
                                    // assim que caverna/alma/coracao ficavam mudos)
                                    if (!som.isEmpty() && !som.startsWith("@") && !som.startsWith("http")) {
                                        SoundEvent teste = BuiltInRegistries.SOUND_EVENT
                                                .get(ResourceLocation.tryParse(som));
                                        if (teste == null) {
                                            ctx.getSource().sendFailure(Component.literal(
                                                    "\u00a7cSom '" + som + "' nao existe no jogo! Aperte TAB"
                                                            + " pra ver os presets (caverna, coracao, trovao...)"));
                                            return 0;
                                        }
                                    }
                                    LoreZones.Zone nova = new LoreZones.Zone(z.id(), z.dimension(), z.center(), z.radius(), z.title(), z.text(), som, z.music(), z.musicInterval(), z.aviso(), z.avisoEstilo(), z.avisoSeg(), z.minY(), z.maxY());
                                    data.put(nova);
                                    String dica = som.isEmpty()
                                            ? "\u00a77 (som removido da zona)"
                                            : "\u00a77 (toca 1x ao entrar · \u00a7fvolume\u00a77: Opcoes > Musica e Sons > \u00a7fAmbiente\u00a77)";
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Som da zona '" + id + "' definido: " + (som.isEmpty() ? "(nenhum)" : som)
                                                    + dica), true);
                                    // (3.52.0) quem ja esta dentro ouve o som novo NA HORA
                                    LoreZones.atualizarSons(ctx.getSource().getServer(), nova);
                                    return Command.SINGLE_SUCCESS;
                                }))));

        // (3.48.0) /lorezone musica <id> <som> [intervalo_segundos]
        // MUSICA DE FUNDO: toca em LOOP enquanto o jogador esta na zona
        // (intervalo = duracao estimada da faixa, padrao 45s) e PARA ao sair.
        // (3.50.0) /lorezone musica <id> <som> — som GREEDY (aceita minecraft:x
        // solto e @arquivo); o intervalo (10-600s) agora vem no MEIO, pois o
        // greedy consome o resto da linha:
        //   /lorezone musica <id> <som>
        //   /lorezone musica <id> intervalo <segundos> <som>
        root.then(Commands.literal("musica")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(ZONE_IDS)
                        .then(Commands.argument("som", StringArgumentType.greedyString()).suggests(SONS)
                                .executes(ctx -> {
                                    return setMusica(ctx,
                                            StringArgumentType.getString(ctx, "id"),
                                            StringArgumentType.getString(ctx, "som"), 45);
                                }))
                        .then(Commands.literal("intervalo")
                                .then(Commands.argument("segundos", IntegerArgumentType.integer(10, 600))
                                        .then(Commands.argument("som", StringArgumentType.greedyString()).suggests(SONS)
                                                .executes(ctx -> {
                                                    return setMusica(ctx,
                                                            StringArgumentType.getString(ctx, "id"),
                                                            StringArgumentType.getString(ctx, "som"),
                                                            IntegerArgumentType.getInteger(ctx, "segundos"));
                                                }))))
                        // (3.99.0) /lorezone musica <id> unica <som> — toca SO
                        // na PRIMEIRA entrada do jogador (para sempre) e com
                        // limite de tempo (nunca fica tocando eternamente)
                        .then(Commands.literal("unica")
                                .then(Commands.argument("som", StringArgumentType.greedyString()).suggests(SONS)
                                        .executes(ctx -> {
                                            return setMusica(ctx,
                                                    StringArgumentType.getString(ctx, "id"),
                                                    StringArgumentType.getString(ctx, "som"),
                                                    -2);
                                        })))
                        // (3.55.0) /lorezone musica <id> 1x <som> — toca UMA vez
                        // quando o jogador ENTRA na zona (sem loop)
                        .then(Commands.literal("1x")
                                .then(Commands.argument("som", StringArgumentType.greedyString()).suggests(SONS)
                                        .executes(ctx -> {
                                            return setMusica(ctx,
                                                    StringArgumentType.getString(ctx, "id"),
                                                    StringArgumentType.getString(ctx, "som"),
                                                    -1);
                                        })))));

        // (3.55.0) /lorezone baixar <url> — BAIXA a musica pra pasta do
        // SERVIDOR (config/rptag/musicas) e sincroniza pros jogadores:
        // todo mundo ouve sem baixar nada!
        root.then(Commands.literal("baixar")
                .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            return baixarMusica(ctx, StringArgumentType.getString(ctx, "url"));
                        })));

        // /lorezone musicas — lista os presets e explica a API de musica custom
        root.then(Commands.literal("musicas").executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal("Sons PRONTOS (presets):").withStyle(ChatFormatting.GOLD), false);
            for (var e : PRESETAS.entrySet()) {
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  • " + e.getKey() + " = " + e.getValue()).withStyle(ChatFormatting.GRAY), false);
            }
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "MUSICA CUSTOM: /lorezone musica <id> @<nome> toca config/rptag/musicas/<nome>.ogg"
                            + " (do CLIENTE) em loop ate sair da zona.").withStyle(ChatFormatting.AQUA), false);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Pegar musica de video (ex. YouTube): yt-dlp -x --audio-format vorbis \"URL\""
                            + " — renomeie o .ogg (letras/numeros/_) e jogue na pasta.").withStyle(ChatFormatting.GRAY), false);
            return Command.SINGLE_SUCCESS;
        }));

        // (3.85.0) /lorezone aviso <id> <classico|pesadelo|runico|real|morte>
        //          <segundos> <texto>  —  /lorezone aviso <id> off
        // AVISO cinematografico que dispara QUANDO O JOGADOR ENTRA na zona
        // (os mesmos estilos do /rp admin avisar), no lugar do titulo classico.
        root.then(Commands.literal("aviso")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(ZONE_IDS)
                        .then(Commands.literal("off")
                                .executes(ctx -> limparAviso(ctx,
                                        StringArgumentType.getString(ctx, "id"))))
                        .then(Commands.argument("estilo", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        ESTILOS.keySet(), b))
                                .then(Commands.argument("segundos", IntegerArgumentType.integer(1, 60))
                                        .then(Commands.argument("texto", StringArgumentType.greedyString())
                                                .executes(ctx -> setAviso(ctx,
                                                        StringArgumentType.getString(ctx, "id"),
                                                        StringArgumentType.getString(ctx, "estilo"),
                                                        IntegerArgumentType.getInteger(ctx, "segundos"),
                                                        StringArgumentType.getString(ctx, "texto"))))))));

        // /lorezone remover <id>
        root.then(Commands.literal("remover")
                .then(Commands.argument("id", StringArgumentType.word()).suggests(ZONE_IDS)
                        .executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            boolean ok = LoreZones.LoreZonesData.get(ctx.getSource().getServer()).remove(id);
                            if (ok) {
                                ctx.getSource().sendSuccess(() -> Component.literal("Zona '" + id + "' removida."), true);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("Zona '" + id + "' nao existe."));
                            }
                            return Command.SINGLE_SUCCESS;
                        })));

        // /lorezone listar
        root.then(Commands.literal("listar").executes(ctx -> {
            var data = LoreZones.LoreZonesData.get(ctx.getSource().getServer());
            var all = data.all();
            if (all.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("Nenhuma lore zone criada ainda.").withStyle(ChatFormatting.GRAY), false);
                return Command.SINGLE_SUCCESS;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("Lore zones (" + all.size() + "):").withStyle(ChatFormatting.GOLD), false);
            for (LoreZones.Zone z : all) {
                String marcaAviso = z.aviso() == null || z.aviso().isEmpty()
                        ? ""
                        : " \u00a76[aviso:" + nomeEstilo(z.avisoEstilo()) + "]";
                // (4.0.0) faixa vertical: mostra quando a zona e limitada em Y
                String faixa = z.faixaVertical()
                        ? " \u00a7eY " + Math.round(z.minY()) + ".." + Math.round(z.maxY())
                        : "";
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  • " + z.id() + " [" + z.dimension().location() + "] r=" + (int) z.radius()
                                + " — " + z.title() + marcaAviso + faixa).withStyle(ChatFormatting.GRAY), false);
            }
            return Command.SINGLE_SUCCESS;
        }));

        event.getDispatcher().register(root);
    }
}
