package com.jarvis.knowledge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Semantic concept frames: "Diamond" links to item/ore/resource/mining/
 * crafting/rarity/location/tags so Jarvis generalizes instead of matching strings.
 */
public final class SemanticKnowledge {
    private final KnowledgeGraph graph;

    public SemanticKnowledge(KnowledgeGraph graph) {
        this.graph = graph;
        seedBaseConcepts();
    }

    private void seedBaseConcepts() {
        define("diamond", Map.of(
            "category", "resource",
            "forms", "diamond_ore,deepslate_diamond_ore,diamond",
            "obtained_by", "mining",
            "found", "deep underground",
            "used_for", "tools,armor,crafting",
            "rarity", "rare"));
        define("village", Map.of(
            "category", "structure",
            "contains", "villagers,beds,workstations,food",
            "variants", "plains,desert,savanna,taiga,snowy",
            "interest", "food,trading,shelter"));
        define("hostile mob", Map.of(
            "category", "entity",
            "examples", "zombie,skeleton,creeper,spider,enderman,witch",
            "active", "night and dark places",
            "threat", "damages the player"));
        define("stress", Map.of(
            "category", "create mechanic",
            "unit", "stress units (SU)",
            "meaning", "rotational power demand vs capacity",
            "observed_via", "goggles display"));
        define("source", Map.of(
            "category", "ars nouveau resource",
            "meaning", "magical energy for spells",
            "stored_in", "source jar"));
    }

    public void define(String concept, Map<String, String> frame) {
        graph.defineConcept(concept, frame);
        for (Map.Entry<String, String> e : frame.entrySet()) {
            graph.add(concept, e.getKey(), e.getValue(), "semantic-seed", 0.7f);
        }
    }

    /** Learn a concept from observation: properties discovered in the world. */
    public void learnConcept(String concept, Map<String, String> properties, String source) {
        Map<String, String> merged = new LinkedHashMap<>(graph.concept(concept));
        merged.putAll(properties);
        graph.defineConcept(concept, merged);
        for (Map.Entry<String, String> e : properties.entrySet()) {
            graph.add(concept, e.getKey(), e.getValue(), source, 0.6f);
        }
    }

    public Map<String, String> describe(String concept) {
        return graph.concept(concept);
    }

    public List<KnowledgeEntry> related(String concept) {
        return graph.queryObject(concept);
    }
}
