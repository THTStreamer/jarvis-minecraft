package com.jarvis.ai.neural;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

/**
 * JarvisNeuralNetwork: tokenizer -> embeddings (+ learned positions) ->
 * transformer encoder blocks -> sentence representation + next-token head.
 *
 * <p>Two training signals are supported:
 * <ul>
 *   <li>next-token prediction (language-model loss over conversations/registry text)</li>
 *   <li>sentence-vector alignment (used by intent/semantic heads)</li>
 * </ul>
 * The layout is fixed-size (maxVocab x dim) so the vocabulary can grow without
 * invalidating saved weights.
 */
public final class JarvisNeuralNetwork {
    public static final int SERIAL_VERSION = 3;

    private final int maxVocab;
    private final int dim;
    private final int heads;
    private final int blocks;
    private final int ffnDim;
    private final int maxSeq;
    private final long seed;

    private final EmbeddingLayer tokenEmbed;
    private final EmbeddingLayer posEmbed;
    private final List<TransformerBlock> encoder;
    private final LayerNorm finalNorm;
    private final DenseLayer lmHead; // dim -> maxVocab (tied-ish output projection)

    // caches for training
    private int[] lastIds;
    private float[][] lastHidden;

    public JarvisNeuralNetwork(int maxVocab, int dim, int heads, int blocks, int ffnDim, int maxSeq, long seed) {
        if (dim % heads != 0) throw new IllegalArgumentException("dim % heads != 0");
        this.maxVocab = maxVocab;
        this.dim = dim;
        this.heads = heads;
        this.blocks = blocks;
        this.ffnDim = ffnDim;
        this.maxSeq = maxSeq;
        this.seed = seed;
        Random rng = new Random(seed);
        this.tokenEmbed = new EmbeddingLayer(maxVocab, dim, rng);
        this.posEmbed = new EmbeddingLayer(maxSeq, dim, rng);
        this.encoder = new ArrayList<>(blocks);
        for (int i = 0; i < blocks; i++) encoder.add(new TransformerBlock(dim, heads, ffnDim, rng));
        this.finalNorm = new LayerNorm(dim);
        this.lmHead = new DenseLayer(dim, maxVocab, Activation.LINEAR, rng);
    }

    public int dim() { return dim; }
    public int maxVocab() { return maxVocab; }
    public int maxSeq() { return maxSeq; }

    public long paramCount() {
        long n = tokenEmbed.paramCount() + posEmbed.paramCount() + finalNorm.paramCount() + lmHead.paramCount();
        for (TransformerBlock b : encoder) n += b.paramCount();
        return n;
    }

