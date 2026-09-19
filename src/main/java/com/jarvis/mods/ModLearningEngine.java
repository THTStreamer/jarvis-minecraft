package com.jarvis.mods;

import com.jarvis.ai.tokenizer.JarvisTokenizer;
import com.jarvis.ai.training.TrainingPipeline;
import com.jarvis.knowledge.KnowledgeGraph;
import com.jarvis.knowledge.SemanticKnowledge;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds structured semantic representations from registry data - not raw
 * dumps. For each mod it records contains-relations, learns vocabulary for
 * unknown item/block names, and trains the network on semantic sentences.
 */
public final class ModLearningEngine {
    private final KnowledgeGraph knowledge;
    private final SemanticKnowledge semantics;
    private final TrainingPipeline training;
    private final JarvisTokenizer tokenizer;

    public ModLearningEngine(KnowledgeGraph knowledge, SemanticKnowledge semantics,
                             TrainingPipeline training, JarvisTokenizer tokenizer) {
        this.knowledge = knowledge;
        this.semantics = semantics;
        this.training = training;
        this.tokenizer = tokenizer;
    }

    public record Report(String modId, int blocks, int items, int entities, int facts) {}

    public Report learnMod(ModInfo mod, RegistryView view) {
        int facts = 0;
        knowledge.add("mod", "installed", mod.id(), "discovery", 0.95f);
        knowledge.add(mod.id(), "has-name", mod.name(), "discovery", 0.95f);
        facts += 2;

        List<String> blocks = view.blockIds(mod.id(), 40);
        List<String> items = view.itemIds(mod.id(), 40);
        List<String> entities = view.entityIds(mod.id(), 20);

        for (String b : blocks) {
            String name = pretty(b);
            knowledge.add(mod.id(), "contains-block", b, "registry", 0.9f);
            knowledge.add(b, "is-a", "block", "registry", 0.9f);
            learnWords(name);
            training.learnFact(name + " is a block from the " + mod.name() + " mod.");
            facts += 2;
        }
        for (String i : items) {
            String name = pretty(i);
            knowledge.add(mod.id(), "contains-item", i, "registry", 0.9f);
            knowledge.add(i, "is-a", "item", "registry", 0.9f);
            learnWords(name);
            training.learnFact(name + " is an item from the " + mod.name() + " mod.");
            facts += 2;
        }
        for (String e : entities) {
            String name = pretty(e);
            knowledge.add(mod.id(), "contains-entity", e, "registry", 0.9f);
            learnWords(name);
            training.learnFact(name + " is a creature from the " + mod.name() + " mod.");
            facts += 1;
        }
        for (String recipe : view.recipeSummaries(mod.id(), 15)) {
            knowledge.add(mod.id(), "recipe", recipe, "registry", 0.75f);
            training.learnFact("In " + mod.name() + ": " + recipe + ".");
            facts += 1;
        }
        // semantic concept for the mod itself
        Map<String, String> frame = new LinkedHashMap<>();
        frame.put("category", "mod");
        frame.put("name", mod.name());
        frame.put("blocks", String.valueOf(view.blockCount(mod.id())));
        frame.put("items", String.valueOf(view.itemCount(mod.id())));
        semantics.learnConcept(mod.id(), frame, "registry");
        return new Report(mod.id(), view.blockCount(mod.id()), view.itemCount(mod.id()),
            view.entityCount(mod.id()), facts);
    }

    private void learnWords(String humanName) {
        for (String w : humanName.split("\\s+")) {
            if (w.length() >= 3) tokenizer.vocabulary().learn(w);
        }
    }

    private static String pretty(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return path.replace('_', ' ').replace('/', ' ');
    }
}
