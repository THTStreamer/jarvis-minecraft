package com.jarvis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jarvis.core.JarvisInstance;
import com.jarvis.language.Intent;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IntentTest {
    @Test
    public void paraphrasesResolveToSameIntent(@TempDir Path tmp) {
        JarvisInstance inst = TestKit.instance(tmp);
        String[] villageAsks = {
            "Jarvis, where's the nearest village?",
            "Can you locate the closest settlement?",
            "Find me a village.",
            "Take me to the nearest village."
        };
        for (String ask : villageAsks) {
            var scored = inst.intents().recognize(ask);
            assertEquals(Intent.LOCATE_STRUCTURE, scored.intent(), "paraphrase: " + ask);
        }
        String[] enemyAsks = {
            "How many hostile creatures are around me?",
            "Scan the area for enemies.",
            "Are there any hostile mobs nearby?",
            "Check my surroundings for hostile entities."
        };
        for (String ask : enemyAsks) {
            var scored = inst.intents().recognize(ask);
            assertTrue(scored.intent() == Intent.SCAN_HOSTILES || scored.intent() == Intent.SCAN_AREA,
                "enemy paraphrase: " + ask + " -> " + scored.intent());
        }
    }

    @Test
    public void fullPipelineAnswers(@TempDir Path tmp) {
        JarvisInstance inst = TestKit.instance(tmp);
        var snapshot = new TestKit.FakeWorld().snapshot("x");
        var r1 = inst.handle("Jarvis, find me a village.", snapshot);
        assertTrue(r1.text().toLowerCase().contains("village") || r1.text().toLowerCase().contains("located"),
            "village answer: " + r1.text());
        var r2 = inst.handle("How far?", snapshot);
        assertTrue(r2.text().toLowerCase().contains("block") || r2.text().toLowerCase().contains("guid"),
            "follow-up keeps context: " + r2.text());
        var r3 = inst.handle("Jarvis, scan the area.", snapshot);
        assertTrue(!r3.text().isBlank(), "scan answers");
        var r4 = inst.handle("Jarvis, scan for ores.", snapshot);
        assertTrue(r4.text().toLowerCase().contains("disabled"), "ores disabled by default: " + r4.text());
    }

    @Test
    public void impossibleSkillFailsHonestly(@TempDir Path tmp) {
        JarvisInstance inst = TestKit.instance(tmp);
        String msg = inst.requestSkill("generate an entirely new dimension using unavailable capabilities xyzzy");
        assertTrue(msg.equals("Sorry, sir, I cannot seem to create this skill.") || msg.contains("learn"),
            "honest skill creation: " + msg);
    }
}
