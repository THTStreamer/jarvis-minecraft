package com.jarvis.world;

import java.util.List;

/** Result of an ore scan: hits plus a natural summary. */
public record OreScanResult(List<OreHit> hits, String summary, boolean disabled) {
    public static OreScanResult disabledResult() {
        return new OreScanResult(List.of(), "disabled", true);
    }
}
