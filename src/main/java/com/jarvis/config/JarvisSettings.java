package com.jarvis.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain settings snapshot consumed by the AI core. Built from the NeoForge
 * specs by the glue layer; unit-testable without Minecraft.
 */
public final class JarvisSettings {
    public boolean aiEnabled = true;
    public boolean voiceEnabled = true;
    public boolean requireVoiceChat = false;
    public boolean learningEnabled = true;
    public boolean modLearningEnabled = true;
    public boolean dynamicSkillsEnabled = true;
    public boolean jarvisToJarvisEnabled = true;
    public boolean bootstrapEnabled = true;
    public boolean debugLogging = false;

    public String addressTitle = "SIR";
    public double verbosity = 0.45;
    public double wit = 0.35;
    public double formality = 0.8;
    public float speechSpeed = 1.0f;
    public float speechPitch = 112f;

    public boolean hudEnabled = true;
    public String hudPosition = "TOP_LEFT";
    public int hudSize = 64;

    public boolean oreDetectionEnabled = false;
    public List<String> enabledOres = new ArrayList<>(List.of(
        "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:ancient_debris"));
    public double oreScanRadius = 20.0;

    public double hostileScanRadius = 32.0;
    public int worldSearchRadius = 5000;
    public int maxVocab = 2048;
    public int dim = 64;
    public int heads = 4;
    public int blocks = 2;
    public int ffnDim = 128;
    public int maxSeq = 48;
    public int trainingBatch = 8;
    public int trainingQueue = 512;
    public long inferenceBudgetMs = 1500;
    public long seed = 20240808L;
}
