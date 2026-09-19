package com.jarvis.core;

import com.jarvis.ai.inference.AIWorkerPool;
import com.jarvis.api.IModIntegration;
import com.jarvis.config.JarvisSettings;
import com.jarvis.mods.ModDiscovery;
import com.jarvis.mods.ModInfo;
import com.jarvis.mods.RegistryView;
import com.jarvis.network.JarvisNetwork;
import com.jarvis.player.JarvisProfile;
import com.jarvis.skills.WorldAccess;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Server-wide Jarvis service: owns per-player instances, shared worker pools,
 * the Jarvis network, integrations and persistence. Created once by the mod
 * entrypoint; {@link JarvisAPI} reads through here.
 */
public final class JarvisService {
    private static volatile JarvisService INSTANCE;

    /** Wiring supplied by the Minecraft glue layer. */
    public record Wiring(
        Path dataRoot,
        AIWorkerPool pools,
        WorldAccess world,
        Supplier<JarvisSettings> settings,
        BiConsumer<UUID, String> chat,
        BiConsumer<UUID, String> speech,
        java.util.function.Consumer<UUID> saveOne,
        BiConsumer<UUID, JarvisInstance.JarvisResponse> onResponse,
        JarvisNetwork network,
        List<IModIntegration> integrations,
        BiConsumer<UUID, com.jarvis.network.HudSignal> hud
    ) {
        public java.util.concurrent.ExecutorService aiPool() { return pools.ai(); }
        public java.util.concurrent.ExecutorService trainingPool() { return pools.training(); }
        public java.util.concurrent.ExecutorService voicePool() { return pools.voice(); }
        public Runnable save() { return () -> {}; }
    }

    private final Wiring wiring;
    private final Map<UUID, JarvisInstance> instances = new ConcurrentHashMap<>();
    private final ModDiscovery discovery;
    private final RegistryView registryView;
    private final com.jarvis.persistence.PersistenceManager persistence;
    private volatile BiConsumer<UUID, Integer> speechNotice = (id, streak) -> {};

    private JarvisService(Wiring wiring, Supplier<List<ModInfo>> modSupplier, RegistryView registryView) {
        this.wiring = wiring;
        this.discovery = new ModDiscovery(modSupplier);
        this.registryView = registryView;
        this.persistence = new com.jarvis.persistence.PersistenceManager(wiring.dataRoot());
    }

    public static synchronized void init(Wiring wiring, Supplier<List<ModInfo>> modSupplier,
                                         RegistryView registryView) {
        shutdown();
        INSTANCE = new JarvisService(wiring, modSupplier, registryView);
    }

    public static JarvisService get() {
        return INSTANCE;
    }

    public static synchronized void shutdown() {
        if (INSTANCE != null) {
            INSTANCE.saveAll();
            INSTANCE = null;
        }
    }

    public Wiring wiring() { return wiring; }
    public ModDiscovery discovery() { return discovery; }
    public RegistryView registryView() { return registryView; }
    public JarvisNetwork network() { return wiring.network(); }
    public com.jarvis.persistence.PersistenceManager persistence() { return persistence; }

    public JarvisInstance getOrCreate(UUID playerId, String playerName) {
        return instances.computeIfAbsent(playerId, id -> {
            JarvisProfile profile = new JarvisProfile(id, playerName);
            JarvisInstance inst = new JarvisInstance(profile, wiring, wiring.settings());
            inst.reception().setSpeechNoticedHook((uuid, streak) -> {
                try {
                    speechNotice.accept(uuid, streak);
                } catch (Exception ignored) {}
            });
            try {
                persistence.load(inst);
            } catch (Exception ignored) {}
            // refresh display name each login
            inst.profile().setPlayerName(playerName);
            // notify integrations of present mods (per-player knowledge isolation:
            // detection facts go to this instance only)
            for (ModInfo mod : discovery.cached()) {
                for (IModIntegration integration : wiring.integrations()) {
                    if (integration.modId().equals(mod.id())) {
                        try {
                            integration.onModDetected(Map.of("player", playerName));
                        } catch (Exception ignored) {}
                    }
                }
            }
            // Minecraft pre-training (once per player) covers the curated corpus
            // plus registry facts; the per-login learner below is skipped then.
            runBootstrap(inst);
            // learn installed mods on first sight (skipped once bootstrapped)
            if (wiring.settings().get().modLearningEnabled && !inst.bootstrapped()) {
                for (ModInfo mod : discovery.cached()) {
                    try {
                        inst.modLearner().learnMod(mod, registryView);
                    } catch (Exception ignored) {}
                }
            }
            int drained = wiring.network().drain(playerId);
            if (drained > 0) {
                wiring.chat().accept(playerId,
                    "Welcome back, " + profile.personality().address() + ". I delivered "
                        + drained + " pending message" + (drained == 1 ? "" : "s") + ".");
            }
            return inst;
        });
    }

    public Optional<JarvisInstance> instance(UUID playerId) {
        return Optional.ofNullable(instances.get(playerId));
    }

    /**
     * First-login pre-training: ingest the curated Minecraft corpus plus a
     * registry sweep of installed mods into this player's private knowledge,
     * vocabulary and training queue, then run a bounded neural burst
     * off-thread so the network itself carries Minecraft priors. Runs once;
     * the flag is persisted with the player data.
     */
    public void runBootstrap(JarvisInstance inst) {
        if (inst.bootstrapped()) return;
        if (!wiring.settings().get().bootstrapEnabled) return;
        if (!wiring.settings().get().learningEnabled) return;
        inst.setBootstrapped(true);
        wiring.trainingPool().submit(() -> {
            try {
                com.jarvis.bootstrap.ModBootstrap sweep = new com.jarvis.bootstrap.ModBootstrap(
                    inst.knowledge(), inst.semantics(), inst.training(), inst.tokenizer());
                com.jarvis.bootstrap.ModBootstrap.Report report =
                    sweep.run(discovery.cached(), registryView, 120);
                for (int i = 0; i < 30; i++) {
                    inst.training().trainAsync();
                    try {
                        Thread.sleep(120);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                try {
                    persistence.save(inst);
                } catch (Exception ignored) {}
            } catch (Exception ignored) {}
        });
    }

    public void remove(UUID playerId) {
        JarvisInstance inst = instances.remove(playerId);
        if (inst != null) {
            try {
                persistence.save(inst);
            } catch (Exception ignored) {}
        }
    }

    public void saveAll() {
        for (JarvisInstance inst : new ArrayList<>(instances.values())) {
            try {
                persistence.save(inst);
            } catch (Exception ignored) {}
        }
    }

    public List<JarvisInstance> loaded() {
        return new ArrayList<>(instances.values());
    }

    public void registerIntegration(IModIntegration integration) {
        wiring.integrations().add(integration);
    }

    /** Hook fired when sustained mic speech arrives with no transcription. */
    public void setSpeechNotice(BiConsumer<UUID, Integer> hook) {
        this.speechNotice = hook;
    }

    public Supplier<JarvisSettings> settings() {
        return wiring.settings();
    }
}
