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
