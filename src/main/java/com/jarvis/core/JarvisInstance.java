package com.jarvis.core;

import com.jarvis.ai.attention.AttentionCache;
import com.jarvis.ai.embeddings.SemanticEmbedder;
import com.jarvis.ai.inference.InferenceEngine;
import com.jarvis.ai.neural.JarvisNeuralNetwork;
import com.jarvis.ai.neural.Optimizer;
import com.jarvis.ai.tokenizer.JarvisTokenizer;
import com.jarvis.ai.tokenizer.Vocabulary;
import com.jarvis.ai.training.LearningLoop;
import com.jarvis.ai.training.ReinforcementLearner;
import com.jarvis.ai.training.TrainingPipeline;
import com.jarvis.api.IModIntegration;
import com.jarvis.config.JarvisSettings;
import com.jarvis.knowledge.KnowledgeGraph;
import com.jarvis.knowledge.SemanticKnowledge;
import com.jarvis.language.ContextTracker;
import com.jarvis.language.EntityExtractor;
import com.jarvis.language.Intent;
import com.jarvis.language.IntentEngine;
import com.jarvis.language.Personality;
import com.jarvis.language.ReasoningEngine;
import com.jarvis.language.ResponseGenerator;
import com.jarvis.memory.JarvisMemory;
import com.jarvis.memory.MemoryRetriever;
import com.jarvis.memory.MemoryType;
import com.jarvis.mods.GenericModLearner;
import com.jarvis.mods.ModLearningEngine;
import com.jarvis.mods.ModObservationEngine;
import com.jarvis.mods.RegistryView;
import com.jarvis.mods.integrations.ArsNouveauIntegration;
import com.jarvis.mods.integrations.CreateIntegration;
import com.jarvis.navigation.NavigationMemory;
import com.jarvis.network.HudSignal;
import com.jarvis.network.JarvisNetwork;
import com.jarvis.persistence.PersistenceManager;
import com.jarvis.player.JarvisProfile;
import com.jarvis.skills.DynamicSkillCreator;
import com.jarvis.skills.Skill;
import com.jarvis.skills.SkillContext;
import com.jarvis.skills.SkillExecutor;
import com.jarvis.skills.SkillRegistry;
import com.jarvis.skills.SkillResult;
import com.jarvis.skills.WorldAccess;
import com.jarvis.skills.builtin.AreaSurveySkill;
import com.jarvis.skills.builtin.BiomeLocatorSkill;
import com.jarvis.skills.builtin.HostileMobScannerSkill;
import com.jarvis.skills.builtin.KnowledgeAnswerSkill;
import com.jarvis.skills.builtin.NavigationSkill;
import com.jarvis.skills.builtin.OreScannerSkill;
import com.jarvis.skills.builtin.RememberSkill;
import com.jarvis.skills.builtin.StatusSkill;
import com.jarvis.skills.builtin.VillageLocatorSkill;
import com.jarvis.voice.ProsodyEngine;
import com.jarvis.voice.VoiceMemory;
import com.jarvis.voice.VoicePipeline;
import com.jarvis.voice.VoiceProfile;
import com.jarvis.voice.VoiceReception;
import com.jarvis.world.PlayerSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * One player's Jarvis: identity + neural state + memory + knowledge + skills
 * + voice. All heavy work runs on the AI pool; world access marshals to the
 * server thread inside the {@link WorldAccess} implementation.
 */
public final class JarvisInstance {
    public record JarvisResponse(String text, Intent intent, String skillId,
                                 double confidence, long inferenceNanos) {}

    private final JarvisProfile profile;
    private final JarvisService.Wiring wiring;
    private Supplier<JarvisSettings> settings;

    // language stack
    private final Vocabulary vocabulary;
    private final JarvisTokenizer tokenizer;
    private final JarvisNeuralNetwork network;
    private final Optimizer optimizer;
    private final Optimizer alignOptimizer;
    private final SemanticEmbedder embedder;
    private final IntentEngine intents;
    private final EntityExtractor entities = new EntityExtractor();
    private final ContextTracker context;
    private final ResponseGenerator responses;
    private final ReasoningEngine reasoning = new ReasoningEngine();

