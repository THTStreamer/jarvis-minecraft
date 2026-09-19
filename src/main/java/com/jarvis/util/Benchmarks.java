package com.jarvis.util;

import com.jarvis.core.JarvisInstance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Performance + size metrics for /jarvis benchmark and the UI. */
public final class Benchmarks {
    private Benchmarks() {}

    public static Map<String, Object> forInstance(JarvisInstance inst) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("params", inst.network().paramCount());
        m.put("vocab", inst.tokenizer().vocabulary().size());
        m.put("facts", inst.knowledge().size());
        m.put("memories", inst.memory().countAll());
        m.put("skills", inst.skills().size());
        m.put("trainSteps", inst.training().steps());
        m.put("trainLoss", inst.training().lastLoss());
        m.put("inferenceAvgMs", inst.inference().avgMillis());
        m.put("inferenceTimeouts", inst.inference().timedOut());
        m.put("utterances", inst.voice() == null ? 0 : 0);
        return m;
    }

    public static List<String> lines(JarvisInstance inst) {
        Map<String, Object> m = forInstance(inst);
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : m.entrySet()) out.add(e.getKey() + "=" + e.getValue());
        out.addAll(inst.inference().stats());
        out.addAll(inst.debugInfo().entrySet().stream()
            .map(e -> "debug." + e.getKey() + "=" + e.getValue()).toList());
        return out;
    }
}
