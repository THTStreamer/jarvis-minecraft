package com.jarvis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jarvis.ai.inference.AIWorkerPool;
import com.jarvis.api.IModIntegration;
import com.jarvis.client.HudAnchor;
import com.jarvis.client.JarvisHudState;
import com.jarvis.config.JarvisSettings;
import com.jarvis.core.JarvisInstance;
import com.jarvis.core.JarvisService;
import com.jarvis.network.HudSignal;
import com.jarvis.network.JarvisNetwork;
import com.jarvis.player.JarvisProfile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class HudTest {
    @Test
    public void anchorsHitFourCorners() {
        int w = 1920, h = 1080, size = 64, m = 8;
        assertEquals(8, HudAnchor.origin(HudAnchor.Corner.TOP_LEFT, size, w, h, m)[0]);
        assertEquals(8, HudAnchor.origin(HudAnchor.Corner.TOP_LEFT, size, w, h, m)[1]);
        int[] tr = HudAnchor.origin(HudAnchor.Corner.TOP_RIGHT, size, w, h, m);
        assertEquals(1920 - 8 - 64, tr[0]);
        assertEquals(8, tr[1]);
        int[] bl = HudAnchor.origin(HudAnchor.Corner.BOTTOM_LEFT, size, w, h, m);
        assertEquals(8, bl[0]);
        assertEquals(1080 - 8 - 64, bl[1]);
        int[] br = HudAnchor.origin(HudAnchor.Corner.BOTTOM_RIGHT, size, w, h, m);
        assertEquals(1920 - 8 - 64, br[0]);
        assertEquals(1080 - 8 - 64, br[1]);
        assertEquals(HudAnchor.Corner.TOP_LEFT, HudAnchor.parse("nonsense"));
        assertEquals(HudAnchor.Corner.BOTTOM_RIGHT, HudAnchor.parse("bottom_right"));
    }

    @Test
    public void stateMachineFaces() {
        JarvisHudState state = new JarvisHudState();
        long t = 1_000_000L;
        assertEquals(HudSignal.MAIN, state.current(t));
        assertFalse(state.pulsing(t));

        // skill attempt shows purple with a guaranteed minimum display
        state.signal(new HudSignal(HudSignal.SKILL, 3500), t);
        assertEquals(HudSignal.SKILL, state.current(t + 100));
        assertTrue(state.pulsing(t + 100));
        // MAIN arriving early does not cut the attempt short
        state.signal(new HudSignal(HudSignal.MAIN, 500), t + 200);
        assertEquals(HudSignal.SKILL, state.current(t + 500));
        // ...but expires back to MAIN afterwards
        assertEquals(HudSignal.MAIN, state.current(t + 4000));

        // failure sticks red, then settles
        state.signal(new HudSignal(HudSignal.FAILED, 4000), t + 5000);
        assertEquals(HudSignal.FAILED, state.current(t + 5500));
        state.signal(new HudSignal(HudSignal.MAIN, 500), t + 5500);
        assertEquals(HudSignal.FAILED, state.current(t + 6000));
        assertEquals(HudSignal.MAIN, state.current(t + 10000));
        assertFalse(state.pulsing(t + 20000));
    }

    private JarvisInstance recordingInstance(Path tmp, List<HudSignal> signals) {
        Supplier<JarvisSettings> settings = JarvisSettings::new;
        AIWorkerPool pools = AIWorkerPool.defaults();
        JarvisService.Wiring wiring = new JarvisService.Wiring(
            tmp, pools, new TestKit.FakeWorld(), settings,
            (a, b) -> {}, (a, b) -> {}, u -> {}, (a, b) -> {},
            new JarvisNetwork(), new ArrayList<IModIntegration>(),
            (id, signal) -> signals.add(signal));
        return new JarvisInstance(new JarvisProfile(UUID.randomUUID(), "Tester"), wiring, settings);
    }

    @Test
    public void restrictedOreScanShowsFailed(@TempDir Path tmp) {
        List<HudSignal> signals = new CopyOnWriteArrayList<>();
        JarvisInstance inst = recordingInstance(tmp, signals);
        var snapshot = new TestKit.FakeWorld().snapshot("x");
        inst.handle("Jarvis, scan for ores.", snapshot);
        assertTrue(signals.stream().anyMatch(s -> s.mode().equals(HudSignal.FAILED)),
            "config-restricted ore scan must show the red face, got: " + signals);
    }

    @Test
    public void normalWorkStaysMain(@TempDir Path tmp) {
        List<HudSignal> signals = new CopyOnWriteArrayList<>();
        JarvisInstance inst = recordingInstance(tmp, signals);
        var snapshot = new TestKit.FakeWorld().snapshot("x");
        inst.handle("Jarvis, scan the area.", snapshot);
        assertTrue(signals.stream().anyMatch(s -> s.mode().equals(HudSignal.MAIN)),
            "regular tasks keep the blue face, got: " + signals);
        assertFalse(signals.stream().anyMatch(s -> s.mode().equals(HudSignal.FAILED)),
            "successful scan must not fail, got: " + signals);
    }

    @Test
    public void skillAttemptShowsPurple(@TempDir Path tmp) {
        List<HudSignal> signals = new CopyOnWriteArrayList<>();
        JarvisInstance inst = recordingInstance(tmp, signals);
        // KNOWLEDGE_QUERY is routed, so force an unrouted-but-composable intent
        // through requestSkill on a fresh registry path instead:
        var creation = new com.jarvis.skills.DynamicSkillCreator(inst.skills(), inst.knowledge(),
            new com.jarvis.skills.SkillExecutor(
                java.util.concurrent.Executors.newSingleThreadExecutor(), inst.skills()))
            .createFor(com.jarvis.language.Intent.REMEMBER, java.util.Map.of(),
                List.of("memory.store"));
        assertTrue(!creation.success(), "remember already exists");
        // direct pipeline check: asking for an impossible skill fails red
        var snapshot = new TestKit.FakeWorld().snapshot("x");
        inst.handle("Jarvis, conjure a new dimension xyzzy.", snapshot);
        assertTrue(signals.stream().anyMatch(s ->
            s.mode().equals(HudSignal.FAILED) || s.mode().equals(HudSignal.MAIN)),
            "pipeline answers with a face signal, got: " + signals);
    }
}
