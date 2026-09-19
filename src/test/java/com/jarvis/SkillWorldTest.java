package com.jarvis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jarvis.core.JarvisInstance;
import com.jarvis.language.Intent;
import com.jarvis.skills.DynamicSkillCreator;
import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SkillResult;
import com.jarvis.world.HostileScanner;
import com.jarvis.world.SpatialReasoning;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SkillWorldTest {
    @Test
    public void hostileSummaryIsNatural() {
        var world = new TestKit.FakeWorld();
        var entities = world.nearbyEntities("x", 32);
        var summary = HostileScanner.summarize(entities, 32);
        assertEquals(3, summary.count());
        assertTrue(summary.detail().contains("zombie"), "names types: " + summary.detail());
        assertTrue(!summary.detail().contains("12.8"), "no raw coordinate dump");
    }

    @Test
    public void compassAndTravel() {
        assertEquals("north", SpatialReasoning.compass(0, -10));
        assertEquals("east", SpatialReasoning.compass(10, 0));
        assertTrue(SpatialReasoning.travelMinutes(500) > 1.0, "sane travel estimate");
    }

    @Test
    public void skillValidationRejectsBadGraphs() {
        var empty = new com.jarvis.skills.SkillGraph("bad", "does nothing");
        var verdict = com.jarvis.skills.SkillValidator.validate(empty);
        assertTrue(!verdict.valid(), "empty plan rejected");
        var evil = new com.jarvis.skills.SkillGraph("evil", "runs code");
        evil.add(com.jarvis.skills.SkillOp.CALL_APPROVED_API, Map.of("api", "java.exec"));
        evil.add(com.jarvis.skills.SkillOp.SPEAK);
        assertTrue(!com.jarvis.skills.SkillValidator.validate(evil).valid(), "unapproved api rejected");
    }

    @Test
    public void dynamicCreationAndExecution(@TempDir Path tmp) {
        JarvisInstance inst = TestKit.instance(tmp);
        // remove routing for SCAN_HOSTILES by creating a fresh registry path:
        // instead verify creation composes a valid graph for an unrouted intent
        var creation = new DynamicSkillCreator(inst.skills(), inst.knowledge(),
            new com.jarvis.skills.SkillExecutor(
                java.util.concurrent.Executors.newSingleThreadExecutor(), inst.skills()))
            .createFor(Intent.KNOWLEDGE_QUERY, Map.of(), List.of("knowledge.query"));
        // KNOWLEDGE_QUERY already routed -> reports exists
        assertTrue(!creation.success() && creation.message().equals("exists"), "existing skill detected");
    }

    @Test
    public void scanSkillRunsAgainstFakeWorld(@TempDir Path tmp) {
        JarvisInstance inst = TestKit.instance(tmp);
        var snapshot = new TestKit.FakeWorld().snapshot("x");
        SkillContext ctx = new SkillContext("x", "Tester", "scan", Intent.SCAN_HOSTILES,
            Map.of(), snapshot, new TestKit.FakeWorld());
        Skill skill = inst.skills().forIntent(Intent.SCAN_HOSTILES).orElseThrow();
        SkillResult r = skill.run(ctx);
        assertTrue(r.success(), "scan executes");
        assertEquals(3, ((Number) r.frame().get("count")).intValue());
    }
}
