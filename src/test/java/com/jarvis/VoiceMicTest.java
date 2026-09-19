package com.jarvis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jarvis.voice.VoicePipeline;
import com.jarvis.voice.VoiceMemory;
import com.jarvis.voice.VoiceProfile;
import com.jarvis.voice.VoiceReception;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class VoiceMicTest {
    @Test
    public void synthIsPeakNormalized() {
        VoiceProfile profile = new VoiceProfile();
        var pipeline = new VoicePipeline(profile, new VoiceMemory(8));
        var spoken = pipeline.speak("Sir, I've detected three hostile creatures nearby.");
        int peak = 0;
        for (short s : spoken.pcm()) peak = Math.max(peak, Math.abs((int) s));
        assertTrue(peak >= 25000 && peak <= 32767,
            "normalized and audible without clipping, peak=" + peak);
    }

    /** Rising-energy packet ramp simulates sustained speech against the adaptive floor. */
    private static byte[] packet(int loudness, int len) {
        byte[] b = new byte[len];
        int v = Math.max(0, Math.min(250, loudness));
        for (int i = 0; i < len; i++) b[i] = (byte) (i % 3 == 0 ? v : v / 2);
        return b;
    }

    @Test
    public void sustainedSpeechNoticedOncePerCooldown() {
        VoiceReception reception = new VoiceReception(256);
        AtomicInteger notices = new AtomicInteger();
        AtomicInteger transcripts = new AtomicInteger();
        reception.setSpeechNoticedHook((id, streak) -> notices.incrementAndGet());
        reception.setTranscriptHook((id, text) -> transcripts.incrementAndGet());
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 60; i++) {
            reception.feed(player, packet(120 + i * 3, 100), false);
        }
        assertEquals(1, notices.get(), "one honest nudge per cooldown window");
        assertEquals(0, transcripts.get(), "default provider yields no words");
        // a second burst inside the cooldown stays silent
        for (int i = 0; i < 40; i++) {
            reception.feed(player, packet(250, 100), false);
        }
        assertEquals(1, notices.get(), "cooldown suppresses repeats");
        assertTrue(reception.speechPackets() > 20, "speech was actually detected");
    }

    @Test
    public void injectedTranscriberFlowsThrough() {
        VoiceReception reception = new VoiceReception(256);
        AtomicReference<String> heard = new AtomicReference<>("");
        reception.setTranscriber((id, hint) -> "hello jarvis");
        reception.setTranscriptHook((id, text) -> heard.set(text));
        UUID player = UUID.randomUUID();
        for (int i = 0; i < 40; i++) {
            reception.feed(player, packet(150 + i * 3, 100), false);
        }
        assertEquals("hello jarvis", heard.get(), "provider transcription reaches the pipeline");
    }
}
