package com.jarvis.network;

/**
 * Server-to-client portrait signal: which face Jarvis shows and how long
 * the portrait pulses (speaking / working). Modes: MAIN, SKILL, FAILED.
 */
public record HudSignal(String mode, int pulseMillis) {
    public static final String MAIN = "MAIN";
    public static final String SKILL = "SKILL";
    public static final String FAILED = "FAILED";

    public HudSignal {
        if (mode == null) mode = MAIN;
        pulseMillis = Math.max(0, Math.min(15000, pulseMillis));
    }
}
