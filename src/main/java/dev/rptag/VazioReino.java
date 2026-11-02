package dev.rptag;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * (5.13.0) O REINO DO VAZIO — tudo em volta do CENTRO, como o dono pediu.
 *
 * <h2>O pedido 50, palavra por palavra</h2>
 * <i>"ter fragmentos do vazio, ter pedestais em volta do centro aonde spawna na dimensão, e
 * assim que ele colocar os 8 fragmentos nos pedestais, abre um portal negro"</i>
 *
 * <p>Então a versão antiga (pedestais a 200–470 blocos e o portal a 780) <b>morreu</b>. Agora:
 *
 * <ol>
 *   <li><b>8 SANTUÁRIOS</b> espalhados entre 55 e 135 blocos da praça: altares quebrados, cada um
 *       com um {@code fragmento_vazio} em cima. Clique com o botão direito para <b>pegar o item de
 *       verdade</b> — ele entra no inventário, pode ser roubado, dropado e perdido.</li>
 *   <li><b>8 PEDESTAIS</b> num círculo de {@value VazioGeometria#RAIO_PEDESTAL} blocos em volta do
 *       ponto de spawn, no chão da praça. Com o fragmento na mão, clique direito: o fragmento é
 *       <b>encaixado</b> e o pedestal acende (deixa de ser sculk morto e vira catalisador).</li>
 *   <li><b>O PORTAL NEGRO</b> fica logo ao norte da praça (5 de largura por 7 de altura). Com os 8
 *       encaixados ele <b>abre de baixo para cima</b> (6 s, preenchendo o miolo linha por linha),
 *       exatamente como o protótipo {@code KfElheRr.java} do dono: moldura de obsidiana, vórtice
 *       vazio girando e brasas subindo. É só atravessar (ou clicar) para voltar.</li>
 *   <li>Quem atravessa volta para <b>o lugar de onde entrou</b> — gravado no save
 *       ({@link RPWorldData.Volta}), então nem restart perde.</li>
 * </ol>
 *
 * <p>O desenho do portal e das colunas de luz <b>não depende de partícula</b>: quem manda no visual
 * é geometria no cliente ({@code VazioPortalFx}), que continua aparecendo com partículas no
 * mínimo — pedido 50.
 */
public final class VazioReino {

    private VazioReino() {
    }

    // ------------------------------------------------------------------ números
    /** quantos fragmentos abrem o portal. */
    public static final int TOTAL_FRAGMENTOS = VazioGeometria.TOTAL;

    /** raio (blocos) da praça em volta do spawn. */
    public static final int RAIO_PRACA = 17;

    /** índice no banco de alturas: 0..7 pedestais, 8 portal, 9 praça, 10..17 santuários. */
    private static final int Y_PORTAL = 8, Y_PRACA = 9, Y_SANTUARIO = 10;

    /** fila de blocos a colocar — 400 por tick. */
    private static final ArrayDeque<Object[]> FILA = new ArrayDeque<>();
    private static final int POR_TICK = 400;

    /** construções já enfileiradas nesta sessão. */
    private static final Set<Long> FEITOS = new HashSet<>();
    private static final Set<Long> PREDIOS_FEITOS = new HashSet<>();

    /** jogadores caindo no vazio: uuid -> quando morre (ms). */
    private static final Map<UUID, Long> CAINDO = new HashMap<>();

    /** jogadores dentro do portal aberto: uuid -> desde quando (ms). */
    private static final Map<UUID, Long> NO_PORTAL = new HashMap<>();

    /**
     * (5.13.1) O RELÓGIO DA ABERTURA É POR PORTAL.
     *
     * <p>Antes era um contador só para o jogo inteiro: invocar um portal no overworld fazia o
     * portal do vazio "gastar" as 7 linhas da animação e o do overworld ficava sem miolo. A
     * chave é a dimensão + a posição da moldura.
     */
    private static final Map<String, Long> ABERTURA = new HashMap<>();
    private static final Map<String, Integer> LINHAS = new HashMap<>();

    /** chave do portal (dimensão + posição). */
    private static String chavePortal(ServerLevel nv, BlockPos base) {
        return dim(nv) + ":" + base.getX() + "," + base.getY() + "," + base.getZ();
    }

    private static boolean pracaFeita;
    private static long proximaDica;
    private static long proximoPredio;
    private static long proximoMundo;
    private static long proximaRuina;
    private static int contadorRuinas;

    /** último envio do estado do portal para cada jogador (ms). */
    private static final Map<UUID, Long> ULTIMO_SYNC = new HashMap<>();

    // =============================================================== níveis
    /**
     * A altura do CHÃO DA PRAÇA — e de tudo o que fica nela.
     *
     * <p>Guardar no save é o que impede o bug de "pedestal virando torre": sem isso o heightmap
     * subia a cada restart e a construção era empilhada em cima de si mesma.
     */
    public static int centroY(ServerLevel nv) {
        RPWorldData dados = RPWorldData.get(nv.getServer());
        int salvo = dados.getVazioY(Y_PRACA);
        if (salvo > nv.getMinBuildHeight() + 1) {
            return salvo;
        }
        nv.getChunk(0, 0);
        int y = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        if (y <= nv.getMinBuildHeight() + 2) {
            y = 12;
        }
        dados.setVazioY(Y_PRACA, y);
        dados.setVazioY(Y_PORTAL, y);
        for (int i = 0; i < TOTAL_FRAGMENTOS; i++) {
            dados.setVazioY(i, y);          // pedestais no MESMO nível da praça
        }
        return y;
    }

    /** base do pedestal {@code i} (no anel em volta da praça). */
    public static BlockPos ancoraPedestal(ServerLevel nv, int i) {
        int[] p = VazioGeometria.pedestal(i);
        return new BlockPos(p[0], centroY(nv), p[1]);
    }

    /** base (chão) do santuário {@code i}. */
    public static BlockPos ancoraSantuario(ServerLevel nv, int i) {
        int[] p = VazioGeometria.santuario(i);
        int chave = Y_SANTUARIO + i;
        RPWorldData dados = RPWorldData.get(nv.getServer());
        int salvo = dados.getVazioY(chave);
        if (salvo > nv.getMinBuildHeight() + 1) {
            return new BlockPos(p[0], salvo, p[1]);
        }
        nv.getChunk(p[0] >> 4, p[1] >> 4);
        int y = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p[0], p[1]);
        if (y <= nv.getMinBuildHeight() + 2) {
            return null;
        }
        dados.setVazioY(chave, y);
        return new BlockPos(p[0], y, p[1]);
    }

    /**
     * Onde o portal está: o invocado pelo {@code /vazio portal} (se houver) ou o da praça, a
     * {@value VazioGeometria#PORTAL}<code>[1]</code> blocos ao norte do centro.
     */
    public static BlockPos ancoraPortal(ServerLevel nv) {
        RPWorldData dados = RPWorldData.get(nv.getServer());
        // (5.13.1) cada dimensão pode ter o seu portal invocado (o dono invocou no
        // overworld e nada aparecia porque o código só olhava o do vazio).
        RPWorldData.Portal invocado = dados.getPortalInvocado(dim(nv));
        if (invocado != null) {
            return new BlockPos(invocado.x(), invocado.y(), invocado.z());
        }
        if (!ehVazio(nv)) {
            return null;                 // fora do vazio, sem invocação: não há portal
        }
        int[] p = VazioGeometria.PORTAL;
        return new BlockPos(p[0], centroY(nv), p[1]);
    }

    /** chave da dimensão (para guardar o portal invocado de cada uma). */
    private static String dim(ServerLevel nv) {
        return nv.dimension().location().toString();
    }

    /** (5.13.1) o portal desta dimensão foi INVOCADO por comando? (aí ele é livre) */
    public static boolean portalInvocado(ServerLevel nv) {
        return RPWorldData.get(nv.getServer()).getPortalInvocado(dim(nv)) != null;
    }

    /**
     * (5.13.1) O PORTAL ESTA ABERTO? Sim se os 8 fragmentos abriram o do vazio OU se ele foi
     * invocado por comando. Antes o {@code /vazio portal} do overworld montava a moldura e
     * deixava o miolo em AR num mundo sem os 8 pedestais ("invoquei o portal e ele nao
     * apareceu") — o portao invocado e de teste, entao ele abre sozinho.
     */
    private static boolean aberto(ServerLevel nv, RPWorldData dados) {
        return dados.isPortalAberto() || portalInvocado(nv);
    }

    // =============================================================== tick geral
    /** chamado do tick do exílio (20x por segundo). */
    public static void tick(ServerLevel nv, ServerPlayer p, long agora) {
        try {
            // 1) a praça e a fila andam SEMPRE (mesmo com o jogador parado)
            prepararPraca(nv);
            colocarFila(nv);

            // 2) perto da praça: pedestais, santuários e o portal existem
            double dCentro = Math.hypot(p.getX(), p.getZ());
            if (dCentro < 190) {
                garantirPedestais(nv, p);
                garantirPortais(nv);
            }
            if (dCentro < 150 || distanciaSantuario(nv, p) < 90) {
                garantirSantuarios(nv);
            }

            // 3) o portal: animação de abertura e o "atravessar"
            tickPortal(nv, p, agora);

            // 4) a dica da barra de ação
            if (agora >= proximaDica) {
                proximaDica = agora + 30_000L;
                dica(p, RPWorldData.get(p.getServer()));
            }

            // 5) PRÉDIOS PERDIDOS: nasce um a cada ~3 s, longe da praça
            if (agora >= proximoPredio) {
                proximoPredio = agora + 2200L + (long) (Math.random() * 1800);
                tentarPredio(nv, p);
            }

            // 6) o vazio que MATA devagar (pedido 49)
            tickCaindo(nv, p, agora);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: reino do vazio falhou (o jogo segue)", t);
        }
    }

    /**
     * (5.13.0) O REINO SEM NINGUÉM DENTRO — a dimensão se prepara sozinha.
     *
     * <p>Antes (5.12.0) a praça, os pedestais e o portal só nasciam quando um jogador entrava,
     * e quem chegava via o mundo se montando na frente dele. Agora, se a dimensão está rodando,
     * a praça e o anel de pedestais já estão de pé antes de qualquer um ser exilado.
     */
    public static void tickMundo(ServerLevel nv, long agora) {
        try {
            prepararPraca(nv);
            if (agora >= proximoMundo) {
                proximoMundo = agora + 5000L;
                garantirPedestais(nv, null);
                garantirPortais(nv);
                garantirUmSantuario(nv);
            }
            // (5.13.1) a cidade morta cresce sozinha: uma ruína a cada 3 s em volta da
            // praça (o dono: "a dimensão tem que ter mais coisas... prédios, ruínas")
            if (agora >= proximaRuina) {
                proximaRuina = agora + 3000L;
                ruinaAutomatica(nv);
            }
            animarAbertura(nv, agora);
            colocarFila(nv);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: reino do vazio (mundo) falhou", t);
        }
    }

    /** a mensagem da barra de ação — agora aponta para a PRAÇA, não para o leste. */
    private static void dica(ServerPlayer p, RPWorldData dados) {
        int cheios = dados.quantosPedestais(p.getUUID());
        if (dados.temOsOito(p.getUUID())) {
            p.displayClientMessage(Component.literal(
                    "\u00a75\u00a7lO PORTAL ESTÁ ABERTO. \u00a77Ele é logo ao \u00a7fNORTE\u00a77 da praça."), true);
            return;
        }
        int sant = Integer.bitCount(dados.getSantuarios(p.getUUID()));
        String linha = sant < TOTAL_FRAGMENTOS
                ? "\u00a78\u00a7oFragmento: \u00a7c" + sant + "/8\u00a78\u00a7o — procure as colunas de luz no breu."
                : "\u00a78\u00a7oVocê tem os 8. Encaixe cada um num pedestal em volta da praça.";
        p.displayClientMessage(Component.literal(
                "\u00a78\u00a7oPedestais: \u00a7d" + cheios + "/8\u00a78\u00a7o · " + linha), true);
    }

    // ================================================================ A PRAÇA
    /** o chão da praça: anel de blackstone, lajes no meio e o caminho até o portal. */
    private static void prepararPraca(ServerLevel nv) {
        if (pracaFeita) {
            return;
        }
        pracaFeita = true;
        try {
            int y = centroY(nv);
            final int r = RAIO_PRACA;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > r) {
                        continue;
                    }
                    BlockPos chao = new BlockPos(dx, y - 1, dz);
                    BlockState bloco;
                    if (d < 2.2) {
                        bloco = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
                    } else if (d > r - 1.6) {
                        bloco = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                    } else if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
                        bloco = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                    } else {
                        bloco = Blocks.DEEPSLATE_TILES.defaultBlockState();
                    }
                    if (d <= 4.02F) {
                        nv.setBlockAndUpdate(chao, bloco);      // núcleo: na hora
                    } else {
                        filaForte(chao, bloco);
                    }
                    for (int dy = 0; dy <= 7; dy++) {
                        BlockPos ar = chao.offset(0, 1 + dy, 0);
                        if (!nv.getBlockState(ar).isAir()) {
                            filaForte(ar, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
            // caminho de laje preta até o portal (a pessoa tem que ACHAR a saída) e o
            // terreno ABERTO em volta dele — o portal tem 9 de altura e não pode ficar
            // enterrado num morro
            int zPortal = VazioGeometria.PORTAL[1];
            for (int dz = -2; dz >= zPortal - 3; dz--) {
                int raioCaminho = (dz <= zPortal + 1) ? 5 : 2;
                for (int dx = -raioCaminho; dx <= raioCaminho; dx++) {
                    filaForte(new BlockPos(dx, y - 1, dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                    for (int dy = 0; dy <= 10; dy++) {
                        BlockPos ar = new BlockPos(dx, y + dy, dz);
                        if (!nv.getBlockState(ar).isAir()) {
                            filaForte(ar, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
            // (5.13.0) 8 colunas de obsidiana com lanterna de alma na BORDA da praça
            // (fora do anel dos pedestais, que é em r=13): a praça é o único lugar
            // iluminado do vazio — é por isso que ela é vista de longe.
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI * 2 / 8 + Math.PI / 8;
                int cxp = (int) Math.round(Math.cos(a) * (RAIO_PRACA - 2));
                int czp = (int) Math.round(Math.sin(a) * (RAIO_PRACA - 2));
                filaForte(new BlockPos(cxp, y - 1, czp), Blocks.OBSIDIAN.defaultBlockState());
                filaForte(new BlockPos(cxp, y, czp), Blocks.CRYING_OBSIDIAN.defaultBlockState());
                filaForte(new BlockPos(cxp, y + 1, czp), Blocks.OBSIDIAN.defaultBlockState());
                filaForte(new BlockPos(cxp, y + 2, czp), Blocks.SOUL_LANTERN.defaultBlockState());
            }
            RPTagMod.LOGGER.info("RP Tag: praça do vazio nivelada em y={}", y);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: praça do vazio falhou", t);
        }
    }

    /** (5.13.0) o ponto de chegada: o CENTRO da praça, no nível do chão. */
    public static net.minecraft.world.phys.Vec3 chegada(ServerLevel nv) {
        prepararPraca(nv);
        int y = centroY(nv);
        nv.getChunk(0, 0);
        nv.setBlockAndUpdate(new BlockPos(0, y - 1, 0), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
        for (int dy = 0; dy <= 3; dy++) {
            if (!nv.getBlockState(new BlockPos(0, y + dy, 0)).isAir()) {
                nv.setBlockAndUpdate(new BlockPos(0, y + dy, 0), Blocks.AIR.defaultBlockState());
            }
        }
        return new net.minecraft.world.phys.Vec3(0.5, y, 0.5);
    }

    // ================================================================ PEDESTAIS
    /** constrói (uma vez) os 8 pedestais em volta da praça. */
    private static void garantirPedestais(ServerLevel nv, ServerPlayer p) {
        for (int i = 0; i < TOTAL_FRAGMENTOS; i++) {
            if (FEITOS.contains(chave('P', i))) {
                continue;
            }
            FEITOS.add(chave('P', i));
            BlockPos base = ancoraPedestal(nv, i);
            nv.getChunk(base.getX() >> 4, base.getZ() >> 4);
            enfileirarPedestal(nv, i, base);
        }
    }

    /**
     * Um pedestal: base 3x3 de blackstone, coluna de obsidiana, e em cima a "tomada" de sculk (que
     * vira catalisador quando o fragmento é encaixado).
     */
    private static void enfileirarPedestal(ServerLevel nv, int i, BlockPos base) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                filaForte(base.offset(dx, -1, dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                if (dx == 0 && dz == 0) {
                    filaForte(base, Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
                    filaForte(base.offset(0, 1, 0), Blocks.OBSIDIAN.defaultBlockState());
                    // a tomada: enquanto vazia é sculk morto; cheia vira catalisador
                    boolean cheio = nv.getBlockState(base.offset(0, 2, 0)).is(Blocks.SCULK_CATALYST);
                    filaForte(base.offset(0, 2, 0), (cheio
                            ? Blocks.SCULK_CATALYST
                            : Blocks.SCULK).defaultBlockState());
                } else {
                    filaForte(base.offset(dx, 0, dz), Blocks.DEEPSLATE_TILES.defaultBlockState());
                }
            }
        }
        // 4 pontas de obsidiana chorando nas diagonais (a marca do anel)
        int[][] cantos = { { -2, -2 }, { 2, -2 }, { -2, 2 }, { 2, 2 } };
        for (int[] c : cantos) {
            filaForte(base.offset(c[0], -1, c[1]), Blocks.CRYING_OBSIDIAN.defaultBlockState());
        }
        RPTagMod.LOGGER.info("RP Tag: pedestal do vazio {} em {}", i + 1, base);
    }

    // =============================================================== SANTUÁRIOS
    /** constrói (uma vez) os 8 altares onde nasce o fragmento. */
    private static void garantirSantuarios(ServerLevel nv) {
        for (int i = 0; i < VazioGeometria.SANTUARIOS; i++) {
            if (FEITOS.contains(chave('S', i))) {
                continue;
            }
            FEITOS.add(chave('S', i));
            BlockPos base = ancoraSantuario(nv, i);
            if (base == null) {
                continue;
            }
            enfileirarSantuario(nv, base);
        }
    }

    /**
     * (5.13.0) um altar por vez, a cada 5 s, mesmo sem jogador dentro: assim quem é
     * exilado não assiste o mundo se montando na frente dele (e o custo é diluído).
     */
    private static int proximoSantuario;

    private static void garantirUmSantuario(ServerLevel nv) {
        for (int tentativa = 0; tentativa < VazioGeometria.SANTUARIOS; tentativa++) {
            int i = proximoSantuario % VazioGeometria.SANTUARIOS;
            proximoSantuario++;
            if (FEITOS.contains(chave('S', i))) {
                continue;
            }
            FEITOS.add(chave('S', i));
            BlockPos base = ancoraSantuario(nv, i);
            if (base == null) {
                continue;
            }
            enfileirarSantuario(nv, base);
            return;
        }
    }

    /** era o mais perto dos santuários (para só construir quando ele chega perto). */
    private static double distanciaSantuario(ServerLevel nv, ServerPlayer p) {
        double menor = Double.MAX_VALUE;
        for (int i = 0; i < VazioGeometria.SANTUARIOS; i++) {
            int[] s = VazioGeometria.santuario(i);
            menor = Math.min(menor, Math.hypot(p.getX() - s[0], p.getZ() - s[1]));
        }
        return menor;
    }

    private static void enfileirarSantuario(ServerLevel nv, BlockPos base) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 2.4) {
                    continue;
                }
                boolean meio = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                filaForte(base.offset(dx, -1, dz),
                        (meio ? Blocks.POLISHED_BLACKSTONE : Blocks.DEEPSLATE_BRICKS).defaultBlockState());
                if (meio && (dx != 0 || dz != 0)) {
                    filaForte(base.offset(dx, 0, dz), Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState());
                }
            }
        }
        // pilar quebrado + a tomada do fragmento (shrieker = tem fragmento)
        filaForte(base.offset(-1, 0, -1), Blocks.OBSIDIAN.defaultBlockState());
        filaForte(base.offset(-1, 1, -1), Blocks.CRYING_OBSIDIAN.defaultBlockState());
        filaForte(base.offset(0, 0, 0), Blocks.DEEPSLATE_TILES.defaultBlockState());
        filaForte(base.offset(0, 1, 0), Blocks.OBSIDIAN.defaultBlockState());
        filaForte(base.offset(0, 2, 0), Blocks.SCULK_SHRIEKER.defaultBlockState());
        filaForte(base.offset(1, 0, 1), Blocks.SOUL_LANTERN.defaultBlockState());
        nv.playSound(null, base, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.5F, 0.55F);
        RPTagMod.LOGGER.info("RP Tag: santuário do vazio em {}", base);
    }

    // ================================================================== PORTAL
    /** o portal nunca era construído se ninguém passasse perto: aqui ele nasce com a praça. */
    private static void garantirPortais(ServerLevel nv) {
        if (FEITOS.contains(chave('N', 0))) {
            return;
        }
        FEITOS.add(chave('N', 0));
        BlockPos base = ancoraPortal(nv);
        nv.getChunk(base.getX() >> 4, base.getZ() >> 4);
        enfileirarPortal(nv, base);
    }

    /**
     * A moldura do portal: 7 de largura por 9 de altura (miolo 5x7).
     *
     * <p>É a mesma proporção do protótipo do dono ({@code KfElheRr.java}: moldura de obsidiana em
     * volta de um vórtice vazio), só que em blocos. O miolo fica VAZIO até a abertura — quem
     * preenche é {@link #abrirPortal} linha por linha, de baixo para cima.
     */
    private static void enfileirarPortal(ServerLevel nv, BlockPos base) {
        int larg = VazioGeometria.LARGURA_MIOLO;              // 5
        int alt = VazioGeometria.ALTURA_MIOLO;                // 7
        int meia = larg / 2 + VazioGeometria.BORDA;           // 3
        for (int x = -meia; x <= meia; x++) {
            for (int y = -1; y <= alt; y++) {
                boolean borda = Math.abs(x) == meia || y == -1 || y == alt;
                BlockPos pos = base.offset(x, y, 0);
                if (borda) {
                    BlockState st = (y % 3 == 0)
                            ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                            : Blocks.OBSIDIAN.defaultBlockState();
                    if (y == alt) {
                        st = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                    }
                    filaForte(pos, st);
                } else {
                    // miolo: ar (fechado) — fica só o fundo preto atrás
                    filaForte(pos.offset(0, 0, 1), Blocks.BLACK_CONCRETE.defaultBlockState());
                    if (!nv.getBlockState(pos).isAir()) {
                        filaForte(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        // degraus na frente (a pessoa entra andando)
        for (int x = -meia; x <= meia; x++) {
            filaForte(base.offset(x, -2, 1), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        RPTagMod.LOGGER.info("RP Tag: portal do vazio construído em {}", base);
    }

    /** abre o portal de baixo para cima (chamado quando o 8º fragmento é encaixado). */
    public static void abrirPortal(ServerLevel nv, ServerPlayer p) {
        RPWorldData dados = RPWorldData.get(nv.getServer());
        if (dados.isPortalAberto()) {
            return;
        }
        dados.setPortalAberto(true);
        BlockPos base = ancoraPortal(nv);
        ABERTURA.put(chavePortal(nv, base), System.currentTimeMillis());
        LINHAS.put(chavePortal(nv, base), 0);
        nv.getChunk(base.getX() >> 4, base.getZ() >> 4);
        nv.playSound(null, base, SoundEvents.PORTAL_TRIGGER, SoundSource.AMBIENT, 1.2F, 0.5F);
        nv.playSound(null, base, SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 0.5F, 1.7F);
        if (p != null) {
            p.displayClientMessage(Component.literal(
                    "\u00a75\u00a7lOS OITO FRAGMENTOS ESTÃO NO LUGAR.\n"
                    + "\u00a77O portal está abrindo — de baixo para cima. \u00a7fNÃO olhe por muito tempo."), false);
        }
        for (ServerPlayer outro : nv.players()) {
            if (outro != p) {
                outro.displayClientMessage(Component.literal(
                        "\u00a75\u00a7lAlguma coisa abriu no norte da praça. \u00a77O ar ficou mais pesado."), true);
            }
        }
    }

    /** (5.13.0) fecha o portal e apaga o miolo (usado pelo /vazio fechar). */
    public static void fecharPortal(ServerLevel nv) {
        RPWorldData dados = RPWorldData.get(nv.getServer());
        dados.setPortalAberto(false);
        BlockPos base = ancoraPortal(nv);
        if (base == null) {
            return;
        }
        ABERTURA.remove(chavePortal(nv, base));
        LINHAS.remove(chavePortal(nv, base));
        if (!nv.isLoaded(base)) {
            return;
        }
        for (int y = 0; y < VazioGeometria.ALTURA_MIOLO; y++) {
            for (int x = -VazioGeometria.LARGURA_MIOLO / 2; x <= VazioGeometria.LARGURA_MIOLO / 2; x++) {
                nv.setBlock(base.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        nv.playSound(null, base, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.AMBIENT, 0.8F, 0.4F);
    }

    /**
     * (5.13.0) A ABERTURA DE BAIXO PARA CIMA — roda em {@link #tick} (com jogador) e em
     * {@link #tickMundo} (sem ninguém), então o /vazio abrir do admin também anima.
     */
    private static void animarAbertura(ServerLevel nv, long agora) {
        RPWorldData dados = RPWorldData.get(nv.getServer());
        if (!aberto(nv, dados)) {
            return;
        }
        BlockPos base = ancoraPortal(nv);
        if (base == null) {
            return;
        }
        String k = chavePortal(nv, base);
        ABERTURA.putIfAbsent(k, agora);          // continuou aberto depois de um restart
        LINHAS.putIfAbsent(k, 0);
        long inicio = ABERTURA.get(k);
        if (!nv.isLoaded(base)) {
            nv.getChunk(base.getX() >> 4, base.getZ() >> 4);
            if (!nv.isLoaded(base)) {
                return;
            }
        }
        int alt = VazioGeometria.ALTURA_MIOLO;
        float progresso = Math.min(1.0F,
                (agora - inicio) / (float) VazioGeometria.ABERTURA_MS);
        int devidas = Math.min(alt, (int) Math.floor(progresso * alt + 1e-4F));
        int colocadas = LINHAS.get(k);
        while (colocadas < devidas) {
            int y = colocadas;
            for (int x = -VazioGeometria.LARGURA_MIOLO / 2; x <= VazioGeometria.LARGURA_MIOLO / 2; x++) {
                nv.setBlock(base.offset(x, y, 0), Blocks.BLACK_CONCRETE.defaultBlockState(), 2);
            }
            nv.playSound(null, base.offset(0, y, 0), SoundEvents.SCULK_CATALYST_BLOOM,
                    SoundSource.AMBIENT, 0.55F, 0.6F + y * 0.06F);
            colocadas++;
            LINHAS.put(k, colocadas);
        }
    }

    /** a cada tick: preenche o miolo de baixo para cima e leva quem entrar. */
    private static void tickPortal(ServerLevel nv, ServerPlayer p, long agora) {
        RPWorldData dados = RPWorldData.get(nv.getServer());
        BlockPos base = ancoraPortal(nv);
        if (base == null) {
            return;
        }

        // --- animação de abertura (compartilhada com o tickMundo)
        animarAbertura(nv, agora);

        // --- atravessar andando (sem clique): 0,7 s dentro do miolo.
        //     (5.13.1) portal INVOCADO por comando é livre: não cobra fragmento.
        boolean livre = portalInvocado(nv);
        boolean dentro = (livre || dados.isPortalAberto()) && dentroDoPortal(p, base)
                && (livre || dados.temOsOito(p.getUUID()));
        if (!dentro) {
            NO_PORTAL.remove(p.getUUID());
        } else {
            long desde = NO_PORTAL.computeIfAbsent(p.getUUID(), k -> agora);
            if (agora - desde > 700L) {
                NO_PORTAL.remove(p.getUUID());
                usarPortal(p, nv, base, dados);
                return;
            }
        }

        // --- manda o estado do portal para o cliente desenhar (1x por segundo)
        long ultimo = ULTIMO_SYNC.getOrDefault(p.getUUID(), 0L);
        if (agora - ultimo > 1000L) {
            ULTIMO_SYNC.put(p.getUUID(), agora);
            envioEstado(nv, p, dados, base);
        }
    }

    /**
     * (5.13.1) O PORTAL FORA DO VAZIO, tick a tick.
     *
     * <p>Rodado do tick do servidor: para cada dimensão que tem um portal invocado, anima a
     * abertura (que também vale no overworld) e deixa quem entrar atravessar. Sem jogador por
     * perto, o custo é dois `if` — nada de varrer o mundo.
     */
    public static void tickPortaisInvocados(net.minecraft.server.MinecraftServer server, long agora) {
        try {
            RPWorldData dados = RPWorldData.get(server);
            if (dados.getPortaisInvocados().isEmpty()) {
                return;
            }
            for (Map.Entry<String, RPWorldData.Portal> en : dados.getPortaisInvocados().entrySet()) {
                var chave = net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(en.getKey()));
                ServerLevel nv = server.getLevel(chave);
                if (nv == null || ehVazio(nv)) {
                    continue;                     // o do vazio já é tratado no tick do reino
                }
                BlockPos base = ancoraPortal(nv);
                if (base == null) {
                    continue;
                }
                if (!nv.isLoaded(base)) {
                    continue;                     // ninguém por perto: nem carrega o chunk
                }
                animarAbertura(nv, agora);
                for (ServerPlayer p : nv.players()) {
                    try {
                        boolean dentro = (dados.isPortalAberto() || portalInvocado(nv))
                                && dentroDoPortal(p, base);
                        if (!dentro) {
                            NO_PORTAL.remove(p.getUUID());
                            continue;
                        }
                        long desde = NO_PORTAL.computeIfAbsent(p.getUUID(), k -> agora);
                        if (agora - desde > 700L) {
                            NO_PORTAL.remove(p.getUUID());
                            usarPortal(p, nv, base, dados);
                        }
                    } catch (Throwable t) {
                        RPTagMod.LOGGER.warn("RP Tag: portal invocado falhou para um jogador", t);
                    }
                }
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: tick dos portais invocados falhou", t);
        }
    }

    /** cálculo barato do estado do portal (0 fechado, 1 abrindo, 2 aberto). */
    public static int estadoPortal(ServerLevel nv) {
        RPWorldData dados = RPWorldData.get(nv.getServer());
        if (!aberto(nv, dados)) {
            return 0;
        }
        BlockPos base = ancoraPortal(nv);
        Long inicio = base == null ? null : ABERTURA.get(chavePortal(nv, base));
        if (inicio == null || inicio <= 0L) {
            return 2;
        }
        long dt = System.currentTimeMillis() - inicio;
        return dt >= VazioGeometria.ABERTURA_MS ? 2 : 1;
    }

    /** manda posição + estado para o cliente desenhar o vórtice e as colunas de luz. */
    public static void envioEstado(ServerLevel nv, ServerPlayer p, RPWorldData dados, BlockPos base) {
        try {
            PacketDistributor.sendToPlayer(p, new VazioPortalPayload(
                    base.getX(), base.getY(), base.getZ(),
                    dados.getPedestais(p.getUUID()),
                    Integer.bitCount(dados.getSantuarios(p.getUUID())),
                    estadoPortal(nv)));
        } catch (Throwable t) {
            // pacote é cosmético: se falhar, o jogo segue
        }
    }

    /** o jogador está dentro do miolo do portal? */
    private static boolean dentroDoPortal(ServerPlayer p, BlockPos base) {
        int meiaL = VazioGeometria.LARGURA_MIOLO / 2;
        return Math.abs(p.getX() - (base.getX() + 0.5)) <= meiaL + 0.4
                && Math.abs(p.getZ() - (base.getZ() + 0.5)) <= 1.6
                && p.getY() >= base.getY() - 1.2
                && p.getY() <= base.getY() + VazioGeometria.ALTURA_MIOLO - 0.5;
    }

    // ============================================================== INTERAÇÃO
    /**
     * Clique direito num pedestal, num santuário ou no portal.
     *
     * @return true se o clique era nosso (aí o jogo não abre/fecha nada).
     */
    public static boolean interagir(ServerPlayer p, BlockPos pos) {
        try {
            if (!(p.level() instanceof ServerLevel nv)) {
                return false;
            }
            // (5.13.1) fora do vazio o único clique nosso é no portal invocado
            boolean noVazio = ehVazio(nv);
            if (!noVazio && !portalInvocado(nv)) {
                return false;
            }
            RPWorldData dados = RPWorldData.get(p.getServer());
            // --- pedestal (anel em volta da praça)
            for (int i = 0; noVazio && i < TOTAL_FRAGMENTOS; i++) {
                BlockPos a = ancoraPedestal(nv, i);
                if (Math.hypot(pos.getX() - a.getX(), pos.getZ() - a.getZ()) <= 3.0
                        && pos.getY() >= a.getY() - 1 && pos.getY() <= a.getY() + 4) {
                    return usarPedestal(p, nv, a, i, dados);
                }
            }
            // --- santuário (onde o fragmento nasce)
            for (int i = 0; noVazio && i < VazioGeometria.SANTUARIOS; i++) {
                BlockPos a = ancoraSantuario(nv, i);
                if (a == null) {
                    continue;
                }
                if (Math.hypot(pos.getX() - a.getX(), pos.getZ() - a.getZ()) <= 3.0
                        && pos.getY() >= a.getY() - 1 && pos.getY() <= a.getY() + 4) {
                    return usarSantuario(p, nv, a, i, dados);
                }
            }
            // --- portal
            BlockPos portal = ancoraPortal(nv);
            if (portal != null
                    && Math.abs(pos.getX() - portal.getX()) <= VazioGeometria.LARGURA_MIOLO
                    && Math.abs(pos.getZ() - portal.getZ()) <= 2
                    && pos.getY() >= portal.getY() - 2
                    && pos.getY() <= portal.getY() + VazioGeometria.ALTURA_MIOLO) {
                return usarPortal(p, nv, portal, dados);
            }
            return false;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: interacao do vazio falhou", t);
            return false;
        }
    }

    /** clique no pedestal: encaixa o fragmento que está na mão. */
    private static boolean usarPedestal(ServerPlayer p, ServerLevel nv, BlockPos base,
            int i, RPWorldData dados) {
        UUID id = p.getUUID();
        if (dados.pedestalCheio(id, i)) {
            p.displayClientMessage(Component.literal(
                    "\u00a78Este pedestal já está cheio. \u00a77Faltam \u00a7f"
                    + (TOTAL_FRAGMENTOS - dados.quantosPedestais(id)) + "\u00a77."), true);
            return true;
        }
        // tem o fragmento na mão? (principal ou sekundária)
        ItemStack principal = p.getMainHandItem();
        ItemStack secundaria = p.getOffhandItem();
        boolean temItem = principal.is(RPTagMod.FRAGMENTO_VAZIO.get())
                || secundaria.is(RPTagMod.FRAGMENTO_VAZIO.get());
        // fallback: se ele esvaziou santuário e não colocou, o vazio "lembra" do fragmento
        int santuarios = Integer.bitCount(dados.getSantuarios(id));
        int colocados = dados.quantosPedestais(id);
        if (!temItem && santuarios <= colocados) {
            nv.playSound(null, base, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.AMBIENT, 0.6F, 0.5F);
            p.displayClientMessage(Component.literal(
                    "\u00a78O pedestal está vazio e você também.\n\u00a77Traga um \u00a7fFRAGMENTO DO VAZIO"
                    + "\u00a77 — eles ficam nos altares com coluna de luz."), true);
            return true;
        }
        if (temItem) {
            (principal.is(RPTagMod.FRAGMENTO_VAZIO.get()) ? principal : secundaria).shrink(1);
        }
        dados.encaixarNoPedestal(id, i);
        int cheios = dados.quantosPedestais(id);
        // acende o pedestal (bloco compartilhado: quem chega depois vê aceso)
        nv.setBlock(base.offset(0, 2, 0), Blocks.SCULK_CATALYST.defaultBlockState(), 2);
        nv.playSound(null, base, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.7F);
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 0.6F);
        nv.sendParticles(ParticleTypes.SOUL, base.getX() + 0.5, base.getY() + 2.5, base.getZ() + 0.5,
                40, 0.6, 0.8, 0.6, 0.06);
        nv.sendParticles(ParticleTypes.END_ROD, base.getX() + 0.5, base.getY() + 2.0, base.getZ() + 0.5,
                24, 0.4, 0.6, 0.4, 0.04);
        p.displayClientMessage(Component.literal(
                "\u00a75\u00a7lFRAGMENTO ENCAIXADO \u00a7f" + cheios + "\u00a77/\u00a7f" + TOTAL_FRAGMENTOS
                + (cheios >= TOTAL_FRAGMENTOS
                        ? "\u00a75 — O PORTAL ESTÁ ABRINDO."
                        : "\u00a78 — faltam " + (TOTAL_FRAGMENTOS - cheios) + ".")), true);
        if (cheios >= TOTAL_FRAGMENTOS) {
            abrirPortal(nv, p);
        }
        return true;
    }

    /** clique no altar: leva o fragmento (uma vez por santuário, por jogador). */
    private static boolean usarSantuario(ServerPlayer p, ServerLevel nv, BlockPos base,
            int i, RPWorldData dados) {
        UUID id = p.getUUID();
        if (dados.jaPegouSantuario(id, i)) {
            p.displayClientMessage(Component.literal(
                    "\u00a78Este altar já foi esvaziado por você."), true);
            return true;
        }
        dados.marcarSantuario(id, i);
        ItemStack item = new ItemStack(RPTagMod.FRAGMENTO_VAZIO.get());
        if (!p.getInventory().add(item)) {
            p.drop(item, false);
        }
        nv.setBlock(base.offset(0, 2, 0), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
        nv.playSound(null, base, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.55F);
        p.playNotifySound(SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 0.6F);
        nv.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, base.getX() + 0.5, base.getY() + 2.6,
                base.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.04);
        int sant = Integer.bitCount(dados.getSantuarios(id));
        p.displayClientMessage(Component.literal(
                "\u00a75\u00a7lFRAGMENTO DO VAZIO \u00a7r\u00a78(" + sant + "/8 recolhidos)\n"
                + "\u00a77Leve até um \u00a7fPEDESTAL\u00a77 em volta da praça e clique com ele na mão."), false);
        return true;
    }

    /** clique (ou 0,7 s parado) no portal: volta pro lugar de onde ele entrou. */
    private static boolean usarPortal(ServerPlayer p, ServerLevel nv, BlockPos base, RPWorldData dados) {
        boolean livre = portalInvocado(nv);
        if (!livre && !dados.isPortalAberto()) {
            nv.playSound(null, base, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.AMBIENT, 0.8F, 0.5F);
            p.displayClientMessage(Component.literal(
                    "\u00a78O portal está morto. \u00a77Encaixe os \u00a7f8 FRAGMENTOS\u00a77 nos "
                    + "pedestais em volta da praça (\u00a7f" + dados.quantosPedestais(p.getUUID())
                    + "/8\u00a77)."), false);
            return true;
        }
        if (!livre && !dados.temOsOito(p.getUUID())) {
            nv.playSound(null, base, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.9F, 0.5F);
            p.displayClientMessage(Component.literal(
                    "\u00a74O portal não te reconhece. \u00a77Você precisa \u00a7fENCAIXAR\u00a77 os 8 "
                    + "fragmentos nos pedestais (\u00a7f" + dados.quantosPedestais(p.getUUID())
                    + "/8\u00a77)."), false);
            return true;
        }

        // ---------------- (5.13.1) FORA DO VAZIO: o portal leva PARA o vazio
        if (!ehVazio(nv)) {
            nv.playSound(null, base, SoundEvents.PORTAL_TRAVEL, SoundSource.AMBIENT, 1.0F, 0.7F);
            p.displayClientMessage(Component.literal(
                    "\u00a75\u00a7lA PORTA SE ABRE PARA O OUTRO LADO.\n"
                    + "\u00a78Você vai voltar exatamente para aqui quando sair."), false);
            // entrarNoExilio já guarda o lugar de onde ele saiu (RPWorldData.Volta)
            NeptuneEventos.entrarNoExilio(p);
            return true;
        }

        // ---------------- NO VAZIO: devolve ao ponto de entrada
        dados.limparPedestais(p.getUUID());
        nv.playSound(null, base, SoundEvents.PORTAL_TRAVEL, SoundSource.AMBIENT, 1.0F, 0.7F);
        fecharPortal(nv);
        if (!livre) {
            for (int i = 0; i < TOTAL_FRAGMENTOS; i++) {
                nv.setBlock(ancoraPedestal(nv, i).offset(0, 2, 0), Blocks.SCULK.defaultBlockState(), 2);
            }
        }
        for (ServerPlayer outro : nv.players()) {
            outro.displayClientMessage(Component.literal(
                    "\u00a75\u00a7l" + p.getName().getString().toUpperCase()
                    + " \u00a77atravessou o portal. O vazio ficou mais vazio."), true);
        }
        NeptuneEventos.voltarPeloPortal(p);
        return true;
    }

    /**
     * (5.13.0) {@code /vazio portal}: INVOCA o portal bem na frente de quem chamou.
     *
     * <p>Pedido 50: <i>"preciso também de um comando para invocar o portal"</i>. Ele nasce de
     * frente para o jogador (até 12 blocos, no chão) e já abre — é ferramenta de teste/demonstração.
     */
    public static String invocarPortal(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel nv)) {
            return "\u00a7cSem dimensão? Isso não devia acontecer.";
        }
        var olhar = p.getLookAngle();
        int x = (int) Math.floor(p.getX() + olhar.x * 6.0);
        int z = (int) Math.floor(p.getZ() + olhar.z * 6.0);
        nv.getChunk(x >> 4, z >> 4);
        int y = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= nv.getMinBuildHeight() + 2) {
            y = (int) Math.max(nv.getMinBuildHeight() + 2, Math.floor(p.getY()));
        }
        return invocarPortalEm(nv, x, y, z, p);
    }

    /**
     * (5.13.1) ERGUE O PORTAL NEGRO EM QUALQUER DIMENSÃO, numa posição dada.
     *
     * <p>Pedido do dono: <i>"invoquei o portal no overworld e ele não apareceu"</i>. O portal
     * invocado por comando é uma <b>porta de admin</b>: sai pronta e <b>aberta</b> (sem exigir os
     * 8 fragmentos) e liga os dois lados —
     * <ul>
     *   <li>invocado <b>fora do vazio</b> → atravessar leva a pessoa <b>para o vazio</b> (e guarda
     *       o lugar de onde ela saiu, para voltar nele depois);</li>
     *   <li>invocado <b>no vazio</b> → atravessar <b>devolve ao lugar de onde entrou</b>.</li>
     * </ul>
     */
    public static String invocarPortalEm(ServerLevel nv, int x, int y, int z, ServerPlayer quem) {
        RPWorldData dados = RPWorldData.get(nv.getServer());
        dados.setPortalInvocado(dim(nv), x, y, z);
        if (ehVazio(nv)) {
            dados.setVazioY(Y_PORTAL, y);
        }
        // limpa o lugar onde a moldura vai ficar (não ergue dentro de uma árvore/morro)
        int meia = VazioGeometria.LARGURA_MIOLO / 2 + VazioGeometria.BORDA;
        for (int dx = -meia - 1; dx <= meia + 1; dx++) {
            for (int dy = -2; dy <= VazioGeometria.ALTURA_MIOLO + 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                    if (!nv.getBlockState(pos).isAir()) {
                        nv.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        // piso firme embaixo (no overworld pode ser areia, água ou ar)
        for (int dx = -meia; dx <= meia; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos chao = new BlockPos(x + dx, y - 2, z + dz);
                BlockState atual = nv.getBlockState(chao);
                if (atual.isAir() || !atual.getFluidState().isEmpty()) {
                    nv.setBlock(chao, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), 2);
                }
            }
        }
        FEITOS.remove(chave('N', 0));
        BlockPos base = new BlockPos(x, y, z);
        enfileirarPortal(nv, base);
        while (!FILA.isEmpty()) {
            colocarFila(nv);          // a moldura sai na hora (comando de admin)
        }
        // abre na marra: portal de comando é porta, não quebra-cabeça
        dados.setPortalAberto(true);
        String chaveNovo = chavePortal(nv, base);
        ABERTURA.put(chaveNovo, System.currentTimeMillis());
        LINHAS.put(chaveNovo, 0);
        animarAbertura(nv, System.currentTimeMillis());
        nv.playSound(null, base, SoundEvents.PORTAL_TRIGGER, SoundSource.AMBIENT, 1.0F, 0.6F);
        if (quem != null) {
            quem.displayClientMessage(Component.literal(
                    "\u00a75\u00a7lPORTAL ERGUIDO\u00a7r \u00a78em \u00a7f" + x + ", " + y + ", " + z
                    + "\n\u00a77Ele está \u00a7fABERTO\u00a77. Atravesse para ir "
                    + (ehVazio(nv) ? "de volta ao mundo." : "para o VAZIO.")), false);
        }
        RPTagMod.LOGGER.info("RP Tag: portal invocado em {} ({}, {}, {})", dim(nv), x, y, z);
        return "\u00a75Portal invocado em \u00a7f" + x + ", " + y + ", " + z + "\u00a75 ("
                + (ehVazio(nv) ? "vazio → mundo" : "mundo → vazio") + ").";
    }

    // ================================================== O VAZIO QUE MATA DEVAGAR
    /** quanto tempo (ms) o vazio segura a pessoa antes de matar. */
    private static final long ESPERA_MORTE = 12_000L;

    private static void tickCaindo(ServerLevel nv, ServerPlayer p, long agora) {
        Long fim = CAINDO.get(p.getUUID());
        if (fim == null) {
            if (p.getY() < -4.0) {
                long morre = agora + ESPERA_MORTE;
                CAINDO.put(p.getUUID(), morre);
                p.setNoGravity(true);
                p.playNotifySound(RPTagMod.SOM_VAZIO.get(), SoundSource.HOSTILE, 1.0F, 0.55F);
                PacketDistributor.sendToPlayer(p, new ScreenTextPayload(
                        p.getName().getString().toUpperCase(), 12, 23));
                p.displayClientMessage(Component.literal(
                        "\u00a74\u00a7lO VAZIO TE PEGOU.\u00a7r \u00a7cVocê tem 12 segundos. "
                        + "\u00a78Ninguém vem."), false);
            }
            return;
        }
        long restam = fim - agora;
        if (restam <= 0) {
            CAINDO.remove(p.getUUID());
            p.setNoGravity(false);
            p.hurt(p.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            if (p.isAlive()) {
                p.kill();
            }
            return;
        }
        if (p.getY() < -2.0) {
            p.teleportTo(p.getX(), -2.0, p.getZ());
        }
        p.setDeltaMovement(0, 0, 0);
        p.fallDistance = 0;
        long seg = (restam + 999L) / 1000L;
        if ((nv.getGameTime() % 10L) == 0L) {
            p.displayClientMessage(Component.literal(
                    "\u00a74\u00a7lO VAZIO TE CHAMA \u00a7c" + seg + "\u00a78s"), true);
            nv.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1.0, p.getZ(),
                    12, 0.7, 0.8, 0.7, 0.02);
            p.playNotifySound(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.AMBIENT,
                    0.4F, 1.5F - 0.05F * (12 - seg));
        }
    }

    /** este jogador está no tempo de espera do vazio que mata? */
    public static boolean estaCaindo(UUID id) {
        return CAINDO.containsKey(id);
    }

    /** limpa o estado de quem saiu do vazio (chamado ao voltar). */
    public static void limparCaindo(UUID id) {
        CAINDO.remove(id);
        NO_PORTAL.remove(id);
    }

    // =========================================================== utilidades
    /** dá os 8 fragmentos físicos + marca os santuários (teste do dono). */
    public static void darTudo(ServerPlayer p) {
        RPWorldData dados = RPWorldData.get(p.getServer());
        for (int i = 0; i < VazioGeometria.SANTUARIOS; i++) {
            dados.marcarSantuario(p.getUUID(), i);
        }
        for (int i = 0; i < TOTAL_FRAGMENTOS; i++) {
            ItemStack item = new ItemStack(RPTagMod.FRAGMENTO_VAZIO.get());
            if (!p.getInventory().add(item)) {
                p.drop(item, false);
            }
        }
    }

    /** reinicia o reino (some com as construções marcadas e refaz). */
    public static void esquecerConstrucoes() {
        FEITOS.clear();
        PREDIOS_FEITOS.clear();
        FILA.clear();
        pracaFeita = false;
    }

    /** @return true se o nível é a dimensão do vazio. */
    public static boolean ehVazio(Level level) {
        return level.dimension().equals(NeptuneEventos.CHAVE_EXILIO_PUBLICA);
    }

    /** lista dos 8 pedestais (usada pelo /vazio pedestais). */
    public static String listaPedestais() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TOTAL_FRAGMENTOS; i++) {
            int[] p = VazioGeometria.pedestal(i);
            if (i > 0) {
                sb.append("\u00a78 · ");
            }
            sb.append("\u00a7f").append(i + 1).append("\u00a78 (").append(p[0])
                    .append(", ").append(p[1]).append(")");
        }
        return sb.toString();
    }

    /** (debug) levanta um prédio perdido numa célula fixa (teste do dono). */
    public static String testePredio(ServerLevel nv, int celX, int celZ) {
        int x = celX * 96 + 48, z = celZ * 96 + 48;
        nv.getChunk(x >> 4, z >> 4);
        enfileirarPredio(nv, x, z, celX, celZ);
        int fila = FILA.size();
        while (!FILA.isEmpty()) {
            colocarFila(nv);
        }
        int topo = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return "predio em " + x + "," + z + " fila=" + fila + " topo=" + topo;
    }

    /** quantos blocos esperam na fila de construção. */
    public static int construindo() {
        return FILA.size();
    }

    /** força a construção de tudo em volta do jogador (debug). */
    public static int construirTudo(ServerLevel nv, ServerPlayer p) {
        esquecerConstrucoes();
        int antes = FILA.size();
        prepararPraca(nv);
        garantirPedestais(nv, p);
        garantirSantuarios(nv);
        garantirPortais(nv);
        while (!FILA.isEmpty()) {
            colocarFila(nv);
        }
        return FILA.size() - antes + construindo();
    }

    /** (debug) o que tem na fila (posição/estado do primeiro item). */
    public static String espiarFila(ServerLevel nv) {
        Object[] e = FILA.peek();
        if (e == null) {
            return "vazia";
        }
        BlockPos pos = (BlockPos) e[0];
        return pos + " -> " + e[1] + " | carregado=" + nv.isLoaded(pos)
                + " | atual=" + nv.getBlockState(pos);
    }

    /** (debug) força o andamento da fila. */
    public static void drenar(ServerLevel nv) {
        colocarFila(nv);
    }

    /** religa a gravidade de quem estava caindo (na saída do servidor). */
    public static void soltarCaindo(ServerPlayer p) {
        if (CAINDO.remove(p.getUUID()) != null) {
            p.setNoGravity(false);
        }
        NO_PORTAL.remove(p.getUUID());
    }

    /** varre a lista de quem caiu (limpeza de jogadores deslogados). */
    public static void limparOffline(net.minecraft.server.MinecraftServer server) {
        Iterator<UUID> it = CAINDO.keySet().iterator();
        while (it.hasNext()) {
            if (server.getPlayerList().getPlayer(it.next()) == null) {
                it.remove();
            }
        }
    }

    /** (5.13.0) chamado quando o mundo do vazio carrega: a praça volta a ser construída. */
    public static void acordar(ServerLevel nv) {
        esquecerConstrucoes();
        ABERTURA.clear();
        LINHAS.clear();
    }

    // ---------------------------------------------------------------- fila
    private static long chave(char tipo, int i) {
        return (long) tipo * 1000L + i;
    }

    private static void fila(BlockPos pos, BlockState st) {
        if (FILA.size() > 40_000) {
            return;
        }
        FILA.add(new Object[]{ pos, st, Boolean.FALSE });
    }

    /** variação da fila que PODE sobrescrever (chão da praça, ar, moldura do portal). */
    private static void filaForte(BlockPos pos, BlockState st) {
        if (FILA.size() > 40_000) {
            return;
        }
        FILA.add(new Object[]{ pos, st, Boolean.TRUE });
    }

    private static void colocarFila(ServerLevel nv) {
        int n = 0;
        while (!FILA.isEmpty() && n < POR_TICK) {
            Object[] e = FILA.poll();
            BlockPos pos = (BlockPos) e[0];
            BlockState st = (BlockState) e[1];
            boolean forte = Boolean.TRUE.equals(e[2]);
            // (5.13.0) a fila CARREGA o chunk: antes, com ninguém dentro da dimensão,
            // o bloco era descartado ("chunk não carregado") e a praça/altar nunca
            // aparecia. Agora a construção sempre chega ao mundo.
            if (!nv.isLoaded(pos)) {
                nv.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            }
            BlockState atual = nv.getBlockState(pos);
            if (!forte && !atual.isAir() && !atual.canBeReplaced()) {
                continue;                      // não estraga o que já existe
            }
            if (forte && atual == st) {
                continue;                      // já está assim
            }
            nv.setBlock(pos, st, 2);
            n++;
        }
    }

    // ===================================================== RUÍNAS APAGADAS (5.13.1)
    /**
     * CASA/PRÉDIO BAIXO EM RUÍNAS — a "ruína apagada" da cidade morta.
     *
     * <p>Pedido do dono: <i>"lembra que pedi para a dimensão ter mais coisas, builda uns prédios,
     * ruínas apagadas, coisas assim"</i>. Eram 22–63 blocos de torre e nada mais. Agora, além das
     * torres, nascem prédios de 4 a 11 andares de altura com:
     * <ul>
     *   <li>paredes furadas (janelas quebradas, buracos de tamanhos diferentes);</li>
     *   <li>telhado desabado (só um canto em pé);</li>
     *   <li>piso de entulho por dentro e uma <b>marca de fogo</b> (basalto/carvão) onde alguma
     *       coisa queimou.</li>
     * </ul>
     */
    private static void enfileirarRuinaBaixa(ServerLevel nv, int x, int z, int cx, int cz) {
        RandomSource r = RandomSource.create(cx * 51_331L + cz * 77_017L + 11L);
        int y0 = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y0 <= nv.getMinBuildHeight() + 2) {
            return;
        }
        int larg = 7 + r.nextInt(7);          // 7..13
        int prof = 7 + r.nextInt(7);
        int alt = 5 + r.nextInt(8);           // 5..12
        BlockState[] paleta = {
                Blocks.DEEPSLATE_TILES.defaultBlockState(),
                Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
                Blocks.BLACKSTONE.defaultBlockState(),
                Blocks.GRAY_CONCRETE.defaultBlockState(),
                Blocks.TUFF.defaultBlockState(),
                Blocks.POLISHED_DEEPSLATE.defaultBlockState()
        };
        BlockState queimado = Blocks.BASALT.defaultBlockState();
        BlockState carvao = Blocks.COAL_BLOCK.defaultBlockState();
        int x0 = x - larg / 2, z0 = z - prof / 2;

        // piso por dentro (entulho + chão queimado)
        for (int dx = 0; dx < larg; dx++) {
            for (int dz = 0; dz < prof; dz++) {
                boolean dentro = dx > 0 && dz > 0 && dx < larg - 1 && dz < prof - 1;
                if (!dentro) {
                    continue;
                }
                float sorte = r.nextFloat();
                if (sorte < 0.18F) {
                    fila(new BlockPos(x0 + dx, y0 - 1, z0 + dz), queimado);
                } else if (sorte < 0.30F) {
                    fila(new BlockPos(x0 + dx, y0 - 1, z0 + dz), carvao);
                }
            }
        }
        // paredes
        for (int y = 0; y < alt; y++) {
            float desmancha = 0.12F + 0.5F * (y / (float) alt);
            for (int dx = 0; dx < larg; dx++) {
                for (int dz = 0; dz < prof; dz++) {
                    boolean borda = dx == 0 || dz == 0 || dx == larg - 1 || dz == prof - 1;
                    if (!borda) {
                        continue;
                    }
                    if (r.nextFloat() < desmancha) {
                        continue;                        // buraco na parede
                    }
                    // janelas: faixa no meio da parede, quebradas
                    boolean janela = (y % 3 == 1) && !(dx == 0 && dz == 0)
                            && (r.nextFloat() < 0.55F);
                    if (janela) {
                        continue;
                    }
                    fila(new BlockPos(x0 + dx, y0 + y, z0 + dz), paleta[r.nextInt(paleta.length)]);
                }
            }
        }
        // telhado desabado: só um canto continua em pé
        int cantoX = r.nextBoolean() ? 0 : larg - 1;
        int cantoZ = r.nextBoolean() ? 0 : prof - 1;
        for (int dx = 0; dx < larg; dx++) {
            for (int dz = 0; dz < prof; dz++) {
                double distCanto = Math.hypot(dx - cantoX, dz - cantoZ);
                if (distCanto < Math.max(2.0, larg * 0.35) && r.nextFloat() < 0.85F) {
                    fila(new BlockPos(x0 + dx, y0 + alt, z0 + dz), paleta[r.nextInt(paleta.length)]);
                }
            }
        }
        // entulho em volta (pedaços que caíram das paredes)
        for (int i = 0; i < 24; i++) {
            int px = x0 + r.nextInt(larg + 6) - 3;
            int pz = z0 + r.nextInt(prof + 6) - 3;
            int y = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
            if (y <= nv.getMinBuildHeight() + 1) {
                continue;
            }
            fila(new BlockPos(px, y, pz), r.nextFloat() < 0.2F ? carvao
                    : paleta[r.nextInt(paleta.length)]);
        }
    }

    /**
     * QUARTEIRÃO MORTO — muros caídos, postes de luz apagados e entulho.
     *
     * <p>É o "recheio" entre os prédios: sem isso, o vazio eram torres soltas num descampado. O
     * quarteirão desenha duas paredes de muro com buracos, dois postes tortos e uma pilha de
     * escombros — a cidade que existia ali antes.
     */
    private static void enfileirarQuarteirao(ServerLevel nv, int x, int z, int cx, int cz) {
        RandomSource r = RandomSource.create(cx * 91_117L + cz * 41_903L + 29L);
        int y0 = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y0 <= nv.getMinBuildHeight() + 2) {
            return;
        }
        BlockState muro = Blocks.DEEPSLATE_TILES.defaultBlockState();
        BlockState muroQuebrado = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        BlockState poste = Blocks.POLISHED_BASALT.defaultBlockState();
        BlockState lampada = Blocks.LANTERN.defaultBlockState();
        int comp = 8 + r.nextInt(10);

        // dois muros em L, com buracos
        for (int i = 0; i < comp; i++) {
            int altura = 1 + r.nextInt(3);
            for (int y = 0; y < altura; y++) {
                if (r.nextFloat() < 0.15F) {
                    continue;
                }
                fila(new BlockPos(x + i, y0 + y, z), r.nextFloat() < 0.35F ? muroQuebrado : muro);
            }
            if (r.nextFloat() < 0.75F) {
                int altura2 = 1 + r.nextInt(3);
                for (int y = 0; y < altura2; y++) {
                    fila(new BlockPos(x, y0 + y, z + i), muro);
                }
            }
        }
        // dois postes tortos com lanterna (a luz da rua que não acende mais)
        for (int i = 0; i < 2; i++) {
            int px = x + r.nextInt(comp);
            int pz = z + r.nextInt(comp) - comp / 2;
            int altura = 3 + r.nextInt(3);
            for (int y = 0; y < altura; y++) {
                fila(new BlockPos(px, y0 + y, pz), poste);
            }
            if (r.nextFloat() < 0.5F) {
                fila(new BlockPos(px, y0 + altura, pz), lampada);
            }
        }
        // escombros
        for (int i = 0; i < 40; i++) {
            int px = x + r.nextInt(comp + 8) - 4;
            int pz = z + r.nextInt(comp + 8) - 4;
            int y = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
            if (y <= nv.getMinBuildHeight() + 1) {
                continue;
            }
            fila(new PosEscombro(px, y, pz).pos(),
                    r.nextFloat() < 0.3F ? muroQuebrado : muro);
        }
    }

    /** só um atalho de legibilidade (evita três new BlockPos aninhados acima). */
    private record PosEscombro(int x, int y, int z) {
        BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    // =============================================================== PRÉDIOS
    /**
     * (5.13.1) UMA RUÍNA AUTOMÁTICA — a cidade morta se constrói sem ninguém dentro.
     *
     * <p>Posições em <b>espiral de ouro</b> (ângulo 137,5° + raio crescente) entre 34 e 105
     * blocos da praça: não ficam enfileiradas, não se sobrepõem e cobrem o anel inteiro.
     * Cada célula de 96 blocos só recebe uma construção (a mesma trava do `tentarPredio`).
     */
    private static void ruinaAutomatica(ServerLevel nv) {
        for (int tentativa = 0; tentativa < 12; tentativa++) {
            int i = contadorRuinas++;
            double ang = i * 2.399963;                       // espiral de ouro
            double raio = 34 + (i * 13 % 71);                // 34..104
            int x = (int) Math.round(Math.cos(ang) * raio);
            int z = (int) Math.round(Math.sin(ang) * raio);
            int cx = Math.floorDiv(x, 96);
            int cz = Math.floorDiv(z, 96);
            long k = (long) cx * 100_003L + cz;
            if (PREDIOS_FEITOS.contains(k)) {
                continue;
            }
            if (Math.hypot(x, z) < RAIO_PRACA + 8) {
                continue;
            }
            PREDIOS_FEITOS.add(k);
            nv.getChunk(x >> 4, z >> 4);
            int tipo = Math.floorMod(i * 37, 10);
            if (tipo < 2) {
                enfileirarPredio(nv, x, z, cx, cz);
            } else if (tipo < 7) {
                enfileirarRuinaBaixa(nv, x, z, cx, cz);
            } else {
                enfileirarQuarteirao(nv, x, z, cx, cz);
            }
            return;
        }
    }

    /** ruínas de arranha-céus no breu — o "vazio mais escuro" com cara de cidade morta. */
    private static void tentarPredio(ServerLevel nv, ServerPlayer p) {
        int celX = (int) Math.floor(p.getX() / 96.0);
        int celZ = (int) Math.floor(p.getZ() / 96.0);
        RandomSource r = RandomSource.create(celX * 34187L + celZ * 132897L + 17L);
        for (int tentativa = 0; tentativa < 6; tentativa++) {
            int ox = r.nextInt(5) - 2, oz = r.nextInt(5) - 2;
            int cx = celX + ox, cz = celZ + oz;
            long k = (long) cx * 100_003L + cz;
            if (PREDIOS_FEITOS.contains(k)) {
                continue;
            }
            int x = cx * 96 + 48, z = cz * 96 + 48;
            double d = Math.hypot(x - p.getX(), z - p.getZ());
            // (5.13.1) MAIS PERTO: 30–130 blocos. Antes era 55–190 e, com a névoa
            // fechando em 96, o dono andava e não via ruína nenhuma.
            if (d < 30 || d > 130) {
                continue;
            }
            if (Math.hypot(x, z) < RAIO_PRACA + 12) {
                continue;                       // a praça fica limpa
            }
            nv.getChunk(x >> 4, z >> 4);
            int topo = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int viz = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + 24, z);
            PREDIOS_FEITOS.add(k);
            if (topo - viz > 8) {
                continue;
            }
            // três tipos de construção: torre quebrada, casa apagada e quarteirão
            int tipo = r.nextInt(10);
            if (tipo < 3) {
                enfileirarPredio(nv, x, z, cx, cz);           // arranha-céu
            } else if (tipo < 8) {
                enfileirarRuinaBaixa(nv, x, z, cx, cz);       // casa/prédio baixo
            } else {
                enfileirarQuarteirao(nv, x, z, cx, cz);       // muros, postes, entulho
            }
            return;
        }
    }

    /** ruína de arranha-céu: casca com buracos, andares quebrados e entulho. */
    private static void enfileirarPredio(ServerLevel nv, int x, int z, int cx, int cz) {
        RandomSource r = RandomSource.create(cx * 71_771L + cz * 91_213L + 3L);
        int y0 = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y0 <= nv.getMinBuildHeight() + 2) {
            return;
        }
        int larg = 9 + r.nextInt(9);
        int prof = 9 + r.nextInt(9);
        int alt = 22 + r.nextInt(42);
        BlockState[] paleta = {
                Blocks.DEEPSLATE.defaultBlockState(),
                Blocks.BLACKSTONE.defaultBlockState(),
                Blocks.TUFF.defaultBlockState(),
                Blocks.GRAY_CONCRETE.defaultBlockState(),
                Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(),
                Blocks.DEEPSLATE_TILES.defaultBlockState()
        };
        int x0 = x - larg / 2, z0 = z - prof / 2;
        int andarH = 6 + r.nextInt(4);
        for (int y = 0; y < alt; y++) {
            float desmancha = 0.15F + 0.55F * (y / (float) alt);
            boolean casca = (y % andarH == 0);
            for (int dx = 0; dx < larg; dx++) {
                for (int dz = 0; dz < prof; dz++) {
                    boolean borda = dx == 0 || dz == 0 || dx == larg - 1 || dz == prof - 1;
                    if (borda) {
                        if (r.nextFloat() < desmancha || r.nextFloat() < 0.06F) {
                            continue;
                        }
                        fila(new BlockPos(x0 + dx, y0 + y, z0 + dz), paleta[r.nextInt(paleta.length)]);
                    } else if (casca && r.nextFloat() < 0.55F) {
                        fila(new BlockPos(x0 + dx, y0 + y, z0 + dz), paleta[r.nextInt(paleta.length)]);
                    }
                }
            }
        }
        for (int i = 0; i < 70; i++) {
            int px = x0 + r.nextInt(larg + 10) - 5;
            int pz = z0 + r.nextInt(prof + 10) - 5;
            int y = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
            if (y <= nv.getMinBuildHeight() + 1) {
                continue;
            }
            fila(new BlockPos(px, y, pz), paleta[r.nextInt(paleta.length)]);
        }
    }
}
