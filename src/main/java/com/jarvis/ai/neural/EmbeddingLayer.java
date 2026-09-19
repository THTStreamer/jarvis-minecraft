package com.jarvis.ai.neural;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Learnable token embedding table. Rows beyond the active vocabulary stay
 * allocated so the vocabulary can grow without changing the model layout.
 */
public final class EmbeddingLayer {
    private final int maxVocab;
    private final int dim;
    private final float[][] table;     // [maxVocab][dim]
    private final float[][] gradTable;

    public EmbeddingLayer(int maxVocab, int dim, Random rng) {
        this.maxVocab = maxVocab;
        this.dim = dim;
        this.table = new float[maxVocab][dim];
        this.gradTable = new float[maxVocab][dim];
        float limit = 0.05f;
        for (int i = 0; i < maxVocab; i++) {
            for (int j = 0; j < dim; j++) {
                table[i][j] = (rng.nextFloat() * 2f - 1f) * limit;
            }
        }
    }

    public int dim() { return dim; }
    public int maxVocab() { return maxVocab; }

    /** Embed a token id into a fresh vector. */
    public float[] embed(int id) {
        return table[id].clone();
    }

    /** Embed a sequence of ids into a [seq x dim] matrix. */
    public float[][] forward(int[] ids) {
        float[][] out = new float[ids.length][dim];
        for (int s = 0; s < ids.length; s++) {
            System.arraycopy(table[ids[s]], 0, out[s], 0, dim);
        }
        return out;
    }

    /** Accumulate embedding gradients for one sequence. */
    public void backward(int[] ids, float[][] dEmb) {
        for (int s = 0; s < ids.length; s++) {
            float[] g = gradTable[ids[s]];
            float[] d = dEmb[s];
            for (int j = 0; j < dim; j++) g[j] += d[j];
        }
    }

    public void zeroGrad() {
        for (float[] row : gradTable) {
            for (int j = 0; j < row.length; j++) row[j] = 0f;
        }
    }

    public void collectParams(List<float[]> params, List<float[]> grads) {
        for (int i = 0; i < maxVocab; i++) {
            params.add(table[i]);
            grads.add(gradTable[i]);
        }
    }

    public List<float[]> paramRows() {
        List<float[]> rows = new ArrayList<>(maxVocab);
        for (float[] row : table) rows.add(row);
        return rows;
    }

    public List<float[]> gradRows() {
        List<float[]> rows = new ArrayList<>(maxVocab);
        for (float[] row : gradTable) rows.add(row);
        return rows;
    }

    public long paramCount() { return (long) maxVocab * dim; }

    public void write(DataOutput out) throws IOException {
        for (float[] row : table) for (float v : row) out.writeFloat(v);
    }

    public void read(DataInput in) throws IOException {
        for (float[] row : table) for (int j = 0; j < row.length; j++) row[j] = in.readFloat();
    }
}
