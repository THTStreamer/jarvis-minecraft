package com.jarvis.world;

/** One observed block position. */
public record BlockInfo(String blockId, int x, int y, int z, double distance) {}
