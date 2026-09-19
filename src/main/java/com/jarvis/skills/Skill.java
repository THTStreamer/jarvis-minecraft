package com.jarvis.skills;

import com.jarvis.knowledge.Confidence;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Base class for every Jarvis capability. Carries metadata, preconditions,
 * confidence, version and usage statistics; subclasses implement execute().
 */
public abstract class Skill {
    private final String id;
    private final String name;
    private final String description;
    private final String version;
    private final Confidence confidence;
    private long uses;
    private long successes;

    protected Skill(String id, String name, String description, String version, float initialConfidence) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.version = version;
        this.confidence = new Confidence(initialConfidence);
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public String version() { return version; }
    public Confidence confidence() { return confidence; }
    public long uses() { return uses; }
    public long successes() { return successes; }

    /** Knowledge ids required before this skill is trusted. */
    public Set<String> requiredKnowledge() { return Set.of(); }

    /** Preconditions checked before execution (world state, config). */
    public boolean preconditions(SkillContext ctx) { return true; }

    /** Failure conditions that abort execution safely. */
    public boolean failed(SkillResult result) { return !result.success(); }

    protected abstract SkillResult execute(SkillContext ctx) throws Exception;

    /** Runs with stats tracking + confidence updates. Never throws. */
    public final SkillResult run(SkillContext ctx) {
        uses++;
        if (!preconditions(ctx)) {
            return SkillResult.fail(ctx.intent(), SkillResult.map("failure", "preconditions not met"),
                "precondition");
        }
        try {
            SkillResult r = execute(ctx);
            if (r.success()) {
                successes++;
                confidence.confirm((float) Math.max(0.05, r.confidenceDelta() + 0.05));
            } else {
                confidence.contradict(0.2f);
            }
            return r;
        } catch (Exception e) {
            confidence.contradict(0.3f);
            return SkillResult.fail(ctx.intent(), SkillResult.map("failure", "skill error"), e.getMessage());
        }
    }

    public Map<String, Object> describe() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("description", description);
        m.put("version", version);
        m.put("confidence", confidence.value());
        m.put("uses", uses);
        m.put("successes", successes);
        return m;
    }

    public void restoreStats(long uses, long successes, float confidence) {
        this.uses = uses;
        this.successes = successes;
        this.confidence.set(confidence);
    }
}
