package com.jarvis.skills.builtin;

import com.jarvis.language.Intent;
import com.jarvis.navigation.LocationFilter;
import com.jarvis.navigation.LocationQuery;
import com.jarvis.navigation.NavigationMemory;
import com.jarvis.navigation.PathPlanning;
import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SkillResult;
import com.jarvis.world.LocatedStructure;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Locates structures via world search with variant filters. */
public final class VillageLocatorSkill extends Skill {
    private final NavigationMemory navMemory;
    private final int defaultRadius;

    public VillageLocatorSkill(NavigationMemory navMemory, int defaultRadius) {
        super("village_locator", "Structure Locator",
            "Finds structures (villages, strongholds, caves, mansions) with filters", "1", 0.85f);
        this.navMemory = navMemory;
        this.defaultRadius = defaultRadius;
    }

    @Override
    protected SkillResult execute(SkillContext ctx) {
        LocationQuery query = LocationQuery.interpret(ctx.entities(), defaultRadius);
        String want = mapTarget(query.target());
        LocationQuery mapped = new LocationQuery(LocationQuery.Kind.STRUCTURE, want,
            query.filters(), query.radius());
        Optional<LocatedStructure> found = ctx.world().findStructure(ctx.playerId(), mapped);
        if (found.isEmpty()) {
            return SkillResult.fail(Intent.LOCATE_STRUCTURE,
                SkillResult.map("failure", "Sir, I attempted to locate a nearby "
                    + query.target() + ", but the search area did not contain one."),
                "not found");
        }
        LocatedStructure best = LocationFilter.select(List.of(found.get()), mapped).orElse(found.get());
        navMemory.setActive(best);
        PathPlanning.Route route = PathPlanning.plan(best, ctx.snapshot().blockX(),
            ctx.snapshot().blockY(), ctx.snapshot().blockZ());
        Map<String, Object> frame = PathPlanning.asFrame(best, route);
        return SkillResult.ok(Intent.LOCATE_STRUCTURE, frame);
    }

    /** Normalize natural words to searchable structure ids. */
    static String mapTarget(String target) {
        String t = target.toLowerCase();
        if (t.contains("village") || t.contains("settlement")) return "minecraft:village";
        if (t.contains("stronghold")) return "minecraft:stronghold";
        if (t.contains("mansion")) return "minecraft:mansion";
        if (t.contains("temple")) return "minecraft:desert_pyramid";
        if (t.contains("fortress")) return "minecraft:fortress";
        if (t.contains("bastion")) return "minecraft:bastion_remnant";
        if (t.contains("city")) return "minecraft:end_city";
        if (t.contains("monument")) return "minecraft:monument";
        if (t.contains("lush")) return "minecraft:lush_caves";
        if (t.contains("cave")) return "minecraft:cave";
        if (t.contains("mineshaft")) return "minecraft:mineshaft";
        if (t.contains("shipwreck")) return "minecraft:shipwreck";
        return "minecraft:village";
    }
}
