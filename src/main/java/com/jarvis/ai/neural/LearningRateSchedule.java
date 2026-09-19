package com.jarvis.ai.neural;

/** Learning-rate schedules. */
public interface LearningRateSchedule {
    float rateAt(int step);

    /** Constant learning rate. */
    static LearningRateSchedule constant(float lr) {
        return step -> lr;
    }

    /** Linear warmup followed by cosine decay to minRate. */
    static LearningRateSchedule warmupCosine(float peak, int warmupSteps, int totalSteps, float minRate) {
        return step -> {
            if (step < warmupSteps) {
                return peak * (float) (step + 1) / (float) Math.max(1, warmupSteps);
            }
            float t = (float) (step - warmupSteps) / (float) Math.max(1, totalSteps - warmupSteps);
            t = Math.min(1f, Math.max(0f, t));
            float cosine = (float) (0.5 * (1.0 + Math.cos(Math.PI * t)));
            return minRate + (peak - minRate) * cosine;
        };
    }
}
