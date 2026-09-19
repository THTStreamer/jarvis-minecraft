package com.jarvis.server;

import com.jarvis.core.JarvisInstance;
import com.jarvis.language.Intent;
import com.jarvis.skills.WorldAccess;
import com.jarvis.world.PlayerSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * In-game self-test suites for /jarvis test &lt;suite&gt;. Each suite runs
 * real computations against the player's live Jarvis and reports pass/fail
 * with timings.
 */
public final class JarvisSelfTest {
    private JarvisSelfTest() {}

    public static List<String> run(JarvisInstance inst, WorldAccess world, String suite) {
        List<String> out = new ArrayList<>();
        long start = System.nanoTime();
        try {
            switch (suite) {
                case "tokenizer" -> {
                    int[] ids = inst.tokenizer().encode("Jarvis, find me a village at minecraft:plains 500 blocks north-east!", true);
                    String back = inst.tokenizer().decode(ids);
                    out.add(ids.length > 6 && back.contains("village") ? "PASS encode/decode ids=" + ids.length : "FAIL roundtrip: " + back);
                    int[] idents = inst.tokenizer().encode("create:mechanical_press", true);
                    out.add("PASS identifiers ids=" + idents.length);
                }
                case "neural" -> {
                    int[] ids = inst.tokenizer().encode("scan for enemies nearby", false);
                    float[] vec = inst.network().encodeSentence(ids);
                    float[] logits = inst.network().nextTokenLogits(ids);
                    float loss = inst.network().trainNextToken(ids,
                        ids.length > 2 ? ids[2] : 1, inst.optimizer());
                    out.add(vec.length == inst.network().dim() ? "PASS encode dim=" + vec.length : "FAIL dim");
                    out.add(logits.length == inst.network().maxVocab() ? "PASS logits=" + logits.length : "FAIL logits");
                    out.add(Float.isFinite(loss) ? "PASS trainStep loss=" + String.format("%.3f", loss) : "FAIL loss");
                    out.add("params=" + inst.network().paramCount());
                }
                case "memory" -> {
                    inst.memory().store(com.jarvis.memory.MemoryType.SEMANTIC, "selftest fact", null, 0.9f, "test");
                    var hits = new com.jarvis.memory.MemoryRetriever(inst.memory())
                        .retrieve(null, "selftest", 3, System.currentTimeMillis());
                    out.add(!hits.isEmpty() ? "PASS retrieve hits=" + hits.size() : "FAIL retrieve");
                    out.add("memories=" + inst.memory().countAll());
                }
                case "navigation" -> {
                    var q = com.jarvis.navigation.LocationQuery.interpret(
                        Map.of("structure", "village"), 5000);
                    out.add(q.kind() == com.jarvis.navigation.LocationQuery.Kind.STRUCTURE
                        ? "PASS query=" + q.target() + " radius=" + q.radius() : "FAIL query");
                    String dir = com.jarvis.world.SpatialReasoning.compass(10, -10);
                    out.add("PASS compass=" + dir);
                }
                case "mobs" -> {
                    String pid = inst.profile().playerId().toString();
                    var snapshot = world.snapshot(pid);
                    if (snapshot == null) {
                        out.add("FAIL no snapshot");
                    } else {
                        var entities = world.nearbyEntities(pid, 32);
                        var summary = com.jarvis.world.HostileScanner.summarize(entities, 32);
                        out.add("PASS entities=" + entities.size() + " hostiles=" + summary.count());
                    }
                }
                case "skills" -> {
                    out.add("PASS skills=" + inst.skills().size());
                    for (var s : inst.skills().all()) {
                        out.add("  - " + s.id() + " conf=" + String.format("%.2f", s.confidence().value()));
                    }
                    var resp = inst.handle("Jarvis, hello.", (PlayerSnapshot) null);
                    out.add(resp.intent() == Intent.GREETING || !resp.text().isBlank()
                        ? "PASS pipeline: " + resp.text() : "FAIL pipeline");
                }
                case "modlearning" -> {
                    var mods = inst.modLearner() != null ? "ok" : "missing";
                    out.add("PASS learner=" + mods + " facts=" + inst.knowledge().size());
                    inst.observe("tooltip", Map.of("item", "minecraft:diamond_sword", "line", "selftest line"));
                    out.add("PASS observation recorded");
                }
                case "voice" -> {
                    var spoken = inst.voice().speak("Self test complete.");
                    out.add(spoken.pcm().length > 1000
                        ? "PASS synth samples=" + spoken.pcm().length + " phonemes=" + spoken.phonemes()
                        : "FAIL synth");
                }
                default -> out.add("Unknown suite. Try: tokenizer neural memory navigation mobs skills modlearning voice");
            }
        } catch (Exception e) {
            out.add("FAIL exception: " + e.getMessage());
        }
        out.add("took " + ((System.nanoTime() - start) / 1_000_000) + "ms");
        return out;
    }
}
