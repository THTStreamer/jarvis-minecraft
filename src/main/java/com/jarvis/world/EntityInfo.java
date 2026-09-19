package com.jarvis.world;

/** One observed entity with spatial relations precomputed. */
public record EntityInfo(
    String typeId,
    String displayName,
    boolean hostile,
    double x, double y, double z,
    double distance,
    String direction,
    double dy,
    int threat
) {}
