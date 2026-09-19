package com.jarvis.player;

import com.jarvis.language.Personality;
import java.util.UUID;

/**
 * Persistent per-player Jarvis identity: who the player is to Jarvis, how
 * Jarvis speaks, relationship state and trust. Intelligence state (memory,
 * knowledge, neural weights) lives in {@code JarvisInstance}, keyed by the
 * same player UUID.
 */
public final class JarvisProfile {
    private final UUID playerId;
    private String playerName;
    private final Personality personality = new Personality();
    private float speechSpeed = 1.0f;
    private float speechPitch = 112f;
    private boolean muted;
    private boolean voiceEnabled = true;
    private int trustLevel;
    private String relationship = "new acquaintance";
    private long firstSeen;
    private long lastSeen;
    private long exchanges;

    public JarvisProfile(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        long now = System.currentTimeMillis();
        this.firstSeen = now;
        this.lastSeen = now;
    }

    public UUID playerId() { return playerId; }
    public String playerName() { return playerName; }
    public void setPlayerName(String name) { this.playerName = name; }
    public Personality personality() { return personality; }
    public float speechSpeed() { return speechSpeed; }
    public void setSpeechSpeed(float v) { this.speechSpeed = v; }
    public float speechPitch() { return speechPitch; }
    public void setSpeechPitch(float v) { this.speechPitch = v; }
    public boolean muted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }
    public boolean voiceEnabled() { return voiceEnabled; }
    public void setVoiceEnabled(boolean v) { this.voiceEnabled = v; }
    public int trustLevel() { return trustLevel; }
    public String relationship() { return relationship; }
    public long exchanges() { return exchanges; }

    public void markExchange() {
        exchanges++;
        lastSeen = System.currentTimeMillis();
        if (exchanges > 200) {
            trustLevel = 3;
            relationship = "trusted partner";
        } else if (exchanges > 50) {
            trustLevel = 2;
            relationship = "valued colleague";
        } else if (exchanges > 10) {
            trustLevel = 1;
            relationship = "familiar friend";
        }
    }
}
