# LIMITATIONS (honest boundaries)

1. **No day-one omniscience.** Embeddings start random; paraphrase strength
   grows with per-player training. The hybrid scorer keeps v1 useful while
   the neural component matures.
2. **No open-vocabulary speech-to-text.** Voice packets drive VAD/prosody;
   words arrive via chat. `TranscriptionProvider` is the STT extension point.
3. **SVC playback is positional best-effort.** Guaranteed path is client-side
   synthesis + text; both always fire.
4. **Generic "cave" search** has no vanilla structure target and fails
   honestly; lush caves route to biome search. Responses never invent
   coordinates, counts, recipes or mechanics (confidence-gated answers).
5. **Structure search** uses live datapack data (direct registry ids +
   biome search); modded structures work when their ids resolve.
6. **Single-checkpoint rollback** for weights; JSON saves are atomic but not
   historically versioned.
7. **Per-player networks cost ~1.3 MB RAM/disk each** at defaults; raise
   `[neural] dim/blocks` only on capable servers.
8. **First build needs network** (NeoForge userdev, mappings, Gradle plugins).
