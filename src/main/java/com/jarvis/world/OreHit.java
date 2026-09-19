package com.jarvis.world;

/** One ore block found by the ore scanner. */
public record OreHit(String blockId, int x, int y, int z, double distance) {}
