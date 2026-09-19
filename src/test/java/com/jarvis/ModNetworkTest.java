package com.jarvis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jarvis.core.JarvisInstance;
import com.jarvis.mods.ModInfo;
import com.jarvis.mods.RegistryView;
import com.jarvis.network.JarvisNetwork;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ModNetworkTest {
    static final class StubView implements RegistryView {
        @Override public List<String> blockIds(String modId, int limit) {
            return List.of("create:mechanical_press", "create:encased_fan");
        }
        @Override public List<String> itemIds(String modId, int limit) {
            return List.of("create:brass_ingot");
        }
        @Override public List<String> entityIds(String modId, int limit) { return List.of(); }
        @Override public int blockCount(String modId) { return 2; }
        @Override public int itemCount(String modId) { return 1; }
        @Override public int entityCount(String modId) { return 0; }
        @Override public List<String> tooltipFor(String itemId) { return List.of(); }
        @Override public List<String> recipeSummaries(String modId, int limit) {
            return List.of("brass_ingot + iron_ingot -> brass_block");
        }
        @Override public Map<String, String> empty() { return Map.of(); }
    }

    @Test
    public void learnsModContent(@TempDir Path tmp) {
        JarvisInstance inst = TestKit.instance(tmp);
        var report = inst.modLearner().learnMod(new ModInfo("create", "Create", "6.0"), new StubView());
        assertEquals("create", report.modId());
        assertTrue(report.facts() > 5, "structured facts built");
        assertTrue(!inst.knowledge().query("create", "contains-block").isEmpty(), "contains relations");
        // player-specific: another instance must not share
        JarvisInstance other = TestKit.instance(tmp);
        assertEquals(0, other.knowledge().query("create", "contains-block").size(), "isolated learning");
    }

    @Test
    public void observesAndDiagnoses(@TempDir Path tmp) {
        JarvisInstance inst = TestKit.instance(tmp);
        inst.observe("goggles", Map.of("block", "create:mechanical_press",
            "text", "Stress: 128 SU, overstressed capacity 64 SU"));
        String diagnosis = inst.create().diagnose("create:mechanical_press");
        assertTrue(diagnosis != null && diagnosis.toLowerCase().contains("overstress"),
            "learned diagnosis: " + diagnosis);
    }

    @Test
    public void jarvisToJarvisRelay() {
        JarvisNetwork net = new JarvisNetwork();
        UUID greg = UUID.randomUUID();
        UUID john = UUID.randomUUID();
        Map<UUID, String> inbox = new java.util.concurrent.ConcurrentHashMap<>();
        net.setResolver(
            name -> name.equalsIgnoreCase("john") ? john : null,
            id -> id.equals(john),
            (id, text) -> inbox.put(id, text));
        var receipt = net.send(greg, "Greg", "john", "I'm at the base.", 0);
        assertTrue(receipt.ok(), "delivery ok");
        assertTrue(inbox.get(john).contains("at the base"), "message content relayed");

        // offline queue
        net.setResolver(name -> john, id -> false, (id, text) -> inbox.put(id, text));
        var queued = net.send(greg, "Greg", "john", "Come home.", 1);
        assertTrue(queued.ok(), "offline queued");
        assertEquals(1, net.pendingCount());
        net.setResolver(name -> john, id -> true, (id, text) -> inbox.put(id, text));
        assertEquals(1, net.drain(john), "drained on login");
    }
}
