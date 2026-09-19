package com.jarvis.skills;

import com.jarvis.language.Intent;
import java.util.LinkedHashMap;
import java.util.Map;

/** Outcome of one skill execution. */
public final class SkillResult {
    private final boolean success;
    private final Intent responseIntent;
    private final Map<String, Object> frame;
    private final String note;
    private final double confidenceDelta;

    private SkillResult(boolean success, Intent responseIntent, Map<String, Object> frame,
                        String note, double confidenceDelta) {
        this.success = success;
        this.responseIntent = responseIntent;
        this.frame = frame;
        this.note = note;
        this.confidenceDelta = confidenceDelta;
    }

    public static SkillResult ok(Intent responseIntent, Map<String, Object> frame) {
        return new SkillResult(true, responseIntent, frame, "", 0.05);
    }

    public static SkillResult ok(Intent responseIntent, Map<String, Object> frame, String note) {
        return new SkillResult(true, responseIntent, frame, note, 0.05);
    }

    public static SkillResult fail(Intent responseIntent, Map<String, Object> frame, String note) {
        return new SkillResult(false, responseIntent, frame, note, -0.08);
    }

    public boolean success() { return success; }
    public Intent responseIntent() { return responseIntent; }
    public Map<String, Object> frame() { return frame; }
    public String note() { return note; }
    public double confidenceDelta() { return confidenceDelta; }

    /** Static frame builder (named `map` to avoid clashing with the {@link #frame()} accessor). */
    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }
}
