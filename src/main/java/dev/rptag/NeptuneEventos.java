package dev.rptag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * (5.4.0) MOTOR DO EVENTO NEPTUNE — terremoto, deus do vazio e o bicho.
 *
 * <p>Tudo aqui é estado de SERVIDOR e roda num único tick handler
 * ({@link #tick(MinecraftServer)}). Nada disso mexe em render: o cliente só
 * recebe pacotes ({@link NeptunePackets}) e desenha.
 *
 * <h2>1. TERREMOTO ({@code /terremoto iniciar})</h2>
 * Estilo "a correção do universo" (a série Loki): a terra colapsa e o universo
 * se reescreve. Enquanto dura:
 * <ul>
 *   <li>o áudio do dono ({@link RPTagMod#SOM_TERREMOTO_COLAPSO}, 14,7 s) toca
 *       no começo pra todos;</li>
 *   <li>o rumble sintetizado entra em loop (um a cada 7 s, volume de evento);</li>
 *   <li>a câmera de TODO MUNDO treme (pacote {@code Terremoto});</li>
 *   <li>os jogadores ficam ESTUNCADOS E LENTOS em PULSOS: 6 s travados
 *       (Lentidão III + Fadiga) e 4 s livres — e nesses 4 s podem correr com
 *       tudo tremendo, exatamente como o dono pediu;</li>
 *   <li>partículas de poeira/terra brotam no chão em volta de cada um.</li>
 * </ul>
 * Termina sozinho no tempo pedido, ou quando um admin manda
 * {@code /terremoto parar}.
 *
 * <h2>2. SAÍDA DO SERVIDOR ({@code /terremoto sair [jogador|todos]})</h2>
 * Player por player: cada um recebe a animação VERDE de dissolução
 * ({@code Saida}), o servidor avisa no chat e SÓ DEPOIS de 4 s chuta o jogador
 * (kick) com a mensagem do evento. É o "universo se corrigindo".
 *
 * <h2>3. DEUS DO VAZIO ({@code /vazio ligar})</h2>
 * O admin vira o "Vazio" (tipo o Void da Marvel): aura preta de partículas em
 * volta do corpo, trilha sonora própria enquanto ANDA, e medo em quem chega
 * perto. Com o modo ligado ele controla o destino:
 * <ul>
 *   <li>{@code /vazio apagar <jogador>} — mata e anuncia
 *       "---- vazio --- apagou <jogador> da existência";</li>
 *   <li>{@code /vazio exilar <jogador>} — manda pra dimensão {@code rptag:vazio}
 *       (feita sob medida pra isso).</li>
 * </ul>
 *
 * <h2>4. O BICHO ({@code /bicho <x> <y> <z>} ou {@code /bicho aqui})</h2>
 * Uma CAIXA DE MÚSICA é colocada no mundo. A caixinha toca (36 s, vai
 * desafinando) pra o jogador ir atrás. Quando alguém chega a menos de
 * {@link #BICHO_RAIO} blocos, o bicho PULA NA TELA (pacote {@code Susto} +
 * berro) e a caixa arrebenta.
 */
public final class NeptuneEventos {

    private NeptuneEventos() {
    }

    // ------------------------------------------------------------------ estado
    /** terremoto */
    private static boolean terremotoAtivo = false;
    private static long terremotoFim = 0;
    private static float terremotoForca = 1.0F;
    private static long proximoRumble = 0;
    private static long proximoColapso = 0;
    private static long proximoPulso = 0;
    private static boolean faseTravado = false;
    /** deus do vazio */
    private static final Map<UUID, Boolean> DEUSES = new HashMap<>();
    private static final Map<UUID, long[]> TRILHA = new HashMap<>();   // [ultimoX, ultimoZ, ultimoTick, tocando]
    private static final Map<UUID, Long> PROXIMO_MEDO = new HashMap<>();
    /** saídas agendadas: [quando, segundos] + nome */
    private static final List<Object[]> SAIDAS = new ArrayList<>();
    /** caixas do bicho: posição + quando começou */
    private static final List<Caixa> CAIXAS = new ArrayList<>();
    /**
     * (5.6.0) FENDAS ABERTAS — cada bloco que a terra engoliu, com o estado
     * original guardado pra fechar depois. É o "chão rachando": o jogador cai
     * 2 blocos numa cova e fica lá, imóvel, até a terra voltar.
     */
    private static final List<Fenda> FENDAS = new ArrayList<>();
    /** (5.7.2) próximo instante de abrir uma rachadura que quebra blocos. */
    private static long proximaFendaSolta = 0L;
    /** (5.7.5) próximo instante de chover escombro em volta dos jogadores. */
    private static long proximoEscombro = 0L;
    private static final java.util.Set<Long> CAVADOS = new java.util.HashSet<>();
    /** queda: uuid -> {y do chão original} (só quem caiu na fenda) */
    private static final Map<UUID, long[]> QUEDA = new HashMap<>();
    private static final ResourceLocation MOD_RAIZ =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "raiz_do_terremoto");
    /** (5.7.6) modificador do "peso": o jogador anda, só que devagar. */
    private static final ResourceLocation MOD_LENTO =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "peso_do_terremoto");
    /** (5.7.6) EXÍLIOS em animação: [fim, uuid, tipo(0=sair, 1=voltar), inicio]. */
    private static final List<Object[]> EXILIOS = new ArrayList<>();
    /** (5.7.6) de ONDE a pessoa foi exilada — ela volta pro mesmo lugar. */
    private static final Map<UUID, Object[]> ORIGEM = new HashMap<>();
    /** (5.7.7) APAGADOS: a animação de cinzas/alma em volta de quem o vazio apagou. */
    private static final List<Apagando> APAGANDO = new ArrayList<>();
    /** (5.7.7) BICHO: primeiro toque (som) -> depois de 4 s, o segundo dá o susto. */
    private static final Map<BlockPos, Long> TOQUES_CAIXA = new HashMap<>();

    /** (5.7.7) uma animação de apagamento em andamento. */
    private record Apagando(ServerLevel nivel, Vec3 pos, long fim) {
    }

    /** (5.7.6) RASTRO DE CAOS: manchas que FICAM no chão por onde o vazio passou. */
    private static final List<Mancha> MANCHAS = new ArrayList<>();
    private static final Map<UUID, double[]> ULTIMA_MANCHA = new HashMap<>();

    /** (5.7.6) uma mancha do rastro: ponto do chão + até quando ela solta partícula. */
    private record Mancha(ServerLevel nivel, double x, double y, double z, long fim, double fase) {
    }

    /** (5.4.1) duração do ogg do colapso (75 s) — pra repetir se o evento durar mais. */
    public static final long COLAPSO_MS = 75000L;
    public static final double BICHO_RAIO = 3.0;
    public static final int BICHO_MUSICA_SEG = 36;

    /** um bloco que foi cavado pela fenda (nível + posição + estado original). */
    private record Fenda(ServerLevel nivel, BlockPos pos, BlockState estado) {
    }

    /**
     * (5.6.0) Só terra, pedra e areia entram na fenda. Nada de baú, forno,
     * bloco de valor ou bloco com dados (block entity) — e nada de água/lava.
     * Assim o evento NUNCA destrói a build de ninguém: tudo volta no fim.
     */
    private static final java.util.Set<net.minecraft.world.level.block.Block> CAVEIS =
            java.util.Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
                    Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MUD, Blocks.CLAY, Blocks.GRAVEL,
                    Blocks.SAND, Blocks.RED_SAND, Blocks.STONE, Blocks.DEEPSLATE, Blocks.GRANITE,
                    Blocks.DIORITE, Blocks.ANDESITE, Blocks.TUFF, Blocks.CALCITE, Blocks.SANDSTONE,
                    Blocks.PACKED_MUD, Blocks.MOSS_BLOCK, Blocks.SNOW_BLOCK, Blocks.DIRT_PATH,
                    Blocks.FARMLAND, Blocks.DRIPSTONE_BLOCK, Blocks.BLACKSTONE, Blocks.BASALT,
                    Blocks.NETHERRACK, Blocks.SOUL_SAND, Blocks.SOUL_SOIL);

    private static final class Caixa {
        final BlockPos pos;
        final long inicio;
        long proximoAviso = 0;
        /** (5.7.3) próximo instante de TOCAR a música de novo. */
        long proximaMusica = 0;
        /** (5.7.3) quando essa caixa tocou o som pela última vez (anti-spam). */
        long ultimoSom = 0;

        Caixa(BlockPos pos) {
            this.pos = pos;
            this.inicio = System.currentTimeMillis();
            this.proximaMusica = this.inicio + 30_000L;
        }
    }

    /** Chamado do ServerEvents (PostTick) — 20x por segundo. */
    public static void tick(MinecraftServer server) {
        try {
            tickTerremoto(server);
            tickDeus(server);
            tickSaidas(server);
            tickCaixas(server);
            tickExilios(server);
            tickManchas(server);
            tickApagando(server);
            tickExilioMundo(server);
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: tick do evento Neptune falhou (o jogo segue)", t);
        }
    }

    // =============================================================== TERREMOTO
    public static boolean terremotoAtivo() {
        return terremotoAtivo;
    }

    public static void iniciarTerremoto(MinecraftServer server, int segundos) {
        terremotoAtivo = true;
        terremotoFim = System.currentTimeMillis() + Math.max(10, segundos) * 1000L;
        terremotoForca = 1.0F;
        proximoRumble = 0;
        proximoColapso = System.currentTimeMillis();
        proximoPulso = System.currentTimeMillis();
        proximaFendaSolta = System.currentTimeMillis() + 2500;
        faseTravado = true;   // começa travado (o primeiro baque)
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.displayClientMessage(Component.literal(
                    "\u00a78\u00a7oA terra está cedendo. O universo está se corrigindo..."), false);
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "\u00a7c\u00a7l\u2694 TERREMOTO  \u00a77— o chão está rachando! (\u00a7c"
                        + segundos + "s\u00a77)").withStyle(ChatFormatting.RED), false);
        // (5.7.5) O TRANCO INICIAL ACONTECE NO MUNDO, não na tela.
        // O dono apontou a rachadura desenhada na lente como o que mais
        // incomodava ("não quero essa animação"). Agora a abertura é o chão
        // quebrando DE VERDADE embaixo de cada jogador: duas fendas reais
        // (blocos viram ar), pedra e poeira do próprio bloco voando pra cima,
        // estalo de terra e o tremor da câmera começando junto.
        java.util.Random sorte = new java.util.Random();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new NeptunePackets.Cena(
                    NeptunePackets.Cena.CENA_ESTOURO, 1.0F, 900, System.nanoTime()));
            if (p.level() instanceof ServerLevel nv) {
                aberturaNoMundo(nv, p, sorte);
            }
        }
        enviarTerremoto(server, true, 1.0F, segundos, true);
    }

    /**
     * (5.7.7) PARTÍCULA DA AURA — quem é o vazio NÃO pode ficar cego.
     *
     * <p>O dono mandou print: em primeira pessoa a tela dele ficava PRETA (o
     * manto de SQUID_INK/fumaça nascia justo na altura do rosto). Ele pediu:
     * <i>"deixe de uma maneira que só dá para ver no F5, e os outros players
     * poder ver"</i>.
     *
     * <p>Como o servidor não sabe se o cara está em F1 ou F5, a regra é pela
     * ALTURA: tudo que nasce acima de 1,25 do corpo (peito, ombro, rosto) vai
     * SÓ para os outros jogadores; o que é baixo (chão, pernas) vai pra todos.
     * Assim o vazio enxerga o mundo, e no F5 (câmera atrás) ainda vê o manto
     * subindo e os raios.
     */
    private static void auraPart(ServerLevel nv, ServerPlayer dono, net.minecraft.core.particles.ParticleOptions opt,
            double x, double y, double z, int n, double dx, double dy, double dz, double sp) {
        boolean alto = y > dono.getY() + 0.95;
        for (ServerPlayer o : nv.players()) {
            if (o == dono && alto) {
                continue;                      // F1 dele fica limpo
            }
            nv.sendParticles(o, opt, false, x, y, z, n, dx, dy, dz, sp);
        }
    }

    /**
     * (5.7.6) POEIRA SEM VERDE — o dono: "tira as particulas verdes que saem do
     * chao".
     *
     * <p>O problema não era a cor escolhida no código: era usar o bloco REAL do
     * chão. Em cima de grama, musgo, folha ou mato o jogo cospe partícula
     * VERDE — e no meio de um terremoto isso parece confete. Aqui qualquer
     * bloco de vegetação vira <b>terra grossa</b> (marrom), e o resto (pedra,
     * areia, neve…) continua sendo o material de verdade.
     */
    private static BlockState estadoPoeira(BlockState estado) {
        if (estado == null || estado.isAir()) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        var b = estado.getBlock();
        if (b == Blocks.GRASS_BLOCK || b == Blocks.MOSS_BLOCK || b == Blocks.MOSS_CARPET
                || b == Blocks.MYCELIUM || b == Blocks.PODZOL || b == Blocks.FARMLAND
                || b == Blocks.DIRT_PATH || b == Blocks.SHORT_GRASS
                || b == Blocks.TALL_GRASS || b == Blocks.FERN || b == Blocks.LARGE_FERN
                || b == Blocks.OAK_LEAVES || b == Blocks.BIRCH_LEAVES
                || b == Blocks.SPRUCE_LEAVES || b == Blocks.JUNGLE_LEAVES
                || b == Blocks.ACACIA_LEAVES || b == Blocks.DARK_OAK_LEAVES
                || b == Blocks.MANGROVE_LEAVES || b == Blocks.CHERRY_LEAVES
                || b == Blocks.AZALEA_LEAVES || b == Blocks.FLOWERING_AZALEA_LEAVES
                || b == Blocks.VINE || b == Blocks.GLOW_LICHEN || b == Blocks.LILY_PAD) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        return estado;
    }

    /**
     * (5.7.5) ABERTURA DO TERREMOTO NO MUNDO — o "baque" que o dono pediu.
     *
     * <p>Em vez de desenhar uma rachadura na tela, o chão aqui racha de verdade:
     * abre duas fendas reais embaixo/ao lado do jogador (blocos viram ar e são
     * guardados pra voltar no fim), sobe pedra e poeira DO PRÓPRIO bloco em
     * volta, e a terra "cospe" detritos pra cima. O som é o estalo de pedra
     * quebrando — nada de zumbido.
     */
    private static void aberturaNoMundo(ServerLevel nv, ServerPlayer p, java.util.Random r) {
        try {
            Vec3 pos = p.position();
            // duas fendas reais: uma embaixo e outra num raio de 2 a 4 blocos
            fendaDePassagem(nv, pos, r);
            Vec3 perto = pos.add(r.nextDouble() * 6 - 3, 0, r.nextDouble() * 6 - 3);
            fendaDePassagem(nv, perto, r);
            // pedra/terra saltando: pega o bloco REAL do chão pra poeira ser a certa
            BlockPos chao = p.blockPosition().below();
            BlockState estado = estadoPoeira(nv.getBlockState(chao));
            for (int i = 0; i < 3; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double rr = 0.6 + r.nextDouble() * 2.4;
                double x = pos.x + Math.cos(a) * rr;
                double z = pos.z + Math.sin(a) * rr;
                // pedra subindo (é a terra quebrando)
                nv.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                ParticleTypes.BLOCK, estado),
                        x, pos.y + 0.15, z, 18, 0.35, 0.25, 0.35, 0.22);
                // poeira caindo do alto (o que levanta da cratera)
                nv.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                ParticleTypes.FALLING_DUST, estado),
                        x, pos.y + 2.2, z, 10, 0.4, 0.5, 0.4, 0.02);
            }
            nv.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.WITHER_BREAK_BLOCK,
                    SoundSource.BLOCKS, 1.0F, 0.5F);
            nv.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.GRAVEL_BREAK,
                    SoundSource.BLOCKS, 1.2F, 0.6F);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: abertura do terremoto no mundo falhou", t);
        }
    }

    public static void pararTerremoto(MinecraftServer server, boolean silencioso) {
        boolean estava = terremotoAtivo;
        terremotoAtivo = false;
        faseTravado = false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            p.removeEffect(MobEffects.DIG_SLOWDOWN);
            imobilizar(p, false);
            pesado(p, false);
            // (5.7.9) BUG DO DONO: "/terremoto parar calava o tremor mas o SOM
            // continuava". O áudio do colapso é tocado NO SERVIDOR
            // (playNotifySound = pacote de som), então o cliente só para de
            // tocar se RECEBER o StopSound — exatamente como foi feito no
            // /vazio desligar. Manda parar os dois sons do evento em cada
            // cliente (nome do som + fonte nula = mata todas as instâncias).
            try {
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                        RPTagMod.SOM_TERREMOTO_COLAPSO.get().getLocation(), null));
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                        RPTagMod.SOM_RUMBLE.get().getLocation(), null));
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: nao consegui parar o som do terremoto", t);
            }
        }
        fecharFendas(server);
        enviarTerremoto(server, false, 0.0F, 0, false);
        if (estava && !silencioso) {
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "\u00a77\u00a7oO chão parou. Por enquanto.").withStyle(ChatFormatting.GRAY), false);
        }
    }

    private static void enviarTerremoto(MinecraftServer server, boolean ativo, float forca, int seg,
            boolean travado) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new NeptunePackets.Terremoto(ativo, forca, seg, travado));
        }
    }

    private static void tickTerremoto(MinecraftServer server) {
        if (!terremotoAtivo) {
            return;
        }
        long agora = System.currentTimeMillis();
        if (agora >= terremotoFim) {
            pararTerremoto(server, false);
            return;
        }
        int restam = (int) Math.max(1, (terremotoFim - agora) / 1000L);

        // ---- COLAPSO: o áudio do dono agora tem 75 s (5.4.1). Se o evento
        // passar disso, ele volta (o som não pode "acabar" no meio do terremoto).
        if (agora >= proximoColapso) {
            proximoColapso = agora + COLAPSO_MS;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.playNotifySound(RPTagMod.SOM_TERREMOTO_COLAPSO.get(),
                        SoundSource.AMBIENT, 1.0F, 1.0F);
            }
        }
        // ---- rumble de reforço, BEM de vez em quando e baixinho (é cama, não som)
        if (agora >= proximoRumble) {
            proximoRumble = agora + 12000;
            for (ServerServerHold h : ServerServerHold.de(server)) {
                ServerLevel nv = h.nivel();
                Vec3 v = h.pos();
                nv.playSound(null, v.x, v.y, v.z, RPTagMod.SOM_RUMBLE.get(),
                        SoundSource.AMBIENT, 0.45F, 1.0F);
            }
        }

        // ---- PULSOS: 6 s estuncado, 4 s livre (dá pra correr)
        if (agora >= proximoPulso) {
            faseTravado = !faseTravado;
            proximoPulso = agora + (faseTravado ? 6000 : 4000);
            // avisa o cliente (visão dupla + blur mudam no pulso)
            enviarTerremoto(server, true, 1.0F, restam, faseTravado);
            if (faseTravado) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 130, 2, false, false, false));
                    p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 130, 1, false, false, false));
                    // (5.7.6) O DONO PEDIU: "o player não deve ficar 100%
                    // parado, ele deve conseguir andar, mais lento, mas
                    // andar". Então SAIU o imobilizar (que zerava a
                    // velocidade): agora é peso — anda arrastado e pula
                    // baixo, mas se mexe.
                    pesado(p, true);
                    boolean caiu = abrirFenda(p);
                    if (caiu) {
                        PacketDistributor.sendToPlayer(p, new NeptunePackets.Cena(
                                NeptunePackets.Cena.CENA_QUEDA, 1.0F, 3200, System.nanoTime()));  // 5.7.2: era 5500 (preto demais)
                        p.displayClientMessage(Component.literal(
                                "\u00a74\u00a7lO CH\u00c3O SE ABRIU! \u00a7cVoc\u00ea caiu \u2014 o corpo pesa."), true);
                    } else {
                        PacketDistributor.sendToPlayer(p, new NeptunePackets.Cena(
                                NeptunePackets.Cena.CENA_RACHADURA, 0.7F, 2800, System.nanoTime()));  // 5.7.2: era 4500
                        p.displayClientMessage(Component.literal(
                                "\u00a78Voc\u00ea cambaleia \u2014 anda arrastado, mas anda."), true);
                    }
                }
            } else {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    p.removeEffect(MobEffects.DIG_SLOWDOWN);
                    imobilizar(p, false);
                    pesado(p, false);
                    p.displayClientMessage(Component.literal(
                            "\u00a7aAgora! Corre!"), true);
                }
                // a terra se fecha atrás de quem caiu (antes de sair do buraco)
                fecharFendas(server);
            }
        }

        // ---- (5.7.5) ESCOMBROS CAINDO DO ALTO: a cada 1,5 s chove terra/pedrinha
        // em volta de cada jogador (pedaços do "teto" que está desabando). É o
        // que dá a sensação de mundo caindo — junto com as fendas no chão.
        if (agora >= proximoEscombro) {
            proximoEscombro = agora + 1500L;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (!(p.level() instanceof ServerLevel nv)) {
                    continue;
                }
                BlockPos chao = p.blockPosition().below();
                BlockState baixo = nv.getBlockState(chao);
                BlockState pedra = estadoPoeira(baixo);
                for (int i = 0; i < 4; i++) {
                    double a = Math.random() * Math.PI * 2;
                    double rr = 0.8 + Math.random() * 3.2;
                    double x = p.getX() + Math.cos(a) * rr;
                    double z = p.getZ() + Math.sin(a) * rr;
                    double y = p.getY() + 2.6 + Math.random() * 1.8;
                    nv.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                    ParticleTypes.BLOCK, pedra),
                            x, y, z, 3, 0.12, 0.10, 0.12, 0.06);
                }
            }
        }

        // ---- (5.7.2) FENDAS QUE QUEBRAM BLOCOS: a cada ~5 s abre uma rachadura
        // fina no chão em volta de um jogador (blocos quebrando na frente dele,
        // com poeira e estalo). Tudo é restaurado no fim (fecharFendas).
        if (agora >= proximaFendaSolta) {
            proximaFendaSolta = agora + 2800 + (long) (Math.random() * 1800);   // 5.7.5: mais seguido
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.isCreative() || p.isSpectator()) {
                    continue;
                }
                if (p.level() instanceof ServerLevel nv && Math.random() < 0.75) {
                    // (5.7.7) "fendas devem acontecer aleatoriamente": antes
                    // abria sempre no pé do jogador (padrão previsível). Agora
                    // é num ponto sorteado de 2 a 12 blocos em volta dele,
                    // em qualquer direção — pode abrir do lado, atrás, longe.
                    double ang = Math.random() * Math.PI * 2;
                    double dist = 2.0 + Math.random() * 10.0;
                    Vec3 alvo = p.position().add(Math.cos(ang) * dist, 0,
                            Math.sin(ang) * dist);
                    fendaDePassagem(nv, alvo, new java.util.Random());
                }
            }
        }

        // ---- POEIRA DE TERRA (5.4.1): SAIU a fumaça branca (CAMPFIRE_COSY_SMOKE)
        // porque parecia bolha de sabão e dava cara de queimado. O que combina
        // com terra cedendo é o MATERIAL do chão: FALLING_DUST (poeira caindo do
        // bloco) + BLOCK (pedrisco subindo do impacto) + um pouco de ASH.
        if (agora % 3 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerLevel nv = (ServerLevel) p.level();
                double px = p.getX();
                double py = p.getY();
                double pz = p.getZ();
                // (5.7.7) era um ANEL girando (dava pra ver o padrão de relógio):
                // agora são 5 pontos SORTEADOS em volta a cada quadro.
                for (int q = 0; q < 5; q++) {
                    double a = Math.random() * Math.PI * 2;
                    double r = 1.2 + Math.random() * 5.2;
                    double x = px + Math.cos(a) * r;
                    double z = pz + Math.sin(a) * r;
                    BlockPos chao = BlockPos.containing(x, py - 0.4, z);
                    if (nv.getBlockState(chao).isAir()) {
                        continue;                       // não faz poeira no ar
                    }
                    // (5.7.6) poeira SEM VERDE: grama/folha viram terra grossa
                    BlockState estado = estadoPoeira(nv.getBlockState(chao));
                    // poeira do próprio chão subindo devagar
                    nv.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                    ParticleTypes.FALLING_DUST, estado),
                            x, py + 0.15, z, 2, 0.15, 0.05, 0.15, 0.02);
                    // pedrisco saindo do chão quando o tranco é forte
                    if (faseTravado && r > 3.4) {
                        nv.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                        ParticleTypes.BLOCK, estado),
                                x, py + 0.05, z, 2, 0.06, 0.10, 0.06, 0.25);
                    }
                }
                // cinza fina caindo (pouca, e só no pulso travado)
                if (faseTravado && agora % 9 == 0) {
                    nv.sendParticles(ParticleTypes.ASH, px, py + 2.2, pz, 3, 1.4, 0.6, 1.4, 0.01);
                }
            }
        }
    }

    /** atalho pra ter a posição de um jogador como "fonte de som do mundo". */
    private record ServerServerHold(ServerLevel nivel, Vec3 pos) {
        static List<ServerServerHold> de(MinecraftServer server) {
            List<ServerServerHold> l = new ArrayList<>();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                l.add(new ServerServerHold((ServerLevel) p.level(), p.position()));
            }
            return l;
        }
    }

    // ============================================================ FENDA / QUEDA
    /**
     * (5.6.0) ABRE A FENDA embaixo do jogador: cava um poço de 2 de
     * profundidade (3x3) e comprido de rachaduras saindo dele. Ele cai dentro,
     * fica IMÓVEL (velocidade e pulo zerados) e a terra fecha depois.
     *
     * <p>Regras de segurança (o evento não pode estragar build):
     * só entra na fenda bloco de {@link #CAVEIS} (terra/pedra/areia), nunca
     * bloco com block entity (baú, forno…), nunca água/lava, e TUDO é
     * restaurado no fim ({@link #fecharFendas}).
     *
     * @return true se o jogador realmente afundou na fenda
     */
    public static boolean abrirFenda(ServerPlayer p) {
        try {
            if (!(p.level() instanceof ServerLevel nv)) {
                return false;
            }
            if (p.isCreative() || p.isSpectator() || p.isPassenger()) {
                return false;
            }
            int bx = p.blockPosition().getX();
            int bz = p.blockPosition().getZ();
            int by = p.blockPosition().getY();
            // o chão é o primeiro bloco cavável logo abaixo dos pés
            BlockPos chao = null;
            for (int dy = 1; dy <= 3 && chao == null; dy++) {
                BlockPos c = new BlockPos(bx, by - dy, bz);
                if (CAVEIS.contains(nv.getBlockState(c).getBlock())) {
                    chao = c;
                }
            }
            if (chao == null) {
                return false;            // piso de madeira/pedra trabalhada: não mexe
            }
            java.util.Random r = new java.util.Random(p.getUUID().getMostSignificantBits()
                    ^ System.nanoTime());
            return cavarFenda(nv, chao, r, p);
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: nao consegui abrir a fenda do terremoto", t);
            return false;
        }
    }

    /**
     * (5.6.0) abre a fenda num lugar do mundo (sem jogador) — é o que o
     * {@code /terremoto fenda} usa quando o admin quer testar no chão em que
     * está pisando, e é o mesmo motor que roda embaixo dos jogadores.
     */
    public static boolean abrirFendaEm(ServerLevel nv, BlockPos perto) {
        try {
            BlockPos chao = null;
            for (int dy = 0; dy <= 3 && chao == null; dy++) {
                BlockPos c = perto.offset(0, -dy, 0);
                if (CAVEIS.contains(nv.getBlockState(c).getBlock())) {
                    chao = c;
                }
            }
            if (chao == null) {
                return false;
            }
            java.util.Random r = new java.util.Random(nv.getRandom().nextLong());
            return cavarFenda(nv, chao, r, null);
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: nao consegui abrir a fenda no mundo", t);
            return false;
        }
    }

    /** o miolo: cava a cova + as rachaduras e (se houver jogador) marca a queda. */
    private static boolean cavarFenda(ServerLevel nv, BlockPos chao, java.util.Random r,
            ServerPlayer p) {
        {
            // (1) a COVA: 3x3, dois blocos de profundidade -> ele cai 2
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    cavar(nv, chao.offset(dx, 0, dz), r);
                    cavar(nv, chao.offset(dx, -1, dz), r);
                }
            }
            // (2) as RACHADURAS: 4 a 6 braços serpenteando pela superfície
            int bracos = 4 + r.nextInt(3);
            for (int b = 0; b < bracos; b++) {
                double ang = (Math.PI * 2 * b / bracos) + (r.nextDouble() - 0.5) * 0.9;
                int x = chao.getX();
                int z = chao.getZ();
                int comp = 5 + r.nextInt(6);
                for (int i = 0; i < comp; i++) {
                    ang += (r.nextDouble() - 0.5) * 0.75;
                    x += (int) Math.round(Math.cos(ang));
                    z += (int) Math.round(Math.sin(ang));
                    // segue o relevo: procura o primeiro bloco cavável na coluna
                    for (int dy = -2; dy <= 3; dy++) {
                        if (cavar(nv, new BlockPos(x, chao.getY() + dy, z), r)) {
                            break;
                        }
                    }
                }
            }
            if (p != null) {
                QUEDA.put(p.getUUID(), new long[]{ chao.getY() });
            }
            // o baque da terra se abrindo
            nv.playSound(null, chao, net.minecraft.sounds.SoundEvents.WITHER_BREAK_BLOCK,
                    SoundSource.BLOCKS, 1.0F, 0.55F);
            nv.playSound(null, chao, net.minecraft.sounds.SoundEvents.DEEPSLATE_BREAK,
                    SoundSource.BLOCKS, 1.0F, 0.6F);
            return true;
        }
    }

    /**
     * (5.7.2) FENDA DE PASSAGEM: o dono pediu "fendas quebrando blocos, para dar
     * mais imersão". Enquanto o terremoto está ativo, de tempos em tempos abre
     * uma rachadura FINA (1 bloco de largura, 4 a 8 de comprimento, 1 de
     * profundidade) no chão em volta de um jogador — os blocos quebram na frente
     * dele, com a poeira do próprio bloco e o estalo. Tudo volta no
     * {@link #fecharFendas} (mesmo motor, mesmas regras: só bloco CAVEl, nunca
     * baú/forno, nunca água/lava).
     *
     * @return quantidade de blocos quebrados (0 = não achou chão bom ali)
     */
    public static int fendaDePassagem(ServerLevel nv, Vec3 centro, java.util.Random r) {
        try {
            int quantos = 0;
            double ang = r.nextDouble() * Math.PI * 2;
            double raio = 2.0 + r.nextDouble() * 6.0;
            int x = (int) Math.floor(centro.x + Math.cos(ang) * raio);
            int z = (int) Math.floor(centro.z + Math.sin(ang) * raio);
            int comp = 4 + r.nextInt(5);
            for (int i = 0; i < comp; i++) {
                ang += (r.nextDouble() - 0.5) * 0.6;
                x += (int) Math.round(Math.cos(ang));
                z += (int) Math.round(Math.sin(ang));
                for (int dy = -2; dy <= 3; dy++) {
                    BlockPos c = new BlockPos(x, (int) Math.floor(centro.y) + dy, z);
                    if (cavar(nv, c, r)) {
                        quantos++;
                        break;
                    }
                }
            }
            if (quantos > 0) {
                nv.playSound(null, BlockPos.containing(centro),
                        net.minecraft.sounds.SoundEvents.GRAVEL_BREAK,
                        SoundSource.BLOCKS, 0.9F, 0.55F + r.nextFloat() * 0.2F);
            }
            return quantos;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: fenda de passagem falhou", t);
            return 0;
        }
    }

    /** cava um bloco (se for permitido) e guarda o original pra fechar depois. */
    private static boolean cavar(ServerLevel nv, BlockPos pos, java.util.Random r) {
        if (CAVADOS.contains(pos.asLong())) {
            return false;
        }
        if (pos.getY() <= nv.getMinBuildHeight() + 1) {
            return false;
        }
        BlockState st = nv.getBlockState(pos);
        if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty()) {
            return false;
        }
        if (!CAVEIS.contains(st.getBlock())) {
            return false;
        }
        nv.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        FENDAS.add(new Fenda(nv, pos.immutable(), st));
        CAVADOS.add(pos.asLong());
        // poeira do próprio bloco saindo da boca da fenda
        nv.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                        ParticleTypes.BLOCK, st),
                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                7, 0.35, 0.25, 0.35, 0.05);
        if (r.nextInt(3) == 0) {
            nv.playSound(null, pos, r.nextBoolean()
                            ? net.minecraft.sounds.SoundEvents.STONE_BREAK
                            : net.minecraft.sounds.SoundEvents.GRAVEL_BREAK,
                    SoundSource.BLOCKS, 0.85F, 0.65F + r.nextFloat() * 0.5F);
        }
        return true;
    }

    /**
     * (5.6.0) FECHA TODAS as fendas: tira quem caiu (sobe de volta pro nível do
     * chão) e devolve cada bloco no lugar. Roda no fim de cada pulso, ao parar
     * o terremoto e se o evento for desligado no meio.
     */
    public static void fecharFendas(MinecraftServer server) {
        try {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                long[] q = QUEDA.remove(p.getUUID());
                if (q == null) {
                    continue;
                }
                // (5.6.0) se ele morreu ou trocou de dimensão no meio, não
                // teleporta ninguém pra cima de um buraco que não é dele
                if (p.isDeadOrDying() || p.isRemoved()) {
                    continue;
                }
                if (p.level() instanceof ServerLevel nv && Math.abs(p.getY() - q[0]) < 8.0) {
                    p.teleportTo(nv, p.getX(), q[0] + 1.0, p.getZ(),
                            java.util.Set.of(), p.getYRot(), p.getXRot());
                    // (5.6.0) poeira de TERRA fechando o buraco — nada de
                    // fumaça branca (o dono já vetou: parecia bolha/queimado)
                    nv.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                    ParticleTypes.FALLING_DUST, Blocks.DIRT.defaultBlockState()),
                            p.getX(), q[0] + 0.9, p.getZ(), 28, 0.8, 0.2, 0.8, 0.02);
                    nv.playSound(null, p.blockPosition(),
                            net.minecraft.sounds.SoundEvents.GRAVEL_PLACE,
                            SoundSource.BLOCKS, 1.0F, 0.6F);
                }
            }
            for (Fenda f : FENDAS) {
                f.nivel().setBlockAndUpdate(f.pos(), f.estado());
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: erro ao fechar as fendas do terremoto", t);
        } finally {
            FENDAS.clear();
            CAVADOS.clear();
            QUEDA.clear();
        }
    }

    /** quantos blocos estão cavados agora (o /terremoto status mostra). */
    public static int blocosCavados() {
        return FENDAS.size();
    }

    /**
     * (5.6.0) IMOBILIZA o jogador de verdade: velocidade e força de pulo em
     * zero por modificador de atributo (efeito de poção não dava conta — o
     * jogador ainda pulava pra fora da fenda). Tudo é temporal: sai ao soltar.
     */
    /** (5.6.0) versão pública (o /terremoto fenda usa pra testar). */
    public static void imobilizarAlvo(ServerPlayer p, boolean preso) {
        imobilizar(p, preso);
    }

    private static void imobilizar(ServerPlayer p, boolean preso) {
        try {
            var vel = p.getAttribute(Attributes.MOVEMENT_SPEED);
            var pulo = p.getAttribute(Attributes.JUMP_STRENGTH);
            if (preso) {
                if (vel != null) {
                    vel.addOrUpdateTransientModifier(new AttributeModifier(MOD_RAIZ, -1.0,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                if (pulo != null) {
                    pulo.addOrUpdateTransientModifier(new AttributeModifier(MOD_RAIZ, -0.42,
                            AttributeModifier.Operation.ADD_VALUE));
                }
            } else {
                if (vel != null) {
                    vel.removeModifier(MOD_RAIZ);
                }
                if (pulo != null) {
                    pulo.removeModifier(MOD_RAIZ);
                }
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.error("RP Tag: nao consegui (des)imobilizar o jogador", t);
        }
    }

    /**
     * (5.7.6) PESO DO TERREMOTO — anda devagar, mas ANDA.
     *
     * <p>O dono: <i>"no terremoto o player nao deve ficar 100% parado, ele deve
     * conseguir andar, mais lento, mas andar de certa forma"</i>. Então saiu o
     * {@link #imobilizar} (que zerava a velocidade por atributo, usado só pelo
     * {@code /terremoto fenda}) e entrou este: <b>-45% de velocidade</b> e
     * <b>-0,20 de força de pulo</b> — ele se arrasta e pula baixinho, mas tem
     * controle. Tudo em modificador temporal (some quando o pulso acaba).
     */
    private static void pesado(ServerPlayer p, boolean lento) {
        try {
            var vel = p.getAttribute(Attributes.MOVEMENT_SPEED);
            var pulo = p.getAttribute(Attributes.JUMP_STRENGTH);
            if (lento) {
                if (vel != null) {
                    vel.addOrUpdateTransientModifier(new AttributeModifier(MOD_LENTO, -0.45,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                }
                if (pulo != null) {
                    pulo.addOrUpdateTransientModifier(new AttributeModifier(MOD_LENTO, -0.20,
                            AttributeModifier.Operation.ADD_VALUE));
                }
            } else {
                if (vel != null) {
                    vel.removeModifier(MOD_LENTO);
                }
                if (pulo != null) {
                    pulo.removeModifier(MOD_LENTO);
                }
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: peso do terremoto falhou (o jogo segue)", t);
        }
    }

    // =================================================================== DEUS
    public static boolean ehDeus(UUID id) {
        return DEUSES.getOrDefault(id, false);
    }

    public static void ligarDeus(ServerPlayer p) {
        DEUSES.put(p.getUUID(), true);
        TRILHA.remove(p.getUUID());
        // (5.7.4) a aura vai pra TODO MUNDO com o UUID de quem é o vazio: é
        // assim que os outros clientes desenham os raios 3D em volta dele.
        for (ServerPlayer outro : p.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(outro,
                    new NeptunePackets.Aura(p.getUUID(), outro == p, outro == p));
        }
        p.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "\u00a78\u00a7l\u2588 \u00a75\u00a7lO VAZIO CAMINHA ENTRE NÓS\u00a78 \u00a7l\u2588")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        p.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "\u00a78Uma escuridão sem fundo acompanha \u00a7f" + p.getName().getString()
                        + "\u00a78. Não chegue perto.").withStyle(ChatFormatting.DARK_GRAY), false);
        // (5.7.6) A TRANSFORMAÇÃO: rajada de RAIOS VERMELHOS por 2,2 s
        if (p.level() instanceof ServerLevel nv) {
            surtoTransformacao(nv, p);
        }
    }

    /**
     * (5.7.6) A TRANSFORMAÇÃO — "as particulas vermelhas devem virar raios
     * vermelhos mais intensos, para dar uma diferenciada na transformação".
     *
     * <p>Quando o admin liga o modo vazio, o corpo dele abre: 28 raios
     * vermelhos de verdade (linhas grossas de partícula saindo do peito em
     * 360°, e também pra cima), uma onda de choque rente ao chão e um boom.
     * Depois disso entra a aura normal — agora bem mais preta.
     */
    private static void surtoTransformacao(ServerLevel nv, ServerPlayer p) {
        try {
            Vec3 pos = p.position();
            DustParticleOptions fogo = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.06f, 0.02f), 1.9f);
            DustParticleOptions brasa = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.35f, 0.08f), 1.3f);
            DustParticleOptions negro = new DustParticleOptions(new org.joml.Vector3f(0.02f, 0.0f, 0.03f), 2.4f);
            for (int r = 0; r < 28; r++) {
                double az = (r / 28.0) * Math.PI * 2 + Math.random() * 0.12;
                boolean alto = r % 4 == 0;
                double incl = alto ? 1.05 + Math.random() * 0.5 : (Math.random() - 0.5) * 0.45;
                double alcance = 3.2 + Math.random() * 4.2;
                double y0 = pos.y + 1.0;
                for (int k = 1; k <= 14; k++) {
                    double f = k / 14.0;
                    double serr = (Math.random() - 0.5) * 0.20 * f;
                    double x = pos.x + Math.cos(az) * Math.cos(incl) * alcance * f + serr;
                    double z = pos.z + Math.sin(az) * Math.cos(incl) * alcance * f + serr;
                    double y = y0 + Math.sin(incl) * alcance * f;
                    nv.sendParticles(f > 0.35 ? brasa : fogo, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                    if (k % 4 == 0) {
                        nv.sendParticles(negro, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
                    }
                }
            }
            // onda de choque rente ao chão (aro de brasa abrindo)
            for (int a = 0; a < 64; a++) {
                double ang = (a / 64.0) * Math.PI * 2;
                double rr = 2.6;
                nv.sendParticles(brasa, pos.x + Math.cos(ang) * rr, pos.y + 0.08,
                        pos.z + Math.sin(ang) * rr, 1, 0.0, 0.0, 0.0, 0.0);
            }
            // (5.7.7) SAIU o som que EU tinha posto aqui (WITHER_SPAWN): o dono
            // disse "tira esse som que você adicionou no vazio... ficou
            // estranho; deixa apenas aquele que eu te mandei". A transformação
            // agora é só VISUAL — o som fica por conta do SOM_VAZIO original.
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: surto de transformacao falhou (o jogo segue)", t);
        }
    }

    public static void desligarDeus(ServerPlayer p) {
        DEUSES.remove(p.getUUID());
        TRILHA.remove(p.getUUID());
        for (ServerPlayer outro : p.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(outro, NeptunePackets.Aura.desligada());
            // (5.7.7) BUG: a trilha do deus continuava tocando "no ouvido de
            // todos, infinito" mesmo depois do /vazio desligar. O som é tocado
            // no SERVIDOR (playSound posicionado), então tem que ser MANDADO
            // PARAR em cada cliente.
            try {
                outro.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                        RPTagMod.SOM_TRILHA_DEUS.get().getLocation(), SoundSource.AMBIENT));
                outro.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                        RPTagMod.SOM_VAZIO.get().getLocation(), SoundSource.HOSTILE));
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: nao consegui parar a trilha do vazio", t);
            }
        }
        p.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "\u00a77A escuridão recolheu.").withStyle(ChatFormatting.DARK_GRAY), false);
    }

    /** (5.7.4) próximo trovão grave que os OUTROS vão ouvir (medo). */
    private static long proximoTrovao = 0L;

    private static void tickDeus(MinecraftServer server) {
        long agora = System.currentTimeMillis();
        // ---- TROVÃO DO VAZIO: quem está perto ouve o raio estalar no corpo
        // dele (grave e distante — não é para ser um trovão de tempestade).
        if (agora >= proximoTrovao) {
            proximoTrovao = agora + 4200 + (long) (Math.random() * 2800);
            for (ServerPlayer deus : server.getPlayerList().getPlayers()) {
                if (!ehDeus(deus.getUUID())) {
                    continue;
                }
                for (ServerPlayer ouvinte : server.getPlayerList().getPlayers()) {
                    if (ouvinte == deus || ouvinte.level() != deus.level()) {
                        continue;
                    }
                    double d = ouvinte.position().distanceTo(deus.position());
                    if (d > 32.0) {
                        continue;
                    }
                    float vol = (float) Math.max(0.12, 0.55 * (1.0 - d / 32.0));
                    ouvinte.playNotifySound(net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                            SoundSource.AMBIENT, vol, 0.55F + (float) Math.random() * 0.12F);
                }
            }
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!ehDeus(p.getUUID())) {
                continue;
            }
            ServerLevel nv = (ServerLevel) p.level();
            Vec3 pos = p.position();

            // ---- AURA DO VAZIO (5.7.3) — ESCURIDÃO + RAIOS NO CORPO.
            // 5.7.2 tinha só rachaduras no chão e a fumaça era desenhada NA
            // TELA (virava um borrão preto no céu no F5). O dono pediu: "aura
            // de escuridão e raios negros e vermelhos SAINDO DO PLAYER".
            // Agora tudo é partícula no MUNDO: coluna de escuridão em espiral,
            // raios serrilhados preto/vermelho saindo do peito e dos pés, e as
            // rachaduras do chão continuam (mais discretas).
            auraVazio(nv, p, pos, agora);

            // ---- MEDO em quem chega perto (inimigos tremem)
            for (ServerPlayer outro : server.getPlayerList().getPlayers()) {
                if (outro == p || outro.level() != p.level()) {
                    continue;
                }
                double d = outro.position().distanceTo(pos);
                if (d <= 14.0) {
                    long prox = PROXIMO_MEDO.getOrDefault(outro.getUUID(), 0L);
                    if (agora >= prox) {
                        PROXIMO_MEDO.put(outro.getUUID(), agora + 6000);
                        outro.addEffect(new MobEffectInstance(RPEffects.MEDO, 140, 0, false, false, true));
                        PacketDistributor.sendToPlayer(outro, new FearPayload(7));
                        outro.displayClientMessage(Component.literal(
                                "\u00a78O ar fica pesado. Alguma coisa aqui não é daqui."), true);
                    }
                }
            }

            // ---- TRILHA: toca enquanto ELE ANDA; para quando ele para
            long[] st = TRILHA.get(p.getUUID());
            if (st == null) {
                st = new long[]{ (long) pos.x, (long) pos.z, agora, 0 };
                TRILHA.put(p.getUUID(), st);
            }
            double andou = Math.hypot(pos.x - st[0], pos.z - st[1]);
            boolean tocando = st[3] == 1;
            if (andou >= 0.9) {
                st[0] = (long) pos.x;
                st[1] = (long) pos.z;
                st[2] = agora;
                if (!tocando) {
                    st[3] = 1;
                    nv.playSound(null, p.blockPosition(), RPTagMod.SOM_TRILHA_DEUS.get(),
                            SoundSource.AMBIENT, 1.0F, 1.0F);
                }
            } else if (tocando && agora - st[2] > 2500) {
                // parou de andar há 2,5 s: corta a trilha
                st[3] = 0;
                for (ServerPlayer o : server.getPlayerList().getPlayers()) {
                    if (o.level() == p.level() && o.position().distanceTo(pos) < 48) {
                        o.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                                RPTagMod.SOM_TRILHA_DEUS.get().getLocation(), SoundSource.AMBIENT));
                    }
                }
            }
        }
    }

    /**
     * (5.7.2) RACHADURAS DO VAZIO no chão, em volta do deus.
     *
     * <p>Ancorado no MUNDO (procura o chão de verdade embaixo do jogador e põe
     * tudo em cima do bloco), então quando ele anda as faixas vermelhas vão
     * junto — e cada "racha" serpenteia com o tempo (fase que avança devagar),
     * dando a impressão de que está se abrindo. Só partículas de poeira
     * vermelha (DUST) + um pouco de fumaça escura baixa: nada na tela.
     */
    private static void auraVazio(ServerLevel nv, ServerPlayer p, Vec3 pos, long agora) {
        // limita a taxa: o evento roda a cada tick; aqui desenhamos 1 em 3
        if (agora % 3 != 0) {
            return;
        }
        double fase = agora / 1000.0;

        // ---- as CORES. O "preto" do raio não pode ser dust 0,0,0 (some no
        // escuro): usa um roxo-escuro quase preto, que o jogo desenha visível.
        DustParticleOptions vermelho = new DustParticleOptions(new org.joml.Vector3f(0.92f, 0.02f, 0.03f), 1.5f);
        DustParticleOptions sangue = new DustParticleOptions(new org.joml.Vector3f(0.45f, 0.0f, 0.02f), 1.7f);
        DustParticleOptions negro = new DustParticleOptions(new org.joml.Vector3f(0.03f, 0.0f, 0.05f), 2.1f);
        DustParticleOptions sombra = new DustParticleOptions(new org.joml.Vector3f(0.10f, 0.01f, 0.15f), 1.8f);
        DustParticleOptions brasa = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.12f, 0.05f), 1.2f);

        // ============ (A) O MANTO: O CORPO Some DENTRO DA ESCURIDÃO ============
        // (5.7.6) O dono: "escureça mais a parte de partículas, quero o preto
        // principalmente ficando bizarro, que de certa maneira nem ver o corpo
        // da entidade os players consigam, apenas o rosto se possível".
        //
        // Então agora são 3 camadas de PRETO bem juntas (SQUID_INK + fumaça
        // grande + dust quase preto) subindo em espiral, de 0,05 a 2,35 de
        // altura. Na faixa do ROSTO (1,52 a 1,98) a densidade cai pela metade
        // e entram dois pontos vermelhos (os olhos) — o corpo vira silhueta e
        // o rosto é a única coisa que dá pra enxergar.
        for (int i = 0; i < 16; i++) {
            double a = (i / 16.0) * Math.PI * 2 + fase * 1.15;              // gira
            double r = 0.32 + 0.20 * Math.sin(fase * 1.7 + i * 0.9);        // colado
            double h = ((fase * 0.62 + i * 0.29) % 1.0);                    // sobe
            double y = pos.y + 0.05 + h * 2.30;
            // fecha em cima (cone) e abre na base: é um manto, não um tubo
            double raio = r * (1.0 + 0.55 * (1.0 - h));
            double x = pos.x + Math.cos(a) * raio;
            double z = pos.z + Math.sin(a) * raio;
            boolean rosto = y > pos.y + 1.52 && y < pos.y + 1.98;
            if (rosto && i % 2 == 0) {
                continue;                        // poupa o rosto (dá pra ver)
            }
            auraPart(nv, p, ParticleTypes.SQUID_INK, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            auraPart(nv, p, negro, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            if (i % 3 == 0) {
                auraPart(nv, p, ParticleTypes.LARGE_SMOKE, x, y, z, 1, 0.03, 0.03, 0.03, 0.0);
            }
            if (i % 5 == 0) {
                auraPart(nv, p, sombra, x, y, z, 1, 0.01, 0.01, 0.01, 0.0);
            }
        }
        // os OLHOS: dois pontos vermelhos na altura do rosto, sempre no mesmo
        // lugar do corpo (giram com ele) — é o que "aparece" no meio do preto
        double yaw = Math.toRadians(p.getYRot());
        double ox = Math.cos(yaw) * 0.16, oz = Math.sin(yaw) * 0.16;
        double lx = Math.cos(yaw) * 0.085;
        double lz = Math.sin(yaw) * 0.085;
        double olhoY = pos.y + 1.62;
        for (int s2 = -1; s2 <= 1; s2 += 2) {
            auraPart(nv, p, vermelho, pos.x + ox + lx * s2, olhoY, pos.z + oz + lz * s2,
                    1, 0.012, 0.012, 0.012, 0.0);
            if (agora % 9 == 0) {
                auraPart(nv, p, brasa, pos.x + ox + lx * s2, olhoY, pos.z + oz + lz * s2,
                        1, 0.03, 0.03, 0.03, 0.0);
            }
        }
        // base: a escuridão "pesada" no chão, em volta dos pés
        if (agora % 6 == 0) {
            for (int i = 0; i < 8; i++) {
                double a = Math.random() * Math.PI * 2;
                double r = 0.2 + Math.random() * 1.1;
                nv.sendParticles(ParticleTypes.SQUID_INK,
                        pos.x + Math.cos(a) * r, pos.y + 0.06, pos.z + Math.sin(a) * r,
                        1, 0.02, 0.0, 0.02, 0.0);
            }
        }

        // ================= (B) RAIOS NEGROS E VERMELHOS =================
        // (5.7.7) O dono: "quero que seja tipo uma aura de escuridão e raios
        // negros e vermelhos saindo do player". Agora são 4 raios por disparo
        // (antes 2), mais compridos e mais grossos, saindo do PEITO, dos PÉS e
        // também do ALTO da cabeça — em todas as direções, inclusive pra cima.
        // O miolo é NEGRO (SQUID_INK, que aparece no escuro) e a ponta é
        // VERMELHA viva, com brasa estalando: lê como raio, não como poeira.
        if (agora % 4 == 0) {
            for (int n = 0; n < 4; n++) {
                double ang = Math.random() * Math.PI * 2;
                double alcance = 2.6 + Math.random() * 3.6;
                int pontos = 12 + (int) (Math.random() * 6);
                boolean doPe = n == 1;
                boolean doAlto = n == 3;
                double x0 = pos.x;
                double y0 = doPe ? pos.y + 0.1 : (doAlto ? pos.y + 2.0 : pos.y + 1.0);
                double z0 = pos.z;
                double dx = Math.cos(ang);
                double dz = Math.sin(ang);
                double subida = doPe ? 0.55 : (doAlto ? 1.15 : 0.25);
                double x, y, z;
                for (int k = 1; k <= pontos; k++) {
                    double f = k / (double) pontos;
                    double serrilhado = (Math.random() - 0.5) * 0.42;
                    x = x0 + dx * alcance * f - dz * serrilhado;
                    z = z0 + dz * alcance * f + dx * serrilhado;
                    y = y0 + subida * f * alcance * 0.5 + (Math.random() - 0.5) * 0.16;
                    boolean ponta = f > 0.45;
                    auraPart(nv, p, ponta ? (k % 2 == 0 ? vermelho : brasa) : negro,
                            x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                    // o NEGRO precisa de volume: SQUID_INK em cima do dust
                    if (!ponta) {
                        auraPart(nv, p, ParticleTypes.SQUID_INK, x, y, z,
                                1, 0.02, 0.02, 0.02, 0.0);
                    }
                    if (k % 3 == 0) {
                        auraPart(nv, p, ponta ? brasa : ParticleTypes.SMOKE,
                                x, y, z, 1, 0.01, 0.01, 0.01, 0.0);
                    }
                }
            }
        }

        // ================= (C) RACHADURAS NO CHÃO (discretas) =================
        double chaoY = pos.y;
        var bp = p.blockPosition();
        for (int dy = 0; dy <= 5; dy++) {
            var abaixo = bp.below(dy);
            var estado = nv.getBlockState(abaixo);
            if (estado.isSolidRender(nv, abaixo)) {
                chaoY = abaixo.getY() + 1.02;
                break;
            }
        }
        for (int r = 0; r < 4; r++) {
            double ang = (r / 4.0) * Math.PI * 2 + Math.sin(fase * 0.35 + r * 1.7) * 0.25;
            double comp = 1.2 + 1.1 * Math.abs(Math.sin(fase * 0.27 + r * 2.1));
            for (int k = 1; k <= 5; k++) {
                double f = k / 5.0;
                double lateral = Math.sin(fase * 1.6 + r * 2.3 + k * 0.9) * 0.24 * f;
                double x = pos.x + Math.cos(ang) * comp * f - Math.sin(ang) * lateral;
                double z = pos.z + Math.sin(ang) * comp * f + Math.cos(ang) * lateral;
                nv.sendParticles((k % 3 == 0) ? sangue : vermelho, x, chaoY + 0.02, z, 1, 0.01, 0.0, 0.01, 0.0);
            }
        }
        // cinza subindo bem devagar, colada no corpo (não na tela)
        if (agora % 12 == 0) {
            nv.sendParticles(ParticleTypes.ASH, pos.x, pos.y + 0.1, pos.z, 3, 0.35, 0.30, 0.35, 0.0);
        }

        // ================= (D) RASTRO DE CAOS =================
        // (5.7.6) "faça que por onde ele passar, ficar um rastro de caos e
        // destruição (apenas efeito das partículas ficando pelo local)".
        // Cada 6 ticks, se ele andou pelo menos meio bloco, deixa uma MANCHA
        // no chão — e a mancha é desenhada pelo tickManchas por 22 s.
        if (agora % 6 == 0) {
            double[] ult = ULTIMA_MANCHA.get(p.getUUID());
            if (ult == null || Math.hypot(pos.x - ult[0], pos.z - ult[1]) > 0.5
                    || Math.abs(pos.y - ult[1]) > 1.5) {
                ULTIMA_MANCHA.put(p.getUUID(), new double[]{ pos.x, pos.z, pos.y });
                if (MANCHAS.size() < 220) {
                    MANCHAS.add(new Mancha(nv, pos.x, chaoY, pos.z, agora + 22_000L,
                            Math.random() * Math.PI * 2));
                }
            }
        }
    }

    /**
     * (5.7.7) CINZAS, SOMBRA E ALMA — a marca da perda canônica.
     *
     * <p>Quando o vazio apaga alguém, o corpo não só cai: ele se DESFAZ. Aqui,
     * por 3 s, saem do lugar três coisas ao mesmo tempo:
     * <ul>
     *   <li><b>CINZAS</b> (ASH) subindo devagar, como se o corpo virasse pó;</li>
     *   <li><b>SOMBRA</b> (SQUID_INK + fumaça preta) escorrendo pra baixo e
     *       ficando no chão;</li>
     *   <li><b>ALMA</b> (SOUL) — uma coluna fina de almas subindo reta, que é o
     *       que dá o "ele se foi de verdade".</li>
     * </ul>
     * Tudo visível pra todo mundo: a morte canônica é um evento público.
     */
    private static void tickApagando(MinecraftServer server) {
        if (APAGANDO.isEmpty()) {
            return;
        }
        long agora = System.currentTimeMillis();
        Iterator<Apagando> it = APAGANDO.iterator();
        while (it.hasNext()) {
            Apagando a = it.next();
            if (agora > a.fim()) {
                it.remove();
                continue;
            }
            double k = 1.0 - (a.fim() - agora) / 3000.0;         // 0 -> 1
            ServerLevel nv = a.nivel();
            Vec3 p = a.pos();
            // cinzas subindo
            nv.sendParticles(ParticleTypes.ASH, p.x, p.y + 0.3 + k * 1.4, p.z,
                    6, 0.55, 0.35, 0.55, 0.01);
            nv.sendParticles(ParticleTypes.WHITE_ASH, p.x, p.y + 0.2 + k * 1.1, p.z,
                    3, 0.45, 0.30, 0.45, 0.01);
            // sombra escorrendo (fica baixa, no chão)
            nv.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y + 0.15, p.z,
                    3, 0.5, 0.12, 0.5, 0.0);
            nv.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + 0.45, p.z,
                    2, 0.45, 0.30, 0.45, 0.0);
            // a alma subindo reta
            for (int i = 0; i < 2; i++) {
                double y = p.y + 0.6 + ((k * 3.0 + i * 0.7) % 2.4);
                nv.sendParticles(ParticleTypes.SOUL, p.x + (Math.random() - 0.5) * 0.25,
                        y, p.z + (Math.random() - 0.5) * 0.25, 1, 0.02, 0.02, 0.02, 0.005);
            }
            if (Math.random() < 0.35) {
                nv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x + (Math.random() - 0.5) * 0.5,
                        p.y + 0.4 + Math.random() * 1.2, p.z + (Math.random() - 0.5) * 0.5,
                        1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    /**
     * (5.7.6) O RASTRO QUE FICA: as manchas deixadas pelo vazio continuam
     * soltando fumaça, brasa e rachadura no chão por 22 s (22 s é o tempo que
     * dá pra alguém passar depois e ver que "alguma coisa passou aqui").
     * Nada é escrito no mundo — é só partícula, some sozinho.
     */
    private static void tickManchas(MinecraftServer server) {
        if (MANCHAS.isEmpty()) {
            return;
        }
        long agora = System.currentTimeMillis();
        DustParticleOptions brasa = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.15f, 0.05f), 1.1f);
        DustParticleOptions sangue = new DustParticleOptions(new org.joml.Vector3f(0.55f, 0.02f, 0.02f), 1.6f);
        Iterator<Mancha> it = MANCHAS.iterator();
        while (it.hasNext()) {
            Mancha m = it.next();
            if (agora > m.fim()) {
                it.remove();
                continue;
            }
            // só desenha 1 em 4 ticks por mancha (não pesa o pacote)
            if ((agora + (long) (m.fase() * 100)) % 4 != 0) {
                continue;
            }
            ServerLevel nivel = m.nivel();
            double vida = (m.fim() - agora) / 22_000.0;          // 1 -> 0
            int n = 1 + (int) (3 * vida);
            for (int i = 0; i < n; i++) {
                double a = m.fase() + Math.random() * Math.PI * 2;
                double r = Math.random() * 0.85;
                double x = m.x() + Math.cos(a) * r;
                double z = m.z() + Math.sin(a) * r;
                nivel.sendParticles(ParticleTypes.SQUID_INK, x, m.y() + 0.05, z, 1, 0.01, 0.01, 0.01, 0.0);
                if (Math.random() < 0.5) {
                    nivel.sendParticles(Math.random() < 0.5 ? brasa : sangue,
                            x, m.y() + 0.03, z, 1, 0.01, 0.0, 0.01, 0.0);
                }
                if (Math.random() < 0.25) {
                    nivel.sendParticles(ParticleTypes.ASH, x, m.y() + 0.35, z, 1, 0.05, 0.15, 0.05, 0.0);
                }
            }
        }
    }

    /**
     * (5.7.8) O AMBIENTE DO EXÍLIO — "toda escura porém dá para o player andar,
     * se perder lá dentro e tal".
     *
     * <p>A dimensão era uma laje preta e vazia: não dava nem pra se orientar nem
     * pra se perder. Agora:
     * <ul>
     *   <li>o chão tem camadas (deepslate, blackstone, tufo, sculk) — dá textura
     *       sem claridade;</li>
     *   <li>a cada ~14 s nasce um <b>MONÓLITO</b> no escuro, a 10–25 blocos de
     *       quem está lá: uma coluna de deepslate com um <b>lanterna de alma</b>
     *       no topo. São TODOS iguais — servem de referência e de armadilha: você
     *       acha que voltou pro mesmo lugar;</li>
     *   <li>sussurros e passos de vez em quando (som baixo, sem tela).</li>
     * </ul>
     * Nada é feito fora da dimensão do exílio, e nada é salvo em lugar nenhum
     * além dela.
     */
    private static void tickExilioMundo(MinecraftServer server) {
        ServerLevel exilio = server.getLevel(CHAVE_EXILIO);
        if (exilio == null) {
            return;
        }
        long agora = System.currentTimeMillis();
        boolean podeMonolito = agora >= proximoMonolito;
        boolean podeSussurro = agora >= proximoSussurro;
        if (podeMonolito) {
            proximoMonolito = agora + 14_000L;
        }
        if (podeSussurro) {
            proximoSussurro = agora + 11_000L;
        }
        for (ServerPlayer p : exilio.players()) {
            try {
                // poeira escura nos pés (o chão respira)
                if (agora % 8 == 0) {
                    exilio.sendParticles(ParticleTypes.SQUID_INK,
                            p.getX() + (Math.random() - 0.5) * 1.6, p.getY() + 0.1,
                            p.getZ() + (Math.random() - 0.5) * 1.6, 1, 0.0, 0.02, 0.0, 0.0);
                }
                if (podeSussurro && p.level() == exilio) {
                    p.playNotifySound(RPTagMod.SOM_VAZIO.get(), SoundSource.AMBIENT, 0.28F, 0.62F);
                    if (Math.random() < 0.35) {
                        p.displayClientMessage(Component.literal(
                                "\u00a78\u00a7o...você já passou por aqui."), true);
                    }
                }
                if (podeMonolito) {
                    monólito(exilio, p);
                }
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: ambiente do exilio falhou (o jogo segue)", t);
            }
        }
    }

    /** (5.7.8) nasce um monólito com lanterna de alma a 10–25 blocos do jogador. */
    private static void monólito(ServerLevel nv, ServerPlayer p) {
        double ang = Math.random() * Math.PI * 2;
        double dist = 10 + Math.random() * 15;
        int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist);
        int z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
        if (!nv.isLoaded(new BlockPos(x, p.getBlockY(), z))) {
            nv.getChunk(x >> 4, z >> 4);          // carrega o chunk antes
        }
        int y = nv.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= nv.getMinBuildHeight() + 1) {
            return;
        }
        BlockPos base = new BlockPos(x, y, z);
        if (!nv.getBlockState(base.below()).isSolidRender(nv, base.below())) {
            return;
        }
        int alt = 3 + (int) (Math.random() * 4);
        for (int i = 0; i < alt; i++) {
            BlockPos b = base.above(i);
            if (!nv.getBlockState(b).isAir()) {
                return;                            // não estraga nada que existe
            }
        }
        for (int i = 0; i < alt - 1; i++) {
            nv.setBlockAndUpdate(base.above(i),
                    (i % 3 == 0 ? Blocks.OBSIDIAN : Blocks.DEEPSLATE).defaultBlockState());
        }
        nv.setBlockAndUpdate(base.above(alt - 1),
                Blocks.SOUL_LANTERN.defaultBlockState());
        // estalo de pedra surgindo do nada (de noite, sem ver de onde vem)
        nv.playSound(null, base, net.minecraft.sounds.SoundEvents.DEEPSLATE_BREAK,
                SoundSource.BLOCKS, 0.45F, 0.6F);
    }

    // =================================================================== SAÍDA
    /** Agenda a saída: sombras crescem agora, kick depois de segundos. */
    public static void sairDoServidor(ServerPlayer p, int segundos, String motivo) {
        PacketDistributor.sendToPlayer(p, new NeptunePackets.Saida(segundos, p.getName().getString()));
        ServerLevel nv = (ServerLevel) p.level();
        Vec3 pos = p.position();
        // (5.7.2) SAIU a chuva de 90 HAPPY_VILLAGER (verde). O dono rejeitou:
        // "essas partículas verdes não ficaram legais, coloque algumas e vai
        // aumentando até a pessoa sumir". Então: começa com POUCAS sombras e é
        // o tickSaidas que vai multiplicando até ela desaparecer.
        sombraChuva(nv, pos, 3);
        // (5.7.6) "as outras pessoas que estiverem la dentro vao ver essas
        // pessoas desaparecendo?" — SIM: a coluna de sombra é partícula no
        // MUNDO (todo mundo vê), e quem está num raio de 24 blocos sente o
        // peso (medo) + ouve o corpo sendo puxado.
        for (ServerPlayer outro : p.getServer().getPlayerList().getPlayers()) {
            if (outro == p || outro.level() != p.level()) {
                continue;
            }
            double d = outro.position().distanceTo(pos);
            if (d <= 24.0) {
                outro.addEffect(new MobEffectInstance(RPEffects.MEDO, 100, 0, false, false, true));
                outro.playNotifySound(net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(),
                        SoundSource.AMBIENT, 0.7F, 0.5F);
                outro.displayClientMessage(Component.literal(
                        "\u00a78Alguma coisa está sendo puxada pra dentro do nada..."), true);
            }
        }
        // (5.7.8) o mesmo som da expulsão, que o dono mandou (terremoto sair)
        nv.playSound(null, p.blockPosition(), RPTagMod.SOM_EXPULSAO.get(),
                SoundSource.HOSTILE, 1.0F, 0.8F);
        nv.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(),
                SoundSource.PLAYERS, 0.85F, 0.55F);
        nv.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value(),
                SoundSource.PLAYERS, 0.45F, 0.7F);
        p.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "\u00a78" + p.getName().getString() + " \u00a77foi engolido pelas sombras...")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        SAIDAS.add(new Object[]{ System.currentTimeMillis() + Math.max(1, segundos) * 1000L,
                p.getUUID(), motivo, System.currentTimeMillis() });
    }

    /**
     * (5.7.2) A SOMBRA DO BANIMENTO: um punhado de partículas escuras em volta do
     * corpo. Quem chama é o {@link #tickSaidas}, aumentando a quantidade com o
     * tempo — no começo 2, no fim 11 por quadro (o dobro do espaçamento), então
     * a nuvem CRESCE até o jogador sumir dentro dela.
     */
    private static void sombraChuva(ServerLevel nv, Vec3 pos, int quantas) {
        for (int i = 0; i < quantas; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = 0.12 + Math.random() * 0.6;
            double y = pos.y + 0.1 + Math.random() * 1.8;
            double px = pos.x + Math.cos(a) * r;
            double pz = pos.z + Math.sin(a) * r;
            nv.sendParticles(ParticleTypes.SQUID_INK, px, y, pz, 1, 0.01, 0.02, 0.01, 0.0);
            if (Math.random() < 0.45) {
                nv.sendParticles(ParticleTypes.LARGE_SMOKE, px, y, pz, 1, 0.02, 0.02, 0.02, 0.0);
            }
            if (Math.random() < 0.18) {
                nv.sendParticles(ParticleTypes.ASH, px, y, pz, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    private static void tickSaidas(MinecraftServer server) {
        long agora = System.currentTimeMillis();
        Iterator<Object[]> it = SAIDAS.iterator();
        while (it.hasNext()) {
            Object[] s = it.next();
            long fim = (Long) s[0];
            ServerPlayer p = server.getPlayerList().getPlayer((UUID) s[1]);
            // (5.7.2) a sombra do banimento vai AUMENTANDO até engolir a pessoa:
            // 1 partícula por quadro no começo, até 11 no fim (a cada 2 ticks
            // pra não exagerar no pacote nem pesar o cliente).
            if (p != null && p.level() instanceof ServerLevel nv && (agora % 2 == 0)) {
                long inicio = (Long) s[3];
                long dur = Math.max(1L, fim - inicio);
                float k = Math.max(0.0F, Math.min(1.0F, (agora - inicio) / (float) dur));
                sombraChuva(nv, p.position(), 1 + (int) (10 * k * k));
                if (k > 0.35F && Math.random() < 0.5) {
                    nv.sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 0.9, p.getZ(),
                            1, 0.25, 0.6, 0.25, 0.01);
                }
            }
            if (agora < fim) {
                continue;
            }
            it.remove();
            if (p == null) {
                continue;
            }
            p.connection.disconnect(Component.literal("\u00a78\u00a7l\u2591 O universo se corrigiu \u00a78\u00a7l\u2591\n\n")
                    .append(Component.literal(String.valueOf(s[2])).withStyle(ChatFormatting.GRAY)));
        }
    }

    // =================================================================== BICHO
    public static Caixa colocarCaixa(ServerLevel nv, BlockPos pos) {
        // (5.7.3) DEDUPE: cada clique criava uma Caixa NOVA na lista e cada uma
        // tocava a música no seu ciclo — o dono apertava 5x e ouviam-se 5
        // músicas sobrepostas. Agora, se já tem caixa aqui, devolve a mesma e
        // NÃO toca nada (quem quiser som usa tocarSomCaixa).
        for (Caixa c : CAIXAS) {
            if (c.pos.equals(pos)) {
                if (nv.getBlockState(pos).isAir()) {
                    nv.setBlockAndUpdate(pos, RPTagMod.CAIXA_BICHO.get().defaultBlockState());
                }
                return c;
            }
        }
        // (5.7.7) SÓ UMA CAIXA TOCANDO POR VEZ NO MUNDO. O dono reclamou de
        // novo: "se eu apertar varias vezes na caixa do palhaço, começa tocar
        // varias musicas". O dedupe por posição não resolvia quando ele
        // colocava caixas em lugares diferentes (cada uma com sua música, e o
        // jogo SOBREPÕE as instâncias de som). Agora a caixa nova APAGA a
        // anterior (bloco e som) antes de tocar.
        for (Iterator<Caixa> it = CAIXAS.iterator(); it.hasNext();) {
            Caixa velha = it.next();
            it.remove();
            if (!velha.pos.equals(pos)) {
                try {
                    if (nv.getBlockState(velha.pos).is(RPTagMod.CAIXA_BICHO.get())) {
                        nv.removeBlock(velha.pos, false);
                    }
                } catch (Throwable t) {
                    RPTagMod.LOGGER.warn("RP Tag: nao consegui apagar a caixa velha", t);
                }
            }
        }
        pararMusicaCaixa(nv);
        // (5.4.0) põe o BLOCO de verdade no mundo (é a caixinha que o jogador vê
        // e de onde o bicho pula) e registra pra o tick vigiar a distância.
        if (nv.getBlockState(pos).isAir()) {
            nv.setBlockAndUpdate(pos, RPTagMod.CAIXA_BICHO.get().defaultBlockState());
        }
        Caixa c = new Caixa(pos);
        CAIXAS.add(c);
        // (5.7.8) A MÚSICA NÃO TOCA MAIS AO COLOCAR A CAIXA. O dono: "arrume o
        // negócio de começar a tocar a música quando o player apertar a
        // primeira vez". Antes o /bicho aqui já saía tocando; agora quem
        // começa é o primeiro clique (toqueNaCaixa).
        return c;
    }

    /**
     * (5.7.3) Toca o som da caixinha UMA vez, com trava de 4 s na mesma caixa.
     * Antes o `playSound` era solto em 3 lugares (colocar, clicar, ciclo de
     * 30 s) e cada um deles empilhava uma música em cima da outra.
     */
    public static void tocarSomCaixa(ServerLevel nv, BlockPos pos) {
        long agora = System.currentTimeMillis();
        for (Caixa c : CAIXAS) {
            if (c.pos.equals(pos)) {
                if (agora - c.ultimoSom < 8000L) {
                    return;                      // spam de clique: ignora
                }
                c.ultimoSom = agora;
                break;
            }
        }
        // (5.7.7) para a música ANTERIOR antes de começar a nova: sem isso o
        // jogo sobrepõe instâncias e vira "várias músicas juntas".
        pararMusicaCaixa(nv);
        nv.playSound(null, pos, RPTagMod.SOM_CAIXA.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
    }

    /** (5.7.7) manda TODO MUNDO parar a música da caixa (fim do empilhamento). */
    public static void pararMusicaCaixa(ServerLevel nv) {
        try {
            for (ServerPlayer o : nv.players()) {
                o.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                        RPTagMod.SOM_CAIXA.get().getLocation(), SoundSource.AMBIENT));
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui parar a musica da caixa", t);
        }
    }

    public static void limparCaixas() {
        CAIXAS.clear();
    }

    public static int caixas() {
        return CAIXAS.size();
    }

    private static void tickCaixas(MinecraftServer server) {
        if (CAIXAS.isEmpty()) {
            return;
        }
        Iterator<Caixa> it = CAIXAS.iterator();
        while (it.hasNext()) {
            Caixa c = it.next();
            ServerLevel nv = null;
            for (ServerLevel n : server.getAllLevels()) {
                if (n.hasChunkAt(c.pos)) {
                    nv = n;
                    break;
                }
            }
            if (nv == null) {
                continue;
            }
            long agoraMs = System.currentTimeMillis();
            long durou = (agoraMs - c.inicio) / 1000L;
            // (5.7.3) FIM DO "VÁRIAS MÚSICAS": antes era `durou % 30 == 0`, que
            // é verdade durante o SEGUNDO INTEIRO — e como o tick roda 20x por
            // segundo, eram 20 músicas tocadas juntas a cada 30 s. Agora é um
            // instante marcado (proximaMusica) e toca UMA vez.
            // (5.7.8) só continua a música se já começou (alguém clicou)
            if (c.ultimoSom > 0L && durou < BICHO_MUSICA_SEG && agoraMs >= c.proximaMusica) {
                c.proximaMusica = agoraMs + BICHO_MUSICA_SEG * 1000L;
                c.ultimoSom = 0L;                 // libera a trava: é o ciclo do tick
                tocarSomCaixa(nv, c.pos);
            }
            // quem chegou perto?
            for (Player p : nv.players()) {
                if (!(p instanceof ServerPlayer sp)) {
                    continue;
                }
                // (5.7.3) O CRIATIVO LEVA O SUSTO TAMBÉM. Era o motivo do
                // "o bicho não pula na tela do player": o dono testa em
                // criativo e o tick pulava ele — só o espectador fica fora.
                if (sp.isSpectator()) {
                    continue;
                }
                if (sp.blockPosition().distSqr(c.pos) <= BICHO_RAIO * BICHO_RAIO) {
                    it.remove();
                    dispararBicho(nv, c.pos, sp);
                    break;
                }
            }
        }
    }

    /**
     * (5.7.3) Abre a caixa na hora (usado pelo CLIQUE DIREITO e pelo tick).
     * Público porque o {@link CaixaBichoBlock} chama quando o admin clica —
     * antes o clique não fazia o bicho pular, só tocava música.
     */
    public static void estourarCaixa(ServerLevel nv, BlockPos pos, ServerPlayer quem) {
        dispararBicho(nv, pos, quem);
    }

    /**
     * (5.7.7) O BICHO SÓ PULA NA SEGUNDA VEZ — pedido do dono:
     * <i>"o bicho no primeiro click deve fazer um som, e depois de passar alguns
     * segundos ele dá o susto quando apertar de novo na caixa"</i>.
     *
     * <p>Primeiro toque: a caixa toca (som/chim) e o jogador leva um aviso de
     * que tem alguma coisa dentro. A partir de {@link #ESPERA_SUSTO_MS}, o
     * próximo toque ARREBENTA a caixa e o bicho pula na cara. Antes disso o
     * clique só repete o som (não gasta o susto).
     */
    public static void toqueNaCaixa(ServerLevel nv, BlockPos pos, ServerPlayer quem) {
        long agora = System.currentTimeMillis();
        Long primeiro = TOQUES_CAIXA.get(pos);
        if (primeiro == null) {
            TOQUES_CAIXA.put(pos, agora);
            tocarSomCaixa(nv, pos);
            quem.displayClientMessage(Component.literal(
                    "\u00a77A caixa rangeu sozinha. Tem alguma coisa aí dentro \u2014 "
                            + "bata de novo quando quiser ver."), true);
            return;
        }
        if (agora - primeiro < ESPERA_SUSTO_MS) {
            tocarSomCaixa(nv, pos);
            long falta = (ESPERA_SUSTO_MS - (agora - primeiro)) / 1000L + 1L;
            quem.displayClientMessage(Component.literal(
                    "\u00a78Ainda não. (" + falta + "s) A música está te chamando."), true);
            return;
        }
        TOQUES_CAIXA.remove(pos);
        estourarCaixa(nv, pos, quem);      // agora sim: o susto
    }

    /** (5.7.7) quanto tempo entre o primeiro toque e o susto liberar. */
    private static final long ESPERA_SUSTO_MS = 4000L;

    private static void dispararBicho(ServerLevel nv, BlockPos pos, ServerPlayer quem) {
        // o berro, a caixa arrebentando e o bicho PULANDO NA TELA
        PacketDistributor.sendToPlayer(quem, new NeptunePackets.Susto(1.0F, System.nanoTime()));
        quem.playNotifySound(RPTagMod.SOM_BICHO_GRITO.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        nv.playSound(null, pos, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 0.8F, 1.6F);
        nv.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                3, 0.2, 0.2, 0.2, 0.0);
        nv.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                40, 0.4, 0.4, 0.4, 0.02);
        nv.removeBlock(pos, false);
        quem.addEffect(new MobEffectInstance(RPEffects.MEDO, 200, 0, false, false, true));
        quem.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, false));
        nv.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "\u00a78\u00a7oA caixinha parou de tocar.").withStyle(ChatFormatting.DARK_GRAY), false);
        // e ele continua aparecendo pros outros por perto (susto coletivo curto)
        for (ServerPlayer outro : nv.getServer().getPlayerList().getPlayers()) {
            if (outro != quem && outro.level() == nv && outro.position().distanceTo(quem.position()) < 12) {
                PacketDistributor.sendToPlayer(outro, new NeptunePackets.Susto(0.55F, System.nanoTime()));
            }
        }
    }

    // =================================================================== VAZIO
    /** Apaga alguém da existência (kill + aviso + som bizarro). */
    public static void apagarDaExistencia(ServerPlayer alvo, ServerPlayer quem) {
        ServerLevel nv = (ServerLevel) alvo.level();
        Vec3 pos = alvo.position();
        for (ServerPlayer p : alvo.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new NeptunePackets.Nebulosa(4, true));
            p.playNotifySound(RPTagMod.SOM_VAZIO.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        // (5.7.7) O CORPO SE DESFAZ: cinzas, sombra e a ALMA saindo — o dono
        // pediu "algo para evidenciar essa perda canon".
        nv.sendParticles(ParticleTypes.SQUID_INK, pos.x, pos.y + 1, pos.z, 120, 0.6, 1.0, 0.6, 0.03);
        nv.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1, pos.z, 80, 0.5, 1.0, 0.5, 0.4);
        // a animação contínua (3 s) fica no tickApagando
        APAGANDO.add(new Apagando(nv, pos, System.currentTimeMillis() + 3000L));
        alvo.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("---- vazio --- apagou ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal("\"" + alvo.getName().getString() + "\"")
                                .withStyle(ChatFormatting.DARK_PURPLE))
                        .append(Component.literal(" da existência").withStyle(ChatFormatting.DARK_GRAY)),
                false);
        alvo.hurt(alvo.damageSources().magic(), Float.MAX_VALUE);
        if (alvo.isAlive()) {
            alvo.kill();
        }
    }

    // ============================================================== ENGOLIR
    /**
     * (5.7.6) O TERREMOTO TIRA A PESSOA DO JOGO — com animação que os outros
     * VEEM.
     *
     * <p>Pergunta do dono: <i>"tem a animação de quando o terremoto tirar a
     * pessoa do jogo? as outras pessoas que estiverem lá dentro vão ver essas
     * pessoas desaparecendo? com efeito e tudo mais?"</i> — antes o
     * {@code /terremoto sair} só chutava o jogador e as sombras eram discretas.
     * Agora o chão ABRE embaixo dele, a escuridão sobe em espiral, blocos são
     * sugados e os outros veem tudo (partícula de mundo) antes de ele sumir
     * pro exílio.
     */
    public static void engolirNoTerremoto(ServerPlayer alvo, ServerPlayer quem) {
        try {
            if (alvo.level() instanceof ServerLevel nv) {
                // abre a fenda de verdade embaixo dele
                abrirFenda(alvo);
                // e a animação de exílio (2,6 s) com todo mundo vendo
                exilar(alvo, quem == null ? alvo : quem);
                for (ServerPlayer outro : alvo.getServer().getPlayerList().getPlayers()) {
                    if (outro.level() == alvo.level()
                            && outro.position().distanceTo(alvo.position()) <= 40) {
                        PacketDistributor.sendToPlayer(outro, new NeptunePackets.Cena(
                                NeptunePackets.Cena.CENA_RACHADURA, 0.8F, 2600, System.nanoTime()));
                        outro.displayClientMessage(Component.literal(
                                "\u00a74\u00a7lO CH\u00c3O ABRIU E LEVOU \u00a7f"
                                        + alvo.getName().getString().toUpperCase()), true);
                    }
                }
                nv.playSound(null, alvo.blockPosition(),
                        net.minecraft.sounds.SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.0F, 0.5F);
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: engolir no terremoto falhou (o jogo segue)", t);
        }
    }

    /**
     * (5.7.6) A ANIMAÇÃO DE SAÍDA, USÁVEL NO MAPA.
     *
     * <p>Antes ela só rodava quando alguém desconectava de verdade (e quem saía
     * não via nada). Agora o admin pode disparar com {@code /saida <jogador>}:
     * todo mundo vê o bonequinho se desfazendo em chuva de código, com o nome
     * de quem "saiu" — perfeito pra fechar uma cena de RP. Ninguém é
     * desconectado.
     *
     * @return quantos jogadores receberam a animação.
     */
    public static int tocarAnimacaoSaida(ServerPlayer alvo, net.minecraft.server.MinecraftServer server) {
        int n = 0;
        try {
            ScreenTextPayload pacote = new ScreenTextPayload(alvo.getName().getString().toUpperCase(),
                    9, 18);
            // (5.7.7) e os CÓDIGOS VERDES EM VOLTA DO CORPO, no mundo
            NeptunePackets.Codigos glifos = new NeptunePackets.Codigos(
                    alvo.getX(), alvo.getY(), alvo.getZ(), 6500,
                    alvo.getName().getString());
            for (ServerPlayer outro : server.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(outro, pacote);
                PacketDistributor.sendToPlayer(outro, glifos);
                n++;
            }
            // som de "sessão encerrando"
            if (alvo.level() instanceof ServerLevel nv) {
                nv.playSound(null, alvo.blockPosition(),
                        net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.6F);
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: animacao de saida manual falhou (o jogo segue)", t);
        }
        return n;
    }

    /**
     * (5.7.9) A ANIMAÇÃO DE SAÍDA QUE O DONO QUER — SOZINHA.
     *
     * <p>Ele recusou o comando: <i>"não quero essa /saida, quero que a animação
     * apareça quando o player sair do jogo"</i>. Então ela dispara no logout:
     * quem FICA vê, na posição de onde a pessoa desconectou, o corpo sendo
     * engolido — a coluna de sombra sobe em espiral, os códigos do glifo
     * piscam em volta, o som da expulsão toca e o chat anuncia. O que saiu não
     * vê nada (ele já foi), e é assim que tem que ser.
     */
    public static void saidaAutomatica(ServerPlayer saiu) {
        try {
            if (!(saiu.level() instanceof ServerLevel nv)) {
                return;
            }
            Vec3 pos = saiu.position();
            String nome = saiu.getName().getString();
            // (1) o corpo sumindo: coluna de sombra no lugar exato
            for (int passo = 0; passo < 4; passo++) {
                sombraChuva(nv, pos.add(0, passo * 0.55, 0), 7 - passo);
            }
            nv.sendParticles(ParticleTypes.SOUL, pos.x, pos.y + 0.7, pos.z, 26,
                    0.35, 0.9, 0.35, 0.02);
            nv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 0.4, pos.z, 18,
                    0.30, 0.7, 0.30, 0.01);
            // (2) os CÓDIGOS em volta do corpo + a placa com o nome (só pra quem fica)
            NeptunePackets.Codigos glifos = new NeptunePackets.Codigos(
                    pos.x, pos.y, pos.z, 6500, nome);
            // estilo 18 = A ANIMACAO DE SAIDA em si (o sprite do jogador se
            // desfazendo em chuva de código, na tela de quem fica)
            ScreenTextPayload pacote = new ScreenTextPayload(nome, 18, 9);
            for (ServerPlayer outro : nv.getServer().getPlayerList().getPlayers()) {
                if (outro == saiu) {
                    continue;
                }
                PacketDistributor.sendToPlayer(outro, glifos);
                PacketDistributor.sendToPlayer(outro, pacote);
                if (outro.position().distanceTo(pos) <= 32.0) {
                    outro.playNotifySound(RPTagMod.SOM_EXPULSAO.get(), SoundSource.AMBIENT, 0.9F, 0.8F);
                    outro.displayClientMessage(Component.literal(
                            "\u00a78" + nome + " se desfez em sombra."), true);
                }
            }
            // (3) o som no mundo + o aviso no chat
            nv.playSound(null, BlockPos.containing(pos), RPTagMod.SOM_EXPULSAO.get(),
                    SoundSource.AMBIENT, 1.0F, 0.8F);
            nv.playSound(null, BlockPos.containing(pos),
                    net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value(),
                    SoundSource.AMBIENT, 0.5F, 0.7F);
            nv.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "\u00a78" + nome + " \u00a77saiu do jogo \u2014 e as sombras o levaram.")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: animacao automatica de saida falhou (o jogo segue)", t);
        }
    }

    // ================================================================ EXÍLIO
    /**
     * (5.7.6) O EXÍLIO COM ANIMAÇÃO — "deve ter uma animação do player sendo
     * exilado para a dimensão do vazio".
     *
     * <p>Antes: teleporte seco (o cara piscava e sumia) e, na volta, outro
     * teleporte seco no spawn. Agora são <b>2,6 s de animação no mundo</b>,
     * visível por TODO MUNDO que estiver por perto: o chão escurece, a escuridão
     * sobe em espiral engolindo o corpo, pedaços de terra são ARRANCADOS do
     * chão e sugados pra dentro, raios vermelhos estalam — e só depois ele é
     * levado. A volta é o contrário: a escuridão se abre e cospe ele de volta
     * no MESMO lugar de onde saiu.
     */
    public static void exilar(ServerPlayer alvo, ServerPlayer quem) {
        var chave = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "exilio"));
        ServerLevel destino = alvo.getServer().getLevel(chave);
        if (destino == null) {
            quem.sendSystemMessage(Component.literal(
                    "\u00a7cA dimensão rptag:exilio não carregou. Reinicie o servidor."));
            return;
        }
        // guarda de ONDE ele saiu (a volta devolve pro mesmo lugar)
        ORIGEM.put(alvo.getUUID(), new Object[]{ alvo.level().dimension(),
                alvo.getX(), alvo.getY(), alvo.getZ(), alvo.getYRot(), alvo.getXRot() });
        long agora = System.currentTimeMillis();
        EXILIOS.add(new Object[]{ agora + 2600L, alvo.getUUID(), 0, agora });
        PacketDistributor.sendToPlayer(alvo, new NeptunePackets.Cena(
                NeptunePackets.Cena.CENA_EXILIO, 1.0F, 3000, System.nanoTime()));
        for (ServerPlayer p : alvo.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new NeptunePackets.Nebulosa(5, true));
            p.playNotifySound(RPTagMod.SOM_VAZIO.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        alvo.getServer().getPlayerList().broadcastSystemMessage(
                Component.literal("---- vazio --- lançou ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal("\"" + alvo.getName().getString() + "\"")
                                .withStyle(ChatFormatting.DARK_PURPLE))
                        .append(Component.literal(" para fora do universo").withStyle(ChatFormatting.DARK_GRAY)),
                false);
        alvo.displayClientMessage(Component.literal(
                "\u00a74\u00a7lVOC\u00ca FOI EXPULSO DO MUNDO.\n"
                + "\u00a78Uma planície de pedra cinzenta até onde a vista alcança.\n"
                + "\u00a78(um admin pode te tirar com /vazio soltar <jogador>)"), false);
    }

    // ============================================================ EXÍLIO 5.7.9
    /** (5.7.9) ponto de chegada: SUPERFÍCIE da praça (nunca mais dentro da pedra). */
    private static Vec3 chegadaNoExilio(ServerLevel exilio) {
        exilio.getChunk(0, 0);                    // garante o chunk carregado
        prepararPraca(exilio);
        int y = exilio.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        if (y <= exilio.getMinBuildHeight() + 2) {
            y = 12;
        }
        return new Vec3(0.5, y + 0.25, 0.5);
    }

    /** (5.7.9) a PRAÇA da chegada: chão firme, 8 colunas com lanterna de alma e a
     * marca no centro. Feita uma vez por sessão (idempotente e barata). */
    private static boolean pracaFeita = false;

    private static void prepararPraca(ServerLevel exilio) {
        if (pracaFeita) {
            return;
        }
        pracaFeita = true;
        try {
            exilio.getChunk(0, 0);
            int y = exilio.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
            if (y <= exilio.getMinBuildHeight() + 2) {
                y = 12;
            }
            final int R = 15;
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > R) {
                        continue;
                    }
                    BlockPos chao = new BlockPos(dx, y - 1, dz);
                    BlockState bloco;
                    if (d < 2.2) {
                        bloco = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
                    } else if (d > R - 1.5) {
                        bloco = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                    } else if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
                        bloco = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
                    } else {
                        bloco = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                    }
                    exilio.setBlockAndUpdate(chao, bloco);
                    // tira o que estiver em cima (nada de teto, nada de parede)
                    for (int dy = 0; dy <= 6; dy++) {
                        BlockPos ar = chao.above(1 + dy);
                        if (!exilio.getBlockState(ar).isAir()) {
                            exilio.setBlockAndUpdate(ar, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
            // 8 colunas de obsidiana com lanterna de alma (luz no meio do nada)
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI * 2 / 8;
                int px = (int) Math.round(Math.cos(a) * (R - 2));
                int pz = (int) Math.round(Math.sin(a) * (R - 2));
                for (int k = 0; k < 4; k++) {
                    exilio.setBlockAndUpdate(new BlockPos(px, y + k, pz),
                            Blocks.OBSIDIAN.defaultBlockState());
                }
                exilio.setBlockAndUpdate(new BlockPos(px, y + 4, pz),
                        Blocks.SOUL_LANTERN.defaultBlockState());
            }
            // a marca do centro: cruz de lanternas apontando os 4 rumos
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI * 0.5;
                int px = (int) Math.round(Math.cos(a) * 4);
                int pz = (int) Math.round(Math.sin(a) * 4);
                exilio.setBlockAndUpdate(new BlockPos(px, y, pz),
                        Blocks.SOUL_LANTERN.defaultBlockState());
            }
            RPTagMod.LOGGER.info("RP Tag: praça do exílio pronta (y={})", y);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: praça do exílio falhou (a dimensão segue andável)", t);
        }
    }

    /** (5.7.8) admin entra no exílio pra testar o ambiente (sem punir ninguém). */
    public static void entrarNoExilio(ServerPlayer quem) {
        ServerLevel destino = quem.getServer().getLevel(CHAVE_EXILIO);
        if (destino == null) {
            quem.sendSystemMessage(Component.literal(
                    "\u00a7cA dimensão rptag:exilio não carregou."));
            return;
        }
        PacketDistributor.sendToPlayer(quem, new NeptunePackets.Cena(
                NeptunePackets.Cena.CENA_EXILIO, 1.0F, 2600, System.nanoTime()));
        EXILIOS.add(new Object[]{ System.currentTimeMillis() + 2400L, quem.getUUID(), 0,
                System.currentTimeMillis() });
        quem.displayClientMessage(Component.literal(
                "\u00a78Um lugar sem céu. Sem norte. Só o que você conseguir lembrar."), false);
    }

    /** (5.5.0/5.7.6) tira alguém do exílio: animação de escuridão + volta pro lugar de origem. */
    public static void soltarDoExilio(ServerPlayer alvo) {
        long agora = System.currentTimeMillis();
        EXILIOS.add(new Object[]{ agora + 2400L, alvo.getUUID(), 1, agora });
        PacketDistributor.sendToPlayer(alvo, new NeptunePackets.Cena(
                NeptunePackets.Cena.CENA_VOLTA, 1.0F, 2800, System.nanoTime()));
        alvo.displayClientMessage(Component.literal(
                "\u00a77A escuridão te devolve. Não conte o que viu."), false);
    }

    /**
     * (5.7.6) ANIMAÇÃO DO EXÍLIO/DA VOLTA, tick a tick, NO MUNDO.
     *
     * <p>Nada de tela (o dono já vetou desenho na lente): é partícula de
     * verdade no lugar onde a pessoa está, então os outros jogadores veem a
     * pessoa sendo engolida e depois cuspida de volta.
     */
    private static void tickExilios(MinecraftServer server) {
        if (EXILIOS.isEmpty()) {
            return;
        }
        long agora = System.currentTimeMillis();
        Iterator<Object[]> it = EXILIOS.iterator();
        while (it.hasNext()) {
            Object[] e = it.next();
            long fim = (Long) e[0];
            ServerPlayer p = server.getPlayerList().getPlayer((UUID) e[1]);
            int tipo = (Integer) e[2];
            long inicio = (Long) e[3];
            if (p != null && p.level() instanceof ServerLevel nv) {
                float k = Math.max(0.0F, Math.min(1.0F, (agora - inicio) / (float) Math.max(1L, fim - inicio)));
                particulasExilio(nv, p.position(), k, tipo == 1);
            }
            if (agora < fim) {
                continue;
            }
            it.remove();
            if (p == null) {
                continue;
            }
            if (tipo == 0) {
                levarProExilio(p);
            } else {
                devolverAleatorio(p);
            }
        }
    }

    /** (5.7.6) as partículas do exílio (k = 0..1). {@code voltando} inverte a direção. */
    private static void particulasExilio(ServerLevel nv, Vec3 pos, float k, boolean voltando) {
        DustParticleOptions negro = new DustParticleOptions(new org.joml.Vector3f(0.03f, 0.0f, 0.05f), 2.2f);
        DustParticleOptions vermelho = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.08f, 0.05f), 1.4f);
        // o chão de onde ele está sendo arrancado (poeira certa, SEM verde)
        BlockState estado = estadoPoeira(nv.getBlockState(BlockPos.containing(pos.x, pos.y - 0.5, pos.z)));
        double fase = System.currentTimeMillis() / 1000.0;
        // 1) ESPIRAL DE ESCURIDÃO: engolindo (subindo) ou abrindo (descendo)
        for (int i = 0; i < 12; i++) {
            double a = (i / 12.0) * Math.PI * 2 + fase * (voltando ? -2.2 : 2.2);
            double h = voltando ? (1.0 - k) * 2.2 : k * 2.2;
            double r = 0.75 * (1.0 - 0.55 * h / 2.2);
            double x = pos.x + Math.cos(a) * r;
            double z = pos.z + Math.sin(a) * r;
            double y = pos.y + 0.1 + h + (i % 3) * 0.12;
            nv.sendParticles(negro, x, y, z, 1, 0.02, 0.02, 0.02, 0.0);
            nv.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        // 2) SUCÇÃO: pedaços do chão voando PRA DENTRO do corpo (e o contrário na volta)
        for (int i = 0; i < 5; i++) {
            double a = Math.random() * Math.PI * 2;
            double r = voltando ? (0.4 + Math.random() * 0.8) : (2.6 - 2.0 * k + Math.random() * 0.8);
            double x = pos.x + Math.cos(a) * r;
            double z = pos.z + Math.sin(a) * r;
            double y = pos.y + 0.1 + Math.random() * 1.8;
            nv.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                            ParticleTypes.BLOCK, estado),
                    x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // 3) RAIOS vermelhos estalando (mais fortes no auge)
        float auge = 1.0F - Math.abs(k - 0.5F) * 2.0F;
        if (auge > 0.25F && Math.random() < 0.55) {
            double ang = Math.random() * Math.PI * 2;
            double alcance = 1.6 + Math.random() * 2.6;
            for (int s2 = 1; s2 <= 6; s2++) {
                double f = s2 / 6.0;
                double x = pos.x + Math.cos(ang) * alcance * f;
                double z = pos.z + Math.sin(ang) * alcance * f;
                double y = pos.y + 0.35 + f * 1.4 + (Math.random() - 0.5) * 0.18;
                nv.sendParticles(vermelho, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        // 4) CINZA subindo devagar
        if (Math.random() < 0.5) {
            nv.sendParticles(ParticleTypes.ASH, pos.x, pos.y + 0.2 + Math.random(), pos.z,
                    2, 0.45, 0.45, 0.45, 0.01);
        }

        // ================= (5.7.7) O EXÍLIO FICOU MAIOR =================
        // 5) BURACO NO CHÃO: um aro de escuridão que ABRE (cresce) e depois
        //    fecha, como se o chão fosse um papel sendo rasgado por baixo.
        float abrir = (float) Math.sin(Math.PI * Math.min(1.0, k * 1.1));
        double raio = 0.35 + 1.75 * abrir;
        int aro = 26;
        for (int a = 0; a < aro; a++) {
            double ang = (a / (double) aro) * Math.PI * 2 + fase * 0.6;
            double x = pos.x + Math.cos(ang) * raio;
            double z = pos.z + Math.sin(ang) * raio;
            nv.sendParticles(ParticleTypes.SQUID_INK, x, pos.y + 0.06, z, 1, 0.01, 0.0, 0.01, 0.0);
            nv.sendParticles(negro, x, pos.y + 0.05, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        // 6) TENTÁCULOS: 6 colunas tortas de escuridão em volta do corpo
        if (Math.random() < 0.7) {
            for (int t = 0; t < 6; t++) {
                double ang = (t / 6.0) * Math.PI * 2 + fase * 0.4;
                double rr = 0.75 + Math.random() * 0.25;
                for (int j = 0; j < 4; j++) {
                    double y = pos.y + 0.1 + j * 0.45 + Math.sin(fase * 2 + j + t) * 0.08;
                    double xx = pos.x + Math.cos(ang) * rr + Math.sin(fase * 1.3 + j) * 0.10;
                    double zz = pos.z + Math.sin(ang) * rr + Math.cos(fase * 1.1 + j) * 0.10;
                    nv.sendParticles(t % 2 == 0 ? ParticleTypes.SQUID_INK : negro,
                            xx, y, zz, 1, 0.01, 0.01, 0.01, 0.0);
                }
            }
        }
        // 7) O ESTOURO FINAL: brasa vermelha em círculo (o vazio "cuspindo")
        if (k > 0.82F) {
            double r2 = 0.5 + 0.6 * Math.random();
            for (int a = 0; a < 40; a++) {
                double ang = (a / 40.0) * Math.PI * 2;
                nv.sendParticles(vermelho, pos.x + Math.cos(ang) * r2, pos.y + 0.9 + Math.random() * 0.9,
                        pos.z + Math.sin(ang) * r2, 1, 0.0, 0.0, 0.0, 0.0);
            }
            nv.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 1.0, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** (5.7.6) leva pro exílio (fim da animação). */
    private static void levarProExilio(ServerPlayer alvo) {
        try {
            var chave = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "exilio"));
            ServerLevel destino = alvo.getServer().getLevel(chave);
            if (destino == null) {
                return;
            }
            // (5.7.8) O SOM DA EXPULSÃO (o que o dono mandou) toca no instante
            // em que ele é JOGADO PRA FORA DO MAPA — e quem está por perto ouve.
            var nivelOrigem = alvo.level() instanceof ServerLevel sl ? sl : null;
            if (nivelOrigem != null) {
                BlockPos aqui = alvo.blockPosition();
                nivelOrigem.playSound(null, aqui, RPTagMod.SOM_EXPULSAO.get(),
                        SoundSource.HOSTILE, 1.0F, 0.75F);
                for (ServerPlayer perto : nivelOrigem.players()) {
                    if (perto.position().distanceTo(alvo.position()) < 40) {
                        perto.playNotifySound(RPTagMod.SOM_EXPULSAO.get(),
                                SoundSource.HOSTILE, 0.8F, 0.7F);
                    }
                }
            }
            // (5.7.9) BUG DO DONO: "entro no vazio e não posso fazer nada".
            // O teleporte era pra y=1.0 — DENTRO da pedra (a superfície é y≈9),
            // então a pessoa nascia sufocada no escuro, sem andar e sem ver.
            // Agora a chegada é na superfície da praça, com o chão garantido.
            Vec3 chegada = chegadaNoExilio(destino);
            alvo.teleportTo(destino, chegada.x, chegada.y, chegada.z,
                    java.util.Set.of(), alvo.getYRot(), alvo.getXRot());
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: exilio falhou (o jogo segue)", t);
        }
    }

    /**
     * (5.7.8) DEVOLVE A PESSOA NUM LUGAR ALEATÓRIO DO MUNDO.
     *
     * <p>O dono: <i>"quando algum adm tirar do vazio, tem que soltar por lugares
     * aleatórios do mundo"</i>. Antes ela voltava pro exato lugar de onde saiu
     * (o que estragava a punição: o exílio era só uma pausa). Agora ela cai
     * num ponto sorteado do Overworld, a até {@link #RAIO_SOLTURA} blocos do
     * spawn — longe de casa, possivelmente perdida.
     *
     * <p>Segurança: procura a SUPERFÍCIE (heightmap), rejeita água/lava/ar e
     * tenta 24 vezes antes de desistir; se nada der certo, cai no spawn.
     */
    private static void devolverAleatorio(ServerPlayer alvo) {
        try {
            ORIGEM.remove(alvo.getUUID());
            ServerLevel mundo = alvo.getServer().overworld();
            java.util.Random sorte = new java.util.Random();
            BlockPos alvoPos = null;
            for (int tentativa = 0; tentativa < 24 && alvoPos == null; tentativa++) {
                int x = sorte.nextInt(RAIO_SOLTURA * 2 + 1) - RAIO_SOLTURA;
                int z = sorte.nextInt(RAIO_SOLTURA * 2 + 1) - RAIO_SOLTURA;
                if (Math.hypot(x, z) < 120) {
                    continue;                        // não solta colado no spawn
                }
                // garante que o chunk existe antes de perguntar a altura
                mundo.getChunk(x >> 4, z >> 4);
                int y = mundo.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos p = new BlockPos(x, y, z);
                BlockState chao = mundo.getBlockState(p.below());
                BlockState ar = mundo.getBlockState(p);
                boolean ruim = chao.isAir() || !chao.getFluidState().isEmpty()
                        || chao.is(Blocks.LAVA) || chao.is(Blocks.WATER)
                        || !ar.getFluidState().isEmpty();
                if (!ruim) {
                    alvoPos = p;
                }
            }
            if (alvoPos == null) {
                alvoPos = mundo.getSharedSpawnPos();
            }
            alvo.teleportTo(mundo, alvoPos.getX() + 0.5, alvoPos.getY() + 1.0, alvoPos.getZ() + 0.5,
                    java.util.Set.of(), sorte.nextFloat() * 360.0F, 0.0F);
            mundo.playSound(null, alvoPos, RPTagMod.SOM_EXPULSAO.get(), SoundSource.HOSTILE, 1.0F, 0.9F);
            PacketDistributor.sendToPlayer(alvo, new NeptunePackets.Cena(
                    NeptunePackets.Cena.CENA_VOLTA, 1.0F, 2200, System.nanoTime()));
            alvo.displayClientMessage(Component.literal(
                    "\u00a78A escuridão te cuspiu em algum lugar do mundo. "
                    + "Boa sorte achando o caminho de volta."), false);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: volta do exilio falhou (o jogo segue)", t);
        }
    }

    /** (5.7.8) raio (em blocos) em volta do spawn onde a pessoa pode ser solta. */
    private static final int RAIO_SOLTURA = 4000;

    /** (5.7.8) a dimensão do exílio (pra o ambiente dela rodar no tick). */
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> CHAVE_EXILIO =
            net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "exilio"));
    /** (5.7.8) próximo monólito que nasce no escuro. */
    private static long proximoMonolito = 0L;
    /** (5.7.8) próximo sussurro. */
    private static long proximoSussurro = 0L;
}
