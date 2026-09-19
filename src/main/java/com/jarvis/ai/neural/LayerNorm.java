package com.jarvis.ai.neural;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Layer normalization with learnable gain and bias. Forward caches stats for backprop. */
public final class LayerNorm {
    private final int dim;
    private final float eps;
    private final float[] gain;
    private final float[] bias;
    private final float[] gradGain;
    private final float[] gradBias;

    private float[] lastX;
    private float[] lastXn;
    private float lastMean;
    private float lastInvStd;

    public LayerNorm(int dim) {
        this(dim, 1e-5f);
    }

    public LayerNorm(int dim, float eps) {
        this.dim = dim;
        this.eps = eps;
        this.gain = new float[dim];
        this.bias = new float[dim];
        this.gradGain = new float[dim];
        this.gradBias = new float[dim];
        for (int i = 0; i < dim; i++) gain[i] = 1f;
    }

    public float[] forward(float[] x) {
        lastX = x.clone();
        float mean = 0f;
        for (float v : x) mean += v;
        mean /= dim;
        float var = 0f;
        for (float v : x) {
            float d = v - mean;
            var += d * d;
        }
        var /= dim;
        float invStd = (float) (1.0 / Math.sqrt(var + eps));
        lastMean = mean;
        lastInvStd = invStd;
        lastXn = new float[dim];
        float[] y = new float[dim];
        for (int i = 0; i < dim; i++) {
            lastXn[i] = (x[i] - mean) * invStd;
            y[i] = gain[i] * lastXn[i] + bias[i];
        }
        return y;
    }

    public float[] backward(float[] dOut) {
        for (int i = 0; i < dim; i++) {
            gradGain[i] += dOut[i] * lastXn[i];
            gradBias[i] += dOut[i];
        }
        float[] dXn = new float[dim];
        for (int i = 0; i < dim; i++) dXn[i] = dOut[i] * gain[i];
        float meanDXn = 0f, meanDXnXn = 0f;
        for (int i = 0; i < dim; i++) {
            meanDXn += dXn[i];
            meanDXnXn += dXn[i] * lastXn[i];
        }
        meanDXn /= dim;
        meanDXnXn /= dim;
        float[] dIn = new float[dim];
        for (int i = 0; i < dim; i++) {
            dIn[i] = lastInvStd * (dXn[i] - meanDXn - lastXn[i] * meanDXnXn);
        }
        return dIn;
    }

    public void zeroGrad() {
        for (int i = 0; i < dim; i++) {
            gradGain[i] = 0f;
            gradBias[i] = 0f;
        }
    }

    public float[] gain() { return gain; }
    public float[] bias() { return bias; }
    public float[] gradGain() { return gradGain; }
    public float[] gradBias() { return gradBias; }

    public long paramCount() { return dim * 2L; }

    public void write(DataOutput out) throws IOException {
        for (float v : gain) out.writeFloat(v);
        for (float v : bias) out.writeFloat(v);
    }

    public void read(DataInput in) throws IOException {
        for (int i = 0; i < dim; i++) gain[i] = in.readFloat();
        for (int i = 0; i < dim; i++) bias[i] = in.readFloat();
    }
}
