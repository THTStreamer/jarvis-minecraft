package com.jarvis.client;

import com.jarvis.network.HudSignal;

/**
 * Client portrait state machine. MAIN is the resting face; SKILL shows while
 * Jarvis attempts skill creation (with a minimum display time so the swap is
 * always visible); FAILED shows on action failure or config-restricted
 * requests and sticks briefly. Pulse timing is independent of the face.
 */
public final class JarvisHudState {
    /** SKILL stays visible at least this long even if MAIN arrives early. */
    static final long SKILL_MIN_MS = 1500L;
    /** FAILED sticks this long before MAIN may replace it. */
    static final long FAILED_STICKY_MS = 4000L;

    private String mode = HudSignal.MAIN;
    private long modeUntil = 0L;
    private long pulseUntil = 0L;

    public synchronized void signal(HudSignal signal, long now) {
        pulseUntil = Math.max(pulseUntil, now + signal.pulseMillis());
        switch (signal.mode()) {
            case HudSignal.FAILED -> {
                mode = HudSignal.FAILED;
                modeUntil = now + Math.max(FAILED_STICKY_MS, signal.pulseMillis());
            }
            case HudSignal.SKILL -> {
                // a fresh failure keeps the red face; otherwise show the attempt
                if (!HudSignal.FAILED.equals(mode) || now >= modeUntil) {
                    mode = HudSignal.SKILL;
                    modeUntil = now + Math.max(SKILL_MIN_MS, signal.pulseMillis());
                }
            }
            default -> {
                // MAIN only replaces a sticky face once its time is up
                if (now >= modeUntil) {
                    mode = HudSignal.MAIN;
                    modeUntil = 0L;
                }
            }
        }
    }

    public synchronized void pulse(int millis, long now) {
        pulseUntil = Math.max(pulseUntil, now + Math.max(0, millis));
    }

    /** Current face to render; lazily settles expired stickies back to MAIN. */
    public synchronized String current(long now) {
        if (now >= modeUntil && !HudSignal.MAIN.equals(mode)) {
            mode = HudSignal.MAIN;
            modeUntil = 0L;
        }
        return mode;
    }

    public synchronized boolean pulsing(long now) {
        return now < pulseUntil;
    }

    public synchronized String mode() {
        return mode;
    }
}
