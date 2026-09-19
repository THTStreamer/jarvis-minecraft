package com.jarvis.world;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WorldKnowledge: current dimension/position/biome/time/weather plus known
 * locations and a short history of visited places. Updated by the Minecraft
 * glue from live snapshots; the AI core only reads plain data.
 */
public final class WorldKnowledge {
    private volatile PlayerSnapshot lastSnapshot;
    private final Map<String, int[]> knownLocations = new LinkedHashMap<>();
    private final Map<String, String> locationDimensions = new LinkedHashMap<>();

    public synchronized void update(PlayerSnapshot snapshot) {
        this.lastSnapshot = snapshot;
    }

    public PlayerSnapshot last() {
        return lastSnapshot;
    }

    public synchronized void rememberLocation(String name, int x, int y, int z, String dimension) {
        knownLocations.put(name.toLowerCase(), new int[]{x, y, z});
        locationDimensions.put(name.toLowerCase(), dimension);
        while (knownLocations.size() > 64) {
            String first = knownLocations.keySet().iterator().next();
            knownLocations.remove(first);
            locationDimensions.remove(first);
        }
    }

    public synchronized Map<String, int[]> knownLocations() {
        return new LinkedHashMap<>(knownLocations);
    }

    public String describeCurrent() {
        PlayerSnapshot s = lastSnapshot;
        if (s == null) return "unknown location";
        return "at " + s.blockX() + ", " + s.blockY() + ", " + s.blockZ()
            + " in " + s.dimension() + " (" + s.biome() + "), time " + s.time()
            + ", weather " + s.weather();
    }
}
