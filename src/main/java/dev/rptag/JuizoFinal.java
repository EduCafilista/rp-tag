package dev.rptag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * (5.28.0) <b>O JULGAMENTO — o destino de quem perdeu as 5 vidas.</b>
 *
 * <p>Pedido do dono: <i>"o player morre perdeu as 5 vidas. Em vez de só perder o personagem, ele
 * vai para outra dimensão, um labirinto infinito, cheio de bicho bufado e tem que achar a saída.
 * Após a saída, ele vai entrar na sala de um boss, uma sala toda branca que seria interpretado
 * pelo adm de lore da pessoa. Caso ele ganhe a luta, ele vai ganhar um item do boss, caso ele
 * perca ele perde o personagem permanente. Após ele derrotar o boss o adm vai dropar um item que
 * seria uma chave que abriria o portal atrás do boss, assim podendo retornar à vida, e ganhando
 * mais 2 vidas canônicas."</i>
 *
 * <p>Como ficou (decisões que o dono confirmou):
 * <ul>
 *   <li>o gatilho é <b>sempre</b> que as vidas canônicas zeram — é o novo fim de personagem;</li>
 *   <li>o boss <b>não é um mob do mod</b>: é um ADM interpretando com um modelo. O mod monta a
 *       sala, faz a <b>introdução</b> (tela + som + o vulto no trono) e deixa o ADM conduzir;</li>
 *   <li>a gravação da luta fica com os ADMs (não é do mod);</li>
 *   <li>a <b>chave</b> ({@code rptag:chave_julgamento}) é o que abre o portal atrás do trono.
 *       Ao atravessar, o jogador volta à vida com <b>+2 vidas canônicas</b>.</li>
 * </ul>
 *
 * <p>Regra de ouro do mod: erro aqui devolve o jogador pro mundo normal, nunca o prende.
 */
public final class JuizoFinal {

    private JuizoFinal() {
    }

    // ---------------------------------------------------------------- dimensões
    /** o labirinto infinito. */
    public static final ResourceKey<Level> CHAVE_JUIZO = chave("juizo");
    /** a sala branca do julgamento. */
    public static final ResourceKey<Level> CHAVE_SALA = chave("sala_julgamento");

