package dev.rptag;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * (5.12.0) O REINO DO VAZIO — a saída do exílio SEM ADMIN.
 *
 * <p>Pedido do dono (49): <i>"na dimensão do vazio: portal muito longe, animado
 * e bonito, OU reunir pedestais para abrir o portal e voltar — sem admin tirar
 * o jogador; 8 fragmentos do vazio"</i>. Foi feito TUDO junto:
 *
 * <ul>
 *   <li><b>8 pedestais</b> espalhados pelo vazio (de 220 a 620 blocos da praça
 *       de chegada). Cada um tem uma <b>coluna de luz</b> visível de longe e um
 *       <b>fragmento do vazio</b> em cima. Clique com o botão direito para
 *       pegar — um por pedestal, por jogador.</li>
 *   <li><b>O PORTAL</b> fica a <b>780 blocos</b> a leste da chegada: dois
 *       pilares de obsidiana chorando, 14 de altura, com o miolo de sculk. Ele
 *       fica dormindo (apagado) e <b>acende</b> quando quem chega carrega os
 *       8 fragmentos — aí é só clicar no miolo para voltar ao mundo.</li>
 *   <li><b>PRÉDIOS PERDIDOS</b>: ruínas de arranha-céus nascem no breu em volta
 *       de quem anda (paleta preta: deepslate, blackstone, tufo, concreto
 *       cinza), com buracos, andares quebrados e entulho. É o "vazio mais
 *       escuro" com silhueta de cidade morta.</li>
 * </ul>
 *
 * <p>Tudo é construído <b>sob demanda</b> (só perto de jogador) e em fatias de
 * 240 blocos por tick — nada de travar o servidor carregando o vazio inteiro.
 */
public final class VazioReino {

    private VazioReino() {
    }

    // ------------------------------------------------------------------ números
    /** quantos fragmentos abrem o portal. */
    public static final int TOTAL_FRAGMENTOS = 8;
    /** raio (blocos) da chegada em que NADA é construído (a praça). */
    private static final int RUA_PRAÇA = 26;
    /** o portal: 780 blocos a leste da praça ("muito longe"). */
    private static final int PORTAL_X = 780, PORTAL_Z = 40;

    /** posições (x,z) dos 8 pedestais — um em cada rumo, longe da praça. */
    private static final int[][] PED = {
            { 210,   40 }, {  60,  290 }, { -190, 210 }, { -300, -120 },
            { -140, -330 }, {  120, -410 }, {  380, -230 }, {  470,  180 }
    };

    private static final Set<Long> PED_FEITOS = new HashSet<>();
    private static final Set<Long> PREDIOS_FEITOS = new HashSet<>();
    private static boolean portalFeito;
    private static long[][] portalTopo;              // {x, y, z} do miolo

    /** fila de blocos a colocar (pos, estado) — 240 por tick. */
    private static final ArrayDeque<Object[]> FILA = new ArrayDeque<>();
    private static final int POR_TICK = 240;

    /** jogadores caindo no vazio: uuid -> quando morre (ms). */
    private static final java.util.Map<UUID, Long> CAINDO = new java.util.HashMap<>();
    private static long proximaDica;

