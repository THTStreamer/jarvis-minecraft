package com.jarvis.skills.builtin;

import com.jarvis.knowledge.KnowledgeEntry;
import com.jarvis.knowledge.KnowledgeGraph;
import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SkillResult;
import java.util.List;

/**
 * Answers from learned knowledge: semantic frames, mod observations and the
 * knowledge graph. Never invents facts - says what it doesn't know and
 * offers to learn by observation.
 */
public final class KnowledgeAnswerSkill extends Skill {
    private final KnowledgeGraph knowledge;

    public KnowledgeAnswerSkill(KnowledgeGraph knowledge) {
        super("knowledge_answer", "Knowledge Answer",
            "Answers from learned semantic knowledge and mod observations", "1", 0.7f);
        this.knowledge = knowledge;
    }

    @Override
    protected SkillResult execute(SkillContext ctx) {
        String topic = topicOf(ctx);
        List<KnowledgeEntry> hits = knowledge.queryObject(topic);
        KnowledgeEntry best = null;
        for (KnowledgeEntry e : hits) {
            if (e.confidence().value() >= 0.35) {
                best = e;
                break;
            }
        }
        if (best != null) {
            String answer = "Based on what I've learned" + sourceNote(best) + ": "
                + best.subject() + " " + best.relation() + " " + best.object() + ".";
            return SkillResult.ok(ctx.intent(), SkillResult.map("answer", answer, "topic", topic));
        }
        // contextual fallbacks from live observation
        String observed = ctx.world().lookingAt(ctx.playerId());
        if (observed != null && !observed.isBlank() && !observed.equals("minecraft:air")) {
            return SkillResult.ok(ctx.intent(), SkillResult.map("answer",
                "You're looking at " + pretty(observed) + ". I haven't studied it yet - "
                + "interact with it and I'll observe and learn.", "topic", topic));
        }
        return SkillResult.ok(ctx.intent(), SkillResult.map("topic", topic));
    }

    private String topicOf(SkillContext ctx) {
        Object ore = ctx.entities().get("ore");
        if (ore instanceof String s) return s;
        Object structure = ctx.entities().get("structure");
        if (structure instanceof String s) return s;
        Object mob = ctx.entities().get("mob");
        if (mob instanceof String s) return s;
        if (!ctx.entities().isEmpty()) return String.valueOf(ctx.entities().values().iterator().next());
        String[] words = ctx.input().toLowerCase().replaceAll("[^a-z ]", "").split("\\s+");
        for (int i = words.length - 1; i >= 0; i--) {
            if (words[i].length() > 3) return words[i];
        }
        return "that";
    }

    private static String sourceNote(KnowledgeEntry e) {
        int obs = e.observations();
        if (obs > 3) return ", after " + obs + " observations";
        return "";
    }

    private static String pretty(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return path.replace('_', ' ');
    }
}
