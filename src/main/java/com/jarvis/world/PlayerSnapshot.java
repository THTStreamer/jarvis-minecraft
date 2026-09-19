package com.jarvis.world;

/** Immutable snapshot of player/world state - the unit Jarvis reasons about. */
public record PlayerSnapshot(
    String playerId,
    String playerName,
    double x, double y, double z,
    float yaw, float pitch,
    String dimension,
    String biome,
    long time,
    String weather,
    int light
) {
    public int blockX() { return (int) Math.floor(x); }
    public int blockY() { return (int) Math.floor(y); }
    public int blockZ() { return (int) Math.floor(z); }
}
