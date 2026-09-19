package com.jarvis.ai.neural;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adam optimizer operating on parameter/gradient vector pairs collected from layers.
 * State is keyed by parameter-array identity so layers can be optimized in place.
 */
public final class Optimizer {
    private final float beta1;
    private final float beta2;
    private final float eps;
    private final float weightDecay;
    private final float clipNorm;
    private LearningRateSchedule schedule;
    private int step;

    private static final class State {
        float[] m;
        float[] v;
    }

    private final Map<float[], State> states = new IdentityHashMap<>();

    public Optimizer(float beta1, float beta2, float eps, float weightDecay, float clipNorm,
                     LearningRateSchedule schedule) {
        this.beta1 = beta1;
        this.beta2 = beta2;
        this.eps = eps;
        this.weightDecay = weightDecay;
        this.clipNorm = clipNorm;
        this.schedule = schedule;
        this.step = 0;
    }

    public static Optimizer adam(float lr) {
        return new Optimizer(0.9f, 0.999f, 1e-8f, 0.0f, 1.0f, LearningRateSchedule.constant(lr));
    }

    public static Optimizer adamW(float lr, float weightDecay) {
        return new Optimizer(0.9f, 0.999f, 1e-8f, weightDecay, 1.0f, LearningRateSchedule.constant(lr));
    }

    public void setSchedule(LearningRateSchedule schedule) {
        this.schedule = schedule;
    }

    public int stepCount() { return step; }
    public void setStepCount(int step) { this.step = step; }
    public float currentRate() { return schedule.rateAt(step); }

    /** Applies one Adam update over all params using their paired grads. Returns global grad norm. */
    public float step(List<float[]> params, List<float[]> grads) {
        double totalSq = 0.0;
        for (float[] g : grads) {
            for (float v : g) totalSq += (double) v * v;
        }
        float globalNorm = (float) Math.sqrt(totalSq);
        float clipScale = 1f;
        if (clipNorm > 0f && globalNorm > clipNorm) {
            clipScale = clipNorm / (globalNorm + 1e-9f);
        }
        float lr = schedule.rateAt(step);
        float b1t = 1f - (float) Math.pow(beta1, step + 1);
        float b2t = 1f - (float) Math.pow(beta2, step + 1);
        for (int p = 0; p < params.size(); p++) {
            float[] param = params.get(p);
            float[] grad = grads.get(p);
            State st = states.get(param);
            if (st == null) {
                st = new State();
                st.m = new float[param.length];
                st.v = new float[param.length];
                states.put(param, st);
            }
            for (int i = 0; i < param.length; i++) {
                float g = grad[i] * clipScale;
                st.m[i] = beta1 * st.m[i] + (1f - beta1) * g;
                st.v[i] = beta2 * st.v[i] + (1f - beta2) * g * g;
                float mHat = st.m[i] / b1t;
                float vHat = st.v[i] / b2t;
                float update = mHat / ((float) Math.sqrt(vHat) + eps);
                if (weightDecay > 0f) update += weightDecay * param[i];
                param[i] -= lr * update;
            }
        }
        step++;
        return globalNorm;
    }

    /** Plain SGD used for fast prototype-style updates. */
    public static void sgd(List<float[]> params, List<float[]> grads, float lr) {
        for (int p = 0; p < params.size(); p++) {
            float[] param = params.get(p);
            float[] grad = grads.get(p);
            for (int i = 0; i < param.length; i++) param[i] -= lr * grad[i];
        }
    }
}
