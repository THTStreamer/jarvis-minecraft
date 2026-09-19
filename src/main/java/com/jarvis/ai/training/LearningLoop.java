package com.jarvis.ai.training;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Observe -> interpret -> predict -> act -> evaluate -> reward/penalty ->
 * update -> remember. Every cycle appends a trace entry consumed by memory
 * and reinforcement learning.
 */
public final class LearningLoop {
    public record Cycle(String observation, String prediction, String action,
                        boolean success, float reward, String note) {}

    private final List<Cycle> history = new ArrayList<>();
    private final int maxHistory;
    private Consumer<Cycle> listener = c -> {};

    public LearningLoop(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    public void onCycle(Consumer<Cycle> listener) {
        this.listener = listener;
    }

    public Cycle record(String observation, String prediction, String action,
                        boolean success, float reward, String note) {
        Cycle c = new Cycle(observation, prediction, action, success, reward, note);
        synchronized (history) {
            history.add(c);
            while (history.size() > maxHistory) history.remove(0);
        }
        listener.accept(c);
        return c;
    }

    public List<Cycle> recent(int n) {
        synchronized (history) {
            int from = Math.max(0, history.size() - n);
            return new ArrayList<>(history.subList(from, history.size()));
        }
    }

    public double successRate(int lastN) {
        List<Cycle> r = recent(lastN);
        if (r.isEmpty()) return 1.0;
        long ok = r.stream().filter(Cycle::success).count();
        return (double) ok / r.size();
    }
}
