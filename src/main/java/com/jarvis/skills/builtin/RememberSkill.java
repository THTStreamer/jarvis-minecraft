package com.jarvis.skills.builtin;

import com.jarvis.language.Intent;
import com.jarvis.memory.JarvisMemory;
import com.jarvis.memory.MemoryRetriever;
import com.jarvis.memory.MemoryType;
import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SkillResult;
import java.util.List;

/** Stores, recalls and clears memories on request. */
public final class RememberSkill extends Skill {
    private final JarvisMemory memory;

    public RememberSkill(JarvisMemory memory) {
        super("memory_keeper", "Memory Keeper",
            "Remembers facts, recalls memories and forgets on request", "1", 0.9f);
        this.memory = memory;
    }

    @Override
    protected SkillResult execute(SkillContext ctx) {
        Intent intent = ctx.intent();
        if (intent == Intent.FORGET) {
            memory.forgetType(MemoryType.EPISODIC);
            memory.forgetType(MemoryType.SEMANTIC);
            return SkillResult.ok(Intent.FORGET, SkillResult.map());
        }
        if (intent == Intent.MEMORY_QUERY) {
            MemoryRetriever retriever = new MemoryRetriever(memory);
            List<MemoryRetriever.Hit> hits = retriever.retrieve(null, ctx.input(), 5, System.currentTimeMillis());
            if (hits.isEmpty()) {
                return SkillResult.ok(Intent.MEMORY_QUERY,
                    SkillResult.map("answer", "I don't hold any specific memories yet. Tell me things worth remembering."));
            }
            StringBuilder sb = new StringBuilder("What I remember: ");
            for (int i = 0; i < hits.size(); i++) {
                if (i > 0) sb.append(" ");
                sb.append(hits.get(i).entry().text()).append(".");
            }
            return SkillResult.ok(Intent.MEMORY_QUERY, SkillResult.map("answer", sb.toString()));
        }
        // REMEMBER: store episodic fact
        String fact = ctx.input();
        memory.store(MemoryType.EPISODIC, fact, null, 0.8f, "player-request");
        if (ctx.snapshot() != null) {
            memory.store(MemoryType.WORLD,
                "Player was at " + ctx.snapshot().blockX() + "," + ctx.snapshot().blockY()
                + "," + ctx.snapshot().blockZ() + " in " + ctx.snapshot().dimension(),
                null, 0.4f, "auto");
        }
        return SkillResult.ok(Intent.REMEMBER, SkillResult.map());
    }
}
