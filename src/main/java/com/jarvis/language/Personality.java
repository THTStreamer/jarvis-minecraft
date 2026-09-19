package com.jarvis.language;

/**
 * Configurable personality. Modulation parameters bias response generation
 * (formality, verbosity, wit, title of address) instead of selecting canned lines.
 */
public final class Personality {
    public enum AddressTitle { SIR, MADAM, COMMANDER, BOSS, FRIEND, NONE }

    private AddressTitle title = AddressTitle.SIR;
    private String playerPreferredName = "";
    /** 0 = terse, 1 = elaborate. */
    private double verbosity = 0.45;
    /** 0 = flat, 1 = dry wit. */
    private double wit = 0.35;
    /** 0 = casual, 1 = formal butler. */
    private double formality = 0.8;

    public AddressTitle title() { return title; }
    public void setTitle(AddressTitle title) { this.title = title; }

    public String playerPreferredName() { return playerPreferredName; }
    public void setPlayerPreferredName(String name) { this.playerPreferredName = name == null ? "" : name; }

    public double verbosity() { return verbosity; }
    public void setVerbosity(double v) { this.verbosity = clamp(v); }

    public double wit() { return wit; }
    public void setWit(double w) { this.wit = clamp(w); }

    public double formality() { return formality; }
    public void setFormality(double f) { this.formality = clamp(f); }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** How Jarvis addresses the player, e.g. "sir" or a preferred name. */
    public String address() {
        if (!playerPreferredName.isBlank()) return playerPreferredName;
        return switch (title) {
            case SIR -> "sir";
            case MADAM -> "madam";
            case COMMANDER -> "commander";
            case BOSS -> "boss";
            case FRIEND -> "my friend";
            case NONE -> "";
        };
    }

    public String addressSuffix() {
        String a = address();
        return a.isEmpty() ? "" : ", " + a;
    }
}
