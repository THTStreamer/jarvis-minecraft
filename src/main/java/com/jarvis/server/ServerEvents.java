package com.jarvis.server;

import com.jarvis.api.IModIntegration;
import com.jarvis.config.JarvisConfig;
import com.jarvis.core.JarvisInstance;
import com.jarvis.core.JarvisService;
import com.jarvis.network.JarvisNetwork;
import com.jarvis.voice.svc.VoiceIntegration;
import com.jarvis.world.PlayerSnapshot;
import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

/**
 * Server-side event glue: chat wake-word, login/logout, commands, autosave
 * and the observation hooks that feed the learning engines.
 */
public final class ServerEvents {
    private static final Logger LOG = LogUtils.getLogger();
    private static Runnable serviceFactory = () -> {};
    private static volatile boolean initialized;
    private static com.jarvis.ai.inference.AIWorkerPool pools;
    private static java.util.Timer autosave;

    private ServerEvents() {}

    public static void setServiceFactory(Runnable factory) {
        serviceFactory = factory;
    }

    public static void finishInit() {
        initialized = true;
    }

    public static JarvisService.Wiring wiring() {
        pools = com.jarvis.ai.inference.AIWorkerPool.defaults();
        Path dataRoot = FMLPaths.GAMEDIR.get().resolve("jarvis");
        NeoForgeWorldAccess world = new NeoForgeWorldAccess();
        JarvisNetwork network = new JarvisNetwork();
        List<IModIntegration> integrations = new ArrayList<>();
        return new JarvisService.Wiring(
            dataRoot,
            pools,
            world,
            JarvisConfig::snapshot,
            ServerEvents::sendChat,
            ServerEvents::sendSpeech,
            uuid -> {},
            (uuid, response) -> {},
            network,
            integrations,
            ServerEvents::sendHud);
    }

    private static synchronized void ensureInit() {
        if (initialized || JarvisService.get() != null) return;
        try {
            serviceFactory.run();
            JarvisService svc = JarvisService.get();
            if (svc != null) {
                wireNetwork(svc);
                wireVoice(svc);
                startAutosave();
            }
        } catch (Exception e) {
            LOG.error("[Jarvis] Failed to initialize service", e);
        }
    }

