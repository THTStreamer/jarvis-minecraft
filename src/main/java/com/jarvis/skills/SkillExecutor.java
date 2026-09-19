package com.jarvis.skills;

import com.jarvis.language.Intent;
import com.jarvis.navigation.LocationQuery;
import com.jarvis.world.EntityInfo;
import com.jarvis.world.LocatedStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

/**
 * Executes skill graphs step by step against the sandboxed op set, plus
 * direct skill execution. Heavy work runs on the AI pool; any world mutation
 * is already thread-safe behind {@link WorldAccess}.
 */
public final class SkillExecutor {
    private final ExecutorService aiPool;
    private final SkillRegistry registry;
    /** Hook for STORE_MEMORY ops: (text, context). Set by the instance wiring. */
    private BiConsumer<String, String> memoryHook = (t, c) -> {};

    public SkillExecutor(ExecutorService aiPool, SkillRegistry registry) {
        this.aiPool = aiPool;
        this.registry = registry;
    }

    public void setMemoryHook(BiConsumer<String, String> hook) {
        this.memoryHook = hook;
    }

    public SkillResult execute(String skillId, SkillContext ctx) {
        return registry.get(skillId)
            .map(s -> s.run(ctx))
            .orElseGet(() -> SkillResult.fail(ctx.intent(),
                SkillResult.map("failure", "unknown skill " + skillId), "unknown skill"));
    }

    public Future<SkillResult> executeAsync(String skillId, SkillContext ctx) {
        return aiPool.submit(() -> execute(skillId, ctx));
    }

    /** Execute a sandboxed op graph step by step with real reviewed implementations. */
    public SkillResult executeGraph(SkillGraph graph, SkillContext ctx) {
        try {
            List<EntityInfo> observed = new ArrayList<>();
            List<EntityInfo> filtered = new ArrayList<>();
            LocatedStructure located = null;
            for (SkillGraph.Step step : graph.steps()) {
                switch (step.op()) {
                    case OBSERVE_ENTITY -> {
                        double radius = parseDouble(step.args().getOrDefault("radius", "32"), 32);
                        observed = new ArrayList<>(
                            ctx.world().nearbyEntities(ctx.playerId(), radius));
                        ctx.put("observed", observed);
                    }
                    case FILTER_ENTITY -> {
                        String kind = step.args().getOrDefault("kind", "hostile");
                        filtered = new ArrayList<>();
                        for (EntityInfo e : observed) {
                            if (kind.equals("hostile") && e.hostile()) filtered.add(e);
                            else if (kind.equals("all")) filtered.add(e);
                        }
                        if (filtered.isEmpty() && !kind.equals("hostile")) filtered = new ArrayList<>(observed);
                        ctx.put("filtered", filtered);
                    }
                    case GET_POSITION -> ctx.put("position", ctx.snapshot());
                    case CALCULATE_DISTANCE, CALCULATE_DIRECTION -> {
                        // distances/directions are precomputed on the DTOs; nothing to do
                    }
                    case QUERY_WORLD -> {
                        LocationQuery q = LocationQuery.interpret(ctx.entities(), 5000);
                        located = q.kind() == LocationQuery.Kind.BIOME
                            ? ctx.world().findBiome(ctx.playerId(), q).orElse(null)
                            : ctx.world().findStructure(ctx.playerId(), q).orElse(null);
                        ctx.put("located", located);
                    }
                    case QUERY_REGISTRY, QUERY_RECIPE, QUERY_BLOCK, QUERY_ITEM ->
                        ctx.put("registry", ctx.entities());
                    case NAVIGATE -> {
                        if (located != null) {
                            ctx.world().setGuideTarget(ctx.playerId(), located.x(), located.y(),
                                located.z(), located.id());
                        }
                    }
                    case STORE_MEMORY ->
                        memoryHook.accept(ctx.input(), "skill:" + graph.skillId());
                    case DISPLAY, SPEAK -> {
                        // terminal ops: result assembled below
                    }
                    case CALL_APPROVED_API -> {
                        String api = step.args().getOrDefault("api", "");
                        if (!SkillValidator.ApprovedApis.isAllowed(api)) {
                            return SkillResult.fail(ctx.intent(),
                                SkillResult.map("failure", "api not approved"), "sandbox");
                        }
                    }
                }
            }
            if (located != null) {
                return SkillResult.ok(ctx.intent() == Intent.UNKNOWN ? Intent.LOCATE_STRUCTURE : ctx.intent(),
                    SkillResult.map("name", located.id(), "distance", located.distanceBlocks(),
                        "direction", located.direction(), "x", located.x(), "y", located.y(), "z", located.z()));
            }
            if (!filtered.isEmpty() || !observed.isEmpty()) {
                List<EntityInfo> hostiles = filtered.isEmpty() ? observed : filtered;
                ctx.put("hostiles", hostiles);
                return SkillResult.ok(Intent.SCAN_HOSTILES,
                    SkillResult.map("count", hostiles.size(), "radius", 32));
            }
            return SkillResult.ok(ctx.intent(), SkillResult.map("done", true));
        } catch (Exception e) {
            return SkillResult.fail(ctx.intent(), SkillResult.map("failure", "graph error"), e.getMessage());
        }
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Interpret a sandboxed graph into a human-readable plan (used for learning + debug UI). */
    public String describePlan(SkillGraph graph) {
        StringBuilder sb = new StringBuilder(graph.skillId()).append(": ").append(graph.goal()).append("\n");
        int i = 1;
        for (SkillGraph.Step s : graph.steps()) {
            sb.append(i++).append(". ").append(s.op());
            if (!s.args().isEmpty()) sb.append(" ").append(s.args());
            sb.append("\n");
        }
        return sb.toString();
    }
}
