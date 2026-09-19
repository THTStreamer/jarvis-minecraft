package com.jarvis.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Developer view: exposes client-observable pipeline state and one-click
 * server debug/test commands. Server-side intent/skill/memory detail is
 * printed via /jarvis debug (chat) and mirrored here as guidance.
 */
public class JarvisDebugScreen extends Screen {
    public JarvisDebugScreen() {
        super(Component.literal("Jarvis Debug"));
    }

    @Override
    protected void init() {
        int y = this.height - 60;
        int cx = this.width / 2 - 100;
        this.addRenderableWidget(Button.builder(Component.literal("Dump server debug to chat"), btn -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.connection.sendCommand("jarvis debug");
        }).bounds(cx, y, 200, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Run self-tests"), btn -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.connection.sendCommand("jarvis test skills");
        }).bounds(cx, y + 24, 200, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Back"), btn -> {
            Minecraft.getInstance().setScreen(new JarvisScreen());
        }).bounds(cx, y + 48, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, "JARVIS DEBUG", this.width / 2, 20, 0xFFC866);
        String[] lines = {
            "Server-side pipeline state lives on the server thread.",
            "Use the buttons below (or /jarvis debug) to inspect:",
            "current intent, entities, goal, skill + confidence,",
            "relevant memories/knowledge, inference/train/voice/scan timings.",
            "",
            "Client state:",
            "- voice playback: local PCM synth, async",
            "- ore highlights: blue outlines (30s), guide: gold",
            "- voice chat link: " + (com.jarvis.voice.svc.VoiceIntegration.available() ? "up" : "down"),
        };
        int y = 44;
        for (String line : lines) {
            gfx.drawCenteredString(this.font, line, this.width / 2, y, 0xC8D4DC);
            y += 12;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
