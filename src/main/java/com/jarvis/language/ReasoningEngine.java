package com.jarvis.language;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-step reasoning: decomposes requests like "find a village where I can
 * get food" into ordered sub-goals with dependency tracking. Only conclusions
 * are exposed; internal traces stay in the debug view.
 */
public final class ReasoningEngine {
    public record Step(int order, String goal, String dependsOn, String status) {}
    public record Plan(String objective, List<Step> steps, String conclusion) {}

    public Plan decompose(String request, Intent intent, Map<String, Object> entities) {
        List<Step> steps = new ArrayList<>();
        String objective = request.trim();
        switch (intent) {
            case LOCATE_STRUCTURE -> {
                steps.add(new Step(1, "identify target structure", "", "planned"));
                if (entities.containsKey("biome") || hasFoodNeed(request)) {
                    steps.add(new Step(2, "apply filters (variant/resources)", "1", "planned"));
                }
                steps.add(new Step(steps.size() + 1, "search world within radius", String.valueOf(steps.size()), "planned"));
                steps.add(new Step(steps.size() + 1, "rank candidates by distance", String.valueOf(steps.size()), "planned"));
                steps.add(new Step(steps.size() + 1, "explain best result", String.valueOf(steps.size()), "planned"));
            }
            case SCAN_HOSTILES, SCAN_AREA -> {
                steps.add(new Step(1, "collect nearby entities", "", "planned"));
                steps.add(new Step(2, "classify threats", "1", "planned"));
                steps.add(new Step(3, "summarize spatially", "2", "planned"));
            }
            case MACHINE_QUESTION -> {
                steps.add(new Step(1, "identify observed machine", "", "planned"));
                steps.add(new Step(2, "recall learned mechanics", "1", "planned"));
                steps.add(new Step(3, "diagnose state", "2", "planned"));
            }
            case SKILL_CREATE -> {
                steps.add(new Step(1, "check existing skills", "", "planned"));
                steps.add(new Step(2, "search available actions", "1", "planned"));
                steps.add(new Step(3, "construct skill plan", "2", "planned"));
                steps.add(new Step(4, "validate safely", "3", "planned"));
            }
            default -> steps.add(new Step(1, "answer directly", "", "planned"));
        }
        return new Plan(objective, steps, "plan ready: " + steps.size() + " steps");
    }

    private boolean hasFoodNeed(String request) {
        String l = request.toLowerCase();
        return l.contains("food") || l.contains("eat") || l.contains("bread") || l.contains("farm");
    }

    public Map<String, Object> explain(Plan plan) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("objective", plan.objective());
        out.put("steps", plan.steps().size());
        out.put("conclusion", plan.conclusion());
        return out;
    }
}