    private int[] clamp(int[] ids) {
        int n = Math.min(ids.length, maxSeq);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            int id = ids[i];
            out[i] = Math.max(0, Math.min(maxVocab - 1, id));
        }
        return out;
    }

    /** Encode a token sequence into hidden states [seq x dim]. */
    public synchronized float[][] encodeHidden(int[] ids) {
        int[] c = clamp(ids);
        if (c.length == 0) return new float[0][dim];
        float[][] h = tokenEmbed.forward(c);
        int[] pos = new int[c.length];
        for (int i = 0; i < pos.length; i++) pos[i] = i;
        float[][] p = posEmbed.forward(pos);
        for (int s = 0; s < h.length; s++) {
            for (int d = 0; d < dim; d++) h[s][d] += p[s][d];
        }
        for (TransformerBlock b : encoder) h = b.forward(h);
        return h;
    }

    /** Sentence vector = mean of final-normed hidden states. */
    public synchronized float[] encodeSentence(int[] ids) {
        float[][] h = encodeHidden(ids);
        if (h.length == 0) return new float[dim];
        float[] mean = new float[dim];
        for (float[] row : h) {
            // per-row norm replay for exactness is unnecessary at inference; share forward
            float[] n = finalNorm.forward(row);
            for (int d = 0; d < dim; d++) mean[d] += n[d];
        }
        for (int d = 0; d < dim; d++) mean[d] /= h.length;
        return mean;
    }

    /** Next-token logits for the last position. */
    public synchronized float[] nextTokenLogits(int[] ids) {
        float[] s = encodeSentenceForLm(ids);
        return lmHead.forward(s);
    }

    private float[] encodeSentenceForLm(int[] ids) {
        float[][] h = encodeHidden(ids);
        if (h.length == 0) return new float[dim];
        float[] mean = new float[dim];
        for (float[] row : h) {
            float[] n = finalNorm.forward(row);
            for (int d = 0; d < dim; d++) mean[d] += n[d];
        }
        for (int d = 0; d < dim; d++) mean[d] /= h.length;
        return mean;
    }

    /**
     * One training step of next-token prediction. Returns the loss.
     * Gradients are accumulated into layers and applied with the given optimizer.
     */
    public synchronized float trainNextToken(int[] ids, int target, Optimizer optimizer) {
        int[] c = clamp(ids);
        if (c.length == 0) return 0f;
        zeroGrad();
        lastIds = c;
        // forward with caches
        float[][] h = tokenEmbed.forward(c);
        int[] pos = new int[c.length];
        for (int i = 0; i < pos.length; i++) pos[i] = i;
        float[][] p = posEmbed.forward(pos);
        for (int s = 0; s < h.length; s++) {
            for (int d = 0; d < dim; d++) h[s][d] += p[s][d];
        }
        for (TransformerBlock b : encoder) h = b.forward(h);
        lastHidden = h;
        float[][] hn = new float[h.length][dim];
        for (int s = 0; s < h.length; s++) hn[s] = finalNorm.forward(h[s]);
        float[] mean = new float[dim];
        for (float[] row : hn) {
            for (int d = 0; d < dim; d++) mean[d] += row[d];
        }
        for (int d = 0; d < dim; d++) mean[d] /= hn.length;
        float[] logits = lmHead.forward(mean);
        int t = Math.max(0, Math.min(maxVocab - 1, target));
        LossFunctions.CeResult ce = LossFunctions.softmaxCrossEntropy(logits, t);
        // backward
        float[] dMean = lmHead.backward(ce.dLogits);
        float[][] dHn = new float[hn.length][dim];
        for (int s = 0; s < hn.length; s++) {
            for (int d = 0; d < dim; d++) dHn[s][d] = dMean[d] / hn.length;
        }
        float[][] dH = new float[h.length][dim];
        for (int s = h.length - 1; s >= 0; s--) {
            finalNorm.forward(h[s]);
            dH[s] = finalNorm.backward(dHn[s]);
        }
        for (int i = encoder.size() - 1; i >= 0; i--) dH = encoder.get(i).backward(dH);
        // split embedding grads between token and positional tables
        float[][] dTok = new float[c.length][dim];
        float[][] dPos = new float[c.length][dim];
        for (int s = 0; s < c.length; s++) {
            for (int d = 0; d < dim; d++) {
                dTok[s][d] = dH[s][d] * 0.5f;
                dPos[s][d] = dH[s][d] * 0.5f;
            }
        }
        tokenEmbed.backward(c, dTok);
        posEmbed.backward(pos, dPos);
        List<float[]> params = new ArrayList<>();
        List<float[]> grads = new ArrayList<>();
        collectParams(params, grads);
        optimizer.step(params, grads);
        return ce.loss;
    }

    /** One alignment step pulling the sentence vector toward a target (intent prototype). */
    public synchronized float trainAlign(int[] ids, float[] target, Optimizer optimizer) {
        int[] c = clamp(ids);
        if (c.length == 0 || target.length != dim) return 0f;
        zeroGrad();
        float[][] h = tokenEmbed.forward(c);
        int[] pos = new int[c.length];
        for (int i = 0; i < pos.length; i++) pos[i] = i;
        float[][] p = posEmbed.forward(pos);
        for (int s = 0; s < h.length; s++) {
            for (int d = 0; d < dim; d++) h[s][d] += p[s][d];
        }
        for (TransformerBlock b : encoder) h = b.forward(h);
        float[][] hn = new float[h.length][dim];
        for (int s = 0; s < h.length; s++) hn[s] = finalNorm.forward(h[s]);
        float[] mean = new float[dim];
        for (float[] row : hn) {
            for (int d = 0; d < dim; d++) mean[d] += row[d];
        }
        for (int d = 0; d < dim; d++) mean[d] /= hn.length;
        float[] dMean = new float[dim];
        float loss = LossFunctions.cosineAlign(mean, target, dMean);
        // scale update so alignment steps stay small and stable
        for (int d = 0; d < dim; d++) dMean[d] *= 0.25f;
        float[][] dHn = new float[hn.length][dim];
        for (int s = 0; s < hn.length; s++) {
            for (int d = 0; d < dim; d++) dHn[s][d] = dMean[d] / hn.length;
        }
        float[][] dH = new float[h.length][dim];
        for (int s = h.length - 1; s >= 0; s--) {
            finalNorm.forward(h[s]);
            dH[s] = finalNorm.backward(dHn[s]);
        }
        for (int i = encoder.size() - 1; i >= 0; i--) dH = encoder.get(i).backward(dH);
        tokenEmbed.backward(c, dH);
        List<float[]> params = new ArrayList<>();
        List<float[]> grads = new ArrayList<>();
        collectParams(params, grads);
        optimizer.step(params, grads);
        return loss;
    }

    private void zeroGrad() {
        tokenEmbed.zeroGrad();
        posEmbed.zeroGrad();
        finalNorm.zeroGrad();
        lmHead.zeroGrad();
        for (TransformerBlock b : encoder) b.zeroGrad();
    }

    private void collectParams(List<float[]> params, List<float[]> grads) {
        tokenEmbed.collectParams(params, grads);
        posEmbed.collectParams(params, grads);
        for (TransformerBlock b : encoder) b.collectParams(params, grads);
        params.add(finalNorm.gain()); grads.add(finalNorm.gradGain());
        params.add(finalNorm.bias()); grads.add(finalNorm.gradBias());
        lmHead.collectParams(params, grads);
    }

    // ---- serialization ----

    public void write(DataOutput out) throws IOException {
        out.writeInt(SERIAL_VERSION);
        out.writeInt(maxVocab);
        out.writeInt(dim);
        out.writeInt(heads);
        out.writeInt(blocks);
        out.writeInt(ffnDim);
        out.writeInt(maxSeq);
        out.writeLong(seed);
        tokenEmbed.write(out);
        posEmbed.write(out);
        for (TransformerBlock b : encoder) b.write(out);
        finalNorm.write(out);
        lmHead.write(out);
    }

    public void read(DataInput in) throws IOException {
        int version = in.readInt();
        if (version != SERIAL_VERSION) {
            throw new IOException("Unsupported neural model version: " + version);
        }
        int mv = in.readInt();
        int dm = in.readInt();
        int hd = in.readInt();
        int bl = in.readInt();
        int ff = in.readInt();
        int ms = in.readInt();
        in.readLong();
        if (mv != maxVocab || dm != dim || hd != heads || bl != blocks || ff != ffnDim || ms != maxSeq) {
            throw new IOException("Model layout mismatch; cannot load weights into this network");
        }
        tokenEmbed.read(in);
        posEmbed.read(in);
        for (TransformerBlock b : encoder) b.read(in);
        finalNorm.read(in);
        lmHead.read(in);
    }

    public String toBase64() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        write(dos);
        dos.flush();
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    public void fromBase64(String data) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(data);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes));
        read(dis);
    }
}
