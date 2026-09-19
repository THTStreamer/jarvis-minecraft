package com.jarvis.mods;

import com.jarvis.ai.tokenizer.JarvisTokenizer;
import com.jarvis.ai.training.TrainingPipeline;
import com.jarvis.knowledge.KnowledgeGraph;
import com.jarvis.knowledge.SemanticKnowledge;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Discovers installed mods (id, name, version) via a supplier from the glue. */
public final class ModDiscovery {
    private final Supplier<List<ModInfo>> supplier;
    private volatile List<ModInfo> cache = List.of();

    public ModDiscovery(Supplier<List<ModInfo>> supplier) {
        this.supplier = supplier;
    }

    public List<ModInfo> discover() {
        try {
            cache = List.copyOf(supplier.get());
        } catch (Exception e) {
            cache = List.of();
        }
        return cache;
    }

    public List<ModInfo> cached() {
        return cache;
    }

    public boolean isPresent(String modId) {
        return cache.stream().anyMatch(m -> m.id().equals(modId));
    }
}
