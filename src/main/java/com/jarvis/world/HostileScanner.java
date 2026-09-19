package com.jarvis.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure summarization of hostile scans: groups by mob type and direction,
 * ranks threats, and produces a natural-language summary with counts -
 * never a raw coordinate dump.
 */
public final class HostileScanner {
    private HostileScanner() {}

    public record Summary(int count, String detail, List<EntityInfo> hostiles, double radius) {}

    public static Summary summarize(List<EntityInfo> nearby, double radius) {
        List<EntityInfo> hostiles = new ArrayList<>();
        for (EntityInfo e : nearby) {
            if (e.hostile()) hostiles.add(e);
        }
        hostiles.sort((a, b) -> Double.compare(a.distance(), b.distance()));
        if (hostiles.isEmpty()) {
            return new Summary(0, "", List.of(), radius);
        }
        // group by type + coarse direction
        Map<String, List<EntityInfo>> groups = new LinkedHashMap<>();
        for (EntityInfo e : hostiles) {
            String key = e.displayName().toLowerCase(Locale.ROOT) + "|" + coarse(e.direction());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, List<EntityInfo>> g : groups.entrySet()) {
            List<EntityInfo> list = g.getValue();
            String name = list.get(0).displayName().toLowerCase(Locale.ROOT);
            long nearest = Math.round(list.stream().mapToDouble(EntityInfo::distance).min().orElse(0));
            String dir = list.get(0).direction();
            if (list.size() == 1) {
                parts.add("a " + name + " roughly " + nearest + " blocks " + dir);
            } else {
                parts.add(list.size() + " " + name + "s to the " + dir);
            }
        }
        String detail = joinNatural(parts);
        detail = Character.toUpperCase(detail.charAt(0)) + detail.substring(1) + ".";
        return new Summary(hostiles.size(), detail, hostiles, radius);
    }

    private static String coarse(String dir) {
        String d = dir.toLowerCase(Locale.ROOT);
        if (d.contains("north") && d.contains("east")) return "north-east";
        if (d.contains("north") && d.contains("west")) return "north-west";
        if (d.contains("south") && d.contains("east")) return "south-east";
        if (d.contains("south") && d.contains("west")) return "south-west";
        if (d.contains("north")) return "north";
        if (d.contains("south")) return "south";
        if (d.contains("east")) return "east";
        if (d.contains("west")) return "west";
        return d;
    }

    private static String joinNatural(List<String> parts) {
        if (parts.size() == 1) return "there is " + parts.get(0);
        StringBuilder sb = new StringBuilder("there are ");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(i == parts.size() - 1 ? ", and " : ", ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
