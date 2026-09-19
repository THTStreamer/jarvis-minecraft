package com.jarvis.bootstrap;

import com.jarvis.ai.tokenizer.JarvisTokenizer;
import com.jarvis.ai.training.TrainingPipeline;
import com.jarvis.knowledge.KnowledgeGraph;
import com.jarvis.knowledge.SemanticKnowledge;
import com.jarvis.mods.ModInfo;
import com.jarvis.mods.RegistryView;
import java.util.List;
import java.util.Locale;

/**
 * Pre-training sweep: curated corpus facts plus registry-derived semantic
 * sentences for every installed mod (blocks, items, entities, biomes).
 * Everything lands in the player's private graph, vocabulary and training
 * queue - structured representations, never raw dumps.
 */
public final class ModBootstrap {
    public record Report(int corpusFacts, int registryFacts, int sentences, int vocabAdded) {}

    private final KnowledgeGraph knowledge;
    private final SemanticKnowledge semantics;
    private final TrainingPipeline training;
    private final JarvisTokenizer tokenizer;

    public ModBootstrap(KnowledgeGraph knowledge, SemanticKnowledge semantics,
                        TrainingPipeline training, JarvisTokenizer tokenizer) {
        this.knowledge = knowledge;
        this.semantics = semantics;
        this.training = training;
        this.tokenizer = tokenizer;
    }

    public Report run(List<ModInfo> mods, RegistryView view, int perModCap) {
        int corpusFacts = 0;
        int registryFacts = 0;
        int sentences = 0;
        int vocabBefore = tokenizer.vocabulary().size();

        for (BootstrapCorpus.Fact fact : BootstrapCorpus.load()) {
            knowledge.add(fact.s(), fact.r(), fact.o(), "bootstrap", fact.c());
            training.learnFact(fact.sentence());
            learnWords(fact.s() + " " + fact.o());
            corpusFacts++;
            sentences++;
        }

        int cap = Math.max(20, perModCap);
        for (ModInfo mod : mods) {
            if (mod.id().equals("minecraft") || mod.id().equals("neoforge")
                    || mod.id().equals("jarvis") || mod.id().equals("voicechat")
                    || mod.id().equals("voicechat_api")) {
                continue; // vanilla is covered by the corpus; skip infra mods
            }
            String display = mod.name();
            for (String b : view.blockIds(mod.id(), cap)) {
                String name = pretty(b);
                knowledge.add(mod.id(), "contains-block", b, "bootstrap", 0.85f);
                knowledge.add(b, "is-a", "block", "bootstrap", 0.85f);
                training.learnFact(name + " is a block from the " + display + " mod.");
                learnWords(name);
                registryFacts += 2;
                sentences++;
            }
            for (String i : view.itemIds(mod.id(), cap)) {
                String name = pretty(i);
                knowledge.add(mod.id(), "contains-item", i, "bootstrap", 0.85f);
                knowledge.add(i, "is-a", "item", "bootstrap", 0.85f);
                training.learnFact(name + " is an item from the " + display + " mod.");
                learnWords(name);
                registryFacts += 2;
                sentences++;
            }
            for (String e : view.entityIds(mod.id(), 30)) {
                String name = pretty(e);
                knowledge.add(mod.id(), "contains-entity", e, "bootstrap", 0.85f);
                training.learnFact(name + " is a creature from the " + display + " mod.");
                learnWords(name);
                registryFacts++;
                sentences++;
            }
            for (String biome : view.biomeIds(mod.id(), 20)) {
                knowledge.add(mod.id(), "has-biome", biome, "bootstrap", 0.8f);
                training.learnFact(pretty(biome) + " is a biome from the " + display + " mod.");
                registryFacts++;
                sentences++;
            }
        }
        return new Report(corpusFacts, registryFacts, sentences,
            tokenizer.vocabulary().size() - vocabBefore);
    }

    private void learnWords(String text) {
        for (String w : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").split("\\s+")) {
            if (w.length() >= 3) tokenizer.vocabulary().learn(w);
        }
    }

    private static String pretty(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return path.replace('_', ' ').replace('/', ' ');
    }
}
