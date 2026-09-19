package com.jarvis.server;

import com.jarvis.navigation.LocationQuery;
import com.jarvis.skills.WorldAccess;
import com.jarvis.world.BlockInfo;
import com.jarvis.world.EntityInfo;
import com.jarvis.world.LocatedStructure;
import com.jarvis.world.OreHit;
import com.jarvis.world.PlayerSnapshot;
import com.jarvis.world.SpatialReasoning;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * {@link WorldAccess} implemented against NeoForge 1.21.1. Every level access
 * runs on the server thread (marshalled with a bounded wait when called from
 * AI threads); pure data crosses back as DTOs.
 */
public final class NeoForgeWorldAccess implements WorldAccess {
    private static final Map<String, List<String>> BIOME_HINTS = Map.ofEntries(
        Map.entry("desert", List.of("desert")),
        Map.entry("plains", List.of("plains")),
        Map.entry("forest", List.of("forest", "birch", "dark_forest", "flower_forest")),
        Map.entry("jungle", List.of("jungle", "bamboo")),
        Map.entry("savanna", List.of("savanna")),
        Map.entry("taiga", List.of("taiga", "grove")),
        Map.entry("swamp", List.of("swamp", "mangrove")),
        Map.entry("ocean", List.of("ocean", "deep_ocean")),
        Map.entry("mountains", List.of("windswept", "stony_peaks", "jagged", "frozen_peaks", "meadow", "grove", "snowy_slopes")),
        Map.entry("badlands", List.of("badlands", "wooded_badlands")),
        Map.entry("mushroom", List.of("mushroom")),
        Map.entry("lush", List.of("lush_caves")),
        Map.entry("dripstone", List.of("dripstone_caves")),
        Map.entry("nether", List.of("nether_wastes", "crimson", "warped", "basalt", "soul_sand")),
        Map.entry("end", List.of("end_highlands", "end_midlands", "end_barrens", "the_end")));

