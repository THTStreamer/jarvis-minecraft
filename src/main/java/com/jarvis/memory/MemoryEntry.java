package com.jarvis.memory;

/** One memory record with relevance metadata. */
public final class MemoryEntry {
    private final String id;
    private final MemoryType type;
    private final String text;
    private final float[] vector; // may be null until embedded
    private final long createdAt;
    private long lastAccess;
    private int accesses;
    private float importance;
    private String context;

    public MemoryEntry(String id, MemoryType type, String text, float[] vector,
                       long createdAt, float importance, String context) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.vector = vector;
        this.createdAt = createdAt;
        this.lastAccess = createdAt;
        this.accesses = 0;
        this.importance = importance;
        this.context = context == null ? "" : context;
    }

    public String id() { return id; }
    public MemoryType type() { return type; }
    public String text() { return text; }
    public float[] vector() { return vector; }
    public long createdAt() { return createdAt; }
    public long lastAccess() { return lastAccess; }
    public int accesses() { return accesses; }
    public float importance() { return importance; }
    public String context() { return context; }

    public void setImportance(float importance) { this.importance = importance; }
    public void setContext(String context) { this.context = context; }

    public void touch(long now) {
        this.lastAccess = now;
        this.accesses++;
    }
}
