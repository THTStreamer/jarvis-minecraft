package com.jarvis.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/** NeoForge configuration: jarvis-common.toml, jarvis-client.toml, jarvis-server.toml. */
public final class JarvisConfig {
    private JarvisConfig() {}

    public static final class Common {
        public final ModConfigSpec.BooleanValue aiEnabled;
        public final ModConfigSpec.BooleanValue learningEnabled;
        public final ModConfigSpec.BooleanValue modLearningEnabled;
        public final ModConfigSpec.BooleanValue dynamicSkillsEnabled;
        public final ModConfigSpec.BooleanValue jarvisToJarvis;
        public final ModConfigSpec.BooleanValue debugLogging;
        public final ModConfigSpec.ConfigValue<String> addressTitle;
        public final ModConfigSpec.DoubleValue verbosity;
        public final ModConfigSpec.DoubleValue wit;
        public final ModConfigSpec.DoubleValue formality;
        public final ModConfigSpec.IntValue maxVocab;
        public final ModConfigSpec.IntValue dim;
        public final ModConfigSpec.IntValue heads;
        public final ModConfigSpec.IntValue blocks;
        public final ModConfigSpec.IntValue ffnDim;
        public final ModConfigSpec.IntValue trainingBatch;

        public final ModConfigSpec spec;

        public Common() {
            ModConfigSpec.Builder b = new ModConfigSpec.Builder();
            b.push("ai");
            aiEnabled = b.comment("Master switch for the Jarvis AI.").define("enabled", true);
            learningEnabled = b.comment("Learn from conversations, observations and outcomes.")
                .define("learningEnabled", true);
            modLearningEnabled = b.comment("Inspect registries and learn mod mechanics by observation.")
                .define("modLearningEnabled", true);
            dynamicSkillsEnabled = b.comment("Allow Jarvis to compose new sandboxed skills.")
                .define("dynamicSkillsEnabled", true);
            jarvisToJarvis = b.comment("Allow Jarvis-to-Jarvis messages between players.")
                .define("jarvisToJarvis", true);
            debugLogging = b.comment("Verbose AI debug logging.").define("debug", false);
            b.pop();
            b.push("personality");
            addressTitle = b.comment("How Jarvis addresses the player: SIR, MADAM, COMMANDER, BOSS, FRIEND, NONE.")
                .define("addressTitle", "SIR");
            verbosity = b.defineInRange("verbosity", 0.45, 0.0, 1.0);
            wit = b.defineInRange("wit", 0.35, 0.0, 1.0);
            formality = b.defineInRange("formality", 0.8, 0.0, 1.0);
            b.pop();
            b.push("neural");
            maxVocab = b.defineInRange("maxVocab", 2048, 512, 8192);
            dim = b.defineInRange("dim", 64, 32, 256);
            heads = b.defineInRange("heads", 4, 2, 8);
            blocks = b.defineInRange("blocks", 2, 1, 6);
            ffnDim = b.defineInRange("ffnDim", 128, 64, 1024);
            trainingBatch = b.defineInRange("trainingBatch", 8, 1, 64);
            b.pop();
            spec = b.build();
        }
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue voiceEnabled;
        public final ModConfigSpec.DoubleValue speechSpeed;
        public final ModConfigSpec.DoubleValue speechPitch;
        public final ModConfigSpec.BooleanValue requireVoiceChat;
        public final ModConfigSpec.BooleanValue hudEnabled;
        public final ModConfigSpec.ConfigValue<String> hudPosition;
        public final ModConfigSpec.IntValue hudSize;
        public final ModConfigSpec spec;

