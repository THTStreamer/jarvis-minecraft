package com.jarvis.ai.inference;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs neural inference off the server thread with a per-request compute
 * budget. Requests that exceed the budget are cancelled and reported so the
 * deterministic fallback can answer instead of hanging the game.
 */
public final class InferenceEngine {
    private final ExecutorService executor;
    private final long budgetMillis;
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong timedOut = new AtomicLong();
    private final AtomicLong totalNanos = new AtomicLong();

    public InferenceEngine(ExecutorService executor, long budgetMillis) {
        this.executor = executor;
        this.budgetMillis = budgetMillis;
    }

    public <T> T infer(Callable<T> task, T fallback) throws Exception {
        totalRequests.incrementAndGet();
        long start = System.nanoTime();
        Future<T> future = executor.submit(task);
        try {
            T result = future.get(budgetMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
            totalNanos.addAndGet(System.nanoTime() - start);
            return result;
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            timedOut.incrementAndGet();
            return fallback;
        }
    }

    public long totalRequests() { return totalRequests.get(); }
    public long timedOut() { return timedOut.get(); }
    public double avgMillis() {
        long n = totalRequests.get();
        if (n == 0) return 0.0;
        return totalNanos.get() / 1_000_000.0 / n;
    }

    public List<String> stats() {
        List<String> out = new ArrayList<>();
        out.add("inference_requests=" + totalRequests.get());
        out.add("inference_timeouts=" + timedOut.get());
        out.add(String.format("inference_avg_ms=%.2f", avgMillis()));
        return out;
    }
}
