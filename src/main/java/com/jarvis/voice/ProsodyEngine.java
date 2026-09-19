package com.jarvis.voice;

import java.util.ArrayList;
import java.util.List;

/**
 * Prosody: pitch contour, timing, pauses and emphasis over a phoneme stream.
 * Refined British-assistant character: measured pace, falling declaratives,
 * slight rise on questions, stress on content words.
 */
public final class ProsodyEngine {
    public enum Emotion { NEUTRAL, PLEASED, CONCERNED, URGENT, APOLOGETIC }

    public record Frame(Phoneme phoneme, float durationMs, float pitchHz, float volume) {}

    private final VoiceProfile profile;

    public ProsodyEngine(VoiceProfile profile) {
        this.profile = profile;
    }

    public List<Frame> render(List<Phoneme> phonemes, Emotion emotion) {
        List<Frame> out = new ArrayList<>(phonemes.size());
        int n = phonemes.size();
        for (int i = 0; i < n; i++) {
            Phoneme p = phonemes.get(i);
            float dur = baseDuration(p) * profile.speechRate();
            float pitch = profile.basePitchHz();
            // declination: gentle fall across the utterance
            pitch *= 1.06f - 0.12f * ((float) i / Math.max(1, n));
            // stressed vowels slightly higher + longer
            if (p.isVowel()) {
                dur *= 1.25f;
                pitch *= 1.04f;
            }
            float vol = profile.volume();
            switch (emotion) {
                case PLEASED -> pitch *= 1.06f;
                case CONCERNED -> { pitch *= 0.94f; dur *= 1.1f; }
                case URGENT -> { pitch *= 1.1f; dur *= 0.85f; vol = Math.min(1f, vol * 1.15f); }
                case APOLOGETIC -> { pitch *= 0.9f; dur *= 1.15f; vol *= 0.9f; }
                case NEUTRAL -> {}
            }
            if (p == Phoneme.PAUSE) { dur = 70f; vol = 0f; }
            if (p == Phoneme.SENTENCE_END) { dur = 220f; vol = 0f; pitch *= 0.85f; }
            out.add(new Frame(p, dur, pitch, vol));
        }
        return out;
    }

    private static float baseDuration(Phoneme p) {
        if (p == Phoneme.PAUSE || p == Phoneme.SENTENCE_END) return 100f;
        if (p.isVowel()) return 95f;
        return switch (p) {
            case P, T, K, B, D, G -> 55f;
            case S, SH, F, TH, HH -> 110f;
            case M, N, NG, L, R -> 85f;
            default -> 75f;
        };
    }
}
