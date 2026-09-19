package com.jarvis.skills.builtin;

import com.jarvis.language.Intent;
import com.jarvis.navigation.NavigationMemory;
import com.jarvis.navigation.PathPlanning;
import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SkillResult;
import com.jarvis.world.LocatedStructure;

/** Guides the player to the active navigation target. */
public final class NavigationSkill extends Skill {
    private final NavigationMemory navMemory;

    public NavigationSkill(NavigationMemory navMemory) {
        super("navigation", "Navigation Guide",
            "Marks the route and guides the player to the active target", "1", 0.8f);
        this.navMemory = navMemory;
    }

    @Override
    protected SkillResult execute(SkillContext ctx) {
        LocatedStructure active = navMemory.active();
        if (active == null) {
            return SkillResult.fail(ctx.intent(),
                SkillResult.map("failure", "I don't have an active destination. Ask me to find somewhere first."),
                "no target");
        }
        ctx.world().setGuideTarget(ctx.playerId(), active.x(), active.y(), active.z(), active.id());
        PathPlanning.Route route = PathPlanning.plan(active, ctx.snapshot().blockX(),
            ctx.snapshot().blockY(), ctx.snapshot().blockZ());
        java.util.Map<String, Object> frame = PathPlanning.asFrame(active, route);
        frame.put("advice", route.advice());
        return SkillResult.ok(Intent.NAVIGATE_GUIDE, frame,
            "guiding to " + active.x() + "," + active.y() + "," + active.z());
    }
}
