package com.jarvis.knowledge;

/** Confidence value in [0,1] with reinforcement update rules. */
public final class Confidence {
    private float value;

    public Confidence(float initial) {
        this.value = clamp(initial);
    }

    public float value() { return value; }

    public void confirm(float weight) {
        value = clamp(value + (1f - value) * clamp(weight) * 0.25f);
    }

    public void contradict(float weight) {
        value = clamp(value - value * clamp(weight) * 0.35f);
    }

    public void set(float v) { this.value = clamp(v); }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