    // memory + knowledge
    private final JarvisMemory memory = new JarvisMemory();
    private final MemoryRetriever retriever = new MemoryRetriever(memory);
    private final KnowledgeGraph knowledge = new KnowledgeGraph();
    private final SemanticKnowledge semantics;
    private final com.jarvis.world.WorldKnowledge worldKnowledge = new com.jarvis.world.WorldKnowledge();
    private final NavigationMemory navigation = new NavigationMemory(16);

    // skills
    private final SkillRegistry skills = new SkillRegistry();
    private final SkillExecutor executor;
    private final DynamicSkillCreator creator;

    // learning
    private final TrainingPipeline training;
    private final LearningLoop learningLoop = new LearningLoop(200);
    private final ReinforcementLearner rl = new ReinforcementLearner(0.15);

    // voice
    private final VoiceProfile voiceProfile = new VoiceProfile();
    private final VoicePipeline voice;
    private final VoiceReception reception = new VoiceReception(128);
    private final AttentionCache attentionCache = new AttentionCache(128);
    private final InferenceEngine inference;

    // mods
    private final ModLearningEngine modLearner;
    private final ModObservationEngine observationEngine;
    private final GenericModLearner genericLearner;
    private final CreateIntegration createIntegration;
    private final ArsNouveauIntegration arsIntegration;

    // debug
    private volatile String lastIntent = "NONE";
    private volatile boolean bootstrapped;    private volatile String lastSkill = "NONE";
    private volatile double lastConfidence;
    private volatile long lastInferenceNanos;
    private volatile long lastWorldNanos;
    private volatile String lastGoal = "";

    public JarvisInstance(JarvisProfile profile, JarvisService.Wiring wiring, Supplier<JarvisSettings> settings) {
        this.profile = profile;
        this.wiring = wiring;
        this.settings = settings;
        JarvisSettings s = settings.get();

        this.vocabulary = new Vocabulary(s.maxVocab);
        this.tokenizer = new JarvisTokenizer(vocabulary);
        this.network = new JarvisNeuralNetwork(s.maxVocab, s.dim, s.heads, s.blocks, s.ffnDim, s.maxSeq,
            s.seed + profile.playerId().getLeastSignificantBits());
        this.optimizer = Optimizer.adamW(3e-4f, 0.01f);
        this.alignOptimizer = Optimizer.adam(1e-3f);
        this.embedder = new SemanticEmbedder(tokenizer, network);
        this.intents = new IntentEngine(embedder, tokenizer, network, alignOptimizer);
        this.context = new ContextTracker(40);
        Personality p = profile.personality();
        this.responses = new ResponseGenerator(p, s.seed);
        this.semantics = new SemanticKnowledge(knowledge);
        this.executor = new SkillExecutor(wiring.aiPool(), skills);
        this.executor.setMemoryHook((text, ctx2) ->
            memory.store(MemoryType.EPISODIC, text, null, 0.5f, ctx2));
        this.creator = new DynamicSkillCreator(skills, knowledge, executor);
        this.training = new TrainingPipeline(network, tokenizer, optimizer, wiring.trainingPool(),
            512, s.trainingBatch);
        this.voice = new VoicePipeline(voiceProfile, new VoiceMemory(64));
        this.inference = new InferenceEngine(wiring.aiPool(), s.inferenceBudgetMs);
        this.modLearner = new ModLearningEngine(knowledge, semantics, training, tokenizer);
        this.observationEngine = new ModObservationEngine(knowledge);
        this.observationEngine.setMemoryHook((text, imp) ->
            memory.store(MemoryType.EPISODIC, text, null, imp, "observation"));
        this.genericLearner = new GenericModLearner(knowledge);
        this.createIntegration = new CreateIntegration(knowledge);
        this.arsIntegration = new ArsNouveauIntegration(knowledge);

        applyPersonality(s);
        registerBuiltins(s);
        intents.initialize();
        reception.setTranscriptHook((id, text) -> handleTranscript(text));
    }

