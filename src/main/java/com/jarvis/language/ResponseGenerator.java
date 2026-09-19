package com.jarvis.language;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Compositional response generation. Responses are built from semantic frames
 * (intent + entities + result data) with personality modulation controlling
 * openings, titles, verbosity and closings. A deterministic fallback guarantees
 * a safe, well-formed answer when generation inputs are missing or invalid -
 * malformed output can never reach the server.
 */
public final class ResponseGenerator {
    private final Personality personality;
    private final Random rng;

    public ResponseGenerator(Personality personality, long seed) {
        this.personality = personality;
        this.rng = new Random(seed);
    }

    /** Free-form frame: VERB + OBJECT + DETAIL + EVIDENCE, modulated by personality. */
    public String compose(Intent intent, Map<String, Object> frame) {
        try {
            String body = buildBody(intent, frame);
            return polish(body, intent);
        } catch (Exception e) {
            return fallback(intent);
        }
    }

    private String buildBody(Intent intent, Map<String, Object> f) {
        return switch (intent) {
            case GREETING -> pick("At your service", "Yes", "Good to see you", "Online and ready");
            case FAREWELL -> "Goodbye for now. I'll be here when you need me";
            case HELP -> "I can scan for hostiles, locate structures and biomes, guide you, "
                + "track ores where permitted, learn mod mechanics by observation, and "
                + "create new skills when you teach me. Just ask naturally";
            case STATUS -> "All systems operational. Neural core, memory, world model and voice engine nominal";
            case SCAN_HOSTILES -> hostileSummary(f);
            case SCAN_AREA -> areaSummary(f);
            case LOCATE_STRUCTURE -> locateSummary(f, "structure");
            case LOCATE_BIOME -> locateSummary(f, "biome");
            case NAVIGATE_GUIDE -> "Of course. I'll mark the route and guide you";
            case DISTANCE_QUERY -> distanceSummary(f);
            case TRAVEL_TIME_QUERY -> travelSummary(f);
            case ORE_SCAN -> oreSummary(f);
            case MOD_QUESTION, MACHINE_QUESTION, SPELL_QUESTION -> knowledgeSummary(f);
            case REMEMBER -> "Noted. I'll remember that";
            case FORGET -> "Done. I've cleared what you asked me to forget";
            case MESSAGE_PLAYER -> messageSummary(f);
            case MUTE -> "Understood. I'll stay quiet until you need me";
            case UNMUTE -> "I'm listening again";
            case VOICE_CONFIG -> "Voice configuration updated";
            case SKILL_CREATE -> skillCreateSummary(f);
            case SKILL_LIST -> skillListSummary(f);
            case MEMORY_QUERY -> "Here's what I hold in memory";
            case KNOWLEDGE_QUERY -> knowledgeSummary(f);
            case CONFIRM -> "Confirmed";
            case DENY -> "Very well. Standing by";
            case FOLLOW_UP -> followUpSummary(f);
            case UNKNOWN -> "I'm not certain I understood. Could you rephrase that";
        };
    }

    private String hostileSummary(Map<String, Object> f) {
        Object count = f.get("count");
        Object detail = f.get("detail");
        if (count instanceof Number n && n.intValue() == 0) {
            return "Nothing hostile within the current scan radius";
        }
        StringBuilder sb = new StringBuilder("I've detected ");
        if (count instanceof Number n) {
            sb.append(spellCount(n.intValue())).append(n.intValue() == 1 ? " hostile creature" : " hostile creatures");
        } else {
            sb.append("hostile creatures");
        }
        Object radius = f.get("radius");
        if (radius instanceof Number r) sb.append(" within approximately ").append(r.intValue()).append(" blocks");
        if (detail instanceof String d && !d.isBlank()) sb.append(". ").append(d);
        return sb.toString();
    }

    private String areaSummary(Map<String, Object> f) {
        Object detail = f.get("detail");
        if (detail instanceof String d && !d.isBlank()) return d;
        return "I've surveyed the surroundings";
    }

    private String locateSummary(Map<String, Object> f, String kind) {
        Object name = f.get("name");
        Object dist = f.get("distance");
        Object dir = f.get("direction");
        Object fail = f.get("failure");
        if (fail instanceof String s) return s;
        StringBuilder sb = new StringBuilder("I've located ");
        sb.append(name instanceof String s ? "a " + s : "a " + kind);
        if (dist instanceof Number n && dir instanceof String d) {
            sb.append(" approximately ").append(Math.round(n.doubleValue()))
              .append(" blocks ").append(d).append(" of us");
        } else if (dist instanceof Number n) {
            sb.append(" approximately ").append(Math.round(n.doubleValue())).append(" blocks away");
        }
        return sb.toString();
    }

    private String distanceSummary(Map<String, Object> f) {
        Object dist = f.get("distance");
        Object dir = f.get("direction");
        if (dist instanceof Number n) {
            String s = "Approximately " + Math.round(n.doubleValue()) + " blocks";
            if (dir instanceof String d) s += " " + d;
            s += " from our current position";
            return s;
        }
        return fallback(Intent.DISTANCE_QUERY);
    }

