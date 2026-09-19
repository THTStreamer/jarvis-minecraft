package com.jarvis.language;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts structured entities: numbers, coordinates, directions, targets, names. */
public final class EntityExtractor {
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern IDENT = Pattern.compile("[a-z0-9_\\-]+:[a-z0-9_/\\-\\.]+");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    private static final List<String> STRUCTURES = List.of(
        "village", "stronghold", "mansion", "temple", "fortress", "bastion", "city",
        "monument", "shipwreck", "ruins", "cave", "lush cave", "dripstone", "mineshaft",
        "settlement", "blacksmith", "desert village", "plains village", "savanna village");
    private static final List<String> MOBS = List.of(
        "zombie", "skeleton", "creeper", "spider", "enderman", "witch", "slime",
        "phantom", "drowned", "husk", "pillager", "blaze", "ghast", "warden", "mob", "enemy");
    private static final List<String> ORES = List.of(
        "diamond", "iron", "gold", "coal", "copper", "redstone", "lapis", "emerald",
        "netherite", "ancient debris", "quartz", "amethyst");
    private static final List<String> BIOMES = List.of(
        "desert", "plains", "forest", "jungle", "savanna", "taiga", "swamp", "ocean",
        "mountains", "badlands", "mushroom", "lush", "dripstone", "nether", "end");
    private static final List<String> DIRS = List.of(
        "north", "south", "east", "west", "north-east", "northeast", "north-west",
        "northwest", "south-east", "southeast", "south-west", "southwest", "up", "down",
        "above", "below", "behind", "left", "right");

    public Map<String, Object> extract(String text) {
        Map<String, Object> out = new LinkedHashMap<>();
        String lower = text.toLowerCase(Locale.ROOT);

        List<Double> numbers = new ArrayList<>();
        Matcher nm = NUMBER.matcher(lower);
        while (nm.find()) {
            try {
                numbers.add(Double.parseDouble(nm.group()));
            } catch (NumberFormatException ignored) {}
        }
        if (!numbers.isEmpty()) out.put("numbers", numbers);

        List<String> idents = new ArrayList<>();
        Matcher im = IDENT.matcher(lower);
        while (im.find()) idents.add(im.group());
        if (!idents.isEmpty()) out.put("identifiers", idents);

        List<String> quoted = new ArrayList<>();
        Matcher qm = QUOTED.matcher(text);
        while (qm.find()) quoted.add(qm.group(1));
        if (!quoted.isEmpty()) out.put("quoted", quoted);

        String structure = firstMatch(lower, STRUCTURES);
        if (structure != null) out.put("structure", structure);
        String mob = firstMatch(lower, MOBS);
        if (mob != null) out.put("mob", mob);
        String ore = firstMatch(lower, ORES);
        if (ore != null) out.put("ore", ore);
        String biome = firstMatch(lower, BIOMES);
        if (biome != null) out.put("biome", biome);
        String dir = firstMatch(lower, DIRS);
        if (dir != null) out.put("direction", normalizeDir(dir));

        if (lower.contains("closest") || lower.contains("nearest")) out.put("superlative", "nearest");
        if (lower.contains("large") || lower.contains("big")) out.put("size", "large");
        if (lower.matches(".*\\b(200|hundred|thousand)\\b.*") || lower.contains("blocks away")) out.put("has_distance", true);

        // player name in Jarvis-to-Jarvis requests: "tell <name>'s jarvis ..."
        Matcher tell = Pattern.compile("tell\\s+([a-zA-Z0-9_]{3,16})").matcher(lower);
        if (tell.find()) out.put("target_player", tell.group(1));

        return out;
    }

    private static String firstMatch(String lower, List<String> options) {
        String best = null;
        for (String o : options) {
            if (lower.contains(o) && (best == null || o.length() > best.length())) best = o;
        }
        return best;
    }

    private static String normalizeDir(String d) {
        return switch (d) {
            case "northeast", "north-east" -> "north-east";
            case "northwest", "north-west" -> "north-west";
            case "southeast", "south-east" -> "south-east";
            case "southwest", "south-west" -> "south-west";
            default -> d;
        };
    }
}
