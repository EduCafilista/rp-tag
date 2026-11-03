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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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

    /**
     * (5.13.4) "JÁ FOI CONSTRUÍDO?" — agora mora no SAVE do mundo, não na memória.
     *
     * <p>Pedido 53: <i>"toda vez que entro novamente no void spawna de novo a estrutura,
     * as lanternas ficam quebrando — corrija para só spawnar uma vez"</i>. Estes dois
     * helpers são a única porta para as marcas (ver {@link RPWorldData#marcarConstrucao}).
     */
    private static boolean marcado(ServerLevel nv, String k) {
        return RPWorldData.get(nv.getServer()).temConstrucao(k);
    }

    /** @return true se marcou AGORA (ou seja: ainda não existia, pode construir). */
    private static boolean marcar(ServerLevel nv, String k) {
        return RPWorldData.get(nv.getServer()).marcarConstrucao(k);
    }

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

    private static long proximaDica;
    private static long proximoPredio;
    private static long proximoMundo;
    private static long proximaRuina;
    /** (5.13.3) quando o miolo do portal é conferido de novo (reparo automático). */
    private static long proximoReparo;

    // --------------------------------------------------------- RELEVO (5.13.4)
    /** fila de chunks que ainda vão receber relevo (x,z em coordenadas de chunk). */
    private static final ArrayDeque<int[]> RELEVO = new ArrayDeque<>();
    private static long proximoRelevo;
    /** (5.13.4) quando os bichos do vazio são varridos de novo. */
    private static long proximoLimpeza;
    /** onde já existe construção (o relevo não passa por cima). */
    private static final java.util.List<int[]> CASAS = new java.util.ArrayList<>();
    private static int contadorRuinas;
    /** (5.13.4) quando o relevo é puxado do mapa (uma vez). */
    private static boolean relevoMapaFeito;
    /** quantos blocos o relevo já assentou nesta vida do servidor (só para o log). */
    private static int relevoBlocos;

    // ============================================== ALMAS VAZIAS (5.13.6)
    /**
     * (5.13.6) AS ALMAS VAZIAS — pedido do dono: <i>"faça almas vazias que ficaram perdidas
     * naquele local"</i>.
     *
     * <p>Elas <b>não são bicho</b> (a primeira tentativa usou suporte de armadura invisível e o
     * servidor duplicava as almas quando o chunk descarregava). O servidor guarda só a posição e
     * calcula o movimento; o <b>corpo</b> é desenhado pelo cliente em geometria
     * ({@code VazioAlmasFx}) — pelo mesmo motivo do portal: partícula desligada não pode sumir
     * com o bicho.
     *
     * <p>Elas ficam <b>perdidas</b>: vagam devagar em volta de onde morreram e, quando alguém
     * chega perto, <b>se viram para a pessoa e se aproximam</b> — devagar, sem tocar, sussurrando.
     * Nada de dano: alma vazia só assusta.
     */
    /** posição ATUAL de cada alma (x, y, z). */
    private static final float[] ALMAS_ESTADO = new float[12 * 3];
    private static boolean almasAcordadas;

    /** onde cada alma ficou perdida (x, z) — perto da praça e nos ermos, nas montanhas. */
    private static final int[][] ALMAS_CASAS = {
            { 26, -31 }, { -33, 19 }, { 14, 43 }, { -19, -46 },
            { 63, 66 }, { -78, 37 }, { 99, -72 }, { -44, -101 }, { 124, 86 }, { -113, -24 },
            { 41, -104 }, { -96, 96 }
    };

    /** última vez que cada alma sussurrou (para o sussurro não virar metralhadora). */
    private static final long[] ALMA_FALOU = new long[12];
    private static long proximaAlma;

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

    /**
     * (5.13.3) A BASE DO PORTAL DA PRAÇA — ignora portal invocado.
     *
     * <p>Bug do dono (pedido 52): <i>"mesmo ativando os 8 pedestais não abriu o portal
     * animado"</i>. O {@link #ancoraPortal} devolve o portal INVOCADO quando existe um
     * (de qualquer `/vazio portal` antigo feito no vazio); então os 8 fragmentos abriam
     * um portal que não era o da praça — ou nenhum, se o invocado tinha sido desmontado
     * e o registro ficou para trás. Agora o caminho dos 8 fragmentos é sempre o portal
     * da praça.
     */
    public static BlockPos ancoraPortalPraca(ServerLevel nv) {
        if (!ehVazio(nv)) {
            return null;
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

            // 4) (5.13.4) SEM dica automática na barra de ação.
            // Pedido 53: "tira esses avisos quando entrar na dimensão da tela do
            // player". Nada de "Pedestais 3/8" pipocando na tela — quem quiser o
            // progresso pergunta com /vazio pedestais.

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
            // (5.13.2) AO CHEGAR já tem cidade: 14 ruínas num anel de 26 a 78 blocos,
            // uma vez por sessão. Antes a primeira ruína automática caía a 34+ blocos,
            // e com a névoa fechada o dono entrava e não via NADA ("a dimensão não tem
            // nada").
            // (5.13.4) só uma vez NA VIDA DO MUNDO (a marca está no save)
            if (marcar(nv, "anel")) {
                anelInicial(nv);
            }
            relevoTick(nv, agora);
            // (5.13.6) as almas vazias (são virtuais: posição + desenho no cliente)
            tickAlmas(nv, agora);
            // (5.13.1) a cidade morta cresce sozinha: uma ruína a cada 2 s em volta da
            // praça (o dono: "a dimensão tem que ter mais coisas... prédios, ruínas")
            if (agora >= proximaRuina) {
                proximaRuina = agora + 2000L;
                ruinaAutomatica(nv);
            }
            animarAbertura(nv, agora);
            // (5.13.4) NADA DE SLIME (e nada de bicho que não seja PRETO)
            if (agora >= proximoLimpeza) {
                proximoLimpeza = agora + 4000L;
                limparBichos(nv);
            }
            // (5.13.3) o miolo aberto tem que CONTINUAR aberto: se um chunk foi
            // descarregado no meio da animação (ou alguém quebrou o bloco), ele
            // repara sozinho — sem depender de ninguém clicar em nada.
            if (agora >= proximoReparo) {
                proximoReparo = agora + 5000L;
                repararMiolo(nv);
            }
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
        // (5.13.4) constrói UMA VEZ na vida do mundo (a marca vai pro save)
        if (!marcar(nv, "praca")) {
            return;
        }
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

    // ======================================================= BICHOS (5.13.4)
    /**
     * (5.13.4) O VAZIO NÃO CRIA SLIME — pedido 53.
     *
     * <p><i>"não deixe spawnar slimes, se for para spawnar um bicho completamente
     * preto"</i>. Então: nenhum slime (nem os que já existiam: a varredura remove), e
     * dos bichos só fica o que é PRETO de verdade — o <b>Enderman</b> e o <b>Warden</b>.
     * Todo o resto é descartado a cada 4 s (o spawn natural já é cancelado em
     * {@code ServerEvents}).
     */
    private static void limparBichos(ServerLevel nv) {
        try {
            int removidos = 0;
            for (net.minecraft.world.entity.Entity e : nv.getAllEntities()) {
                if (e instanceof ServerPlayer || e instanceof net.minecraft.world.entity.item.ItemEntity
                        || e instanceof net.minecraft.world.entity.decoration.ArmorStand
                        || e instanceof net.minecraft.world.entity.decoration.ItemFrame) {
                    continue;
                }
                if (!(e instanceof net.minecraft.world.entity.Mob)) {
                    continue;
                }
                boolean preto = e.getType() == net.minecraft.world.entity.EntityType.ENDERMAN
                        || e.getType() == net.minecraft.world.entity.EntityType.WARDEN;
                if (preto) {
                    continue;
                }
                e.discard();
                removidos++;
            }
            if (removidos > 0) {
                RPTagMod.LOGGER.info("RP Tag: vazio limpou {} bicho(s) que nao sao pretos", removidos);
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: limpeza de bichos do vazio falhou", t);
        }
    }

    // ============================================================ BAÚS (5.13.4)
    /**
     * (5.13.4) O BAÚ DA RUÍNA — pedido 53: <i>"faça baús dentro da dimensão com raridade
     * para os players encontrarem os fragmentos do void"</i>.
     *
     * <p>Cada construção erguida pela cidade morta tem chance de ganhar um baú. O sorteio
     * é por RARIDADE (os pesos estão na tabela abaixo): o comum é sucata que qualquer
     * ruína teria; o <b>raro</b> é um FRAGMENTO DO VAZIO — a alternativa de quem não quer
     * caçar os 8 altares; o <b>mítico</b> é o fragmento + um tesouro de verdade.
     *
     * <p>Os itens são escritos direto no baú (nada de loot table): assim não há risco de
     * o arquivo de loot faltar no jar e o baú abrir vazio.
     */
    private static void colocarBau(ServerLevel nv, int x, int y, int z, RandomSource r) {
        try {
            if (y <= nv.getMinBuildHeight() + 1) {
                return;
            }
            BlockPos pos = new BlockPos(x, y, z);
            BlockState atual = nv.getBlockState(pos);
            if (!atual.isAir() && !atual.canBeReplaced()) {
                return;                              // não estraga o que já existe
            }
            // encosta o baú na parede mais próxima (visual de ruína saqueada)
            var facing = net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(r);
            nv.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState()
                    .setValue(ChestBlock.FACING, facing));
            if (!(nv.getBlockEntity(pos) instanceof ChestBlockEntity bau)) {
                return;
            }
            // ---- RARIDADE (0..99) ----
            int sorte = r.nextInt(100);
            boolean raro = sorte >= 82 && sorte < 97;     // 15%: 1 fragmento
            boolean mitico = sorte >= 97;                 //  3%: 2 fragmentos + tesouro
            int comuns = 2 + r.nextInt(3);
            for (int i = 0; i < comuns; i++) {
                bau.setItem(r.nextInt(27), sucata(r));
            }
            if (raro || mitico) {
                bau.setItem(r.nextInt(27),
                        new ItemStack(RPTagMod.FRAGMENTO_VAZIO.get(), mitico ? 2 : 1));
            }
            if (sorte >= 55) {                            // 45%: um item "bom"
                bau.setItem(r.nextInt(27), bomItem(r));
            }
            if (mitico) {
                bau.setItem(r.nextInt(27), new ItemStack(Items.DIAMOND, 2 + r.nextInt(3)));
                bau.setItem(r.nextInt(27), new ItemStack(Items.ECHO_SHARD, 1 + r.nextInt(2)));
            }
        } catch (Throwable t) {
            // baú é enfeite/tesouro: se falhar, a ruína continua de pé
        }
    }

    /** o que se acha em qualquer ruína do vazio (sucata). */
    private static ItemStack sucata(RandomSource r) {
        return switch (r.nextInt(10)) {
            case 0 -> new ItemStack(Items.BONE, 1 + r.nextInt(4));
            case 1 -> new ItemStack(Items.ROTTEN_FLESH, 1 + r.nextInt(3));
            case 2 -> new ItemStack(Items.COAL, 1 + r.nextInt(6));
            case 3 -> new ItemStack(Items.TUFF, 2 + r.nextInt(8));
            case 4 -> new ItemStack(Items.GRAVEL, 2 + r.nextInt(8));
            case 5 -> new ItemStack(Items.DEEPSLATE, 2 + r.nextInt(6));
            case 6 -> new ItemStack(Items.IRON_NUGGET, 1 + r.nextInt(5));
            case 7 -> new ItemStack(Items.STRING, 1 + r.nextInt(3));
            case 8 -> new ItemStack(Items.SCULK_VEIN, 1 + r.nextInt(3));
            default -> new ItemStack(Items.ROTTEN_FLESH, 2);
        };
    }

    /** o "bom" (a parte que faz valer a pena entrar na ruína). */
    private static ItemStack bomItem(RandomSource r) {
        return switch (r.nextInt(9)) {
            case 0 -> new ItemStack(Items.IRON_INGOT, 1 + r.nextInt(4));
            case 1 -> new ItemStack(Items.GOLD_INGOT, 1 + r.nextInt(3));
            case 2 -> new ItemStack(Items.SOUL_TORCH, 2 + r.nextInt(4));
            case 3 -> new ItemStack(Items.SOUL_LANTERN, 1);
            case 4 -> new ItemStack(Items.ECHO_SHARD, 1);
            case 5 -> new ItemStack(Items.SCULK_CATALYST, 1);
            case 6 -> new ItemStack(Items.AMETHYST_SHARD, 2 + r.nextInt(4));
            case 7 -> new ItemStack(Items.EXPERIENCE_BOTTLE, 1 + r.nextInt(3));
            default -> new ItemStack(Items.OBSIDIAN, 2 + r.nextInt(4));
        };
    }

    // ================================================================ RELEVO
    /**
     * (5.13.4) RELEVOS NA DIMENSÃO — pedido 53: <i>"faça relevos na dimensão, quero
     * prédios caídos, ruínas, coisas que evidencie o vazio"</i>.
     *
     * <p>O chão do vazio era uma laje infinita. Agora, num anel de 45 a 130 blocos em
     * volta da praça, o terreno sobe em lombo e desce em cratera (valor-noise suave,
     * determinístico): montes de deepslate/tuff/cascalho, valas, pedras soltas. É feito
     * <b>uma vez por chunk</b> ({@code R:cx,cz} no save), 2 chunks por tick, e nunca por
     * cima de construção — só mexe em bloco NATURAL.
     */
    private static void relevoTick(ServerLevel nv, long agora) {
        try {
            if (!relevoMapaFeito) {
                relevoMapaFeito = true;
                montarMapaRelevo();
            }
            if (agora < proximoRelevo) {
                return;
            }
            proximoRelevo = agora + 100L;             // ~2 chunks/tick, diluído
            int feitos = 0;
            while (!RELEVO.isEmpty() && feitos < 2) {
                int[] c = RELEVO.poll();
                int cx = c[0], cz = c[1];
                if (!marcar(nv, "R:" + cx + "," + cz)) {
                    continue;
                }
                // (5.13.4) o chunk TEM de existir antes de esculpir: getHeight num chunk
                // que nunca foi gerado devolvia o chão do vazio errado e o relevo sumia.
                nv.getChunk(cx, cz);
                esculpirChunk(nv, cx, cz);
                // (5.13.4) e a fila é despejada NA HORA: ela tem teto de 40.000 blocos e,
                // com o relevo despejando ~1.400 por tick contra 400 drenados, o que
                // passava do teto era JOGADO FORA (metade da dimensão ficava chapada).
                despejarFila(nv);
                feitos++;
            }
            if (RELEVO.isEmpty() && relevoBlocos > 0) {
                RPTagMod.LOGGER.info("RP Tag: relevo do vazio pronto ({} blocos assentados)",
                        relevoBlocos);
                relevoBlocos = 0;
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: relevo do vazio falhou (o jogo segue)", t);
        }
    }

    /** monta a fila de chunks do relevo (anel de 45 a 130 blocos em volta da praça). */
    private static void montarMapaRelevo() {
        RELEVO.clear();
        int min = 45, max = 130;
        for (int cx = -(max >> 4) - 1; cx <= (max >> 4) + 1; cx++) {
            for (int cz = -(max >> 4) - 1; cz <= (max >> 4) + 1; cz++) {
                double d = Math.hypot(cx * 16 + 8, cz * 16 + 8);
                if (d < min || d > max) {
                    continue;
                }
                RELEVO.add(new int[]{ cx, cz });
            }
        }
        RPTagMod.LOGGER.info("RP Tag: relevo do vazio agendado ({} chunks)", RELEVO.size());
    }

    /**
     * (5.13.6) A ALTURA DO RELEVO — agora com MONTANHAS de verdade.
     *
     * <p>O dono: <i>"coloca uns relevos na dimensão, umas montanhas, coisas assim"</i>. Antes
     * existia só a ondulação (no máximo ±8 blocos: parecia chapa amassada). Agora são duas
     * coisas somadas:
     * <ol>
     *   <li>a <b>ondulação</b> de sempre (vales, montinhos, crateras);</li>
     *   <li>uma <b>cordilheira</b> de serras de até ~34 blocos, que só nasce <b>depois de 62
     *       blocos da praça</b> (praça e caminho do portal intactos) e cresce suave até 102 —
     *       nada de paredão na cara de quem chega;</li>
     *   <li>o <b>cinto de serras</b> a ~118 blocos: é o horizonte do vazio.</li>
     * </ol>
     *
     * <p>É tudo seno de posição: o mesmo mundo gera sempre a mesma montanha, sem guardar
     * altura nenhuma no save.
     */
    private static double relevoAltura(int x, int z) {
        // 1) a ondulação (a de sempre)
        double a = Math.sin(x * 0.041 + 1.7) * Math.cos(z * 0.037 - 0.4);
        double b = Math.sin((x + z) * 0.017 - 2.1) * Math.cos((x - z) * 0.023 + 0.9);
        double c = Math.sin(x * 0.101 - z * 0.087 + 3.3);
        double base = (a * 3.4 + b * 2.6 + c * 1.1) * 1.15;

        // 2) as MONTANHAS (só longe da praça)
        double d = Math.hypot(x, z);
        double longe = Math.min(1.0, Math.max(0.0, (d - 62.0) / 40.0));
        if (longe <= 0.0) {
            return base;
        }
        // (5.13.6) RIDGED NOISE: "1 - |seno|" dá CRISTA (quina) em vez de lomba redonda —
        // é o que separa serra de monte de areia. Três escalas: a serra larga (~270
        // blocos), o contraforte (~100) e a agulha (~40, que dá relevo dentro do chunk).
        double serra = Math.sin((x + z * 0.35) * 0.0062 + 0.4);            // a cordilheira
        double largo = 0.5 + 0.5 * serra;
        double r1 = 1.0 - Math.abs(Math.sin(x * 0.023 + 0.7) * Math.cos(z * 0.019 - 0.4));
        double r2 = 1.0 - Math.abs(Math.sin(x * 0.061 - z * 0.052 + 1.3));
        double r3 = 1.0 - Math.abs(Math.sin(x * 0.147 + z * 0.131 + 2.1));
        double pico = largo * (r1 * 0.58 + r2 * 0.28 + r3 * 0.14);
        pico = Math.pow(pico, 1.8);                                        // afina as pontas
        double altura = Math.min(40.0, pico * 46.0);
        // 3) o cinturão de serras no horizonte — o "fim do mundo" do vazio
        double cinto = Math.max(0.0, 1.0 - Math.abs(d - 118.0) / 26.0);
        altura = Math.max(altura, cinto * (6.0 + 10.0 * largo));
        return base + altura * longe;
    }

    /** o bloco é terreno natural? (o relevo só mexe no que é natural) */
    private static boolean natural(BlockState st) {
        // (5.13.4) a LAJE do vazio (data/rptag/dimension/exilio.json) é, de baixo para
        // cima: bedrock, deepslate, blackstone, tuff, sculk e CONCRETO CINZA-CLARO —
        // sem o concreto, o relevo não via "terreno natural" em lugar nenhum e não
        // esculpia nada (bug pego no teste do servidor).
        return st.is(Blocks.DEEPSLATE) || st.is(Blocks.STONE) || st.is(Blocks.TUFF)
                || st.is(Blocks.GRAVEL) || st.is(Blocks.DIRT) || st.is(Blocks.BLACKSTONE)
                || st.is(Blocks.BASALT) || st.is(Blocks.COBBLED_DEEPSLATE)
                || st.is(Blocks.DEEPSLATE_COAL_ORE) || st.is(Blocks.COAL_ORE)
                || st.is(Blocks.SMOOTH_BASALT) || st.is(Blocks.CLAY)
                || st.is(Blocks.SCULK) || st.is(Blocks.LIGHT_GRAY_CONCRETE)
                || st.is(Blocks.GRAY_CONCRETE);
    }

    // ============================================== ALMAS VAZIAS (5.13.6)
    /** acorda as almas (uma vez por sessão): cada uma no lugar onde ficou perdida. */
    private static void nascerAlmas(ServerLevel nv) {
        for (int i = 0; i < ALMAS_CASAS.length; i++) {
            ALMAS_ESTADO[i * 3] = ALMAS_CASAS[i][0] + 0.5F;
            ALMAS_ESTADO[i * 3 + 1] = 12.0F;           // o chão de verdade vem no primeiro tick
            ALMAS_ESTADO[i * 3 + 2] = ALMAS_CASAS[i][1] + 0.5F;
        }
        almasAcordadas = true;
        RPTagMod.LOGGER.info("RP Tag: {} almas vazias perdidas no vazio", ALMAS_CASAS.length);
    }

    /**
     * o que as almas fazem a cada meio segundo: vagam em volta de onde morreram e, quando alguém
     * chega perto, se viram para a pessoa e se aproximam devagar, sussurrando. Sem dano.
     */
    private static void tickAlmas(ServerLevel nv, long agora) {
        if (agora < proximaAlma) {
            return;
        }
        proximaAlma = agora + 500L;
        try {
            if (!almasAcordadas) {
                nascerAlmas(nv);
            }
            float[] pontos = new float[ALMAS_CASAS.length * 3];
            long t = agora / 1000L;
            for (int i = 0; i < ALMAS_CASAS.length; i++) {
                int[] casa = ALMAS_CASAS[i];
                float ax = ALMAS_ESTADO[i * 3];
                float ay = ALMAS_ESTADO[i * 3 + 1];
                float az = ALMAS_ESTADO[i * 3 + 2];

                // ---- quem está por perto? (medido contra a POSIÇÃO da alma)
                ServerPlayer perto = null;
                double menor = 42.0;
                for (ServerPlayer p : nv.players()) {
                    double d = p.distanceToSqr(ax, ay, az);
                    if (d < menor * menor) {
                        menor = Math.sqrt(d);
                        perto = p;
                    }
                }

                double alvoX, alvoZ;
                if (perto != null && menor > 2.6) {
                    // vem na direção da pessoa, de leve (0,9 bloco por segundo)
                    double dx = perto.getX() - ax;
                    double dz = perto.getZ() - az;
                    double tam = Math.max(0.001, Math.hypot(dx, dz));
                    alvoX = ax + dx / tam * 0.9;
                    alvoZ = az + dz / tam * 0.9;
                    if (menor < 9.0 && agora - ALMA_FALOU[i] > 6000L) {
                        ALMA_FALOU[i] = agora;
                        nv.playSound(null, BlockPos.containing(ax, ay, az),
                                SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT,
                                0.35F, 0.55F + (float) Math.random() * 0.2F);
                        nv.playSound(null, BlockPos.containing(ax, ay, az),
                                SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.22F, 0.7F);
                    }
                } else if (perto != null) {
                    alvoX = ax;                            // colou na pessoa: para e a encara
                    alvoZ = az;
                } else {
                    // perdida: dá voltas lentas em volta do lugar onde morreu
                    double ang = (t * 0.10 + i * 1.9) % (Math.PI * 2);
                    double raio = 2.5 + 1.2 * Math.sin(t * 0.11 + i);
                    alvoX = casa[0] + 0.5 + Math.cos(ang) * raio;
                    alvoZ = casa[1] + 0.5 + Math.sin(ang) * raio;
                }

                // ---- a alma acompanha o chão (montanha, cratera, ruína)
                int bx = (int) Math.floor(alvoX), bz = (int) Math.floor(alvoZ);
                if (nv.isLoaded(new BlockPos(bx, (int) ay, bz))) {
                    int chao = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                    if (chao > nv.getMinBuildHeight() + 2) {
                        ay = chao + 0.4F;
                    }
                }

                ALMAS_ESTADO[i * 3] = (float) alvoX;
                ALMAS_ESTADO[i * 3 + 1] = ay;
                ALMAS_ESTADO[i * 3 + 2] = (float) alvoZ;
                pontos[i * 3] = (float) alvoX;
                pontos[i * 3 + 1] = ay;
                pontos[i * 3 + 2] = (float) alvoZ;

                // ---- poeira da alma (a partícula é enfeite; o corpo é do cliente)
                if (perto != null && menor < 40.0 && Math.random() < 0.5) {
                    nv.sendParticles(ParticleTypes.SOUL, alvoX, ay + 1.0, alvoZ,
                            1, 0.12, 0.22, 0.12, 0.005);
                }
            }
            // manda as posições para quem está no vazio (2x por segundo)
            VazioAlmasPayload pacote = new VazioAlmasPayload(pontos);
            for (ServerPlayer p : nv.players()) {
                if (p.distanceToSqr(0.5, p.getY(), 0.5) < 260.0 * 260.0) {
                    PacketDistributor.sendToPlayer(p, pacote);
                }
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: almas do vazio falharam (o jogo segue)", t);
        }
    }

    /**
     * (5.13.6) A PEDRA DA MONTANHA, por ESTRATO.
     *
     * <p>De baixo para cima: pé (deepslate/tuff) → contraforte (tuff/cobbled deepslate) →
     * parede (blackstone) → cume (smooth basalt). Veios: carvão no miolo, obsidiana chorando
     * nas paredes altas e um fio de sculk no cume — o vazio subindo pela pedra.
     */
    private static BlockState pedraDoMonte(RandomSource r, int i, int h, int x, int y, int z) {
        double f = h <= 1 ? 1.0 : i / (double) (h - 1);        // 0 = pé, 1 = cume
        float s = r.nextFloat();
        if (f > 0.86) {                                        // cume
            if (s < 0.62F) {
                return Blocks.SMOOTH_BASALT.defaultBlockState();
            }
            if (s < 0.80F) {
                return Blocks.BLACKSTONE.defaultBlockState();
            }
            if (s < 0.90F) {
                return Blocks.SCULK.defaultBlockState();
            }
            return Blocks.CRYING_OBSIDIAN.defaultBlockState();
        }
        if (f > 0.55) {                                        // parede
            if (s < 0.55F) {
                return Blocks.BLACKSTONE.defaultBlockState();
            }
            if (s < 0.78F) {
                return Blocks.TUFF.defaultBlockState();
            }
            if (s < 0.92F) {
                return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
            }
            return Blocks.DEEPSLATE_COAL_ORE.defaultBlockState();
        }
        if (f > 0.18) {                                        // contraforte
            if (s < 0.46F) {
                return Blocks.TUFF.defaultBlockState();
            }
            if (s < 0.74F) {
                return Blocks.DEEPSLATE.defaultBlockState();
            }
            if (s < 0.90F) {
                return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            }
            return Blocks.GRAVEL.defaultBlockState();
        }
        return s < 0.62F ? Blocks.DEEPSLATE.defaultBlockState()
                : Blocks.TUFF.defaultBlockState();             // pé
    }

    /** esculpe UM chunk de relevo (montes, crateras, pedras soltas). */
    private static void esculpirChunk(ServerLevel nv, int cx, int cz) {
        RandomSource r = RandomSource.create(cx * 34_133L + cz * 71_117L + 5L);
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = cx * 16 + dx, z = cz * 16 + dz;
                double dist = Math.hypot(x, z);
                if (dist < RAIO_PRACA + 12) {
                    continue;                          // praça intacta
                }
                if (Math.abs(x) <= 10 && z > -36 && z < 8) {
                    continue;                          // caminho do portal
                }
                boolean pertoDeCasa = false;
                for (int[] casa : CASAS) {
                    if (Math.hypot(x - casa[0], z - casa[1]) < 15) {
                        pertoDeCasa = true;
                        break;
                    }
                }
                if (pertoDeCasa) {
                    continue;
                }
                // (5.13.6) e NADA de montanha em cima dos 8 altares: o fragmento do vazio nasce
                // numa coluna de luz, e se o relevo crescer por cima ele fica soterrado.
                boolean pertoDeAltar = false;
                for (int[] alt : VazioGeometria.SANTUARIO) {
                    if (Math.hypot(x - alt[0], z - alt[1]) < 16) {
                        pertoDeAltar = true;
                        break;
                    }
                }
                if (pertoDeAltar) {
                    continue;
                }
                int y = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y <= nv.getMinBuildHeight() + 4) {
                    continue;
                }
                BlockPos topo = new BlockPos(x, y - 1, z);
                if (!natural(nv.getBlockState(topo))) {
                    continue;                          // construção ou já mexido
                }
                // (5.13.4) a amplitude CRESCE com a distância: sem essa rampa o relevo
                // caía 5 blocos de uma vez na borda do anel e parecia terreno quebrado.
                double rampa = Math.min(1.0, Math.max(0.0, (dist - (RAIO_PRACA + 12)) / 40.0));
                int h = (int) Math.round(relevoAltura(x, z) * rampa);
                relevoBlocos += Math.abs(h);
                if (h > 0) {
                    // (5.13.6) montanha tem ESTRATO, não sorteio por bloco
                    for (int i = 0; i < h; i++) {
                        filaForte(new BlockPos(x, y + i, z), pedraDoMonte(r, i, h, x, y + i, z));
                    }
                    // entulho no pé da serra: pedra solta caindo pelo barranco
                    if (h >= 10 && r.nextFloat() < 0.30F) {
                        int quantas = 1 + r.nextInt(3);
                        for (int k = 0; k < quantas; k++) {
                            int ox = r.nextInt(5) - 2, oz = r.nextInt(5) - 2;
                            int py = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    x + ox, z + oz);
                            if (py > nv.getMinBuildHeight() + 3) {
                                filaForte(new BlockPos(x + ox, py, z + oz),
                                        r.nextBoolean()
                                                ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                                                : Blocks.GRAVEL.defaultBlockState());
                            }
                        }
                    }
                } else if (h < 0) {
                    for (int i = 0; i < -h; i++) {
                        BlockPos p = new BlockPos(x, y - 1 - i, z);
                        if (p.getY() <= nv.getMinBuildHeight() + 2
                                || !natural(nv.getBlockState(p))) {
                            break;
                        }
                        filaForte(p, Blocks.AIR.defaultBlockState());
                    }
                    // fundo da cratera: cascalho solto
                    if (r.nextFloat() < 0.35F) {
                        filaForte(new BlockPos(x, y - 1 + h, z),
                                Blocks.GRAVEL.defaultBlockState());
                    }
                }
                // pedras soltas e blocos caídos (a marca do vazio)
                if (r.nextFloat() < 0.04F) {
                    int py = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (py > nv.getMinBuildHeight() + 3) {
                        filaForte(new BlockPos(x, py, z), r.nextBoolean()
                                ? Blocks.BLACKSTONE.defaultBlockState()
                                : Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState());
                    }
                }
            }
        }
    }

    // ================================================================ PEDESTAIS
    /** constrói (uma vez) os 8 pedestais em volta da praça. */
    private static void garantirPedestais(ServerLevel nv, ServerPlayer p) {
        for (int i = 0; i < TOTAL_FRAGMENTOS; i++) {
            if (!marcar(nv, "P" + i)) {
                continue;
            }
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
            if (!marcar(nv, "S" + i)) {
                continue;
            }
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
            if (!marcar(nv, "S" + i)) {
                continue;
            }
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
        if (!marcar(nv, "N")) {
            return;
        }
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
                    // miolo: o cliente desenha o vórtice; o FUNDO de escuridão fica
                    // ATRÁS (lado -z, oposto ao caminho que vem da praça).
                    // (5.13.4) antes o fundo era em +z = o lado de QUEM CHEGA: o
                    // jogador via a parede preta e o vórtice ficava escondido nela.
                    filaForte(pos.offset(0, 0, -1), RPTagMod.ESCURIDAO.get().defaultBlockState());
                    if (!nv.getBlockState(pos).isAir()) {
                        filaForte(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        // (5.13.4) O PORTAL NO NÍVEL DO CHÃO — pedido 53.
        // Antes, se o terreno em volta era mais baixo que a praça, a moldura ficava
        // FLUTUANDO com um degrau solto embaixo. Agora o portal ganha um piso de
        // blackstone na altura do miolo e um alicerce que desce até o chão: ele sempre
        // encosta no chão, sem degrau no ar.
        for (int x = -meia - 1; x <= meia + 1; x++) {
            for (int dz = -1; dz <= 2; dz++) {
                BlockPos piso = base.offset(x, -1, dz);
                filaForte(piso, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                // alicerce (esconde o vão até o terreno)
                for (int y = -2; y >= -6; y--) {
                    filaForte(base.offset(x, y, dz), Blocks.DEEPSLATE_TILES.defaultBlockState());
                }
                // e o ar livre em volta de quem chega, para não ter parede na cara
                if (dz >= 1) {
                    for (int y = 0; y <= 2; y++) {
                        BlockPos ar = base.offset(x, y, dz);
                        if (!nv.getBlockState(ar).isAir()) {
                            filaForte(ar, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }
        RPTagMod.LOGGER.info("RP Tag: portal do vazio construído em {}", base);
    }

    /** abre o portal de baixo para cima (chamado quando o 8º fragmento é encaixado). */
    public static void abrirPortal(ServerLevel nv, ServerPlayer p) {
        RPWorldData dados = RPWorldData.get(nv.getServer());
        // (5.13.3) o portal dos 8 fragmentos é SEMPRE o da praça
        BlockPos base = ancoraPortalPraca(nv);
        if (base == null) {
            base = ancoraPortal(nv);
        }
        if (base == null) {
            return;
        }
        RPTagMod.LOGGER.info("RP Tag: abrindo o portal da praca em {} (pedestais de {})",
                base, p == null ? "comando" : p.getName().getString());
        // (5.13.2) "esse aviso não precisa aparecer que o portal está sendo aberto":
        // o /vazio abrir agora só FAZ — som e animação, nada de mensagem no chat.
        // E se já estava aberto, ele REABRE do zero (é assim que o dono grava vídeo).
        // (5.13.2) "o portal mesmo eu dando o comando para abrir, não aparece ele":
        // garante a MOLDURA também (se o chunk tinha sido apagado/regerado, o comando
        // sozinho abre um portal que não existia).
        enfileirarPortal(nv, base);
        while (!FILA.isEmpty()) {
            colocarFila(nv);
        }
        dados.setPortalAberto(true);
        ABERTURA.put(chavePortal(nv, base), System.currentTimeMillis());
        LINHAS.put(chavePortal(nv, base), 0);
        nv.getChunk(base.getX() >> 4, base.getZ() >> 4);
        for (int y = 0; y < VazioGeometria.ALTURA_MIOLO; y++) {
            for (int x = -VazioGeometria.LARGURA_MIOLO / 2; x <= VazioGeometria.LARGURA_MIOLO / 2; x++) {
                nv.setBlock(base.offset(x, y, 0), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        nv.playSound(null, base, SoundEvents.PORTAL_TRIGGER, SoundSource.AMBIENT, 1.2F, 0.5F);
        nv.playSound(null, base, SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 0.5F, 1.7F);
        if (p != null) {
            // só o 8º fragmento avisa (é conquista do jogador, não comando de admin)
            p.displayClientMessage(Component.literal(
                    "\u00a75\u00a7lOS OITO FRAGMENTOS ESTÃO NO LUGAR."), true);
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
        // (5.13.3) a praça e o portal invocado animam cada um por si: antes, se existia
        // um invocado, a animação só olhava para ele e a praça (a saída do preso) nunca
        // ficava pronta.
        BlockPos praca = ancoraPortalPraca(nv);
        if (praca != null) {
            animarAbertura(nv, agora, praca);
        }
        BlockPos outro = ancoraPortal(nv);
        if (outro != null && !outro.equals(praca)) {
            animarAbertura(nv, agora, outro);
        }
    }

    /** (5.13.3) este portal está autorizado a abrir? (praça = 8 fragmentos; invocado = livre) */
    private static boolean baseAberta(ServerLevel nv, RPWorldData dados, BlockPos base) {
        BlockPos praca = ancoraPortalPraca(nv);
        if (praca != null && praca.equals(base)) {
            return dados.isPortalAberto();
        }
        return portalInvocado(nv);
    }

    private static void animarAbertura(ServerLevel nv, long agora, BlockPos base) {
        RPWorldData dados = RPWorldData.get(nv.getServer());
        if (base == null || !baseAberta(nv, dados, base)) {
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
                // (5.13.2) o miolo do portal é o bloco ESCURIDÃO (pedido do dono),
                // não concreto preto: colisão cheia, ninguém passa "para trás".
                nv.setBlock(base.offset(x, y, 0), RPTagMod.ESCURIDAO.get().defaultBlockState(), 2);
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
        // (5.13.3) a PRAÇA manda: é por ela que o preso sai. Um portal invocado
        // (ferramenta de teste) nunca mais tira a vez dela.
        BlockPos praca = ancoraPortalPraca(nv);
        BlockPos base = praca != null ? praca : ancoraPortal(nv);
        if (base == null) {
            return;
        }

        // (5.13.3) REDE DE SEGURANÇA: se ele já tem os 8 nos pedestais e o portal da
        // praça está fechado, abre AGORA — mesmo que o 8º clique tenha se perdido
        // (chunk descarregado, cliente antigo, clique no bloco errado). Antes, quem
        // enchia os 8 e não via nada ficava preso no vazio para sempre.
        try {
            if (praca != null && !dados.isPortalAberto() && dados.temOsOito(p.getUUID())) {
                abrirPortal(nv, p);
            }
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: auto-abertura do portal falhou", t);
        }

        // --- animação de abertura (da praça E do invocado, se houver os dois)
        animarAbertura(nv, agora);

        // --- atravessar andando (sem clique): 0,7 s dentro do miolo.
        //     · praça: cobra os 8 fragmentos;
        //     · invocado: livre (é ferramenta de teste).
        boolean atravessou = false;
        if (praca != null && dados.isPortalAberto() && dados.temOsOito(p.getUUID())
                && dentroDoPortal(p, praca)) {
            atravessou = esperarNoMiolo(p, nv, dados, praca, agora);
        }
        if (!atravessou) {
            BlockPos invocado = portalInvocado(nv) ? ancoraPortal(nv) : null;
            if (invocado != null && !invocado.equals(praca) && dentroDoPortal(p, invocado)) {
                atravessou = esperarNoMiolo(p, nv, dados, invocado, agora);
            }
        }
        if (!atravessou) {
            NO_PORTAL.remove(p.getUUID());
        }

        // --- manda o estado do portal para o cliente desenhar (1x por segundo)
        long ultimo = ULTIMO_SYNC.getOrDefault(p.getUUID(), 0L);
        if (agora - ultimo > 1000L) {
            ULTIMO_SYNC.put(p.getUUID(), agora);
            if (praca != null && dados.isPortalAberto()) {
                envioEstado(nv, p, dados, praca);
            } else if (praca != null) {
                // portal fechado: manda o da praça (para as colunas dos pedestais e o
                // vinco vermelho), mas sem estado de abertura
                envioEstado(nv, p, dados, praca);
            } else {
                envioEstado(nv, p, dados, base);
            }
        }
    }

    /**
     * (5.13.3) ficar DENTRO do miolo = atravessou.
     *
     * <p>(5.13.5) eram 0,7 s porque o miolo era sólido e o jogador só conseguia ficar
     * ENCOSTADO nele; agora ele ATRAVESSA o bloco andando. A conta: dentro do miolo
     * (0,96 bloco de profundidade útil) ele fica ~0,22 s ANDANDO e ~0,17 s CORRENDO —
     * então o tempo caiu para 0,12 s: passa andando, passa correndo, e quem só encosta
     * na beirada não é levado.
     */
    private static boolean esperarNoMiolo(ServerPlayer p, ServerLevel nv, RPWorldData dados,
            BlockPos base, long agora) {
        long desde = NO_PORTAL.computeIfAbsent(p.getUUID(), k -> agora);
        if (agora - desde > 120L) {
            NO_PORTAL.remove(p.getUUID());
            usarPortal(p, nv, base, dados);
            return true;
        }
        return false;
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
                        // (5.13.2) o cliente precisa saber que ESTE portal existe para
                        // desenhar o vórtice animado (antes só o do vazio recebia).
                        envioEstado(nv, p, dados, base);
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

    /**
     * (5.13.3) O MIOLO ABERTO SE REPARA SOZINHO.
     *
     * <p>Se o portal está aberto e alguma linha do miolo não é {@code escuridao} (chunk
     * descarregado durante a animação, bloco quebrado em criativo, `/fill` de teste),
     * ele recoloca a linha. Roda a cada 5 s no tick do mundo — custa 35 leituras.
     */
    private static void repararMiolo(ServerLevel nv) {
        try {
            RPWorldData dados = RPWorldData.get(nv.getServer());
            if (!dados.isPortalAberto()) {
                return;
            }
            BlockPos base = ancoraPortalPraca(nv);
            if (base == null || !nv.isLoaded(base)) {
                return;
            }
            // (5.13.3) NUNCA durante a animação: o miolo é AR de propósito enquanto as
            // linhas sobem (o reparo já chegou a encher os 35 blocos no 1º segundo e
            // matou a abertura de baixo para cima).
            Long inicio = ABERTURA.get(chavePortal(nv, base));
            if (inicio != null && System.currentTimeMillis() - inicio
                    < VazioGeometria.ABERTURA_MS + 4000L) {
                return;
            }
            int faltando = 0;
            for (int y = 0; y < VazioGeometria.ALTURA_MIOLO; y++) {
                for (int x = -VazioGeometria.LARGURA_MIOLO / 2;
                        x <= VazioGeometria.LARGURA_MIOLO / 2; x++) {
                    if (!nv.getBlockState(base.offset(x, y, 0)).is(RPTagMod.ESCURIDAO.get())) {
                        faltando++;
                    }
                }
            }
            if (faltando == 0) {
                return;
            }
            for (int y = 0; y < VazioGeometria.ALTURA_MIOLO; y++) {
                for (int x = -VazioGeometria.LARGURA_MIOLO / 2;
                        x <= VazioGeometria.LARGURA_MIOLO / 2; x++) {
                    nv.setBlock(base.offset(x, y, 0),
                            RPTagMod.ESCURIDAO.get().defaultBlockState(), 2);
                }
            }
            RPTagMod.LOGGER.info("RP Tag: miolo do portal reparado ({} blocos faltando em {})",
                    faltando, base);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: reparo do portal falhou", t);
        }
    }

    /** cálculo barato do estado do portal (0 fechado, 1 abrindo, 2 aberto). */
    public static int estadoPortal(ServerLevel nv, BlockPos base) {
        RPWorldData dados = RPWorldData.get(nv.getServer());
        if (base == null || !baseAberta(nv, dados, base)) {
            return 0;
        }
        Long inicio = ABERTURA.get(chavePortal(nv, base));
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
                    estadoPortal(nv, base)));
        } catch (Throwable t) {
            // pacote é cosmético: se falhar, o jogo segue
        }
    }

    /** o jogador está dentro do miolo do portal? */
    private static boolean dentroDoPortal(ServerPlayer p, BlockPos base) {
        // (5.13.5) O MIOLO AGORA É ATRAVESSÁVEL (pedido 55): o jogador ENTRA andando no
        // bloco de escuridão. A caixa é a do próprio miolo (5 de largura, 7 de altura,
        // 1 de profundidade) com a folga da hitbox — quem passa na frente, sem entrar,
        // não dispara.
        int meiaL = VazioGeometria.LARGURA_MIOLO / 2;
        return Math.abs(p.getX() - (base.getX() + 0.5)) <= meiaL + 0.5
                // 0,48 = o miolo tem 1 de profundidade: quem está a menos de meio bloco
                // do plano está DENTRO do portal (a hitbox já encosta). Parado na frente,
                // na beirada, não dispara.
                && Math.abs(p.getZ() - (base.getZ() + 0.5)) <= 0.48
                && p.getY() >= base.getY() - 0.6
                && p.getY() <= base.getY() + VazioGeometria.ALTURA_MIOLO - 0.4;
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
            // (5.13.2) o bloco ESCURIDÃO do portal responde em qualquer dimensão
            if (p.level().getBlockState(pos).is(RPTagMod.ESCURIDAO.get())) {
                return interagirPortal(p, nv, pos);
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

    /**
     * (5.13.2) CLIQUE NO BLOCO DE ESCURIDÃO — o portal é usado, não atravessado.
     *
     * <p>Como o miolo é sólido (pedido: <i>"não pode passar para trás o bloco"</i>), quem
     * entra no vazio é quem <b>usa</b> o portal: clique direito ou ficar encostado nele
     * por 0,7 s. Aqui só filtramos se o bloco clicado é mesmo do miolo deste portal.
     */
    public static boolean interagirPortal(ServerPlayer p, ServerLevel nv, BlockPos pos) {
        try {
            RPWorldData dados = RPWorldData.get(p.getServer());
            BlockPos portal = ancoraPortal(nv);
            if (portal == null) {
                return false;
            }
            boolean dentro = Math.abs(pos.getX() - portal.getX()) <= VazioGeometria.LARGURA_MIOLO
                    && Math.abs(pos.getZ() - portal.getZ()) <= 2
                    && pos.getY() >= portal.getY() - 2
                    && pos.getY() <= portal.getY() + VazioGeometria.ALTURA_MIOLO;
            if (!dentro) {
                return false;
            }
            return usarPortal(p, nv, portal, dados);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: interacao do portal falhou", t);
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

    /**
     * (5.13.3) `/vazio pedestais encher` — enche os 8 pedestais na hora e abre o portal.
     *
     * <p>É ferramenta de teste/demonstração: o dono já ficou preso no vazio esperando o
     * portal que os 8 fragmentos deveriam abrir. Com isso, um comando mostra na hora se
     * o portal da praça abre (e, se não abrir, o problema é outro — dá para ver no
     * relatório do {@code /vazio pedestais}).
     *
     * @return quantos pedestais ficaram cheios, ou -1 se não estava no vazio.
     */
    public static int encherPedestais(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel nv) || !ehVazio(nv)) {
            return -1;
        }
        RPWorldData dados = RPWorldData.get(p.getServer());
        for (int i = 0; i < TOTAL_FRAGMENTOS; i++) {
            dados.encaixarNoPedestal(p.getUUID(), i);
            BlockPos a = ancoraPedestal(nv, i);
            nv.getChunk(a.getX() >> 4, a.getZ() >> 4);
            nv.setBlock(a.offset(0, 2, 0), Blocks.SCULK_CATALYST.defaultBlockState(), 2);
        }
        abrirPortal(nv, p);
        return dados.quantosPedestais(p.getUUID());
    }

    /**
     * (5.13.3) RELATÓRIO DO PORTAL — uma linha para o dono mandar quando "não abre".
     *
     * <p>Diz onde o portal da praça deveria estar, quantos blocos de escuridão existem no
     * miolo (0/35 = fechado, 35/35 = aberto), se o mundo acha que ele está aberto, se o
     * jogador tem os 8 e se existe algum portal invocado atrapalhando.
     */
    public static String relatorioPortal(ServerLevel nv, java.util.UUID id) {
        try {
            RPWorldData dados = RPWorldData.get(nv.getServer());
            BlockPos praca = ancoraPortalPraca(nv);
            int escuros = 0;
            if (praca != null && nv.isLoaded(praca)) {
                for (int y = 0; y < VazioGeometria.ALTURA_MIOLO; y++) {
                    for (int x = -VazioGeometria.LARGURA_MIOLO / 2;
                            x <= VazioGeometria.LARGURA_MIOLO / 2; x++) {
                        if (nv.getBlockState(praca.offset(x, y, 0))
                                .is(RPTagMod.ESCURIDAO.get())) {
                            escuros++;
                        }
                    }
                }
            }
            BlockPos invocado = portalInvocado(nv) ? ancoraPortal(nv) : null;
            return "\u00a78praca: \u00a7f" + (praca == null ? "fora do vazio" : praca)
                    + "\u00a78 · miolo: \u00a7f" + escuros + "/" + (VazioGeometria.ALTURA_MIOLO
                            * VazioGeometria.LARGURA_MIOLO)
                    + "\u00a78 · aberto: \u00a7f" + dados.isPortalAberto()
                    + "\u00a78 · 8 fragmentos: \u00a7f" + dados.temOsOito(id)
                    + "\u00a78 · invocado: \u00a7f" + (invocado == null ? "nao" : invocado);
        } catch (Throwable t) {
            return "\u00a7crelatorio falhou: " + t;
        }
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
            // (5.13.4) SEM aviso na entrada (pedido 53) — só o som do portal.
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
        // (5.13.5) SEM aviso na tela de ninguém: o dono não quer texto nenhum no vazio
        // ("nenhum rp admin avisar na tela do player"). Só a animação e o som.
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
        // (5.13.2) UM PORTAL POR DIMENSÃO. O dono: "não é para aparecer em qualquer
        // canto do mundo mais, eu já te avisei isso e continua acontecendo". Antes cada
        // /vazio portal deixava mais uma moldura plantada onde ele estivesse olhando.
        // Agora o portal ANTERIOR desta dimensão é desmontado na hora.
        RPWorldData.Portal antigo = dados.getPortalInvocado(dim(nv));
        if (antigo != null && (antigo.x() != x || antigo.y() != y || antigo.z() != z)) {
            desmontarPortal(nv, new BlockPos(antigo.x(), antigo.y(), antigo.z()));
        }
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
        // (5.13.4) o portal invocado tem marca própria no save ("I:" + posição)
        marcar(nv, "I:" + x + "," + y + "," + z);
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

    /**
     * (5.13.2) DESMONTA uma moldura antiga (o portal anterior da dimensão).
     * Só a moldura sai — não mexe em terreno, não mexe em construção de jogador
     * além dos blocos que o próprio portal colocou (obsidiana/crying/verga/esc. do miolo).
     */
    private static void desmontarPortal(ServerLevel nv, BlockPos base) {
        try {
            if (!nv.isLoaded(base)) {
                nv.getChunk(base.getX() >> 4, base.getZ() >> 4);
            }
            int meia = VazioGeometria.LARGURA_MIOLO / 2 + VazioGeometria.BORDA;
            for (int dx = -meia; dx <= meia; dx++) {
                for (int dy = -2; dy <= VazioGeometria.ALTURA_MIOLO + 1; dy++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        BlockPos pos = base.offset(dx, dy, dz);
                        BlockState st = nv.getBlockState(pos);
                        if (st.is(RPTagMod.ESCURIDAO.get()) || st.is(Blocks.OBSIDIAN)
                                || st.is(Blocks.CRYING_OBSIDIAN) || st.is(Blocks.POLISHED_BLACKSTONE)) {
                            nv.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }
            ABERTURA.remove(chavePortal(nv, base));
            LINHAS.remove(chavePortal(nv, base));
            RPTagMod.LOGGER.info("RP Tag: portal anterior desmontado em {} ({}, {}, {})",
                    dim(nv), base.getX(), base.getY(), base.getZ());
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: nao consegui desmontar o portal antigo", t);
        }
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

    /**
     * (5.13.4) REINICIA O REINO — só para o {@code /vazio construir} (que quer refazer na
     * marra). Nada no jogo normal chama isto: as construções são "uma vez por mundo".
     */
    public static void esquecerConstrucoes(ServerLevel nv) {
        RPWorldData.get(nv.getServer()).esquecerConstrucoes();
        FILA.clear();
        relevoMapaFeito = false;
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
        esquecerConstrucoes(nv);
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

    /**
     * (5.13.4) chamado quando o mundo do vazio carrega.
     *
     * <p>NÃO reconstrói mais nada: o que já foi construído está marcado no save
     * (pedido 53: "só spawnar uma vez"). Aqui só se ajusta o relógio da animação do
     * portal para ele não "reabrir" do zero a cada boot.
     */
    public static void acordar(ServerLevel nv) {
        ABERTURA.clear();
        LINHAS.clear();
        try {
            RPWorldData dados = RPWorldData.get(nv.getServer());
            BlockPos base = ancoraPortalPraca(nv);
            if (base != null && dados.isPortalAberto()) {
                // aberto e estável: a animação já aconteceu, não repete
                ABERTURA.put(chavePortal(nv, base),
                        System.currentTimeMillis() - VazioGeometria.ABERTURA_MS - 1000L);
                LINHAS.put(chavePortal(nv, base), VazioGeometria.ALTURA_MIOLO);
            }
        } catch (Throwable ignorado) {
            // sem portal, sem problema
        }
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

    /** (5.13.4) despeja a fila INTEIRA (o relevo não pode depender do gotejo de 400/tick). */
    private static void despejarFila(ServerLevel nv) {
        int guarda = 0;
        while (!FILA.isEmpty() && guarda++ < 4000) {
            int antes = FILA.size();
            colocarFila(nv);
            if (FILA.size() == antes) {
                break;
            }
        }
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
        // (5.13.4) o baú da ruína (raro = fragmento do vazio) + o relevo desvia dela
        CASAS.add(new int[]{ x, z });
        if (r.nextFloat() < 0.75F) {
            int bx = x0 + 1 + r.nextInt(Math.max(1, larg - 2));
            int bz = z0 + 1 + r.nextInt(Math.max(1, prof - 2));
            colocarBau(nv, bx, y0, bz, r);
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
        // (5.13.4) baú do quarteirão (o relevo desvia do lugar)
        CASAS.add(new int[]{ x, z });
        if (r.nextFloat() < 0.55F) {
            colocarBau(nv, x + r.nextInt(comp) - comp / 2, y0,
                    z + r.nextInt(comp) - comp / 2, r);
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
    /**
     * (5.13.2) O ANEL INICIAL — a cidade que já está de pé quando alguém chega.
     *
     * <p>14 construções em volta da praça (raio 26 a 78, ângulo de ouro), todas
     * determinísticas: 2 quarteirões, o resto ruína baixa e uma torre. São as silhuetas
     * que aparecem no horizonte do spawn — e nenhuma encosta no anel dos pedestais.
     */
    private static void anelInicial(ServerLevel nv) {
        try {
            for (int i = 0; i < 14; i++) {
                double ang = i * 2.399963 + 0.7;
                double raio = 26 + (i * 11 % 52);
                int x = (int) Math.round(Math.cos(ang) * raio);
                int z = (int) Math.round(Math.sin(ang) * raio);
                if (Math.hypot(x, z) < RAIO_PRACA + 6) {
                    continue;                     // nunca em cima da praça
                }
                int cx = Math.floorDiv(x, 96);
                int cz = Math.floorDiv(z, 96);
                if (!marcar(nv, "B:" + cx + "," + cz)) {
                    continue;
                }
                nv.getChunk(x >> 4, z >> 4);
                if (i % 7 == 3) {
                    enfileirarQuarteirao(nv, x, z, cx, cz);
                } else if (i == 5) {
                    enfileirarPredio(nv, x, z, cx, cz);
                } else {
                    enfileirarRuinaBaixa(nv, x, z, cx, cz);
                }
            }
            RPTagMod.LOGGER.info("RP Tag: anel de ruinas em volta da praca erguido (14 construcoes)");
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: anel inicial de ruinas falhou (o jogo segue)", t);
        }
    }

    private static void ruinaAutomatica(ServerLevel nv) {
        for (int tentativa = 0; tentativa < 12; tentativa++) {
            int i = contadorRuinas++;
            double ang = i * 2.399963;                       // espiral de ouro
            double raio = 34 + (i * 13 % 71);                // 34..104
            int x = (int) Math.round(Math.cos(ang) * raio);
            int z = (int) Math.round(Math.sin(ang) * raio);
            int cx = Math.floorDiv(x, 96);
            int cz = Math.floorDiv(z, 96);
            if (Math.hypot(x, z) < RAIO_PRACA + 8) {
                continue;
            }
            if (!marcar(nv, "B:" + cx + "," + cz)) {
                continue;
            }
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
            if (!marcar(nv, "B:" + cx + "," + cz)) {
                continue;
            }
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
        // (5.13.4) o baú do prédio caído (mais fundo, mais raro — e mais gordo)
        CASAS.add(new int[]{ x, z });
        if (r.nextFloat() < 0.85F) {
            colocarBau(nv, x0 + 1 + r.nextInt(Math.max(1, larg - 2)), y0,
                    z0 + 1 + r.nextInt(Math.max(1, prof - 2)), r);
            if (r.nextFloat() < 0.35F) {              // segundo baú no prédio grande
                colocarBau(nv, x0 + 1 + r.nextInt(Math.max(1, larg - 2)), y0 + andarH,
                        z0 + 1 + r.nextInt(Math.max(1, prof - 2)), r);
            }
        }
    }
}