    private void applyPersonality(JarvisSettings s) {
        Personality p = profile.personality();
        try {
            p.setTitle(Personality.AddressTitle.valueOf(s.addressTitle));
        } catch (IllegalArgumentException ignored) {}
        p.setVerbosity(s.verbosity);
        p.setWit(s.wit);
        p.setFormality(s.formality);
        voiceProfile.setSpeechRate(s.speechSpeed);
        voiceProfile.setBasePitchHz(s.speechPitch);
    }

    private void registerBuiltins(JarvisSettings s) {
        skills.register(new HostileMobScannerSkill(s.hostileScanRadius));
        skills.register(new AreaSurveySkill(s.hostileScanRadius));
        skills.register(new VillageLocatorSkill(navigation, s.worldSearchRadius));
        skills.register(new BiomeLocatorSkill(navigation, s.worldSearchRadius));
        skills.register(new NavigationSkill(navigation));
        skills.register(new OreScannerSkill(new OreScannerSkill.OrePolicy() {
            @Override public boolean enabled() { return settings.get().oreDetectionEnabled; }
            @Override public java.util.Set<String> allowedOres() {
                return java.util.Set.copyOf(settings.get().enabledOres);
            }
            @Override public double radius() { return settings.get().oreScanRadius; }
        }));
        skills.register(new KnowledgeAnswerSkill(knowledge));
        skills.register(new RememberSkill(memory));
        skills.register(new StatusSkill(skills, () ->
            "All systems operational. " + skills.size() + " skills, "
                + memory.countAll() + " memories, " + knowledge.size() + " facts, "
                + network.paramCount() + " neural parameters."));
        skills.route(Intent.SCAN_HOSTILES, "hostile_mob_scanner");
        skills.route(Intent.SCAN_AREA, "area_survey");
        skills.route(Intent.LOCATE_STRUCTURE, "village_locator");
        skills.route(Intent.LOCATE_BIOME, "biome_locator");
        skills.route(Intent.NAVIGATE_GUIDE, "navigation");
        skills.route(Intent.ORE_SCAN, "ore_scanner");
        skills.route(Intent.MOD_QUESTION, "knowledge_answer");
        skills.route(Intent.MACHINE_QUESTION, "knowledge_answer");
        skills.route(Intent.SPELL_QUESTION, "knowledge_answer");
        skills.route(Intent.KNOWLEDGE_QUERY, "knowledge_answer");
        skills.route(Intent.REMEMBER, "memory_keeper");
        skills.route(Intent.FORGET, "memory_keeper");
        skills.route(Intent.MEMORY_QUERY, "memory_keeper");
        skills.route(Intent.STATUS, "status");
        skills.route(Intent.GREETING, "status");
        skills.route(Intent.HELP, "status");
        skills.route(Intent.SKILL_LIST, "status");
        skills.route(Intent.FAREWELL, "status");
        skills.route(Intent.CONFIRM, "status");
        skills.route(Intent.DENY, "status");
    }

    // ---- accessors ----

    public JarvisProfile profile() { return profile; }
    public JarvisMemory memory() { return memory; }
    public KnowledgeGraph knowledge() { return knowledge; }
    public SemanticKnowledge semantics() { return semantics; }
    public SkillRegistry skills() { return skills; }
    public TrainingPipeline training() { return training; }
    public ReinforcementLearner rl() { return rl; }
    public LearningLoop learningLoop() { return learningLoop; }
    public VoicePipeline voice() { return voice; }
    public VoiceReception reception() { return reception; }
    public NavigationMemory navigation() { return navigation; }
    public ContextTracker context() { return context; }
    public IntentEngine intents() { return intents; }
    public JarvisTokenizer tokenizer() { return tokenizer; }
    public JarvisNeuralNetwork network() { return network; }
    public Optimizer optimizer() { return optimizer; }
    public CreateIntegration create() { return createIntegration; }
    public ArsNouveauIntegration ars() { return arsIntegration; }
    public GenericModLearner genericLearner() { return genericLearner; }
    public ModLearningEngine modLearner() { return modLearner; }
    public InferenceEngine inference() { return inference; }

