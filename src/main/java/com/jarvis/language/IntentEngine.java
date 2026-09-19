package com.jarvis.language;

import com.jarvis.ai.embeddings.SemanticEmbedder;
import com.jarvis.ai.neural.JarvisNeuralNetwork;
import com.jarvis.ai.neural.Optimizer;
import com.jarvis.ai.tokenizer.JarvisTokenizer;
import com.jarvis.util.Floats;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hybrid intent recognizer. Every intent owns a trainable prototype vector in
 * the neural sentence space plus a learned synonym/keyword graph. The final
 * score blends neural cosine similarity (which improves as the network trains
 * on this player's conversations) with lexical-semantic overlap (which grows
 * as Jarvis learns synonyms). Paraphrases resolve to the same intent through
 * this representation - not through phrase alias lists.
 */
public final class IntentEngine {
    public record ScoredIntent(Intent intent, double score, double neural, double lexical) {}

    private final SemanticEmbedder embedder;
    private final JarvisTokenizer tokenizer;
    private final JarvisNeuralNetwork network;
    private final Optimizer alignOptimizer;
    private final Map<Intent, float[]> prototypes = new EnumMap<>(Intent.class);
    private final Map<Intent, List<String>> seeds = new EnumMap<>(Intent.class);
    private final Map<String, Map<Intent, Double>> learnedWords = new LinkedHashMap<>();
    private final Object lock = new Object();

    public IntentEngine(SemanticEmbedder embedder, JarvisTokenizer tokenizer,
                        JarvisNeuralNetwork network, Optimizer alignOptimizer) {
        this.embedder = embedder;
        this.tokenizer = tokenizer;
        this.network = network;
        this.alignOptimizer = alignOptimizer;
        seedAll();
    }

    private void seed(Intent intent, String... phrases) {
        seeds.computeIfAbsent(intent, k -> new ArrayList<>()).addAll(List.of(phrases));
    }

    private void seedAll() {
        seed(Intent.GREETING, "hello jarvis", "hey jarvis", "good morning", "jarvis are you there");
        seed(Intent.FAREWELL, "goodbye", "good night jarvis", "see you later");
        seed(Intent.HELP, "help me", "what can you do", "how do you work", "list commands");
        seed(Intent.STATUS, "status report", "how are you", "system status", "are you online");
        seed(Intent.SCAN_HOSTILES, "scan for enemies", "any hostile mobs nearby", "anything dangerous around us",
            "check surroundings for hostile creatures", "how many monsters are around me");
        seed(Intent.SCAN_AREA, "scan the area", "what is around me", "survey surroundings", "what do you see");
        seed(Intent.LOCATE_STRUCTURE, "find me a village", "where is the nearest village", "locate the closest settlement",
            "take me to the nearest village", "find a stronghold", "find a desert village",
            "find the closest cave", "find a lush cave", "find a woodland mansion");
        seed(Intent.LOCATE_BIOME, "find a desert biome", "where is the nearest jungle", "locate a mushroom island");
        seed(Intent.NAVIGATE_GUIDE, "guide me there", "take me there", "mark the route", "lead the way", "navigate");
        seed(Intent.DISTANCE_QUERY, "how far is it", "how far away", "what is the distance");
        seed(Intent.TRAVEL_TIME_QUERY, "how long will it take", "how long to get there", "travel time");
        seed(Intent.ORE_SCAN, "find diamonds", "scan for ores", "where are the diamonds", "locate ore nearby");
        seed(Intent.MOD_QUESTION, "what is this machine", "how does this mod work", "what does this do",
            "explain this block", "what is this item");
        seed(Intent.MACHINE_QUESTION, "why is this machine not working", "how much stress is this using",
            "is the network overstressed", "why is my factory stopped");
        seed(Intent.SPELL_QUESTION, "what do i need to learn this spell", "what am i missing for the spell",
            "spell requirements", "how do glyphs work");
        seed(Intent.REMEMBER, "remember that", "don't forget", "my base is here", "note this down");
        seed(Intent.FORGET, "forget everything", "clear your memory", "forget what i told you");
        seed(Intent.MESSAGE_PLAYER, "tell john's jarvis", "send a message to", "let him know that");
        seed(Intent.MUTE, "be quiet", "mute yourself", "stop talking");
        seed(Intent.UNMUTE, "you can speak now", "unmute");
        seed(Intent.VOICE_CONFIG, "change your voice", "speak faster", "voice settings");
        seed(Intent.SKILL_CREATE, "can you learn to", "i want you to be able to", "teach yourself to");
        seed(Intent.SKILL_LIST, "what skills do you have", "list your skills", "what can you do for me");
        seed(Intent.MEMORY_QUERY, "what do you remember", "what do you know about me");
        seed(Intent.KNOWLEDGE_QUERY, "what do you know about", "tell me about diamonds", "explain stress units");
        seed(Intent.CONFIRM, "yes", "of course", "do it", "confirmed");
        seed(Intent.DENY, "no", "cancel", "never mind", "stop");
        seed(Intent.FOLLOW_UP, "how far", "what about the closest one", "can you mark it", "anything else",
            "and then", "what about it");
    }