    // =============================================================== tick geral
    /** chamado do tickExilioMundo (NeptuneEventos) 20x por segundo. */
    public static void tick(ServerLevel exilio, ServerPlayer p, long agora) {
        try {
            // 1) a fila de construção anda SEMPRE (mesmo com o jogador parado)
            colocarFila(exilio);

            // 2) pedestais e portal perto dele: constrói (uma vez) e acende.
            //    A distância é medida pelos PONTOS FIXOS (barato); só força o
            //    chunk quando ele está perto de verdade.
            for (int i = 0; i < PED.length; i++) {
                double d = Math.hypot(p.getX() - PED[i][0], p.getZ() - PED[i][1]);
                if (d > 175) {
                    continue;
                }
                if (!PED_FEITOS.contains(chave(i))) {
                    exilio.getChunk(PED[i][0] >> 4, PED[i][1] >> 4);
                    BlockPos ancora = ancoraPedestal(exilio, i);
                    if (ancora != null) {
                        // se o mundo já tem essa construção salva, NÃO refaz
                        boolean existe = exilio.getBlockState(ancora.offset(0, 4, 0))
                                .is(Blocks.SOUL_LANTERN)
                                || exilio.getBlockState(ancora.offset(0, 5, 0))
                                        .is(Blocks.SCULK_SHRIEKER);
                        if (!existe) {
                            enfileirarPedestal(exilio, i, ancora);
                        }
                        PED_FEITOS.add(chave(i));
                    }
                }
                if (d < 150) {
                    BlockPos ancora = ancoraPedestal(exilio, i);
                    if (ancora != null) {
                        colunaDeLuz(exilio, ancora, i, p);
                    }
                }
            }
            double dp = Math.hypot(p.getX() - PORTAL_X, p.getZ() - PORTAL_Z);
            if (dp < 265) {
                if (!portalFeito) {
                    exilio.getChunk(PORTAL_X >> 4, PORTAL_Z >> 4);
                    BlockPos portal = ancoraPortal(exilio);
                    if (portal != null) {
                        boolean existe = exilio.getBlockState(portal.offset(0, 0, 0))
                                .is(Blocks.SCULK_CATALYST);
                        if (!existe) {
                            enfileirarPortal(exilio, portal);
                        }
                        portalFeito = true;
                    }
                }
                if (dp < 90) {
                    BlockPos portal = ancoraPortal(exilio);
                    if (portal != null) {
                        animarPortal(exilio, portal, p);
                    }
                }
            }

            // 3) PRÉDIOS PERDIDOS: nasce um a cada ~3 s, 60–190 blocos de quem anda
            if (agora >= proximoPredio) {
                proximoPredio = agora + 3000L
                        + (long) (Math.random() * 2500);
                tentarPredio(exilio, p);
            }

            // 4) dicas no actionbar (sem admin, ele tem que se achar)
            if (agora >= proximaDica) {
                proximaDica = agora + 25_000L;
                int tem = RPWorldData.get(p.getServer()).getFragmentos(p.getUUID());
                if (tem < TOTAL_FRAGMENTOS) {
                    p.displayClientMessage(Component.literal(
                            "\u00a78\u00a7oOs fragmentos do vazio: \u00a7c" + tem + "/8\u00a78\u00a7o — "
                            + "procure as colunas de luz. O portal dorme a \u00a7fLESTE\u00a78\u00a7o."), true);
                } else {
                    p.displayClientMessage(Component.literal(
                            "\u00a75\u00a7lO PORTAL ESTÁ ACESO. \u00a77Corra para o \u00a7fLESTE\u00a77 e toque no miolo."), true);
                }
            }

            // 5) o vazio que MATA devagar (pedido 49: "dá um tempo e depois mata")
            tickCaindo(exilio, p, agora);
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: reino do vazio falhou (o jogo segue)", t);
        }
    }

    /** próximo prédio (ms). */
    private static long proximoPredio;

    private static long chave(int i) {
        return 0x5A0000L + i;
    }

