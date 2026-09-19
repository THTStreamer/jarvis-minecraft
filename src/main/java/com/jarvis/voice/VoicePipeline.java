package com.jarvis.voice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full voice pipeline: text -> phonemes -> prosody -> PCM. Caches recent
 * utterances; exposes a pluggable transmit hook (SVC channel or local play).
 */
public final class VoicePipeline {
    public record Spoken(short[] pcm, int phonemes, long nanos) {}

    private final PhonemeProcessor phonemes = new PhonemeProcessor();
    private final VoiceProfile profile;
    private final ProsodyEngine prosody;
    private final SpeechSynthesizer synth;
    private final VoiceMemory memory;
    private final Map<String, short[]> cache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, short[]> eldest) {
            return size() > 64;
        }
    };

    public VoicePipeline(VoiceProfile profile, VoiceMemory memory) {
        this.profile = profile;
        this.prosody = new ProsodyEngine(profile);
        this.synth = new SpeechSynthesizer(profile);
        this.memory = memory;
    }

    public VoiceProfile profile() { return profile; }

    public synchronized Spoken speak(String text, ProsodyEngine.Emotion emotion) {
        long start = System.nanoTime();
        short[] cached;
        synchronized (cache) {
            cached = cache.get(text);
        }
        if (cached != null) {
            memory.remember(text, emotion, cached.length);
            return new Spoken(cached, 0, System.nanoTime() - start);
        }
        List<Phoneme> ph = phonemes.process(text);
        List<ProsodyEngine.Frame> frames = prosody.render(ph, emotion);
        short[] pcm = synth.synthesize(frames);
        synchronized (cache) {
            cache.put(text, pcm);
        }
        memory.remember(text, emotion, pcm.length);
        return new Spoken(pcm, ph.size(), System.nanoTime() - start);
    }

    public Spoken speak(String text) {
        return speak(text, ProsodyEngine.Emotion.NEUTRAL);
    }

    public synchronized void clearCache() {
        synchronized (cache) {
            cache.clear();
        }
    }
}
