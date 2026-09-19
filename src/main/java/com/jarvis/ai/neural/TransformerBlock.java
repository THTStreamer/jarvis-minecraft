package com.jarvis.ai.neural;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Pre-norm transformer block: x + Attn(Norm(x)), then x + FFN(Norm(x)).
 * FFN: dense(dim -> ffn) + GELU + dense(ffn -> dim).
 */
public final class TransformerBlock {
    private final int dim;
    private final LayerNorm norm1;
    private final MultiHeadAttention attention;
    private final LayerNorm norm2;
    private final DenseLayer ffn1;
    private final DenseLayer ffn2;

    // caches
    private float[][] lastX;
    private float[][] lastA;
    private float[][] lastH1;
    private float[][] lastH2;
    private float[][] lastH3;

    public TransformerBlock(int dim, int heads, int ffnDim, Random rng) {
        this.dim = dim;
        this.norm1 = new LayerNorm(dim);
        this.attention = new MultiHeadAttention(dim, heads, rng);
        this.norm2 = new LayerNorm(dim);
        this.ffn1 = new DenseLayer(dim, ffnDim, Activation.GELU, rng);
        this.ffn2 = new DenseLayer(ffnDim, dim, Activation.LINEAR, rng);
    }

    public float[][] forward(float[][] x) {
        int seq = x.length;
        lastX = new float[seq][dim];
        for (int s = 0; s < seq; s++) lastX[s] = x[s].clone();

        float[][] n1 = new float[seq][dim];
        for (int s = 0; s < seq; s++) n1[s] = norm1ForwardRow(x[s], s == 0);
        // LayerNorm caches only the last row; replay per-row below in backward instead.
        float[][] a = attention.forward(n1);
        lastA = new float[seq][dim];
        float[][] h1 = new float[seq][dim];
        for (int s = 0; s < seq; s++) {
            for (int d = 0; d < dim; d++) {
                lastA[s][d] = a[s][d];
                h1[s][d] = x[s][d] + a[s][d];
            }
        }
        lastH1 = h1;
        float[][] n2 = new float[seq][dim];
        for (int s = 0; s < seq; s++) n2[s] = norm2ForwardRow(h1[s]);
        lastH2 = n2;
        lastH3 = new float[seq][];
        float[][] h3 = new float[seq][dim];
        for (int s = 0; s < seq; s++) {
            float[] h = ffn1.forward(n2[s]);
            lastH3[s] = h;
            float[] o = ffn2.forward(h);
            for (int d = 0; d < dim; d++) h3[s][d] = h1[s][d] + o[d];
        }
        return h3;
    }

    // norm1 is shared across rows; forward caches last row only, so record per-row outputs here
    // and replay exact forward/backward per row during backward().
    private float[][] norm1Rows;
    private float[][] norm2Rows;

    private float[] norm1ForwardRow(float[] x, boolean first) {
        if (first) norm1Rows = new float[lastX.length][dim];
        float[] y = norm1.forward(x);
        // store placeholder; actual per-row replay happens in backward
        return y;
    }

    private float[] norm2ForwardRow(float[] x) {
        if (norm2Rows == null || norm2Rows.length != lastX.length) norm2Rows = new float[lastX.length][dim];
        return norm2.forward(x);
    }

    public float[][] backward(float[][] dOut) {
        int seq = lastX.length;
        float[][] dH1 = new float[seq][dim];
        // FFN branch (per-row replay for exact layer caches)
        for (int s = 0; s < seq; s++) {
            ffn1.forward(lastH2[s]);
            float[] h = lastH3[s].clone();
            // re-run ffn2 forward to restore its cache, then backprop
            float[] o = ffn2.forward(h);
            float[] dO = dOut[s].clone();
            float[] dH = ffn2.backward(dO);
            float[] dN2 = ffn1.backward(dH);
            // residual: dH1 += dOut + dN2 path handled below via norm2
            dH1[s] = dO.clone(); // residual contribution
            // stash dN2 for norm2 backward
            if (s == 0) norm2GradAccum = new float[seq][dim];
            norm2GradAccum[s] = dN2;
            @SuppressWarnings("unused") float[] oo = o;
            @SuppressWarnings("unused") float[] hh = h;
        }
        // norm2 backward per row
        for (int s = 0; s < seq; s++) {
            norm2.forward(lastH1[s]);
            float[] dPre = norm2.backward(norm2GradAccum[s]);
            for (int d = 0; d < dim; d++) dH1[s][d] += dPre[d];
        }
        // attention branch
        float[][] dN1 = attention.backward(extractAttnGrad(dH1));
        float[][] dIn = new float[seq][dim];
        for (int s = 0; s < seq; s++) {
            norm1.forward(lastX[s]);
            float[] dPre = norm1.backward(dN1[s]);
            for (int d = 0; d < dim; d++) dIn[s][d] = dH1[s][d] + dPre[d];
        }
        return dIn;
    }

    private float[][] norm2GradAccum;

    private float[][] extractAttnGrad(float[][] dH1) {
        // dH1 currently holds residual + norm2 contributions; attention output gradient equals
        // the part flowing into h1, which is exactly dH1 (both paths merge additively at h1).
        // The residual path gradient stays in dH1; attention needs the same upstream value.
        float[][] g = new float[dH1.length][dim];
        for (int s = 0; s < dH1.length; s++) g[s] = dH1[s].clone();
        return g;
    }

    public void zeroGrad() {
        norm1.zeroGrad();
        norm2.zeroGrad();
        attention.zeroGrad();
        ffn1.zeroGrad();
        ffn2.zeroGrad();
    }

    public void collectParams(List<float[]> params, List<float[]> grads) {
        params.add(norm1.gain()); grads.add(norm1.gradGain());
        params.add(norm1.bias()); grads.add(norm1.gradBias());
        attention.collectParams(params, grads);
        params.add(norm2.gain()); grads.add(norm2.gradGain());
        params.add(norm2.bias()); grads.add(norm2.gradBias());
        ffn1.collectParams(params, grads);
        ffn2.collectParams(params, grads);
    }

    public long paramCount() {
        return norm1.paramCount() + attention.paramCount() + norm2.paramCount()
                + ffn1.paramCount() + ffn2.paramCount();
    }

    public void write(DataOutput out) throws IOException {
        norm1.write(out);
        attention.write(out);
        norm2.write(out);
        ffn1.write(out);
        ffn2.write(out);
    }

    public void read(DataInput in) throws IOException {
        norm1.read(in);
        attention.read(in);
        norm2.read(in);
        ffn1.read(in);
        ffn2.read(in);
    }
}
