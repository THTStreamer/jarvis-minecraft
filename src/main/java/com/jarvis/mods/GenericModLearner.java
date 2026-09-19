package com.jarvis.mods;

import com.jarvis.knowledge.KnowledgeGraph;
import java.util.Map;

/**
 * Generic learner for unknown mods: registry introspection, tooltip/GUI/
 * recipe/interaction observation and item metadata - the foundation for
 * effectively unlimited mod knowledge without per-mod code.
 */
public final class GenericModLearner {
    private final KnowledgeGraph knowledge;

    public GenericModLearner(KnowledgeGraph knowledge) {
        this.knowledge = knowledge;
    }

    public void learnTooltip(String itemId, String line) {
        if (itemId.isBlank() || line.isBlank()) return;
        knowledge.add(itemId, "tooltip", line, "generic-learner", 0.55f);
        String lower = line.toLowerCase();
        if (lower.contains("machine") || lower.contains("generates") || lower.contains("processes")) {
            knowledge.add(itemId, "is-a", "machine", "generic-learner", 0.5f);
        }
        if (lower.contains("food") || lower.contains("eat") || lower.contains("hunger")) {
            knowledge.add(itemId, "is-a", "food", "generic-learner", 0.55f);
        }
    }

    public void learnGuiInteraction(String blockId, String guiTitle, Map<String, String> slots) {
        if (blockId.isBlank()) return;
        knowledge.add(blockId, "opens-gui", guiTitle, "generic-learner", 0.6f);
        if (!slots.isEmpty()) {
            knowledge.add(blockId, "has-slots", String.join(",", slots.keySet()), "generic-learner", 0.55f);
            for (Map.Entry<String, String> e : slots.entrySet()) {
                knowledge.add(blockId, "slot-" + e.getKey(), e.getValue(), "generic-learner", 0.5f);
            }
        }
    }

    public void learnRecipe(String summary) {
        if (summary.isBlank()) return;
        knowledge.add("recipe", "discovered", summary, "generic-learner", 0.6f);
    }
}