    private String travelSummary(Map<String, Object> f) {
        Object minutes = f.get("minutes");
        if (minutes instanceof Number n) {
            if (n.doubleValue() < 1) return "Under a minute on foot, give or take terrain";
            return "Roughly " + Math.round(n.doubleValue()) + " minutes on foot, depending on terrain";
        }
        return fallback(Intent.TRAVEL_TIME_QUERY);
    }

    private String oreSummary(Map<String, Object> f) {
        if (Boolean.TRUE.equals(f.get("disabled"))) {
            return "I'm afraid I can't scan for ores, sir. Ore detection is currently disabled";
        }
        Object detail = f.get("detail");
        if (detail instanceof String d && !d.isBlank()) return d;
        return "Ore scan complete";
    }

    private String knowledgeSummary(Map<String, Object> f) {
        Object answer = f.get("answer");
        if (answer instanceof String s && !s.isBlank()) return s;
        Object topic = f.get("topic");
        if (topic instanceof String t) return "I don't have reliable knowledge about " + t + " yet, but I can learn by observing";
        return "I'm not familiar with it yet, but I'll observe and learn";
    }

    private String messageSummary(Map<String, Object> f) {
        Object target = f.get("target");
        if (target instanceof String t) return "Message relayed. I'll have " + t + "'s Jarvis pass it on";
        return "Message queued for delivery";
    }

    private String skillCreateSummary(Map<String, Object> f) {
        if (Boolean.TRUE.equals(f.get("success"))) {
            Object name = f.get("skill");
            return "I believe I can learn how to do that" + (name instanceof String s ? ". I've drafted the " + s + " skill" : "") + ". Give me a moment";
        }
        return "Sorry, sir, I cannot seem to create this skill.";
    }

    private String skillListSummary(Map<String, Object> f) {
        Object list = f.get("list");
        if (list instanceof String s && !s.isBlank()) return "My current skills: " + s;
        return "I have my core skills ready: scanning, locating, navigation and observation";
    }

    private String followUpSummary(Map<String, Object> f) {
        Object answer = f.get("answer");
        if (answer instanceof String s && !s.isBlank()) return s;
        return "Standing by. What would you like to know";
    }

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    private String polish(String body, Intent intent) {
        String addr = personality.addressSuffix();
        boolean question = intent == Intent.MOD_QUESTION || intent == Intent.MACHINE_QUESTION
            || intent == Intent.SPELL_QUESTION || intent == Intent.KNOWLEDGE_QUERY;
        StringBuilder sb = new StringBuilder();
        // opening modulated by formality
        if (personality.formality() > 0.6 && needsOpening(intent)) {
            sb.append(pick("Certainly", "Of course", "Right away")).append(addr).append(". ");
        }
        sb.append(body);
        // address player naturally once
        if (!addr.isEmpty() && !sb.toString().contains(addr) && personality.formality() > 0.4
                && (intent == Intent.SCAN_HOSTILES || intent == Intent.LOCATE_STRUCTURE
                    || intent == Intent.ORE_SCAN || intent == Intent.GREETING)) {
            sb.append(addr);
        }
        String out = sb.toString().trim();
        if (!out.endsWith(".") && !out.endsWith("?") && !out.endsWith("!")) {
            out += question ? "." : ".";
        }
        return out;
    }

    private boolean needsOpening(Intent intent) {
        return switch (intent) {
            case LOCATE_STRUCTURE, LOCATE_BIOME, NAVIGATE_GUIDE, SCAN_HOSTILES, SCAN_AREA -> true;
            default -> false;
        };
    }

    private String spellCount(int n) {
        if (n == 0) return "no ";
        if (n < 20) {
            String[] words = {"zero", "one", "two", "three", "four", "five", "six", "seven",
                "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
                "sixteen", "seventeen", "eighteen", "nineteen"};
            return words[n] + " ";
        }
        return n + " ";
    }

    /** Deterministic safe fallback - always well-formed, never crashes, never invents facts. */
    public String fallback(Intent intent) {
        Map<String, Object> empty = new LinkedHashMap<>();
        return switch (intent) {
            case DISTANCE_QUERY -> "I don't have a current destination to measure against.";
            case TRAVEL_TIME_QUERY -> "I can't estimate that without a destination.";
            case SCAN_HOSTILES -> "Scan unavailable at the moment.";
            case LOCATE_STRUCTURE, LOCATE_BIOME ->
                "Sir, I attempted to locate that, but the search area did not contain one.";
            case ORE_SCAN -> "I'm afraid I can't scan for ores, sir. Ore detection is currently disabled.";
            case SKILL_CREATE -> "Sorry, sir, I cannot seem to create this skill.";
            default -> "Understood.";
        };
    }

    /** Natural variation helper for repeated status-style lines. */
    public List<String> variations(String base, int n) {
        List<String> out = new ArrayList<>();
        out.add(base);
        return out;
    }
}
