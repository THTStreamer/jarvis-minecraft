package com.jarvis.ai.training;

import com.jarvis.ai.neural.JarvisNeuralNetwork;
import com.jarvis.ai.neural.Optimizer;
import com.jarvis.ai.tokenizer.JarvisTokenizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Minecraft-specific training pipeline. Builds structured training examples
 * from conversations, registry-derived sentences and skill outcomes, and
 * trains the neural network off-thread in small batches.
 */
public final class TrainingPipeline {
    private final JarvisNeuralNetwork network;
    private final JarvisTokenizer tokenizer;
    private final Optimizer optimizer;
    private final ExecutorService trainingPool;
    private final Deque<TrainingExample> queue = new ArrayDeque<>();
    private final int maxQueue;
    private final int batchSize;

    private final AtomicLong steps = new AtomicLong();
    private volatile float lastLoss = Float.NaN;
    private volatile boolean training;

    public TrainingPipeline(JarvisNeuralNetwork network, JarvisTokenizer tokenizer,
                            Optimizer optimizer, ExecutorService trainingPool,
                            int maxQueue, int batchSize) {
        this.network = network;
        this.tokenizer = tokenizer;
        this.optimizer = optimizer;
        this.trainingPool = trainingPool;
        this.maxQueue = maxQueue;
        this.batchSize = batchSize;
    }

    public synchronized void submit(TrainingExample ex) {
        while (queue.size() >= maxQueue) queue.pollFirst();
        queue.addLast(ex);
    }

    /** Turn a (player, jarvis) exchange into next-token examples. */
    public void learnExchange(String playerText, String jarvisText) {
        int[] p = tokenizer.encode(playerText, true);
        int[] j = tokenizer.encode(jarvisText, true);
        // predict each response token from the player prompt + response prefix
        int[] ctx = concat(p, j);
        for (int i = p.length; i < ctx.length - 1 && i < ctx.length; i++) {
            int[] input = slice(ctx, Math.max(0, i - 24), i);
            submit(new TrainingExample(input, ctx[i], 1.0f, "conversation"));
        }
    }

    /** Turn a registry-derived semantic sentence into training signal. */
    public void learnFact(String sentence) {
        int[] ids = tokenizer.encode(sentence, true);
        for (int i = 1; i < ids.length - 1; i++) {
            int[] input = slice(ids, Math.max(0, i - 24), i);
            submit(new TrainingExample(input, ids[i], 0.5f, "knowledge"));
        }
    }

    public synchronized int queued() { return queue.size(); }
    public long steps() { return steps.get(); }
    public float lastLoss() { return lastLoss; }
    public boolean isTraining() { return training; }

    /** Train one batch off-thread; no-op on the calling thread. */
    public void trainAsync() {
        List<TrainingExample> batch;
        synchronized (this) {
            if (training || queue.isEmpty()) return;
            training = true;
            batch = new ArrayList<>(batchSize);
            List<TrainingExample> all = new ArrayList<>(queue);
            Collections.shuffle(all);
            for (int i = 0; i < Math.min(batchSize, all.size()); i++) batch.add(all.get(i));
        }
        trainingPool.submit(() -> {
            try {
                float total = 0f;
                for (TrainingExample ex : batch) {
                    total += network.trainNextToken(ex.inputIds(), ex.targetId(), optimizer);
                    steps.incrementAndGet();
                }
                lastLoss = total / Math.max(1, batch.size());
            } finally {
                synchronized (TrainingPipeline.this) {
                    training = false;
                }
            }
        });
    }

    private static int[] concat(int[] a, int[] b) {
        int[] c = new int[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    private static int[] slice(int[] a, int from, int to) {
        int[] c = new int[Math.max(0, to - from)];
        for (int i = 0; i < c.length; i++) c[i] = a[from + i];
        return c;
    }
}