    private static void wireNetwork(JarvisService svc) {
        svc.network().setEnabled(JarvisConfig.snapshot().jarvisToJarvisEnabled);
        svc.network().setResolver(
            name -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null) return null;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.getGameProfile().getName().equalsIgnoreCase(name)) {
                        return p.getUUID();
                    }
                }
                return null;
            },
            uuid -> {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                return server != null && server.getPlayerList().getPlayer(uuid) != null;
            },
            (uuid, text) -> sendChat(uuid, text));
    }

    private static void wireVoice(JarvisService svc) {
        VoiceIntegration.setMicrophoneHook((uuid, opus) -> {
            svc.instance(uuid).ifPresent(inst -> {
                try {
                    inst.reception().feed(uuid, opus, false);
                } catch (Exception ignored) {}
            });
        });
        // sustained speech with no transcription available: answer honestly
        // instead of silence (wired per instance at creation below)
        svc.setSpeechNotice((uuid, streak) -> {
            sendChat(uuid, "I can hear you speaking, sir, but my speech recognition "
                + "is still in training and I cannot make out words yet. "
                + "Please type your request after \"Jarvis,\" and I will act at once.");
            LOG.info("[Jarvis] Speech noticed without transcription; sent guidance nudge.");
        });
    }

    private static void startAutosave() {
        if (autosave != null) return;
        autosave = new java.util.Timer("jarvis-autosave", true);
        autosave.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                try {
                    JarvisService svc = JarvisService.get();
                    if (svc != null) svc.saveAll();
                } catch (Exception ignored) {}
            }
        }, 300_000L, 300_000L);
    }

    // ---- messaging ----

    static void sendChat(UUID playerId, String text) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            ServerPlayer p = server.getPlayerList().getPlayer(playerId);
            if (p == null) return;
            String line = text.startsWith("[Jarvis]") ? text : "[Jarvis] " + text;
            if (server.isSameThread()) {
                p.sendSystemMessage(Component.literal(line));
            } else {
                server.execute(() -> p.sendSystemMessage(Component.literal(line)));
            }
        } catch (Exception ignored) {}
    }

    static void sendSpeech(UUID playerId, String text) {        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            ServerPlayer p = server.getPlayerList().getPlayer(playerId);
            if (p == null) return;
            JarvisService svc = JarvisService.get();
            boolean muted = svc != null && svc.instance(playerId).map(i -> i.profile().muted()).orElse(false);
            boolean voiceOn = svc == null || svc.settings().get().voiceEnabled;
            if (!muted && voiceOn) {
                JarvisPayloads.sendSpeech(p, text);
            }
        } catch (Exception ignored) {}
    }

    static void sendHud(UUID playerId, com.jarvis.network.HudSignal signal) {
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            ServerPlayer p = server.getPlayerList().getPlayer(playerId);
            if (p == null) return;
            JarvisPayloads.sendHud(p, signal.mode(), signal.pulseMillis());
        } catch (Exception ignored) {}
    }

    // ---- events ----

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        JarvisCommands.register(event);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ensureInit();
        JarvisService svc = JarvisService.get();
        if (svc == null) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            JarvisInstance inst = svc.getOrCreate(player.getUUID(), player.getGameProfile().getName());
            boolean svcInstalled = ModList.get().isLoaded("voicechat");
            boolean linkActive = com.jarvis.voice.svc.VoiceIntegration.available();
            String voiceNote = svcInstalled
                ? (linkActive ? ", voice link active" : ", voice chat installed (link starting)")
                : "";
            sendChat(player.getUUID(), "Jarvis online" + voiceNote
                + ". Say \"Jarvis\" followed by your request, " + inst.profile().personality().address() + ".");
        } catch (Exception e) {
            LOG.error("[Jarvis] login init failed", e);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        JarvisService svc = JarvisService.get();
        if (svc == null) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                svc.remove(player.getUUID());
            } catch (Exception ignored) {}
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        try {
            JarvisService.shutdown();
        } catch (Exception ignored) {}
        try {
            if (autosave != null) {
                autosave.cancel();
                autosave = null;
            }
            if (pools != null) {
                pools.close();
                pools = null;
            }
        } catch (Exception ignored) {}
        initialized = false;
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ensureInit();
        JarvisService svc = JarvisService.get();
        if (svc == null) return;
        String raw = event.getMessage().getString();
        if (raw == null) return;
        String lower = raw.toLowerCase(Locale.ROOT).trim();
        if (!lower.equals("jarvis") && !lower.startsWith("jarvis,") && !lower.startsWith("jarvis ")) {
            return;
        }
        event.setCanceled(true);
        ServerPlayer player = event.getPlayer();
        UUID id = player.getUUID();
        JarvisInstance inst = svc.getOrCreate(id, player.getGameProfile().getName());
        PlayerSnapshot snapshot = svc.wiring().world().snapshot(id.toString());
        final String message = raw;
        // portrait pulses while Jarvis works on the request
        sendHud(id, new com.jarvis.network.HudSignal(
            com.jarvis.network.HudSignal.MAIN, 5000));
        inst.handleAsync(message, snapshot).thenAccept(response -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            server.execute(() -> {
                sendChat(id, response.text());
                sendSpeech(id, response.text());
                if (svc.settings().get().learningEnabled && svc.settings().get().debugLogging) {
                    LOG.info("[Jarvis] {} intent={} skill={} conf={}",
                        player.getGameProfile().getName(), response.intent(), response.skillId(),
                        String.format("%.2f", response.confidence()));
                }
            });
        });
    }

    // ---- observation hooks ----

    private static Optional<JarvisInstance> instanceFor(net.minecraft.world.entity.Entity entity) {
        JarvisService svc = JarvisService.get();
        if (svc == null || !(entity instanceof ServerPlayer p)) return Optional.empty();
        return svc.instance(p.getUUID());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            var inst = instanceFor(event.getPlayer());
            if (inst.isEmpty()) return;
            String block = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
            inst.get().observe("block_break", Map.of("block", block));
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        try {
            var inst = instanceFor(event.getEntity());
            if (inst.isEmpty()) return;
            String block = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString();
            inst.get().observe("block_place", Map.of("block", block));
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        try {
            var inst = instanceFor(event.getEntity());
            if (inst.isEmpty()) return;
            String block = BuiltInRegistries.BLOCK.getKey(
                event.getLevel().getBlockState(event.getPos()).getBlock()).toString();
            inst.get().observe("gui_open", Map.of("block", block, "gui", block));
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        try {
            var inst = instanceFor(event.getEntity());
            if (inst.isEmpty()) return;
            String item = BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem()).toString();
            inst.get().observe("craft", Map.of("result", item));
        } catch (Exception ignored) {}
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        try {
            var inst = instanceFor(event.getEntity());
            if (inst.isEmpty()) return;
            inst.get().observe("advancement", Map.of("id", event.getAdvancement().id().toString()));
        } catch (Exception ignored) {}
    }
}