    public void setSettings(Supplier<JarvisSettings> settings) {
        this.settings = settings;
        applyPersonality(settings.get());
    }

    /** True once Minecraft pre-training has been ingested for this player. */
    public boolean bootstrapped() { return bootstrapped; }
    public void setBootstrapped(boolean bootstrapped) { this.bootstrapped = bootstrapped; }

    // ---- main pipeline ----

    /** Async entry: run the full pipeline on the AI pool. */
    public CompletableFuture<JarvisResponse> handleAsync(String text, PlayerSnapshot snapshot) {
        ExecutorService pool = wiring.aiPool();
        return CompletableFuture.supplyAsync(() -> handle(text, snapshot), pool);
    }

    /** Synchronous pipeline (AI thread). Never throws. */
    public JarvisResponse handle(String text, PlayerSnapshot snapshot) {
        long start = System.nanoTime();
        JarvisSettings s = settings.get();
        try {
            if (!s.aiEnabled) {
                hud(HudSignal.FAILED, 2500);
                return new JarvisResponse("Jarvis is currently disabled.", Intent.UNKNOWN, "none", 0, 0);
            }
            if (snapshot != null) worldKnowledge.update(snapshot);
            String input = text == null ? "" : text.trim();
            if (input.isEmpty()) {
                return new JarvisResponse("Yes" + profile.personality().addressSuffix() + "?", Intent.UNKNOWN, "none", 0, 0);
            }
            // strip a leading "jarvis" wake word for understanding (but remember it)
            String core = stripWakeWord(input);

            // 1-2. understand: intent + entities + context resolution
            IntentEngine.ScoredIntent scored = inferenceSafe(core);
            Map<String, Object> fresh = entities.extract(core);
            Map<String, Object> resolved = context.resolve(fresh, scored.intent());
            context.addPlayer(core, scored.intent(), resolved);
            lastIntent = scored.intent().name();

            // short-term memory
            memory.store(MemoryType.SHORT_TERM, "Player: " + core, embedSafe(core), 0.5f, "chat");

            // 3. direct intents
            JarvisResponse direct = handleDirect(scored, resolved, core, snapshot);
            if (direct != null) {
                finish(direct, core, scored, start, true, "direct");
                return direct;
            }

            // 4. reasoning: decompose (internal; only conclusions surface)
            ReasoningEngine.Plan plan = reasoning.decompose(core, scored.intent(), resolved);
            lastGoal = plan.objective();

            // 5-6. skill routing / dynamic creation
            Intent routingIntent = scored.intent();
            if (routingIntent == Intent.FOLLOW_UP) {
                // "What about the closest one?" / "Can you mark it?" inherit the target.
                if (resolved.containsKey("structure")) routingIntent = Intent.LOCATE_STRUCTURE;
                else if (resolved.containsKey("biome")) routingIntent = Intent.LOCATE_BIOME;
                else if (resolved.containsKey("ore")) routingIntent = Intent.ORE_SCAN;
                else if (lastSkill.contains("locator") || lastSkill.contains("navigation")) {
                    routingIntent = Intent.NAVIGATE_GUIDE;
                }
            }
            Skill skill = skills.forIntent(routingIntent).orElse(null);
            final Intent route = routingIntent;
            String skillId = "none";
            SkillResult result;
            WorldAccess world = wiring.world();
            SkillContext ctx = new SkillContext(profile.playerId().toString(), profile.playerName(),
                core, route, resolved, snapshot, world);
            if (skill != null) {
                skillId = skill.id();
                result = skill.run(ctx);
            } else if (s.dynamicSkillsEnabled) {
                DynamicSkillCreator.Creation creation = creator.createFor(route, resolved,
                    List.of("world.scan", "world.locate", "world.guide", "knowledge.query", "memory.store"));
                if (creation.success()) {
                    skillId = creation.skillId();
                    // purple face while Jarvis attempts the new skill
                    hud(HudSignal.SKILL, 3500);
                    Optional<Skill> created = skills.get(skillId);
                    result = created.map(value -> value.run(ctx)).orElseGet(() ->
                        SkillResult.fail(route, SkillResult.map("failure", "creation failed"), "route"));
                } else {
                    result = SkillResult.fail(route, SkillResult.map("failure", "no skill"), "no-route");
                }
            } else {
                result = SkillResult.fail(route, SkillResult.map("failure", "no skill"), "no-route");
            }
            lastSkill = skillId;

            // 7. respond (follow-ups against the active target reuse its frame)
            String spoken;
            double conf = scored.score();
            if ((scored.intent() == Intent.DISTANCE_QUERY || scored.intent() == Intent.TRAVEL_TIME_QUERY
                    || scored.intent() == Intent.FOLLOW_UP || scored.intent() == Intent.NAVIGATE_GUIDE)
                    && navigation.active() != null && !result.success()) {
                SkillContext navCtx = new SkillContext(profile.playerId().toString(), profile.playerName(),
                    core, Intent.NAVIGATE_GUIDE, resolved, snapshot, world);
                SkillResult navResult = new NavigationSkill(navigation).run(navCtx);
                if (navResult.success()) {
                    result = navResult;
                    skillId = "navigation";
                    lastSkill = skillId;
                }
            }
            if (!result.success() && skillId.equals("none")) {
                if (scored.intent() == Intent.SKILL_CREATE) {
                    spoken = DynamicSkillCreator.FAILURE_SENTENCE;
                    hud(HudSignal.FAILED, 4000);
                } else if (scored.intent() == Intent.UNKNOWN) {
                    spoken = responses.compose(Intent.UNKNOWN, Map.of());
                    hud(HudSignal.MAIN, 1200);
                } else {
                    spoken = responses.fallback(scored.intent());
                    hud(HudSignal.MAIN, 1200);
                }
            } else if (!result.success()) {
                Object failure = result.frame().get("failure");
                spoken = failure instanceof String f
                    ? f
                    : responses.fallback(result.responseIntent());
                hud(HudSignal.FAILED, 4000);
            } else if (scored.intent() == Intent.DISTANCE_QUERY && result.frame().containsKey("distance")) {
                spoken = responses.compose(Intent.DISTANCE_QUERY, result.frame());
                hud(HudSignal.MAIN, 1500);
            } else if (scored.intent() == Intent.TRAVEL_TIME_QUERY && result.frame().containsKey("minutes")) {
                spoken = responses.compose(Intent.TRAVEL_TIME_QUERY, result.frame());
                hud(HudSignal.MAIN, 1500);
            } else if (scored.intent() == Intent.FOLLOW_UP && result.responseIntent() == Intent.NAVIGATE_GUIDE) {
                spoken = responses.compose(Intent.TRAVEL_TIME_QUERY, result.frame());
            } else if (scored.intent() == Intent.FOLLOW_UP && result.responseIntent() == Intent.NAVIGATE_GUIDE) {
                Map<String, Object> frame = new LinkedHashMap<>(result.frame());
                frame.put("answer", "Still guiding you to " + frame.getOrDefault("name", "your destination")
                    + ", approximately " + Math.round(((Number) frame.getOrDefault("distance", 0)).doubleValue())
                    + " blocks " + frame.getOrDefault("direction", "away") + ".");
                spoken = responses.compose(Intent.FOLLOW_UP, frame);
                hud(HudSignal.MAIN, 1500);
            } else {
                spoken = responses.compose(result.responseIntent(), result.frame());
                // config-restricted requests (e.g. ore scanning while disabled)
                // show the red face even though the skill "succeeded" in answering
                if (result.responseIntent() == Intent.ORE_SCAN
                        && Boolean.TRUE.equals(result.frame().get("disabled"))) {
                    hud(HudSignal.FAILED, 3500);
                } else {
                    hud(HudSignal.MAIN, 1200);
                }
            }
            lastConfidence = conf;
            JarvisResponse response = new JarvisResponse(spoken, scored.intent(), skillId, conf,
                System.nanoTime() - start);

            // 8. learn + remember
            if (s.learningEnabled) {
                training.learnExchange(core, spoken);
                training.trainAsync();
                float[] vec = embedSafe(core);
                memory.store(MemoryType.EPISODIC,
                    "Asked '" + truncate(core) + "' -> '" + truncate(spoken) + "'", vec, 0.4f, "exchange");
                rl.outcome("skill:" + skillId, result.success());
                learningLoop.record(core, skillId, result.note(), result.success(),
                    result.success() ? 0.6f : -0.5f, scored.intent().name());
                // reinforce intent recognizer on clear successes
                if (result.success() && scored.score() > 0.6) {
                    try {
                        intents.reinforce(core, scored.intent(), scored.intent());
                    } catch (Exception ignored) {}
                }
            }
            context.addJarvis(spoken);
            memory.store(MemoryType.SHORT_TERM, "Jarvis: " + spoken, embedSafe(spoken), 0.5f, "chat");
            profile.markExchange();
            wiring.onResponse().accept(profile.playerId(), response);
            lastInferenceNanos = System.nanoTime() - start;
            return response;
        } catch (Exception e) {
            String safe = responses.fallback(Intent.UNKNOWN);
            return new JarvisResponse(safe, Intent.UNKNOWN, "none", 0, System.nanoTime() - start);
        }
    }

