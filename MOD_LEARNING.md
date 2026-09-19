# MOD_LEARNING

Jarvis never hardcodes mods (beyond two learning integrations). For each
installed mod it records id/name/version, then builds **structured semantic
representations**:

- `mod --contains-block/item/entity--> id`, `id --is-a--> block/item/...`
- vocabulary learning for unknown names ("mechanical", "press")
- semantic sentences into the training pipeline
- concept frames (`category`, `obtained_by`, `used_for`, …)

## Observation engine

`ModObservationEngine` converts block place/break, item pickup/craft, GUI
interaction, tooltips, advancements, goggle-style displays and spell events
into graph facts + episodic memories. `GenericModLearner` adds tooltip
classification (machine/food/…), GUI slot maps and recipe facts for mods with
no dedicated code.

## Create (optional integration)

Detects `create`, records stress/rotation/kinetics mechanics, parses goggle
text (`128 SU`, `overstressed`) into `uses-stress`/`state` facts, and
diagnoses from learned entries — e.g. overstress with capacity advice.
Nothing here references Create classes; it works purely from observations.

## Ars Nouveau (optional integration)

Detects `ars_nouveau`, records glyphs/source/spell-crafting mechanics,
observes spell requirements, and answers "what am I missing?" by comparing
learned requirements against the live inventory summary.

## API for mods

Other mods teach Jarvis via `JarvisAPI` (registerKnowledge/registerSkill/
observe/query/…) or `IModIntegration`. See API.md.
