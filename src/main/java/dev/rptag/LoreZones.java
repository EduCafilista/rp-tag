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
            double radius, String title, String text, String sound) {

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
                    tag.getString("Sound"));
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

    public static void clearSeen(UUID playerId) {
        SEEN.remove(playerId);
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

            for (Zone zone : data.all()) {
                if (seen.contains(zone.id())) {
                    continue;
                }
                if (zone.dimension() != player.level().dimension()) {
                    continue;
                }
                if (player.position().distanceTo(zone.center()) > zone.radius()) {
                    continue;
                }
                seen.add(zone.id());
                show(player, zone);
                break; // uma zona por check
            }
        }
    }

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
