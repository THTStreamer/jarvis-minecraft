package com.jarvis.client;

import com.jarvis.world.OreHit;
import java.util.List;
import net.minecraft.client.Minecraft;

/**
 * Dist-safe client handlers invoked from the payload layer. All calls hop to
 * the client thread before touching Minecraft.
 */
public final class ClientHandlers {
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private ClientHandlers() {}

    public static void onSpeech(String text) {
        LOG.debug("[Jarvis] Speech payload received ({} chars)", text == null ? 0 : text.length());
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> JarvisClient.speakAsync(text));
    }

    public static void onOreHighlights(List<OreHit> hits, long expiresAt) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> HighlightRenderer.setOres(hits, expiresAt));
    }

    public static void onGuide(int x, int y, int z, String label, boolean clear) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (clear) HighlightRenderer.clearGuide();
            else HighlightRenderer.setGuide(x, y, z, label);
        });
    }

    public static void onUi(String screen) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (screen.equals("debug")) {
                mc.setScreen(new JarvisDebugScreen());
            } else if (screen.equals("hud")) {
                mc.setScreen(new JarvisHudScreen());
            } else {
                mc.setScreen(new JarvisScreen());
            }
        });
    }

    public static void onHud(String mode, int pulseMillis) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try {
                JarvisClient.hudState().signal(
                    new com.jarvis.network.HudSignal(mode, pulseMillis), System.currentTimeMillis());
            } catch (Exception ignored) {}
        });
    }
}
