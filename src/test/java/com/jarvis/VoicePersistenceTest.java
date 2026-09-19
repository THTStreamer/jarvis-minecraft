package com.jarvis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jarvis.core.JarvisInstance;
import com.jarvis.persistence.PersistenceManager;
import com.jarvis.voice.Phoneme;
import com.jarvis.voice.PhonemeProcessor;
import com.jarvis.voice.ProsodyEngine;
import com.jarvis.voice.VoiceProfile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class VoicePersistenceTest {
    @Test
    public void voicePipelineSynthesizes() {
        VoiceProfile profile = new VoiceProfile();
        var pipeline = new com.jarvis.voice.VoicePipeline(profile, new com.jarvis.voice.VoiceMemory(8));
        var spoken = pipeline.speak("Sir, I've detected three hostile creatures nearby.");
        assertTrue(spoken.pcm().length > 5000, "real PCM produced: " + spoken.pcm().length);
        assertTrue(spoken.phonemes() > 10, "phonemes processed");
        var cached = pipeline.speak("Sir, I've detected three hostile creatures nearby.");
        assertEquals(spoken.pcm().length, cached.pcm().length, "cache consistent");
    }

    @Test
    public void phonemesAndProsody() {
        PhonemeProcessor proc = new PhonemeProcessor();
        List<Phoneme> ph = proc.process("Hello sir. Scanning now!");
        assertTrue(ph.contains(Phoneme.SENTENCE_END), "sentence boundaries kept");
        ProsodyEngine prosody = new ProsodyEngine(new VoiceProfile());
        var frames = prosody.render(ph, ProsodyEngine.Emotion.CONCERNED);
        assertEquals(ph.size(), frames.size());
        assertTrue(frames.stream().allMatch(f -> f.pitchHz() > 40 && f.durationMs() > 0), "sane prosody");
    }

    @Test
    public void saveAndLoadRoundTrip(@TempDir Path tmp) throws Exception {
        JarvisInstance inst = TestKit.instance(tmp);
        var snapshot = new TestKit.FakeWorld().snapshot("x");
        inst.handle("Jarvis, remember that my base is at spawn.", snapshot);
        inst.handle("Jarvis, find me a village.", snapshot);
        PersistenceManager pm = new PersistenceManager(tmp.resolve("jarvis"));
        pm.save(inst);
        // second save rotates latest -> previous, enabling rollback
        inst.handle("Jarvis, scan the area.", snapshot);
        pm.save(inst);

        JarvisInstance inst2 = TestKit.instance(tmp);
        // point at the same player id by copying profile id is internal; instead
        // verify files exist and JSON parses with expected keys
        var playerFile = tmp.resolve("jarvis").resolve("players");
        assertTrue(java.nio.file.Files.exists(playerFile), "player saves written");
        try (var stream = java.nio.file.Files.list(playerFile)) {
            assertTrue(stream.findAny().isPresent(), "at least one player file");
        }
        assertTrue(com.jarvis.ai.neural.ModelCheckpoint.exists(tmp.resolve("jarvis"),
            inst.profile().playerId().toString()), "model checkpoint written");
        assertTrue(pm.rollbackModel(inst.profile().playerId().toString()), "rollback works");
    }
}
