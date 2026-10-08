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
 *   <li>{@code /lorezone musica <id> <som> [intervalo]} — MUSICA DE FUNDO
 *       em loop enquanto o jogador esta na zona (para ao sair)</li>
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
                zone.radius(), zone.title(), zone.text(), zone.sound(), resolvido, intervalo);
        data.put(nova);
        String msg;
        if (resolvido.startsWith("@")) {
            msg = "Musica CUSTOM da zona '" + id + "': " + resolvido + " — o jogador toca o arquivo "
                    + resolvido.substring(1) + ".ogg (pasta config/rptag/musicas do CLIENTE) em LOOP"
                    + " e para ao sair. Presets: /lorezone musicas";
        } else if (resolvido.startsWith("http")) {
            msg = "Musica por LINK da zona '" + id + "': " + resolvido
                    + " — os players baixam o audio automaticamente (cache local) e tocam em LOOP.";
        } else if (intervalo == -1) {
            // (3.55.0) 1x = toca uma unica vez QUANDO ENTRAR na zona
            msg = "Musica da zona '" + id + "' definida: " + resolvido
                    + " — toca 1x SÓ QUANDO ENTRAR na zona (sem loop). Presets: /lorezone musicas";
        } else {
            msg = "Musica da zona '" + id + "' definida: " + resolvido + " em LOOP de " + intervalo
                    + "s (para ao sair). Presets: /lorezone musicas · custom: /lorezone musica " + id + " @arquivo";
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
                                                    raio, titulo, "", "", "", 45));
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
                                    data.put(new LoreZones.Zone(z.id(), z.dimension(), z.center(), z.radius(), z.title(), texto, z.sound(), z.music(), z.musicInterval()));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Texto da zona '" + id + "' atualizado."), true);
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
                                    LoreZones.Zone nova = new LoreZones.Zone(z.id(), z.dimension(), z.center(), z.radius(), z.title(), z.text(), som, z.music(), z.musicInterval());
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
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "  • " + z.id() + " [" + z.dimension().location() + "] r=" + (int) z.radius()
                                + " — " + z.title()).withStyle(ChatFormatting.GRAY), false);
            }
            return Command.SINGLE_SUCCESS;
        }));

        event.getDispatcher().register(root);
    }
}
