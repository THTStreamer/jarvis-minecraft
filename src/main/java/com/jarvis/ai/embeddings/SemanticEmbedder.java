package com.jarvis.ai.embeddings;

import com.jarvis.ai.neural.JarvisNeuralNetwork;
import com.jarvis.ai.tokenizer.JarvisTokenizer;
import com.jarvis.util.Floats;

/**
 * Sentence-level semantic representations: tokenizer -> neural encoder ->
 * L2-normalized vector, with cosine similarity for retrieval and intent scoring.
 */
public final class SemanticEmbedder {
    private final JarvisTokenizer tokenizer;
    private final JarvisNeuralNetwork network;

    public SemanticEmbedder(JarvisTokenizer tokenizer, JarvisNeuralNetwork network) {
        this.tokenizer = tokenizer;
        this.network = network;
    }

    public float[] embed(String text) {
        int[] ids = tokenizer.encode(text, false);
        float[] v = network.encodeSentence(ids);
        float n = Floats.norm(v);
        if (n > 1e-9f) {
            for (int i = 0; i < v.length; i++) v[i] /= n;
        }
        return v;
    }

    public float similarity(String a, String b) {
        return Floats.cosine(embed(a), embed(b));
    }

    public float similarity(float[] a, float[] b) {
        return Floats.cosine(a, b);
    }
}
