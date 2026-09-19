package com.jarvis.api;

import java.util.Map;

/**
 * Contract for other mods to teach Jarvis without touching Jarvis sources.
 * Register via {@link JarvisAPI#registerModIntegration(IModIntegration)}.
 */
public interface IModIntegration {
    /** The target mod id, e.g. "create". */
    String modId();

    /** Called once when the target mod is detected as installed. */
    default void onModDetected(Map<String, Object> context) {}

    /** Called for player observations relevant to this mod. */
    default void onObservation(String eventType, Map<String, String> data, Map<String, Object> context) {}

    /** Optional: answer a question using this integration; null when unknown. */
    default String answer(String question, Map<String, Object> context) {
        return null;
    }
}
