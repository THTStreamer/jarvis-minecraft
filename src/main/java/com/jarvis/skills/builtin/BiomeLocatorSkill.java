package com.jarvis.skills.builtin;

import com.jarvis.language.Intent;
import com.jarvis.navigation.LocationQuery;
import com.jarvis.navigation.NavigationMemory;
import com.jarvis.navigation.PathPlanning;
import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SkillResult;
import com.jarvis.world.LocatedStructure;
import java.util.Map;
import java.util.Optional;

/** Locates biomes via world search. */
public final class BiomeLocatorSkill extends Skill {
    private final NavigationMemory navMemory;
    private final int defaultRadius;

    public BiomeLocatorSkill(NavigationMemory navMemory, int defaultRadius) {
        super("biome_locator", "Biome Locator", "Finds biomes matching the request", "1", 0.8f);
        this.navMemory = navMemory;
        this.defaultRadius = defaultRadius;
    }

    @Override
    protected SkillResult execute(SkillContext ctx) {
        LocationQuery query = LocationQuery.interpret(ctx.entities(), defaultRadius);
        Optional<LocatedStructure> found = ctx.world().findBiome(ctx.playerId(), query);
        if (found.isEmpty()) {
            return SkillResult.fail(Intent.LOCATE_BIOME,
                SkillResult.map("failure", "Sir, I attempted to locate a " + query.target()
                    + " biome, but the search area did not contain one."),
                "not found");
        }
        LocatedStructure best = found.get();
        navMemory.setActive(best);
        PathPlanning.Route route = PathPlanning.plan(best, ctx.snapshot().blockX(),
            ctx.snapshot().blockY(), ctx.snapshot().blockZ());
        Map<String, Object> frame = PathPlanning.asFrame(best, route);
        return SkillResult.ok(Intent.LOCATE_BIOME, frame);
    }
}
