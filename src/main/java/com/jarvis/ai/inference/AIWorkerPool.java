package com.jarvis.ai.inference;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread separation for Jarvis: AI inference, training, and voice each get
 * their own pool. World mutation always hops back to the server thread via
 * the callback supplied by the Minecraft glue layer.
 */
public final class AIWorkerPool implements AutoCloseable {
    private final ExecutorService aiPool;
    private final ExecutorService trainingPool;
    private final ExecutorService voicePool;

    public AIWorkerPool(int aiThreads, int trainingThreads, int voiceThreads) {
        this.aiPool = Executors.newFixedThreadPool(Math.max(1, aiThreads), named("jarvis-ai"));
        this.trainingPool = Executors.newFixedThreadPool(Math.max(1, trainingThreads), named("jarvis-train"));
        this.voicePool = Executors.newFixedThreadPool(Math.max(1, voiceThreads), named("jarvis-voice"));
    }

    public static AIWorkerPool defaults() {
        return new AIWorkerPool(2, 1, 1);
    }

    private static ThreadFactory named(String base) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, base + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    public ExecutorService ai() { return aiPool; }
    public ExecutorService training() { return trainingPool; }
    public ExecutorService voice() { return voicePool; }

    @Override
    public void close() {
        aiPool.shutdownNow();
        trainingPool.shutdownNow();
        voicePool.shutdownNow();
    }
}
