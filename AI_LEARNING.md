# AI_LEARNING

## Sources of training data

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
