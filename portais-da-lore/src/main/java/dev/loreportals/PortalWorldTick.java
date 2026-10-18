package dev.loreportals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MOTOR dos portais no servidor:
 * <ul>
 *   <li>a cada 20 ticks valida os aneis (moldura quebrada = portal dorme;
 *       anel completo = portal acorda SOZINHO);</li>
 *   <li>a cada tick verifica quem esta DENTRO de um portal ativo e
 *       conectado — 10 ticks dentro = teleporte pro parceiro (com cooldown);</li>
 *   <li>particulas ambiente no disco e som de portal de vez em quando.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LorePortalsMod.MODID)
public final class PortalWorldTick {

    /** Ticks dentro do portal pra teleportar. */
    private static final int TICKS_PARA_TELEPORTAR = 10;
    /** Cooldown pos-teleporte (ms) — sem isso o jogador volta e volta. */
    private static final long COOLDOWN_MS = 3000;

    private static final Map<UUID, Long> DENTRO_DESDE = new HashMap<>();
    private static final Map<UUID, Long> ATEH = new HashMap<>();

    private PortalWorldTick() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        motor(event.getServer());
    }

    private static void motor(net.minecraft.server.MinecraftServer server) {
        boolean tickLento = server.getTickCount() % 20 == 0;
        try {
            for (ServerLevel level : server.getAllLevels()) {
                String dim = level.dimension().location().toString();

                // ---- validacao lenta (anel) + ambiente ----
                if (tickLento) {
                    PortalData data = PortalData.get(server);
                    for (PortalData.Portal p : data.todos()) {
                        if (!p.dim().equals(dim)) {
                            continue;
                        }
                        if (level.getBlockEntity(new BlockPos(p.x(), p.y(), p.z()))
                                instanceof PortalBlockEntity be) {
                            be.revalidarAnel(); // acorda se o anel fechou / dorme se abriu
                            ambiente(level, be);
                        }
                    }
                }

                // ---- teleporte (todo tick) ----
                for (ServerPlayer player : level.getPlayers(pl -> true)) {
                    verificaTeleporte(level, player, tickLento);
                }
            }
        } catch (Throwable t) {
            LorePortalsMod.LOG.error("Portais: erro no tick do servidor (motor pausado neste tick)", t);
        }
    }

    /** Moldura colocada/quebrada: acorda nucleos num raio (validacao rapida). */
    public static void procurarNucleosDormantes(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }
        // o anel vai de dv 0 a 4 a partir do nucleo: raio 4 cobre tudo
        for (PortalBlockEntity be : PortalBlockEntity.nucleosProximos(level, pos, 5)) {
            be.revalidarAnel();
        }
    }

    // ------------------------------------------------------------------
    // TELEPORTE
    // ------------------------------------------------------------------
    private static void verificaTeleporte(ServerLevel level, ServerPlayer player, boolean tickLento) {
        PortalData data = PortalData.get(level.getServer());
        UUID id = player.getUUID();
        boolean dentro = false;
        PortalData.Portal destino = null;
        PortalData.Portal origem = null;

        for (PortalData.Portal p : data.todos()) {
            if (!p.dim().equals(level.dimension().location().toString()) || p.linkedTo() == null) {
                continue;
            }
            PortalData.Portal parceiro = data.get(p.linkedTo());
            if (parceiro == null) {
                continue;
            }
            // centro do disco
            boolean molde = PortalBlockEntity.TIPO_MOLDE.equals(p.tipo());
            float cy = molde ? PortalBlockEntity.DISCO_Y : PortalBlockEntity.DISCO_Y_PAR;
            float raio = molde ? PortalBlockEntity.DISCO_RAIO_MOLDE : PortalBlockEntity.DISCO_RAIO_PAR;
            float rry = 2.05F;
            float cdu = 0.0F;
            if (molde && level.getBlockEntity(new BlockPos(p.x(), p.y(), p.z()))
                    instanceof PortalBlockEntity beM && beM.renderRx > 0.5F) {
                // disco moldado no formato do jogador: usa a geometria real
                raio = beM.renderRx;
                rry = beM.renderRy;
                cy = beM.renderCy;
                cdu = beM.renderCentroDu;
            }
            double cdx = (p.facing() == 0 ? 1 : p.facing() == 2 ? -1 : 0) * cdu;
            double cdz = (p.facing() == 1 ? 1 : p.facing() == 3 ? -1 : 0) * cdu;
            double cx = p.x() + 0.5 + cdx;
            double cyy = p.y() + cy;
            double cz = p.z() + 0.5 + cdz;
            Vec3 rel = player.position().subtract(cx, cyy, cz);
            // distancia ao EIXO do disco (plano vertical p/ molde, horizontal p/ par)
            if (molde) {
                // plano do anel: X (facing 0/2) ou Z (facing 1/3); "du" ao longo do plano
                double du = (p.facing() == 1 || p.facing() == 3) ? rel.x : rel.z;
                double dn = (p.facing() == 1 || p.facing() == 3) ? rel.z : rel.x;
                double dv = player.getY() - (p.y() + 1.0);
                dv = Math.max(0.0, Math.min(2.0 * rry, dv));
                double dentroDoRaio = Math.hypot(du, dv);
                double espessura = Math.abs(dn);
                if (dentroDoRaio > raio + 0.6 || espessura > 0.75
                        || player.getY() < p.y() || player.getY() > p.y() + 2.0 * rry + 0.6) {
                    continue;
                }
            } else {
                // OVAL VERTICAL girando sobre o nucleo (centro y+1.7, ry 1.05):
                // conta como "dentro" quem esta perto do nucleo na faixa do oval
                double dxz = Math.hypot(rel.x, rel.z);
                if (dxz > 1.3 || rel.y < -1.3 || rel.y > 1.2) {
                    continue;
                }
            }
            dentro = true;
            destino = parceiro;
            origem = p;
            break;
        }

        long now = System.currentTimeMillis();
        if (!dentro) {
            DENTRO_DESDE.remove(id);
            return;
        }
        long ateh = ATEH.getOrDefault(id, 0L);
        if (now < ateh) {
            return; // ainda em cooldown
        }
        long desde = DENTRO_DESDE.computeIfAbsent(id, k -> now);
        long decorrido = now - desde;
        if (decorrido < TICKS_PARA_TELEPORTAR * 50L) {
            carregar(level, player, decorrido, TICKS_PARA_TELEPORTAR * 50L);
            return; // ainda carregando o portal
        }
        DENTRO_DESDE.remove(id);
        ATEH.put(id, now + COOLDOWN_MS);

        PortalData.get(level.getServer()).contarViagem(destino.key(), id.toString());

        // teleporta pro parceiro (PAR = em cima do nucleo; MOLDE = no vao do anel)
        boolean moldeD = PortalBlockEntity.TIPO_MOLDE.equals(destino.tipo());
        ServerLevel alvoLevel = serverLevel(level.getServer(), destino.dim());
        if (alvoLevel == null) {
            return;
        }
        PortalBlockEntity beD = alvoLevel.getBlockEntity(
                new BlockPos(destino.x(), destino.y(), destino.z()))
                instanceof PortalBlockEntity b ? b : null;
        double alturaChegada = 1.02;
        if (moldeD && beD != null && beD.renderCy > 0.1F) {
            alturaChegada = Math.max(1.02, beD.renderCy - 0.6);
        } else if (moldeD) {
            alturaChegada = PortalBlockEntity.DISCO_Y - 0.6;
        }
        double tx = destino.x() + 0.5;
        double ty = destino.y() + alturaChegada;
        double tz = destino.z() + 0.5;

        // ANIMACAO DE TRAVESSIA: onda no disco de ORIGEM (succao) + DESTINO
        // (materializacao) + particulas nos dois lados
        if (origem != null && level.getBlockEntity(
                new BlockPos(origem.x(), origem.y(), origem.z()))
                instanceof PortalBlockEntity beO) {
            beO.dispararPulso();
            double oy = origem.y() + (PortalBlockEntity.TIPO_MOLDE.equals(origem.tipo())
                    ? (beO.renderCy > 0.1F ? beO.renderCy : PortalBlockEntity.DISCO_Y)
                    : PortalBlockEntity.DISCO_Y_PAR);
            level.sendParticles(ParticleTypes.PORTAL,
                    origem.x() + 0.5, oy + 0.2, origem.z() + 0.5, 60, 0.7, 1.1, 0.7, 0.55);
            // SOM DE ENTRADA (ogg proprio do mod) + whoosh vanilla de apoio
            level.playSound(null, new BlockPos(origem.x(), origem.y(), origem.z()),
                    LorePortalsMod.SOM_PORTAL_ENTRADA.get(), SoundSource.BLOCKS, 0.55F, 1.0F);
            level.playSound(null, new BlockPos(origem.x(), origem.y(), origem.z()),
                    SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.25F, 1.4F);
        }
        if (beD != null) {
            beD.dispararPulso();
        }
        alvoLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, tx, ty + 1.0, tz,
                50, 0.6, 1.0, 0.6, 0.08);
        alvoLevel.sendParticles(ParticleTypes.PORTAL, tx, ty + 1.0, tz, 30, 0.8, 1.2, 0.8, 0.3);
        alvoLevel.playSound(null, new BlockPos(destino.x(), destino.y(), destino.z()),
                LorePortalsMod.SOM_PORTAL_ENTRADA.get(), SoundSource.BLOCKS, 0.5F, 1.25F);
        alvoLevel.playSound(null, new BlockPos(destino.x(), destino.y(), destino.z()),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.45F, 1.0F);
        player.teleportTo(alvoLevel, tx, ty, tz, player.getYRot(), player.getXRot());
        LorePortalsMod.LOG.info("Portal: jogador {} teleportado por portal em {}",
                player.getName().getString(), destino.key());
    }

    /** ANIMACAO DE ENTRADA (enquanto o teleporte carrega, ~10 ticks):
     * particulas giram e CONVERGEM no jogador + o portal "acorda" com um
     * som grave no primeiro tick. */
    private static void carregar(ServerLevel level, ServerPlayer player,
            long decorrido, long total) {
        float prog = Math.min(1.0F, (float) ((double) decorrido / (double) total));
        if (decorrido < 60L) {
            level.playSound(null, player.blockPosition(),
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.35F, 0.6F);
        }
        double base = (level.getGameTime() % 360L) * 0.55;
        double raio = 1.15 * (1.0 - prog);
        for (int i = 0; i < 4; i += 1) {
            double ang = Math.toRadians(base + i * 90.0);
            double px = player.getX() + Math.cos(ang) * raio;
            double py = player.getY() + 0.25 + prog * 1.5 + i * 0.12;
            double pz = player.getZ() + Math.sin(ang) * raio;
            level.sendParticles(ParticleTypes.PORTAL, px, py, pz, 1, 0.02, 0.02, 0.02, 0.0);
        }
        if (prog > 0.55F) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    2, 0.25, 0.5, 0.25, 0.05);
        }
    }

    private static ServerLevel serverLevel(net.minecraft.server.MinecraftServer server, String dim) {
        return LorePortalsMod.nivel(server, dim);
    }

    // ------------------------------------------------------------------
    // AMBIENTE
    // ------------------------------------------------------------------
    private static void ambiente(ServerLevel level, PortalBlockEntity be) {
        if (!be.renderAtivo) {
            return;
        }
        // partículas ambiente esporadicas + som de portal distante
        boolean molde = PortalBlockEntity.TIPO_MOLDE.equals(be.renderTipo);
        float cy = molde ? PortalBlockEntity.DISCO_Y : PortalBlockEntity.DISCO_Y_PAR;
        float raio = molde ? PortalBlockEntity.DISCO_RAIO_MOLDE : PortalBlockEntity.DISCO_RAIO_PAR;
        double ang = level.random.nextDouble() * Math.PI * 2;
        double rr = level.random.nextDouble() * raio * 0.85;
        double px = be.getBlockPos().getX() + 0.5 + Math.cos(ang) * rr;
        double py = be.getBlockPos().getY() + cy + (level.random.nextDouble() - 0.2) * (molde ? 3.4 : 1.4);
        double pz = be.getBlockPos().getZ() + 0.5 + Math.sin(ang) * rr;
        level.sendParticles(ParticleTypes.PORTAL, px, py, pz, 2, 0.05, 0.05, 0.05, 0.02);
        if (level.random.nextInt(140) == 0) {
            level.playSound(null, be.getBlockPos(), SoundEvents.PORTAL_AMBIENT,
                    SoundSource.BLOCKS, 0.5F, 0.8F);
        }
    }
}
