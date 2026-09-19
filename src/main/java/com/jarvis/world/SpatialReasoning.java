package com.jarvis.world;

import java.util.Locale;

/**
 * Spatial reasoning: compass directions, relative descriptions (behind me,
 * to my left, above us), distance formatting and travel estimates.
 */
public final class SpatialReasoning {
    private SpatialReasoning() {}

    /** Compass direction from a delta vector (dx east+, dz south+). */
    public static String compass(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        String[] names = {"north", "north-east", "east", "south-east",
            "south", "south-west", "west", "north-west"};
        int idx = (int) Math.round(((angle + 360) % 360) / 45.0) % 8;
        return names[idx];
    }

    /** Relative description given the player's yaw (degrees, Minecraft convention). */
    public static String relative(double dx, double dz, double dy, float yaw) {
        double targetAngle = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((targetAngle - yaw) % 360 + 540) % 360 - 180; // -180..180
        String horiz;
        double a = Math.abs(rel);
        if (a < 22.5) horiz = "directly ahead of you";
        else if (a < 67.5) horiz = rel > 0 ? "to your front-right" : "to your front-left";
        else if (a < 112.5) horiz = rel > 0 ? "to your right" : "to your left";
        else if (a < 157.5) horiz = rel > 0 ? "behind you to the right" : "behind you to the left";
        else horiz = "directly behind you";
        if (dy > 4) return horiz + " and above";
        if (dy < -4) return horiz + " and below";
        return horiz;
    }

    public static String describeDistance(double blocks) {
        long b = Math.round(blocks);
        if (b < 8) return "just " + b + " blocks away";
        if (b < 60) return "approximately " + b + " blocks away";
        if (b < 600) return "approximately " + b + " blocks";
        return "approximately " + b + " blocks";
    }

    /** Rough overworld walking minutes assuming 4.3 blocks/s with detours. */
    public static double travelMinutes(double blocks) {
        return blocks / 4.3 / 60.0 * 1.6;
    }

    public static String directionAdvice(String compassDir, double blocks) {
        String dir = compassDir.toLowerCase(Locale.ROOT);
        long b = Math.round(blocks);
        if (b < 10) return "It's close - just head " + dir + ".";
        return "Travel " + dir + " for approximately " + b + " blocks, keeping an eye on terrain.";
    }
}
