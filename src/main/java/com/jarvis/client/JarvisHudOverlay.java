package com.jarvis.client;

import com.jarvis.network.HudSignal;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.slf4j.Logger;

/**
 * Renders the Jarvis portrait over the HUD. Small by default, uniformly
 * scaled (never cropped), static at rest and gently pulsing while Jarvis is
 * speaking or working. The world underneath is never modified.
 *
 * <p>Diagnostics: the first successful blit is logged once per session, and
 * the first failure is logged as a warning (failures never crash the game).
 * Run {@code /jarvis hud preview} to cycle all three faces on demand.
 */
public final class JarvisHudOverlay {
    private static final Logger LOG = LogUtils.getLogger();

    public static final ResourceLocation TEX_MAIN =
        ResourceLocation.fromNamespaceAndPath("jarvis", "textures/hud/jarvis_main.png");
    public static final ResourceLocation TEX_SKILL =
        ResourceLocation.fromNamespaceAndPath("jarvis", "textures/hud/jarvis_skill.png");
    public static final ResourceLocation TEX_FAILED =
        ResourceLocation.fromNamespaceAndPath("jarvis", "textures/hud/jarvis_failed.png");

    private static final int TEX_SIZE = 512;
    private static final int MARGIN = 8;
    private static volatile boolean loggedFirstRender;
    private static volatile boolean loggedFirstError;

    private JarvisHudOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        try {
            render(event);
        } catch (Exception e) {
            if (!loggedFirstError) {
                loggedFirstError = true;
                LOG.warn("[Jarvis] Portrait HUD failed to render (hiding further errors): {}", String.valueOf(e));
            }
        }
    }

    private static void render(RenderGuiEvent.Post event) {
        JarvisHudConfig config = JarvisClient.hudConfig();
        if (config == null || !config.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.screen instanceof JarvisScreen || mc.screen instanceof JarvisDebugScreen) {
            // hide while the other Jarvis config screens are open; the portrait
            // screen itself keeps the overlay visible as a live preview
            return;
        }

        GuiGraphics gfx = event.getGuiGraphics();
        // screen size comes from the graphics being drawn, so GUI scale,
        // window resizes and tiny windows are always honored
        int w = gfx.guiWidth();
        int h = gfx.guiHeight();
        if (w <= 0 || h <= 0) return;

        JarvisHudState state = JarvisClient.hudState();
        long now = System.currentTimeMillis();
        String mode = state.current(now);
        ResourceLocation tex = switch (mode) {
            case HudSignal.SKILL -> TEX_SKILL;
            case HudSignal.FAILED -> TEX_FAILED;
            default -> TEX_MAIN;
        };

        // clamp to the live screen so the portrait can never end up off-screen
        int size = Math.max(16, Math.min(config.size(), Math.min(w / 2, h / 2)));
        int[] center = HudAnchor.center(config.corner(), size, w, h, MARGIN);

        int draw = size;
        if (state.pulsing(now)) {
            double wave = Math.sin(now / 160.0);
            draw = size + (int) Math.round(size * 0.09 * (0.5 + 0.5 * wave));
        }
        int dx = center[0] - draw / 2;
        int dy = center[1] - draw / 2;

        gfx.pose().pushPose();
        try {
            gfx.blit(tex, dx, dy, 0, 0, draw, draw, TEX_SIZE, TEX_SIZE);
        } finally {
            gfx.pose().popPose();
        }
        gfx.flush();

        if (!loggedFirstRender) {
            loggedFirstRender = true;
            LOG.info("[Jarvis] Portrait HUD rendering: face={} size={} corner={} screen={}x{}",
                mode, size, config.corner(), w, h);
        }
    }
}
