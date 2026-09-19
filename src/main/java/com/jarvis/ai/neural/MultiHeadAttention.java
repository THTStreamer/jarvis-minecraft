package com.jarvis.ai.neural;

import com.jarvis.util.Floats;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Multi-head scaled dot-product self-attention with full backpropagation.
 * Input/output shape: [seq][dim]. Heads must divide dim.
 */
public final class MultiHeadAttention {
    private final int dim;
    private final int heads;
    private final int headDim;
    private final DenseLayer wq;
    private final DenseLayer wk;
    private final DenseLayer wv;
    private final DenseLayer wo;

    // forward caches
    private float[][] lastX;
    private float[][][] lastProbs; // [head][q][k]
    private float[][] lastQ, lastK, lastV;

    public MultiHeadAttention(int dim, int heads, Random rng) {
        if (dim % heads != 0) throw new IllegalArgumentException("dim must be divisible by heads");
        this.dim = dim;
        this.heads = heads;
        this.headDim = dim / heads;
        this.wq = new DenseLayer(dim, dim, Activation.LINEAR, rng);
        this.wk = new DenseLayer(dim, dim, Activation.LINEAR, rng);
        this.wv = new DenseLayer(dim, dim, Activation.LINEAR, rng);
        this.wo = new DenseLayer(dim, dim, Activation.LINEAR, rng);
    }

    public float[][] forward(float[][] x) {
        int seq = x.length;
        lastX = new float[seq][dim];
        for (int s = 0; s < seq; s++) lastX[s] = x[s].clone();
        lastQ = new float[seq][dim];
        lastK = new float[seq][dim];
        lastV = new float[seq][dim];
        for (int s = 0; s < seq; s++) {
            lastQ[s] = wq.forward(x[s]);
            lastK[s] = wk.forward(x[s]);
            lastV[s] = wv.forward(x[s]);
        }
        float scale = (float) (1.0 / Math.sqrt(headDim));
        lastProbs = new float[heads][seq][seq];
        float[][] context = new float[seq][dim];
        float[] scores = new float[seq];
        for (int h = 0; h < heads; h++) {
            int off = h * headDim;
            for (int q = 0; q < seq; q++) {
                for (int k = 0; k < seq; k++) {
                    float s = 0f;
                    for (int d = 0; d < headDim; d++) {
                        s += lastQ[q][off + d] * lastK[k][off + d];
                    }
                    scores[k] = s * scale;
                }
                Floats.softmaxInPlace(scores);
                System.arraycopy(scores, 0, lastProbs[h][q], 0, seq);
                for (int k = 0; k < seq; k++) {
                    float p = lastProbs[h][q][k];
                    for (int d = 0; d < headDim; d++) {
                        context[q][off + d] += p * lastV[k][off + d];
                    }
                }
            }
        }
        float[][] out = new float[seq][dim];
        for (int s = 0; s < seq; s++) out[s] = wo.forward(context[s]);
        return out;
    }

    public float[][] backward(float[][] dOut) {
        int seq = lastX.length;
        float[][] dContext = new float[seq][dim];
        // NOTE: wo.forward was called per-row with shared layer; its cache only holds
        // the last row, so re-run per-row forward/backward to get exact gradients.
        float[][] rebuiltContext = rebuildContext();
        // Recompute per-row wo gradients exactly:
        for (int s = 0; s < seq; s++) {
            wo.forward(rebuiltContext[s]);
            dContext[s] = wo.backward(dOut[s]);
        }
        float scale = (float) (1.0 / Math.sqrt(headDim));
        float[][] dQ = new float[seq][dim];
        float[][] dK = new float[seq][dim];
        float[][] dV = new float[seq][dim];
        for (int h = 0; h < heads; h++) {
            int off = h * headDim;
            for (int q = 0; q < seq; q++) {
                float[] probs = lastProbs[h][q];
                // dV
                for (int k = 0; k < seq; k++) {
                    float p = probs[k];
                    for (int d = 0; d < headDim; d++) {
                        dV[k][off + d] += p * dContext[q][off + d];
                    }
                }
                // dScores via softmax jacobian
                float dot = 0f;
                for (int k = 0; k < seq; k++) {
                    float c = 0f;
                    for (int d = 0; d < headDim; d++) {
                        c += dContext[q][off + d] * lastV[k][off + d];
                    }
                    dot += probs[k] * c;
                }
                for (int k = 0; k < seq; k++) {
                    float c = 0f;
                    for (int d = 0; d < headDim; d++) {
                        c += dContext[q][off + d] * lastV[k][off + d];
                    }
                    float dScore = probs[k] * (c - dot) * scale;
                    for (int d = 0; d < headDim; d++) {
                        dQ[q][off + d] += dScore * lastK[k][off + d];
                        dK[k][off + d] += dScore * lastQ[q][off + d];
                    }
                }
            }
        }
        float[][] dIn = new float[seq][dim];
        for (int s = 0; s < seq; s++) {
            // Each projection layer is shared across rows; replay forward for exact cache.
            wq.forward(lastX[s]);
            float[] a = wq.backward(dQ[s]);
            wk.forward(lastX[s]);
            float[] b = wk.backward(dK[s]);
            wv.forward(lastX[s]);
            float[] c = wv.backward(dV[s]);
            for (int d = 0; d < dim; d++) dIn[s][d] = a[d] + b[d] + c[d];
        }
        return dIn;
    }

    private float[][] rebuildContext() {
        int seq = lastX.length;
        float[][] context = new float[seq][dim];
        for (int h = 0; h < heads; h++) {
            int off = h * headDim;
            for (int q = 0; q < seq; q++) {
                for (int k = 0; k < seq; k++) {
                    float p = lastProbs[h][q][k];
                    for (int d = 0; d < headDim; d++) {
                        context[q][off + d] += p * lastV[k][off + d];
                    }
                }
            }
        }
        return context;
    }

    public void zeroGrad() {
        wq.zeroGrad();
        wk.zeroGrad();
        wv.zeroGrad();
        wo.zeroGrad();
    }

    public void collectParams(List<float[]> params, List<float[]> grads) {
        wq.collectParams(params, grads);
        wk.collectParams(params, grads);
        wv.collectParams(params, grads);
        wo.collectParams(params, grads);
    }

    public long paramCount() {
        return wq.paramCount() + wk.paramCount() + wv.paramCount() + wo.paramCount();
    }

    public void write(DataOutput out) throws IOException {
        wq.write(out);
        wk.write(out);
        wv.write(out);
        wo.write(out);
    }

    public void read(DataInput in) throws IOException {
        wq.read(in);
        wk.read(in);
        wv.read(in);
        wo.read(in);
    }
}
