package com.jarvis.world;

/** A located structure/biome/place result. */
public record LocatedStructure(
    String id,
    String kind,
    int x, int y, int z,
    double distanceBlocks,
    String direction,
    String dimension
) {}
