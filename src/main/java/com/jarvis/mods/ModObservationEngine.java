package com.jarvis.mods;

import com.jarvis.knowledge.KnowledgeGraph;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Converts player-action observations (pickup, place, break, GUI, craft,
 * smelt, damage, advancement, tooltip) into structured knowledge.
 */
public final class ModObservationEngine {
    private final KnowledgeGraph knowledge;
    private BiConsumer<String, Float> memoryHook = (t, i) -> {};

    public ModObservationEngine(KnowledgeGraph knowledge) {
        this.knowledge = knowledge;
    }

    public void setMemoryHook(BiConsumer<String, Float> hook) {
        this.memoryHook = hook;
    }

    public void observe(String eventType, Map<String, String> data) {
        switch (eventType) {
            case "item_pickup" -> {
                String item = data.getOrDefault("item", "");
                if (!item.isEmpty()) {
                    knowledge.add("player", "picked-up", item, "observation", 0.8f);
                    knowledge.add(item, "is-a", "item", "observation", 0.7f);
                }
            }
            case "block_place", "block_break" -> {
                String block = data.getOrDefault("block", "");
                if (!block.isEmpty()) {
                    knowledge.add("player", eventType.equals("block_place") ? "placed" : "broke",
                        block, "observation", 0.8f);
                    memoryHook.accept("Player " + eventType.replace('_', ' ') + "d " + block, 0.3f);
                }
            }
            case "gui_open" -> {
                String gui = data.getOrDefault("gui", "");
                String block = data.getOrDefault("block", "");
                if (!gui.isEmpty()) {
                    knowledge.add(block.isEmpty() ? "player" : block, "opens-gui", gui, "observation", 0.75f);
                    if (!block.isEmpty()) {
                        knowledge.add(block, "is-a", "machine", "observation", 0.55f);
                    }
                }
            }
            case "craft" -> {
                String result = data.getOrDefault("result", "");
                if (!result.isEmpty()) {
                    knowledge.add("player", "crafted", result, "observation", 0.85f);
                    knowledge.add(result, "crafted-by", "player", "observation", 0.7f);
                }
            }
            case "tooltip" -> {
                String item = data.getOrDefault("item", "");
                String line = data.getOrDefault("line", "");
                if (!item.isEmpty() && !line.isEmpty()) {
                    knowledge.add(item, "tooltip", line, "observation", 0.6f);
                }
            }
            case "advancement" -> {
                String adv = data.getOrDefault("id", "");
                if (!adv.isEmpty()) {
                    knowledge.add("player", "completed", adv, "observation", 0.9f);
                    memoryHook.accept("Player earned advancement " + adv, 0.5f);
                }
            }
            case "goggles" -> {
                // Create-style display observation; handled jointly with the integration
                String text = data.getOrDefault("text", "");
                String block = data.getOrDefault("block", "");
                if (!text.isEmpty()) {
                    knowledge.add(block.isEmpty() ? "machine" : block, "displays", text, "observation", 0.7f);
                }
            }
            case "spell" -> {
                String glyph = data.getOrDefault("glyph", "");
                if (!glyph.isEmpty()) {
                    knowledge.add("player", "used-glyph", glyph, "observation", 0.75f);
                }
            }
            default -> {
                String target = data.getOrDefault("target", data.getOrDefault("item",
                    data.getOrDefault("block", "")));
                if (!target.isEmpty()) {
                    knowledge.add("player", "interacted", target, "observation", 0.5f);
                }
            }
        }
    }
}
