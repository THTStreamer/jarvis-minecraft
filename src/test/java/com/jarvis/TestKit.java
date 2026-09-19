package com.jarvis;

import com.jarvis.ai.inference.AIWorkerPool;
import com.jarvis.api.IModIntegration;
import com.jarvis.config.JarvisSettings;
import com.jarvis.core.JarvisInstance;
import com.jarvis.core.JarvisService;
import com.jarvis.navigation.LocationQuery;
import com.jarvis.network.JarvisNetwork;
import com.jarvis.player.JarvisProfile;
import com.jarvis.skills.WorldAccess;
import com.jarvis.world.BlockInfo;
import com.jarvis.world.EntityInfo;
import com.jarvis.world.LocatedStructure;
import com.jarvis.world.OreHit;
import com.jarvis.world.PlayerSnapshot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Shared test scaffolding: fake world + wiring + instances without Minecraft. */
public final class TestKit {
    private TestKit() {}

    public static final class FakeWorld implements WorldAccess {
        @Override
        public PlayerSnapshot snapshot(String playerId) {
            return new PlayerSnapshot(playerId, "Tester", 0, 64, 0, 0, 0,
                "minecraft:overworld", "minecraft:plains", 6000, "clear", 15);
        }

        @Override
        public List<EntityInfo> nearbyEntities(String playerId, double radius) {
            return List.of(
                new EntityInfo("minecraft:zombie", "Zombie", true, 10, 64, -8, 12.8, "north", 0, 6),
                new EntityInfo("minecraft:zombie", "Zombie", true, 12, 64, -6, 13.4, "north-east", 0, 6),
                new EntityInfo("minecraft:skeleton", "Skeleton", true, -9, 64, 4, 9.8, "west", 0, 6),
                new EntityInfo("minecraft:sheep", "Sheep", false, 5, 64, 5, 7.0, "south-east", 0, 0));
        }

        @Override
        public List<BlockInfo> scanBlocks(String playerId, double radius, Set<String> blockIds) {
            List<BlockInfo> out = new ArrayList<>();
            if (blockIds.contains("minecraft:diamond_ore")) {
                out.add(new BlockInfo("minecraft:diamond_ore", 5, 40, 5, 24.7));
            }
            return out;
        }

        @Override
        public Optional<LocatedStructure> findStructure(String playerId, LocationQuery query) {
            if (query.target().contains("village")) {
                return Optional.of(new LocatedStructure("minecraft:village", "structure",
                    300, 70, -400, 500, "north-east", "minecraft:overworld"));
            }
            return Optional.empty();
        }

        @Override
        public Optional<LocatedStructure> findBiome(String playerId, LocationQuery query) {
            return Optional.of(new LocatedStructure("minecraft:desert", "biome",
                800, 70, 200, 824, "east", "minecraft:overworld"));
        }

        @Override public void sendMessage(String playerId, String text) {}
        @Override public void highlightOres(String playerId, List<OreHit> hits) {}
        @Override public void setGuideTarget(String playerId, int x, int y, int z, String label) {}
        @Override public void clearGuideTarget(String playerId) {}
        @Override public List<String> inventorySummary(String playerId) {
            return List.of("5x minecraft:torch", "1x minecraft:diamond_sword");
        }
        @Override public String lookingAt(String playerId) { return "minecraft:grass_block"; }
        @Override public List<String> nearbyContainers(String playerId, double radius) { return List.of(); }
        @Override public List<String> knownModIds() { return List.of("minecraft", "create"); }
    }

    public static JarvisService.Wiring wiring(Path dataRoot, Supplier<JarvisSettings> settings) {
        AIWorkerPool pools = AIWorkerPool.defaults();
        return new JarvisService.Wiring(
            dataRoot, pools, new FakeWorld(), settings,
            (a, b) -> {}, (a, b) -> {}, u -> {}, (a, b) -> {},
            new JarvisNetwork(), new ArrayList<IModIntegration>(), (a, b) -> {});
    }

    public static JarvisInstance instance(Path dataRoot) {
        Supplier<JarvisSettings> settings = JarvisSettings::new;
        return new JarvisInstance(new JarvisProfile(UUID.randomUUID(), "Tester"),
            wiring(dataRoot, settings), settings);
    }

    public static JarvisInstance instance(Path dataRoot, Supplier<JarvisSettings> settings) {
        return new JarvisInstance(new JarvisProfile(UUID.randomUUID(), "Tester"),
            wiring(dataRoot, settings), settings);
    }
}
