package com.jarvis.navigation;

import com.jarvis.world.LocatedStructure;
import com.jarvis.world.SpatialReasoning;
import java.util.LinkedHashMap;
import java.util.Map;

/** Route description + travel estimate for a located target. */
public final class PathPlanning {
    private PathPlanning() {}

    public record Route(String advice, double minutes, int waypointX, int waypointY, int waypointZ) {}

    public static Route plan(LocatedStructure target, int fromX, int fromY, int fromZ) {
        double minutes = SpatialReasoning.travelMinutes(target.distanceBlocks());
        String advice = SpatialReasoning.directionAdvice(target.direction(), target.distanceBlocks());
        return new Route(advice, minutes, target.x(), target.y(), target.z());
    }

    public static Map<String, Object> asFrame(LocatedStructure target, Route route) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", target.id());
        f.put("distance", target.distanceBlocks());
        f.put("direction", target.direction());
        f.put("minutes", route.minutes());
        f.put("x", target.x());
        f.put("y", target.y());
        f.put("z", target.z());
        f.put("advice", route.advice());
        return f;
    }
}
