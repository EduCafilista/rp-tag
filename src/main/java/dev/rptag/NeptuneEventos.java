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
    private static final java.util.Set<Long> CAVADOS = new java.util.HashSet<>();
    /** queda: uuid -> {y do chão original} (só quem caiu na fenda) */
    private static final Map<UUID, long[]> QUEDA = new HashMap<>();
    private static final ResourceLocation MOD_RAIZ =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "raiz_do_terremoto");

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

        Caixa(BlockPos pos) {
            this.pos = pos;
            this.inicio = System.currentTimeMillis();
        }
    }

    /** Chamado do ServerEvents (PostTick) — 20x por segundo. */
    public static void tick(MinecraftServer server) {
        try {
            tickTerremoto(server);
            tickDeus(server);
            tickSaidas(server);
            tickCaixas(server);
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
        faseTravado = true;   // começa travado (o primeiro baque)
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.displayClientMessage(Component.literal(
                    "\u00a78\u00a7oA terra está cedendo. O universo está se corrigindo..."), false);
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal(
                "\u00a7c\u00a7l\u2694 TERREMOTO  \u00a77— o chão está rachando! (\u00a7c"
                        + segundos + "s\u00a77)").withStyle(ChatFormatting.RED), false);
        // (5.6.0) o TRANCO inicial: flash + onda de choque + rachadura na tela
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(p, new NeptunePackets.Cena(
                    NeptunePackets.Cena.CENA_ESTOURO, 1.0F, 2600, System.nanoTime()));
        }
        enviarTerremoto(server, true, 1.0F, segundos, true);
    }

    public static void pararTerremoto(MinecraftServer server, boolean silencioso) {
        boolean estava = terremotoAtivo;
        terremotoAtivo = false;
        faseTravado = false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            p.removeEffect(MobEffects.DIG_SLOWDOWN);
            imobilizar(p, false);
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
                    // (5.6.0) O CHÃO ABRE: imobiliza de verdade (velocidade e
                    // pulo zerados por atributo) e abre a fenda embaixo dele.
                    imobilizar(p, true);
                    boolean caiu = abrirFenda(p);
                    if (caiu) {
                        PacketDistributor.sendToPlayer(p, new NeptunePackets.Cena(
                                NeptunePackets.Cena.CENA_QUEDA, 1.0F, 5500, System.nanoTime()));
                        p.displayClientMessage(Component.literal(
                                "\u00a74\u00a7lO CH\u00c3O SE ABRIU! \u00a7cVoc\u00ea caiu e n\u00e3o consegue se levantar."), true);
                    } else {
                        PacketDistributor.sendToPlayer(p, new NeptunePackets.Cena(
                                NeptunePackets.Cena.CENA_RACHADURA, 0.7F, 4500, System.nanoTime()));
                        p.displayClientMessage(Component.literal(
                                "\u00a78Voc\u00ea cambaleia \u2014 o ch\u00e3o est\u00e1 cedendo."), true);
                    }
                }
            } else {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    p.removeEffect(MobEffects.DIG_SLOWDOWN);
                    imobilizar(p, false);
                    p.displayClientMessage(Component.literal(
                            "\u00a7aAgora! Corre!"), true);
                }
                // a terra se fecha atrás de quem caiu (antes de sair do buraco)
                fecharFendas(server);
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
                for (double r = 1.2; r <= 6.0; r += 1.2) {
                    double a = Math.toRadians((agora / 3 % 360) + r * 61);
                    double x = px + Math.cos(a) * r;
                    double z = pz + Math.sin(a) * r;
                    BlockPos chao = BlockPos.containing(x, py - 0.4, z);
                    var estado = nv.getBlockState(chao);
                    if (estado.isAir()) {
                        continue;                       // não faz poeira no ar
                    }
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

    // =================================================================== DEUS
    public static boolean ehDeus(UUID id) {
        return DEUSES.getOrDefault(id, false);
    }

    public static void ligarDeus(ServerPlayer p) {
        DEUSES.put(p.getUUID(), true);
        TRILHA.remove(p.getUUID());
        PacketDistributor.sendToPlayer(p, new NeptunePackets.Aura(true, true));
        for (ServerPlayer outro : p.getServer().getPlayerList().getPlayers()) {
            if (outro != p) {
                PacketDistributor.sendToPlayer(outro, new NeptunePackets.Aura(false, false));
            }
        }
        p.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "\u00a78\u00a7l\u2588 \u00a75\u00a7lO VAZIO CAMINHA ENTRE NÓS\u00a78 \u00a7l\u2588")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        p.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "\u00a78Uma escuridão sem fundo acompanha \u00a7f" + p.getName().getString()
                        + "\u00a78. Não chegue perto.").withStyle(ChatFormatting.DARK_GRAY), false);
    }

    public static void desligarDeus(ServerPlayer p) {
        DEUSES.remove(p.getUUID());
        TRILHA.remove(p.getUUID());
        PacketDistributor.sendToPlayer(p, new NeptunePackets.Aura(false, false));
        p.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "\u00a77A escuridão recolheu.").withStyle(ChatFormatting.DARK_GRAY), false);
    }

    private static void tickDeus(MinecraftServer server) {
        long agora = System.currentTimeMillis();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!ehDeus(p.getUUID())) {
                continue;
            }
            ServerLevel nv = (ServerLevel) p.level();
            Vec3 pos = p.position();

            // ---- AURA (5.5.0) — TUDO GIRA EM VOLTA DO CORPO.
            // O dono reclamou de dois coisas: (1) o efeito subia pela CABEÇA
            // (era o SCULK_SOUL em y+2,1 — aquelas chamas azuis em cima do
            // personagem na captura dele) e (2) as partículas não "circulavam"
            // o player: ficavam empilhadas num anel que subia. Agora são TRÊS
            // anéis horizontais de alturas diferentes, girando em sentidos
            // diferentes (peito, cintura e tornozelo) — e NADA acima da cabeça.
            double volta = agora / 1000.0;
            // (1) peito: tinta preta, gira rápido no sentido horário
            double rPeito = 1.20;
            for (int i = 0; i < 10; i++) {
                double a = Math.toRadians(volta * 170.0 + i * 36.0);
                nv.sendParticles(ParticleTypes.SQUID_INK,
                        pos.x + Math.cos(a) * rPeito, pos.y + 1.05, pos.z + Math.sin(a) * rPeito,
                        0, 0.02, 0.02, 0.02, 0.0);
            }
            // (2) cintura: fumaça escura, gira ao contrário (mais devagar)
            double rCintura = 1.05;
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(-volta * 105.0 + i * 60.0);
                nv.sendParticles(ParticleTypes.SMOKE,
                        pos.x + Math.cos(a) * rCintura, pos.y + 0.62, pos.z + Math.sin(a) * rCintura,
                        0, 0.0, 0.01, 0.0, 0.0);
            }
            // (3) tornozelo: cinza/poeira preta, gira no sentido horário
            double rChao = 1.45;
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(volta * 70.0 + i * 60.0);
                nv.sendParticles(ParticleTypes.ASH,
                        pos.x + Math.cos(a) * rChao, pos.y + 0.12, pos.z + Math.sin(a) * rChao,
                        0, 0.0, 0.0, 0.0, 0.0);
            }

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

    // =================================================================== SAÍDA
    /** Agenda a saída: animação verde agora, kick depois de segundos. */
    public static void sairDoServidor(ServerPlayer p, int segundos, String motivo) {
        PacketDistributor.sendToPlayer(p, new NeptunePackets.Saida(segundos, p.getName().getString()));
        ServerLevel nv = (ServerLevel) p.level();
        Vec3 pos = p.position();
        for (int i = 0; i < 90; i++) {
            double a = Math.toRadians(i * 4);
            double r = 0.2 + (i / 90.0) * 1.1;
            nv.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.x + Math.cos(a) * r, pos.y + 0.15 + (i / 90.0) * 1.8, pos.z + Math.sin(a) * r,
                    1, 0.02, 0.02, 0.02, 0.0);
        }
        nv.playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, 0.7F);
        p.getServer().getPlayerList().broadcastSystemMessage(Component.literal(
                "\u00a7a" + p.getName().getString() + " \u00a77está se desfazendo em luz verde...")
                .withStyle(ChatFormatting.GREEN), false);
        SAIDAS.add(new Object[]{ System.currentTimeMillis() + Math.max(1, segundos) * 1000L,
                p.getUUID(), motivo });
    }

    private static void tickSaidas(MinecraftServer server) {
        long agora = System.currentTimeMillis();
        Iterator<Object[]> it = SAIDAS.iterator();
        while (it.hasNext()) {
            Object[] s = it.next();
            if (agora < (Long) s[0]) {
                continue;
            }
            it.remove();
            ServerPlayer p = server.getPlayerList().getPlayer((UUID) s[1]);
            if (p == null) {
                continue;
            }
            p.connection.disconnect(Component.literal("\u00a78\u00a7l\u2591 O universo se corrigiu \u00a78\u00a7l\u2591\n\n")
                    .append(Component.literal(String.valueOf(s[2])).withStyle(ChatFormatting.GRAY)));
        }
    }

    // =================================================================== BICHO
    public static Caixa colocarCaixa(ServerLevel nv, BlockPos pos) {
        // (5.4.0) põe o BLOCO de verdade no mundo (é a caixinha que o jogador vê
        // e de onde o bicho pula) e registra pra o tick vigiar a distância.
        if (nv.getBlockState(pos).isAir()) {
            nv.setBlockAndUpdate(pos, RPTagMod.CAIXA_BICHO.get().defaultBlockState());
        }
        Caixa c = new Caixa(pos);
        CAIXAS.add(c);
        nv.playSound(null, pos, RPTagMod.SOM_CAIXA.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
        return c;
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
            long durou = (System.currentTimeMillis() - c.inicio) / 1000L;
            // a caixinha range enquanto ninguém chega (a cada 30 s repete a música)
            if (durou < BICHO_MUSICA_SEG && durou > 0 && durou % 30 == 0) {
                nv.playSound(null, c.pos, RPTagMod.SOM_CAIXA.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
            }
            // quem chegou perto?
            for (Player p : nv.players()) {
                if (!(p instanceof ServerPlayer sp)) {
                    continue;
                }
                if (sp.isCreative() || sp.isSpectator()) {
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
        nv.sendParticles(ParticleTypes.SQUID_INK, pos.x, pos.y + 1, pos.z, 120, 0.6, 1.0, 0.6, 0.03);
        nv.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1, pos.z, 80, 0.5, 1.0, 0.5, 0.4);
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

    /** Manda pra dimensão do EXÍLIO (5.5.0: rptag:exilio — tudo preto e vazio). */
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
        // (5.5.0) chão preto em y=0: 1 bloco de concreto preto. O jogador cai
        // em cima dele (antes ia pra y=120 num mundo SEM chão e despencava).
        alvo.teleportTo(destino, 0.5, 1.0, 0.5, java.util.Set.of(), alvo.getYRot(), alvo.getXRot());
        alvo.displayClientMessage(Component.literal(
                "\u00a78Não há nada aqui. Nem chão, nem céu, nem volta.\n"
                + "\u00a78(um admin pode te tirar com /vazio soltar <jogador>)"), false);
    }

    /** (5.5.0) tira alguém do exílio e devolve pro spawn do overworld. */
    public static void soltarDoExilio(ServerPlayer alvo) {
        ServerLevel mundo = alvo.getServer().overworld();
        BlockPos spawn = mundo.getSharedSpawnPos();
        alvo.teleportTo(mundo, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5,
                java.util.Set.of(), alvo.getYRot(), alvo.getXRot());
        alvo.displayClientMessage(Component.literal(
                "\u00a77A escuridão te devolve. Não conte o que viu."), false);
    }
}
