package com.jarvis.ai.attention;

import com.jarvis.util.Floats;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small LRU cache of recent sentence vectors so repeated/overlapping context
 * (follow-up questions, rephrased retries) does not re-run the encoder.
 */
public final class AttentionCache {
    private final int capacity;
    private final LinkedHashMap<String, float[]> cache;

    public AttentionCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > AttentionCache.this.capacity;
            }
        };
    }

    public synchronized float[] get(String key) {
        float[] v = cache.get(key);
        return v == null ? null : v.clone();
    }

    public synchronized void put(String key, float[] vec) {
        cache.put(key, vec.clone());
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }

    public static float pooledSimilarity(float[] a, float[] b) {
        return Floats.cosine(a, b);
    }
}
