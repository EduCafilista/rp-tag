package dev.rptag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Lore Zones — regioes que contam a historia do mundo.
 *
 * <p>Quando um jogador em RP entra na area, recebe um titulo na tela,
 * texto de lore no chat e (opcionalmente) um som — uma vez por sessao.
 * Administradores criam as zonas com {@code /lorezone}.
 */
public final class LoreZones {

    /** Uma regiao esferica com historia. */
    public record Zone(String id, ResourceKey<Level> dimension, Vec3 center,
            double radius, String title, String text, String sound,
            String music, int musicInterval,
            String aviso, int avisoEstilo, int avisoSeg,
            double minY, double maxY) {

        /** (4.0.0) a zona tem faixa vertical limitada (ex.: so a caverna)? */
        public boolean faixaVertical() {
            return maxY - minY < 2 * radius - 0.001;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Id", id);
            tag.putString("Dim", dimension.location().toString());
            tag.putDouble("X", center.x);
            tag.putDouble("Y", center.y);
            tag.putDouble("Z", center.z);
            tag.putDouble("Radius", radius);
            tag.putString("Title", title);
            tag.putString("Text", text);
            tag.putString("Sound", sound == null ? "" : sound);
            tag.putString("Music", music == null ? "" : music);
            // (3.55.0) -1 = toca 1x na entrada (preserva!); 0/ausente = 45s
            tag.putInt("MusicInterval", musicInterval == 0 ? 45 : musicInterval);
            // (3.85.0) AVISO ao entrar (estilos do /rp admin avisar);
            // aviso vazio = desligado (zona usa o titulo classico)
            tag.putString("Aviso", aviso == null ? "" : aviso);
            tag.putInt("AvisoEstilo", avisoEstilo);
            tag.putInt("AvisoSeg", avisoSeg == 0 ? 8 : avisoSeg);
            // (4.0.0) faixa vertical (minY/maxY) — ex.: so a caverna
            tag.putDouble("MinY", minY);
            tag.putDouble("MaxY", maxY);
            return tag;
        }

        public static Zone load(CompoundTag tag) {
            return new Zone(
                    tag.getString("Id"),
                    ResourceKey.create(Registries.DIMENSION,
                            ResourceLocation.parse(tag.getString("Dim"))),
                    new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                    tag.getDouble("Radius"),
                    tag.getString("Title"),
                    tag.getString("Text"),
                    tag.getString("Sound"),
                    tag.contains("Music") ? tag.getString("Music") : "",
                    tag.contains("MusicInterval") ? tag.getInt("MusicInterval") : 45,
                    // (3.85.0) zonas antigas: sem aviso = titulo classico
                    tag.contains("Aviso") ? tag.getString("Aviso") : "",
                    Math.max(0, Math.min(11, tag.getInt("AvisoEstilo"))),
                    tag.contains("AvisoSeg") && tag.getInt("AvisoSeg") > 0
                            ? tag.getInt("AvisoSeg") : 8,
                    // (4.0.0) zonas antigas: sem faixa = esfera inteira
                    tag.contains("MinY") ? tag.getDouble("MinY") : tag.getDouble("Y") - tag.getDouble("Radius"),
                    tag.contains("MaxY") ? tag.getDouble("MaxY") : tag.getDouble("Y") + tag.getDouble("Radius"));
        }
    }

    private LoreZones() {
    }

    // ================================================================
    //  Armazenamento (SavedData)
    // ================================================================

    public static final class LoreZonesData extends net.minecraft.world.level.saveddata.SavedData {

        public static final String DATA_NAME = "rptag_lorezones";
        private final Map<String, Zone> zones = new HashMap<>();

        /**
         * (3.99.0) AVISOS JA VISTOS: jogador -> zonas cujo aviso ja apareceu.
         * E PERSISTENTE (vai pro mundo): entrou uma vez, nao aparece nunca
         * mais para aquele jogador — nem saindo e voltando, nem relogando.
         */
        private final Map<UUID, Set<String>> avisosVistos = new HashMap<>();

        public boolean avisoVisto(UUID jogador, String zonaId) {
            Set<String> vistos = avisosVistos.get(jogador);
            return vistos != null && vistos.contains(zonaId);
        }

        public void marcarAvisoVisto(UUID jogador, String zonaId) {
            avisosVistos.computeIfAbsent(jogador, k -> new HashSet<>()).add(zonaId);
            setDirty();
        }

        /** (3.99.0) admin quer que um jogador veja o aviso de novo? */
        public int resetAvisos(UUID jogador) {
            Set<String> vistos = avisosVistos.remove(jogador);
            setDirty();
            return vistos == null ? 0 : vistos.size();
        }

