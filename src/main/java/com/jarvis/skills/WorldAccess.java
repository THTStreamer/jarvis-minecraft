package com.jarvis.skills;

import com.jarvis.navigation.LocationQuery;
import com.jarvis.world.BlockInfo;
import com.jarvis.world.EntityInfo;
import com.jarvis.world.LocatedStructure;
import com.jarvis.world.OreHit;
import com.jarvis.world.PlayerSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Capability boundary between skills and Minecraft. Skills only see this
 * interface (plus plain DTOs), which keeps them testable, sandboxable and
 * independent of mappings. Implemented by the NeoForge glue layer.
 */
public interface WorldAccess {
    PlayerSnapshot snapshot(String playerId);

    List<EntityInfo> nearbyEntities(String playerId, double radius);

    List<BlockInfo> scanBlocks(String playerId, double radius, Set<String> blockIds);

    Optional<LocatedStructure> findStructure(String playerId, LocationQuery query);

    Optional<LocatedStructure> findBiome(String playerId, LocationQuery query);

    void sendMessage(String playerId, String text);

    void highlightOres(String playerId, List<OreHit> hits);

    void setGuideTarget(String playerId, int x, int y, int z, String label);

    void clearGuideTarget(String playerId);

    List<String> inventorySummary(String playerId);

    String lookingAt(String playerId);

    List<String> nearbyContainers(String playerId, double radius);

    List<String> knownModIds();
}
