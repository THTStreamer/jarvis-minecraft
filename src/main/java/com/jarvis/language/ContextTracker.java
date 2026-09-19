package com.jarvis.language;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversational context: current target, recent turns, pronoun resolution.
 * Lets "How far?", "Can you mark it?" and "What about the closest one?"
 * resolve against the village journey established earlier.
 */
public final class ContextTracker {
    public record Turn(String speaker, String text, Intent intent) {}

    private final Deque<Turn> turns = new ArrayDeque<>();
    private final int maxTurns;
    private Map<String, Object> currentTarget = new LinkedHashMap<>();
    private Intent lastIntent = Intent.UNKNOWN;

    public ContextTracker(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    public synchronized void addPlayer(String text, Intent intent, Map<String, Object> entities) {
        push(new Turn("player", text, intent));
        if (!entities.isEmpty()) {
            if (currentTarget.isEmpty() || intent == Intent.LOCATE_STRUCTURE
                    || intent == Intent.LOCATE_BIOME || intent == Intent.SCAN_HOSTILES) {
                currentTarget = new LinkedHashMap<>(entities);
            } else {
                currentTarget.putAll(entities);
            }
        }
        lastIntent = intent;
    }

    public synchronized void addJarvis(String text) {
        push(new Turn("jarvis", text, Intent.UNKNOWN));
    }

    private void push(Turn t) {
        turns.addLast(t);
        while (turns.size() > maxTurns) turns.pollFirst();
    }

    public synchronized List<Turn> recent() {
        return new ArrayList<>(turns);
    }

    public synchronized Map<String, Object> currentTarget() {
        return new LinkedHashMap<>(currentTarget);
    }

    public synchronized void setCurrentTarget(Map<String, Object> target) {
        this.currentTarget = new LinkedHashMap<>(target);
    }

    public synchronized Intent lastIntent() {
        return lastIntent;
    }

    /** Resolve follow-ups: merge stored target entities into the new request. */
    public synchronized Map<String, Object> resolve(Map<String, Object> fresh, Intent intent) {
        if ((intent == Intent.FOLLOW_UP || intent == Intent.DISTANCE_QUERY
                || intent == Intent.TRAVEL_TIME_QUERY || intent == Intent.NAVIGATE_GUIDE) && fresh.isEmpty()) {
            return currentTarget();
        }
        Map<String, Object> merged = new LinkedHashMap<>(currentTarget);
        merged.putAll(fresh);
        return merged;
    }

    public synchronized String lastJarvisSaying() {
        List<Turn> all = new ArrayList<>(turns);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i).speaker().equals("jarvis")) return all.get(i).text();
        }
        return "";
    }
}
