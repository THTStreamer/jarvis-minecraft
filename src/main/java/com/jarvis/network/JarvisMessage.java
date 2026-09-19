package com.jarvis.network;

import java.util.UUID;

/** One Jarvis-to-Jarvis message. */
public record JarvisMessage(
    String id,
    UUID fromPlayer,
    String fromName,
    String toPlayerName,
    String text,
    long sentAt,
    int priority,
    boolean delivered
) {
    public JarvisMessage withDelivered(boolean d) {
        return new JarvisMessage(id, fromPlayer, fromName, toPlayerName, text, sentAt, priority, d);
    }
}
