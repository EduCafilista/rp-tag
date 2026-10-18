package dev.loreportals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * O block entity do NUCLEO: guarda o estado do portal (ativo, tipo,
 * orientacao), valida o ANEL de molduras (formato especifico) e conversa
 * com o PortalData (vinculos persistidos).
 *
 * <p>Estado sincronizado pro cliente (render): renderAtivo, renderTipo,
 * renderFacing — o cliente so desenha o que o servidor autorizou.</p>
 */
public class PortalBlockEntity extends BlockEntity {

    /** Tipo do portal: PAR (nucleos conectados) ou MOLDE (anel de molduras). */
    public static final String TIPO_PAR = "PAR";
    public static final String TIPO_MOLDE = "MOLDE";

    /** Offset do ANEL (formato especifico): [du, dv] relativos ao nucleo.
     * 5 de largura, 5 de altura, cantos fechando o circulo. 11 molduras. */
    static final int[][] ANEL = {
            {-2, 0}, {2, 0},            // linha do nucleo (vao central = nucleo)
            {-2, 1}, {2, 1},
            {-2, 2}, {2, 2},
            {-2, 3}, {2, 3},
            {-1, 4}, {0, 4}, {1, 4},    // topo arredondado
    };
    /** Vao do portal: centro do disco (dv 0..4 -> discos cabem no anel). */
    public static final float DISCO_Y = 2.45F;
    public static final float DISCO_RAIO_MOLDE = 2.05F;
    public static final float DISCO_RAIO_PAR = 0.95F;
    public static final float DISCO_Y_PAR = 1.7F;

    // ---- estado do SERVIDOR ----
    private boolean ativo;
    private String tipo = "";
    private int facing;         // 0=+X, 1=+Z (plano do anel), so pro MOLDE
    private boolean validadoUmaVez;

    // ---- estado espelhado pro CLIENTE (render) ----
    public boolean renderAtivo;
    public String renderTipo = "";
    public int renderFacing;
    /** Geometria do disco medida no formato do jogador (0 = usa o padrao). */
    public float renderRx;
    public float renderRy;
    public float renderCy;
    public float renderCentroDu;

    // ---- geometria do disco (SERVIDOR, calculada na validacao) ----
    private float rx;
    private float ry;
    private float cy;
    private float centroDu;
    /** Animação de travessia: gameTime do instante do pulso (0 = nenhum). */
    private long pulsoViagem;
    public long renderPulso;

