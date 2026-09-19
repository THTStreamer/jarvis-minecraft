package com.jarvis.bootstrap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the curated Minecraft bootstrap corpus shipped with the mod.
 * Pure Java; the JSON lives at assets/jarvis/corpus/minecraft_bootstrap.json
 * and is on the classpath in-game and in unit tests.
 */
public final class BootstrapCorpus {
    public static final String PATH = "/assets/jarvis/corpus/minecraft_bootstrap.json";

    public record Fact(String s, String r, String o, float c) {
        public String sentence() {
            return s + " " + r + " " + o + ".";
        }
    }

    private BootstrapCorpus() {}

    @SuppressWarnings("unchecked")
    public static List<Fact> load() {
        List<Fact> out = new ArrayList<>();
        try (InputStream in = BootstrapCorpus.class.getResourceAsStream(PATH)) {
            if (in == null) return out;
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> root = gson.fromJson(
                new InputStreamReader(in, StandardCharsets.UTF_8), type);
            Object facts = root.get("facts");
            if (!(facts instanceof List<?> list)) return out;
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                String s = str(m.get("s"));
                String r = str(m.get("r"));
                String ob = str(m.get("o"));
                if (s.isEmpty() || r.isEmpty() || ob.isEmpty()) continue;
                float c = 0.8f;
                try {
                    c = Float.parseFloat(str(m.get("c")));
                } catch (NumberFormatException ignored) {}
                out.add(new Fact(s, r, ob, Math.max(0f, Math.min(1f, c))));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
