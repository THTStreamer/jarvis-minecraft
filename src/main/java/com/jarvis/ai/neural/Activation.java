package com.jarvis.ai.neural;

/** Activation functions with forward and derivative (given the activated value where possible). */
public enum Activation {
    RELU {
        @Override public float forward(float x) { return Math.max(0f, x); }
        @Override public float deriv(float x, float y) { return x > 0f ? 1f : 0f; }
    },
    TANH {
        @Override public float forward(float x) { return (float) Math.tanh(x); }
        @Override public float deriv(float x, float y) { return 1f - y * y; }
    },
    SIGMOID {
        @Override public float forward(float x) { return (float) (1.0 / (1.0 + Math.exp(-x))); }
        @Override public float deriv(float x, float y) { return y * (1f - y); }
    },
    GELU {
        @Override public float forward(float x) {
            // tanh approximation of GELU
            double c = Math.sqrt(2.0 / Math.PI);
            double inner = c * (x + 0.044715 * x * x * x);
            return (float) (0.5 * x * (1.0 + Math.tanh(inner)));
        }
        @Override public float deriv(float x, float y) {
            // Numeric derivative of the approximation; cheap and stable for our sizes.
            float e = 1e-3f;
            return (forward(x + e) - forward(x - e)) / (2f * e);
        }
    },
    LINEAR {
        @Override public float forward(float x) { return x; }
        @Override public float deriv(float x, float y) { return 1f; }
    };

    public abstract float forward(float x);
    /** @param x pre-activation, @param y post-activation value */
    public abstract float deriv(float x, float y);

    public void forwardInPlace(float[] v) {
        for (int i = 0; i < v.length; i++) v[i] = forward(v[i]);
    }
}
