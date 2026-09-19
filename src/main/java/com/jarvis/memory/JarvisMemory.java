package com.jarvis.memory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Layered memory: short-term (conversation), working (current task),
 * episodic (events), semantic (facts), procedural (how-to), relationship,
 * mod knowledge and world memory. Each layer is capacity-bounded.
 */
public final class JarvisMemory {
    private final Map<MemoryType, List<MemoryEntry>> layers = new EnumMap<>(MemoryType.class);
    private final Map<MemoryType, Integer> caps = new EnumMap<>(MemoryType.class);

    public JarvisMemory() {
        caps.put(MemoryType.SHORT_TERM, 60);
        caps.put(MemoryType.WORKING, 12);
        caps.put(MemoryType.EPISODIC, 300);
        caps.put(MemoryType.SEMANTIC, 500);
        caps.put(MemoryType.PROCEDURAL, 200);
        caps.put(MemoryType.RELATIONSHIP, 100);
        caps.put(MemoryType.MOD, 500);
        caps.put(MemoryType.WORLD, 300);
        for (MemoryType t : MemoryType.values()) layers.put(t, new ArrayList<>());
    }

    public synchronized MemoryEntry store(MemoryType type, String text, float[] vector,
                                          float importance, String context) {
        MemoryEntry e = new MemoryEntry(UUID.randomUUID().toString(), type, text, vector,
            System.currentTimeMillis(), importance, context);
        List<MemoryEntry> layer = layers.get(type);
        layer.add(e);
        int cap = caps.getOrDefault(type, 200);
        while (layer.size() > cap) {
            // evict lowest importance, oldest first
            int idx = 0;
            for (int i = 1; i < layer.size(); i++) {
                MemoryEntry a = layer.get(i);
                MemoryEntry b = layer.get(idx);
                if (a.importance() < b.importance()
                        || (a.importance() == b.importance() && a.createdAt() < b.createdAt())) {
                    idx = i;
                }
            }
            layer.remove(idx);
        }
        return e;
    }

    public synchronized List<MemoryEntry> all(MemoryType type) {
        return new ArrayList<>(layers.get(type));
    }

    public synchronized int count(MemoryType type) {
        return layers.get(type).size();
    }

    public synchronized int countAll() {
        int n = 0;
        for (List<MemoryEntry> l : layers.values()) n += l.size();
        return n;
    }

    public synchronized boolean forgetType(MemoryType type) {
        layers.get(type).clear();
        return true;
    }

    public synchronized void clearAll() {
        for (List<MemoryEntry> l : layers.values()) l.clear();
    }

    public synchronized List<MemoryEntry> conversation(int lastN) {
        List<MemoryEntry> st = layers.get(MemoryType.SHORT_TERM);
        int from = Math.max(0, st.size() - lastN);
        return new ArrayList<>(st.subList(from, st.size()));
    }

    public synchronized void setWorking(String task) {
        List<MemoryEntry> working = layers.get(MemoryType.WORKING);
        working.clear();
        store(MemoryType.WORKING, task, null, 1.0f, "task");
    }

    // ---- persistence helpers ----

    public synchronized List<Map<String, Object>> export() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<MemoryType, List<MemoryEntry>> e : layers.entrySet()) {
            for (MemoryEntry m : e.getValue()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("type", m.type().name());
                map.put("text", m.text());
                map.put("importance", m.importance());
                map.put("context", m.context());
                map.put("createdAt", m.createdAt());
                out.add(map);
            }
        }
        return out;
    }

    public synchronized void restore(List<Map<String, Object>> data) {
        clearAll();
        for (Map<String, Object> map : data) {
            try {
                MemoryType t = MemoryType.valueOf(String.valueOf(map.get("type")));
                String text = String.valueOf(map.getOrDefault("text", ""));
                float imp = ((Number) map.getOrDefault("importance", 0.5)).floatValue();
                String ctx = String.valueOf(map.getOrDefault("context", ""));
                store(t, text, null, imp, ctx);
            } catch (Exception ignored) {}
        }
    }
}
