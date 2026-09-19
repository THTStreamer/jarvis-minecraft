package com.jarvis.skills;

import com.jarvis.language.Intent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Registry of built-in and dynamically learned skills. */
public final class SkillRegistry {
    private final Map<String, Skill> skills = new LinkedHashMap<>();
    private final Map<Intent, String> intentRouting = new LinkedHashMap<>();

    public synchronized void register(Skill skill) {
        skills.put(skill.id(), skill);
    }

    public synchronized void route(Intent intent, String skillId) {
        intentRouting.put(intent, skillId);
    }

    public synchronized Optional<Skill> get(String id) {
        return Optional.ofNullable(skills.get(id));
    }

    public synchronized Optional<Skill> forIntent(Intent intent) {
        String id = intentRouting.get(intent);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(skills.get(id));
    }

    public synchronized List<Skill> all() {
        return new ArrayList<>(skills.values());
    }

    public synchronized int size() {
        return skills.size();
    }

    public synchronized List<Map<String, Object>> describeAll() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Skill s : skills.values()) out.add(s.describe());
        return out;
    }

    public synchronized void restoreStats(Map<String, Map<String, Object>> stats) {
        for (Map.Entry<String, Map<String, Object>> e : stats.entrySet()) {
            Skill s = skills.get(e.getKey());
            if (s == null) continue;
            Map<String, Object> st = e.getValue();
            long uses = ((Number) st.getOrDefault("uses", 0L)).longValue();
            long ok = ((Number) st.getOrDefault("successes", 0L)).longValue();
            float conf = ((Number) st.getOrDefault("confidence", 0.5)).floatValue();
            s.restoreStats(uses, ok, conf);
        }
    }

    public synchronized Map<String, Map<String, Object>> exportStats() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Skill s : skills.values()) {
            Map<String, Object> st = new LinkedHashMap<>();
            st.put("uses", s.uses());
            st.put("successes", s.successes());
            st.put("confidence", s.confidence().value());
            out.put(s.id(), st);
        }
        return out;
    }
}
