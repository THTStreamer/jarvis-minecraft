package com.jarvis.voice;

/** English phoneme inventory used by the synthesizer. */
public enum Phoneme {
    // vowels
    IY, IH, EY, EH, AE, AA, AO, OW, UW, UH, AH, ER, AY, AW, OY,
    // consonants
    P, B, T, D, K, G, CH, JH, F, V, TH, DH, S, Z, SH, ZH, HH, M, N, NG, L, R, W, Y,
    // control
    PAUSE, SENTENCE_END;

    public boolean isVowel() {
        return switch (this) {
            case IY, IH, EY, EH, AE, AA, AO, OW, UW, UH, AH, ER, AY, AW, OY -> true;
            default -> false;
        };
    }

    public boolean isSonorant() {
        return isVowel() || switch (this) {
            case M, N, NG, L, R, W, Y -> true;
            default -> false;
        };
    }
}
