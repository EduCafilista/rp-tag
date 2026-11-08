package dev.rptag;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Logica do lado do servidor: guardaria de estado/persona, sincronizacao
 * com os clientes, nomes (chat/TAB) e chat local por proximidade.
 */
@EventBusSubscriber(modid = RPTagMod.MODID)
public final class ServerEvents {

    private ServerEvents() {
    }

    /** @return true se o jogador esta em RP. */
    public static boolean isInRp(ServerPlayer player) {
        return RPWorldData.get(player.server).isInRp(player.getUUID());
    }

    /** @return a persona do jogador (ou EMPTY). */
    public static Persona getPersona(ServerPlayer player) {
        return RPWorldData.get(player.server).getPersona(player.getUUID());
    }

    // ---- (5.11.0) o DEUS DO VAZIO volta com o servidor ----
    @SubscribeEvent
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        NeptuneEventos.restaurarDeuses(event.getServer());
    }

    // ---- (5.4.0) tick do evento NEPTUNE (terremoto, deus, bicho, saidas) ----
    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        NeptuneEventos.tick(event.getServer());
        // (5.17.0) NÚCLEO VIAL: o desgaste é por DIA do jogo, então checar a cada 2 s é de sobra
        // (o dia só vira a cada 20 minutos). Barato: sai na hora quando o dia não mudou.
        if (event.getServer().getTickCount() % 40 == 0) {
            try {
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    NucleoVialItem.tickJogador(p);
                }
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: tick do núcleo vial falhou", t);
            }
        }
        // (5.25.0) VAMPIRO: uma vez por segundo o sol decide se queima ou não.
        // Só faz trabalho pra quem é vampiro (quem não é sai na primeira linha).
        if (event.getServer().getTickCount() % 20 == 0) {
            try {
                for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                    VampiroSistema.tick(p);
                }
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: tick do vampiro falhou", t);
            }
        }
    }

    // ---- (5.12.0) REINO DO VAZIO: cliques nos pedestais e no portal ----
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        try {
            // (5.13.0) WAYSTONE PROIBIDA NO VAZIO — pedido 50. Sem isso o exílio era
            // uma piada: a pessoa colocava a waystone e saía em 2 segundos.
            if (bloquearWaystone(p, event.getLevel().getBlockState(event.getPos())
                    .getBlock().builtInRegistryHolder().key().location().toString(), event.getItemStack())) {
                event.setCanceled(true);
                event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
                event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                return;
            }
            if (VazioReino.interagir(p, event.getPos())) {
                event.setCanceled(true);          // não abre baú/usa item à toa
                event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
                event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: clique no pedestal falhou (o jogo segue)", t);
        }
    }

    /**
     * (5.13.4) O VAZIO NÃO CRIA BICHO — pedido 53.
     *
     * <p><i>"não deixe spawnar slimes, se for para spawnar um bicho completamente
     * preto"</i> (5.13.4). <b>(5.22.0)</b> o dono foi além: <i>"tira eles e deixa vazio
     * mesmo, nao quero spawn de enderman la"</i>. Agora o vazio não cria <b>nada</b>: todo
     * spawn natural é cancelado (Enderman e Warden inclusive) e o que aparecer é varrido
     * por {@link VazioReino#limparBichos}.
     */
    @SubscribeEvent
    public static void onFinalizeSpawn(
            net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        try {
            if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl)
                    || !VazioReino.ehVazio(sl)) {
                return;
            }
            // (5.22.0) NADA NASCE NO VAZIO — nem enderman. O dono: "tira eles e deixa
            // vazio mesmo, nao quero spawn de enderman la". Antes passava enderman e
            // warden ("bicho preto"); agora o spawn natural é cancelado para TODO mundo.
            if (event.getEntity().getType() != null) {
                event.setSpawnCancelled(true);
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: bloqueio de spawn no vazio falhou (o jogo segue)", t);
        }
    }

    /** (5.13.0) clicar com a waystone/warp stone NA MÃO dentro do vazio. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        try {
            String id = event.getItemStack().getItem().builtInRegistryHolder().key().location().toString();
            if (bloquearWaystone(p, id, event.getItemStack())) {
                event.setCanceled(true);
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: bloqueio de waystone (mao) falhou", t);
        }
    }

    /**
     * (5.13.0) A WAYSTONE NÃO FUNCIONA NO VAZIO.
     *
     * <p>Pedido 50: <i>"não pode usar waystone na dimensão do vazio"</i>. Olhamos o
     * <b>id de registro</b> (não a classe): qualquer bloco/item de mod de teleporte com
     * "waystone", "warp" ou "portstone" no nome é barrado dentro de {@code rptag:exilio}.
     * Se o mod nem estiver instalado, isto nunca dispara — não quebra nada.
     *
     * @return true se o clique foi barrado (aí o jogo não faz mais nada com ele)
     */
    private static boolean bloquearWaystone(ServerPlayer p, String id, net.minecraft.world.item.ItemStack naMao) {
        try {
            if (id == null || id.isEmpty()) {
                return false;
            }
            if (!p.level().dimension().equals(NeptuneEventos.CHAVE_EXILIO_PUBLICA)) {
                return false;
            }
            String alvo = id.toLowerCase(java.util.Locale.ROOT);
            String mao = naMao == null ? ""
                    : naMao.getItem().builtInRegistryHolder().key().location().toString().toLowerCase(java.util.Locale.ROOT);
            boolean eh = alvo.contains("waystone") || alvo.contains("warp_stone")
                    || alvo.contains("warpstone") || alvo.contains("portstone")
                    || alvo.contains("warp_plate") || alvo.contains("sharestone")
                    || mao.contains("waystone") || mao.contains("warp_stone")
                    || mao.contains("warpstone") || mao.contains("portstone");
            if (!eh) {
                return false;
            }
            p.playNotifySound(RPTagMod.SOM_VAZIO.get(), net.minecraft.sounds.SoundSource.HOSTILE, 0.9F, 0.6F);
            p.displayClientMessage(Component.literal(
                    "\u00a74A pedra está morta aqui.\u00a7r \u00a78Não há caminho que atravesse o vazio\n"
                    + "\u00a78— só o portal negro, e ele cobra os 8 fragmentos."), true);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * (5.12.0) O VAZIO QUE MATA DEVAGAR — pedido 49: "dá um tempo e depois
     * mata". Esta é a parte essencial: enquanto a contagem corre ({@link
     * VazioReino#estaCaindo}), o dano do void do jogo é CANCELADO. Sem isso o
     * jogador morreria no primeiro tick em que passasse do min_y, e a
     * contagem nunca aconteceria.
     */
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        try {
            if (!p.level().dimension().equals(NeptuneEventos.CHAVE_EXILIO_PUBLICA)) {
                return;
            }
            var fonte = event.getSource();
            boolean doVoid = fonte.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD);
            if (doVoid && VazioReino.estaCaindo(p.getUUID())) {
                event.setCanceled(true);          // o vazio espera a contagem
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: dano do vazio tratado com erro (o jogo segue)", t);
        }
    }

    // ---- (5.12.0) respawn de verdade encerra o selo do /apagar ----
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            NeptuneEventos.avisarRespawn(p.getUUID());
            // (5.20.0) MORREU NO VAZIO? CONTINUA NO VAZIO.
            // O dono: "se o player morrer la dentro ele continua la dentro". Antes o respawn
            // devolvia a pessoa pro overworld (ou pro spawn do mundo), o que esvaziava o
            // exílio: morrer era a saída de graça.
            try {
                if (VazioReino.consumirMorteNoVazio(p.getUUID())) {
                    var nv = p.getServer().getLevel(NeptuneEventos.CHAVE_EXILIO_PUBLICA);
                    if (nv != null) {
                        var chegada = VazioReino.chegada(nv);
                        p.teleportTo(nv, chegada.x, chegada.y, chegada.z,
                                java.util.Set.of(), p.getYRot(), p.getXRot());
                        p.setHealth(p.getMaxHealth());     // volta de pé, não com 0 de vida
                        p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                                "\u00a78O vazio não devolve ninguém.\u00a7r \u00a77Você acordou de novo "
                                        + "na praça \u2014 ainda dentro dele."), false);
                    }
                }
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: respawn no vazio falhou (o jogo segue)", t);
            }
        }
    }

    /** (5.20.0) anota a morte para saber, no respawn, que ela foi DENTRO do vazio. */
    @SubscribeEvent
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        try {
            if (event.getEntity() instanceof ServerPlayer p && VazioReino.ehVazio(p.level())) {
                VazioReino.marcarMorteNoVazio(p.getUUID());
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: marcação de morte no vazio falhou", t);
        }
    }

    /**
     * (5.20.0) TODO CHUNK DO VAZIO PASSA PELO RELEVO — o mundo inteiro tem montanha.
     *
     * <p>O dono: <i>"NAO QUERO que apenas o spawn seja com montanhas, quero que o mundo
     * inteiro seja assim"</i>. Cada chunk que carrega entra na fila do relevo (2 por tick),
     * então onde o jogador for, o terreno é serra/cratera — não laje lisa.
     */
    @SubscribeEvent
    public static void onChunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        try {
            if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) {
                VazioReino.aoCarregarChunk(sl, event.getChunk().getPos().x, event.getChunk().getPos().z);
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: relevo do chunk falhou (o jogo segue)", t);
        }
    }

    // ================================================================
    //  Estado RP
    // ================================================================

    /** Define o estado do jogador e sincroniza todos os clientes. */
    public static void setState(ServerPlayer target, boolean inRp, boolean notifyTarget) {
        MinecraftServer server = target.server;
        RPWorldData data = RPWorldData.get(server);
        data.setInRp(target.getUUID(), inRp);

        target.refreshDisplayName();
        target.refreshTabListName();
        PacketDistributor.sendToAllPlayers(new SyncRPStatePayload(target.getUUID(), inRp));

        ClientboundPlayerInfoUpdatePacket tabPacket = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(target));
        server.getPlayerList().broadcastAll(tabPacket);

        if (notifyTarget) {
            target.sendSystemMessage(Component.empty()
                    .append(Component.literal(inRp ? "Modo RP ativado! Seu nome agora mostra " : "Modo RP desativado! Seu nome agora mostra ")
                            .withStyle(inRp ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .append(RPTags.tag(inRp))
                    .append(Component.literal(".").withStyle(inRp ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
        }
    }

    /** Alterna o estado do jogador. @return o novo estado. */
    public static boolean toggle(ServerPlayer player) {
        boolean newValue = !isInRp(player);
        setState(player, newValue, true);
        return newValue;
    }

    // ================================================================
    //  Persona
    // ================================================================

    /** Define a persona e sincroniza todos os clientes. */
    public static void setPersona(ServerPlayer target, Persona persona) {
        RPWorldData.get(target.server).setPersona(target.getUUID(), persona);
        target.refreshDisplayName();
        target.refreshTabListName();
        PacketDistributor.sendToAllPlayers(PersonaSyncPayload.of(target.getUUID(), persona));

        ClientboundPlayerInfoUpdatePacket tabPacket = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), List.of(target));
        target.server.getPlayerList().broadcastAll(tabPacket);
    }

    // ================================================================
    //  Login: sincroniza RP + persona de todos
    // ================================================================

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joined)) {
            return;
        }
        MinecraftServer server = joined.server;
        RPWorldData data = RPWorldData.get(server);

        // Estado e persona do novo jogador para quem ja estava online.
        PacketDistributor.sendToAllPlayers(new SyncRPStatePayload(joined.getUUID(), data.isInRp(joined.getUUID())));
        PacketDistributor.sendToAllPlayers(PersonaSyncPayload.of(joined.getUUID(), data.getPersona(joined.getUUID())));

        // (5.17.0) a BARRA do HUD e a AXION para quem acabou de entrar
        try {
            RPCommands.enviarBarra(joined, data);
            RPCommands.enviarAxion(joined, data);
            NucleoVialItem.tickJogador(joined);     // acerta os dias que passaram com o jogador offline
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: barra/axion no login falhou", t);
        }

        // (4.5.0) recado de vida deixado por um admin enquanto a pessoa estava offline
        String recado = data.consumirRecadoVida(joined.getUUID());
        if (recado != null && !recado.isBlank()) {
            PacketDistributor.sendToPlayer(joined, new ScreenTextPayload(recado, 8, 4));
            joined.playNotifySound(net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                    net.minecraft.sounds.SoundSource.WEATHER, 1.2F, 0.8F);
        }

        // Estado e persona de todos os online para o novo jogador.
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(joined, new SyncRPStatePayload(other.getUUID(), data.isInRp(other.getUUID())));
            PacketDistributor.sendToPlayer(joined, PersonaSyncPayload.of(other.getUUID(), data.getPersona(other.getUUID())));
        }
        // (5.7.2) TÍTULOS: manda os que já existem pro recém-chegado e avisa
        // quem já estava online do título DELE (senão o nametag fica sem).
        Titulos.enviarTodos(joined);
        Titulos.avisar(server, joined.getUUID());
        // (5.11.0) quem entra depois do /vazio ligar também precisa da aura
        NeptuneEventos.reenviarAura(server);
    }

    /**
     * (5.7.2) SOMBRA DA MORTE: quando um jogador morre, sai escuridão do corpo.
     * <ul>
     *   <li>o MORTO recebe o efeito de tela (estilo 19) — ele está olhando pro
     *       próprio corpo na tela de morte;</li>
     *   <li>quem está por perto vê as partículas escuras subindo do cadáver.</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onMorte(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        try {
            if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer morto)) {
                return;
            }
            var server = morto.getServer();
            if (server == null) {
                return;
            }
            // efeito de tela pra quem morreu
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(morto,
                    new ScreenTextPayload("", 5, 19));
            // partículas no cadáver (todos que estiverem perto veem)
            var nivel = morto.serverLevel();
            var pos = morto.position();
            nivel.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK,
                    pos.x, pos.y + 0.6, pos.z, 90, 0.45, 0.7, 0.45, 0.02);
            nivel.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    pos.x, pos.y + 0.5, pos.z, 60, 0.5, 0.6, 0.5, 0.01);
            nivel.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH,
                    pos.x, pos.y + 1.0, pos.z, 50, 0.6, 0.8, 0.6, 0.0);
            nivel.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                    pos.x, pos.y + 0.8, pos.z, 12, 0.3, 0.5, 0.3, 0.01);
            nivel.playSound(null, morto.blockPosition(),
                    net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.55F);
        } catch (Throwable t) {
            dev.rptag.RPTagMod.LOGGER.warn("RP Tag: sombra da morte falhou", t);
        }
    }

    /**
     * (5.23.0) TROCOU DE MUNDO? O aviso de lugar recomeça.
     *
     * <p>Sem isto, quem saía do vazio dentro da cidade e voltava direto para a cidade não
     * recebia o aviso ("todas as vezes que o player entrar na cidade"). Aqui a memória de
     * "onde eu estava" é apagada na troca de dimensão.
     */
    @SubscribeEvent
    public static void onTrocarDimensao(PlayerEvent.PlayerChangedDimensionEvent event) {
        try {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) {
                VazioLugares.aoTrocarDimensao(sp, event.getFrom(), event.getTo());
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: troca de dimensao (aviso de lugar) falhou", t);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // As lore zones voltam a aparecer na proxima sessao.
        LoreZones.clearSeen(event.getEntity().getUUID());
        // (5.23.0) e os avisos de lugar do vazio esquecem onde a pessoa estava
        VazioLugares.esquecer(event.getEntity().getUUID());
        // (5.7.0) ANIMACAO DE SAIDA: quem FICA vê o bonequinho do jogador que
        // saiu se desfazendo em código (o próprio que saiu não vê nada, claro).
        // (5.7.9) O DONO PEDIU: "não quero essa /saida, quero que a animação
        // apareça quando o player sair do jogo". Ela agora é AUTOMÁTICA e
        // SEMPRE roda: coluna de sombra no lugar, códigos em volta, som da
        // expulsão e o anúncio no chat (NeptuneEventos.saidaAutomatica).
        try {
            if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer saiu)) {
                return;
            }
            var server = saiu.getServer();
            if (server == null) {
                return;
            }
            // (5.7.9) uma chamada só: sombra + códigos + som + anúncio.
            // (5.17.1) É A ANIMAÇÃO NO CORPO (no mundo), padrão de todo jogador:
            // nada de tela cheia e nada de comando — sempre que alguém sai do jogo.
            // (5.12.0) se ele estava no meio da contagem do vazio, solta a gravidade
            dev.rptag.VazioReino.soltarCaindo(saiu);
            dev.rptag.NeptuneEventos.saidaAutomatica(saiu);
        } catch (Throwable t) {
            dev.rptag.RPTagMod.LOGGER.warn("RP Tag: animacao de saida falhou ({})", t.toString());
        }
    }

    // ================================================================
    //  Nomes (chat/TAB/servidor)
    // ================================================================

    /** Nome base do jogador: personagem (se houver) ou nick, com tooltip da descricao. */
    private static MutableComponent baseName(ServerPlayer player, Persona persona) {
        if (persona.isEmpty()) {
            return Component.literal(player.getName().getString());
        }
        MutableComponent name = Component.literal(persona.name());
        if (!persona.desc().isEmpty()) {
            MutableComponent tooltip = Component.literal(persona.name() + "\n")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(persona.desc()).withStyle(ChatFormatting.GRAY));
            if (persona.age() > 0) {
                tooltip.append(Component.literal(" • " + persona.age() + " anos").withStyle(ChatFormatting.GRAY));
            }
            name = name.withStyle(s -> s.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip)));
        }
        return name;
    }

    /** Nome usado no chat/servidor (com persona e tag). */
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return; // lado do cliente trata em dev.rptag.client.ClientEvents
        }
        if (RPTags.hasTag(event.getDisplayname())) {
            return;
        }
        // (5.7.2) TÍTULO na frente: [Sagrado] Seraphiel (ʀᴘ) [O Ferreiro]
        event.setDisplayname(Titulos.aplicar(player.server, player.getUUID(),
                baseName(player, getPersona(player)))
                .copy().append(RPTags.tag(isInRp(player))));
    }

    /** Nome usado na lista de jogadores (TAB). */
    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Component base = event.getDisplayName();
        if (base != null && RPTags.hasTag(base)) {
            return; // ja tem a tag (veio do NameFormat)
        }
        // (3.34.0) FIX: null = padrao do vanilla — AGORA colocamos a tag!
        // (antes dava return e o TAB NUNCA mostrava o estado RP)
        // (5.7.2) o TÍTULO também vale pro TAB
        event.setDisplayName(Titulos.aplicar(player.server, player.getUUID(),
                baseName(player, getPersona(player)))
                .copy().append(RPTags.tag(isInRp(player))));
    }

    // ================================================================
    //  Chat local (vanilla -> por proximidade)
    // ================================================================

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        RPWorldData data = RPWorldData.get(event.getPlayer().server);
        boolean bubbleMode = data.isBubbleMode(event.getPlayer().getUUID());
        if (!data.isChatLocal() && !bubbleMode) {
            return; // chat global vanilla, sem balao
        }
        event.setCanceled(true);
        if (bubbleMode) {
            // MODO BALAO: so o balao na cabeca (quem estiver perto ve) — nada no chat
            RPChat.spawnBubble(event.getPlayer(), event.getRawText(), false);
        } else {
            RPChat.broadcastLocalChat(event.getPlayer(), event.getRawText(), RPWorldData.LOCAL_RANGE, false);
        }
    }


    // ================================================================
    //  Estilo do balao e modo balao (vindos da tela de personalizacao)
    // ================================================================

    /**
     * (3.39.0) BLINDAGEM: so aceita codepoints de adesivo/emote validos, com
     * limite de quantidade — payload de cliente alterado nao consegue gravar
     * lixo no estilo (nem estourar o render de quem esta perto).
     */
    private static String sanitizeStickers(String in, int max) {
        if (in == null || in.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < in.length() && sb.length() < max; i++) {
            char c = in.charAt(i);
            if (Stickers.isStickerChar(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void handleUpdateBubbleStyle(final UpdateBubbleStylePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // (3.39.0) TODOS os campos livres sao limitados no SERVIDOR:
            // borderStyle 0..5 (negativo quebrava o RING_MARGIN!), posicao do
            // adesivo 0..100%, duracao 2..20s, adesivo <= 4 e emotes <= 8.
            BubbleStyle style = new BubbleStyle(payload.colorRGB(), payload.borderRGB(),
                    Math.max(0, Math.min(5, payload.borderStyle())),
                    payload.textColorRGB(), payload.prefix(), payload.mid(), payload.suffix(),
                    sanitizeStickers(payload.corner(), 4), sanitizeStickers(payload.extras(), 8),
                    Math.max(0, Math.min(100, payload.stickerX())),
                    Math.max(0, Math.min(100, payload.stickerY())), payload.background(),
                    Math.max(2, Math.min(20, payload.durationSec())));
            RPWorldData.get(player.server).setStyle(player.getUUID(), style);
            player.sendSystemMessage(Component.literal("💾 Salvo no servidor v" + RPTagMod.VERSION + "! Dentro ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(String.format("#%06X", style.colorRGB()))
                            .withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(style.colorRGB()))))
                    .append(Component.literal(" · Borda ").withStyle(ChatFormatting.GREEN))
                    .append(style.borderRGB() == BubbleStyle.NO_BORDER
                            ? Component.literal("sem").withStyle(ChatFormatting.GRAY)
                            : Component.literal(String.format("#%06X", style.borderRGB()))
                                    .withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(
                                            style.borderRGB() == BubbleStyle.AUTO_BORDER ? style.colorRGB() : style.borderRGB()))))
                    .append(Component.literal(" · Letra ").withStyle(ChatFormatting.GREEN))
                    .append(style.textColorRGB() == BubbleStyle.TEXT_AUTO
                            ? Component.literal("auto").withStyle(ChatFormatting.GRAY)
                            : Component.literal(String.format("#%06X", style.textColorRGB()))
                                    .withStyle(s -> s.withColor(net.minecraft.network.chat.TextColor.fromRgb(
                                            style.textColorRGB()))))
                    .append(Component.literal(" — salvo pra sempre, fale no chat! ✨").withStyle(ChatFormatting.GREEN)));
        });
    }

    /**
     * (3.55.0) resposta do cliente ao sync de musica: tem=false = o jogador
     * NAO tem o arquivo — o servidor envia os pedaços pra ele.
     */
    public static void handleMusicAnswer(final dev.rptag.MusicFileAnswerPayload payload,
            final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                dev.rptag.MusicSync.responder(player, payload.nome(), payload.tem());
            }
        });
    }

    /** (3.65.0) TYPEWRITER: jogador ligou/desligou a digitacao do balao
     * (comando ou tela) — guarda por jogador e CONFIRMA pro cliente. */
    public static void handleSetTypewriter(final TypewriterPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sp) {
                RPWorldData.get(sp.getServer()).setTypewriter(sp.getUUID(), payload.on());
                PacketDistributor.sendToPlayer(sp, new TypewriterPayload(payload.on()));
            }
        });
    }

    public static void handleSetBubbleMode(final SetBubbleModePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            RPWorldData.get(player.server).setBubbleMode(player.getUUID(), payload.on());
            if (!payload.on()) {
                // (3.17.0) desligou o modo? o balao atual some NA HORA (balao vazio)
                ChatBubblePayload clear = new ChatBubblePayload(player.getUUID(), "", 0xFFFFFF,
                        -2, 0, -3, false, "", "", 50, 50, 0, 1, 0);
                for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, clear);
                }
            }
            player.sendSystemMessage(payload.on()
                    ? Component.literal("Modo balão LIGADO! Suas falas aparecem SÓ no balão.")
                            .withStyle(ChatFormatting.GREEN)
                    : Component.literal("Modo balão desligado. Suas falas voltaram ao chat.")
                            .withStyle(ChatFormatting.GRAY));
        });
    }
}
