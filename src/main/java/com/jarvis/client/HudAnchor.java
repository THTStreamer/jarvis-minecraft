package com.jarvis.client;

/**
 * Pure anchor math for the Jarvis portrait: four corners, margin-aware,
 * never cropped (square art scales uniformly). Unit-tested without Minecraft.
 */
public final class HudAnchor {
    public enum Corner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    private HudAnchor() {}

    public static Corner parse(String name) {
        if (name == null) return Corner.TOP_LEFT;
        try {
            return Corner.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Corner.TOP_LEFT;
        }
    }

    /** Top-left pixel of the portrait box for the given corner and size. */
    public static int[] origin(Corner corner, int size, int screenW, int screenH, int margin) {
        int x = switch (corner) {
            case TOP_LEFT, BOTTOM_LEFT -> margin;
            case TOP_RIGHT, BOTTOM_RIGHT -> Math.max(margin, screenW - margin - size);
        };
        int y = switch (corner) {
            case TOP_LEFT, TOP_RIGHT -> margin;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> Math.max(margin, screenH - margin - size);
        };
        return new int[]{x, y};
    }

    /** Center pixel of the portrait box (pulse scaling grows around this). */
    public static int[] center(Corner corner, int size, int screenW, int screenH, int margin) {
        int[] o = origin(corner, size, screenW, screenH, margin);
        return new int[]{o[0] + size / 2, o[1] + size / 2};
    }
}
