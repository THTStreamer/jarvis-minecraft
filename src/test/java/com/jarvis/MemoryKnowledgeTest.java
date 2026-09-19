package com.jarvis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jarvis.core.JarvisInstance;
import com.jarvis.knowledge.KnowledgeGraph;
import com.jarvis.memory.JarvisMemory;
import com.jarvis.memory.MemoryRetriever;
import com.jarvis.memory.MemoryType;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MemoryKnowledgeTest {
    @Test
    public void memoryStoresAndRetrieves() {
        JarvisMemory memory = new JarvisMemory();
        memory.store(MemoryType.EPISODIC, "Player built a new base near the plains village", null, 0.9f, "base");
        memory.store(MemoryType.SEMANTIC, "Diamonds occur deep underground", null, 0.8f, "mining");
        assertEquals(2, memory.countAll());
        MemoryRetriever retriever = new MemoryRetriever(memory);
        var hits = retriever.retrieve(null, "where is the player base", 2, System.currentTimeMillis());
        assertEquals(2, hits.size(), "retrieval returns top-k only");
        memory.forgetType(MemoryType.EPISODIC);
        assertEquals(1, memory.countAll());
    }

    @Test
    public void knowledgeGraphConfidence() {
        KnowledgeGraph graph = new KnowledgeGraph();
        var e = graph.add("create", "contains", "mechanical_press", "registry", 0.6f);
        assertTrue(e.confidence().value() >= 0.6f);
        e.confirm(0.8f);
        float after = e.confidence().value();
        assertTrue(after > 0.6f, "confirmation raises confidence");
        e.contradict(1.0f);
        assertTrue(e.confidence().value() < after, "contradiction lowers confidence");
        assertEquals(1, graph.query("create", "contains").size());
    }

    @Test
    public void playersStayIsolated(@TempDir Path tmp) throws Exception {
        JarvisInstance a = TestKit.instance(tmp);
        JarvisInstance b = TestKit.instance(tmp);
        a.handle("Jarvis, remember that my base is at the desert pyramid.", new TestKit.FakeWorld().snapshot("a"));
        var hitsB = new MemoryRetriever(b.memory()).retrieve(null, "desert pyramid base", 5, System.currentTimeMillis());
        boolean leaked = hitsB.stream().anyMatch(h -> h.entry().text().contains("desert pyramid"));
        assertTrue(!leaked, "player B must not inherit player A's memories");
    }
}
