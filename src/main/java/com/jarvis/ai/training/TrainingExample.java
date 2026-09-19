package com.jarvis.ai.training;

/** One supervised example: token sequence with a next-token target. */
public record TrainingExample(int[] inputIds, int targetId, float weight, String source) {
    public TrainingExample(int[] inputIds, int targetId) {
        this(inputIds, targetId, 1.0f, "conversation");
    }
}
