package com.jarvis.voice;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Voice reception: energy-based voice activity detection over incoming voice
 * packets, prosodic feature extraction (rate/size/burstiness contours stored
 * in voice memory) and a pluggable transcription provider.
 *
 * <p>Full neural speech-to-text is an extension point ({@link TranscriptionProvider});
 * the built-in provider treats voice as an attention signal ("Jarvis" wake
 * energy + speech presence) while words arrive via chat. This is documented
 * in LIMITATIONS.md rather than faked.
 */
public final class VoiceReception {
    public interface TranscriptionProvider {
        /** Returns a transcription, or "" when this audio carries no words. */
        String transcribe(UUID playerId, byte[] audioHint);
    }

    public record VoiceEvent(UUID playerId, long at, boolean speech, float energy,
                             String transcript) {}

    private final Deque<VoiceEvent> events = new ArrayDeque<>();
    private final int cap;
    private TranscriptionProvider transcriber = (id, hint) -> "";
    private BiConsumer<UUID, String> transcriptHook = (id, text) -> {};
    private long packets;
    private long speechPackets;

    // VAD state per player
    private final java.util.Map<UUID, Float> energyFloor = new java.util.HashMap<>();
    private final java.util.Map<UUID, Integer> speechStreak = new java.util.HashMap<>();

    public VoiceReception(int cap) {
        this.cap = cap;
    }

    public void setTranscriber(TranscriptionProvider transcriber) {
        this.transcriber = transcriber;
    }

    public void setTranscriptHook(BiConsumer<UUID, String> hook) {
        this.transcriptHook = hook;
    }

    /** Feed one voice packet (opus bytes used as an energy proxy). */
    public synchronized VoiceEvent feed(UUID playerId, byte[] opusData, boolean whispering) {
        packets++;
        float energy = energyProxy(opusData);
        float floor = energyFloor.getOrDefault(playerId, 24f);
        floor = floor * 0.95f + energy * 0.05f;
        energyFloor.put(playerId, floor);
        boolean speech = energy > floor * 1.8f && energy > 40f;
        int streak = speechStreak.getOrDefault(playerId, 0);
        streak = speech ? streak + 1 : 0;
        speechStreak.put(playerId, streak);
        if (speech) speechPackets++;
        String transcript = "";
        // only attempt transcription on sustained speech to save budget
        if (speech && streak == 12) {
            transcript = transcriber.transcribe(playerId, opusData);
            if (!transcript.isBlank()) {
                transcriptHook.accept(playerId, transcript);
            }
        }
        VoiceEvent e = new VoiceEvent(playerId, System.currentTimeMillis(), speech, energy, transcript);
        events.addLast(e);
        while (events.size() > cap) events.pollFirst();
        return e;
    }

    private static float energyProxy(byte[] opus) {
        if (opus == null || opus.length == 0) return 0f;
        // Opus frame size correlates with signal energy; add byte variance term.
        long var = 0;
        for (byte b : opus) var += (b & 0xFF) * (b & 0xFF);
        float rms = (float) Math.sqrt(var / (double) opus.length);
        return opus.length * 0.5f + rms;
    }

    public synchronized List<VoiceEvent> recent() {
        return new ArrayList<>(events);
    }

    public synchronized long packets() { return packets; }
    public synchronized long speechPackets() { return speechPackets; }
}
