package com.jarvis.network;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Jarvis-to-Jarvis relay. Instances never read each other's private memory;
 * only explicitly transmitted text crosses the boundary, subject to the
 * sharing configuration. Offline recipients get queued delivery on login.
 */
public final class JarvisNetwork {
    private final Map<String, Deque<JarvisMessage>> offline = new ConcurrentHashMap<>();
    private Function<String, UUID> resolveName = name -> null;
    private Function<UUID, Boolean> onlineCheck = id -> false;
    private BiConsumer<UUID, String> deliver = (id, text) -> {};
    private volatile boolean enabled = true;

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }

    public void setResolver(Function<String, UUID> resolveName,
                            Function<UUID, Boolean> onlineCheck,
                            BiConsumer<UUID, String> deliver) {
        this.resolveName = resolveName;
        this.onlineCheck = onlineCheck;
        this.deliver = deliver;
    }

    public record Receipt(boolean ok, String note) {}

    public Receipt send(UUID fromPlayer, String fromName, String toPlayerName, String text, int priority) {
        if (!enabled) return new Receipt(false, "Jarvis-to-Jarvis communication is disabled.");
        UUID target = resolveName.apply(toPlayerName);
        if (target == null) {
            return new Receipt(false, "I couldn't find a player called " + toPlayerName + ".");
        }
        JarvisMessage msg = new JarvisMessage(UUID.randomUUID().toString(), fromPlayer, fromName,
            toPlayerName, text, System.currentTimeMillis(), priority, false);
        if (Boolean.TRUE.equals(onlineCheck.apply(target))) {
            deliver.accept(target, fromName + "'s Jarvis asked me to let you know: " + text);
            return new Receipt(true, "Message relayed to " + toPlayerName + ".");
        }
        offline.computeIfAbsent(target.toString(), k -> new ConcurrentLinkedDeque<>()).add(msg);
        return new Receipt(true, toPlayerName + " is offline. I'll deliver it when they return.");
    }

    /** Drain queued messages on login; returns delivered count. */
    public int drain(UUID playerId) {
        Deque<JarvisMessage> q = offline.remove(playerId.toString());
        if (q == null || q.isEmpty()) return 0;
        List<JarvisMessage> sorted = new ArrayList<>(q);
        sorted.sort((a, b) -> {
            int c = Integer.compare(b.priority(), a.priority());
            return c != 0 ? c : Long.compare(a.sentAt(), b.sentAt());
        });
        for (JarvisMessage m : sorted) {
            deliver.accept(playerId, m.fromName() + "'s Jarvis asked me to let you know: " + m.text());
        }
        return sorted.size();
    }

    public int pendingCount() {
        int n = 0;
        for (Deque<JarvisMessage> q : offline.values()) n += q.size();
        return n;
    }

    public Map<String, List<Map<String, Object>>> export() {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Deque<JarvisMessage>> e : offline.entrySet()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (JarvisMessage m : e.getValue()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", m.id());
                map.put("from", m.fromPlayer().toString());
                map.put("fromName", m.fromName());
                map.put("to", m.toPlayerName());
                map.put("text", m.text());
                map.put("sentAt", m.sentAt());
                map.put("priority", m.priority());
                list.add(map);
            }
            out.put(e.getKey(), list);
        }
        return out;
    }
}
