package com.jarvis.skills.builtin;

import com.jarvis.language.Intent;
import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SkillResult;
import com.jarvis.world.EntityInfo;
import com.jarvis.world.HostileScanner;
import java.util.List;

/** Scans nearby entities, classifies hostiles and summarizes spatially. */
public final class HostileMobScannerSkill extends Skill {
    private final double radius;

    public HostileMobScannerSkill(double radius) {
        super("hostile_mob_scanner", "Hostile Mob Scanner",
            "Detects, counts and localizes hostile creatures around the player", "1", 0.9f);
        this.radius = radius;
    }

    @Override
    protected SkillResult execute(SkillContext ctx) {
        List<EntityInfo> nearby = ctx.world().nearbyEntities(ctx.playerId(), radius);
        HostileScanner.Summary summary = HostileScanner.summarize(nearby, radius);
        if (summary.count() == 0) {
            return SkillResult.ok(Intent.SCAN_HOSTILES,
                SkillResult.map("count", 0, "radius", (int) radius));
        }
        return SkillResult.ok(Intent.SCAN_HOSTILES,
            SkillResult.map("count", summary.count(), "detail", summary.detail(),
                "radius", (int) radius));
    }
}
