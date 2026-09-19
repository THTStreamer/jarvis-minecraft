package com.jarvis.skills;

/** Validates skill graphs before they may execute or persist. */
public final class SkillValidator {
    private SkillValidator() {}

    public record Verdict(boolean valid, String reason) {}

    public static Verdict validate(SkillGraph graph) {
        if (graph.steps().isEmpty()) {
            return new Verdict(false, "empty plan");
        }
        if (graph.steps().size() > 24) {
            return new Verdict(false, "plan too long");
        }
        boolean speaks = false;
        for (SkillGraph.Step s : graph.steps()) {
            if (s.op() == null) return new Verdict(false, "null op");
            if (s.op() == SkillOp.SPEAK) speaks = true;
            if (s.op() == SkillOp.CALL_APPROVED_API) {
                String api = s.args().getOrDefault("api", "");
                if (!ApprovedApis.isAllowed(api)) {
                    return new Verdict(false, "api not approved: " + api);
                }
            }
        }
        if (!speaks) {
            return new Verdict(false, "plan never answers the player");
        }
        return new Verdict(true, "ok");
    }

    /** Closed allow-list for CALL_APPROVED_API targets. */
    public static final class ApprovedApis {
        private ApprovedApis() {}

        public static boolean isAllowed(String api) {
            return switch (api) {
                case "world.scan", "world.locate", "world.guide", "memory.store",
                     "knowledge.query", "voice.speak", "network.send" -> true;
                default -> false;
            };
        }
    }
}
