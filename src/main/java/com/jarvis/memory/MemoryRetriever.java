package com.jarvis.memory;

import com.jarvis.util.Floats;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Relevance-scoped retrieval: blends semantic similarity, recency,
 * importance, access history and task/location context. Never dumps the
 * whole memory into a request - only the top-k relevant entries.
 */
public final class MemoryRetriever {
    public record Hit(MemoryEntry entry, double score) {}

    private final JarvisMemory memory;

    public MemoryRetriever(JarvisMemory memory) {
        this.memory = memory;
    }

    public List<Hit> retrieve(float[] queryVec, String taskHint, int topK, long now) {
        List<Hit> hits = new ArrayList<>();
        for (MemoryType type : MemoryType.values()) {
            if (type == MemoryType.SHORT_TERM || type == MemoryType.WORKING) continue;
            for (MemoryEntry e : memory.all(type)) {
                double sem = 0.0;
                if (queryVec != null && e.vector() != null && e.vector().length == queryVec.length) {
                    sem = (Floats.cosine(queryVec, e.vector()) + 1.0) / 2.0;
                }
                double ageHours = Math.max(0.0, (now - e.lastAccess()) / 3_600_000.0);
                double recency = 1.0 / (1.0 + ageHours / 24.0);
                double importance = Math.max(0.0, Math.min(1.0, e.importance()));
                double familiarity = Math.min(1.0, e.accesses() / 5.0) * 0.1;
                double contextBoost = 0.0;
                if (taskHint != null && !taskHint.isBlank()) {
                    String t = e.text().toLowerCase();
                    String c = e.context().toLowerCase();
                    String h = taskHint.toLowerCase();
                    for (String w : h.split("\\s+")) {
                        if (w.length() >= 4 && (t.contains(w) || c.contains(w))) {
                            contextBoost += 0.08;
                        }
                    }
                    contextBoost = Math.min(0.4, contextBoost);
                }
                double score = 0.5 * sem + 0.2 * recency + 0.2 * importance + familiarity + contextBoost;
                hits.add(new Hit(e, score));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        List<Hit> top = hits.subList(0, Math.min(topK, hits.size()));
        for (Hit h : top) h.entry().touch(now);
        return new ArrayList<>(top);
    }
}