    private JarvisResponse handleDirect(IntentEngine.ScoredIntent scored, Map<String, Object> resolved,
                                        String core, PlayerSnapshot snapshot) {
        Intent intent = scored.intent();
        switch (intent) {
            case MUTE -> {
                profile.setMuted(true);
                String t = responses.compose(Intent.MUTE, Map.of());
                context.addJarvis(t);
                return new JarvisResponse(t, intent, "direct", 1, 0);
            }
            case UNMUTE -> {
                profile.setMuted(false);
                String t = responses.compose(Intent.UNMUTE, Map.of());
                context.addJarvis(t);
                return new JarvisResponse(t, intent, "direct", 1, 0);
            }
            case VOICE_CONFIG -> {
                applyVoiceRequest(core);
                String t = responses.compose(Intent.VOICE_CONFIG, Map.of());
                context.addJarvis(t);
                wiring.save().run();
                return new JarvisResponse(t, intent, "direct", 1, 0);
            }
            case MESSAGE_PLAYER -> {
                Object target = resolved.get("target_player");
                String to = target instanceof String s ? s : guessName(core);
                String body = stripTellPrefix(core, to);
                JarvisNetwork.Receipt receipt = wiring.network().send(
                    profile.playerId(), profile.playerName(), to, body, 0);
                String t = receipt.ok()
                    ? responses.compose(Intent.MESSAGE_PLAYER, Map.of("target", to))
                    : receipt.note();
                if (!receipt.ok()) hud(HudSignal.FAILED, 3000);
                else hud(HudSignal.MAIN, 1200);
                context.addJarvis(t);
                return new JarvisResponse(t, intent, "direct", 0.9, 0);
            }
            case DISTANCE_QUERY, TRAVEL_TIME_QUERY, FOLLOW_UP -> {
                if (navigation.active() != null) return null; // let skill path answer
                String t = responses.fallback(intent);
                context.addJarvis(t);
                return new JarvisResponse(t, intent, "direct", 0.7, 0);
            }
            default -> {}
        }
        // status/greeting/help/remember/forget/memory/skill-list run through their skills
        if (intent == Intent.STATUS || intent == Intent.GREETING || intent == Intent.HELP
                || intent == Intent.SKILL_LIST || intent == Intent.FAREWELL
                || intent == Intent.CONFIRM || intent == Intent.DENY
                || intent == Intent.REMEMBER || intent == Intent.FORGET || intent == Intent.MEMORY_QUERY) {
            Skill skill = skills.forIntent(intent).orElse(null);
            if (skill != null) {
                SkillContext ctx = new SkillContext(profile.playerId().toString(), profile.playerName(),
                    core, intent, resolved, snapshot, wiring.world());
                SkillResult r = skill.run(ctx);
                String t = responses.compose(r.responseIntent(), r.frame());
                lastSkill = skill.id();
                lastConfidence = scored.score();
                context.addJarvis(t);
                JarvisResponse resp = new JarvisResponse(t, intent, skill.id(), scored.score(), 0);
                finish(resp, core, scored, System.nanoTime(), r.success(), skill.id());
                return resp;
            }
        }
        return null;
    }

