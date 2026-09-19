package com.jarvis.mods;

import java.util.List;
import java.util.Map;

/**
 * Generic view over a mod's registries. Implemented by the NeoForge glue with
 * live registry data; the learning engine only sees plain strings.
 */
public interface RegistryView {
    List<String> blockIds(String modId, int limit);
    List<String> itemIds(String modId, int limit);
    List<String> entityIds(String modId, int limit);
    int blockCount(String modId);
    int itemCount(String modId);
    int entityCount(String modId);
    /** itemId -> human-readable tooltip lines (for tooltip observation). */
    List<String> tooltipFor(String itemId);
    /** recipe summaries: "input -> output" strings involving this mod. */
    List<String> recipeSummaries(String modId, int limit);
    Map<String, String> empty();
}
