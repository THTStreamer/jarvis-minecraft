package com.jarvis.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Effective portrait settings on the client: jarvis-client.toml defaults
 * overlaid with the in-game GUI choices stored in config/jarvis-hud.properties.
 */
public final class JarvisHudConfig {
    private volatile boolean enabled = true;
    private volatile HudAnchor.Corner corner = HudAnchor.Corner.TOP_LEFT;
    private volatile int size = 64;

    public boolean enabled() { return enabled; }
    public HudAnchor.Corner corner() { return corner; }
    public int size() { return size; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setCorner(HudAnchor.Corner corner) { this.corner = corner; }
    public void setSize(int size) { this.size = Math.max(32, Math.min(128, size)); }

    /** Load: toml defaults first, then the GUI properties override. */
    public void load(boolean tomlEnabled, String tomlPosition, int tomlSize) {
        this.enabled = tomlEnabled;
        this.corner = HudAnchor.parse(tomlPosition);
        setSize(tomlSize);
        Path file = file();
        if (!Files.exists(file)) return;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
            String en = props.getProperty("enabled");
            if (en != null) this.enabled = Boolean.parseBoolean(en.trim());
            String pos = props.getProperty("position");
            if (pos != null) this.corner = HudAnchor.parse(pos);
            String size = props.getProperty("size");
            if (size != null) {
                try {
                    setSize(Integer.parseInt(size.trim()));
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    public void save() {
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            Properties props = new Properties();
            props.setProperty("enabled", Boolean.toString(enabled));
            props.setProperty("position", corner.name());
            props.setProperty("size", Integer.toString(size));
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Jarvis portrait HUD (written by the in-game GUI)");
            }
        } catch (IOException ignored) {}
    }

    private static Path file() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve("jarvis-hud.properties");
        } catch (Exception e) {
            return Path.of("config", "jarvis-hud.properties");
        }
    }
}
