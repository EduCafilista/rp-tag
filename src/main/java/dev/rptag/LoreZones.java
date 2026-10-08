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
            String music, int musicInterval) {

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
                    tag.contains("MusicInterval") ? tag.getInt("MusicInterval") : 45);
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
            return tag;
        }

        public static LoreZonesData load(CompoundTag tag, HolderLookup.Provider registries) {
            LoreZonesData data = new LoreZonesData();
            ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                Zone z = Zone.load(list.getCompound(i));
                data.zones.put(z.id(), z);
            }
            return data;
        }
    }

    // ================================================================
    //  Disparo (tick do jogador)
    // ================================================================

    /** Zonas ja vistas por cada jogador nesta sessao (id da zona). */
    private static final Map<UUID, Set<String>> SEEN = new HashMap<>();

    /**
     * (3.50.0) Zonas em que o jogador esta AGORA. Quando ele SAI de uma zona,
     * o id sai daqui e do SEEN — saindo e voltando, o titulo/som disparam
     * DE NOVO (era o bug: o SEEN so crescia e a zona "morria" na 1a visita).
     */
    private static final Map<UUID, Set<String>> INSIDE = new HashMap<>();

    /** (3.48.0) estado da MUSICA de fundo por jogador (zona + ultimo play). */
    private record MusicState(String zoneId, long lastPlayedMs) {
    }

    private static final Map<UUID, MusicState> MUSIC = new HashMap<>();

    public static void clearSeen(UUID playerId) {
        SEEN.remove(playerId);
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
            Set<String> seen = SEEN.computeIfAbsent(uuid, k -> new HashSet<>());

            Zone insideMusic = null;
            Set<String> insideNow = new HashSet<>();
            for (Zone zone : data.all()) {
                if (zone.dimension() != player.level().dimension()) {
                    continue;
                }
                boolean inside = player.position().distanceTo(zone.center()) <= zone.radius();
                if (!inside) {
                    continue;
                }
                insideNow.add(zone.id());
                if (!seen.contains(zone.id())) {
                    seen.add(zone.id());
                    show(player, zone);
                }
                if (insideMusic == null && !zone.music().isEmpty()) {
                    insideMusic = zone; // primeira zona com musica contando
                }
            }
            // (3.50.0) SAIU de uma zona? esquece ela — entrar de novo mostra a
            // lore de novo (o "1x por sessao" agora e "1x por visita").
            Set<String> prev = INSIDE.get(uuid);
            if (prev != null) {
                for (String zid : prev) {
                    if (!insideNow.contains(zid)) {
                        seen.remove(zid);
                    }
                }
            }
            // (3.52.0) saiu da zona onde o som pontual tocou? para o som
            // (disco/ambiente longo nao fica ecoando atras do jogador)
            String[] somAndando = SOM_ATIVO.get(uuid);
            if (somAndando != null && !insideNow.contains(somAndando[0])) {
                pararSomPontual(player);
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
            if (inside == null) {
                if (st != null) {
                    // saiu da zona: para a musica com elegancia
                    if (st.zoneId().startsWith("@")) {
                        try {
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                    player, new LorezoneMusicPayload(false, ""));
                        } catch (Exception ignored) {
                        }
                    } else {
                        try {
                            player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                                    ResourceLocation.parse(MUSIC_SOUND.getOrDefault(uuid, "")),
                                    net.minecraft.sounds.SoundSource.MUSIC));
                        } catch (Exception ignored) {
                        }
                    }
                    MUSIC.remove(uuid);
                }
                return;
            }
            String music = inside.music();
            if (music.startsWith("@") || music.startsWith("http")) {
                // CUSTOM/URL: o cliente toca o .ogg (pasta ou LINK) em LOOP;
                // so mandamos PLAY 1x (na entrada ou ao trocar de zona) e STOP
                // na saida. (3.52.0) http://...ogg entra na mesma via.
                if (st == null || !st.zoneId().equals(inside.id())) {
                    // (3.52.0) trocando de musica vanilla pra custom? para a vanilla
                    String prevMus = MUSIC_SOUND.getOrDefault(uuid, "");
                    if (!prevMus.isEmpty()) {
                        try {
                            player.connection.send(new ClientboundStopSoundPacket(
                                    ResourceLocation.parse(prevMus), SoundSource.MUSIC));
                        } catch (Exception ignored) {
                        }
                        MUSIC_SOUND.remove(uuid);
                    }
                    if (music.startsWith("@")) {
                        // (3.55.0) SYNC: o SERVIDOR tem o arquivo (baixado com
                        // /lorezone baixar ou enviado pelo admin) e entrega pra
                        // quem nao tem — todo mundo ouve a mesma musica.
                        String nome = music.substring(1).replaceAll("[^a-zA-Z0-9_-]", "");
                        if (!nome.isEmpty()) {
                            MusicSync.pedir(player, nome);
                        }
                    } else {
                        // (3.52.0) URL direta: o proprio cliente baixa e toca
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                                player, new LorezoneMusicPayload(true, music));
                    }
                    MUSIC.put(uuid, new MusicState(inside.id(), System.currentTimeMillis()));
                }
                return;
            }
            long now = System.currentTimeMillis();
            // (3.55.0) intervalo -1 = toca 1x SÓ QUANDO ENTRAR na zona (sem loop)
            boolean umaVez = inside.musicInterval() == -1;
            int intervalMs = Math.max(10, Math.abs(inside.musicInterval())) * 1000;
            boolean hora = (st == null || !st.zoneId().equals(inside.id()))
                    || (!umaVez && now - st.lastPlayedMs() >= intervalMs);
            if (hora) {
                // (3.52.0) som REGISTRADO (BuiltInRegistries): e o mesmo caminho
                // do /playsound — o pacote sai com o holder certo e o som TOCA.
                // Antes usava um evento solto NAO-registrado e vários sons ficavam mudos.
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(music));
                if (sound == null) {
                    MUSIC.put(uuid, new MusicState(inside.id(), now)); // nao existe: para de tentar
                    return;
                }
                // (3.52.0) SUBSTITUI: para a musica anterior (re-disparo no
                // intervalo ou troca de zona) antes de tocar a nova — nunca empilha
                String prevMus = MUSIC_SOUND.getOrDefault(uuid, "");
                if (!prevMus.isEmpty()) {
                    try {
                        player.connection.send(new ClientboundStopSoundPacket(
                                ResourceLocation.parse(prevMus), SoundSource.MUSIC));
                    } catch (Exception ignored) {
                    }
                }
                player.level().playSound(null, player.blockPosition(), sound,
                        SoundSource.MUSIC, 0.7F, 1.0F);
                MUSIC.put(uuid, new MusicState(inside.id(), now));
                MUSIC_SOUND.put(uuid, music);
            }
        }
    }

    /** (3.48.0) o som atual de cada jogador (pro StopSound ao sair). */
    private static final Map<UUID, String> MUSIC_SOUND = new HashMap<>();

    /** Mostra a lore: titulo na tela + texto no chat + som. */
    private static void show(ServerPlayer player, Zone zone) {
        var connection = player.connection;
        connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(15, 70, 25));
        connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.literal("✦ " + zone.title() + " ✦").withStyle(net.minecraft.ChatFormatting.GOLD)));
        if (!zone.text().isEmpty()) {
            connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                    Component.literal(zone.text()).withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC)));
        }
        if (!zone.sound().isEmpty()) {
            tocarSom(player, zone);
        }
    }

    /** som pontual ativo por jogador: [zoneId, location] (pro StopSound). */
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
            SOM_ATIVO.put(player.getUUID(), new String[]{zone.id(), loc});
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
            String prevMus = MUSIC_SOUND.getOrDefault(uuid, "");
            if (!prevMus.isEmpty()) {
                try {
                    p.connection.send(new ClientboundStopSoundPacket(
                            ResourceLocation.parse(prevMus), SoundSource.MUSIC));
                } catch (Exception ignored) {
                }
            }
            try {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        p, new LorezoneMusicPayload(false, ""));
            } catch (Exception ignored) {
            }
            MUSIC.remove(uuid);
            MUSIC_SOUND.remove(uuid);
            pararSomPontual(p);
            if (!z.sound().isEmpty()) {
                tocarSom(p, z);
            }
        }
    }
}
