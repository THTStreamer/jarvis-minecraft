package com.jarvis.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;

/** One semantic fact with confidence, source and observation count. */
public final class KnowledgeEntry {
    private final String id;
    private final String subject;
    private final String relation;
    private final String object;
    private final String source;
    private final Confidence confidence;
    private int observations;
    private long updatedAt;

    public KnowledgeEntry(String id, String subject, String relation, String object,
                          String source, float confidence) {
        this.id = id;
        this.subject = subject;
        this.relation = relation;
        this.object = object;
        this.source = source;
        this.confidence = new Confidence(confidence);
        this.observations = 1;
        this.updatedAt = System.currentTimeMillis();
    }

    public String id() { return id; }
    public String subject() { return subject; }
    public String relation() { return relation; }
    public String object() { return object; }
    public String source() { return source; }
    public Confidence confidence() { return confidence; }
    public int observations() { return observations; }
    public long updatedAt() { return updatedAt; }

    public void confirm(float weight) {
        observations++;
        confidence.confirm(weight);
        updatedAt = System.currentTimeMillis();
    }

    public void contradict(float weight) {
        confidence.contradict(weight);
        updatedAt = System.currentTimeMillis();
    }

    public Map<String, Object> export() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("subject", subject);
        m.put("relation", relation);
        m.put("object", object);
        m.put("source", source);
        m.put("confidence", confidence.value());
        m.put("observations", observations);
        m.put("updatedAt", updatedAt);
        return m;
    }

    public static KnowledgeEntry restore(Map<String, Object> m) {
        KnowledgeEntry e = new KnowledgeEntry(
            String.valueOf(m.get("id")),
            String.valueOf(m.get("subject")),
            String.valueOf(m.get("relation")),
            String.valueOf(m.get("object")),
            String.valueOf(m.getOrDefault("source", "unknown")),
            ((Number) m.getOrDefault("confidence", 0.5)).floatValue());
        e.observations = ((Number) m.getOrDefault("observations", 1)).intValue();
        return e;
    }
}
