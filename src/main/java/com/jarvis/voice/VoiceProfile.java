package com.jarvis.voice;

/** Tunable voice identity: pitch, rate, volume, timbre seed. */
public final class VoiceProfile {
    private float basePitchHz = 112f;
    private float speechRate = 1.0f; // multiplier on durations; <1 faster
    private float volume = 0.85f;
    private long timbreSeed = 7L;

    public float basePitchHz() { return basePitchHz; }
    public void setBasePitchHz(float hz) { this.basePitchHz = Math.max(60f, Math.min(300f, hz)); }

    /** Rate multiplier: 0.7 = fast, 1.3 = slow. */
    public float speechRate() { return speechRate; }
    public void setSpeechRate(float rate) { this.speechRate = Math.max(0.5f, Math.min(2.0f, rate)); }

    public float volume() { return volume; }
    public void setVolume(float volume) { this.volume = Math.max(0f, Math.min(1f, volume)); }

    public long timbreSeed() { return timbreSeed; }
    public void setTimbreSeed(long seed) { this.timbreSeed = seed; }
}
