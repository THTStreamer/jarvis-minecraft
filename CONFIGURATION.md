# CONFIGURATION

Three files (plus per-world server overrides under
`saves/<world>/serverconfig/`):

## jarvis-common.toml

- `[ai]` — `enabled`, `learningEnabled`, `modLearningEnabled`,
  `dynamicSkillsEnabled`, `jarvisToJarvis`, `debug`
- `[personality]` — `addressTitle` (SIR/MADAM/COMMANDER/BOSS/FRIEND/NONE),
  `verbosity`, `wit`, `formality` (0..1)
- `[neural]` — `maxVocab` (512..8192), `dim`, `heads`, `blocks`, `ffnDim`,
  `trainingBatch`

## jarvis-client.toml

- `[voice]` — `enabled`, `speed` (0.5..2.0), `pitch` (60..300 Hz),
  `requireVoiceChat` (text always works regardless)
- `[portrait]` — `enabled` (default true), `position`
  (`TOP_LEFT` default, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`),
  `size` (default 64 px, 32..128, scaled uniformly, never cropped)

The in-game portrait GUI (`/jarvis hud`, or the Portrait button in `/jarvis ui`)
moves/resizes the face live and stores per-player choices in
`config/jarvis-hud.properties`, which override the toml defaults.

## jarvis-server.toml

- `[scanning]` — **`oreDetectionEnabled=false` (default!)**,
  `enabledOres` (default: diamond ore, deepslate diamond ore, ancient debris),
  `oreRadius` (default 20), `hostileRadius` (32), `worldSearchRadius` (5000)

`/jarvis reload` (op 2) re-snapshots all three files into live instances.
Per-player voice/personality tweaks persist in `jarvis/players/<uuid>.json`
and override file defaults for that player only.
