package com.jarvis.mods.integrations;

import com.jarvis.api.IModIntegration;
import com.jarvis.knowledge.KnowledgeGraph;
import java.util.Map;

/**
 * Optional Ars Nouveau integration. Learns glyphs, spell components, source
 * requirements and progression from observations - no hard dependency.
 */
public final class ArsNouveauIntegration implements IModIntegration {
    private final KnowledgeGraph knowledge;

    public ArsNouveauIntegration(KnowledgeGraph knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public String modId() { return "ars_nouveau"; }

    @Override
    public void onModDetected(Map<String, Object> context) {
        knowledge.add("ars_nouveau", "has-mechanic", "glyphs", "integration", 0.85f);
        knowledge.add("ars_nouveau", "has-mechanic", "source", "integration", 0.85f);
        knowledge.add("ars_nouveau", "has-mechanic", "spell crafting", "integration", 0.8f);
        knowledge.add("source", "stored-in", "source jar", "integration", 0.8f);
    }

    @Override
    public void onObservation(String eventType, Map<String, String> data, Map<String, Object> context) {
        if (eventType.equals("spell")) {
            String glyph = data.getOrDefault("glyph", "");
            String need = data.getOrDefault("requires", "");
            if (!glyph.isEmpty()) {
                knowledge.add("spell", "uses-glyph", glyph, "observation", 0.7f);
                if (!need.isEmpty()) {
                    knowledge.add(glyph, "requires", need, "observation", 0.65f);
                }
            }
        }
        if (eventType.equals("gui_open")) {
            String block = data.getOrDefault("block", "");
            if (block.startsWith("ars_nouveau:")) {
                knowledge.add(block, "is-a", "ars machine", "observation", 0.7f);
            }
        }
    }

    /** Explain missing spell components from learned entries + inventory. */
    public String missingFor(String spell, java.util.List<String> inventory) {
        var reqs = knowledge.query(spell, "requires");
        if (reqs.isEmpty()) return null;
        StringBuilder missing = new StringBuilder();
        for (var r : reqs) {
            String need = r.object();
            boolean have = inventory.stream().anyMatch(i -> i.toLowerCase().contains(need.toLowerCase()));
            if (!have) {
                if (missing.length() > 0) missing.append(", ");
                missing.append(need);
            }
        }
        if (missing.length() == 0) {
            return "You appear to have everything needed for that spell.";
        }
        return "You're missing: " + missing + ".";
    }
}
