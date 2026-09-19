package com.jarvis.skills.builtin;

import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SkillResult;
import com.jarvis.skills.SkillRegistry;
import java.util.List;

/** Reports status, help, greetings and the skill catalogue. */
public final class StatusSkill extends Skill {
    private final SkillRegistry registry;
    private final StatusProvider provider;

    public interface StatusProvider {
        String statusLine();
    }

    public StatusSkill(SkillRegistry registry, StatusProvider provider) {
        super("status", "Status & Help", "Reports Jarvis status and capabilities", "1", 0.95f);
        this.registry = registry;
        this.provider = provider;
    }

    @Override
    protected SkillResult execute(SkillContext ctx) {
        return switch (ctx.intent()) {
            case SKILL_LIST -> {
                List<Skill> skills = registry.all();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < skills.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(skills.get(i).name());
                }
                yield SkillResult.ok(ctx.intent(), SkillResult.map("list", sb.toString()));
            }
            case STATUS -> SkillResult.ok(ctx.intent(),
                SkillResult.map("answer", provider.statusLine()));
            case DISTANCE_QUERY, TRAVEL_TIME_QUERY, FOLLOW_UP -> SkillResult.ok(ctx.intent(),
                SkillResult.map("answer", provider.statusLine()));
            default -> SkillResult.ok(ctx.intent(), SkillResult.map());
        };
    }
}
