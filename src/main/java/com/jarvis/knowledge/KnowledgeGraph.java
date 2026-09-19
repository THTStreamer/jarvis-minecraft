package com.jarvis.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * MinecraftKnowledgeGraph: entities (player/item/block/entity/biome/structure/
 * dimension/recipe/mod/machine/skill/location/effect/spell/resource) linked by
 * typed relations. Concepts carry semantic frames, not bare strings.
 */
public final class KnowledgeGraph {
    private final Map<String, KnowledgeEntry> entries = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> concepts = new LinkedHashMap<>();

    public synchronized KnowledgeEntry add(String subject, String relation, String object,
                                           String source, float confidence) {
        String key = norm(subject) + "|" + norm(relation) + "|" + norm(object);
        KnowledgeEntry existing = entries.get(key);
        if (existing != null) {
            existing.confirm(confidence);
            return existing;
        }
        KnowledgeEntry e = new KnowledgeEntry(UUID.randomUUID().toString(), subject, relation, object, source, confidence);
        entries.put(key, e);
        return e;
    }

    public synchronized List<KnowledgeEntry> query(String subject, String relation) {
        List<KnowledgeEntry> out = new ArrayList<>();
        String ns = subject == null ? null : norm(subject);
        String nr = relation == null ? null : norm(relation);
        for (KnowledgeEntry e : entries.values()) {
            if (ns != null && !norm(e.subject()).contains(ns)) continue;
            if (nr != null && !norm(e.relation()).equals(nr)) continue;
            out.add(e);
        }
        out.sort((a, b) -> Float.compare(b.confidence().value(), a.confidence().value()));
        return out;
    }

    public synchronized List<KnowledgeEntry> queryObject(String object) {
        List<KnowledgeEntry> out = new ArrayList<>();
        String no = norm(object);
        for (KnowledgeEntry e : entries.values()) {
            if (norm(e.object()).contains(no) || norm(e.subject()).contains(no)) out.add(e);
        }
        out.sort((a, b) -> Float.compare(b.confidence().value(), a.confidence().value()));
        return out;
    }

    /** Attach a semantic frame to a concept (category, properties, related ids). */
    public synchronized void defineConcept(String concept, Map<String, String> frame) {
        concepts.put(norm(concept), new LinkedHashMap<>(frame));
    }

    public synchronized Map<String, String> concept(String name) {
        return concepts.getOrDefault(norm(name), Map.of());
    }

    public synchronized int size() {
        return entries.size();
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).trim();
    }

    public synchronized List<Map<String, Object>> export() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (KnowledgeEntry e : entries.values()) out.add(e.export());
        return out;
    }

    public synchronized void restore(List<Map<String, Object>> data) {
        entries.clear();
        for (Map<String, Object> m : data) {
            try {
                KnowledgeEntry e = KnowledgeEntry.restore(m);
                entries.put(norm(e.subject()) + "|" + norm(e.relation()) + "|" + norm(e.object()), e);
            } catch (Exception ignored) {}
        }
    }
}
