package com.jarvis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jarvis.ai.neural.Activation;
import com.jarvis.ai.neural.DenseLayer;
import com.jarvis.ai.neural.JarvisNeuralNetwork;
import com.jarvis.ai.neural.LossFunctions;
import com.jarvis.ai.neural.MultiHeadAttention;
import com.jarvis.ai.neural.Optimizer;
import com.jarvis.ai.neural.TransformerBlock;
import com.jarvis.util.Floats;
import java.util.Random;
import org.junit.jupiter.api.Test;

public class NeuralTest {
    @Test
    public void denseForwardBackward() {
        DenseLayer layer = new DenseLayer(4, 3, Activation.TANH, new Random(1));
        float[] out = layer.forward(new float[]{0.5f, -0.5f, 0.25f, 0.1f});
        assertEquals(3, out.length);
        float[] dIn = layer.backward(new float[]{0.1f, -0.2f, 0.3f});
        assertEquals(4, dIn.length);
        for (float v : dIn) assertTrue(Float.isFinite(v), "finite gradients");
    }

    @Test
    public void attentionHoldsShape() {
        MultiHeadAttention attn = new MultiHeadAttention(16, 4, new Random(2));
        float[][] x = new float[5][16];
        Random rng = new Random(3);
        for (float[] row : x) for (int i = 0; i < row.length; i++) row[i] = rng.nextFloat() - 0.5f;
        float[][] y = attn.forward(x);
        assertEquals(5, y.length);
        assertEquals(16, y[0].length);
        float[][] dIn = attn.backward(y);
        assertEquals(5, dIn.length);
        for (float[] row : dIn) for (float v : row) assertTrue(Float.isFinite(v), "finite attention grads");
    }

    @Test
    public void transformerBlockTrains() {
        TransformerBlock block = new TransformerBlock(16, 4, 32, new Random(4));
        float[][] x = new float[4][16];
        float[][] y = block.forward(x);
        assertEquals(4, y.length);
        float[][] dIn = block.backward(y);
        assertEquals(4, dIn.length);
    }

    @Test
    public void networkTrainsAndSerializes() throws Exception {
        JarvisNeuralNetwork net = new JarvisNeuralNetwork(64, 16, 4, 1, 32, 16, 42L);
        Optimizer opt = Optimizer.adam(1e-3f);
        int[] ids = {2, 10, 11, 12, 3};
        float loss1 = net.trainNextToken(ids, 12, opt);
        float loss2 = net.trainNextToken(ids, 12, opt);
        assertTrue(Float.isFinite(loss1) && Float.isFinite(loss2), "finite losses");
        float[] vec = net.encodeSentence(ids);
        assertEquals(16, vec.length);
        String blob = net.toBase64();
        JarvisNeuralNetwork net2 = new JarvisNeuralNetwork(64, 16, 4, 1, 32, 16, 99L);
        net2.fromBase64(blob);
        float[] vec2 = net2.encodeSentence(ids);
        for (int i = 0; i < vec.length; i++) {
            assertEquals(vec[i], vec2[i], 1e-5f, "weights survive serialization");
        }
    }

    @Test
    public void lossesBehave() {
        var ce = LossFunctions.softmaxCrossEntropy(new float[]{1f, 2f, 0.5f}, 1);
        assertTrue(ce.loss > 0 && Float.isFinite(ce.loss), "ce loss");
        float sum = 0;
        for (float v : ce.dLogits) sum += v;
        assertEquals(0f, sum, 1e-5f, "softmax gradient sums to zero");
        assertTrue(Floats.cosine(new float[]{1, 0}, new float[]{1, 0}) > 0.99f, "cosine identity");
    }
}
