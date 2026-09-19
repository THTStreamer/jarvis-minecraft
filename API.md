# API (for other mods)

```java
JarvisAPI.get(playerId)                    // Optional<JarvisInstance>
JarvisAPI.registerKnowledge(playerId, subject, relation, object, source, confidence)
JarvisAPI.registerSkill(playerId, skill)   // extend Skill
JarvisAPI.observe(playerId, eventType, data)
JarvisAPI.query(playerId, topic)           // List<KnowledgeEntry>
JarvisAPI.getKnowledge(playerId)           // KnowledgeGraph
JarvisAPI.sendMessage(playerId, text)      // proactive speech (chat + voice)
JarvisAPI.registerModIntegration(integration)
JarvisAPI.createSkill(playerId, description)
```

`IModIntegration`: `modId()`, `onModDetected(ctx)`, `onObservation(...)`,
`answer(question, ctx)` (null when unknown). Example: a machine mod can push
`uses-stress`-style facts or answer diagnostics through its own integration
without touching Jarvis sources.

Event types for `observe`: `item_pickup`, `block_place`, `block_break`,
`gui_open` (+`block`), `craft` (+`result`), `tooltip` (+`item`,`line`),
`advancement` (+`id`), `goggles` (+`block`,`text`), `spell` (+`glyph`,`requires`).
All calls are thread-safe and no-ops when the player's Jarvis is unavailable.
