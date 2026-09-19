package com.jarvis.voice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Remembers recent utterances + prosodic statistics for voice consistency. */
public final class VoiceMemory {
    public record Utterance(String text, ProsodyEngine.Emotion emotion, long at, int samples) {}

    private final Deque<Utterance> recent = new ArrayDeque<>();
    private final int cap;

    public VoiceMemory(int cap) {
        this.cap = cap;
    }

    public synchronized void remember(String text, ProsodyEngine.Emotion emotion, int samples) {
        recent.addLast(new Utterance(text, emotion, System.currentTimeMillis(), samples));
        while (recent.size() > cap) recent.pollFirst();
    }

    public synchronized List<Utterance> recent() {
        return new ArrayList<>(recent);
    }

    public synchronized int count() {
        return recent.size();
    }
}