    // ============================================================== PEDESTAIS
    /**
     * Base do pedestal i — <b>na altura salva no mundo</b> (ou na altura do
     * terreno quando é a primeira vez). Salvar é essencial: sem isso o
     * heightmap subia a cada restart e o pedestal era construído em cima dele
     * mesmo, virando uma torre infinita.
     */
    public static BlockPos ancoraPedestal(ServerLevel exilio, int i) {
        try {
            int x = PED[i][0], z = PED[i][1];
            if (!exilio.isLoaded(new BlockPos(x, 12, z))) {
                return null;
            }
            int salvo = RPWorldData.get(exilio.getServer()).getVazioY(i);
            if (salvo > exilio.getMinBuildHeight() + 1) {
                return new BlockPos(x, salvo, z);
            }
            int y = exilio.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= exilio.getMinBuildHeight() + 2) {
                return null;
            }
            RPWorldData.get(exilio.getServer()).setVazioY(i, y);
            return new BlockPos(x, y, z);
        } catch (Throwable t) {
            return null;
        }
    }

    /** o miolo do portal — também na altura SALVA no mundo. */
    public static BlockPos ancoraPortal(ServerLevel exilio) {
        try {
            if (!exilio.isLoaded(new BlockPos(PORTAL_X, 12, PORTAL_Z))) {
                return null;
            }
            RPWorldData dados = RPWorldData.get(exilio.getServer());
            int salvo = dados.getVazioY(8);
            if (salvo > exilio.getMinBuildHeight() + 1) {
                return new BlockPos(PORTAL_X, salvo, PORTAL_Z);
            }
            int y = exilio.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PORTAL_X, PORTAL_Z);
            if (y <= exilio.getMinBuildHeight() + 2) {
                return null;
            }
            dados.setVazioY(8, y);
            return new BlockPos(PORTAL_X, y, PORTAL_Z);
        } catch (Throwable t) {
            return null;
        }
    }


    private static void enfileirarPedestal(ServerLevel nv, int i, BlockPos base) {
        // base 3x3 de blackstone polido + 4 de obsidiana + lanterna de alma
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                fila(base.offset(dx, -1, dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
                if (dx == 0 && dz == 0) {
                    for (int k = 0; k < 4; k++) {
                        fila(base.offset(0, k, 0), (k == 1
                                ? Blocks.CRYING_OBSIDIAN
                                : Blocks.OBSIDIAN).defaultBlockState());
                    }
                    fila(base.offset(0, 4, 0), Blocks.SOUL_LANTERN.defaultBlockState());
                    // o "fragmento" físico em cima: um shrieker de sculk girando luz
                    fila(base.offset(0, 5, 0), Blocks.SCULK_SHRIEKER.defaultBlockState());
                } else {
                    fila(base.offset(dx, 0, dz), Blocks.DEEPSLATE_TILES.defaultBlockState());
                }
            }
        }
        // 4 pontas de obsidiana em volta (a marca de quem já foi tocado)
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI * 0.5;
            int px = (int) Math.round(Math.cos(a) * 3);
            int pz = (int) Math.round(Math.sin(a) * 3);
            fila(base.offset(px, 0, pz), Blocks.CRYING_OBSIDIAN.defaultBlockState());
        }
        nv.playSound(null, base, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 0.4F, 0.6F);
        RPTagMod.LOGGER.info("RP Tag: pedestal do vazio {} construído em {}", i + 1, base);
    }

    /** a coluna de luz que se vê de longe (END_ROD subindo). */
    private static void colunaDeLuz(ServerLevel nv, BlockPos base, int i, ServerPlayer p) {
        boolean pego = RPWorldData.get(p.getServer()).jaPegouPedestal(p.getUUID(), i);
        double x = base.getX() + 0.5, y = base.getY() + 6, z = base.getZ() + 0.5;
        for (int k = 0; k < 14; k++) {
            nv.sendParticles(pego ? ParticleTypes.SOUL : ParticleTypes.END_ROD,
                    x, y + k * 1.6, z, 1, 0.06, 0.05, 0.06, 0.0);
        }
        if ((nv.getGameTime() % 12L) == 0L) {
            nv.sendParticles(ParticleTypes.PORTAL, x, y + 4, z, 6, 0.4, 1.2, 0.4, 0.05);
            if (!pego && ((nv.getGameTime() % 60L) == 0L)) {
                p.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.35F, 0.7F);
            }
        }
    }

    // ================================================================ PORTAL
    private static void enfileirarPortal(ServerLevel nv, BlockPos base) {
        int alt = 16;
        // dois pilares de obsidiana chorando, 3 de largura
        for (int lado = -1; lado <= 1; lado += 2) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int y = 0; y < alt; y++) {
                    BlockState st = (y % 4 == 0)
                            ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                            : Blocks.OBSIDIAN.defaultBlockState();
                    fila(base.offset(lado * 5 + dx, y, 0), st);
                }
            }
        }
        // verga em cima + base
        for (int x = -5; x <= 5; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                fila(base.offset(x, alt, dz), Blocks.BLACKSTONE.defaultBlockState());
                fila(base.offset(x, -1, dz), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            }
        }
        // o miolo: catalisador (o "coração" que acende) PRIMEIRO e o sculk em
        // volta. A ordem importa: a fila não sobrescreve bloco que já existe,
        // então o catalisador tem que entrar antes do sculk.
        fila(base.offset(0, 0, 0), Blocks.SCULK_CATALYST.defaultBlockState());
        for (int x = -3; x <= 3; x++) {
            if (x == 0) {
                continue;
            }
            fila(base.offset(x, 0, 0), Blocks.SCULK.defaultBlockState());
        }
        portalTopo = new long[][]{ { base.getX(), base.getY(), base.getZ() } };
        RPTagMod.LOGGER.info("RP Tag: portal do vazio construído em {}", base);
    }

    /** quando alguém com os 8 fragmentos chega perto: o portal respira. */
    private static void animarPortal(ServerLevel nv, BlockPos base, ServerPlayer p) {
        int tem = RPWorldData.get(p.getServer()).getFragmentos(p.getUUID());
        boolean aceso = tem >= TOTAL_FRAGMENTOS;
        long t = nv.getGameTime();
        double cx = base.getX() + 0.5, cy = base.getY(), cz = base.getZ() + 0.5;
        if (aceso) {
            // vórtice: espiral subindo dentro do arco
            for (int i = 0; i < 26; i++) {
                double a = t * 0.22 + i * 0.24;
                double r = 2.4 - (i / 26.0) * 1.4;
                double y = cy + 0.6 + (i / 26.0) * 14.0;
                nv.sendParticles(i % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD,
                        cx + Math.cos(a) * r, y, cz + Math.sin(a) * r,
                        1, 0.0, 0.02, 0.0, 0.0);
            }
            if ((t % 10L) == 0L) {
                nv.sendParticles(ParticleTypes.PORTAL, cx, cy + 6, cz, 24, 2.2, 6.0, 0.6, 0.12);
                nv.playSound(null, base, SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 0.9F, 0.6F);
            }
        } else {
            // dormindo: brasa fraca no miolo e um sussurro a cada 3 s
            nv.sendParticles(ParticleTypes.SMOKE, cx, cy + 1.2, cz, 2, 1.6, 0.2, 0.3, 0.01);
            if ((t % 60L) == 0L) {
                nv.playSound(null, base, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.AMBIENT, 0.35F, 0.5F);
            }
        }
    }

    // ========================================================= PRÉDIOS PERDIDOS
    private static void tentarPredio(ServerLevel nv, ServerPlayer p) {
        int celX = (int) Math.floor(p.getX() / 96.0);
        int celZ = (int) Math.floor(p.getZ() / 96.0);
        RandomSource r = RandomSource.create(celX * 34187L + celZ * 132897L + 17L);
        // anda até 3 células na direção sorteada: nunca constrói em cima do jogador
        for (int tentativa = 0; tentativa < 6; tentativa++) {
            int ox = r.nextInt(5) - 2, oz = r.nextInt(5) - 2;
            int cx = celX + ox, cz = celZ + oz;
            long k = (long) cx * 100_003L + cz;
            if (PREDIOS_FEITOS.contains(k)) {
                continue;
            }
            int x = cx * 96 + 48, z = cz * 96 + 48;
            double d = Math.hypot(x - p.getX(), z - p.getZ());
            if (d < 55 || d > 190) {
                continue;
            }
            if (Math.hypot(x, z) < RUA_PRAÇA + 40) {
                continue;                       // a praça fica limpa
            }
            if (!nv.isLoaded(new BlockPos(x, 12, z))) {
                nv.getChunk(x >> 4, z >> 4);
            }
            // já tem prédio aí? compara a altura do centro com a de 24 blocos
            // ao lado: se o centro é MUITO mais alto, é uma torre de antes.
            int topo = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int viz = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + 24, z);
            PREDIOS_FEITOS.add(k);
            if (topo - viz > 8) {
                continue;
            }
            enfileirarPredio(nv, x, z, cx, cz);
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
        int larg = 9 + r.nextInt(9);          // 9..17
        int prof = 9 + r.nextInt(9);
        int alt = 22 + r.nextInt(42);         // 22..63 de altura
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
            // o topo vai desmanchando (quanto mais alto, menos bloco sobra)
            float desmancha = 0.15F + 0.55F * (y / (float) alt);
            boolean casca = (y % andarH == 0);
            for (int dx = 0; dx < larg; dx++) {
                for (int dz = 0; dz < prof; dz++) {
                    boolean borda = dx == 0 || dz == 0 || dx == larg - 1 || dz == prof - 1;
                    if (borda) {
                        if (r.nextFloat() < desmancha) {
                            continue;                       // buraco na parede
                        }
                        if (r.nextFloat() < 0.06F) {
                            continue;                       // janela
                        }
                        fila(new BlockPos(x0 + dx, y0 + y, z0 + dz),
                                paleta[r.nextInt(paleta.length)]);
                    } else if (casca) {
                        // laje de andar: só metade (o resto desabou)
                        if (r.nextFloat() < 0.55F) {
                            fila(new BlockPos(x0 + dx, y0 + y, z0 + dz),
                                    paleta[r.nextInt(paleta.length)]);
                        }
                    }
                }
            }
        }
        // entulho em volta da base (pedaços caídos)
        for (int i = 0; i < 70; i++) {
            int dx = r.nextInt(larg + 10) - 5;
            int dz = r.nextInt(prof + 10) - 5;
            int px = x0 + dx, pz = z0 + dz;
            int y = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
            if (y <= nv.getMinBuildHeight() + 1) {
                continue;
            }
            fila(new BlockPos(px, y, pz), paleta[r.nextInt(paleta.length)]);
        }
    }

    // ------------------------------------------------------------------ fila
    private static void fila(BlockPos pos, BlockState st) {
        if (FILA.size() > 26_000) {
            return;                              // trava de segurança
        }
        FILA.add(new Object[]{ pos, st });
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

    /** (debug) força o andamento da fila (usado pelo /devvazio). */
    public static void drenar(ServerLevel nv) {
        colocarFila(nv);
    }

    private static void colocarFila(ServerLevel nv) {
        int n = 0;
        while (!FILA.isEmpty() && n < POR_TICK) {
            Object[] e = FILA.poll();
            BlockPos pos = (BlockPos) e[0];
            BlockState st = (BlockState) e[1];
            if (!nv.isLoaded(pos)) {
                continue;
            }
            BlockState atual = nv.getBlockState(pos);
            if (!atual.isAir() && !atual.canBeReplaced()) {
                continue;                        // não estraga o que já existe
            }
            nv.setBlock(pos, st, 2);
            n++;
        }
    }

    // ============================================================== INTERAÇÃO
    /**
     * Clique direito num pedestal ou no miolo do portal.
     *
     * @return true se o clique era nosso (aí o jogo não abre/fecha nada).
     */
    public static boolean interagir(ServerPlayer p, BlockPos pos) {
        try {
            if (!(p.level() instanceof ServerLevel nv)
                    || !p.level().dimension().equals(NeptuneEventos.CHAVE_EXILIO_PUBLICA)) {
                return false;
            }
            RPWorldData dados = RPWorldData.get(p.getServer());
            // --- pedestal?
            for (int i = 0; i < PED.length; i++) {
                BlockPos a = ancoraPedestal(nv, i);
                if (a == null) {
                    continue;
                }
                if (Math.hypot(pos.getX() - a.getX(), pos.getZ() - a.getZ()) <= 3.5
                        && pos.getY() >= a.getY() - 1 && pos.getY() <= a.getY() + 6) {
                    return usarPedestal(p, nv, a, i, dados);
                }
            }
            // --- portal?
            BlockPos portal = ancoraPortal(nv);
            if (portal != null
                    && Math.hypot(pos.getX() - portal.getX(), pos.getZ() - portal.getZ()) <= 6.5
                    && pos.getY() >= portal.getY() - 1 && pos.getY() <= portal.getY() + 3) {
                return usarPortal(p, nv, portal, dados);
            }
            return false;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: interacao do vazio falhou", t);
            return false;
        }
    }

    private static boolean usarPedestal(ServerPlayer p, ServerLevel nv, BlockPos base,
            int i, RPWorldData dados) {
        int total = dados.pegarFragmento(p.getUUID(), i);
        if (total < 0) {
            p.displayClientMessage(Component.literal(
                    "\u00a78Este pedestal está vazio. O fragmento já é seu."), true);
            return true;
        }
        nv.playSound(null, base, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 0.7F);
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 0.6F);
        nv.sendParticles(ParticleTypes.SOUL, base.getX() + 0.5, base.getY() + 5.5, base.getZ() + 0.5,
                40, 0.6, 0.8, 0.6, 0.06);
        nv.sendParticles(ParticleTypes.END_ROD, base.getX() + 0.5, base.getY() + 5.0, base.getZ() + 0.5,
                24, 0.4, 0.6, 0.4, 0.04);
        // o pedestal apaga (fica só a lanterna de alma)
        nv.setBlock(base.offset(0, 5, 0), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
        p.displayClientMessage(Component.literal(
                "\u00a75\u00a7lFRAGMENTO DO VAZIO \u00a7f" + total + "\u00a77/\u00a7f"
                + TOTAL_FRAGMENTOS
                + (total >= TOTAL_FRAGMENTOS
                        ? "\u00a75 — O PORTAL ACORDOU. Ele fica a LESTE."
                        : "\u00a78 — ainda faltam " + (TOTAL_FRAGMENTOS - total) + ".")), true);
        if (total >= TOTAL_FRAGMENTOS) {
            nv.playSound(null, base, SoundEvents.WITHER_SPAWN, SoundSource.AMBIENT, 0.5F, 1.6F);
            for (ServerPlayer outro : nv.players()) {
                outro.displayClientMessage(Component.literal(
                        "\u00a75\u00a7lAs oito peças se juntaram. \u00a77Alguma coisa acordou no leste."), true);
            }
        }
        return true;
    }

    private static boolean usarPortal(ServerPlayer p, ServerLevel nv, BlockPos base, RPWorldData dados) {
        int tem = dados.getFragmentos(p.getUUID());
        if (tem < TOTAL_FRAGMENTOS) {
            nv.playSound(null, base, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.AMBIENT, 0.8F, 0.5F);
            p.displayClientMessage(Component.literal(
                    "\u00a78O portal está morto. \u00a77Faltam \u00a7f"
                    + (TOTAL_FRAGMENTOS - tem) + "\u00a77 fragmentos do vazio ("
                    + tem + "/8).\u00a78 Procure as colunas de luz."), false);
            return true;
        }
        dados.limparFragmentos(p.getUUID());
        nv.playSound(null, base, SoundEvents.PORTAL_TRAVEL, SoundSource.AMBIENT, 1.0F, 0.7F);
        for (ServerPlayer outro : nv.players()) {
            outro.displayClientMessage(Component.literal(
                    "\u00a75\u00a7l" + p.getName().getString().toUpperCase()
                    + " \u00a77atravessou o portal. O vazio ficou mais vazio."), true);
        }
        NeptuneEventos.voltarPeloPortal(p);
        return true;
    }

    // ================================================== O VAZIO QUE MATA DEVAGAR
    /** quanto tempo (ms) o vazio segura a pessoa antes de matar. */
    private static final long ESPERA_MORTE = 12_000L;

    private static void tickCaindo(ServerLevel exilio, ServerPlayer p, long agora) {
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
        // segura a queda (gravidade zero) e conta
        if (p.getY() < -2.0) {
            p.teleportTo(p.getX(), -2.0, p.getZ());
        }
        p.setDeltaMovement(0, 0, 0);
        p.fallDistance = 0;
        long seg = (restam + 999L) / 1000L;
        if ((exilio.getGameTime() % 10L) == 0L) {
            p.displayClientMessage(Component.literal(
                    "\u00a74\u00a7lO VAZIO TE CHAMA \u00a7c" + seg + "\u00a78s"), true);
            exilio.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1.0, p.getZ(),
                    12, 0.7, 0.8, 0.7, 0.02);
            p.playNotifySound(SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.AMBIENT,
                    0.4F, 1.5F - 0.05F * (12 - seg));
        }
    }

    /** (5.12.0) este jogador está no tempo de espera do vazio que mata? */
    public static boolean estaCaindo(UUID id) {
        return CAINDO.containsKey(id);
    }

    /** limpa o estado de quem saiu do vazio (chamado ao voltar). */
    public static void limparCaindo(UUID id) {
        CAINDO.remove(id);
    }

    /** usado pelo /vazio fragmentos (debug do dono). */
    public static void darTudo(ServerPlayer p) {
        RPWorldData dados = RPWorldData.get(p.getServer());
        for (int i = 0; i < TOTAL_FRAGMENTOS; i++) {
            dados.pegarFragmento(p.getUUID(), i);
        }
    }

    /** reinicia o reino (some com as construções marcadas e refaz). */
    public static void esquecerConstrucoes() {
        PED_FEITOS.clear();
        PREDIOS_FEITOS.clear();
        portalFeito = false;
        FILA.clear();
    }

    /** @return a chave da dimensão (atalho). */
    public static boolean ehVazio(Level level) {
        return level.dimension().equals(NeptuneEventos.CHAVE_EXILIO_PUBLICA);
    }

    /** lista dos 8 pontos dos pedestais (usada pelas dicas e pelo /vazio). */
    public static String listaPedestais() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PED.length; i++) {
            if (i > 0) {
                sb.append("\u00a78 · ");
            }
            sb.append("\u00a7f").append(i + 1).append("\u00a78 (").append(PED[i][0])
                    .append(", ").append(PED[i][1]).append(")");
        }
        return sb.toString();
    }

    /** (debug) levanta um prédio perdido numa célula fixa (teste do dono). */
    public static String testePredio(ServerLevel nv, int celX, int celZ) {
        int x = celX * 96 + 48, z = celZ * 96 + 48;
        nv.getChunk(x >> 4, z >> 4);
        enfileirarPredio(nv, x, z, celX, celZ);
        int fila = FILA.size();
        while (FILA.size() > 0) {
            colocarFila(nv);
        }
        int topo = nv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        StringBuilder sb = new StringBuilder("predio em " + x + "," + z + " fila=" + fila + " topo=" + topo);
        for (int dy = 1; dy <= 30; dy += 5) {
            sb.append(" y").append(topo - dy).append("=")
                    .append(nv.getBlockState(new BlockPos(x, topo - dy, z)).getBlock().toString()
                            .replace("Block{", "").replace("}", ""));
            sb.append(" x").append(x - 5).append("=")
                    .append(nv.getBlockState(new BlockPos(x - 5, topo - dy, z)).getBlock().toString()
                            .replace("Block{", "").replace("}", ""));
        }
        return sb.toString();
    }

    /** itera a fila de construção (para o /vazio construir tudo de uma vez). */
    public static int construindo() {
        return FILA.size();
    }

    /** força a construção de tudo em volta do jogador (debug). */
    public static int construirTudo(ServerLevel nv, ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < PED.length; i++) {
            nv.getChunk(PED[i][0] >> 4, PED[i][1] >> 4);
            BlockPos a = ancoraPedestal(nv, i);
            if (a == null) {
                continue;
            }
            if (!PED_FEITOS.contains(chave(i))) {
                enfileirarPedestal(nv, i, a);
                PED_FEITOS.add(chave(i));
                n++;
            }
        }
        nv.getChunk(PORTAL_X >> 4, PORTAL_Z >> 4);
        BlockPos portal = ancoraPortal(nv);
        if (portal != null && !portalFeito) {
            enfileirarPortal(nv, portal);
            portalFeito = true;
            n++;
        }
        return n;
    }

    /** religa a gravidade de quem estava caindo (na saída do servidor). */
    public static void soltarCaindo(ServerPlayer p) {
        if (CAINDO.remove(p.getUUID()) != null) {
            p.setNoGravity(false);
        }
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
}
