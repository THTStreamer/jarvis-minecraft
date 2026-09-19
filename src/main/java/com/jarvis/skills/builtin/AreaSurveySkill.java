package com.jarvis.skills.builtin;

import com.jarvis.language.Intent;
import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SkillResult;
import com.jarvis.world.EntityInfo;
import com.jarvis.world.PlayerSnapshot;
import java.util.List;

/** General area survey: entities, biome, time, weather, light in one summary. */
public final class AreaSurveySkill extends Skill {
    private final double radius;

    public AreaSurveySkill(double radius) {
        super("area_survey", "Area Survey",
            "Summarizes surroundings: entities, biome, time, weather and light", "1", 0.85f);
        this.radius = radius;
    }

    @Override
    protected SkillResult execute(SkillContext ctx) {
        PlayerSnapshot s = ctx.snapshot();
        List<EntityInfo> nearby = ctx.world().nearbyEntities(ctx.playerId(), radius);
        long hostiles = nearby.stream().filter(EntityInfo::hostile).count();
        long passive = nearby.size() - hostiles;
        String timeWord = timeWord(s.time());
        StringBuilder detail = new StringBuilder("We're in ").append(s.biome())
            .append(", ").append(timeWord).append(", weather ").append(s.weather())
            .append(", light level ").append(s.light()).append(". ");
        if (nearby.isEmpty()) {
            detail.append("No creatures in sight.");
        } else {
            detail.append("I can see ").append(nearby.size()).append(" creatures: ")
                .append(hostiles).append(" hostile, ").append(passive).append(" passive.");
        }
        return SkillResult.ok(Intent.SCAN_AREA, SkillResult.map("detail", detail.toString()));
    }

    private static String timeWord(long time) {
        long t = time % 24000;
        if (t < 1000) return "just after dawn";
        if (t < 6000) return "morning";
        if (t < 12000) return "afternoon";
        if (t < 13000) return "sunset";
        if (t < 23000) return "night";
        return "just before dawn";
    }
}