    public PortalBlockEntity(BlockPos pos, BlockState state) {
        super(LorePortalsMod.PORTAL_BE.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // CONSULTA
    // ------------------------------------------------------------------
    public boolean isAtivo() {
        return ativo;
    }

    /** Registra o nucleo como portal PAR solto (sem anel) no dado do mundo. */
    public void ativarParSolto(ServerLevel level) {
        PortalData data = PortalData.get(level.getServer());
        if (data.get(chave()) == null) {
            data.registrar(level, getBlockPos(), TIPO_PAR, facing);
        }
    }

    public boolean isLinked() {
        PortalData data = level != null && level.getServer() != null
                ? PortalData.get(level.getServer()) : null;
        if (data == null) {
            return false;
        }
        PortalData.Portal p = data.get(chave());
        return p != null && p.linkedTo() != null;
    }

    /** @return a chave deste portal ("dim|x|y|z"), ou null fora do servidor. */
    public String chave() {
        if (level == null) {
            return null;
        }
        return PortalData.Portal.key(level.dimension().location().toString(),
                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
    }

    // ------------------------------------------------------------------
    // ATIVACAO (formato especifico)
    // ------------------------------------------------------------------
    /**
     * Valida o anel 5x5 nas 4 orientacoes. Se fechar, ativa o portal
     * grande. Tambem chamado automaticamente pelo tick do mundo quando
     * uma moldura nova fecha o circulo.
     *
     * @return true se o portal ficou ativo
     */
    public boolean tentarAtivarPorMoldura() {
        if (level == null || level.isClientSide || ativo) {
            return ativo;
        }
        for (int f = 0; f < 4; f++) {
            if (geometriaDoFormato(f)) {
                ativar(TIPO_MOLDE, f);
                return true;
            }
        }
        return false;
    }

    /** @return true se as 11 molduras do anel estao no lugar nessa orientacao. */
    private boolean anelFechado(int facing) {
        for (int[] off : ANEL) {
            BlockPos p = worldPosition.offset(offset(facing, off[0], off[1]));
            BlockState s = level.getBlockState(p);
            if (!(s.getBlock() instanceof PortalFrameBlock)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Valida o FORMATO FECHADO nessa orientacao e JA calcula a geometria do
     * disco (rx/ry/cy/centroDu) — o disco se molda ao formato do jogador!
     * Aceita o anel classico 5x5 E qualquer outro formato fechado: um
     * flood-fill a partir do nucleo nao pode escapar — todas as bordas do
     * vao precisam ser de moldura (ou chao solido embaixo).
     */
    private boolean geometriaDoFormato(int facing) {
        if (anelFechado(facing)) {
            rx = DISCO_RAIO_MOLDE;
            ry = 2.05F;
            cy = DISCO_Y;
            centroDu = 0.0F;
            return true;
        }
        java.util.ArrayDeque<int[]> fila = new java.util.ArrayDeque<>();
        java.util.HashSet<Long> visto = new java.util.HashSet<>();
        fila.add(new int[]{0, 0});
        visto.add(0L);
        int abaixo = 0;
        int celulas = 0;
        int minL = 0;
        int maxR = 0;
        int maxTop = 0;
        while (!fila.isEmpty()) {
            int[] c = fila.poll();
            int[][] viz = {{c[0] - 1, c[1]}, {c[0] + 1, c[1]},
                    {c[0], c[1] - 1}, {c[0], c[1] + 1}};
            for (int[] nv : viz) {
                long k = (((long) nv[0]) << 20) | (nv[1] + 1024);
                if (!visto.add(k)) {
                    continue;
                }
                BlockPos bp = worldPosition.offset(offset(facing, nv[0], nv[1]));
                BlockState s = level.getBlockState(bp);
                boolean moldura = s.getBlock() instanceof PortalFrameBlock;
                boolean solida = !s.isAir() && !s.canBeReplaced();
                if (moldura || solida) {
                    if (moldura && nv[1] >= 0 && nv[1] <= 5 && nv[0] >= -5 && nv[0] <= 5) {
                        minL = Math.min(minL, nv[0]);
                        maxR = Math.max(maxR, nv[0]);
                        maxTop = Math.max(maxTop, nv[1]);
                    }
                    continue;
                }
                // vazio: escapou pro lado ou pro ceu = formato aberto
                if (nv[0] < -4 || nv[0] > 4 || nv[1] > 4) {
                    return false;
                }
                if (nv[1] < 0) {
                    abaixo++;
                    if (abaixo > 10) {
                        return false;
                    }
                    continue;
                }
                if (++celulas > 40) {
                    return false;
                }
                fila.add(nv);
            }
        }
        if (minL >= 0 || maxR <= 0 || maxTop < 2) {
            return false;
        }
        centroDu = (minL + maxR) / 2.0F;
        rx = Math.min((maxR - minL) / 2.0F, 3.4F);
        cy = Math.min((maxTop + 1) / 2.0F, 3.4F);
        ry = Math.min(cy, 3.2F);
        return true;
    }

    /** Converte (du, dv) do plano do anel em offset de bloco na orientacao. */
    public static BlockPos offset(int facing, int du, int dv) {
        return switch (facing) {
            case 0 -> new BlockPos(du, dv, 0);   // plano X (normal +Z)
            case 1 -> new BlockPos(0, dv, du);   // plano Z (normal +X)
            case 2 -> new BlockPos(-du, dv, 0);
            default -> new BlockPos(0, dv, -du);
        };
    }

    private void ativar(String tipo, int facing) {
        this.ativo = true;
        this.tipo = tipo;
        this.facing = facing;
        ServerLevel sl = (ServerLevel) level;
        PortalData.get(sl.getServer()).registrar(sl, worldPosition, tipo, facing);
        syncCliente();
        setChanged();
        // efeito de despertar
        double cx = worldPosition.getX() + 0.5;
        double cy = worldPosition.getY() + (TIPO_MOLDE.equals(tipo) ? DISCO_Y : DISCO_Y_PAR);
        double cz = worldPosition.getZ() + 0.5;
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                cx, cy, cz, 60, 1.2, 1.6, 1.2, 0.35);
        sl.playSound(null, worldPosition, SoundEvents.END_PORTAL_FRAME_FILL,
                SoundSource.BLOCKS, 1.2F, 0.7F);
        LorePortalsMod.LOG.info("Portal {} ATIVADO em {} (facing {})", tipo, chave(), facing);
    }

    /** Quebrou o nucleo: sai do registro e deixa o parceiro orfao. */
    public void desativarERemover() {
        if (level == null || level.isClientSide) {
            return;
        }
        ServerLevel sl = (ServerLevel) level;
        String key = chave();
        PortalData.get(sl.getServer()).remover(key);
        if (ativo) {
            LorePortalsMod.LOG.info("Portal REMOVIDO em {}", key);
        }
        ativo = false;
        tipo = "";
        syncCliente();
        avisarParceiro();
    }

    /** Moldura quebrada: o anel abriu — portal dorme (mantem o vinculo). */
    public void revalidarAnel() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!ativo) {
            // anel pode ter sido FECHADO de novo: tenta acordar
            tentarAtivarPorMoldura();
            return;
        }
        if (TIPO_MOLDE.equals(tipo) && !geometriaDoFormato(facing)) {
            ativo = false;
            syncCliente();
            setChanged();
            level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.BLOCKS, 0.9F, 0.6F);
            LorePortalsMod.LOG.info("Portal DESATIVADO (formato aberto) em {}", chave());
        } else if (TIPO_MOLDE.equals(tipo)
                && (renderRx != rx || renderRy != ry || renderCy != cy
                    || renderCentroDu != centroDu)) {
            syncCliente(); // o formato mudou (jogador ampliou) — atualiza o disco
        }
    }

    /** Animação de travessia: onda de choque no disco (chamado no teleporte,
     * no portal de ORIGEM e no DESTINO). */
    public void dispararPulso() {
        if (level == null || level.isClientSide) {
            return;
        }
        pulsoViagem = level.getGameTime();
        syncCliente();
    }

    /** CLIENTE: revalida o formato no próprio cliente a cada intervalo —
     * quebrou a moldura, o portal SOME NA HORA (e se reerguer, VOLTA) sem
     * esperar a rede; editar a moldura reajusta o disco AO VIVO. */
    public void revalidarVisualCliente() {
        if (level == null || !level.isClientSide || !TIPO_MOLDE.equals(tipo)) {
            return;
        }
        renderAtivo = geometriaDoFormato(facing);
        renderTipo = tipo;
        renderFacing = facing;
        renderRx = rx;
        renderRy = ry;
        renderCy = cy;
        renderCentroDu = centroDu;
    }

    /** Vinculo criado/quebrado: atualiza o render do cliente. */
    public void atualizarDoVinculo() {
        if (level == null || level.isClientSide) {
            return;
        }
        PortalData.Portal p = PortalData.get(level.getServer()).get(chave());
        if (p == null) {
            return;
        }
        if (!ativo && TIPO_MOLDE.equals(p.tipo())) {
            // revalida pelo anel (portal grande volta sozinho se o anel estiver la)
            tentarAtivarPorMoldura();
            return;
        }
        if (TIPO_PAR.equals(p.tipo()) && p.linkedTo() != null && !ativo) {
            ativar(TIPO_PAR, p.facing());
            return;
        }
        syncCliente();
        setChanged();
    }

    /** Forca o parceiro a se atualizar (vinculo caiu). */
    private void avisarParceiro() {
        PortalData data = PortalData.get(level.getServer());
        PortalData.Portal p = data.get(chave());
        if (p == null || p.linkedTo() == null) {
            return;
        }
        PortalData.Portal parceiro = data.get(p.linkedTo());
        if (parceiro == null) {
            return;
        }
        ServerLevel sl = LorePortalsMod.nivel(level.getServer(), parceiro.dim());
        if (sl != null && sl.getBlockEntity(new BlockPos(parceiro.x(), parceiro.y(), parceiro.z()))
                instanceof PortalBlockEntity be) {
            be.atualizarDoVinculo();
        }
    }

    // ------------------------------------------------------------------
    // SYNC cliente
    // ------------------------------------------------------------------
    private void syncCliente() {
        renderAtivo = ativo;
        renderTipo = tipo;
        renderFacing = facing;
        renderRx = rx;
        renderRy = ry;
        renderCy = cy;
        renderCentroDu = centroDu;
        renderPulso = pulsoViagem;
        if (level instanceof ServerLevel sl) {
            sl.getServer().execute(() ->
                    sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("ativo", ativo);
        tag.putString("tipo", tipo);
        tag.putInt("facing", facing);
        tag.putFloat("rx", rx);
        tag.putFloat("ry", ry);
        tag.putFloat("cy", cy);
        tag.putFloat("cdu", centroDu);
        tag.putLong("pul", pulsoViagem);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ativo = tag.getBoolean("ativo");
        tipo = tag.getString("tipo");
        facing = tag.getInt("facing");
        rx = tag.getFloat("rx");
        ry = tag.getFloat("ry");
        cy = tag.getFloat("cy");
        centroDu = tag.getFloat("cdu");
        pulsoViagem = tag.getLong("pul");
        renderPulso = pulsoViagem;
        validadoUmaVez = false;
        renderAtivo = ativo;
        renderTipo = tipo;
        renderFacing = facing;
        renderRx = rx;
        renderRy = ry;
        renderCy = cy;
        renderCentroDu = centroDu;
    }

    /** Procura nucleos num raio (moldura colocada/quebrada perto). */
    public static List<PortalBlockEntity> nucleosProximos(Level level, BlockPos centro, int raio) {
        List<PortalBlockEntity> achados = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(centro.offset(-raio, -raio, -raio),
                centro.offset(raio, raio, raio))) {
            if (level.getBlockEntity(p) instanceof PortalBlockEntity be) {
                achados.add(be);
            }
        }
        return achados;
    }

    /** Mensagem de ajuda reusavel (o import de Player fica nos chamadores). */
}
