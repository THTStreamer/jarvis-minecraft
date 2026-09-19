package com.jarvis.skills;

import com.jarvis.knowledge.KnowledgeGraph;
import com.jarvis.language.Intent;
import java.util.List;
import java.util.Map;

/**
 * Dynamic skill creation. When no built-in skill covers a request, Jarvis
 * tries to compose a validated {@link SkillGraph} from sandboxed ops using
 * its own knowledge. On genuine failure it reports exactly:
 * "Sorry, sir, I cannot seem to create this skill."
 */
public final class DynamicSkillCreator {
    public static final String FAILURE_SENTENCE = "Sorry, sir, I cannot seem to create this skill.";

    private final SkillRegistry registry;
    private final KnowledgeGraph knowledge;
    private final SkillExecutor executor;

    public DynamicSkillCreator(SkillRegistry registry, KnowledgeGraph knowledge, SkillExecutor executor) {
        this.registry = registry;
        this.knowledge = knowledge;
        this.executor = executor;
    }

    public record Creation(boolean success, String skillId, SkillGraph graph, String message) {}

    public Creation createFor(Intent intent, Map<String, Object> entities, List<String> capabilities) {
        // 1. already have one?
        if (registry.forIntent(intent).isPresent()) {
            return new Creation(false, "", null, "exists");
        }
        // 2. compose a plan from known ops
        SkillGraph graph = compose(intent, entities, capabilities);
        if (graph == null) {
            return new Creation(false, "", null, FAILURE_SENTENCE);
        }
        // 3. validate
        SkillValidator.Verdict verdict = SkillValidator.validate(graph);
        if (!verdict.valid()) {
            return new Creation(false, "", null, FAILURE_SENTENCE);
        }
        // 4. register as a learned composite skill
        ComposedSkill skill = new ComposedSkill(graph, executor);
        registry.register(skill);
        registry.route(intent, skill.id());
        knowledge.add("skill", "created", skill.id(), "dynamic-creation", 0.6f);
        return new Creation(true, skill.id(), graph,
            "I believe I can learn how to do that, sir. Give me a moment.");
    }

    private SkillGraph compose(Intent intent, Map<String, Object> entities, List<String> capabilities) {
        boolean canScan = capabilities.contains("world.scan");
        boolean canLocate = capabilities.contains("world.locate");
        boolean canGuide = capabilities.contains("world.guide");
        boolean canQuery = capabilities.contains("knowledge.query");
        return switch (intent) {
            case KNOWLEDGE_QUERY, MOD_QUESTION, MACHINE_QUESTION, SPELL_QUESTION -> {
                if (!canQuery) yield null;
                SkillGraph g = new SkillGraph("learned_answer", "answer from learned knowledge");
                g.add(SkillOp.QUERY_REGISTRY).add(SkillOp.STORE_MEMORY).add(SkillOp.SPEAK);
                yield g;
            }
            case LOCATE_STRUCTURE, LOCATE_BIOME -> {
                if (!canLocate) yield null;
                SkillGraph g = new SkillGraph("learned_locate", "locate requested target");
                g.add(SkillOp.QUERY_WORLD).add(SkillOp.CALCULATE_DISTANCE)
                 .add(SkillOp.CALCULATE_DIRECTION).add(SkillOp.SPEAK);
                if (canGuide) g.add(SkillOp.NAVIGATE);
                yield g;
            }
            case SCAN_HOSTILES, SCAN_AREA -> {
                if (!canScan) yield null;
                SkillGraph g = new SkillGraph("learned_scan", "scan surroundings");
                g.add(SkillOp.OBSERVE_ENTITY).add(SkillOp.FILTER_ENTITY)
                 .add(SkillOp.CALCULATE_DISTANCE).add(SkillOp.CALCULATE_DIRECTION).add(SkillOp.SPEAK);
                yield g;
            }
            case REMEMBER -> {
                SkillGraph g = new SkillGraph("learned_remember", "store a memory");
                g.add(SkillOp.STORE_MEMORY).add(SkillOp.SPEAK);
                yield g;
            }
            default -> null;
        };
    }

    /** A learned composite skill backed by a validated graph; executes op by op. */
    public static final class ComposedSkill extends Skill {
        private final SkillGraph graph;
        private final SkillExecutor executor;

        public ComposedSkill(SkillGraph graph, SkillExecutor executor) {
            super(graph.skillId(), "Learned: " + graph.goal(), graph.goal(), "1", 0.55f);
            this.graph = graph;
            this.executor = executor;
        }

        public SkillGraph graph() { return graph; }

        @Override
        protected SkillResult execute(SkillContext ctx) {
            return executor.executeGraph(graph, ctx);
        }
    }
}