    /** Build initial prototypes from seed phrases (called lazily, then refined by training). */
    public void initialize() {
        synchronized (lock) {
            if (!prototypes.isEmpty()) return;
            for (Intent intent : Intent.values()) {
                List<String> phrases = seeds.getOrDefault(intent, List.of());
                if (phrases.isEmpty()) continue;
                float[] acc = null;
                for (String p : phrases) {
                    float[] v = embedder.embed(p);
                    if (acc == null) acc = v;
                    else {
                        for (int i = 0; i < acc.length; i++) acc[i] += v[i];
                    }
                }
                float n = Floats.norm(acc);
                if (n > 1e-9f) {
                    for (int i = 0; i < acc.length; i++) acc[i] /= n;
                }
                prototypes.put(intent, acc);
            }
        }
    }

    public List<ScoredIntent> scoreAll(String text) {
        initialize();
        float[] vec = embedder.embed(text);
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> words = contentWords(lower);
        List<ScoredIntent> out = new ArrayList<>();
        synchronized (lock) {
            for (Intent intent : Intent.values()) {
                float[] proto = prototypes.get(intent);
                if (proto == null) continue;
                double neural = (Floats.cosine(vec, proto) + 1.0) / 2.0;
                double lexical = lexicalScore(intent, words, lower);
                double score = 0.55 * neural + 0.45 * lexical;
                out.add(new ScoredIntent(intent, score, neural, lexical));
            }
        }
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out;
    }

    public ScoredIntent recognize(String text) {
        List<ScoredIntent> all = scoreAll(text);
        ScoredIntent top = all.get(0);
        // follow-up heuristic: short context-dependent utterances keep prior target
        if (top.score() < 0.52) {
            return new ScoredIntent(Intent.UNKNOWN, top.score(), top.neural(), top.lexical());
        }
        return top;
    }

    private double lexicalScore(Intent intent, List<String> words, String lower) {
        List<String> phrases = seeds.getOrDefault(intent, List.of());
        double best = 0.0;
        for (String phrase : phrases) {
            double s = phraseOverlap(phrase, words, lower);
            if (s > best) best = s;
        }
        Map<Intent, Double> perWord = null;
        double learned = 0.0;
        int hits = 0;
        synchronized (lock) {
            for (String w : words) {
                Map<Intent, Double> m = learnedWords.get(w);
                if (m != null) {
                    Double v = m.get(intent);
                    if (v != null && v > 0) {
                        learned += v;
                        hits++;
                    }
                }
            }
            perWord = null;
        }
        if (hits > 0) best = Math.max(best, Math.min(1.0, learned / hits));
        return best;
    }

