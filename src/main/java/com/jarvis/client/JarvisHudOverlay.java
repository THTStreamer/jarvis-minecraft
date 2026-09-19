package com.jarvis.client;

import com.jarvis.network.HudSignal;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Renders the Jarvis portrait over the HUD. Small by default, uniformly
 * scaled (never cropped), static at rest and gently pulsing while Jarvis is
 * speaking or working. The world underneath is never modified.
 */
public final class JarvisHudOverlay {
    public static final ResourceLocation TEX_MAIN =
        ResourceLocation.fromNamespaceAndPath("jarvis", "textures/hud/jarvis_main.png");
    public static final ResourceLocation TEX_SKILL =
        ResourceLocation.fromNamespaceAndPath("jarvis", "textures/hud/jarvis_skill.png");
    public static final ResourceLocation TEX_FAILED =
        ResourceLocation.fromNamespaceAndPath("jarvis", "textures/hud/jarvis_failed.png");

    private static final int TEX_SIZE = 512;
    private static final int MARGIN = 8;

    private JarvisHudOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        try {
            JarvisHudConfig config = JarvisClient.hudConfig();
            if (config == null || !config.enabled()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            if (mc.screen != null && !(mc.screen instanceof JarvisHudScreen)) {
                // hide while other Jarvis config screens are open, except the
                // portrait screen itself (live preview)
                if (mc.screen instanceof JarvisScreen || mc.screen instanceof JarvisDebugScreen) return;
            }

            JarvisHudState state = JarvisClient.hudState();
            long now = System.currentTimeMillis();
            String mode = state.current(now);
            ResourceLocation tex = switch (mode) {
                case HudSignal.SKILL -> TEX_SKILL;
                case HudSignal.FAILED -> TEX_FAILED;
                default -> TEX_MAIN;
            };

            int size = config.size();
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            int[] center = HudAnchor.center(config.corner(), size, w, h, MARGIN);

            int draw = size;
            if (state.pulsing(now)) {
                double wave = Math.sin(now / 160.0);
                draw = size + (int) Math.round(size * 0.09 * (0.5 + 0.5 * wave));
            }
            int dx = center[0] - draw / 2;
            int dy = center[1] - draw / 2;

            GuiGraphics gfx = event.getGuiGraphics();
            PoseStack pose = gfx.pose();
            pose.pushPose();
            gfx.blit(tex, dx, dy, 0, 0, draw, draw, TEX_SIZE, TEX_SIZE);
            pose.popPose();
        } catch (Exception ignored) {}
    }
}
