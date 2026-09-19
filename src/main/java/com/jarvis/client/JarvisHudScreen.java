package com.jarvis.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-game portrait controls: move Jarvis between the four screen corners,
 * resize (32–128 px, never cropped), or hide the portrait entirely.
 * Choices apply live and persist to config/jarvis-hud.properties.
 */
public class JarvisHudScreen extends Screen {
    public JarvisHudScreen() {
        super(Component.literal("Jarvis Portrait"));
    }

    private JarvisHudConfig config() {
        return JarvisClient.hudConfig();
    }

    @Override
    protected void init() {
        JarvisHudConfig cfg = config();
        int cx = this.width / 2 - 100;
        int y = 64;
        cornerButton(cx, y, "Top left", HudAnchor.Corner.TOP_LEFT, cfg);
        cornerButton(cx, y + 24, "Top right", HudAnchor.Corner.TOP_RIGHT, cfg);
        cornerButton(cx, y + 48, "Bottom left", HudAnchor.Corner.BOTTOM_LEFT, cfg);
        cornerButton(cx, y + 72, "Bottom right", HudAnchor.Corner.BOTTOM_RIGHT, cfg);

        this.addRenderableWidget(Button.builder(
            Component.literal("Size: " + cfg.size() + " px (click to grow)"), btn -> {
                int next = cfg.size() >= 128 ? 32 : cfg.size() + 16;
                cfg.setSize(next);
                cfg.save();
                btn.setMessage(Component.literal("Size: " + cfg.size() + " px (click to grow)"));
            }).bounds(cx, y + 104, 200, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.literal(cfg.enabled() ? "Portrait: shown (click to hide)"
                : "Portrait: hidden (click to show)"), btn -> {
                cfg.setEnabled(!cfg.enabled());
                cfg.save();
                btn.setMessage(Component.literal(cfg.enabled() ? "Portrait: shown (click to hide)"
                    : "Portrait: hidden (click to show)"));
            }).bounds(cx, y + 128, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> {
            Minecraft.getInstance().setScreen(new JarvisScreen());
        }).bounds(cx, y + 156, 200, 20).build());
    }

    private void cornerButton(int x, int y, String label, HudAnchor.Corner corner, JarvisHudConfig cfg) {
        String marker = cfg.corner() == corner ? "  <" : "";
        this.addRenderableWidget(Button.builder(Component.literal(label + marker), btn -> {
            cfg.setCorner(corner);
            cfg.save();
            this.rebuildWidgets();
        }).bounds(x, y, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, "JARVIS PORTRAIT", this.width / 2, 24, 0x7FD4FF);
        gfx.drawCenteredString(this.font, "Blue: talking / working.  Purple: learning a skill.  Red: failed / restricted.",
            this.width / 2, 40, 0x9DB4C0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
