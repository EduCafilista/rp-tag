package dev.rptag;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * (5.78.17) COMPATIBILIDADE COM O MOD <b>Lootr</b> ({@code lootr}) — PEDIDO 159.
 *
 * <p>Pedido do dono: <i>"o jogador consegue pegar 2 loots no mod Lootr abrindo o baú em RP OFF e
 * depois em RP ON"</i> — escolhido <b>mesmo baú sempre (1 só por player)</b>.
 *
 * <h2>Por que acontecia</h2>
 * O Lootr ({@code DefaultLootrAPIImpl.handleProviderOpen} -&gt; {@code DataStorage.getInventory} /
 * {@code IOpeners.addOpener} / {@code IClientOpeners.hasClientOpened}) resolve o dono do inventário
 * chamando {@code LootrAPI.resolveServerPlayerTeam(player)} e
 * {@code LootrAPI.resolveClientPlayerTeam(player)}. Quando {@code team_loot = true} no
 * {@code lootr-common.toml}, o {@code MinecraftDefaultTeamResolver} usa o UUID do
 * {@code PlayerTeam} do Scoreboard em vez do {@code player.getUUID()}. Como alternar entre RP OFF e
 * RP ON (ou títulos/nametags ocultos) muda o estado de time no Scoreboard, o mesmo jogador recebia
 * 2 chaves de inventário diferentes — abrindo 2 loots no mesmo baú!
 *
 * <h2>O que fazemos</h2>
 * <ol>
 *   <li>Injetamos em {@code LootrServiceRegistry} (nos dois lados: servidor e cliente) um
 *       {@code ITeamResolver} dinâmico ({@code rptag:individual_uuid}, prioridade máxima) via
 *       {@link Proxy} que devolve <b>sempre {@code player.getUUID()}</b> em
 *       {@code resolveServerPlayer} e {@code resolveClientPlayer}, ignorando qualquer Scoreboard
 *       Team e qualquer {@code pinned_team_resolver}.</li>
 *   <li>Também desligamos {@code ConfigManager.TEAM_LOOT} em memória/config e reenviamos o
 *       {@code PacketSyncConfig} atualizado para clientes que entram, evitando até os pacotes
 *       extras de {@code MixinScoreboard.syncAfterTeamChange} quando alguém troca entre RP ON e
 *       RP OFF.</li>
 * </ol>
 *
 * <p>Tudo via reflexão protegida e {@link ModList#isLoaded}: compila e roda normalmente mesmo sem o
 * jar do Lootr instalado.
 */
public final class LootrCompat {

    public static final String MODID = "lootr";
    public static final ResourceLocation RESOLVER_ID =
            ResourceLocation.fromNamespaceAndPath(RPTagMod.MODID, "individual_uuid");

    private static Boolean instalado;
    private static volatile Object resolverProxy;
    private static volatile boolean logado;

    private LootrCompat() {
    }

    /** @return true se o mod {@code lootr} está carregado neste cliente/servidor. */
    public static boolean ativo() {
        if (instalado != null) {
            return instalado;
        }
        try {
            ModList lista = ModList.get();
            instalado = lista != null && lista.isLoaded(MODID);
        } catch (Throwable t) {
            instalado = false;
        }
        return instalado;
    }

    /**
     * Garante que o Lootr use SEMPRE o UUID individual da conta ({@link Player#getUUID()}), tanto
     * em RP ON quanto em RP OFF, no cliente e no servidor.
     *
     * @return {@code true} se a blindagem no Lootr está ativa e verificada.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean aplicar() {
        if (!ativo()) {
            return false;
        }
        boolean ok = false;
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = LootrCompat.class.getClassLoader();
            }
            Class<?> iface = Class.forName("noobanidus.mods.lootr.common.api.team.ITeamResolver", true, cl);
            Object proxy = resolverProxy;
            if (proxy == null) {
                proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
                        (inst, method, args) -> {
                            String nome = method.getName();
                            if ("resolveServerPlayer".equals(nome) || "resolveClientPlayer".equals(nome)) {
                                if (args != null && args.length > 0 && args[0] instanceof Player p) {
                                    return p.getUUID();
                                }
                                return null;
                            }
                            if ("resolverId".equals(nome)) {
                                return RESOLVER_ID;
                            }
                            if ("priority".equals(nome)) {
                                return Integer.MAX_VALUE;
                            }
                            if ("init".equals(nome)) {
                                return null;
                            }
                            if ("toString".equals(nome)) {
                                return "RPTagIndividualLootrResolver[" + RESOLVER_ID + "]";
                            }
                            if ("hashCode".equals(nome)) {
                                return System.identityHashCode(inst);
                            }
                            if ("equals".equals(nome)) {
                                return args != null && args.length > 0 && inst == args[0];
                            }
                            return null;
                        });
                resolverProxy = proxy;
            }

            Class<?> regCls = Class.forName("noobanidus.mods.lootr.common.impl.LootrServiceRegistry", true, cl);
            Method getInstance = regCls.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object reg = getInstance.invoke(null);

            Field mapField = regCls.getDeclaredField("teamResolvers");
            mapField.setAccessible(true);
            Object rawMap = mapField.get(reg);
            if (rawMap instanceof Map m) {
                m.put(RESOLVER_ID, proxy);
                // Também sobrescreve o resolver padrão caso alguém fixe minecraft:default no config
                m.put(ResourceLocation.withDefaultNamespace("default"), proxy);
            }

            Field keysField = regCls.getDeclaredField("sortedTeamKeys");
            keysField.setAccessible(true);
            Object rawKeys = keysField.get(reg);
            if (rawKeys instanceof List list) {
                list.remove(RESOLVER_ID);
                list.add(0, RESOLVER_ID);
            }

            Field cachedField = regCls.getDeclaredField("cachedTeamResolver");
            cachedField.setAccessible(true);
            cachedField.set(null, proxy);
            ok = true;
        } catch (Throwable t) {
            RPTagMod.LOGGER.warn("RP Tag: falha ao instalar ITeamResolver individual no Lootr", t);
        }

        // Segunda camada: desliga ConfigManager.TEAM_LOOT (evita pacotes desnecessários de team sync
        // e garante que LootrAPI.isTeamLoot() já devolva false direto na fonte).
        try {
            Class<?> cfgCls = Class.forName("noobanidus.mods.lootr.neoforge.config.ConfigManager");
            Field teamLootField = cfgCls.getDeclaredField("TEAM_LOOT");
            teamLootField.setAccessible(true);
            Object val = teamLootField.get(null);
            if (val instanceof ModConfigSpec.BooleanValue bv) {
                try {
                    if (Boolean.TRUE.equals(bv.get())) {
                        bv.set(false);
                        bv.save();
                    }
                } catch (IllegalStateException configAindaNaoCarregou) {
                    // Normal no construtor do mod antes do ModConfigEvent.Loading; reaplicamos depois.
                }
            }
        } catch (Throwable ignorado) {
        }

        // Terceira camada (cliente em servidor remoto): garante que SYNCED_CONFIG local também não
        // ligue teamLootEnabled caso o servidor já tenha mandado pacote antes.
        forcarSyncedConfigLocalSemTeam();

        if (ok && !logado) {
            logado = true;
            RPTagMod.LOGGER.info("RP Tag: protecao Lootr 1-loot-por-conta ativa ({}) — "
                    + "RP ON e RP OFF compartilham sempre o mesmo bau por UUID.", RESOLVER_ID);
        }
        return ok;
    }

    /**
     * Chamado quando um jogador entra no servidor: garante config aplicada e reenvia o
     * {@code PacketSyncConfig} do Lootr com {@code teamLootEnabled = false} para o cliente.
     */
    public static void aoEntrarJogador(ServerPlayer player) {
        if (player == null || !ativo()) {
            return;
        }
        aplicar();
        try {
            Class<?> cfgCls = Class.forName("noobanidus.mods.lootr.neoforge.config.ConfigManager");
            Method getSynced = cfgCls.getDeclaredMethod("getSyncedConfig");
            getSynced.setAccessible(true);
            Object synced = getSynced.invoke(null);

            Class<?> pktCls = Class.forName("noobanidus.mods.lootr.neoforge.network.toClient.PacketSyncConfig");
            Class<?> syncedCls = Class.forName("noobanidus.mods.lootr.common.api.config.SyncedConfig");
            Object pkt = pktCls.getConstructor(syncedCls).newInstance(synced);
            if (pkt instanceof net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        } catch (Throwable ignorado) {
        }
    }

    /** Resolve o UUID que o Lootr está usando no servidor para este player (útil para diagnóstico/testes). */
    public static UUID diagnosticarUuidServidor(Player player) {
        if (player == null) {
            return null;
        }
        if (!ativo()) {
            return player.getUUID();
        }
        try {
            Class<?> api = Class.forName("noobanidus.mods.lootr.common.api.LootrAPI");
            Method m = api.getMethod("resolveServerPlayerTeam", Player.class);
            Object r = m.invoke(null, player);
            if (r instanceof UUID u) {
                return u;
            }
        } catch (Throwable ignorado) {
        }
        return player.getUUID();
    }

    private static void forcarSyncedConfigLocalSemTeam() {
        try {
            Class<?> apiCls = Class.forName("noobanidus.mods.lootr.common.api.LootrAPI");
            Field syncedField = apiCls.getDeclaredField("SYNCED_CONFIG");
            syncedField.setAccessible(true);
            Object atual = syncedField.get(null);
            if (atual == null) {
                return;
            }
            Class<?> scCls = atual.getClass();
            Boolean teamLoot = (Boolean) scCls.getMethod("teamLootEnabled").invoke(atual);
            if (!Boolean.TRUE.equals(teamLoot)) {
                return;
            }
            boolean refresh = (Boolean) scCls.getMethod("displayRefreshParticles").invoke(atual);
            boolean decay = (Boolean) scCls.getMethod("displayDecayParticles").invoke(atual);
            boolean unopen = (Boolean) scCls.getMethod("displayUnopenedParticles").invoke(atual);
            boolean toasts = (Boolean) scCls.getMethod("displayToasts").invoke(atual);
            boolean blastResist = (Boolean) scCls.getMethod("blastResistant").invoke(atual);
            boolean blastImm = (Boolean) scCls.getMethod("blastImmune").invoke(atual);
            boolean destroy = (Boolean) scCls.getMethod("enablePlayerDestroy").invoke(atual);
            boolean creative = (Boolean) scCls.getMethod("enableCreativeBreak").invoke(atual);
            boolean breakDis = (Boolean) scCls.getMethod("disableBreak").invoke(atual);
            boolean fake = (Boolean) scCls.getMethod("enableFakePlayerBreak").invoke(atual);
            ResourceLocation pinned = (ResourceLocation) scCls.getMethod("pinnedTeamResolver").invoke(atual);

            Object novo = scCls.getConstructor(
                    boolean.class, boolean.class, boolean.class, boolean.class,
                    boolean.class, boolean.class, boolean.class, boolean.class,
                    boolean.class, boolean.class, boolean.class, ResourceLocation.class
            ).newInstance(refresh, decay, unopen, toasts, blastResist, blastImm,
                    destroy, creative, breakDis, fake, false, pinned);
            syncedField.set(null, novo);
        } catch (Throwable ignorado) {
        }
    }
}
