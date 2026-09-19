# SKILL_SYSTEM

Every capability is a `Skill`: id, name, description, version, confidence,
preconditions, required knowledge, usage stats. Built-ins:

| Skill | Covers |
|---|---|
| hostile_mob_scanner | SCAN_HOSTILES |
| area_survey | SCAN_AREA |
| village_locator | LOCATE_STRUCTURE (villages, strongholds, mansions, …) |
| biome_locator | LOCATE_BIOME |
| navigation | NAVIGATE_GUIDE + distance/time follow-ups |
| ore_scanner | ORE_SCAN (policy-gated) |
| knowledge_answer | MOD/MACHINE/SPELL/KNOWLEDGE questions |
| memory_keeper | REMEMBER/FORGET/MEMORY_QUERY |
| status | STATUS/GREETING/HELP/SKILL_LIST/… |

## Sandboxed DSL

Dynamic skills compose only `SkillOp`: OBSERVE_ENTITY, FILTER_ENTITY,
GET_POSITION, CALCULATE_DISTANCE, CALCULATE_DIRECTION, QUERY_REGISTRY,
QUERY_RECIPE, QUERY_BLOCK, QUERY_ITEM, QUERY_WORLD, NAVIGATE, SPEAK, DISPLAY,
STORE_MEMORY, CALL_APPROVED_API (closed allow-list: world.scan, world.locate,
world.guide, memory.store, knowledge.query, voice.speak, network.send).

`SkillValidator` rejects empty/overlong plans, unapproved APIs, and plans
that never answer the player. `SkillExecutor.executeGraph` interprets graphs
op-by-op with reviewed implementations.

## Dynamic creation

Unknown-but-achievable request → compose graph → validate → register as a
`ComposedSkill` (confidence 0.55, improves with use) → execute. Genuinely
impossible requests answer exactly:
`Sorry, sir, I cannot seem to create this skill.`

## Example

"How much stress is this using?" with no prior knowledge → observes goggle
display + machine + network → learns `uses-stress` facts → next time answers
from learned knowledge with observation counts.
