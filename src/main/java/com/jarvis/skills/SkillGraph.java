package com.jarvis.skills;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A sandboxed skill plan: ordered approved ops with string args. */
public final class SkillGraph {
    public record Step(SkillOp op, Map<String, String> args) {}

    private final String skillId;
    private final String goal;
    private final List<Step> steps = new ArrayList<>();

    public SkillGraph(String skillId, String goal) {
        this.skillId = skillId;
        this.goal = goal;
    }

    public SkillGraph add(SkillOp op, Map<String, String> args) {
        steps.add(new Step(op, new LinkedHashMap<>(args)));
        return this;
    }

    public SkillGraph add(SkillOp op) {
        return add(op, Map.of());
    }

    public String skillId() { return skillId; }
    public String goal() { return goal; }
    public List<Step> steps() { return steps; }

    public Map<String, Object> export() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("skillId", skillId);
        m.put("goal", goal);
        List<Map<String, Object>> s = new ArrayList<>();
        for (Step st : steps) {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("op", st.op().name());
            sm.put("args", st.args());
            s.add(sm);
        }
        m.put("steps", s);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static SkillGraph restore(Map<String, Object> m) {
        SkillGraph g = new SkillGraph(String.valueOf(m.get("skillId")), String.valueOf(m.get("goal")));
        Object steps = m.get("steps");
        if (steps instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> sm) {
                    try {
                        SkillOp op = SkillOp.valueOf(String.valueOf(sm.get("op")));
                        Map<String, String> args = new LinkedHashMap<>();
                        Object a = sm.get("args");
                        if (a instanceof Map<?, ?> am) {
                            for (Map.Entry<?, ?> e : am.entrySet()) {
                                args.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                            }
                        }
                        g.add(op, args);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        return g;
    }
}
