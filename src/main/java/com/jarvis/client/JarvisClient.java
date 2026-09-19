package com.jarvis.client;

import com.jarvis.voice.AudioRenderer;
import com.jarvis.voice.VoiceMemory;
import com.jarvis.voice.VoicePipeline;
import com.jarvis.voice.VoiceProfile;
import com.jarvis.world.OreHit;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client entrypoint: registers highlight rendering and hosts the local voice
 * pipeline (synthesizes Jarvis speech PCM on the client and plays it).
 */
public final class JarvisClient {
    private static volatile VoicePipeline voice;
    private static volatile java.util.concurrent.ExecutorService voiceThreads;
    private static final JarvisHudState HUD_STATE = new JarvisHudState();
    private static final JarvisHudConfig HUD_CONFIG = new JarvisHudConfig();

    private JarvisClient() {}

    public static void init(IEventBus modBus) {
        voiceThreads = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jarvis-client-voice");
            t.setDaemon(true);
            return t;
        });
        NeoForge.EVENT_BUS.register(HighlightRenderer.class);
        NeoForge.EVENT_BUS.register(JarvisHudOverlay.class);
        try {
            HUD_CONFIG.load(com.jarvis.config.JarvisConfig.CLIENT.hudEnabled.get(),
                com.jarvis.config.JarvisConfig.CLIENT.hudPosition.get(),
                com.jarvis.config.JarvisConfig.CLIENT.hudSize.get());
        } catch (Exception ignored) {}
    }

    public static JarvisHudState hudState() {
        return HUD_STATE;
    }

    public static JarvisHudConfig hudConfig() {
        return HUD_CONFIG;
    }

    static VoicePipeline pipeline() {
        if (voice == null) {
            synchronized (JarvisClient.class) {
                if (voice == null) {
                    voice = new VoicePipeline(new VoiceProfile(), new VoiceMemory(32));
                }
            }
        }
        return voice;
    }

    static void speakAsync(String text) {
        if (voiceThreads == null || text == null || text.isBlank()) return;
        // portrait pulses while Jarvis speaks (duration scales with the line)
        try {
            int millis = Math.max(1500, Math.min(8000, trimForSpeech(text).length() * 55));
            HUD_STATE.pulse(millis, System.currentTimeMillis());
        } catch (Exception ignored) {}
        voiceThreads.submit(() -> {
            try {
                var spoken = pipeline().speak(trimForSpeech(text));
                if (!Minecraft.getInstance().isPaused()) {
                    AudioRenderer.play(spoken.pcm());
                }
            } catch (Exception ignored) {}
        });
    }

    private static String trimForSpeech(String text) {
        String t = text.replaceAll("^\\[Jarvis\\]\\s*", "");
        return t.length() > 400 ? t.substring(0, 400) : t;
    }
}
