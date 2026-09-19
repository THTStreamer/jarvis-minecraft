package com.jarvis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jarvis.bootstrap.BootstrapCorpus;
import com.jarvis.bootstrap.ModBootstrap;
import com.jarvis.core.JarvisInstance;
import com.jarvis.mods.ModInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BootstrapTest {
    @Test
    public void corpusLoads() {
        List<BootstrapCorpus.Fact> facts = BootstrapCorpus.load();
        assertTrue(facts.size() >= 150, "curated corpus ships with the mod, got " + facts.size());
        for (BootstrapCorpus.Fact f : facts) {
            assertFalse(f.sentence().isBlank(), "every fact renders a sentence");
            assertTrue(f.c() > 0 && f.c() <= 1, "confidence in range");
        }
    }

    @Test
    public void sweepIngestsIntoPrivateKnowledge(@TempDir Path tmp) {
        JarvisInstance inst = TestKit.instance(tmp);
        int vocabBefore = inst.tokenizer().vocabulary().size();
        ModBootstrap sweep = new ModBootstrap(inst.knowledge(), inst.semantics(),
            inst.training(), inst.tokenizer());
        ModBootstrap.Report report = sweep.run(
            List.of(new ModInfo("create", "Create", "6.0")),
            new ModNetworkTest.StubView(), 120);
        assertTrue(report.corpusFacts() >= 150, "corpus ingested");
        assertTrue(report.registryFacts() > 5, "registry facts built");
        assertTrue(inst.tokenizer().vocabulary().size() > vocabBefore, "vocabulary learned");
        var diamond = inst.knowledge().queryObject("diamond ore");
        assertTrue(!diamond.isEmpty(), "pre-trained diamond knowledge answers immediately");
        var answer = inst.handle("Jarvis, tell me about diamond ore.",
            new TestKit.FakeWorld().snapshot("x"));
        assertTrue(answer.text().toLowerCase().contains("diamond"),
            "knowledge answers from bootstrap: " + answer.text());
    }

    @Test
    public void bootstrappedFlagPersists(@TempDir Path tmp) throws Exception {
        JarvisInstance inst = TestKit.instance(tmp);
        assertFalse(inst.bootstrapped(), "fresh instances are not bootstrapped");
        inst.setBootstrapped(true);
        com.jarvis.persistence.PersistenceManager pm =
            new com.jarvis.persistence.PersistenceManager(tmp.resolve("jarvis"));
        pm.save(inst);
        String json = Files.readString(
            tmp.resolve("jarvis").resolve("players")
                .resolve(inst.profile().playerId() + ".json"));
        assertTrue(json.contains("\"bootstrapped\""), "flag persisted, refresh is once-per-player");
    }

    @Test
    public void registrySweepSkipsInfraMods(@TempDir Path tmp) {
        JarvisInstance inst = TestKit.instance(tmp);
        ModBootstrap sweep = new ModBootstrap(inst.knowledge(), inst.semantics(),
            inst.training(), inst.tokenizer());
        int before = inst.knowledge().size();
        sweep.run(List.of(new ModInfo("minecraft", "Minecraft", "1.21.1"),
            new ModInfo("neoforge", "NeoForge", "21.1.250")), new ModNetworkTest.StubView(), 120);
        int vanillaFacts = inst.knowledge().size() - before;
        assertTrue(vanillaFacts > 0, "corpus still ingests");
        assertTrue(inst.knowledge().query("neoforge", "contains-block").isEmpty(),
            "infra mods are not swept");
    }
}
