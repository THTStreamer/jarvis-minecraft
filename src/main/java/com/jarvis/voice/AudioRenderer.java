package com.jarvis.voice;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Client-side audio rendering: plays synthesized PCM through the default
 * output device. Server-side playback goes through Simple Voice Chat when
 * present (see svc integration); this renderer is the local fallback and
 * the path used for voice preview in the UI.
 */
public final class AudioRenderer {
    private AudioRenderer() {}

    public static void play(short[] pcm) throws Exception {
        AudioFormat format = new AudioFormat(SpeechSynthesizer.SAMPLE_RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();
            byte[] bytes = new byte[pcm.length * 2];
            for (int i = 0; i < pcm.length; i++) {
                bytes[i * 2] = (byte) (pcm[i] & 0xFF);
                bytes[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xFF);
            }
            line.write(bytes, 0, bytes.length);
            line.drain();
        }
    }

    public static byte[] toBytes(short[] pcm) {
        byte[] bytes = new byte[pcm.length * 2];
        for (int i = 0; i < pcm.length; i++) {
            bytes[i * 2] = (byte) (pcm[i] & 0xFF);
            bytes[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xFF);
        }
        return bytes;
    }
}