    private void finish(JarvisResponse resp, String core, IntentEngine.ScoredIntent scored,
                        long start, boolean success, String skillId) {
        JarvisSettings s = settings.get();
        lastConfidence = resp.confidence();
        lastInferenceNanos = System.nanoTime() - start;
        if (s.learningEnabled) {
            training.learnExchange(core, resp.text());
            float[] vec = embedSafe(core);
            memory.store(MemoryType.EPISODIC,
                "Asked '" + truncate(core) + "' -> '" + truncate(resp.text()) + "'", vec, 0.4f, "exchange");
            rl.outcome("skill:" + skillId, success);
            profile.markExchange();
        }
        memory.store(MemoryType.SHORT_TERM, "Jarvis: " + resp.text(), embedSafe(resp.text()), 0.5f, "chat");
        wiring.onResponse().accept(profile.playerId(), resp);
    }

    private IntentEngine.ScoredIntent inferenceSafe(String core) {
        String key = "intent:" + core;
        float[] cached = attentionCache.get(key);
        try {
            return inference.infer(() -> intents.recognize(core),
                new IntentEngine.ScoredIntent(Intent.UNKNOWN, 0, 0, 0));
        } catch (Exception e) {
            return new IntentEngine.ScoredIntent(Intent.UNKNOWN, 0, 0, 0);
        }
    }