        public Client() {
            ModConfigSpec.Builder b = new ModConfigSpec.Builder();
            b.push("voice");
            voiceEnabled = b.comment("Enable the built-in Jarvis voice.").define("enabled", true);
            speechSpeed = b.comment("Speech rate multiplier (0.5 fast .. 2.0 slow).")
                .defineInRange("speed", 1.0, 0.5, 2.0);
            speechPitch = b.comment("Base pitch in Hz.").defineInRange("pitch", 112.0, 60.0, 300.0);
            requireVoiceChat = b.comment("If true, voice transport needs Simple Voice Chat; text still works.")
                .define("requireVoiceChat", false);
            b.pop();
            b.push("portrait");
            hudEnabled = b.comment("Show the Jarvis portrait on the HUD (top-left by default).")
                .define("enabled", true);
            hudPosition = b.comment("Portrait corner: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT. " +
                    "The in-game portrait GUI can override this per player.")
                .define("position", "TOP_LEFT");
            hudSize = b.comment("Portrait size in pixels (32-128). Scaled uniformly, never cropped.")
                .defineInRange("size", 64, 32, 128);
            b.pop();
            spec = b.build();
        }
    }

    public static final class Server {
        public final ModConfigSpec.BooleanValue oreDetection;
        public final ModConfigSpec.ConfigValue<List<? extends String>> enabledOres;
        public final ModConfigSpec.DoubleValue oreRadius;
        public final ModConfigSpec.DoubleValue hostileRadius;
        public final ModConfigSpec.IntValue worldSearchRadius;
        public final ModConfigSpec spec;

        public Server() {
            ModConfigSpec.Builder b = new ModConfigSpec.Builder();
            b.push("scanning");
            oreDetection = b.comment("Ore detection is DISABLED by default. Enable explicitly to allow ore scans.")
                .define("oreDetectionEnabled", false);
            enabledOres = b.comment("Only these ores may ever be scanned.")
                .defineListAllowEmpty("enabledOres",
                    List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:ancient_debris"),
                    () -> "", o -> o instanceof String s && s.contains(":"));
            oreRadius = b.defineInRange("oreRadius", 20.0, 4.0, 64.0);
            hostileRadius = b.defineInRange("hostileRadius", 32.0, 8.0, 128.0);
            worldSearchRadius = b.defineInRange("worldSearchRadius", 5000, 512, 20000);
            b.pop();
            spec = b.build();
        }
    }

    public static final Common COMMON = new Common();
    public static final Client CLIENT = new Client();
    public static final Server SERVER = new Server();

    /** Snapshot current spec values into a plain settings object for the core. */
    public static JarvisSettings snapshot() {
        JarvisSettings s = new JarvisSettings();
        s.aiEnabled = COMMON.aiEnabled.get();
        s.learningEnabled = COMMON.learningEnabled.get();
        s.modLearningEnabled = COMMON.modLearningEnabled.get();
        s.dynamicSkillsEnabled = COMMON.dynamicSkillsEnabled.get();
        s.jarvisToJarvisEnabled = COMMON.jarvisToJarvis.get();
        s.debugLogging = COMMON.debugLogging.get();
        s.addressTitle = COMMON.addressTitle.get();
        s.verbosity = COMMON.verbosity.get();
        s.wit = COMMON.wit.get();
        s.formality = COMMON.formality.get();
        s.maxVocab = COMMON.maxVocab.get();
        s.dim = COMMON.dim.get();
        s.heads = COMMON.heads.get();
        s.blocks = COMMON.blocks.get();
        s.ffnDim = COMMON.ffnDim.get();
        s.trainingBatch = COMMON.trainingBatch.get();
        s.voiceEnabled = CLIENT.voiceEnabled.get();
        s.speechSpeed = CLIENT.speechSpeed.get().floatValue();        s.speechPitch = CLIENT.speechPitch.get().floatValue();
        s.requireVoiceChat = CLIENT.requireVoiceChat.get();
        s.hudEnabled = CLIENT.hudEnabled.get();
        s.hudPosition = CLIENT.hudPosition.get();
        s.hudSize = CLIENT.hudSize.get();
        s.oreDetectionEnabled = SERVER.oreDetection.get();
        s.enabledOres = List.copyOf(SERVER.enabledOres.get()).stream().map(String::valueOf).toList();
        s.enabledOres = new java.util.ArrayList<>(s.enabledOres);
        s.oreScanRadius = SERVER.oreRadius.get();
        s.hostileScanRadius = SERVER.hostileRadius.get();
        s.worldSearchRadius = SERVER.worldSearchRadius.get();
        return s;
    }
}
