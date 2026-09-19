package com.jarvis.core;

import com.jarvis.language.Intent;
import com.jarvis.language.ReasoningEngine;
import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SkillResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Explicit pipeline runner used by the debug UI and tests:
 * request -> intent -> entities -> context -> knowledge -> skills -> goal ->
 * plan -> validation -> execution -> result -> learning -> memory.
 */
public final class JarvisPlanner {
    private final JarvisInstance jarvis;

    public JarvisPlanner(JarvisInstance jarvis) {
        this.jarvis = jarvis;
    }

    public record Trace(Intent intent, Map<String, Object> entities, String goal,
                        String skillId, boolean valid, SkillResult result) {}

    public Trace plan(String text, SkillContext ctx) {
        var scored = jarvis.intents().recognize(text);
        Map<String, Object> entities = jarvis.context().resolve(
            new com.jarvis.language.EntityExtractor().extract(text), scored.intent());
        ReasoningEngine.Plan plan = new ReasoningEngine().decompose(text, scored.intent(), entities);
        Optional<Skill> skill = jarvis.skills().forIntent(scored.intent());
        String skillId = skill.map(Skill::id).orElse("none");
        boolean valid = skill.map(s -> s.preconditions(ctx)).orElse(false);
        SkillResult result = skill.map(s -> s.run(ctx)).orElseGet(() ->
            SkillResult.fail(scored.intent(), SkillResult.map("failure", "no skill"), "no-route"));
        Map<String, Object> goal = new LinkedHashMap<>();
        goal.put("objective", plan.objective());
        goal.put("steps", plan.steps().size());
        return new Trace(scored.intent(), entities, plan.objective(), skillId, valid, result);
    }
}
