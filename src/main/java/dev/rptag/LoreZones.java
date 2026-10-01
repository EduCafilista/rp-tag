package dev.rptag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
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
            tag.putInt("MusicInterval", musicInterval <= 0 ? 45 : musicInterval);
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

    /** (3.48.0) estado da MUSICA de fundo por jogador (zona + ultimo play). */
    private record MusicState(String zoneId, long lastPlayedMs) {
    }

    private static final Map<UUID, MusicState> MUSIC = new HashMap<>();

    public static void clearSeen(UUID playerId) {
        SEEN.remove(playerId);
        MUSIC.remove(playerId);
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
            Set<String> seen = SEEN.computeIfAbsent(player.getUUID(), k -> new HashSet<>());

            Zone insideMusic = null;
            for (Zone zone : data.all()) {
                if (zone.dimension() != player.level().dimension()) {
                    continue;
                }
                boolean inside = player.position().distanceTo(zone.center()) <= zone.radius();
                if (!inside) {
                    continue;
                }
                if (!seen.contains(zone.id())) {
                    seen.add(zone.id());
                    show(player, zone);
                }
                if (insideMusic == null && !zone.music().isEmpty()) {
                    insideMusic = zone; // primeira zona com musica contando
                }
            }
            tickMusic(player, insideMusic);
        }

        /**
         * (3.48.0) MUSICA DE FUNDO em loop enquanto o jogador esta na zona
         * (re-dispara a cada intervalo) e PARA ao sair (StopSound).
         */
        private static void tickMusic(ServerPlayer player, Zone inside) {
            UUID uuid = player.getUUID();
            MusicState st = MUSIC.get(uuid);
            if (inside == null) {
                if (st != null) {
                    // saiu da zona: para a musica com elegancia
                    try {
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                                ResourceLocation.parse(st.zoneId().isEmpty() ? "" : MUSIC_SOUND.get(uuid)),
                                net.minecraft.sounds.SoundSource.MUSIC));
                    } catch (Exception ignored) {
                    }
                    MUSIC.remove(uuid);
                }
                return;
            }
            long now = System.currentTimeMillis();
            int intervalMs = Math.max(10, inside.musicInterval()) * 1000;
            if (st == null || !st.zoneId().equals(inside.id()) || now - st.lastPlayedMs() >= intervalMs) {
                try {
                    ResourceLocation rl = ResourceLocation.parse(inside.music());
                    net.minecraft.sounds.SoundEvent sound = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(rl);
                    player.level().playSound(null, player.blockPosition(), sound,
                            net.minecraft.sounds.SoundSource.MUSIC, 0.7F, 1.0F);
                } catch (Exception ignored) {
                    return; // som invalido: tenta de novo no proximo ciclo
                }
                MUSIC.put(uuid, new MusicState(inside.id(), now));
                MUSIC_SOUND.put(uuid, inside.music());
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
            try {
                ResourceLocation rl = ResourceLocation.parse(zone.sound());
                net.minecraft.sounds.SoundEvent sound = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(rl);
                player.level().playSound(null, player.blockPosition(), sound,
                        net.minecraft.sounds.SoundSource.AMBIENT, 0.8F, 1.0F);
            } catch (Exception ignored) {
                // som invalido: ignora sem quebrar
            }
        }
    }
}
