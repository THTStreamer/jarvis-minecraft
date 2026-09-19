package com.jarvis.api;

import com.jarvis.core.JarvisInstance;
import com.jarvis.core.JarvisService;
import com.jarvis.knowledge.KnowledgeEntry;
import com.jarvis.knowledge.KnowledgeGraph;
import com.jarvis.skills.Skill;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API for other mods. All methods are safe to call from any thread;
 * lookups by player return empty when that player's Jarvis is unavailable.
 */
public final class JarvisAPI {
    private JarvisAPI() {}

    public static Optional<JarvisInstance> get(UUID playerId) {
        JarvisService svc = JarvisService.get();
        if (svc == null) return Optional.empty();
        return svc.instance(playerId);
    }

    public static boolean registerKnowledge(UUID playerId, String subject, String relation,
                                            String object, String source, float confidence) {
        return get(playerId).map(j -> {
            j.knowledge().add(subject, relation, object, source, confidence);
            return true;
        }).orElse(false);
    }

    public static boolean registerSkill(UUID playerId, Skill skill) {
        return get(playerId).map(j -> {
            j.skills().register(skill);
            return true;
        }).orElse(false);
    }

    public static boolean observe(UUID playerId, String eventType, Map<String, String> data) {
        return get(playerId).map(j -> {
            j.observe(eventType, data);
            return true;
        }).orElse(false);
    }

    public static List<KnowledgeEntry> query(UUID playerId, String topic) {
        return get(playerId).map(j -> j.knowledge().queryObject(topic)).orElseGet(List::of);
    }

    public static KnowledgeGraph getKnowledge(UUID playerId) {
        return get(playerId).map(j -> j.knowledge()).orElse(null);
    }

    public static boolean sendMessage(UUID playerId, String text) {
        return get(playerId).map(j -> {
            j.speak(text);
            return true;
        }).orElse(false);
    }

    public static boolean registerModIntegration(IModIntegration integration) {
        JarvisService svc = JarvisService.get();
        if (svc == null) return false;
        svc.registerIntegration(integration);
        return true;
    }

    public static String createSkill(UUID playerId, String description) {
        return get(playerId).map(j -> j.requestSkill(description)).orElse("Jarvis is not available.");
    }

    /**
     * Plug in a real speech-to-text provider for a player (e.g. an-approved
     * server-side transcription bridge). Once set, sustained microphone speech
     * flows through the normal dialogue pipeline. The default provider hears
     * speech presence only and yields no words.
     */
    public static boolean setTranscriber(UUID playerId,
                                         com.jarvis.voice.VoiceReception.TranscriptionProvider provider) {
        return get(playerId).map(j -> {
            j.reception().setTranscriber(provider);
            return true;
        }).orElse(false);
    }
}
