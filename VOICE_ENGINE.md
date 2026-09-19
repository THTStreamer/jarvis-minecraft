# VOICE_ENGINE

Modular pipeline: `VoiceModel` (profile) → `PhonemeProcessor` (dictionary +
rule fallback, sentence boundaries) → `ProsodyEngine` (pitch/timing/volume,
emotion states) → `SpeechSynthesizer` (formant PCM @22050 Hz) →
`AudioRenderer` (client playback) + `VoiceMemory`/`VoiceProfile`.

Default voice: measured cadence, ~112 Hz base, falling declaratives —
a refined British-inspired assistant character (original, no impersonation).
Configurable: rate, pitch, volume, timbre seed (`/jarvis voice ...`).

## Transport

- **Simple Voice Chat is optional.** `JarvisVoicePlugin`
  (`@ForgeVoicechatPlugin`, compile-only API dep) captures the server API and
  forwards microphone packets to `VoiceReception` (energy VAD + prosodic
  features + pluggable transcription hook).
- **Speech out** is synthesized by the built-in engine on the client
  (SpeechPayload → local PCM → playback) and always accompanies text, so it
  works with or without SVC.

## Honest limitation

Open-vocabulary neural speech-to-text from raw opus without native ML
libraries is beyond a safe v1 baseline: voice acts as an attention/speech
presence signal while words arrive via chat. The `TranscriptionProvider`
interface is the extension point for a future on-device recognizer.
See LIMITATIONS.md.
