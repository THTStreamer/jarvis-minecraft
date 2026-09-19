package com.jarvis.voice;

import java.util.List;
import java.util.Random;

/**
 * Parametric speech synthesizer. Each phoneme maps to formant targets;
 * frames render band-limited harmonic stacks plus aspiration noise through
 * an amplitude envelope, producing 16-bit PCM at 22050 Hz. Deterministic
 * per timbre seed; quality scales with the configured sample budget.
 */
public final class SpeechSynthesizer {
    public static final int SAMPLE_RATE = 22050;

    private final VoiceProfile profile;

    public SpeechSynthesizer(VoiceProfile profile) {
        this.profile = profile;
    }

    public short[] synthesize(List<ProsodyEngine.Frame> frames) {
        int total = 0;
        for (ProsodyEngine.Frame f : frames) {
            total += (int) (SAMPLE_RATE * f.durationMs() / 1000f);
        }
        short[] pcm = new short[Math.max(1, total)];
        Random rng = new Random(profile.timbreSeed());
        double phase = 0.0;
        int idx = 0;
        for (ProsodyEngine.Frame f : frames) {
            int samples = (int) (SAMPLE_RATE * f.durationMs() / 1000f);
            double[] formants = formantsFor(f.phoneme());
            boolean voiced = f.phoneme().isSonorant();
            for (int s = 0; s < samples && idx < pcm.length; s++, idx++) {
                double t = (double) s / samples;
                double env = Math.sin(Math.PI * Math.min(1.0, t * 1.02)); // smooth on/off
                env = env * env;
                double sample;
                if (f.volume() <= 0f) {
                    sample = 0.0;
                } else if (!voiced) {
                    // unvoiced: shaped noise burst
                    double noise = rng.nextGaussian() * 0.5;
                    double bright = formants[0] / 6000.0;
                    sample = noise * (0.25 + 0.5 * bright) * env * f.volume();
                } else {
                    phase += 2.0 * Math.PI * f.pitchHz() / SAMPLE_RATE;
                    double harm = 0.0;
                    for (int h = 1; h <= 8; h++) {
                        double f_h = f.pitchHz() * h;
                        double amp = 1.0 / (h * h);
                        // formant emphasis
                        for (double formant : formants) {
                            double dist = Math.abs(f_h - formant) / formant;
                            if (dist < 0.25) amp *= 1.0 + 3.0 * (1.0 - dist / 0.25);
                        }
                        harm += amp * Math.sin(phase * h);
                    }
                    double breath = rng.nextGaussian() * 0.02;
                    sample = (harm * 0.35 + breath) * env * f.volume();
                }
                pcm[idx] = (short) Math.max(-32768, Math.min(32767, (int) (sample * 32767)));
            }
        }
        return pcm;
    }

    /** First three formant targets per phoneme (Hz). */
    private static double[] formantsFor(Phoneme p) {
        return switch (p) {
            case IY -> new double[]{300, 2300, 3000};
            case IH -> new double[]{400, 2000, 2550};
            case EY -> new double[]{500, 1900, 2500};
            case EH -> new double[]{550, 1800, 2500};
            case AE -> new double[]{700, 1700, 2500};
            case AA -> new double[]{750, 1200, 2500};
            case AO -> new double[]{550, 1000, 2500};
            case OW -> new double[]{500, 1100, 2500};
            case UW -> new double[]{350, 1000, 2300};
            case UH -> new double[]{450, 1100, 2400};
            case AH -> new double[]{600, 1200, 2500};
            case ER -> new double[]{450, 1400, 2400};
            case AY -> new double[]{650, 1800, 2600};
            case AW -> new double[]{650, 1100, 2500};
            case OY -> new double[]{450, 1600, 2500};
            case M, N, NG -> new double[]{300, 1200, 2400};
            case L, R, W, Y -> new double[]{400, 1300, 2500};
            case S, Z -> new double[]{5500, 6500, 7500};
            case SH, ZH -> new double[]{3500, 4500, 5500};
            case F, V -> new double[]{2500, 3500, 4500};
            case TH, DH -> new double[]{3000, 4000, 5000};
            case HH -> new double[]{1500, 2500, 3500};
            default -> new double[]{800, 1500, 2500};
        };
    }
}
