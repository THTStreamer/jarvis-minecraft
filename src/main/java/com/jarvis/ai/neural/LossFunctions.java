package com.jarvis.ai.neural;

import com.jarvis.util.Floats;

/** Cross-entropy (with softmax) and mean-squared-error losses with gradients. */
public final class LossFunctions {
    private LossFunctions() {}

    public static final class CeResult {
        public final float loss;
        public final float[] dLogits;
        public CeResult(float loss, float[] dLogits) {
            this.loss = loss;
            this.dLogits = dLogits;
        }
    }

    /** Softmax cross-entropy for one target index. Returns loss + dLogits (softmax - oneHot). */
    public static CeResult softmaxCrossEntropy(float[] logits, int target) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : logits) if (v > max) max = v;
        float sum = 0f;
        float[] probs = new float[logits.length];
        for (int i = 0; i < logits.length; i++) {
            probs[i] = (float) Math.exp(logits[i] - max);
            sum += probs[i];
        }
        for (int i = 0; i < probs.length; i++) probs[i] /= sum;
        float loss = (float) -Math.log(Math.max(probs[target], 1e-9f));
        probs[target] -= 1f;
        return new CeResult(loss, probs);
    }

    public static float mse(float[] pred, float[] target, float[] dPredOut) {
        float sum = 0f;
        for (int i = 0; i < pred.length; i++) {
            float d = pred[i] - target[i];
            sum += d * d;
            dPredOut[i] = 2f * d / pred.length;
        }
        return sum / pred.length;
    }

    /** Cosine-embedding alignment loss: 1 - cosine(pred, target); gradient w.r.t. pred. */
    public static float cosineAlign(float[] pred, float[] target, float[] dPredOut) {
        float dot = Floats.dot(pred, target);
        float np = Floats.norm(pred);
        float nt = Floats.norm(target);
        float denom = Math.max(np * nt, 1e-9f);
        float cos = dot / denom;
        for (int i = 0; i < pred.length; i++) {
            dPredOut[i] = (float) (-(target[i] / denom - pred[i] * dot / (denom * np * np + 1e-12)));
        }
        return 1f - cos;
    }
}