    private float[] embedSafe(String text) {
        try {
            return embedder.embed(text);
        } catch (Exception e) {
            return new float[network.dim()];
        }
    }

    private void handleTranscript(String text) {
        // voice transcripts flow through the same pipeline (fire and forget)
        handleAsync(text, worldKnowledge.last());
    }

    private void applyVoiceRequest(String core) {
        String l = core.toLowerCase();
        if (l.contains("faster")) voiceProfile.setSpeechRate(voiceProfile.speechRate() * 0.85f);
        if (l.contains("slower")) voiceProfile.setSpeechRate(voiceProfile.speechRate() * 1.15f);
        if (l.contains("higher")) voiceProfile.setBasePitchHz(voiceProfile.basePitchHz() + 12);
        if (l.contains("lower") || l.contains("deeper")) voiceProfile.setBasePitchHz(voiceProfile.basePitchHz() - 12);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{2,3})\\s*hz").matcher(l);
        if (m.find()) {
            try {
                voiceProfile.setBasePitchHz(Float.parseFloat(m.group(1)));
            } catch (NumberFormatException ignored) {}
        }
    }

    // ---- observations / learning API ----

    public void observe(String eventType, Map<String, String> data) {
        JarvisSettings s = settings.get();
        if (!s.learningEnabled) return;
        observationEngine.observe(eventType, data);
        Map<String, Object> ctx = Map.of("player", profile.playerName());
        for (IModIntegration integration : wiring.integrations()) {
            try {
                integration.onObservation(eventType, data, ctx);
            } catch (Exception ignored) {}
        }
        // Built-in learning integrations observe everything too; their facts
        // stay in this player's private knowledge.
        try {
            createIntegration.onObservation(eventType, data, ctx);
        } catch (Exception ignored) {}
        try {
            arsIntegration.onObservation(eventType, data, ctx);
        } catch (Exception ignored) {}
        if (eventType.equals("tooltip") && s.modLearningEnabled) {
            genericLearner.learnTooltip(data.getOrDefault("item", ""), data.getOrDefault("line", ""));
        }
    }

    /** Player-facing skill request: try dynamic creation and report. */
    public String requestSkill(String description) {
        Map<String, Object> ents = entities.extract(description);
        IntentEngine.ScoredIntent scored = intents.recognize(description);
        DynamicSkillCreator.Creation c = creator.createFor(
            scored.intent() == Intent.UNKNOWN ? Intent.SKILL_CREATE : scored.intent(), ents,
            List.of("world.scan", "world.locate", "world.guide", "knowledge.query", "memory.store"));
        if (c.success()) return c.message();
        return DynamicSkillCreator.FAILURE_SENTENCE;
    }

    /** Speak proactively (API/delivery path). */
    public void speak(String text) {
        wiring.chat().accept(profile.playerId(), text);
        wiring.speech().accept(profile.playerId(), text);
    }

    public ProsodyEngine.Emotion emotionFor(String text) {
        String l = text.toLowerCase();
        if (l.contains("danger") || l.contains("behind you") || l.contains("hostile")) {
            return ProsodyEngine.Emotion.CONCERNED;
        }
        if (l.contains("sorry") || l.contains("afraid")) return ProsodyEngine.Emotion.APOLOGETIC;
        if (l.contains("located") || l.contains("found") || l.contains("complete")) {
            return ProsodyEngine.Emotion.PLEASED;
        }
        return ProsodyEngine.Emotion.NEUTRAL;
    }

    /** Push a portrait signal to the player's client (best-effort, never throws). */
    private void hud(String mode, int pulseMillis) {
        try {
            wiring.hud().accept(profile.playerId(), new HudSignal(mode, pulseMillis));
        } catch (Exception ignored) {}
    }

    // ---- persistence ----

    public void save(PersistenceManager pm) {
        try {
            pm.save(this);
        } catch (Exception ignored) {}
    }

    // ---- debug ----

    public Map<String, Object> debugInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("player", profile.playerName());
        m.put("intent", lastIntent);
        m.put("skill", lastSkill);
        m.put("goal", lastGoal);
        m.put("confidence", String.format("%.3f", lastConfidence));
        m.put("inferenceNs", lastInferenceNanos);
        m.put("worldNs", lastWorldNanos);
        m.put("params", network.paramCount());
        m.put("vocab", vocabulary.size());
        m.put("memories", memory.countAll());
        m.put("facts", knowledge.size());
        m.put("trainSteps", training.steps());
        m.put("trainLoss", training.lastLoss());
        m.put("rlPolicies", rl.snapshot().size());
        m.put("learningCycles", learningLoop.recent(1).size());
        return m;
    }

    public void recordWorldNanos(long nanos) {
        lastWorldNanos = nanos;
    }

    // ---- helpers ----

    private static String stripWakeWord(String input) {
        String t = input.trim();
        if (t.toLowerCase().startsWith("jarvis,")) return t.substring(7).trim();
        if (t.toLowerCase().startsWith("jarvis ")) return t.substring(7).trim();
        if (t.equalsIgnoreCase("jarvis")) return "hello";
        return t;
    }

    private static String truncate(String s) {
        return s.length() > 140 ? s.substring(0, 140) : s;
    }

    private static String guessName(String core) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("tell\\s+([a-zA-Z0-9_]{2,16})").matcher(core.toLowerCase());
        if (m.find()) return m.group(1);
        return "them";
    }

    private static String stripTellPrefix(String core, String to) {
        String l = core.toLowerCase();
        int idx = l.indexOf("that ");
        if (idx >= 0) return core.substring(idx + 5).trim();
        idx = l.indexOf(to.toLowerCase());
        if (idx >= 0) {
            String rest = core.substring(idx + to.length()).trim();
            rest = rest.replaceFirst("(?i)^(that|to|please)\\s+", "");
            return rest.isEmpty() ? rest : rest;
        }
        return core;
    }
}
