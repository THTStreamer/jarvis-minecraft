package com.jarvis.skills;

import com.jarvis.language.Intent;
import com.jarvis.world.PlayerSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;

/** Everything a skill may read while executing. */
public final class SkillContext {
    private final String playerId;
    private final String playerName;
    private final String input;
    private final Intent intent;
    private final Map<String, Object> entities;
    private final PlayerSnapshot snapshot;
    private final WorldAccess world;
    private final Map<String, Object> data = new LinkedHashMap<>();

    public SkillContext(String playerId, String playerName, String input, Intent intent,
                        Map<String, Object> entities, PlayerSnapshot snapshot, WorldAccess world) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.input = input;
        this.intent = intent;
        this.entities = new LinkedHashMap<>(entities);
        this.snapshot = snapshot;
        this.world = world;
    }

    public String playerId() { return playerId; }
    public String playerName() { return playerName; }
    public String input() { return input; }
    public Intent intent() { return intent; }
    public Map<String, Object> entities() { return entities; }
    public PlayerSnapshot snapshot() { return snapshot; }
    public WorldAccess world() { return world; }

    public void put(String key, Object value) { data.put(key, value); }
    public Object get(String key) { return data.get(key); }
    public Map<String, Object> data() { return data; }
}
