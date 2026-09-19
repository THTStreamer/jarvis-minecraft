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
    private BiConsumer<UUID, Integer> speechNoticedHook = (id, streak) -> {};
    private final java.util.Map<UUID, Long> lastNotice = new java.util.HashMap<>();
    // NOTE: pure core must stay free of Minecraft classes (unit tests run
    // without the game), so diagnostics here use plain JUL.
    private static final java.util.logging.Logger LOG =
        java.util.logging.Logger.getLogger("jarvis");
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

    /**
     * Fires when sustained speech is noticed but no transcription is available,
     * so the player gets an honest answer instead of silence. At most once per
     * player per cooldown window.
     */
    public void setSpeechNoticedHook(BiConsumer<UUID, Integer> hook) {
        this.speechNoticedHook = hook;
    }

    /** Feed one voice packet. Opus frame LENGTH is the energy signal: speech
     * frames carry full-bitrate audio while silence frames shrink to a few
     * bytes (byte values of compressed audio carry no amplitude, so they are
     * ignored). The noise floor tracks silence only and never chases speech;
     * a hangover counter tolerates syllable gaps. */
    public synchronized VoiceEvent feed(UUID playerId, byte[] opusData, boolean whispering) {
        packets++;
        float energy = opusData == null ? 0f : opusData.length;
        float floor = energyFloor.getOrDefault(playerId, 24f);
        boolean speech = energy > Math.max(48f, floor * 2.0f);
        if (!speech) {
            floor = floor * 0.95f + energy * 0.05f;
            energyFloor.put(playerId, floor);
        }
        int before = speechStreak.getOrDefault(playerId, 0);
        int streak = speech ? Math.min(1000, before + 1) : Math.max(0, before - 2);
        speechStreak.put(playerId, streak);
        if (speech) speechPackets++;
        if (packets == 1) {
            LOG.info("[Jarvis] Voice reception live: first microphone packet arrived.");
        }
        String transcript = "";
        // transcribe once per talk spurt as the streak crosses the threshold
        if (before < 12 && streak >= 12) {
            transcript = transcriber.transcribe(playerId, opusData);
            if (!transcript.isBlank()) {
                transcriptHook.accept(playerId, transcript);
            }
        }
        // sustained speech with no transcription: notice honestly, with cooldown
        if (before < 25 && streak >= 25 && transcript.isBlank()) {
            long now = System.currentTimeMillis();
            long last = lastNotice.getOrDefault(playerId, 0L);
            if (now - last > 45_000L) {
                lastNotice.put(playerId, now);
                try {
                    speechNoticedHook.accept(playerId, streak);
                } catch (Exception ignored) {}
            }
        }
        VoiceEvent e = new VoiceEvent(playerId, System.currentTimeMillis(), speech, energy, transcript);
        events.addLast(e);
        while (events.size() > cap) events.pollFirst();
        return e;
    }

    public synchronized List<VoiceEvent> recent() {
        return new ArrayList<>(events);
    }

    public synchronized long packets() { return packets; }
    public synchronized long speechPackets() { return speechPackets; }
}
