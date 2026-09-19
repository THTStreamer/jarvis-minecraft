package com.jarvis.ai.neural;

import com.jarvis.util.Floats;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Trainable dense (fully-connected) layer: y = W x + b.
 * Supports forward caching, exact backpropagation and gradient accumulation.
 */
public final class DenseLayer {
    private final int inDim;
    private final int outDim;
    private final Activation activation;
    private final float[][] weights; // [out][in]
    private final float[] bias;      // [out]
    private final float[][] gradW;
    private final float[] gradB;

    // forward cache
    private float[] lastInput;
    private float[] lastPre;

    public DenseLayer(int inDim, int outDim, Activation activation, Random rng) {
        this.inDim = inDim;
        this.outDim = outDim;
        this.activation = activation;
        this.weights = Floats.xavier(outDim, inDim, rng);
        this.bias = new float[outDim];
        this.gradW = new float[outDim][inDim];
        this.gradB = new float[outDim];
    }

    public DenseLayer(int inDim, int outDim, Activation activation, float[][] weights, float[] bias) {
        this.inDim = inDim;
        this.outDim = outDim;
        this.activation = activation;
        this.weights = weights;
        this.bias = bias;
        this.gradW = new float[outDim][inDim];
        this.gradB = new float[outDim];
    }

    public int inDim() { return inDim; }
    public int outDim() { return outDim; }

    public float[] forward(float[] x) {
        lastInput = x.clone();
        lastPre = new float[outDim];
        float[] y = new float[outDim];
        for (int o = 0; o < outDim; o++) {
            float s = bias[o];
            float[] w = weights[o];
            for (int i = 0; i < inDim; i++) s += w[i] * x[i];
            lastPre[o] = s;
            y[o] = activation.forward(s);
        }
        return y;
    }

    /**
     * Backpropagate output gradient, accumulate parameter gradients, return input gradient.
     * Caller must call {@link #zeroGrad()} before a new accumulation window.
     */
    public float[] backward(float[] dOut) {
        float[] dPre = new float[outDim];
        for (int o = 0; o < outDim; o++) {
            dPre[o] = dOut[o] * activation.deriv(lastPre[o], activation.forward(lastPre[o]));
            gradB[o] += dPre[o];
        }
        float[] dIn = new float[inDim];
        for (int o = 0; o < outDim; o++) {
            float d = dPre[o];
            float[] w = weights[o];
            float[] gw = gradW[o];
            for (int i = 0; i < inDim; i++) {
                gw[i] += d * lastInput[i];
                dIn[i] += d * w[i];
            }
        }
        return dIn;
    }

    public void zeroGrad() {
        Floats.zero(gradW);
        Floats.zero(gradB);
    }

    /** Collect parameter/gradient pairs for the optimizer. */
    public void collectParams(List<float[]> params, List<float[]> grads) {
        collectParams(params, grads, null);
    }

    /** Collect parameter/gradient pairs for the optimizer. */
    public void collectParams(List<float[]> params, List<float[]> grads, List<int[]> shapes) {
        for (int o = 0; o < outDim; o++) {
            params.add(weights[o]);
            grads.add(gradW[o]);
            if (shapes != null) shapes.add(new int[]{inDim});
        }
        params.add(bias);
        grads.add(gradB);
        if (shapes != null) shapes.add(new int[]{outDim});
    }

    public long paramCount() {
        return (long) outDim * inDim + outDim;
    }

    public void write(DataOutput out) throws IOException {
        for (float[] row : weights) for (float v : row) out.writeFloat(v);
        for (float v : bias) out.writeFloat(v);
    }

    public void read(DataInput in) throws IOException {
        for (float[] row : weights) for (int j = 0; j < row.length; j++) row[j] = in.readFloat();
        for (int j = 0; j < bias.length; j++) bias[j] = in.readFloat();
    }
}
