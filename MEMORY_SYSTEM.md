# MEMORY_SYSTEM

Layers (`MemoryType`): SHORT_TERM (conversation, 60), WORKING (current task,
12), EPISODIC (events, 300), SEMANTIC (facts, 500), PROCEDURAL (how-to, 200),
RELATIONSHIP (100), MOD (500), WORLD (locations, 300). Eviction drops the
lowest-importance oldest entries first.

## Retrieval

`MemoryRetriever.retrieve(queryVec, taskHint, topK, now)` scores:

```
0.5 * semantic + 0.2 * recency + 0.2 * importance + familiarity + contextBoost
```

Only top-k entries enter the reasoning context — the full memory is never
dumped into a request. Retrieved entries get `touch()`ed (access count feeds
familiarity).

## Conversation

`ContextTracker` keeps the last 40 turns plus the current target, resolving
pronouns and follow-ups ("How far?" / "Can you mark it?" / "What about the
closest one?") against the active village journey or scan.

## Privacy

Memory is per-player and persisted under `jarvis/players/<uuid>.json`.
`/jarvis forget` clears episodic+semantic memory. Jarvis-to-Jarvis never
exposes memory — only explicitly transmitted text.
