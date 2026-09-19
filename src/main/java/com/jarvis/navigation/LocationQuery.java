package com.jarvis.navigation;

import java.util.LinkedHashMap;
import java.util.Map;

/** A parsed location request: structure, biome or named place + filters. */
public final class LocationQuery {
    public enum Kind { STRUCTURE, BIOME, PLACE }

    private final Kind kind;
    private final String target;
    private final Map<String, String> filters;
    private final int radius;

    public LocationQuery(Kind kind, String target, Map<String, String> filters, int radius) {
        this.kind = kind;
        this.target = target;
        this.filters = new LinkedHashMap<>(filters);
        this.radius = radius;
    }

    public Kind kind() { return kind; }
    public String target() { return target; }
    public Map<String, String> filters() { return filters; }
    public int radius() { return radius; }

    /** Interpret entities from the language layer into a query. */
    public static LocationQuery interpret(Map<String, Object> entities, int defaultRadius) {
        Object structure = entities.get("structure");
        Object biome = entities.get("biome");
        Map<String, String> filters = new LinkedHashMap<>();
        if (entities.get("size") instanceof String s) filters.put("size", s);
        if (entities.get("direction") instanceof String d) filters.put("direction", d);
        int radius = defaultRadius;
        if (entities.get("numbers") instanceof java.util.List<?> nums && !nums.isEmpty()
                && nums.get(0) instanceof Number n && n.intValue() > 16 && n.intValue() <= 20000) {
            radius = n.intValue();
        }
        if (structure instanceof String s) {
            return new LocationQuery(Kind.STRUCTURE, s, filters, radius);
        }
        if (biome instanceof String b) {
            return new LocationQuery(Kind.BIOME, b, filters, radius);
        }
        Object quoted = entities.get("quoted");
        if (quoted instanceof java.util.List<?> q && !q.isEmpty()) {
            return new LocationQuery(Kind.PLACE, String.valueOf(q.get(0)), filters, radius);
        }
        return new LocationQuery(Kind.STRUCTURE, "village", filters, radius);
    }
}
