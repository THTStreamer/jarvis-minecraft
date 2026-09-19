package com.jarvis.skills.builtin;

import com.jarvis.language.Intent;
import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SkillResult;
import com.jarvis.world.BlockInfo;
import com.jarvis.world.OreHit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Ore scanner. Disabled by default; when disabled it answers naturally and
 * scans nothing. When enabled it scans only configured ores and highlights
 * them client-side without touching the world.
 */
public final class OreScannerSkill extends Skill {
    public interface OrePolicy {
        boolean enabled();
        Set<String> allowedOres();
        double radius();
    }

    private final OrePolicy policy;

    public OreScannerSkill(OrePolicy policy) {
        super("ore_scanner", "Ore Scanner",
            "Scans configured ores nearby and highlights them client-side", "1", 0.85f);
        this.policy = policy;
    }

    @Override
    public boolean preconditions(SkillContext ctx) {
        return true; // disabled state is a valid answer, not a precondition failure
    }

    @Override
    protected SkillResult execute(SkillContext ctx) {
        if (!policy.enabled()) {
            return SkillResult.ok(Intent.ORE_SCAN, SkillResult.map("disabled", true));
        }
        Set<String> allowed = policy.allowedOres();
        if (allowed.isEmpty()) {
            return SkillResult.ok(Intent.ORE_SCAN, SkillResult.map("disabled", true));
        }
        List<BlockInfo> found = ctx.world().scanBlocks(ctx.playerId(), policy.radius(), allowed);
        List<OreHit> hits = new ArrayList<>();
        for (BlockInfo b : found) hits.add(new OreHit(b.blockId(), b.x(), b.y(), b.z(), b.distance()));
        ctx.world().highlightOres(ctx.playerId(), hits);
        if (hits.isEmpty()) {
            return SkillResult.ok(Intent.ORE_SCAN,
                SkillResult.map("detail", "No configured ores within "
                    + (int) policy.radius() + " blocks."));
        }
        StringBuilder detail = new StringBuilder("I found " + hits.size() + " ore blocks nearby: ");
        for (int i = 0; i < Math.min(4, hits.size()); i++) {
            OreHit h = hits.get(i);
            if (i > 0) detail.append(", ");
            detail.append(pretty(h.blockId())).append(" about ")
                .append(Math.round(h.distance())).append(" blocks away");
        }
        detail.append(". I've highlighted them in blue.");
        return SkillResult.ok(Intent.ORE_SCAN, SkillResult.map("detail", detail.toString()));
    }

    private static String pretty(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return path.replace('_', ' ').toLowerCase(Locale.ROOT);
    }
}