        public static LoreZonesData get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new net.minecraft.world.level.saveddata.SavedData.Factory<>(
                            LoreZonesData::new, LoreZonesData::load, null),
                    DATA_NAME);
        }

        public void put(Zone zone) {
            zones.put(zone.id(), zone);
            setDirty();
        }

        public boolean remove(String id) {
            boolean removed = zones.remove(id) != null;
            if (removed) {
                setDirty();
            }
            return removed;
        }

        public Zone get(String id) {
            return zones.get(id);
        }

        public List<Zone> all() {
            return List.copyOf(zones.values());
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            zones.values().forEach(z -> list.add(z.save()));
            tag.put("Zones", list);
            // (3.99.0) avisos vistos (persistente)
            CompoundTag vistos = new CompoundTag();
            avisosVistos.forEach((u, ids) -> {
                ListTag l = new ListTag();
                ids.forEach(id -> l.add(net.minecraft.nbt.StringTag.valueOf(id)));
                vistos.put(u.toString(), l);
            });
            tag.put("AvisosVistos", vistos);
            return tag;
        }

        public static LoreZonesData load(CompoundTag tag, HolderLookup.Provider registries) {
            LoreZonesData data = new LoreZonesData();
            ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Zone z = Zone.load(list.getCompound(i));
                data.zones.put(z.id(), z);
            }
            CompoundTag vistos = tag.getCompound("AvisosVistos");
            for (String chave : vistos.getAllKeys()) {
                try {
                    Set<String> ids = new HashSet<>();
                    ListTag l = vistos.getList(chave, Tag.TAG_STRING);
                    for (int i = 0; i < l.size(); i++) {
                        ids.add(l.getString(i));
                    }
                    data.avisosVistos.put(UUID.fromString(chave), ids);
                } catch (Exception ignored) {
                    // uuid corrompido: ignora
                }
            }
            return data;
        }
    }

    // ================================================================
    //  Disparo (tick do jogador)
    // ================================================================

    /**
     * (3.99.0) O aviso agora e UNICO E PERMANENTE: fica gravado no mundo
     * (LoreZonesData.avisosVistos). Entrou uma vez, viu o aviso — chega.
     * Sair e voltar (ou relogar) NAO mostra de novo.
     */

    /**
     * (3.50.0) Zonas em que o jogador esta AGORA (so pra saber quando ele
     * ENTROU de novo — o som pontual toca a cada entrada).
     */
    private static final Map<UUID, Set<String>> INSIDE = new HashMap<>();

    /** (3.99.0) estado da MUSICA por jogador: zona + inicio + se ja foi parada no limite. */
    private record MusicState(String zoneId, long startedMs, boolean silenced) {
    }

    /** (3.99.0) tempo MAXIMO padrao de musica numa lorezone (segundos). */
    public static final int MUSIC_MAX_PADRAO_SEG = 60;

    /** (3.99.0) tempo MAXIMO do som pontual de entrada (segundos). */
    private static final long SOM_MAX_MS = 30_000L;

    private static final Map<UUID, MusicState> MUSIC = new HashMap<>();

    /** (3.99.0) limpa so o estado da SESSAO — os avisos vistos ficam salvos. */
    public static void clearSeen(UUID playerId) {
        INSIDE.remove(playerId);
        MUSIC.remove(playerId);
        SOM_ATIVO.remove(playerId);
    }

    @EventBusSubscriber(modid = RPTagMod.MODID)
    public static final class Trigger {

        private Trigger() {
        }

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return; // so dispara no servidor
            }
            if (player.tickCount % 20 != 0) {
                return; // checa 1x por segundo
            }
            LoreZonesData data = LoreZonesData.get(player.server);
            if (data.all().isEmpty()) {
                return;
            }
            UUID uuid = player.getUUID();
            Set<String> prev = INSIDE.getOrDefault(uuid, java.util.Set.of());

            Zone insideMusic = null;
            Set<String> insideNow = new HashSet<>();
            for (Zone zone : data.all()) {
                if (zone.dimension() != player.level().dimension()) {
                    continue;
                }
                // (4.0.0) dentro da esfera E dentro da faixa vertical
                // (ex.: evento na caverna nao dispara la no topo do mundo)
                double py = player.position().y;
                boolean inside = player.position().distanceTo(zone.center()) <= zone.radius()
                        && py >= zone.minY() && py <= zone.maxY();
                if (!inside) {
                    continue;
                }
                insideNow.add(zone.id());
                boolean entrouAgora = !prev.contains(zone.id());
                if (entrouAgora) {
                    // (3.99.0) AVISO: uma vez POR JOGADOR, PARA SEMPRE.
                    // (sair e voltar nao mostra de novo — nem relogando)
                    if (!data.avisoVisto(uuid, zone.id())) {
                        data.marcarAvisoVisto(uuid, zone.id());
                        show(player, zone);
                    }
                    // (3.99.0) SOM pontual: toca a CADA entrada, mas com
                    // limite de tempo (nao vira ambiencia eterna)
                    if (!zone.sound().isEmpty()) {
                        tocarSom(player, zone);
                    }
                }
                if (insideMusic == null && !zone.music().isEmpty()) {
                    insideMusic = zone; // primeira zona com musica contando
                }
            }
            // (3.52.0) saiu da zona onde o som pontual tocou? para o som
            String[] somAndando = SOM_ATIVO.get(uuid);
            if (somAndando != null) {
                boolean saiu = !insideNow.contains(somAndando[0]);
                boolean venceu = false;
                try {
                    venceu = System.currentTimeMillis() - Long.parseLong(somAndando[2]) >= SOM_MAX_MS;
                } catch (Exception ignored) {
                }
                if (saiu || venceu) {
                    pararSomPontual(player);
                }
            }
            INSIDE.put(uuid, insideNow);
            tickMusic(player, insideMusic);
        }

        /**
         * (3.48.0) MUSICA DE FUNDO em loop enquanto o jogador esta na zona
         * (re-dispara a cada intervalo) e PARA ao sair (StopSound).
         * (3.50.0) musicas CUSTOM (@arquivo) sao tocadas pelo CLIENTE
         * (arquivo .ogg na pasta config/rptag/musicas) em LOOP nativo, com
         * payload proprio PLAY/STOP — o servidor nao precisa re-disparar.
         */
        private static void tickMusic(ServerPlayer player, Zone inside) {
            UUID uuid = player.getUUID();
            MusicState st = MUSIC.get(uuid);
            if (inside == null || inside.music().isEmpty()) {
                // (3.99.0) so manda "parar" se havia algo tocando (sem spam)
                if (MUSIC.containsKey(uuid) || MUSIC_SOUND.containsKey(uuid)) {
                    stopMusic(player, uuid);
                }
                return;
            }
            // (3.99.0) TEMPO MAXIMO: a musica NUNCA toca para sempre. O
            // campo "intervalo" virou o limite (1x/-1 = 60s de limite).
            // -2 = musica UNICA: so toca na PRIMEIRA entrada da vida.
            int maxSeg = inside.musicInterval() > 0
                    ? Math.min(600, inside.musicInterval()) : MUSIC_MAX_PADRAO_SEG;
            long maxMs = maxSeg * 1000L;
            long now = System.currentTimeMillis();

            if (st != null && st.zoneId().equals(inside.id())) {
                if (st.silenced()) {
                    return; // ja cumpriu o tempo maximo: silencio ate sair
                }
                if (now - st.startedMs() >= maxMs) {
                    // (3.99.0) atingiu o limite: PARA e nao reinicia ate o
                    // jogador sair e entrar de novo
                    stopMusic(player, uuid);
                    MUSIC.put(uuid, new MusicState(inside.id(), st.startedMs(), true));
                }
                return;
            }

            // entrou na zona (ou trocou de zona): toca do comeco
            stopMusic(player, uuid);
            // (3.99.0) musica UNICA (-2): so na primeira entrada da vida
            if (inside.musicInterval() == -2) {
                LoreZonesData data = LoreZonesData.get(player.server);
                String chave = "m:" + inside.id();
                if (data.avisoVisto(uuid, chave)) {
                    MUSIC.put(uuid, new MusicState(inside.id(), now, true));
                    return; // ja tocou uma vez na vida: silencio
                }
                data.marcarAvisoVisto(uuid, chave);
            }
            if (inside.music().startsWith("@")) {
                // (3.55.0) SYNC: o SERVIDOR tem o arquivo e entrega pra quem
                // nao tem — todo mundo ouve a mesma musica
                String nome = inside.music().substring(1).replaceAll("[^a-zA-Z0-9_-]", "");
                if (!nome.isEmpty()) {
                    MusicSync.pedir(player, nome, maxSeg);
                }
            } else if (inside.music().startsWith("http")) {
                // URL direta: o proprio cliente baixa e toca
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        player, new LorezoneMusicPayload(true, inside.music(), maxSeg));
            } else {
                // som REGISTRADO (BuiltInRegistries): mesmo caminho do /playsound
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(inside.music()));
                if (sound == null) {
                    MUSIC.put(uuid, new MusicState(inside.id(), now, true));
                    return;
                }
                player.level().playSound(null, player.blockPosition(), sound,
                        SoundSource.MUSIC, 0.7F, 1.0F);
                MUSIC_SOUND.put(uuid, inside.music());
            }
            MUSIC.put(uuid, new MusicState(inside.id(), now, false));
        }

        /** (3.99.0) para a musica atual (vanilla OU custom) do jogador. */
        private static void stopMusic(ServerPlayer player, UUID uuid) {
            String prevMus = MUSIC_SOUND.remove(uuid);
            if (prevMus != null && !prevMus.isEmpty()) {
                try {
                    player.connection.send(new ClientboundStopSoundPacket(
                            ResourceLocation.parse(prevMus), SoundSource.MUSIC));
                } catch (Exception ignored) {
                }
            }
            try {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        player, new LorezoneMusicPayload(false, "", 0));
            } catch (Exception ignored) {
            }
            MUSIC.remove(uuid);
        }
    }

    /** (3.48.0) o som atual de cada jogador (pro StopSound ao sair). */
    private static final Map<UUID, String> MUSIC_SOUND = new HashMap<>();

    /**
     * Mostra a lore: titulo na tela + texto no chat + som.
     * (3.85.0) zona com AVISO configurado? O aviso cinematografico (os
     * mesmos estilos do /rp admin avisar: classico, pesadelo, runico,
     * real, morte) substitui o titulo classico — e o som continua.
     */
    private static void show(ServerPlayer player, Zone zone) {
        var connection = player.connection;
        if (zone.aviso() != null && !zone.aviso().isEmpty()) {
            int estilo = Math.max(0, Math.min(11, zone.avisoEstilo()));
            int seg = Math.max(1, Math.min(60,
                    zone.avisoSeg() == 0 ? 8 : zone.avisoSeg()));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new ScreenTextPayload(zone.aviso(), seg, estilo));
            // (3.99.0) o SOM nao toca mais aqui: ele toca a CADA entrada
            // (com limite de tempo), independente do aviso ser unico
            return;
        }
        connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(15, 70, 25));
        connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.literal("✦ " + zone.title() + " ✦").withStyle(net.minecraft.ChatFormatting.GOLD)));
        if (!zone.text().isEmpty()) {
            connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    Component.literal(zone.text()).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC)));
        }
    }

    /** som pontual ativo por jogador: [zoneId, location, inicioMs] (pro StopSound). */
    private static final Map<UUID, String[]> SOM_ATIVO = new HashMap<>();

    /**
     * (3.52.0) toca o som pontual da zona: som REGISTRADO do jogo
     * (BuiltInRegistries — mesmo caminho do /playsound, funciona de verdade;
     * o evento solto NAO-registrado deixava caverna/alma/coracao MUDOS),
     * volume cheio, e PARANDO o som pontual anterior (nunca empilha).
     */
    private static void tocarSom(ServerPlayer player, Zone zone) {
        String loc = zone.sound();
        if (loc.isEmpty()) {
            return;
        }
        try {
            ResourceLocation rl = ResourceLocation.parse(loc);
            SoundEvent som = BuiltInRegistries.SOUND_EVENT.get(rl);
            if (som == null) {
                return; // inexistente: o /lorezone som agora valida e avisa o admin
            }
            pararSomPontual(player);
            player.level().playSound(null, player.blockPosition(), som,
                    SoundSource.AMBIENT, 1.0F, 1.0F);
            SOM_ATIVO.put(player.getUUID(),
                    new String[]{zone.id(), loc, Long.toString(System.currentTimeMillis())});
        } catch (Exception ignored) {
            // som invalido: ignora sem quebrar
        }
    }

    /** (3.52.0) para o som pontual ANTERIOR deste jogador (substituicao). */
    private static void pararSomPontual(ServerPlayer player) {
        String[] sa = SOM_ATIVO.remove(player.getUUID());
        if (sa != null && sa[1] != null && !sa[1].isEmpty()) {
            try {
                player.connection.send(new ClientboundStopSoundPacket(
                        ResourceLocation.parse(sa[1]), SoundSource.AMBIENT));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * (3.52.0) admin trocou o som/musica da zona? quem JA ESTA DENTRO recebe
     * o novo NA HORA — para o antigo e dispara o novo (substitui, sem esperar
     * sair e voltar).
     */
    public static void atualizarSons(MinecraftServer server, Zone z) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() != z.dimension()
                    || p.position().distanceTo(z.center()) > z.radius()) {
                continue;
            }
            UUID uuid = p.getUUID();
            // (3.99.0) para tudo e deixa o tick re-disparar a musica NOVA
            Trigger.stopMusic(p, uuid);
            pararSomPontual(p);
            if (!z.sound().isEmpty()) {
                tocarSom(p, z);
            }
        }
    }
}