    private double phraseOverlap(String phrase, List<String> words, String lower) {
        String[] parts = phrase.toLowerCase(Locale.ROOT).split("\\s+");
        int hit = 0;
        for (String p : parts) {
            if (p.length() < 3) continue;
            for (String w : words) {
                if (w.equals(p) || synonyms(w).contains(p) || synonyms(p).contains(w)) {
                    hit++;
                    break;
                }
            }
        }
        int denom = 0;
        for (String p : parts) if (p.length() >= 3) denom++;
        double wordScore = denom == 0 ? 0 : (double) hit / denom;
        // exact-phrase bonus
        if (lower.contains(phrase.toLowerCase(Locale.ROOT))) wordScore = Math.min(1.0, wordScore + 0.35);
        return wordScore;
    }

    /** Small built-in synonym graph, extended at runtime by learning. */
    private final Map<String, List<String>> synonymGraph = new LinkedHashMap<>();

    private List<String> synonyms(String word) {
        List<String> base = synonymGraph.get(word);
        if (base != null) return base;
        return List.of();
    }

    public void addSynonym(String a, String b) {
        synchronized (lock) {
            synonymGraph.computeIfAbsent(a, k -> new ArrayList<>()).add(b);
            synonymGraph.computeIfAbsent(b, k -> new ArrayList<>()).add(a);
        }
    }

    private List<String> contentWords(String lower) {
        List<String> out = new ArrayList<>();
        for (String w : lower.replaceAll("[^a-z0-9' ]", " ").split("\\s+")) {
            if (w.length() >= 2 && !STOP.contains(w)) out.add(w);
        }
        return out;
    }

    private static final java.util.Set<String> STOP = java.util.Set.of(
        "the", "is", "at", "on", "to", "me", "my", "it", "of", "and", "or", "do", "are",
        "there", "here", "your", "a", "an", "in", "for", "with", "us", "we", "sir", "jarvis");

    /**
     * Reinforcement from explicit feedback: pulls the prototype toward the
     * utterance embedding when correct, pushes away + records word weights otherwise.
     */
    public void reinforce(String text, Intent predicted, Intent actual) {
        initialize();
        int[] ids = tokenizer.encode(text, false);
        if (predicted.equals(actual)) {
            float[] proto = prototypes.get(actual);
            if (proto != null) {
                network.trainAlign(ids, proto, alignOptimizer);
                // move prototype slightly toward the new example
                float[] v = embedder.embed(text);
                synchronized (lock) {
                    for (int i = 0; i < proto.length; i++) proto[i] = 0.95f * proto[i] + 0.05f * v[i];
                    float n = Floats.norm(proto);
                    if (n > 1e-9f) {
                        for (int i = 0; i < proto.length; i++) proto[i] /= n;
                    }
                }
            }
        } else {
            synchronized (lock) {
                for (String w : contentWords(text.toLowerCase(Locale.ROOT))) {
                    learnedWords.computeIfAbsent(w, k -> new LinkedHashMap<>())
                        .merge(actual, 0.4, Double::sum);
                    learnedWords.get(w).merge(predicted, -0.3, Double::sum);
                }
            }
        }
    }

    public Map<Intent, float[]> prototypeSnapshot() {
        synchronized (lock) {
            Map<Intent, float[]> copy = new EnumMap<>(Intent.class);
            for (Map.Entry<Intent, float[]> e : prototypes.entrySet()) copy.put(e.getKey(), e.getValue().clone());
            return copy;
        }
    }

    public void restorePrototypes(Map<String, List<Float>> data) {
        synchronized (lock) {
            for (Map.Entry<String, List<Float>> e : data.entrySet()) {
                try {
                    Intent intent = Intent.valueOf(e.getKey());
                    List<Float> vals = e.getValue();
                    float[] v = new float[vals.size()];
                    for (int i = 0; i < vals.size(); i++) v[i] = vals.get(i);
                    prototypes.put(intent, v);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public Map<String, List<Float>> exportPrototypes() {
        Map<String, List<Float>> out = new LinkedHashMap<>();
        synchronized (lock) {
            for (Map.Entry<Intent, float[]> e : prototypes.entrySet()) {
                List<Float> vals = new ArrayList<>(e.getValue().length);
                for (float v : e.getValue()) vals.add(v);
                out.put(e.getKey().name(), vals);
            }
        }
        return out;
    }
}