    private static ResourceKey<Level> chave(String nome) {
        return ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, nome));
    }

    /**
     * (5.28.0) manda o aviso de tela SEM derrubar nada. Jogador de teste (FakePlayer) não tem
     * conexão de rede: mandar pacote pra ele estoura e levaria junto todo o resto do método.
     */
    private static void aviso(ServerPlayer p, String texto, int seg, int estilo) {
        try {
            PacketDistributor.sendToPlayer(p, new ScreenTextPayload(texto, seg, estilo));
        } catch (Throwable ignorado) {
            // sem conexão (teste): o aviso é enfeite, o resto do julgamento segue
        }
    }

    public static boolean ehJuizo(Level l) {
        return l != null && l.dimension().equals(CHAVE_JUIZO);
    }

    public static boolean ehSala(Level l) {
        return l != null && l.dimension().equals(CHAVE_SALA);
    }

    // ---------------------------------------------------------------- geometria
    /** altura do piso do labirinto. */
    private static final int PISO = 64;
    /** altura do teto do labirinto (fecha o céu de vez). */
    private static final int TETO = 71;
    /** lado da célula do labirinto (corredor de 4, parede de 2). */
    private static final int CELULA = 6;
    /** centro da sala branca. */
    private static final int SALA_X = 0, SALA_Y = 64, SALA_Z = 0;
    /** meia-largura da sala branca. */
    private static final int SALA_R = 12;

    // ---------------------------------------------------------------- estado
    private static final Set<UUID> NO_JUIZO = new HashSet<>();
    private static final Set<UUID> NA_SALA = new HashSet<>();
    /** onde fica a saída do labirinto de cada jogador. */
    private static final Map<UUID, BlockPos> SAIDA = new HashMap<>();
    /** blocos de labirinto já construídos ("dim:cx:cz"). */
    private static final Set<String> PRONTOS = new HashSet<>();
    /** quando (ms) cada um deve ser levado ao julgamento. */
    private static final Map<UUID, Long> AGENDADOS = new HashMap<>();
    /** último aviso de direção da saída (ms). */
    private static final Map<UUID, Long> ULTIMA_DICA = new HashMap<>();
    /** último nascimento de bicho (ms). */
    private static final Map<UUID, Long> ULTIMO_BICHO = new HashMap<>();

    /** o portão atrás do trono está aberto? */
    private static boolean portaoAberto = false;

    // ================================================================ ENTRADA
    /**
     * <b>ZEROU AS VIDAS.</b> Marca pra levar ao labirinto em {@code ms} (dá tempo do ritual da
     * última vida terminar de tocar antes do teleporte).
     */
    public static void agendar(MinecraftServer servidor, ServerPlayer p, long ms) {
        if (servidor == null || p == null) {
            return;
        }
        AGENDADOS.put(p.getUUID(), System.currentTimeMillis() + Math.max(0L, ms));
        RPTagMod.LOGGER.info("RP Tag: {} vai ao JULGAMENTO em {} ms (zerou as vidas)",
                p.getName().getString(), ms);
    }

    /** manda o jogador pro labirinto AGORA (também é o `/juizo entrar`). */
    public static boolean entrar(ServerPlayer p) {
        ServerLevel lv = p.getServer().getLevel(CHAVE_JUIZO);
        if (lv == null) {
            RPTagMod.LOGGER.warn("RP Tag: a dimensão do julgamento não existe (json faltando?)");
            return false;
        }
        try {
            BlockPos centro = new BlockPos(0, PISO + 1, 0);
            construirEmVolta(lv, centro, 2);
            // a saída nasce LONGE, num ângulo sorteado: procurar é o desafio
            double ang = Math.random() * Math.PI * 2.0D;
            double dist = 240.0D + Math.random() * 120.0D;
            // (5.28.1) encosta a saída no CENTRO DO CORREDOR (nunca dentro de parede)
            int sx = snapCelula((int) (Math.cos(ang) * dist));
            int sz = snapCelula((int) (Math.sin(ang) * dist));
            SAIDA.put(p.getUUID(), new BlockPos(sx, PISO + 1, sz));
            NO_JUIZO.add(p.getUUID());
            NA_SALA.remove(p.getUUID());
            ULTIMO_BICHO.remove(p.getUUID());
            ENTROU_EM.put(p.getUUID(), System.currentTimeMillis());
            p.stopRiding();
            p.teleportTo(lv, centro.getX() + 0.5D, centro.getY() + 0.1D, centro.getZ() + 0.5D,
                    0.0F, 0.0F);
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.removeAllEffects();
            aviso(p, "\u00a74\u00a7lO JULGAMENTO", 10, 4);
            try {
                p.playNotifySound(net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                        net.minecraft.sounds.SoundSource.AMBIENT, 0.7F, 0.6F);
                p.displayClientMessage(Component.literal(
                        "\u00a78Você acordou no labirinto. \u00a77Ache a saída \u2014 ela brilha no escuro.")
                        .withStyle(ChatFormatting.DARK_GRAY), false);
            } catch (Throwable ignorado) {
                // jogador de teste: sem chat/tela
            }
            return true;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: entrada no julgamento falhou (o jogo segue)", t);
            return false;
        }
    }

    /** tira alguém do julgamento (usado pela vitória e pelo comando de socorro do ADM). */
    public static void sair(ServerPlayer p) {
        NO_JUIZO.remove(p.getUUID());
        NA_SALA.remove(p.getUUID());
        SAIDA.remove(p.getUUID());
        AGENDADOS.remove(p.getUUID());
        DICA_LIMPA(p);
    }

    private static void DICA_LIMPA(ServerPlayer p) {
        ULTIMA_DICA.remove(p.getUUID());
    }

    public static boolean estaNoJulgamento(UUID id) {
        return NO_JUIZO.contains(id) || NA_SALA.contains(id);
    }

    // ================================================================ SAÍDA DO LABIRINTO
    /** ele achou a saída: vai pra sala branca do boss. */
    public static void achouSaida(ServerPlayer p) {
        ServerLevel sala = p.getServer().getLevel(CHAVE_SALA);
        if (sala == null) {
            RPTagMod.LOGGER.warn("RP Tag: a sala do julgamento não existe (json faltando?)");
            return;
        }
        try {
            NO_JUIZO.remove(p.getUUID());
            SAIDA.remove(p.getUUID());
            NA_SALA.add(p.getUUID());
            construirSala(sala);
            BlockPos entrada = new BlockPos(SALA_X, SALA_Y + 1, SALA_Z + SALA_R - 3);
            p.teleportTo(sala, entrada.getX() + 0.5D, entrada.getY(), entrada.getZ() + 0.5D,
                    180.0F, 0.0F);
            p.setHealth(p.getMaxHealth());
            // a INTRODUÇÃO do boss (o ADM assume daqui pra frente)
            aviso(p, "\u00a7f\u00a7lO JULGAMENTO COME\u00c7A", 10, 4);
            vultoDoBoss(sala);
            try {
                p.playNotifySound(net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                        net.minecraft.sounds.SoundSource.AMBIENT, 0.8F, 0.7F);
                p.displayClientMessage(Component.literal(
                        "\u00a7fA sala é branca. \u00a77Alguém espera por você no trono.")
                        .withStyle(ChatFormatting.GRAY), false);
            } catch (Throwable ignorado) {
                // jogador de teste: sem chat/tela
            }
            for (ServerPlayer adm : p.getServer().getPlayerList().getPlayers()) {
                if (adm.hasPermissions(2)) {
                    adm.sendSystemMessage(Component.literal(
                            "\u00a7e[JULGAMENTO] \u00a7f" + p.getName().getString()
                            + " \u00a7echegou na sala. Assuma o boss com o seu modelo.")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: entrada na sala do julgamento falhou", t);
        }
    }

    // ================================================================ A SALA BRANCA
    /** monta (uma vez) a sala branca: piso, paredes, teto de luz, trono e o portão atrás. */
    public static void construirSala(ServerLevel lv) {
        String k = "sala";
        if (PRONTOS.contains(k)) {
            return;
        }
        try {
            int r = SALA_R;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean borda = Math.abs(x) == r || Math.abs(z) == r;
                    lv.setBlock(m.set(SALA_X + x, SALA_Y, SALA_Z + z),
                            Blocks.SMOOTH_QUARTZ.defaultBlockState(), 2);           // piso
                    for (int y = 1; y <= 8; y++) {
                        lv.setBlock(m.set(SALA_X + x, SALA_Y + y, SALA_Z + z),
                                borda ? Blocks.WHITE_CONCRETE.defaultBlockState()
                                        : Blocks.AIR.defaultBlockState(), 2);
                    }
                    boolean luz = (x % 4 == 0) && (z % 4 == 0);
                    lv.setBlock(m.set(SALA_X + x, SALA_Y + 9, SALA_Z + z),
                            luz ? Blocks.SEA_LANTERN.defaultBlockState()
                                    : Blocks.WHITE_CONCRETE.defaultBlockState(), 2);  // teto
                }
            }
            // ---- o TRONO, no fundo (norte)
            for (int x = -3; x <= 3; x++) {
                lv.setBlock(m.set(SALA_X + x, SALA_Y + 1, SALA_Z - r + 2),
                        Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), 2);
                lv.setBlock(m.set(SALA_X + x, SALA_Y + 2, SALA_Z - r + 2),
                        x == 0 ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
            // ---- o PORTÃO atrás do trono: quem abre é a CHAVE do ADM
            for (int x = -2; x <= 2; x++) {
                for (int y = 1; y <= 4; y++) {
                    boolean dentro = Math.abs(x) <= 1 && y <= 3;
                    lv.setBlock(m.set(SALA_X + x, SALA_Y + y, SALA_Z - r + 1),
                            dentro ? Blocks.AIR.defaultBlockState()
                                    : Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
                }
            }
            portaoAberto = false;
            PRONTOS.add(k);
            RPTagMod.LOGGER.info("RP Tag: sala do julgamento construída");
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: construção da sala falhou", t);
        }
    }

    /** o vulto da INTRODUÇÃO: o ADM usa o modelo dele em cima deste ponto. */
    private static void vultoDoBoss(ServerLevel lv) {
        try {
            BlockPos trono = new BlockPos(SALA_X, SALA_Y + 1, SALA_Z - SALA_R + 3);
            lv.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
                    new AABB(trono).inflate(6.0D), e -> true).forEach(e -> e.discard());
            var stand = EntityType.ARMOR_STAND.spawn(lv, trono, MobSpawnType.EVENT);
            if (stand != null) {
                stand.setCustomName(Component.literal("\u00a7fO JULGAMENTO"));
                stand.setCustomNameVisible(true);
                stand.setGlowingTag(true);
                stand.setNoGravity(true);
            }
            lv.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    trono.getX() + 0.5D, trono.getY() + 1.0D, trono.getZ() + 0.5D,
                    60, 1.2D, 1.6D, 1.2D, 0.02D);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: vulto do julgamento falhou", t);
        }
    }

    /** a CHAVE abre o portal atrás do boss. */
    public static boolean abrirPortao(ServerPlayer p) {
        ServerLevel sala = p.getServer().getLevel(CHAVE_SALA);
        if (sala == null || !ehSala(p.level())) {
            p.displayClientMessage(Component.literal(
                    "\u00a78A chave só gira na sala do julgamento.").withStyle(ChatFormatting.DARK_GRAY), true);
            return false;
        }
        try {
            construirSala(sala);
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = -1; x <= 1; x++) {
                for (int y = 1; y <= 3; y++) {
                    sala.setBlock(m.set(SALA_X + x, SALA_Y + y, SALA_Z - SALA_R + 1),
                            RPTagMod.ESCURIDAO.get().defaultBlockState(), 2);
                }
            }
            portaoAberto = true;
            sala.playSound(null, new BlockPos(SALA_X, SALA_Y + 2, SALA_Z - SALA_R + 1),
                    net.minecraft.sounds.SoundEvents.PORTAL_TRIGGER,
                    net.minecraft.sounds.SoundSource.AMBIENT, 1.0F, 0.6F);
            aviso(p, "\u00a7a\u00a7lO PORT\u00c3O SE ABRIU", 6, 0);
            try {
                p.displayClientMessage(Component.literal(
                        "\u00a7aO portal atrás do trono está aberto. \u00a77Atravesse para voltar à vida.")
                        .withStyle(ChatFormatting.GREEN), false);
            } catch (Throwable ignorado) {
                // jogador de teste: sem chat
            }
            return true;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: abrir o portão do julgamento falhou", t);
            return false;
        }
    }

    // ================================================================ RESULTADOS
    /**
     * <b>VITÓRIA</b> — o ADM declara que o jogador venceu: ele volta à vida com +2 vidas
     * canônicas e leva o item do boss.
     */
    public static void vitoria(ServerPlayer p) {
        try {
            RPWorldData dados = RPWorldData.get(p.getServer());
            int atuais = dados.getCanonLives(p.getUUID());
            int novos = Math.min(5, atuais + 2);
            dados.setCanonLives(p.getUUID(), novos);
            dados.marcarPersonagemPerdido(p.getUUID(), false);
            sair(p);
            darItemDoBoss(p);
            aviso(p, "\u00a7a\u00a7lVOC\u00ca VENCEU O JULGAMENTO", 10, 7);
            try {
                p.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
            } catch (Throwable ignorado) {
                // jogador de teste
            }
            p.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "\u00a7f" + p.getName().getString()
                    + " \u00a77voltou do Julgamento \u2014 e agora tem \u00a7f" + novos + " vidas\u00a77.")
                    .withStyle(ChatFormatting.GRAY), false);
            voltarParaCasa(p);
            RPTagMod.LOGGER.info("RP Tag: {} VENCEU o julgamento (+2 vidas = {})",
                    p.getName().getString(), novos);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: vitória do julgamento falhou", t);
        }
    }

    /** <b>DERROTA</b> — perdeu a luta: o personagem está perdido em definitivo (o ADM desfaz). */
    public static void derrota(ServerPlayer p) {
        try {
            RPWorldData dados = RPWorldData.get(p.getServer());
            dados.setCanonLives(p.getUUID(), 0);
            dados.marcarPersonagemPerdido(p.getUUID(), true);
            sair(p);
            aviso(p, "\u00a74\u00a7lVOC\u00ca PERDEU", 12, 4);
            try {
                p.playNotifySound(net.minecraft.sounds.SoundEvents.WITHER_DEATH,
                        net.minecraft.sounds.SoundSource.AMBIENT, 1.0F, 0.6F);
            } catch (Throwable ignorado) {
                // jogador de teste
            }
            p.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                    "\u00a74" + p.getName().getString()
                    + " \u00a7cperdeu o julgamento. \u00a77O personagem se foi.")
                    .withStyle(ChatFormatting.RED), false);
            voltarParaCasa(p);
            RPTagMod.LOGGER.info("RP Tag: {} PERDEU o julgamento (personagem perdido)",
                    p.getName().getString());
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: derrota do julgamento falhou", t);
        }
    }

    /** devolve o jogador ao mundo normal (spawn), com a animação de volta. */
    public static void voltarParaCasa(ServerPlayer p) {
        ServerLevel destino = p.getServer().overworld();
        BlockPos spawn = destino.getSharedSpawnPos();
        p.teleportTo(destino, spawn.getX() + 0.5D, spawn.getY() + 0.1D, spawn.getZ() + 0.5D,
                p.getYRot(), p.getXRot());
        dadosDeVolta(p);
    }

    private static void dadosDeVolta(ServerPlayer p) {
        NO_JUIZO.remove(p.getUUID());
        NA_SALA.remove(p.getUUID());
        SAIDA.remove(p.getUUID());
        AGENDADOS.remove(p.getUUID());
        ULTIMA_DICA.remove(p.getUUID());
        ULTIMO_BICHO.remove(p.getUUID());
        ENTROU_EM.remove(p.getUUID());
    }

    /** o item do boss (relíquia) + a mensagem. */
    public static void darItemDoBoss(ServerPlayer p) {
        try {
            p.getInventory().add(new net.minecraft.world.item.ItemStack(RPTagMod.RELIQUIA_JULGAMENTO.get()));
            p.displayClientMessage(Component.literal(
                    "\u00a7dVocê tomou o item do boss: \u00a7f" + "RELÍQUIA DO JULGAMENTO")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: dar o item do boss falhou", t);
        }
    }

    /** entrega a CHAVE (o ADM dropa no chão ou entrega na mão). */
    public static void darChave(ServerPlayer p) {
        p.getInventory().add(new net.minecraft.world.item.ItemStack(RPTagMod.CHAVE_JULGAMENTO.get()));
        p.displayClientMessage(Component.literal(
                "\u00a7eA CHAVE DO JULGAMENTO está na sua mão.").withStyle(ChatFormatting.YELLOW), false);
    }

    // ================================================================ O LABIRINTO
    /** constrói os chunks em volta de um ponto (limitado: o servidor não pode engasgar). */
    private static void construirEmVolta(ServerLevel lv, BlockPos centro, int raioChunks) {
        int cx = centro.getX() >> 4, cz = centro.getZ() >> 4;
        for (int dx = -raioChunks; dx <= raioChunks; dx++) {
            for (int dz = -raioChunks; dz <= raioChunks; dz++) {
                construirChunk(lv, cx + dx, cz + dz);
            }
        }
    }

    /** hash estável (o mesmo canto do labirinto é sempre o mesmo). */
    private static int hash(int a, int b, int c) {
        int h = a * 73856093 ^ b * 19349663 ^ c * 83492791;
        h ^= (h >>> 13);
        h *= 0x5bd1e995;
        return h ^ (h >>> 15);
    }

    /** um pedaço de labirinto: corredor de 4, parede de 2, teto fechado. */
    private static void construirChunk(ServerLevel lv, int cx, int cz) {
        String k = "j:" + cx + ":" + cz;
        if (PRONTOS.contains(k) || PRONTOS.size() > 20000) {
            return;
        }
        PRONTOS.add(k);
        try {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    int wx = (cx << 4) + x, wz = (cz << 4) + z;
                    int celX = Math.floorDiv(wx, CELULA), celZ = Math.floorDiv(wz, CELULA);
                    int lx = Math.floorMod(wx, CELULA), lz = Math.floorMod(wz, CELULA);
                    // (5.28.1) A REGRA QUE ACABOU COM OS CAMINHOS SEM SAÍDA.
                    // Antes cada parede era sorteada por um hash solto: isso fechava
                    // bolsões inteiros (salas cercadas de parede por todos os lados) e podia
                    // deixar a PRÓPRIA SAÍDA num bloco isolado. Agora a parede a LESTE só
                    // existe em linha ÍMPAR e a parede ao SUL só em linha PAR — o que dá, por
                    // construção, um caminho entre quaisquer dois pontos (prova em
                    // {@link #ehParede}).
                    boolean parede = ehParede(celX, celZ, lx, lz);
                    lv.setBlock(m.set(wx, PISO, wz), Blocks.BLACKSTONE.defaultBlockState(), 2);
                    for (int y = PISO + 1; y < TETO; y++) {
                        lv.setBlock(m.set(wx, y, wz), parede
                                ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                                : Blocks.AIR.defaultBlockState(), 2);
                    }
                    lv.setBlock(m.set(wx, TETO, wz), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
                }
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: chunk do labirinto falhou", t);
        }
    }

    /**
     * (5.28.1) <b>A PAREDE DO LABIRINTO — E A PROVA DE QUE NENHUM CAMINHO FICA SEM SAÍDA.</b>
     *
     * <p>Bug que o dono viu: <i>"o labirinto está gerando caminhos sem saida"</i>. A geração
     * antiga sorteava cada parede com um hash independente, então nada impedia fechar uma
     * "sala" de 4x4 por todos os lados — e pior, a própria saída podia cair num bloco cercado.
     *
     * <p>Regra nova (corredor de 4 blocos, parede de 2, célula de 6):
     * <ul>
     *   <li><b>parede a LESTE</b> (entre a célula e a vizinha do lado): só existe se a linha
     *       (Z) for <b>ÍMPAR</b>. Em linha PAR ela é sempre aberta;</li>
     *   <li><b>parede ao SUL</b> (entre a célula e a de baixo): só existe se a linha for
     *       <b>PAR</b>. Em linha ÍMPAR é sempre aberta;</li>
     *   <li>o canto de 2x2 (pilar) é sempre maciço — é ele que dá o desenho de labirinto;</li>
     *   <li>aberturas extras (65% nas linhas ímpares, 30% nas pares) criam <b>laços</b>, o que
     *       reduz becos sem saída. Elas só ABREM: nunca fecham uma passagem garantida.</li>
     * </ul>
     *
     * <p><b>Prova da conectividade:</b> numa linha PAR todas as células se ligam na horizontal
     * (parede leste sempre aberta). Toda célula de linha ÍMPAR tem a parede sul aberta, logo se
     * liga à célula de baixo — que está numa linha PAR. Como toda linha par é uma faixa
     * contínua e toda célula de linha ímpar desce para uma linha par, <b>qualquer</b> célula
     * alcança qualquer outra. Como a regra só depende de (celX, celZ) — e não de onde o chunk
     * começa —, isso vale para o labirinto inteiro, inclusive para as partes que ainda não
     * foram construídas.
     */
    private static boolean ehParede(int celX, int celZ, int lx, int lz) {
        boolean bandaX = lx >= 4, bandaZ = lz >= 4;
        if (bandaX && bandaZ) {
            return true;                                        // pilar do canto
        }
        if (bandaX) {                                           // parede LESTE
            if (Math.floorMod(celZ, 2) == 0) {
                return false;                                   // linha par: passa sempre
            }
            return hash(celX, celZ, 11) % 100 >= 65;            // 65% de laços
        }
        if (bandaZ) {                                           // parede SUL
            if (Math.floorMod(celZ, 2) != 0) {
                return false;                                   // linha ímpar: desce sempre
            }
            return hash(celX, celZ, 22) % 100 >= 30;            // 30% de laços
        }
        return false;                                           // corredor
    }

    /**
     * (5.28.1) CENTRO DO CORREDOR da célula que contém essa coordenada.
     *
     * <p>A saída nunca mais pode cair dentro da parede: o ponto sorteado é "encostado" no
     * centro do corredor da célula onde caiu. Antes ela podia nascer cercada de bloco, e aí
     * nem existia caminho até ela.
     */
    private static int snapCelula(int w) {
        return Math.floorDiv(w, CELULA) * CELULA + 2;
    }

    /** a saída: um obelisco de luz (só nasce quando o jogador chega perto). */
    private static void construirSaida(ServerLevel lv, BlockPos base) {
        String k = "s:" + base.asLong();
        if (PRONTOS.contains(k)) {
            return;
        }
        PRONTOS.add(k);
        try {
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    boolean borda = Math.abs(x) == 3 || Math.abs(z) == 3;
                    lv.setBlock(m.set(base.getX() + x, PISO, base.getZ() + z),
                            borda ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                                    : Blocks.SMOOTH_QUARTZ.defaultBlockState(), 2);
                    for (int y = 1; y <= 5; y++) {
                        lv.setBlock(m.set(base.getX() + x, PISO + y, base.getZ() + z),
                                Blocks.AIR.defaultBlockState(), 2);
                    }
                    lv.setBlock(m.set(base.getX() + x, PISO + 6, base.getZ() + z),
                            borda ? Blocks.SEA_LANTERN.defaultBlockState()
                                    : Blocks.CRYING_OBSIDIAN.defaultBlockState(), 2);
                }
            }
            // o miolo é o portal: quem pisa vai pra sala do julgamento
            for (int x = -1; x <= 1; x++) {
                for (int y = 1; y <= 3; y++) {
                    lv.setBlock(m.set(base.getX() + x, PISO + y, base.getZ()),
                            RPTagMod.ESCURIDAO.get().defaultBlockState(), 2);
                }
            }
            lv.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    base.getX() + 0.5D, PISO + 3.0D, base.getZ() + 0.5D, 80, 1.4D, 2.0D, 1.4D, 0.02D);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: obelisco de saída falhou", t);
        }
    }

    /** bichos bufados: mais fortes conforme o tempo passa. */
    private static void soltarBicho(ServerLevel lv, ServerPlayer p) {
        try {
            AABB area = p.getBoundingBox().inflate(48.0D);
            int quantos = lv.getEntitiesOfClass(Mob.class, area, m -> m.isAlive()
                    && !(m instanceof net.minecraft.world.entity.animal.Animal)).size();
            if (quantos >= 14) {
                return;
            }
            // (5.28.0) o labirinto é quase todo parede: sorteia até 12 cantos e só spawna no
            // AR (senão o mob nasce dentro do bloco e o Minecraft recusa — era por isso que a
            // primeira prova voltou com ENTIDADES 0->0).
            BlockPos pos = null;
            for (int tentativa = 0; tentativa < 12 && pos == null; tentativa++) {
                double ang = Math.random() * Math.PI * 2.0D;
                double d = 6.0D + Math.random() * 18.0D;
                BlockPos cand = BlockPos.containing(p.getX() + Math.cos(ang) * d, PISO + 1,
                        p.getZ() + Math.sin(ang) * d);
                if (lv.isEmptyBlock(cand) && lv.isEmptyBlock(cand.above())) {
                    pos = cand;
                }
            }
            if (pos == null) {
                pos = p.blockPosition().offset(2, 0, 2);
            }
            int r = (int) (Math.random() * 4);
            EntityType<? extends Mob> tipo = switch (r) {
                case 0 -> EntityType.ZOMBIE;
                case 1 -> EntityType.HUSK;
                case 2 -> EntityType.SKELETON;
                default -> EntityType.SPIDER;
            };
            // (5.28.0) NADA de EntityType.spawn: ele recusa em chão artificial e devolve null
            // (era o "ENTIDADES 0->0" da prova). Criar e somar à mão sempre funciona.
            Mob mob = tipo.create(lv);
            if (mob == null) {
                return;
            }
            mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    (float) (Math.random() * 360.0D), 0.0F);
            boolean add = lv.addFreshEntity(mob);
            if (!add) {
                return;
            }
            mob.setCustomName(Component.literal("\u00a78algo do labirinto"));
            mob.setPersistenceRequired();
            bufado(mob, p);
            lv.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 0.6D, pos.getZ() + 0.5D, 12, 0.4D, 0.6D, 0.4D, 0.02D);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: bicho do labirinto falhou", t);
        }
    }

    /** (5.28.0) quando cada um entrou no labirinto (pra dificuldade crescer de verdade). */
    private static final Map<UUID, Long> ENTROU_EM = new HashMap<>();

    /** "bufado": vida, força, velocidade e resistência — e ele NÃO desiste de perseguir. */
    private static void bufado(LivingEntity mob, ServerPlayer p) {
        long desde = ENTROU_EM.getOrDefault(p.getUUID(), System.currentTimeMillis());
        int min = Math.max(1, (int) ((System.currentTimeMillis() - desde) / 60000L));  // cresce com o tempo
        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 900, 1, false, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 900, min > 6 ? 2 : 1, false, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 900, 0, false, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 900, 0, false, false, false));
        if (mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) != null) {
            mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                    .setBaseValue(40.0D);
            mob.setHealth(40.0F);
        }
        if (mob instanceof Mob m && p != null) {
            m.setTarget(p);
        }
    }

    // ================================================================ TICK
    /** chamado de 4 em 4 ticks pelo {@link ServerEvents}. */
    public static void tick(MinecraftServer servidor) {
        long agora = System.currentTimeMillis();
        // 1) quem zerou as vidas e está esperando a hora
        if (!AGENDADOS.isEmpty()) {
            List<UUID> prontos = new ArrayList<>();
            for (Map.Entry<UUID, Long> en : AGENDADOS.entrySet()) {
                if (agora >= en.getValue()) {
                    prontos.add(en.getKey());
                }
            }
            for (UUID id : prontos) {
                AGENDADOS.remove(id);
                ServerPlayer p = servidor.getPlayerList().getPlayer(id);
                if (p != null) {
                    entrar(p);
                }
            }
        }
        for (ServerPlayer p : servidor.getPlayerList().getPlayers()) {
            try {
                if (ehJuizo(p.level())) {
                    tickLabirinto(p, (ServerLevel) p.level(), agora);
                } else if (ehSala(p.level())) {
                    tickSala(p, (ServerLevel) p.level(), agora);
                } else if (NO_JUIZO.contains(p.getUUID())) {
                    // fugiu por teleporte (comando/porta): sai da lista, o jogo segue
                    dadosDeVolta(p);
                }
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: tick do julgamento falhou para "
                        + p.getName().getString(), t);
            }
        }
    }

    private static void tickLabirinto(ServerPlayer p, ServerLevel lv, long agora) {
        // --- o mundo vai nascendo na frente dele (3 chunks por passada, orçamento baixo)
        int antes = PRONTOS.size();
        int cx = p.getBlockX() >> 4, cz = p.getBlockZ() >> 4;
        for (int dx = -2; dx <= 2 && PRONTOS.size() - antes < 6; dx++) {
            for (int dz = -2; dz <= 2 && PRONTOS.size() - antes < 6; dz++) {
                construirChunk(lv, cx + dx, cz + dz);
            }
        }
        // --- bichos: um a cada 4 s (no máximo)
        Long ultimo = ULTIMO_BICHO.get(p.getUUID());
        if (ultimo == null || agora - ultimo > 4000L) {
            ULTIMO_BICHO.put(p.getUUID(), agora);
            soltarBicho(lv, p);
        }
        // --- a saída: nasce quando ele chega perto e some do resto do mundo
        BlockPos saida = SAIDA.get(p.getUUID());
        if (saida == null) {
            saida = new BlockPos(0, PISO + 1, 0);
            SAIDA.put(p.getUUID(), saida);
        }
        double d = p.blockPosition().distSqr(saida);
        if (d < 96.0D * 96.0D) {
            construirSaida(lv, saida);
        }
        if (d < 16.0D) {
            achouSaida(p);          // pisou na saída
            return;
        }
        // --- dica de direção de vez em quando (o labirinto é grande demais pra sorte)
        Long dica = ULTIMA_DICA.get(p.getUUID());
        if (dica == null || agora - dica > 15000L) {
            ULTIMA_DICA.put(p.getUUID(), agora);
            String rumo = rumo(p.blockPosition(), saida);
            int dist = (int) Math.sqrt(d);
            p.displayClientMessage(Component.literal(
                    "\u00a78a saída respira a \u00a77" + rumo + "\u00a78 (" + dist + " blocos)")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
        }
    }

    private static String rumo(BlockPos daqui, BlockPos alvo) {
        double dx = alvo.getX() - daqui.getX(), dz = alvo.getZ() - daqui.getZ();
        String ns = dz < -24 ? "norte" : dz > 24 ? "sul" : "";
        String lo = dx < -24 ? "oeste" : dx > 24 ? "leste" : "";
        if (ns.isEmpty() && lo.isEmpty()) {
            return "bem aqui";
        }
        return (ns + " " + lo).trim();
    }

    /** na sala: se ele atravessar o portão aberto, volta à vida (+2 vidas). */
    private static void tickSala(ServerPlayer p, ServerLevel lv, long agora) {
        if (!portaoAberto) {
            return;
        }
        BlockPos portao = new BlockPos(SALA_X, SALA_Y + 1, SALA_Z - SALA_R + 1);
        if (p.blockPosition().distSqr(portao) <= 6.0D || (
                Math.abs(p.getX() - portao.getX()) <= 2.0D
                && Math.abs(p.getZ() - portao.getZ()) <= 1.6D
                && p.getY() >= SALA_Y && p.getY() <= SALA_Y + 5)) {
            vitoria(p);
        }
    }

    // ================================================================ ENTRADA POR MORTE
    public static void aoRenascer(ServerPlayer p) {
        // morreu DENTRO do julgamento: volta pra dentro (morrer não é fuga)
        if (!NO_JUIZO.contains(p.getUUID()) && !NA_SALA.contains(p.getUUID())) {
            return;
        }
        if (NA_SALA.contains(p.getUUID())) {
            ServerLevel sala = p.getServer().getLevel(CHAVE_SALA);
            if (sala != null) {
                p.teleportTo(sala, SALA_X + 0.5D, SALA_Y + 1.0D, SALA_Z + SALA_R - 3.5D, 180.0F, 0.0F);
            }
            return;
        }
        ServerLevel lv = p.getServer().getLevel(CHAVE_JUIZO);
        if (lv != null) {
            construirEmVolta(lv, new BlockPos(0, PISO + 1, 0), 1);
            p.teleportTo(lv, 0.5D, PISO + 1.0D, 0.5D, 0.0F, 0.0F);
        }
    }
}
