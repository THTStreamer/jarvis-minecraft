package com.jarvis.server;

import com.jarvis.mods.ModInfo;
import com.jarvis.mods.RegistryView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Live registry introspection for the mod-learning engine: mod list,
 * per-mod registry samples/counts, tooltips and recipe summaries.
 */
public final class NeoForgeRegistries {
    private NeoForgeRegistries() {}

    /** Vanilla biome ids used when no live server registry is available. */
    private static final List<String> VANILLA_BIOMES = List.of(
        "minecraft:plains", "minecraft:sunflower_plains", "minecraft:desert",
        "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest",
        "minecraft:dark_forest", "minecraft:jungle", "minecraft:sparse_jungle",
        "minecraft:bamboo_jungle", "minecraft:savanna", "minecraft:savanna_plateau",
        "minecraft:windswept_savanna", "minecraft:taiga", "minecraft:snowy_taiga",
        "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga",
        "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:ocean",
        "minecraft:deep_ocean", "minecraft:warm_ocean", "minecraft:lukewarm_ocean",
        "minecraft:deep_lukewarm_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean",
        "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:river",
        "minecraft:frozen_river", "minecraft:beach", "minecraft:snowy_beach",
        "minecraft:stony_shore", "minecraft:windswept_hills", "minecraft:windswept_forest",
        "minecraft:windswept_gravelly_hills", "minecraft:meadow", "minecraft:grove",
        "minecraft:snowy_slopes", "minecraft:jagged_peaks", "minecraft:frozen_peaks",
        "minecraft:stony_peaks", "minecraft:badlands", "minecraft:eroded_badlands",
        "minecraft:wooded_badlands", "minecraft:mushroom_fields", "minecraft:dripstone_caves",
        "minecraft:lush_caves", "minecraft:deep_dark", "minecraft:nether_wastes",
        "minecraft:soul_sand_valley", "minecraft:crimson_forest", "minecraft:warped_forest",
        "minecraft:basalt_deltas", "minecraft:the_end", "minecraft:end_highlands",
        "minecraft:end_midlands", "minecraft:end_barrens", "minecraft:small_end_islands",
        "minecraft:the_void");

    public static List<ModInfo> modInfos() {
        List<ModInfo> out = new ArrayList<>();
        try {
            ModList.get().forEachModContainer((id, container) -> {
                String version = container.getModInfo().getVersion().toString();
                out.add(new ModInfo(id, container.getModInfo().getDisplayName(), version));
            });
        } catch (Exception ignored) {}
        return out;
    }

    public static RegistryView view() {
        return new RegistryView() {
            @Override
            public List<String> blockIds(String modId, int limit) {
                List<String> out = new ArrayList<>();
                try {
                    for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
                        if (id.getNamespace().equals(modId)) {
                            out.add(id.toString());
                            if (out.size() >= limit) break;
                        }
                    }
                } catch (Exception ignored) {}
                return out;
            }

            @Override
            public List<String> itemIds(String modId, int limit) {
                List<String> out = new ArrayList<>();
                try {
                    for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
                        if (id.getNamespace().equals(modId)) {
                            out.add(id.toString());
                            if (out.size() >= limit) break;
                        }
                    }
                } catch (Exception ignored) {}
                return out;
            }

            @Override
            public List<String> entityIds(String modId, int limit) {
                List<String> out = new ArrayList<>();
                try {
                    for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                        if (id.getNamespace().equals(modId)) {
                            out.add(id.toString());
                            if (out.size() >= limit) break;
                        }
                    }
                } catch (Exception ignored) {}
                return out;
            }

            @Override
            public int blockCount(String modId) {
                return blockIds(modId, Integer.MAX_VALUE).size();
            }

            @Override
            public int itemCount(String modId) {
                return itemIds(modId, Integer.MAX_VALUE).size();
            }

            @Override
            public int entityCount(String modId) {
                return entityIds(modId, Integer.MAX_VALUE).size();
            }

            @Override
            public List<String> biomeIds(String modId, int limit) {
                List<String> out = new ArrayList<>();
                try {
                    // datapack registries need a live server; fall back to the
                    // vanilla list when called outside the game (e.g. tests)
                    var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                    if (server != null) {
                        var reg = server.registryAccess()
                            .registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
                        for (ResourceLocation id : reg.keySet()) {
                            if (id.getNamespace().equals(modId)) {
                                out.add(id.toString());
                                if (out.size() >= limit) return out;
                            }
                        }
                        return out;
                    }
                } catch (Exception ignored) {}
                for (String id : VANILLA_BIOMES) {
                    if (id.startsWith(modId + ":")) {
                        out.add(id);
                        if (out.size() >= limit) break;
                    }
                }
                return out;
            }

            @Override
            public List<String> tooltipFor(String itemId) {
                List<String> out = new ArrayList<>();
                try {
                    var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                    ItemStack stack = new ItemStack(item);
                    var lines = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY,
                        null, net.minecraft.world.item.TooltipFlag.NORMAL);
                    for (Component c : lines) {
                        String s = c.getString().trim();
                        if (!s.isEmpty()) out.add(s);
                        if (out.size() >= 6) break;
                    }
                } catch (Exception ignored) {}
                return out;
            }

            @Override
            public List<String> recipeSummaries(String modId, int limit) {
                // Recipe-manager access needs a level; the glue fills these via
                // observations instead. Kept as an extension point.
                return List.of();
            }

            @Override
            public Map<String, String> empty() {
                return Map.of();
            }
        };
    }
}
