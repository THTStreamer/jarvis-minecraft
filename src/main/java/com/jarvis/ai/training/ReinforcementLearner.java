package com.jarvis.ai.training;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight Minecraft-specific reinforcement layer. Learning modifies
 * policy weights, knowledge confidence and skill confidence - never Java code.
 */
public final class ReinforcementLearner {
    private final Map<String, Double> policy = new HashMap<>();
    private final double alpha;

    public ReinforcementLearner(double alpha) {
        this.alpha = alpha;
    }

    /** Reward in [-1, 1]. Positive reinforces the policy key, negative discourages it. */
    public synchronized void reward(String policyKey, double reward) {
        double clamped = Math.max(-1.0, Math.min(1.0, reward));
        double current = policy.getOrDefault(policyKey, 0.0);
        policy.put(policyKey, current + alpha * (clamped - current));
    }

    public synchronized double score(String policyKey) {
        return policy.getOrDefault(policyKey, 0.0);
    }

    public synchronized Map<String, Double> snapshot() {
        return new HashMap<>(policy);
    }

    public synchronized void restore(Map<String, Double> snapshot) {
        policy.clear();
        policy.putAll(snapshot);
    }

    /** Convenience: convert a boolean outcome into a reward update. */
    public void outcome(String policyKey, boolean success) {
        reward(policyKey, success ? 1.0 : -1.0);
    }
}
