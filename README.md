# Jarvis — Minecraft-Native Neural AI (NeoForge 1.21.1)

Jarvis is a **Minecraft-native personal AI companion**. Every player gets their own
independent Jarvis instance with its own identity, memory, learned skills, mod
knowledge, relationship — and its own neural-network state.

This is **not** a wrapper around ChatGPT, Ollama, a cloud API, or a bundled
pretrained LLM. Every intelligence component — tokenizer, embeddings, attention,
transformer encoder, intent/semantic heads, memory retrieval, skill planner,
voice synthesis — is implemented inside this mod in plain Java and learns from
Minecraft itself.

## Quick start

1. Install **NeoForge 1.21.1** (21.1.x) and Java 21.
2. Drop `jarvis-1.0.0.jar` in `mods/`. Optionally add
   [Simple Voice Chat](https://www.curseforge.com/minecraft/mc-mods/simple-voice-chat)
   (any 1.21.1 NeoForge 2.5+/2.6+ release) for voice transport.
3. Join a world. Jarvis greets you. Type `Jarvis, scan the area.` in chat —
   or run `/jarvis ask scan the area`.

## What Jarvis can do (v1)

- **Understand paraphrases** through a hybrid neural + lexical-semantic intent
  engine that trains on your conversations — not an if/else chatbot.
- **Scan hostiles** with counts, directions and threat levels in natural language.
- **Locate structures/biomes** (villages, strongholds, mansions, deserts…),
  report distance/direction/travel time, and **guide** you (gold waypoint).
- **Ore scanning** — disabled by default (`oreDetectionEnabled=false`); when
  enabled, only configured ores, highlighted blue client-side, world untouched.
- **Learn mods by observation**: registry introspection, tooltips, GUIs,
  recipes, goggle-style displays. Ships with learning integrations for
  **Create** (stress/rotation) and **Ars Nouveau** (glyphs/source/spells).
- **Create new skills** from sandboxed op graphs when asked; honest failure:
  `Sorry, sir, I cannot seem to create this skill.`
- **Remember** per-player episodic/semantic/procedural/world memory with
  relevance-scoped retrieval.
- **Jarvis-to-Jarvis messages** between players, with offline delivery.
- **Built-in voice**: parametric phoneme→prosody→formant synthesis with a
  British-assistant profile; no cloud TTS, ever.

## Configuration

`config/jarvis-common.toml`, `config/jarvis-client.toml`,
`config/jarvis-server.toml` (world overrides under `saves/<world>/serverconfig/`).
See [CONFIGURATION.md](CONFIGURATION.md).

## Commands

`/jarvis ask|scan|locate|tell|forget|mute|voice|status|skills|knowledge|training|memory|debug|profile|model|benchmark|ui|hud|test`
(admin: `reload`, `reset`). In-game self-tests: `/jarvis test neural` etc.

## Portrait HUD

Jarvis shows a small portrait top-left by default: **blue** while talking or
working, **purple** while attempting to learn a new skill, **red** on failure
or config-restricted requests. It pulses during speech/activity and rests
static otherwise. Move it to any corner and resize it (32–128 px, never
cropped) with `/jarvis hud`. If the portrait ever fails to appear, run
`/jarvis hud preview` (cycles all three faces) and check the log for the
`Portrait HUD rendering` line.

## Pre-training

Every new Jarvis pre-trains on first login: ~170 curated Minecraft facts
plus a registry sweep of installed mods, with a short neural burst so the
weights carry Minecraft priors. Verify with `/jarvis test bootstrap`.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — system overview
- [NEURAL_NETWORK.md](NEURAL_NETWORK.md) — custom trainable architecture
- [AI_LEARNING.md](AI_LEARNING.md) — training, RL, learning loop
- [MEMORY_SYSTEM.md](MEMORY_SYSTEM.md) — layered memory + retrieval
- [SKILL_SYSTEM.md](SKILL_SYSTEM.md) — skills, DSL sandbox, dynamic creation
- [MOD_LEARNING.md](MOD_LEARNING.md) — registry/observation learning, Create/Ars
- [VOICE_ENGINE.md](VOICE_ENGINE.md) — synthesis pipeline + SVC integration
- [JARVIS_NETWORK.md](JARVIS_NETWORK.md) — Jarvis-to-Jarvis protocol
- [CONFIGURATION.md](CONFIGURATION.md) — all options
- [DEVELOPMENT.md](DEVELOPMENT.md) — build, test, extend
- [API.md](API.md) — API for other mods
- [PERFORMANCE.md](PERFORMANCE.md) — budgets, threading, benchmarks
- [LIMITATIONS.md](LIMITATIONS.md) — honest capability boundaries

## Building

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
gradle build
```

Requires network access on first build (NeoForge userdev + mappings).
Unit tests: `gradle test` (pure-Java core, no Minecraft needed).

## License

MIT. Simple Voice Chat integration is optional and not bundled.
