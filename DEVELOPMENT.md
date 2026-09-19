# DEVELOPMENT

## Requirements

Java 21, Gradle (wrapper or 8.11+), network on first build.

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
gradle build        # full mod jar
gradle test         # pure-Java unit tests (no Minecraft)
gradle runClient    # dev client
gradle runServer    # dev server (--nogui)
```

## Conventions

- Intelligence code stays dependency-free (`com.jarvis.*` except
  `server`/`client`/`config` never import Minecraft/NeoForge).
- No `// TODO` placeholders: phase-scoped features ship functional with
  documented extension points instead.
- Thread rule: AI/train/voice pools for compute; server thread only via
  `WorldAccess` marshalling; no blocking calls on the render thread.
- SOLID: capability boundaries (`WorldAccess`, `RegistryView`,
  `TranscriptionProvider`, `IModIntegration`) keep the core testable.

## Test layers

1. JUnit (`src/test`) — tokenizer, neural math, attention, memory, graph,
   intent paraphrases, skills vs fake world, voice synth, persistence,
   mod learning, J2J relay.
2. In-game (`/jarvis test <suite>`, `/jarvis benchmark`) — live registries,
   scans, navigation against the real server.
3. Debug (`/jarvis debug`, JarvisDebugScreen) — intent/entities/goal/skill/
   confidence/memories/timings.

## Dependency policy

Before adding a dependency: purpose, 1.21.1 compatibility, custom-AI
compliance (no pretrained/LLM/cloud inference), license, distribution impact.
SVC API is compile-only + runtime-optional. Gson comes from Minecraft.
