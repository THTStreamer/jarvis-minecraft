package com.jarvis.client;

import com.jarvis.voice.svc.VoiceIntegration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Polished Jarvis status interface: AI/voice state, quick actions and
 * learned-state summary. Buttons issue /jarvis commands so every action
 * flows through the real server-side pipeline.
 */
public class JarvisScreen extends Screen {
    private final List<String> lines = new ArrayList<>();

    public JarvisScreen() {
        super(Component.literal("Jarvis"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new JarvisScreen());
    }

    @Override
    protected void init() {
        lines.clear();
        lines.add("J A R V I S  -  personal AI companion");
        lines.add("");
        lines.add("Voice engine: built-in parametric synthesis (British assistant profile)");
        lines.add("Voice chat link: " + (VoiceIntegration.available() ? "CONNECTED" : "not installed (optional)"));
        lines.add("Speech: server text + client-side voice playback");
        lines.add("");
        lines.add("Talk: type \"Jarvis, ...\" in chat, or use /jarvis ask <message>.");
        lines.add("Examples: \"Jarvis, scan the area.\"  \"Jarvis, find me a village.\"");

        int y = this.height / 2 - 60;
        int cx = this.width / 2 - 100;
        addButton(cx, y, "Scan area", "jarvis scan");
        addButton(cx, y + 24, "Find village", "jarvis locate village");
        addButton(cx, y + 48, "Status", "jarvis status");
        addButton(cx, y + 72, "My skills", "jarvis skills");
        addButton(cx, y + 96, "Mute / unmute", "jarvis mute");
        addButton(cx, y + 120, "Portrait position", null);
        addButton(cx, y + 144, "Debug view", "debugscreen");
        addButton(cx, y + 168, "Close", "close");
    }

    private void addButton(int x, int y, String label, String command) {
        this.addRenderableWidget(Button.builder(Component.literal(label), btn -> {
            Minecraft mc = Minecraft.getInstance();
            if (command == null) {
                mc.setScreen(new JarvisHudScreen());
            } else if (command.equals("debugscreen")) {
                mc.setScreen(new JarvisDebugScreen());
            } else if (command.equals("close")) {
                mc.setScreen(null);
            } else if (mc.player != null) {
                mc.player.connection.sendCommand(command);
            }
        }).bounds(x, y, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, "JARVIS", this.width / 2, 24, 0x7FD4FF);
        int y = 48;
        for (String line : lines) {
            gfx.drawCenteredString(this.font, line, this.width / 2, y, 0xD8E6F0);
            y += 11;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