    private MinecraftServer server() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    private ServerPlayer player(String playerId) {
        MinecraftServer s = server();
        if (s == null) return null;
        try {
            return s.getPlayerList().getPlayer(UUID.fromString(playerId));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Run on the server thread, waiting at most ~10s from foreign threads. */
    private <T> T onServer(Callable<T> task) {
        MinecraftServer s = server();
        if (s == null) return null;
        try {
            if (s.isSameThread()) {
                return task.call();
            }
            FutureTask<T> ft = new FutureTask<>(task);
            s.execute(ft);
            return ft.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public PlayerSnapshot snapshot(String playerId) {
        return onServer(() -> {
            ServerPlayer p = player(playerId);
            if (p == null) return null;
            ServerLevel level = p.serverLevel();
            BlockPos pos = p.blockPosition();
            String biome = level.getBiome(pos).unwrapKey()
                .map(k -> k.location().toString()).orElse("unknown");
            String weather = level.isRaining() ? (level.isThundering() ? "thunder" : "rain") : "clear";
            int light = 0;
            try {
                light = level.getMaxLocalRawBrightness(pos);
            } catch (Exception ignored) {}
            return new PlayerSnapshot(playerId, p.getGameProfile().getName(),
                p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(),
                level.dimension().location().toString(), biome,
                level.getDayTime(), weather, light);
        });
    }

    @Override
    public List<EntityInfo> nearbyEntities(String playerId, double radius) {
        List<EntityInfo> out = onServer(() -> {
            List<EntityInfo> list = new ArrayList<>();
            ServerPlayer p = player(playerId);
            if (p == null) return list;
            ServerLevel level = p.serverLevel();
            Vec3 center = p.position();
            AABB box = new AABB(center, center).inflate(radius);
            for (Entity e : level.getEntities(p, box, ent -> ent.isAlive())) {
                double dx = e.getX() - p.getX();
                double dz = e.getZ() - p.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz + Math.pow(e.getY() - p.getY(), 2));
                if (dist > radius) continue;
                String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
                String name = e.getDisplayName().getString();
                boolean hostile = e instanceof Monster;
                String dir = SpatialReasoning.compass(dx, dz);
                int threat = threatOf(e, hostile, dist);
                list.add(new EntityInfo(typeId, name, hostile, e.getX(), e.getY(), e.getZ(),
                    dist, dir, e.getY() - p.getY(), threat));
            }
            return list;
        });
        return out == null ? List.of() : out;
    }

    private static int threatOf(Entity e, boolean hostile, double dist) {
        if (!hostile) return 0;
        int t = 5;
        if (dist < 8) t += 3;
        else if (dist < 16) t += 1;
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        if (id.contains("creeper") || id.contains("warden")) t += 2;
        return Math.min(10, t);
    }

    @Override
    public List<BlockInfo> scanBlocks(String playerId, double radius, Set<String> blockIds) {
        List<BlockInfo> out = onServer(() -> {
            List<BlockInfo> list = new ArrayList<>();
            ServerPlayer p = player(playerId);
            if (p == null) return list;
            ServerLevel level = p.serverLevel();
            BlockPos center = p.blockPosition();
            int r = (int) Math.min(Math.ceil(radius), 64);
            for (BlockPos pos : BlockPos.betweenClosed(
                    center.offset(-r, -r, -r), center.offset(r, r, r))) {
                double dist = Math.sqrt(center.distSqr(pos));
                if (dist > radius) continue;
                String id;
                try {
                    id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
                } catch (Exception ex) {
                    continue;
                }
                if (blockIds.contains(id)) {
                    list.add(new BlockInfo(id, pos.getX(), pos.getY(), pos.getZ(), dist));
                    if (list.size() >= 64) break;
                }
            }
            list.sort((a, b) -> Double.compare(a.distance(), b.distance()));
            return list;
        });
        return out == null ? List.of() : out;
    }

    @Override
    public Optional<LocatedStructure> findStructure(String playerId, LocationQuery query) {
        LocatedStructure found = onServer(() -> {
            ServerPlayer p = player(playerId);
            if (p == null) return null;
            ServerLevel level = p.serverLevel();
            // lush caves are a biome; delegate.
            if (query.target().contains("lush_caves") || query.target().equals("minecraft:cave")) {
                return null;
            }
            Registry<Structure> reg;
            try {
                reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            } catch (Exception e) {
                return null;
            }
            Holder.Reference<Structure> holder;
            try {
                holder = reg.getHolderOrThrow(
                    ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(query.target())));
            } catch (Exception e) {
                return null;
            }
            Pair<BlockPos, Holder<Structure>> pair;
            try {
                pair = level.getChunkSource().getGenerator().findNearestMapStructure(
                    level, HolderSet.direct(holder), p.blockPosition(),
                    Math.max(64, Math.min(query.radius(), 20000)), false);
            } catch (Exception e) {
                return null;
            }
            if (pair == null || pair.getFirst() == null) return null;
            BlockPos pos = pair.getFirst();
            double dx = pos.getX() - p.getX();
            double dz = pos.getZ() - p.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            String dir = SpatialReasoning.compass(dx, dz);
            return new LocatedStructure(query.target(), "structure", pos.getX(), pos.getY(), pos.getZ(),
                dist, dir, level.dimension().location().toString());
        });
        return Optional.ofNullable(found);
    }

    @Override
    public Optional<LocatedStructure> findBiome(String playerId, LocationQuery query) {
        LocatedStructure found = onServer(() -> {
            ServerPlayer p = player(playerId);
            if (p == null) return null;
            ServerLevel level = p.serverLevel();
            List<String> hints = hintsFor(query.target());
            Pair<BlockPos, Holder<Biome>> pair;
            try {
                pair = level.findClosestBiome3d(
                    holder -> holder.unwrapKey().map(
                        key -> hints.stream().anyMatch(h -> key.location().getPath().contains(h)))
                        .orElse(false),
                    p.blockPosition(), Math.max(640, Math.min(query.radius(), 20000)), 32, 64);
            } catch (Exception e) {
                return null;
            }
            if (pair == null || pair.getFirst() == null) return null;
            BlockPos pos = pair.getFirst();
            String biomeId = pair.getSecond().unwrapKey()
                .map(k -> k.location().toString()).orElse(query.target());
            double dx = pos.getX() - p.getX();
            double dz = pos.getZ() - p.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            return new LocatedStructure(biomeId, "biome", pos.getX(), pos.getY(), pos.getZ(),
                dist, SpatialReasoning.compass(dx, dz), level.dimension().location().toString());
        });
        return Optional.ofNullable(found);
    }

    private static List<String> hintsFor(String target) {
        String t = target.toLowerCase();
        for (Map.Entry<String, List<String>> e : BIOME_HINTS.entrySet()) {
            if (t.contains(e.getKey())) return e.getValue();
        }
        String path = t.contains(":") ? t.substring(t.indexOf(':') + 1) : t;
        return List.of(path.replace(" ", "_"));
    }

    @Override
    public void sendMessage(String playerId, String text) {
        onServer(() -> {
            ServerPlayer p = player(playerId);
            if (p != null) {
                p.sendSystemMessage(Component.literal(text));
            }
            return null;
        });
    }

    @Override
    public void highlightOres(String playerId, List<OreHit> hits) {
        ServerPlayer p = player(playerId);
        if (p == null) return;
        JarvisPayloads.sendOreHighlights(p, hits);
    }

    @Override
    public void setGuideTarget(String playerId, int x, int y, int z, String label) {
        ServerPlayer p = player(playerId);
        if (p == null) return;
        JarvisPayloads.sendGuide(p, x, y, z, label, false);
    }

    @Override
    public void clearGuideTarget(String playerId) {
        ServerPlayer p = player(playerId);
        if (p == null) return;
        JarvisPayloads.sendGuide(p, 0, 0, 0, "", true);
    }

    @Override
    public List<String> inventorySummary(String playerId) {
        List<String> out = onServer(() -> {
            List<String> list = new ArrayList<>();
            ServerPlayer p = player(playerId);
            if (p == null) return list;
            for (var stack : p.getInventory().items) {
                if (!stack.isEmpty()) {
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    list.add(stack.getCount() + "x " + id);
                }
            }
            return list;
        });
        return out == null ? List.of() : out;
    }

    @Override
    public String lookingAt(String playerId) {
        String id = onServer(() -> {
            ServerPlayer p = player(playerId);
            if (p == null) return "";
            try {
                HitResult hit = p.pick(6.0, 0.0f, false);
                if (hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
                    BlockPos pos = b.getBlockPos();
                    return BuiltInRegistries.BLOCK
                        .getKey(p.serverLevel().getBlockState(pos).getBlock()).toString();
                }
            } catch (Exception ignored) {}
            return "";
        });
        return id == null ? "" : id;
    }

    @Override
    public List<String> nearbyContainers(String playerId, double radius) {
        List<String> out = onServer(() -> {
            List<String> list = new ArrayList<>();
            ServerPlayer p = player(playerId);
            if (p == null) return list;
            ServerLevel level = p.serverLevel();
            BlockPos center = p.blockPosition();
            int r = (int) Math.min(Math.ceil(radius), 32);
            for (BlockPos pos : BlockPos.betweenClosed(
                    center.offset(-r, -4, -r), center.offset(r, 4, r))) {
                try {
                    if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container) {
                        String id = BuiltInRegistries.BLOCK
                            .getKey(level.getBlockState(pos).getBlock()).toString();
                        list.add(id + " at " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                        if (list.size() >= 12) break;
                    }
                } catch (Exception ignored) {}
            }
            return list;
        });
        return out == null ? List.of() : out;
    }

    @Override
    public List<String> knownModIds() {
        try {
            List<String> ids = new ArrayList<>();
            net.neoforged.fml.ModList.get().forEachModContainer((id, c) -> ids.add(id));
            return ids;
        } catch (Exception e) {
            return List.of();
        }
    }
}
