package com.jarvis.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.jarvis.ai.neural.ModelCheckpoint;
import com.jarvis.core.JarvisInstance;
import com.jarvis.language.Personality;
import com.jarvis.memory.MemoryType;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Versioned JSON persistence (players/&lt;uuid&gt;.json) plus binary neural
 * checkpoints (models/&lt;uuid&gt;/). Version migrations never corrupt data:
 * unknown fields are ignored and layout mismatches keep the old checkpoint
 * while training continues from fresh weights.
 */
public final class PersistenceManager {
    public static final int SAVE_VERSION = 1;

    private final Path root;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public PersistenceManager(Path root) {
        this.root = root;
    }

    public Path root() { return root; }

    private Path playerFile(String uuid) {
        return root.resolve("players").resolve(uuid + ".json");
    }

    public void save(JarvisInstance inst) throws IOException {
        String uuid = inst.profile().playerId().toString();
        Files.createDirectories(root.resolve("players"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", SAVE_VERSION);
        data.put("playerId", uuid);
        data.put("playerName", inst.profile().playerName());

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("title", inst.profile().personality().title().name());
        profile.put("preferredName", inst.profile().personality().playerPreferredName());
        profile.put("verbosity", inst.profile().personality().verbosity());
        profile.put("wit", inst.profile().personality().wit());
        profile.put("formality", inst.profile().personality().formality());
        profile.put("speechSpeed", inst.voice().profile().speechRate());
        profile.put("speechPitch", inst.voice().profile().basePitchHz());
        profile.put("muted", inst.profile().muted());
        profile.put("voiceEnabled", inst.profile().voiceEnabled());
        profile.put("exchanges", inst.profile().exchanges());
        data.put("profile", profile);

        data.put("vocabulary", inst.tokenizer().vocabulary().snapshot());
        data.put("prototypes", inst.intents().exportPrototypes());
        data.put("memories", inst.memory().export());
        data.put("knowledge", inst.knowledge().export());
        data.put("skillStats", inst.skills().exportStats());
        data.put("rl", inst.rl().snapshot());
        data.put("trainSteps", inst.training().steps());
        data.put("trainLoss", inst.training().lastLoss());

        String json = gson.toJson(data);
        Path tmp = playerFile(uuid + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, playerFile(uuid), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, playerFile(uuid), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        // neural weights go to the binary checkpoint (rollback-capable)
        try {
            ModelCheckpoint.save(root, uuid, inst.network(), inst.optimizer(),
                inst.training().lastLoss(), String.valueOf(inst.tokenizer().vocabulary().size()));
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    public void load(JarvisInstance inst) throws IOException {
        String uuid = inst.profile().playerId().toString();
        Path file = playerFile(uuid);
        if (!Files.exists(file)) return;
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Type type = new TypeToken<LinkedHashMap<String, Object>>() {}.getType();
        Map<String, Object> data = gson.fromJson(json, type);
        if (data == null) return;
        int version = ((Number) data.getOrDefault("version", 0)).intValue();
        if (version > SAVE_VERSION) {
            throw new IOException("Save version " + version + " is newer than supported " + SAVE_VERSION);
        }

        Object profile = data.get("profile");
        if (profile instanceof Map<?, ?> raw) {
            Map<String, Object> pm = new LinkedHashMap<>();
            raw.forEach((k, v) -> pm.put(String.valueOf(k), v));
            try {
                inst.profile().personality().setTitle(
                    Personality.AddressTitle.valueOf(String.valueOf(pm.getOrDefault("title", "SIR"))));
            } catch (IllegalArgumentException ignored) {}
            inst.profile().personality().setPlayerPreferredName(
                String.valueOf(pm.getOrDefault("preferredName", "")));
            inst.profile().personality().setVerbosity(num(pm, "verbosity", 0.45).doubleValue());
            inst.profile().personality().setWit(num(pm, "wit", 0.35).doubleValue());
            inst.profile().personality().setFormality(num(pm, "formality", 0.8).doubleValue());
            inst.voice().profile().setSpeechRate(num(pm, "speechSpeed", 1.0).floatValue());
            inst.voice().profile().setBasePitchHz(num(pm, "speechPitch", 112).floatValue());
            inst.profile().setMuted(Boolean.parseBoolean(String.valueOf(pm.getOrDefault("muted", "false"))));
        }
        Object vocab = data.get("vocab");
        if (vocab instanceof List<?> list) {
            List<String> tokens = new ArrayList<>();
            for (Object o : list) tokens.add(String.valueOf(o));
            inst.tokenizer().vocabulary().restore(tokens);
        }
        Object prototypes = data.get("prototypes");
        if (prototypes instanceof Map<?, ?> pmap) {
            Map<String, List<Float>> conv = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : pmap.entrySet()) {
                List<Float> vals = new ArrayList<>();
                if (e.getValue() instanceof List<?> l) {
                    for (Object o : l) {
                        try {
                            vals.add(Float.parseFloat(String.valueOf(o)));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                conv.put(String.valueOf(e.getKey()), vals);
            }
            inst.intents().restorePrototypes(conv);
        }
        Object memories = data.get("memories");
        if (memories instanceof List<?> list) {
            List<Map<String, Object>> mems = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) c.put(String.valueOf(e.getKey()), e.getValue());
                    mems.add(c);
                }
            }
            inst.memory().restore(mems);
        }
        Object knowledge = data.get("knowledge");
        if (knowledge instanceof List<?> list) {
            List<Map<String, Object>> facts = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) c.put(String.valueOf(e.getKey()), e.getValue());
                    facts.add(c);
                }
            }
            inst.knowledge().restore(facts);
        }
        Object stats = data.get("skillStats");
        if (stats instanceof Map<?, ?> smap) {
            Map<String, Map<String, Object>> conv = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : smap.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> inner) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> ie : inner.entrySet()) {
                        c.put(String.valueOf(ie.getKey()), ie.getValue());
                    }
                    conv.put(String.valueOf(e.getKey()), c);
                }
            }
            inst.skills().restoreStats(conv);
        }
        Object rl = data.get("rl");
        if (rl instanceof Map<?, ?> rmap) {
            Map<String, Double> conv = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : rmap.entrySet()) {
                try {
                    conv.put(String.valueOf(e.getKey()), Double.parseDouble(String.valueOf(e.getValue())));
                } catch (NumberFormatException ignored) {}
            }
            inst.rl().restore(conv);
        }
        // weights: keep old checkpoint on layout mismatch, never corrupt
        try {
            if (ModelCheckpoint.exists(root, uuid)) {
                ModelCheckpoint.load(root, uuid, inst.network(), inst.optimizer());
            }
        } catch (Exception ignored) {}
    }

    private static Number num(Map<?, ?> m, String key, double def) {
        Object v = m.get(key);
        if (v instanceof Number n) return n;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    public boolean rollbackModel(String uuid) throws IOException {
        return ModelCheckpoint.rollback(root, uuid);
    }

    public void reset(String uuid) throws IOException {
        Files.deleteIfExists(playerFile(uuid));
        Path dir = ModelCheckpoint.dirFor(root, uuid);
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {}
                });
            }
        }
    }
}
