# PERFORMANCE

## Budgets (defaults)

- Inference per request: 1500 ms, then deterministic fallback.
- Training: 1 batch (8 examples) per dialogue turn, training pool only.
- Entity scan radius 32; ore scan radius 20, max 64 hits; structure radius 5000.
- Memory caps per layer (60–500); training queue 512.
- Voice PCM cached per unique utterance (64 entries); synth on voice pool.
- Autosave every 5 min + on logout/stop; ~1.8 MB/player worst case
  (JSON + 1.3 MB weights checkpoint).

## Threading

MAIN (game) / AI pool (2, dialogue+inference) / TRAINING pool (1) /
VOICE pool (1, synth) / RENDER (client outlines only). Level reads marshal
to the server thread with 10 s bounded waits from AI threads; the server
thread never waits on AI.

## Benchmarks

`/jarvis benchmark` reports params, vocab, facts, memories, skills, training
steps/loss, inference avg ms + timeouts, world-scan time, voice time. In-game
suites (`/jarvis test ...`) report per-suite timings. JUnit covers
regression timing implicitly via loss/param assertions.
