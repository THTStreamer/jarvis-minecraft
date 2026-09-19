# AI_LEARNING

## Pre-training (first login)

New players don't start from zero. On first login Jarvis ingests, once per
player and off-thread:

1. **Curated corpus** (`assets/jarvis/corpus/minecraft_bootstrap.json`,
   ~170 facts): dimensions, ores, tools/armor tiers, utility blocks, hostile
   and passive mobs, bosses, biomes, structures, mechanics (day cycle, hunger,
   XP/enchanting, brewing, redstone, portals), food/farming and Nether/End
   knowledge.
2. **Registry sweep** of every installed mod: blocks, items, entities and
   biomes become `contains` / `is-a` facts plus training sentences and
   vocabulary — structured representations, never raw dumps.
3. **Neural burst**: ~30 small batches train the network on the queued
   sentences so the weights themselves carry Minecraft priors.

Everything lands in the player's *private* graph/vocabulary/weights.
Disable with `bootstrapEnabled=false`. Verify with
`/jarvis test bootstrap` or `/jarvis knowledge diamond`.

## Ongoing sources of training data

- Player conversations (prompt -> response next-token examples).
- Registry-derived semantic sentences ("Mechanical press is a block from the
  Create mod.") — structured representations, not raw dumps.
- Skill outcomes (success/failure traces from `LearningLoop`).
- Observations (pickup/place/break/GUI/craft/advancement/tooltip/goggles).

## Learning loop

```
Observe -> Interpret -> Predict -> Act -> Evaluate -> Reward/Penalty -> Update -> Remember
```

Every dialogue turn records a cycle; `ReinforcementLearner` keeps per-policy
scores (`skill:<id>`, updated +1/-1 style with smoothing 0.15) that bias
future routing and surface in debug/benchmarks.

## Confidence

Facts and skills carry confidence in [0,1]: confirmations nudge up
(`c += (1-c)*w*0.25`), contradictions cut (`c -= c*w*0.35`). Knowledge answers
below 0.35 are withheld ("I don't have reliable knowledge…"). Incorrect
predictions decrease confidence via the RL outcome path.

## What learning modifies

Neural weights, intent prototypes, synonym weights, knowledge graph,
skill graphs/stats, memory, RL policies. **Never Java code** — there is no
self-modification of the program.

## Threading

Training runs one batch at a time on the training pool (`trainAsync`),
never on the server thread. Queue cap 512, batch configurable (default 8).
