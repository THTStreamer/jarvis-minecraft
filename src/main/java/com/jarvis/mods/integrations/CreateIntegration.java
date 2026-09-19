package com.jarvis.mods.integrations;

import com.jarvis.api.IModIntegration;
import com.jarvis.knowledge.KnowledgeGraph;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional Create integration. Learns stress/rotation/kinetics from observed
 * goggle displays and machine interactions - no hard dependency on Create.
 * Active only when the glue reports the "create" mod id as present.
 */
public final class CreateIntegration implements IModIntegration {
    private static final Pattern SU = Pattern.compile("(\\d[\\d,\\.]*)\\s*(?:su|stress)", Pattern.CASE_INSENSITIVE);
    private final KnowledgeGraph knowledge;

    public CreateIntegration(KnowledgeGraph knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public String modId() { return "create"; }

    @Override
    public void onModDetected(Map<String, Object> context) {
        knowledge.add("create", "has-mechanic", "stress units", "integration", 0.85f);
        knowledge.add("create", "has-mechanic", "rotation", "integration", 0.85f);
        knowledge.add("create", "has-mechanic", "kinetics", "integration", 0.8f);
        knowledge.add("stress units", "observed-via", "goggles display", "integration", 0.8f);
    }

    @Override
    public void onObservation(String eventType, Map<String, String> data, Map<String, Object> context) {
        if (eventType.equals("goggles")) {
            String text = data.getOrDefault("text", "");
            String block = data.getOrDefault("block", "create machine");
            Matcher m = SU.matcher(text);
            if (m.find()) {
                String value = m.group(1);
                knowledge.add(block, "uses-stress", value + " SU", "goggles", 0.75f);
                if (text.toLowerCase().contains("overstress") || text.toLowerCase().contains("overstressed")) {
                    knowledge.add(block, "state", "overstressed", "goggles", 0.8f);
                }
            }
        }
        if (eventType.equals("gui_open")) {
            String block = data.getOrDefault("block", "");
            if (block.startsWith("create:")) {
                knowledge.add(block, "is-a", "create machine", "observation", 0.7f);
            }
        }
    }

    /** Diagnose from learned entries; returns null when nothing is known. */
    public String diagnose(String blockId) {
        var using = knowledge.query(blockId, "uses-stress");
        var state = knowledge.query(blockId, "state");
        if (!state.isEmpty() && state.get(0).object().contains("overstress")) {
            String demand = using.isEmpty() ? "more than the network provides"
                : "approximately " + using.get(0).object();
            return "The system appears to be overstressed. This setup is demanding " + demand
                + " while the available capacity appears to be lower. "
                + "Try adding another water wheel or windmill, or disconnecting part of the network.";
        }
        if (!using.isEmpty()) {
            return "Based on my observations, this setup uses approximately "
                + using.get(0).object() + " of stress.";
        }
        return null;
    }
}
