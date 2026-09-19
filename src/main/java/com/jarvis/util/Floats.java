package com.jarvis.util;

import java.util.Random;

/** Small float-vector/matrix utilities used by the neural core. Pure Java, no dependencies. */
public final class Floats {
    private Floats() {}

    public static float dot(float[] a, float[] b) {
        float s = 0f;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    public static float norm(float[] a) {
        return (float) Math.sqrt(dot(a, a) + 1e-12f);
    }

    public static float cosine(float[] a, float[] b) {
        float denom = norm(a) * norm(b);
        if (denom < 1e-9f) return 0f;
        return dot(a, b) / denom;
    }

    public static void softmaxInPlace(float[] x) {
        float max = Float.NEGATIVE_INFINITY;
        for (float v : x) if (v > max) max = v;
        float sum = 0f;
        for (int i = 0; i < x.length; i++) {
            x[i] = (float) Math.exp(x[i] - max);
            sum += x[i];
        }
        if (sum <= 0f) {
            float u = 1f / x.length;
            for (int i = 0; i < x.length; i++) x[i] = u;
            return;
        }
        for (int i = 0; i < x.length; i++) x[i] /= sum;
    }

    /** Xavier-uniform init for a [rows x cols] matrix using the given RNG. */
    public static float[][] xavier(int rows, int cols, Random rng) {
        float limit = (float) Math.sqrt(6.0 / (rows + cols));
        float[][] m = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                m[i][j] = (rng.nextFloat() * 2f - 1f) * limit;
            }
        }
        return m;
    }

    public static float[][] zeros(int rows, int cols) {
        return new float[rows][cols];
    }

    public static float[] zeros(int n) {
        return new float[n];
    }

    public static float[][] copy(float[][] m) {
        float[][] c = new float[m.length][];
        for (int i = 0; i < m.length; i++) c[i] = m[i].clone();
        return c;
    }

    public static void addScaled(float[] target, float[] delta, float scale) {
        for (int i = 0; i < target.length; i++) target[i] += delta[i] * scale;
    }

    public static void addScaled(float[][] target, float[][] delta, float scale) {
        for (int i = 0; i < target.length; i++) addScaled(target[i], delta[i], scale);
    }

    public static void zero(float[] a) {
        for (int i = 0; i < a.length; i++) a[i] = 0f;
    }

    public static void zero(float[][] m) {
        for (float[] row : m) zero(row);
    }
}
