# ARCHITECTURE

```
Minecraft Perception (events, scans, registries, voice packets)
        |
Minecraft Knowledge (registries, observations, knowledge graph, world model)
        |
Neural Language Model (tokenizer -> embeddings -> transformer -> intent/semantics)
        |
Memory (short-term/working/episodic/semantic/procedural/relationship/mod/world)
        |
Reasoning (multi-step decomposition, planner pipeline)
        |
Skills (registry, sandboxed DSL graphs, built-ins, dynamic creation)
        |
Learning (training pipeline, learning loop, reinforcement, confidence)
        |
Voice (reception/VAD -> understanding -> synthesis -> client playback/SVC)
        |
Player Relationship (profile, personality, trust, Jarvis network)
```

## Package map (`com.jarvis`)

- `ai.neural` — DenseLayer, EmbeddingLayer, LayerNorm, MultiHeadAttention,
  TransformerBlock, JarvisNeuralNetwork, Optimizer, LearningRateSchedule,
  LossFunctions, ModelCheckpoint. Zero Minecraft dependencies.
- `ai.tokenizer` — JarvisTokenizer, Vocabulary (expandable, fixed layout).
- `ai.embeddings` — SemanticEmbedder (sentence vectors + cosine).
- `ai.attention` — AttentionCache (LRU of recent encodings).
- `ai.inference` — AIWorkerPool (ai/train/voice threads), InferenceEngine (budgets).
- `ai.training` — TrainingPipeline, TrainingExample, LearningLoop, ReinforcementLearner.
- `language` — Intent (+IntentEngine hybrid scorer), EntityExtractor,
  ContextTracker (pronouns/follow-ups), Personality, ResponseGenerator
  (compositional + deterministic fallback), ReasoningEngine.
- `memory` — MemoryType, MemoryEntry, JarvisMemory (layered, capped),
  MemoryRetriever (similarity+recency+importance+context, top-k only).
- `knowledge` — Confidence, KnowledgeEntry, KnowledgeGraph, SemanticKnowledge.
- `world` — PlayerSnapshot, EntityInfo, BlockInfo, LocatedStructure, OreHit,
  WorldKnowledge, SpatialReasoning, HostileScanner (natural summaries).
- `navigation` — LocationQuery, LocationFilter, PathPlanning, NavigationMemory.
- `skills` — Skill, SkillContext, SkillResult, SkillOp (sandboxed DSL),
  SkillGraph, SkillValidator (+ApprovedApis allow-list), SkillRegistry,
  SkillExecutor (graph interpreter), DynamicSkillCreator, WorldAccess
  (capability boundary), `builtin` (9 skills).
- `mods` — ModInfo, ModDiscovery, RegistryView, ModLearningEngine,
  ModObservationEngine, GenericModLearner, `integrations` (Create, Ars Nouveau).
- `voice` — Phoneme, PhonemeProcessor (dict+rules), ProsodyEngine,
  VoiceProfile, SpeechSynthesizer (formant PCM), AudioRenderer, VoiceMemory,
  VoicePipeline (cache), VoiceReception (VAD+transcription hook),
  `svc` (JarvisVoicePlugin, VoiceIntegration).
- `network` — JarvisMessage, JarvisNetwork (permissions, offline queue).
- `player` — JarvisProfile (identity, personality, trust).
- `core` — JarvisInstance (per-player orchestrator), JarvisService (singleton),
  JarvisPlanner (explicit pipeline runner for debug/tests).
- `persistence` — PersistenceManager (versioned JSON + binary checkpoints).
- `config` — JarvisSettings (plain POJO), JarvisConfig (3 NeoForge specs).
- `server` — JarvisMod glue: ServerEvents, NeoForgeWorldAccess (thread
  marshalling), NeoForgeRegistries, JarvisCommands, JarvisSelfTest,
  JarvisPackets/JarvisPayloads.
- `client` — JarvisClient, ClientHandlers, JarvisScreen, JarvisDebugScreen,
  HighlightRenderer (blue ores, gold guide; world untouched).
- `api` — JarvisAPI, IModIntegration.
- `util` — Floats, Benchmarks.

## Key design rules

1. **Pure core, thin glue.** All intelligence is plain Java; only `server`/
   `client` touch Minecraft/NeoForge APIs. That is why `gradle test` runs
   without the game.
2. **Capabilities, not code.** Skills see `WorldAccess` only; dynamic skills
   compose `SkillOp` graphs validated against an allow-list. Jarvis never
   generates or executes Java.
3. **Threads:** AI pool (inference/dialogue), training pool (batches),
   voice pool (synth/playback). Level access marshals to the server thread
   with bounded waits; the server thread is never blocked by training.
4. **Per-player isolation.** Instances share nothing mutable; the Jarvis
   network only carries explicitly transmitted text.
