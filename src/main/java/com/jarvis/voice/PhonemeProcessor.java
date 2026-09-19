package com.jarvis.voice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Grapheme-to-phoneme conversion: pronunciation dictionary for common words
 * plus rule-based fallback for the long tail. Sentence boundaries and pauses
 * are preserved for prosody.
 */
public final class PhonemeProcessor {
    private final Map<String, List<Phoneme>> dictionary = new LinkedHashMap<>();

    public PhonemeProcessor() {
        seed();
    }

    private void put(String word, Phoneme... phonemes) {
        dictionary.put(word, List.of(phonemes));
    }

    private void seed() {
        put("sir", Phoneme.S, Phoneme.ER);
        put("jarvis", Phoneme.JH, Phoneme.AA, Phoneme.R, Phoneme.V, Phoneme.IH, Phoneme.S);
        put("yes", Phoneme.Y, Phoneme.EH, Phoneme.S);
        put("hello", Phoneme.HH, Phoneme.EH, Phoneme.L, Phoneme.OW);
        put("village", Phoneme.V, Phoneme.IH, Phoneme.L, Phoneme.IH, Phoneme.JH);
        put("scan", Phoneme.S, Phoneme.K, Phoneme.AE, Phoneme.N);
        put("hostile", Phoneme.HH, Phoneme.AA, Phoneme.S, Phoneme.T, Phoneme.AY, Phoneme.L);
        put("detected", Phoneme.D, Phoneme.IH, Phoneme.T, Phoneme.EH, Phoneme.K, Phoneme.T, Phoneme.IH, Phoneme.D);
        put("located", Phoneme.L, Phoneme.OW, Phoneme.K, Phoneme.EY, Phoneme.T, Phoneme.IH, Phoneme.D);
        put("blocks", Phoneme.B, Phoneme.L, Phoneme.AA, Phoneme.K, Phoneme.S);
        put("north", Phoneme.N, Phoneme.AO, Phoneme.R, Phoneme.TH);
        put("south", Phoneme.S, Phoneme.AW, Phoneme.TH);
        put("east", Phoneme.IY, Phoneme.S, Phoneme.T);
        put("west", Phoneme.W, Phoneme.EH, Phoneme.S, Phoneme.T);
        put("diamond", Phoneme.D, Phoneme.AY, Phoneme.M, Phoneme.AH, Phoneme.N, Phoneme.D);
        put("zombie", Phoneme.Z, Phoneme.AA, Phoneme.M, Phoneme.B, Phoneme.IY);
        put("skeleton", Phoneme.S, Phoneme.K, Phoneme.EH, Phoneme.L, Phoneme.IH, Phoneme.T, Phoneme.AH, Phoneme.N);
        put("creeper", Phoneme.K, Phoneme.R, Phoneme.IY, Phoneme.P, Phoneme.ER);
        put("certainly", Phoneme.S, Phoneme.ER, Phoneme.T, Phoneme.AH, Phoneme.N, Phoneme.L, Phoneme.IY);
        put("course", Phoneme.K, Phoneme.AO, Phoneme.R, Phoneme.S);
        put("service", Phoneme.S, Phoneme.ER, Phoneme.V, Phoneme.IH, Phoneme.S);
    }

    public void define(String word, List<Phoneme> phonemes) {
        dictionary.put(word.toLowerCase(Locale.ROOT), new ArrayList<>(phonemes));
    }

    public List<Phoneme> process(String text) {
        List<Phoneme> out = new ArrayList<>();
        String[] sentences = text.split("(?<=[.!?])\\s+");
        for (int si = 0; si < sentences.length; si++) {
            String[] words = sentences[si].toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", "").split("\\s+");
            for (String w : words) {
                if (w.isEmpty()) continue;
                List<Phoneme> known = dictionary.get(w);
                if (known != null) {
                    out.addAll(known);
                } else {
                    out.addAll(ruleBased(w));
                }
                out.add(Phoneme.PAUSE);
            }
            out.add(Phoneme.SENTENCE_END);
        }
        return out;
    }

    /** Rule-based fallback: consonant/vowel skeleton mapping. */
    private List<Phoneme> ruleBased(String word) {
        List<Phoneme> out = new ArrayList<>();
        int i = 0;
        while (i < word.length()) {
            if (word.startsWith("sh", i)) { out.add(Phoneme.SH); i += 2; }
            else if (word.startsWith("ch", i)) { out.add(Phoneme.CH); i += 2; }
            else if (word.startsWith("th", i)) { out.add(Phoneme.TH); i += 2; }
            else if (word.startsWith("ng", i)) { out.add(Phoneme.NG); i += 2; }
            else if (word.startsWith("ee", i) || word.startsWith("ea", i)) { out.add(Phoneme.IY); i += 2; }
            else if (word.startsWith("oo", i)) { out.add(Phoneme.UW); i += 2; }
            else if (word.startsWith("ou", i) || word.startsWith("ow", i)) { out.add(Phoneme.AW); i += 2; }
            else if (word.startsWith("ai", i) || word.startsWith("ay", i)) { out.add(Phoneme.EY); i += 2; }
            else {
                char c = word.charAt(i);
                Phoneme p = switch (c) {
                    case 'a' -> Phoneme.AE;
                    case 'e' -> Phoneme.EH;
                    case 'i' -> Phoneme.IH;
                    case 'o' -> Phoneme.AO;
                    case 'u' -> Phoneme.AH;
                    case 'b' -> Phoneme.B;
                    case 'c', 'k' -> Phoneme.K;
                    case 'd' -> Phoneme.D;
                    case 'f' -> Phoneme.F;
                    case 'g' -> Phoneme.G;
                    case 'h' -> Phoneme.HH;
                    case 'j' -> Phoneme.JH;
                    case 'l' -> Phoneme.L;
                    case 'm' -> Phoneme.M;
                    case 'n' -> Phoneme.N;
                    case 'p' -> Phoneme.P;
                    case 'q' -> Phoneme.K;
                    case 'r' -> Phoneme.R;
                    case 's' -> Phoneme.S;
                    case 't' -> Phoneme.T;
                    case 'v' -> Phoneme.V;
                    case 'w' -> Phoneme.W;
                    case 'x' -> Phoneme.K;
                    case 'y' -> Phoneme.Y;
                    case 'z' -> Phoneme.Z;
                    default -> null;
                };
                if (c == 'x') out.add(Phoneme.S);
                if (p != null) out.add(p);
                i++;
            }
        }
        return out;
    }
}
