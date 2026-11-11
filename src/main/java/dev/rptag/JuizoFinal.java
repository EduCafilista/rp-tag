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
    /**
     * (5.29.0) o canto de referência da arena. A dimensão da sala é plana (uma camada de
     * bedrock em y=0) e a arena do dono já traz o próprio piso de bedrock no y=0 do arquivo —
     * então ela é montada <b>em cima do chão</b>, sem ficar flutuando.
     */
    private static final int SALA_X = 0, SALA_Y = 0, SALA_Z = 0;
    /** (5.29.0) a arena do dono (arquivo .schem): canto relativo ao centro. */
    private static final int ARENA_X = JuizoSala.ORIGEM_X;
    private static final int ARENA_Z = JuizoSala.ORIGEM_Z;

    /** (5.29.0) os pontos da arena, achados depois de construir (sempre em ar livre). */
    private static BlockPos pontoEntrada;
    private static BlockPos pontoTrono;
    private static BlockPos pontoPortao;
    /**
     * (5.33.0) A PEDRA DO RETORNO — o bloco que o dono clica com o botão direito para o
     * portal de saída nascer (pedido 90: <i>"o portal deveria aparecer só depois de eu clicar
     * com botão direito no bloco que vai gerar o portal"</i>).
     */
    private static BlockPos pontoGerador;
    /** o bloco clicável que gera o portal. */
    public static final net.minecraft.world.level.block.Block BLOCO_GERADOR =
            net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN;

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

    /**
     * (5.36.0) <b>ONDE CADA UM MORREU</b> — o conserto do "morri no juízo e fui parar em outro
     * lugar".
     *
     * <p>Antes o respawn adivinhava pelo conjunto {@code NA_SALA}/{@code NO_JUIZO}. Só que esses
     * conjuntos só eram limpos por alguns caminhos: quem saía da arena por teleporte (comando,
     * portal, morte seguida de outro teleporte) ficava marcado <b>para sempre</b>. Resultado: a
     * pessoa entrava no VAZIO e, na primeira morte, era jogada na SALA DO JULGAMENTO — exatamente
     * o "fui parar em juizo sala" que o dono viu.
     *
     * <p>Agora a morte grava a dimensão de verdade ({@link #marcarMorte(ServerPlayer)}) e o
     * respawn devolve a pessoa <b>para a dimensão em que ela morreu</b>, nada mais.
     */
    private static final Map<UUID, String> MORREU_EM = new HashMap<>();
    /** quando cada jogador foi visto pela última vez dentro de uma dimensão do Juízo. */
    private static final Map<UUID, Long> VISTO_NO_JUIZO = new HashMap<>();

    /** (5.36.0) chamado no evento de morte: guarda a dimensão para o respawn. */
    public static void marcarMorte(ServerPlayer p) {
        try {
            MORREU_EM.put(p.getUUID(), p.level().dimension().location().toString());
        } catch (Throwable ignorado) {
            // sem memória: o respawn cai na regra pelos conjuntos
        }
    }

    /** (5.36.0) esquece tudo de um jogador (logout): nada de estado vazando pra próxima sessão. */
    public static void esquecer(ServerPlayer p) {
        dadosDeVolta(p);
        MORREU_EM.remove(p.getUUID());
        VISTO_NO_JUIZO.remove(p.getUUID());
    }

    /** (5.36.0) limpa a marca de morte (chamado no respawn e na saída do jogador). */
    public static String consumirMorte(ServerPlayer p) {
        try {
            return MORREU_EM.remove(p.getUUID());
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * (5.32.0) AS FRASES DO JUIZO — o que o dono pediu: um aviso curto no estilo "JUIZO: FRASE",
     * daqueles simples que aparecem no meio da tela, no lugar do "ACHA QUE CONSEGUE SUA REDENÇÃO?".
     */
    public static final String[] FRASES_DO_JUIZO = {
        "ACHA QUE CONSEGUE A REDENÇÃO?",
        "SUAS MÃOS NÃO ESTÃO LIMPAS.",
        "AQUI, TODOS CONFESSAM.",
        "A BALANÇA NÃO SE COMPRA.",
        "DE PÉ, CONDENADO.",
        "O SILÊNCIO TAMBÉM É CULPA.",
        "NINGUÉM SAI INOCENTE.",
    };

    /** (5.32.0) toca um som no jogador sem estourar quando é jogador de teste. */
    private static void som(ServerPlayer p, net.minecraft.sounds.SoundEvent som, float vol, float tom) {
        try {
            p.playNotifySound(som, net.minecraft.sounds.SoundSource.AMBIENT, vol, tom);
        } catch (Throwable ignorado) {
            // jogador de teste: sem som
        }
    }

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

    /**
     * manda o jogador pro labirinto AGORA (também é o `/juizo entrar`).
     *
     * <p>(5.30.0) O labirinto agora é o <b>Reino do outro mod</b> — se ele existir. O labirinto
     * de blocos do próprio RP Tag fica só como reserva para servidor sem o outro mod instalado
     * (sem ele, o jogador que zerasse as vidas não teria pra onde ir).
     */
    public static boolean entrar(ServerPlayer p) {
        // (5.33.0) O LABIRINTO VOLTOU. O dono: "o juizo entrar, você tirou o labirinto? eu falei
        // para deixar, volta a versão e deixa aquele último labirinto que você fez". O que era
        // para virar dimensão do outro mod era SÓ a animação do portal. Então quem zera as
        // vidas cai no labirinto de blocos do próprio RP Tag, como na 5.28.1.
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
            // (5.32.0) só a TELA (nada de chat): "tira esse tanto de aviso do chat"
            aviso(p, "\u00a74\u00a7lO JULGAMENTO", 8, 4);
            som(p, net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.7F, 0.6F);
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

    /** (5.31.0) aviso na barra de ação para outros sistemas (poderes do vazio usam este). */
    public static void avisoPublico(ServerPlayer p, String texto, int segundos, int estilo) {
        aviso(p, texto, segundos, estilo);
    }

    /** (5.30.0) o jogador está procurando a saída (é a marca que o portal do reino respeita). */
    public static boolean estaProcurando(UUID id) {
        return NO_JUIZO.contains(id);
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
            garantirGerador(sala);       // (5.33.0) a pedra do retorno sempre existe
            BlockPos entrada = pontoEntrada != null ? pontoEntrada
                    : new BlockPos(0, SALA_Y + 2, ARENA_Z + JuizoSala.COMPRIMENTO - 12);
            p.teleportTo(sala, entrada.getX() + 0.5D, entrada.getY(), entrada.getZ() + 0.5D,
                    180.0F, 0.0F);
            p.setHealth(p.getMaxHealth());
            // (5.32.0) a INTRODUÇÃO da arena — o dono pediu um cartaz curto, estilo "Frases":
            // "JUIZO: FRASE" (aqueles avisos simples e bonitos, sem encher o chat).
            String frase = FRASES_DO_JUIZO[(int) (Math.random() * FRASES_DO_JUIZO.length)];
            aviso(p, "\u00a74\u00a7lJUIZO\u00a7r\u00a77: \u00a7f" + frase, 9, 4);
            // (5.33.0) a dica da saída: o portal só nasce do clique na pedra do retorno
            try {
                p.displayClientMessage(Component.literal(
                        "\u00a78A sa\u00edda \u00e9 a \u00a77pedra escura ao lado do trono\u00a78 "
                        + "— bot\u00e3o direito nela.").withStyle(ChatFormatting.DARK_GRAY), false);
            } catch (Throwable ignorado) {
                // jogador de teste
            }
            vultoDoBoss(sala);
            som(p, net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL, 0.8F, 0.7F);
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
        if (JuizoSala.pronta()) {
            return;
        }
        try {
            // (5.29.0) A ARENA DO DONO — o .schem que ele mandou (98 x 71 x 93), centrada em
            // (0, SALA_Y, 0). O piso dela já vem no arquivo; a dimensão é plana no bedrock.
            JuizoSala.construir(lv, ARENA_X, SALA_Y, 0 + ARENA_Z);
            // os três pontos da cena, sempre em ar livre
            pontoEntrada = colunaLivre(lv, new BlockPos(0, SALA_Y + 2, ARENA_Z + JuizoSala.COMPRIMENTO - 12));
            pontoTrono = colunaLivre(lv, new BlockPos(0, SALA_Y + 2, ARENA_Z + 14));
            pontoPortao = colunaLivre(lv, new BlockPos(0, SALA_Y + 2, ARENA_Z + 6));
            portaoAberto = false;
            garantirGerador(lv);
            RPTagMod.LOGGER.info("RP Tag: arena do Julgamento pronta (entrada {}, trono {}, portao {})",
                    pontoEntrada, pontoTrono, pontoPortao);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: construcao da arena falhou", t);
        }
    }

    /**
     * (5.29.0) o ponto mais próximo de {@code perto} que tem chão e três blocos de ar — assim a
     * gente nunca joga o jogador (ou o portal) dentro de um pilar da arena do dono.
     */
    private static BlockPos colunaLivre(ServerLevel lv, BlockPos perto) {
        try {
            for (int r = 0; r <= 14; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;   // só o anel de raio r
                        }
                        for (int dy = 0; dy <= 6; dy++) {
                            BlockPos p = perto.offset(dx, dy, dz);
                            if (lv.getBlockState(p.below()).isSolid()
                                    && lv.isEmptyBlock(p)
                                    && lv.isEmptyBlock(p.above())
                                    && lv.isEmptyBlock(p.above(2))) {
                                return p;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: procurar ponto livre na arena falhou", t);
        }
        return perto;
    }


    /**
     * (5.29.0) A INTRODUÇÃO DO BOSS — <b>SEM boneco</b>.
     *
     * <p>O dono: <i>"pode tirar esse stand que você colocou como o JUÍZO kkkk eu falei que vai ser
     * um ADM que vai interpretar"</i>. O armor stand que ficava no trono saiu de vez: a
     * introdução agora é o aviso na tela, o som e uma marcação de partículas no chão onde o ADM
     * deve se posicionar (partícula, não entidade).
     */
    private static void vultoDoBoss(ServerLevel lv) {
        try {
            BlockPos trono = pontoTrono != null ? pontoTrono
                    : new BlockPos(0, SALA_Y + 2, ARENA_Z + 14);
            lv.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    trono.getX() + 0.5D, trono.getY() + 0.4D, trono.getZ() + 0.5D,
                    60, 1.2D, 0.4D, 1.2D, 0.02D);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: marcação do trono falhou", t);
        }
    }


    /**
     * (5.33.0) <b>A PEDRA DO RETORNO — o portal nasce do clique, não sozinho.</b>
     *
     * <p>O dono (pedido 90, item 7): <i>"o portal está bugado, e ele deveria aparecer só depois de
     * eu clicar com botão direito no bloco que vai gerar o portal, olha o mod, e veja como
     * funciona o portal dele"</i>. É exatamente o desenho do Ronova: no mod o portal não existe
     * no mundo — ele é <b>criado</b> por um bloco/uso (o rift), com o vórtice desenhado por cima.
     *
     * <p>Aqui a pedra é uma <b>obsidiana chorosa</b> plantada de frente para o trono, em cima de
     * um altarzinho de deepslate. Clique direito nela → a moldura e o miolo de escuridão crescem
     * <b>atrás do trono</b>, com o vórtice do cliente por cima. Antes dela não existia portal
     * nenhum na sala (era por isso que não tinha para onde ir).
     */
    public static void garantirGerador(ServerLevel lv) {
        try {
            if (!ehSala(lv)) {
                return;
            }
            if (pontoGerador == null) {
                // em frente ao trono, no caminho de quem entra
                pontoGerador = colunaLivre(lv,
                        new BlockPos(0, SALA_Y + 2, ARENA_Z + 20));
            }
            BlockPos g = pontoGerador;
            // o altarzinho: base 3x3 e a pedra no meio (só põe o que estiver faltando)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos chao = g.offset(dx, -1, dz);
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (lv.getBlockState(chao).isAir()) {
                        lv.setBlock(chao, net.minecraft.world.level.block.Blocks
                                .POLISHED_DEEPSLATE.defaultBlockState(), 2);
                    }
                }
            }
            if (!lv.getBlockState(g).is(BLOCO_GERADOR)) {
                lv.setBlock(g, BLOCO_GERADOR.defaultBlockState(), 2);
            }
            // o chão embaixo da pedra, para ela nunca ficar flutuando
            if (lv.getBlockState(g.below()).isAir()) {
                lv.setBlock(g.below(), net.minecraft.world.level.block.Blocks
                        .POLISHED_DEEPSLATE.defaultBlockState(), 2);
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: pedra do retorno falhou", t);
        }
    }

    /**
     * (5.33.0) clique direito na PEDRA DO RETORNO.
     *
     * @return true se o clique era nela (aí o jogo não faz mais nada com o clique)
     */
    public static boolean usarGerador(ServerPlayer p, BlockPos pos) {
        try {
            if (!ehSala(p.level()) || pos == null) {
                return false;
            }
            boolean eh = (pontoGerador != null && pontoGerador.equals(pos))
                    || p.level().getBlockState(pos).is(BLOCO_GERADOR);
            if (!eh) {
                return false;
            }
            ServerLevel sala = (ServerLevel) p.level();
            garantirGerador(sala);
            // (5.34.0) SÓ ABRE COM A CHAVE NA MÃO — o dono: "ele só pode entrar depois de
            // colocar a chave na obsidiana e abrir o portal no juizo sala".
            boolean temChave = p.getMainHandItem().is(RPTagMod.CHAVE_JULGAMENTO.get())
                    || p.getOffhandItem().is(RPTagMod.CHAVE_JULGAMENTO.get());
            if (!temChave) {
                p.displayClientMessage(Component.literal(
                        "\u00a78A pedra est\u00e1 fria. \u00a77Traga a \u00a7fCHAVE DO JULGAMENTO"
                        + "\u00a77 e clique aqui.").withStyle(ChatFormatting.DARK_GRAY), true);
                return true;      // consome o clique: nada de portal sem chave
            }
            if (!gerarPortal(p, sala)) {
                return true;
            }
            // a chave se gasta no portal (é o "colocar a chave na obsidiana")
            if (!p.isCreative()) {
                if (p.getMainHandItem().is(RPTagMod.CHAVE_JULGAMENTO.get())) {
                    p.getMainHandItem().shrink(1);
                } else {
                    p.getOffhandItem().shrink(1);
                }
            }
            return true;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: clique na pedra do retorno falhou", t);
            return false;
        }
    }

    /** a CHAVE abre o portal atrás do boss (o caminho antigo continua valendo). */
    public static boolean abrirPortao(ServerPlayer p) {
        if (!ehSala(p.level())) {
            try {
                p.displayClientMessage(Component.literal(
                        "\u00a78A chave s\u00f3 gira na sala do julgamento.")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
            } catch (Throwable ignorado) {
                // jogador de teste
            }
            return false;
        }
        return gerarPortal(p, (ServerLevel) p.level());
    }

    /**
     * (5.33.0) <b>O PORTAL DE SAÍDA NASCE AQUI.</b>
     *
     * <p>Um método só, chamado pela pedra do retorno e pela chave. Ele:
     * <ol>
     *   <li>limpa um bolsão de ar (o portal nunca mais fica enterrado na estrutura);</li>
     *   <li>levanta a moldura de obsidiana chorosa e o miolo de escuridão atrás do trono;</li>
     *   <li>manda o vórtice para o cliente (o mesmo olho do portal do outro mod);</li>
     *   <li>marca o portão como aberto — quem atravessar volta à vida com +2 vidas.</li>
     * </ol>
     */
    public static boolean gerarPortal(ServerPlayer p, ServerLevel sala) {
        try {
            if (!JuizoSala.pronta()) {
                construirSala(sala);
            }
            BlockPos base = pontoPortao != null ? pontoPortao
                    : new BlockPos(0, SALA_Y + 2, ARENA_Z + 6);
            portaoAberto = false;

            // 1) bolsão de ar: 7 de largura, 8 de altura, 3 de profundidade
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 5; dy++) {
                    for (int dz = -2; dz <= 1; dz++) {
                        sala.setBlock(base.offset(dx, dy, dz),
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            // 2) A PAREDE DE FUNDO — (5.34.0) era AQUI o bug da "parte de trás": a moldura
            // tinha um bloco só de espessura, então por trás se via o vazio/estrutura e o
            // vórtice parecia flutuando. Agora o portal tem costas fechadas de obsidiana.
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -2; dy <= 5; dy++) {
                    sala.setBlock(base.offset(dx, dy, -1),
                            net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN
                                    .defaultBlockState(), 2);
                }
            }
            // 3) a moldura da frente (anel de obsidiana chorosa em volta da boca)
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -1; dy <= 4; dy++) {
                    boolean borda = Math.abs(dx) == 2 || dy == -1 || dy == 4;
                    if (borda) {
                        sala.setBlock(base.offset(dx, dy, 0),
                                net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN
                                        .defaultBlockState(), 2);
                    }
                }
            }
            // 4) o miolo: a escuridão do portal (3 de largura por 5 de altura)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 4; dy++) {
                    sala.setBlock(base.offset(dx, dy, 0),
                            RPTagMod.ESCURIDAO.get().defaultBlockState(), 2);
                }
            }
            // 5) o chão em frente e atrás, para ninguém cair num buraco ao atravessar
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (sala.getBlockState(base.offset(dx, -2, dz)).isAir()) {
                        sala.setBlock(base.offset(dx, -2, dz),
                                net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE
                                        .defaultBlockState(), 2);
                    }
                }
            }
            portaoAberto = true;
            // 6) O VÓRTICE — (5.34.0) agora NA FRENTE do miolo (z + 0,55), virado para a sala.
            // Antes ele nascia atrás (z - 0,6) e ficava enterrado na parede: era o "bugado".
            double vx = base.getX() + 0.5D;
            double vy = base.getY() + 2.2D;
            double vz = base.getZ() + 0.55D;
            for (ServerPlayer outro : sala.players()) {
                try {
                    PacketDistributor.sendToPlayer(outro,
                            new VazioPoderPayload(4, vx, vy, vz, 0.0D, 0.0D, 0.0D));
                } catch (Throwable ignorado) {
                    // jogador de teste: sem conexão
                }
            }
            sala.playSound(null, base, net.minecraft.sounds.SoundEvents.PORTAL_TRIGGER,
                    net.minecraft.sounds.SoundSource.AMBIENT, 1.0F, 0.6F);
            sala.playSound(null, base, net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN,
                    net.minecraft.sounds.SoundSource.AMBIENT, 1.0F, 0.7F);
            sala.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                    vx, vy, vz, 90, 1.4D, 2.2D, 0.4D, 0.05D);
            aviso(p, "\u00a75\u00a7lO PORT\u00c3O SE ABRIU", 7, 4);
            try {
                p.displayClientMessage(Component.literal(
                        "\u00a78Atravesse a escurid\u00e3o atr\u00e1s do trono para voltar \u00e0 vida.")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
            } catch (Throwable ignorado) {
                // jogador de teste
            }
            RPTagMod.LOGGER.info("RP Tag: portal de saida do Julgamento gerado em {}", base);
            return true;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: gerar o portal de saida falhou", t);
            return false;
        }
    }

    /**
     * (5.33.0) clique direito na ESCURIDÃO da sala — o jeito mais natural de sair.
     *
     * <p>Era daqui que vinha o "aviso de morte" que o dono reclamou: o bloco de escuridão
     * respondia <i>"esta escuridão está morta"</i> em qualquer dimensão. Na sala agora ele
     * <b>atravessa</b> (vitória, +2 vidas); se o portão ainda não abriu, ele só avisa onde está
     * a pedra do retorno — nada de "morta".
     *
     * @return true se o clique foi tratado aqui
     */
    public static boolean atravessarPortao(ServerPlayer p) {
        try {
            if (!ehSala(p.level())) {
                return false;
            }
            if (!portaoAberto) {
                p.displayClientMessage(Component.literal(
                        "\u00a78Esta escurid\u00e3o ainda est\u00e1 dormente. \u00a77Ponha a "
                        + "\u00a7fCHAVE DO JULGAMENTO\u00a77 na pedra escura ao lado do trono.")
                        .withStyle(ChatFormatting.DARK_GRAY), true);
                return true;
            }
            vitoria(p);
            return true;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: atravessar o portao falhou", t);
            return true;
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
            // (5.29.0) SEM item automático: o dono — "não é para você dar quando a pessoa sair,
            // o ADM que vai dar um item específico". A vitória só devolve a vida e as +2 vidas
            // canônicas; o item sai da mão do ADM.
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



    /** entrega a CHAVE (o ADM dropa no chão ou entrega na mão). */
    public static void darChave(ServerPlayer p) {
        p.getInventory().add(new net.minecraft.world.item.ItemStack(RPTagMod.CHAVE_JULGAMENTO.get()));
        aviso(p, "\u00a7e\u00a7lA CHAVE DO JULGAMENTO", 5, 4);   // (5.32.0) tela, não chat
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
                // (5.35.0) NO JUÍZO NINGUÉM SENTE FOME.
                // O dono: "o player que estiver dentro do juízo não deve sentir fome, porque ele
                // vai correr muito, então não precisa comer". Vale para o labirinto, a arena e o
                // reino do outro mod: a barra fica cheia e a saturação no teto (sem comer, sem
                // perder vida por fome no meio da fuga).
                if (ehJuizo(p.level()) || ehSala(p.level()) || JuizoReino.eh(p.level())) {
                    semFome(p);
                    VISTO_NO_JUIZO.put(p.getUUID(), agora);
                }
                if (ehJuizo(p.level())) {
                    tickLabirinto(p, (ServerLevel) p.level(), agora);
                } else if (ehSala(p.level())) {
                    tickSala(p, (ServerLevel) p.level(), agora);
                } else if (JuizoReino.eh(p.level())) {
                    // (5.30.0) está no reino do outro mod: o relógio do labirinto não manda
                    // aqui — quem decide a saída é o portal do reino (JuizoPortalExterno)
                    continue;
                } else if (NO_JUIZO.contains(p.getUUID()) || NA_SALA.contains(p.getUUID())) {
                    // (5.36.0) fugiu por teleporte (comando/porta/morte): sai da lista.
                    // Antes só o NO_JUIZO era limpo aqui — o NA_SALA FICAVA PRESO para sempre,
                    // e era ele que jogava a pessoa na arena no respawn seguinte (o bug do
                    // "entrei no vazio e fui parar em juizo sala").
                    dadosDeVolta(p);
                }
            } catch (Throwable t) {
                RPTagMod.LOGGER.warn("RP Tag: tick do julgamento falhou para "
                        + p.getName().getString(), t);
            }
        }
    }

    /**
     * (5.35.0) <b>SEM FOME NO JUÍZO</b> — pedido do dono: <i>"o player que estiver dentro do
     * juízo não deve sentir fome, porque ele vai correr muito, então não precisa comer"</i>.
     *
     * <p>Mantém comida e saturação no máximo e tira qualquer efeito de fome que tenha sobrado.
     * É barato: só roda para quem está dentro das dimensões do julgamento.
     */
    public static void semFome(ServerPlayer p) {
        try {
            var comida = p.getFoodData();
            if (comida.getFoodLevel() < 20) {
                comida.setFoodLevel(20);
            }
            if (comida.getSaturationLevel() < 5.0F) {
                comida.setSaturation(5.0F);
            }
            p.removeEffect(net.minecraft.world.effect.MobEffects.HUNGER);
        } catch (Throwable ignorado) {
            // jogador de teste / versão diferente: a fome só não é reposta
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
        BlockPos portao = pontoPortao != null ? pontoPortao
                : new BlockPos(0, SALA_Y + 2, ARENA_Z + 6);
        if (p.blockPosition().distSqr(portao) <= 6.0D || (
                Math.abs(p.getX() - portao.getX()) <= 2.0D
                && Math.abs(p.getZ() - portao.getZ()) <= 1.6D
                && p.getY() >= portao.getY() - 1 && p.getY() <= portao.getY() + 5)) {
            vitoria(p);
        }
    }

    // ================================================================ ENTRADA POR MORTE
    /**
     * (5.36.0) <b>MORRER NO JUÍZO = CONTINUAR NO JUÍZO.</b>
     *
     * <p>Pedido do dono: <i>"se eu morrer no juízo eu não devia ficar no juízo?"</i>. Agora sim:
     * o respawn olha <b>a dimensão onde a pessoa morreu</b> (gravada no evento de morte) e
     * devolve ela para lá:
     * <ul>
     *   <li><b>labirinto</b> → centro do labirinto (a saída sorteada continua valendo);</li>
     *   <li><b>arena</b> → a porta da arena (de frente para o trono);</li>
     *   <li><b>reino do outro mod</b> → o ponto de chegada do reino;</li>
     *   <li>qualquer outro lugar → não mexe (o respawn normal do jogo decide).</li>
     * </ul>
     * Os conjuntos {@code NO_JUIZO}/{@code NA_SALA} continuam valendo como plano B, mas só
     * quando a pessoa está de fato dentro de uma das dimensões do Juízo.
     */
    public static void aoRenascer(ServerPlayer p) {
        String ondeMorreu = consumirMorte(p);
        String dim = ondeMorreu != null ? ondeMorreu
                : p.level().dimension().location().toString();

        if (dim.equals(RPTagMod.MODID + ":sala_julgamento")) {
            ServerLevel sala = p.getServer().getLevel(CHAVE_SALA);
            if (sala != null) {
                construirSala(sala);
                garantirGerador(sala);
                BlockPos ent = pontoEntrada != null ? pontoEntrada
                        : new BlockPos(0, SALA_Y + 2, ARENA_Z + JuizoSala.COMPRIMENTO - 12);
                p.teleportTo(sala, ent.getX() + 0.5D, ent.getY(), ent.getZ() + 0.5D, 180.0F, 0.0F);
                NA_SALA.add(p.getUUID());
                aviso(p, "\u00a74\u00a7lO JULGAMENTO\n\u00a77n\u00e3o devolve ningu\u00e9m", 4, 7);
                RPTagMod.LOGGER.info("RP Tag: {} renasceu DENTRO da arena do Julgamento",
                        p.getName().getString());
                return;
            }
        }
        if (dim.equals(RPTagMod.MODID + ":juizo")) {
            ServerLevel lv = p.getServer().getLevel(CHAVE_JUIZO);
            if (lv != null) {
                BlockPos centro = new BlockPos(0, PISO + 1, 0);
                construirEmVolta(lv, centro, 1);
                BlockPos saida = SAIDA.get(p.getUUID());
                if (saida == null) {
                    saida = new BlockPos(snapCelula(220), PISO + 1, snapCelula(-180));
                    SAIDA.put(p.getUUID(), saida);
                }
                NO_JUIZO.add(p.getUUID());
                NA_SALA.remove(p.getUUID());
                p.teleportTo(lv, centro.getX() + 0.5D, centro.getY() + 0.1D,
                        centro.getZ() + 0.5D, 0.0F, 0.0F);
                p.setHealth(p.getMaxHealth());
                p.getFoodData().setFoodLevel(20);
                // (5.36.0) aviso de TELA (o chat já é enxuto demais pra gastar linha com isso)
                aviso(p, "\u00a74\u00a7lO JULGAMENTO\n\u00a77n\u00e3o devolve ningu\u00e9m", 4, 7);
                RPTagMod.LOGGER.info("RP Tag: {} renasceu DENTRO do labirinto",
                        p.getName().getString());
                return;
            }
        }
        if (dim.equals(RPTagMod.MODID + ":juizo_reino")) {
            if (JuizoReino.entrar(p)) {
                RPTagMod.LOGGER.info("RP Tag: {} renasceu DENTRO do reino",
                        p.getName().getString());
                return;
            }
        }
        // morreu em qualquer outro lugar: o respawn do jogo decide, e a gente só limpa o estado
        if (!ehJuizo(p.level()) && !ehSala(p.level()) && !JuizoReino.eh(p.level())) {
            dadosDeVolta(p);
        }
    }
}
