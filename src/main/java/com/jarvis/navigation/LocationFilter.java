package com.jarvis.navigation;

import com.jarvis.world.LocatedStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Applies variant/resource filters to raw search results. */
public final class LocationFilter {
    private LocationFilter() {}

    public static Optional<LocatedStructure> select(List<LocatedStructure> candidates, LocationQuery query) {
        if (candidates.isEmpty()) return Optional.empty();
        List<LocatedStructure> filtered = new ArrayList<>(candidates);
        String dir = query.filters().get("direction");
        if (dir != null) {
            filtered.removeIf(c -> !c.direction().toLowerCase(Locale.ROOT).contains(dir.toLowerCase(Locale.ROOT)));
            if (filtered.isEmpty()) filtered = new ArrayList<>(candidates);
        }
        filtered.sort((a, b) -> Double.compare(a.distanceBlocks(), b.distanceBlocks()));
        return Optional.of(filtered.get(0));
    }
}
